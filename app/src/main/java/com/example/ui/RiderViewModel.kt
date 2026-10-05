package com.example.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.RiderApp
import com.example.data.ApiException
import com.example.data.AppNotification
import com.example.data.BookingRequest
import com.example.data.CheckoutOrder
import com.example.data.Complaint
import com.example.data.DriverLocation
import com.example.data.EmergencyContact
import com.example.data.FareOption
import com.example.data.Invoice
import com.example.data.LegalDoc
import com.example.data.LiveTable
import com.example.data.NearbyCar
import com.example.data.Participants
import com.example.data.Payment
import com.example.data.PaymentOption
import com.example.data.Place
import com.example.data.Profile
import com.example.data.Refund
import com.example.data.ReturnOffer
import com.example.data.Ride
import com.example.data.RideStatus
import com.example.data.SavedPlace
import com.example.data.SessionExpiredException
import com.example.data.Validators
import com.example.data.VehicleCategory
import com.example.data.WalletTxn
import com.example.location.LocationProvider
import com.example.push.PushMessagingService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

sealed interface Screen {
    data object Splash : Screen
    data object Auth : Screen
    data object CompleteProfile : Screen
    data class Blocked(val message: String) : Screen
    data object Home : Screen
    data class PickPlace(val target: PlaceTarget) : Screen
    data object Quote : Screen
    data class Trip(val rideId: String) : Screen
    data object Rides : Screen
    data class RideDetail(val rideId: String) : Screen
    data object Wallet : Screen
    data object Profile : Screen
    data object SavedPlaces : Screen
    data object EmergencyContacts : Screen
    data object Support : Screen
}

enum class PlaceTarget { PICKUP, DROP, SAVED }

data class AuthState(val otpSentTo: String? = null, val resendAvailableAt: Long = 0L)

data class BookingState(
    val category: VehicleCategory = VehicleCategory.PASSENGER,
    val pickup: Place? = null,
    val drop: Place? = null,
    val passengers: Int = 1,
    val goodsWeightKg: String = "",
    val goodsDescription: String = "",
    val promoCode: String? = null,
    val scheduledAt: Instant? = null,
    val paymentOption: PaymentOption = PaymentOption.FULL,
    val fares: List<FareOption> = emptyList(),
    val faresLoading: Boolean = false,
    val faresError: String? = null,
    val selectedTypeId: String? = null,
    val distanceKm: Double? = null,
    val returnOffers: List<ReturnOffer> = emptyList(),
    val cars: List<NearbyCar> = emptyList(),
) {
    val selectedFare get() = fares.firstOrNull { it.vehicleTypeId == selectedTypeId }
    val weight get() = goodsWeightKg.toDoubleOrNull()?.takeIf { it > 0 }

    /** Minutes to the nearest car of a vehicle type, if one is around. */
    fun etaFor(typeId: String) = cars.filter { it.vehicleTypeId == typeId }.minOfOrNull { it.etaMin }
}

data class TripState(
    val ride: Ride? = null,
    val payments: List<Payment> = emptyList(),
    val refunds: List<Refund> = emptyList(),
    val participants: Participants? = null,
    val startCode: String? = null,
    val driverLocation: DriverLocation? = null,
    val rated: Boolean = false,
    val loading: Boolean = true,
) {
    val duePayment get() = payments.firstOrNull { it.isDue }
}

data class RideDetailState(
    val ride: Ride? = null,
    val invoice: Invoice? = null,
    val payments: List<Payment> = emptyList(),
    val refunds: List<Refund> = emptyList(),
    val participants: Participants? = null,
    val rated: Boolean = false,
    val loading: Boolean = true,
)

data class WalletState(
    val balance: Double = 0.0,
    val transactions: List<WalletTxn> = emptyList(),
    val refunds: List<Refund> = emptyList(),
    val loading: Boolean = true,
)

/** Emitted after an SOS so the screen can offer to call or text emergency contacts. */
data class SosEvent(val contacts: List<EmergencyContact>, val shareText: String)

class RiderViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as RiderApp
    private val repo = app.repository
    private val geocoding = app.geocoding

    private val _screen = MutableStateFlow<Screen>(Screen.Splash)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messageFlow: SharedFlow<String> = _messages

    private val _checkout = MutableSharedFlow<CheckoutOrder>(extraBufferCapacity = 1)
    val checkoutEvents: SharedFlow<CheckoutOrder> = _checkout

    private val _sos = MutableSharedFlow<SosEvent>(extraBufferCapacity = 1)
    val sosEvents: SharedFlow<SosEvent> = _sos

    private val _startupError = MutableStateFlow<String?>(null)
    val startupError: StateFlow<String?> = _startupError.asStateFlow()

    private val _auth = MutableStateFlow(AuthState())
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private val _booking = MutableStateFlow(BookingState())
    val booking: StateFlow<BookingState> = _booking.asStateFlow()

    private val _myLocation = MutableStateFlow<Place?>(null)
    val myLocation: StateFlow<Place?> = _myLocation.asStateFlow()

    private val _trip = MutableStateFlow(TripState())
    val trip: StateFlow<TripState> = _trip.asStateFlow()

    private val _openRides = MutableStateFlow<List<Ride>>(emptyList())
    val openRides: StateFlow<List<Ride>> = _openRides.asStateFlow()

    private val _history = MutableStateFlow<List<Ride>?>(null)
    val history: StateFlow<List<Ride>?> = _history.asStateFlow()

    private val _detail = MutableStateFlow(RideDetailState())
    val detail: StateFlow<RideDetailState> = _detail.asStateFlow()

    private val _wallet = MutableStateFlow(WalletState())
    val wallet: StateFlow<WalletState> = _wallet.asStateFlow()

    private val _savedPlaces = MutableStateFlow<List<SavedPlace>>(emptyList())
    val savedPlaces: StateFlow<List<SavedPlace>> = _savedPlaces.asStateFlow()

    private val _contacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val contacts: StateFlow<List<EmergencyContact>> = _contacts.asStateFlow()

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _complaints = MutableStateFlow<List<Complaint>>(emptyList())
    val complaints: StateFlow<List<Complaint>> = _complaints.asStateFlow()

    private val _terms = MutableStateFlow<LegalDoc?>(null)
    val terms: StateFlow<LegalDoc?> = _terms.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Place>>(emptyList())
    val searchResults: StateFlow<List<Place>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    val recentPlaces get() = app.session.recentPlaces

    private var pendingPhone: String? = null
    private var pendingName: String? = null
    private var pendingCheckout: CheckoutOrder? = null
    private var tripJob: Job? = null
    private var faresJob: Job? = null
    private var searchJob: Job? = null
    private var carsJob: Job? = null
    private var pendingRideFromIntent: String? = null

    init {
        viewModelScope.launch {
            repo.sessionExpired.collect {
                stopLive()
                _screen.value = Screen.Auth
                _messages.tryEmit("Your session has expired. Please log in again.")
            }
        }
        viewModelScope.launch {
            app.realtime.changes.collect { table ->
                when (table) {
                    LiveTable.RIDES -> onRidesChanged()
                    LiveTable.NOTIFICATIONS -> loadNotifications()
                }
            }
        }
        bootstrap()
    }

    // ---------- Startup & auth ----------

    fun bootstrap() {
        _startupError.value = null
        if (!repo.isLoggedIn) {
            _screen.value = Screen.Auth
            return
        }
        _screen.value = Screen.Splash
        viewModelScope.launch {
            try {
                routeAfterLogin()
            } catch (e: SessionExpiredException) {
                _screen.value = Screen.Auth
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _startupError.value = e.message ?: "Couldn't connect. Check your internet and try again."
            }
        }
    }

    private suspend fun routeAfterLogin() {
        val profile = repo.profile() ?: throw ApiException("Your account could not be loaded. Please try again.")
        _profile.value = profile
        when {
            profile.isDeleted -> { _screen.value = Screen.Blocked("This account has been deleted."); return }
            !profile.isCustomer -> {
                _screen.value = Screen.Blocked(
                    "This number is registered as a ${profile.role}. Please use the GoRide Driver app, or log in with a different number."
                )
                return
            }
            profile.isCurrentlySuspended() -> {
                val until = profile.suspendedUntil?.let { " until ${com.example.ui.components.Format.dateTime(it)}" } ?: ""
                _screen.value = Screen.Blocked("Your account is suspended$until. ${profile.suspensionReason ?: "Please contact support."}")
                return
            }
        }
        if (profile.needsName) {
            val name = pendingName
            if (!name.isNullOrBlank()) {
                repo.updateName(name)
                _profile.value = profile.copy(fullName = name.trim())
            } else {
                _screen.value = Screen.CompleteProfile
                return
            }
        }
        pendingName = null
        startLive()
        val open = runCatching { repo.openRides() }.getOrDefault(emptyList())
        _openRides.value = open
        val intentRide = pendingRideFromIntent.also { pendingRideFromIntent = null }
        val live = open.firstOrNull { it.status.hasDriver || it.status == RideStatus.REQUESTED && !isFutureScheduled(it) }
        when {
            intentRide != null -> openRide(intentRide)
            live != null -> openTrip(live.id)
            else -> _screen.value = Screen.Home
        }
        loadSavedPlaces()
        loadNotifications()
        refreshMyLocation()
    }

    private fun isFutureScheduled(ride: Ride) =
        ride.scheduledAt?.isAfter(Instant.now().plusSeconds(30 * 60)) == true

    fun sendOtp(phoneInput: String, name: String) {
        val phone = Validators.normalizePhone(phoneInput)
        if (phone == null) { toast("Enter a valid 10-digit mobile number"); return }
        run("Sending code…") {
            repo.sendOtp(phone, name.takeIf { it.isNotBlank() })
            pendingPhone = phone
            pendingName = name.trim().takeIf { it.isNotEmpty() }
            _auth.value = AuthState(otpSentTo = phone, resendAvailableAt = System.currentTimeMillis() + 30_000)
        }
    }

    fun resendOtp() {
        val phone = pendingPhone ?: return
        run("Sending code…") {
            repo.sendOtp(phone, pendingName)
            _auth.update { it.copy(resendAvailableAt = System.currentTimeMillis() + 30_000) }
            toast("A new code has been sent")
        }
    }

    fun changeNumber() {
        _auth.value = AuthState()
    }

    fun verifyOtp(code: String) {
        val phone = pendingPhone ?: return
        if (!Validators.isValidOtp(code)) { toast("Enter the 6-digit code"); return }
        run("Verifying…") {
            repo.verifyOtp(phone, code)
            _auth.value = AuthState()
            routeAfterLogin()
        }
    }

    fun saveName(name: String) {
        if (name.trim().length < 2) { toast("Please enter your name"); return }
        run("Saving…") {
            repo.updateName(name)
            pendingName = null
            routeAfterLogin()
        }
    }

    fun logout() {
        viewModelScope.launch {
            _busy.value = "Logging out…"
            stopLive()
            PushMessagingService.unregister(app)
            runCatching { repo.signOut() }
            _profile.value = null
            _booking.value = BookingState()
            _trip.value = TripState()
            _history.value = null
            _openRides.value = emptyList()
            _busy.value = null
            _screen.value = Screen.Auth
        }
    }

    private fun startLive() {
        repo.userId?.let { app.realtime.start(it) }
        viewModelScope.launch { PushMessagingService.register(app) }
    }

    private fun stopLive() {
        tripJob?.cancel()
        app.realtime.stop()
    }

    /** Called when a notification with a ride id was tapped. */
    fun handleRideIntent(rideId: String?) {
        if (rideId == null) return
        if (_screen.value == Screen.Splash || _screen.value == Screen.Auth) pendingRideFromIntent = rideId else openRide(rideId)
    }

    fun onAppResumed() {
        if (!repo.isLoggedIn || _profile.value == null) return
        viewModelScope.launch {
            runCatching { _openRides.value = repo.openRides() }
            (_screen.value as? Screen.Trip)?.let { refreshTrip(it.rideId) }
        }
    }

    // ---------- Navigation ----------

    fun navigate(screen: Screen) {
        _screen.value = screen
        when (screen) {
            Screen.Rides -> loadHistory()
            Screen.Wallet -> loadWallet()
            Screen.Profile -> refreshProfile()
            Screen.SavedPlaces -> loadSavedPlaces()
            Screen.EmergencyContacts -> loadContacts()
            Screen.Support -> loadComplaints()
            Screen.Home -> { refreshOpenRides(); refreshCars() }
            else -> Unit
        }
    }

    fun back() {
        when (val s = _screen.value) {
            is Screen.PickPlace -> navigate(if (s.target == PlaceTarget.SAVED) Screen.SavedPlaces else if (_booking.value.drop != null && _booking.value.pickup != null && s.target == PlaceTarget.PICKUP && _booking.value.fares.isNotEmpty()) Screen.Quote else Screen.Home)
            Screen.Quote -> navigate(Screen.Home)
            is Screen.Trip -> navigate(Screen.Home)
            is Screen.RideDetail -> navigate(Screen.Rides)
            Screen.SavedPlaces, Screen.EmergencyContacts, Screen.Support -> navigate(Screen.Profile)
            Screen.Rides, Screen.Wallet, Screen.Profile -> navigate(Screen.Home)
            else -> Unit
        }
    }

    /** Opens a ride on the live trip screen when it's open, otherwise its receipt. */
    fun openRide(rideId: String) {
        viewModelScope.launch {
            val ride = runCatching { repo.ride(rideId) }.getOrNull()
            if (ride != null && ride.status.isOpen) openTrip(rideId) else openDetail(rideId)
        }
    }

    // ---------- Location & places ----------

    fun refreshMyLocation() {
        viewModelScope.launch {
            val location = LocationProvider.current(app) ?: return@launch
            val here = Place(location.latitude, location.longitude, "Current location")
            _myLocation.value = here
            if (_booking.value.pickup == null) {
                _booking.update { it.copy(pickup = here) }
                val address = geocoding.addressFor(here.lat, here.lng)
                val named = here.copy(address = address)
                _myLocation.value = named
                _booking.update { if (it.pickup == here) it.copy(pickup = named) else it }
            }
            refreshCars()
        }
    }

    fun refreshCars() {
        val at = _booking.value.pickup ?: _myLocation.value ?: return
        carsJob?.cancel()
        carsJob = viewModelScope.launch {
            runCatching { repo.nearbyCars(at) }.onSuccess { cars -> _booking.update { it.copy(cars = cars) } }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 3) {
            _searchResults.value = emptyList()
            _searching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _searching.value = true
            _searchResults.value = runCatching { geocoding.search(query, _myLocation.value ?: _booking.value.pickup) }.getOrDefault(emptyList())
            _searching.value = false
        }
    }

    /** Address for a point picked on the map. */
    suspend fun addressFor(lat: Double, lng: Double): String = geocoding.addressFor(lat, lng)

    fun choosePlace(target: PlaceTarget, place: Place) {
        _searchResults.value = emptyList()
        if (target == PlaceTarget.SAVED) {
            _screen.value = Screen.SavedPlaces
            _pendingSavedPlace.value = place
            return
        }
        app.session.addRecentPlace(place)
        _booking.update {
            if (target == PlaceTarget.PICKUP) it.copy(pickup = place, fares = emptyList()) else it.copy(drop = place, fares = emptyList())
        }
        val b = _booking.value
        if (b.pickup != null && b.drop != null) {
            if (b.pickup.distanceTo(b.drop) < 0.2) {
                toast("Pickup and destination are too close")
                _screen.value = Screen.Home
                return
            }
            _screen.value = Screen.Quote
            loadQuote()
        } else {
            _screen.value = if (b.drop == null) Screen.PickPlace(PlaceTarget.DROP) else Screen.Home
        }
        refreshCars()
    }

    fun useCurrentLocationFor(target: PlaceTarget) {
        viewModelScope.launch {
            _busy.value = "Finding you…"
            val location = LocationProvider.current(app)
            _busy.value = null
            if (location == null) { toast("Turn on location to use your current position"); return@launch }
            val address = geocoding.addressFor(location.latitude, location.longitude)
            val place = Place(location.latitude, location.longitude, address)
            _myLocation.value = place
            choosePlace(target, place)
        }
    }

    fun swapPlaces() {
        _booking.update { it.copy(pickup = it.drop, drop = it.pickup, fares = emptyList()) }
        if (_booking.value.pickup != null && _booking.value.drop != null) loadQuote()
    }

    // ---------- Quote & booking ----------

    fun setCategory(category: VehicleCategory) {
        if (_booking.value.category == category) return
        _booking.update { it.copy(category = category, selectedTypeId = null, fares = emptyList()) }
        if (_screen.value == Screen.Quote) loadQuote()
    }

    fun setPassengers(count: Int) {
        _booking.update { it.copy(passengers = count.coerceIn(1, 50)) }
        loadQuote(debounce = true)
    }

    fun setGoodsWeight(value: String) {
        _booking.update { it.copy(goodsWeightKg = value.filter { c -> c.isDigit() || c == '.' }.take(7)) }
        loadQuote(debounce = true)
    }

    fun setGoodsDescription(value: String) = _booking.update { it.copy(goodsDescription = value.take(200)) }

    fun selectVehicle(typeId: String) = _booking.update { it.copy(selectedTypeId = typeId) }

    fun setPaymentOption(option: PaymentOption) = _booking.update { it.copy(paymentOption = option) }

    fun setSchedule(at: Instant?) {
        if (at != null && at.isBefore(Instant.now().plusSeconds(15 * 60))) {
            toast("Pick a time at least 15 minutes from now")
            return
        }
        if (at != null && at.isAfter(Instant.now().plusSeconds(7L * 24 * 3600))) {
            toast("Rides can be scheduled up to 7 days ahead")
            return
        }
        _booking.update { it.copy(scheduledAt = at) }
    }

    fun applyPromo(code: String) {
        val normalized = Validators.normalizePromo(code)
        if (!Validators.isValidPromo(normalized)) { toast("Enter a valid promo code"); return }
        _booking.update { it.copy(promoCode = normalized) }
        loadQuote(onLoaded = { fares ->
            if (fares.none { it.discount > 0 }) {
                _booking.update { it.copy(promoCode = null) }
                toast("This promo code isn't valid for this trip")
                loadQuote()
            } else {
                toast("Promo applied")
            }
        })
    }

    fun removePromo() {
        _booking.update { it.copy(promoCode = null) }
        loadQuote()
    }

    fun loadQuote(debounce: Boolean = false, onLoaded: ((List<FareOption>) -> Unit)? = null) {
        val b = _booking.value
        val pickup = b.pickup ?: return
        val drop = b.drop ?: return
        faresJob?.cancel()
        faresJob = viewModelScope.launch {
            if (debounce) delay(500)
            _booking.update { it.copy(faresLoading = true, faresError = null) }
            try {
                val state = _booking.value
                val fares = repo.previewFares(
                    category = state.category,
                    pickup = pickup,
                    drop = drop,
                    passengers = if (state.category == VehicleCategory.PASSENGER) state.passengers else null,
                    goodsWeightKg = if (state.category == VehicleCategory.GOODS) state.weight else null,
                    promoCode = state.promoCode,
                )
                val distance = runCatching { repo.tripDistanceKm(pickup, drop) }.getOrNull()
                val offers = runCatching { repo.returnOffers(pickup, drop) }.getOrDefault(emptyList())
                val cars = runCatching { repo.nearbyCars(pickup) }.getOrDefault(state.cars)
                _booking.update { current ->
                    val keep = current.selectedTypeId?.takeIf { id -> fares.any { it.vehicleTypeId == id && it.fits } }
                    current.copy(
                        fares = fares,
                        faresLoading = false,
                        distanceKm = distance,
                        returnOffers = offers,
                        cars = cars,
                        selectedTypeId = keep ?: fares.firstOrNull { it.fits }?.vehicleTypeId,
                    )
                }
                onLoaded?.invoke(fares)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _booking.update { it.copy(faresLoading = false, faresError = e.message ?: "Couldn't load fares") }
            }
        }
    }

    fun loadTerms() {
        if (_terms.value != null) return
        viewModelScope.launch { _terms.value = runCatching { repo.legalDoc() }.getOrNull() }
    }

    private fun bookingRequest(typeId: String): BookingRequest? {
        val b = _booking.value
        val pickup = b.pickup ?: return null
        val drop = b.drop ?: return null
        val minutes = b.distanceKm?.let { it / 22.0 * 60 }
        return BookingRequest(
            pickup = pickup,
            drop = drop,
            vehicleTypeId = typeId,
            paymentOption = b.paymentOption,
            promoCode = b.promoCode,
            passengers = if (b.category == VehicleCategory.PASSENGER) b.passengers else null,
            goodsWeightKg = if (b.category == VehicleCategory.GOODS) b.weight else null,
            goodsDescription = if (b.category == VehicleCategory.GOODS) b.goodsDescription.trim().ifEmpty { null } else null,
            scheduledAt = b.scheduledAt,
            durationMin = minutes,
        )
    }

    fun book(termsAccepted: Boolean) {
        val b = _booking.value
        val fare = b.selectedFare
        if (fare == null) { toast("Choose a vehicle"); return }
        if (!fare.fits) { toast("This vehicle can't take that load. Choose a bigger one."); return }
        if (b.category == VehicleCategory.GOODS && b.weight == null) { toast("Enter the approximate goods weight"); return }
        if (!termsAccepted) { toast("Please accept the Terms & Conditions"); return }
        val request = bookingRequest(fare.vehicleTypeId) ?: return
        run("Booking your ride…") {
            val ride = repo.requestRide(request)
            afterBooking(ride)
        }
    }

    fun bookReturnOffer(offer: ReturnOffer, termsAccepted: Boolean) {
        if (!termsAccepted) { toast("Please accept the Terms & Conditions"); return }
        val b = _booking.value
        if (b.category == VehicleCategory.GOODS && b.weight == null) { toast("Enter the approximate goods weight"); return }
        val typeId = b.selectedTypeId ?: b.fares.firstOrNull()?.vehicleTypeId ?: ""
        val request = bookingRequest(typeId) ?: return
        run("Booking return trip…") {
            val ride = repo.bookReturnTrip(offer.offerId, request.copy(promoCode = null, scheduledAt = null))
            afterBooking(ride)
        }
    }

    private suspend fun afterBooking(ride: Ride) {
        _booking.update { BookingState(category = it.category, pickup = it.pickup, cars = it.cars) }
        _openRides.value = runCatching { repo.openRides() }.getOrDefault(listOf(ride))
        openTrip(ride.id)
        // Go straight to payment for the new booking.
        val due = runCatching { repo.payments(ride.id) }.getOrDefault(emptyList()).firstOrNull { it.isDue }
        if (due != null) startOnlinePayment(due.id, silentBusy = true)
    }

    // ---------- Live trip ----------

    fun openTrip(rideId: String) {
        _screen.value = Screen.Trip(rideId)
        if (_trip.value.ride?.id != rideId) _trip.value = TripState()
        tripJob?.cancel()
        tripJob = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                refreshTrip(rideId, full = tick % 6 == 0)
                val status = _trip.value.ride?.status
                if (status != null && !status.isOpen && tick > 0) {
                    // keep showing the final state, but stop polling
                    break
                }
                tick++
                delay(if (status?.hasDriver == true) 5_000 else 8_000)
            }
        }
    }

    private suspend fun refreshTrip(rideId: String, full: Boolean = true) {
        try {
            val ride = repo.ride(rideId) ?: run {
                _trip.update { it.copy(loading = false) }
                return
            }
            val previous = _trip.value.ride
            val statusChanged = previous?.status != ride.status
            val payments = if (full || statusChanged || _trip.value.payments.isEmpty()) repo.payments(rideId) else _trip.value.payments
            val participants = if (ride.driverId != null && (full || statusChanged || _trip.value.participants == null)) {
                runCatching { repo.participants(rideId) }.getOrNull()
            } else if (ride.driverId == null) null else _trip.value.participants
            val code = if (ride.status == RideStatus.ACCEPTED || ride.status == RideStatus.ARRIVED) {
                if (statusChanged || _trip.value.startCode == null || full) runCatching { repo.startCode(rideId) }.getOrNull() else _trip.value.startCode
            } else null
            val location = if (ride.status.hasDriver && ride.driverId != null) runCatching { repo.driverLocation(ride.driverId) }.getOrNull() else null
            val refunds = if (ride.status == RideStatus.CANCELLED && (full || statusChanged)) {
                runCatching { repo.refundsForRide(rideId) }.getOrDefault(emptyList())
            } else _trip.value.refunds
            val rated = if (ride.status == RideStatus.COMPLETED && statusChanged) runCatching { repo.hasRated(rideId) }.getOrDefault(false) else _trip.value.rated
            _trip.value = TripState(ride, payments, refunds, participants, code, location ?: _trip.value.driverLocation.takeIf { ride.status.hasDriver }, rated, loading = false)
            if (statusChanged && previous != null && previous.id == ride.id) onTripStatusChanged(ride)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpiredException) {
            throw e
        } catch (_: Exception) {
            _trip.update { it.copy(loading = false) }
        }
    }

    private fun onTripStatusChanged(ride: Ride) {
        refreshOpenRides()
        when (ride.status) {
            RideStatus.ACCEPTED -> toast("Driver assigned! Share your start code when they arrive.")
            RideStatus.ARRIVED -> toast("Your driver has arrived")
            RideStatus.ONGOING -> toast("Trip started. Have a safe ride!")
            RideStatus.COMPLETED -> toast("You've arrived. Thanks for riding with GoRide!")
            RideStatus.CANCELLED -> toast(ride.cancelReason ?: "This booking was cancelled")
            else -> Unit
        }
    }

    private fun onRidesChanged() {
        refreshOpenRides()
        (_screen.value as? Screen.Trip)?.let { s ->
            viewModelScope.launch { refreshTrip(s.rideId) }
            if (tripJob?.isActive != true) openTrip(s.rideId)
        }
        if (_screen.value == Screen.Rides) loadHistory()
    }

    private fun refreshOpenRides() {
        viewModelScope.launch { runCatching { repo.openRides() }.onSuccess { _openRides.value = it } }
    }

    fun refreshCurrentTrip() {
        val s = _screen.value as? Screen.Trip ?: return
        viewModelScope.launch { refreshTrip(s.rideId) }
    }

    fun cancelRide(reason: String) {
        val ride = _trip.value.ride ?: return
        run("Cancelling…") {
            repo.cancelRide(ride.id, reason)
            refreshTrip(ride.id)
            refreshOpenRides()
            toast("Booking cancelled")
        }
    }

    fun rateDriver(rideId: String, driverId: String, stars: Int, comment: String) {
        run("Submitting…") {
            repo.rateDriver(rideId, driverId, stars, comment)
            _trip.update { if (it.ride?.id == rideId) it.copy(rated = true) else it }
            _detail.update { if (it.ride?.id == rideId) it.copy(rated = true) else it }
            toast("Thanks for your feedback!")
        }
    }

    fun sos() {
        val ride = _trip.value.ride
        run("Sending SOS…") {
            val location = LocationProvider.current(app)?.let { Place(it.latitude, it.longitude, "") }
            repo.triggerSos(ride?.id, location)
            val contacts = runCatching { repo.emergencyContacts() }.getOrDefault(emptyList())
            _contacts.value = contacts
            _sos.tryEmit(SosEvent(contacts, sosText(ride, location)))
            toast("SOS sent to GoRide safety team")
        }
    }

    private fun sosText(ride: Ride?, location: Place?): String {
        val p = _trip.value.participants
        return buildString {
            append("EMERGENCY: I need help. ")
            location?.let { append("My location: https://maps.google.com/?q=${it.lat},${it.lng} ") }
            if (ride != null) {
                append("I'm on a GoRide trip from ${ride.pickup.address} to ${ride.drop.address}. ")
                p?.driverName?.let { append("Driver: $it. ") }
                p?.vehiclePlate?.let { append("Vehicle: $it.") }
            }
        }.trim()
    }

    fun shareTripText(): String? {
        val t = _trip.value
        val ride = t.ride ?: return null
        val p = t.participants
        return buildString {
            append("I'm on a GoRide trip from ${ride.pickup.address} to ${ride.drop.address}.")
            p?.driverName?.let { append(" Driver: $it") }
            p?.vehiclePlate?.let { append(" ($it)") }
            t.driverLocation?.let { append(". Live position: https://maps.google.com/?q=${it.lat},${it.lng}") }
        }
    }

    // ---------- Payments ----------

    fun startOnlinePayment(paymentId: String, silentBusy: Boolean = false) {
        viewModelScope.launch {
            _busy.value = if (silentBusy) "Preparing payment…" else "Opening payment…"
            try {
                val order = repo.createCheckout(paymentId)
                pendingCheckout = order
                _checkout.tryEmit(order)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.message ?: "Couldn't start the payment")
            } finally {
                _busy.value = null
            }
        }
    }

    fun onCheckoutSuccess(orderId: String?, razorpayPaymentId: String?, signature: String?) {
        val order = pendingCheckout
        pendingCheckout = null
        if (order == null || razorpayPaymentId == null || signature == null) {
            // The webhook still completes it; just refresh.
            toast("Payment received. Confirming…")
            refreshCurrentTrip()
            return
        }
        run("Confirming payment…") {
            repo.verifyCheckout(order.paymentId, orderId ?: order.orderId, razorpayPaymentId, signature)
            toast("Payment successful")
            refreshAfterPayment()
        }
    }

    fun onCheckoutError(code: Int, description: String?) {
        pendingCheckout = null
        val cancelled = code == 0 || description?.contains("cancel", ignoreCase = true) == true
        toast(if (cancelled) "Payment cancelled. You can pay again from this screen." else "Payment failed: ${description ?: "please try again"}")
        refreshCurrentTrip()
    }

    fun payWithWallet(paymentId: String) {
        run("Paying from wallet…") {
            repo.payWithWallet(paymentId)
            toast("Paid from your wallet")
            refreshAfterPayment()
        }
    }

    private suspend fun refreshAfterPayment() {
        (_screen.value as? Screen.Trip)?.let { refreshTrip(it.rideId) }
        (_screen.value as? Screen.RideDetail)?.let { loadDetail(it.rideId) }
        refreshOpenRides()
        runCatching { _wallet.update { it.copy(balance = repo.walletBalance()) } }
    }

    fun refreshWalletBalance() {
        viewModelScope.launch { runCatching { repo.walletBalance() }.onSuccess { b -> _wallet.update { it.copy(balance = b) } } }
    }

    // ---------- History & receipts ----------

    fun loadHistory() {
        viewModelScope.launch {
            runCatching { repo.rideHistory() }
                .onSuccess { _history.value = it }
                .onFailure { if (_history.value == null) _history.value = emptyList(); toast(it.message ?: "Couldn't load your rides") }
        }
    }

    fun openDetail(rideId: String) {
        _screen.value = Screen.RideDetail(rideId)
        _detail.value = RideDetailState()
        viewModelScope.launch { loadDetail(rideId) }
    }

    private suspend fun loadDetail(rideId: String) {
        try {
            val ride = repo.ride(rideId)
            val payments = repo.payments(rideId)
            val refunds = runCatching { repo.refundsForRide(rideId) }.getOrDefault(emptyList())
            val invoice = runCatching { repo.invoice(rideId) }.getOrNull()
            val participants = if (ride?.driverId != null) runCatching { repo.participants(rideId) }.getOrNull() else null
            val rated = if (ride?.status == RideStatus.COMPLETED) runCatching { repo.hasRated(rideId) }.getOrDefault(true) else true
            _detail.value = RideDetailState(ride, invoice, payments, refunds, participants, rated, loading = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _detail.update { it.copy(loading = false) }
            toast(e.message ?: "Couldn't load this ride")
        }
    }

    // ---------- Wallet ----------

    fun loadWallet() {
        viewModelScope.launch {
            try {
                _wallet.value = WalletState(repo.walletBalance(), repo.walletTransactions(), repo.refunds(), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _wallet.update { it.copy(loading = false) }
                toast(e.message ?: "Couldn't load your wallet")
            }
        }
    }

    // ---------- Profile, saved places, contacts ----------

    private fun refreshProfile() {
        viewModelScope.launch { runCatching { repo.profile() }.onSuccess { if (it != null) _profile.value = it } }
        loadNotifications()
    }

    fun updateName(name: String) {
        if (name.trim().length < 2) { toast("Please enter your name"); return }
        run("Saving…") {
            repo.updateName(name)
            _profile.update { it?.copy(fullName = name.trim()) }
            toast("Name updated")
        }
    }

    fun uploadAvatar(uri: android.net.Uri) {
        run("Uploading photo…") {
            val bytes = com.example.data.ImageCompressor.compress(app, uri) ?: throw ApiException("Couldn't read that photo")
            val url = repo.uploadAvatar(bytes)
            _profile.update { it?.copy(avatarUrl = url) }
            toast("Photo updated")
        }
    }

    fun applyReferral(code: String) {
        if (code.trim().length < 4) { toast("Enter a referral code"); return }
        run("Applying…") {
            repo.applyReferral(code)
            toast("Referral applied. Your reward arrives after your first ride.")
        }
    }

    fun requestAccountDeletion(reason: String) {
        run("Submitting…") {
            repo.requestAccountDeletion(reason)
            toast("Deletion requested. Our team will process it within 7 days.")
        }
    }

    private val _pendingSavedPlace = MutableStateFlow<Place?>(null)
    val pendingSavedPlace: StateFlow<Place?> = _pendingSavedPlace.asStateFlow()

    fun clearPendingSavedPlace() { _pendingSavedPlace.value = null }

    fun loadSavedPlaces() {
        viewModelScope.launch { runCatching { repo.savedPlaces() }.onSuccess { _savedPlaces.value = it } }
    }

    fun addSavedPlace(label: String, place: Place) {
        if (label.isBlank()) { toast("Give this place a name"); return }
        run("Saving…") {
            repo.addSavedPlace(label, place)
            _pendingSavedPlace.value = null
            _savedPlaces.value = repo.savedPlaces()
            toast("Place saved")
        }
    }

    fun removeSavedPlace(id: String) {
        run(null) {
            repo.removeSavedPlace(id)
            _savedPlaces.value = repo.savedPlaces()
        }
    }

    fun loadContacts() {
        viewModelScope.launch { runCatching { repo.emergencyContacts() }.onSuccess { _contacts.value = it } }
    }

    fun addContact(name: String, phoneInput: String) {
        val phone = Validators.normalizeContactPhone(phoneInput)
        if (name.isBlank()) { toast("Enter the contact's name"); return }
        if (phone == null) { toast("Enter a valid phone number"); return }
        run("Saving…") {
            repo.addEmergencyContact(name, phone)
            _contacts.value = repo.emergencyContacts()
            toast("Contact added")
        }
    }

    fun removeContact(id: String) {
        run(null) {
            repo.removeEmergencyContact(id)
            _contacts.value = repo.emergencyContacts()
        }
    }

    // ---------- Notifications & support ----------

    fun loadNotifications() {
        viewModelScope.launch { runCatching { repo.notifications() }.onSuccess { _notifications.value = it } }
    }

    fun markNotificationsRead() {
        if (_notifications.value.none { !it.isRead }) return
        viewModelScope.launch {
            runCatching { repo.markNotificationsRead() }
            _notifications.update { list -> list.map { it.copy(isRead = true) } }
        }
    }

    fun loadComplaints() {
        viewModelScope.launch { runCatching { repo.complaints() }.onSuccess { _complaints.value = it } }
    }

    fun raiseComplaint(rideId: String?, category: String, description: String, onDone: () -> Unit = {}) {
        if (description.trim().length < 10) { toast("Please describe the problem in a few words"); return }
        run("Sending…") {
            repo.raiseComplaint(rideId, category, description)
            _complaints.value = runCatching { repo.complaints() }.getOrDefault(_complaints.value)
            toast("We've received your complaint and will get back to you")
            onDone()
        }
    }

    // ---------- Helpers ----------

    fun toast(message: String) {
        _messages.tryEmit(message)
    }

    private fun run(label: String?, block: suspend () -> Unit) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = label ?: ""
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                // handled by the sessionExpired collector
            } catch (e: Exception) {
                toast(e.message ?: "Something went wrong. Please try again.")
            } finally {
                _busy.value = null
            }
        }
    }

    override fun onCleared() {
        app.realtime.stop()
        super.onCleared()
    }
}

/** Android 13+ needs runtime permission for trip notifications. */
val needsNotificationPermission: Boolean get() = Build.VERSION.SDK_INT >= 33
