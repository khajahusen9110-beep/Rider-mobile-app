package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Every backend call the customer app makes. Business rules live in the database RPCs. */
class RiderRepository(private val api: SupabaseClient, private val session: SessionStore) {

    val isLoggedIn get() = session.isLoggedIn
    val userId get() = session.userId
    val sessionExpired get() = api.sessionExpired

    private fun me() = session.userId ?: throw SessionExpiredException()

    // ---------- Auth & profile ----------

    suspend fun sendOtp(phone: String, fullName: String?) = api.sendOtp(phone, fullName)

    suspend fun verifyOtp(phone: String, code: String) = api.verifyOtp(phone, code)

    suspend fun signOut() {
        session.pushToken?.let { token -> runCatching { api.rpc("unregister_device_token", JSONObject().put("p_token", token)) } }
        api.signOut()
    }

    suspend fun accessToken() = api.validAccessToken()

    suspend fun profile(): Profile? =
        api.select("profiles?id=eq.${me()}&select=${Profile.COLUMNS}").optJSONObject(0)?.let(Profile::from)

    suspend fun updateName(name: String) =
        api.update("profiles", "id=eq.${me()}", JSONObject().put("full_name", name.trim()))

    suspend fun uploadAvatar(bytes: ByteArray): String {
        val path = "${me()}/avatar-${System.currentTimeMillis()}.jpg"
        api.upload(SupabaseConfig.AVATARS_BUCKET, path, bytes, "image/jpeg")
        val url = SupabaseConfig.publicUrl(SupabaseConfig.AVATARS_BUCKET, path)
        api.update("profiles", "id=eq.${me()}", JSONObject().put("avatar_url", url))
        return url
    }

    suspend fun registerDeviceToken(token: String) {
        api.rpc("register_device_token", JSONObject().put("p_token", token).put("p_platform", "android"))
        session.pushToken = token
    }

    suspend fun applyReferral(code: String) {
        api.rpc("apply_referral_code", JSONObject().put("p_code", code.trim().uppercase()))
    }

    suspend fun requestAccountDeletion(reason: String?) {
        api.rpc("request_account_deletion", JSONObject().put("p_reason", reason?.takeIf { it.isNotBlank() } ?: JSONObject.NULL))
    }

    suspend fun legalDoc(key: String = "customer_tnc"): LegalDoc? =
        api.select("legal_documents?key=eq.$key&select=title,content,version").optJSONObject(0)?.let(LegalDoc::from)

    // ---------- Booking ----------

    suspend fun previewFares(
        category: VehicleCategory,
        pickup: Place,
        drop: Place,
        passengers: Int?,
        goodsWeightKg: Double?,
        promoCode: String?,
    ): List<FareOption> {
        val params = JSONObject()
            .put("p_category", category.key)
            .put("p_distance_km", JSONObject.NULL)
            .put("p_pickup_lat", pickup.lat).put("p_pickup_lng", pickup.lng)
            .put("p_drop_lat", drop.lat).put("p_drop_lng", drop.lng)
            .put("p_passenger_count", passengers ?: JSONObject.NULL)
            .put("p_goods_weight_kg", goodsWeightKg ?: JSONObject.NULL)
            .put("p_promo_code", promoCode ?: JSONObject.NULL)
        return rows(api.rpc("preview_fares", params), FareOption::from)
    }

    /** The distance the backend prices the trip at (road estimate). */
    suspend fun tripDistanceKm(pickup: Place, drop: Place): Double? =
        api.rpc(
            "ride_distance_km",
            JSONObject().put("p_lat", pickup.lat).put("p_lng", pickup.lng).put("p_dlat", drop.lat).put("p_dlng", drop.lng)
                .put("p_client_km", JSONObject.NULL),
        ).trim().toDoubleOrNull()

    suspend fun nearbyCars(at: Place, vehicleTypeId: String? = null): List<NearbyCar> =
        rows(
            api.rpc(
                "nearby_cars",
                JSONObject().put("p_lat", at.lat).put("p_lng", at.lng).put("p_vehicle_type_id", vehicleTypeId ?: JSONObject.NULL),
            ),
            NearbyCar::from,
        )

    suspend fun returnOffers(pickup: Place, drop: Place): List<ReturnOffer> =
        rows(
            api.rpc(
                "find_return_trips",
                JSONObject().put("p_pickup_lat", pickup.lat).put("p_pickup_lng", pickup.lng)
                    .put("p_drop_lat", drop.lat).put("p_drop_lng", drop.lng).put("p_radius_km", 10),
            ),
            ReturnOffer::from,
        )

    suspend fun requestRide(request: BookingRequest): Ride {
        val params = JSONObject()
            .put("p_pickup_lat", request.pickup.lat).put("p_pickup_lng", request.pickup.lng)
            .put("p_pickup_address", request.pickup.address)
            .put("p_drop_lat", request.drop.lat).put("p_drop_lng", request.drop.lng)
            .put("p_drop_address", request.drop.address)
            .put("p_vehicle_type_id", request.vehicleTypeId)
            .put("p_terms_accepted", true)
            .put("p_payment_option", request.paymentOption.key)
            .put("p_distance_km", JSONObject.NULL)
            .put("p_duration_min", request.durationMin ?: JSONObject.NULL)
            .put("p_promo_code", request.promoCode ?: JSONObject.NULL)
            .put("p_goods_description", request.goodsDescription ?: JSONObject.NULL)
            .put("p_goods_weight_kg", request.goodsWeightKg ?: JSONObject.NULL)
            .put("p_passenger_count", request.passengers ?: JSONObject.NULL)
            .put("p_scheduled_at", request.scheduledAt?.toString() ?: JSONObject.NULL)
        return api.rpcObject("request_ride", params)?.let(Ride::from) ?: throw ApiException("Booking failed. Please try again.")
    }

    suspend fun bookReturnTrip(offerId: String, request: BookingRequest): Ride {
        val params = JSONObject()
            .put("p_offer_id", offerId)
            .put("p_pickup_lat", request.pickup.lat).put("p_pickup_lng", request.pickup.lng)
            .put("p_pickup_address", request.pickup.address)
            .put("p_drop_lat", request.drop.lat).put("p_drop_lng", request.drop.lng)
            .put("p_drop_address", request.drop.address)
            .put("p_terms_accepted", true)
            .put("p_payment_option", request.paymentOption.key)
            .put("p_distance_km", JSONObject.NULL)
            .put("p_duration_min", request.durationMin ?: JSONObject.NULL)
            .put("p_goods_description", request.goodsDescription ?: JSONObject.NULL)
            .put("p_goods_weight_kg", request.goodsWeightKg ?: JSONObject.NULL)
            .put("p_passenger_count", request.passengers ?: JSONObject.NULL)
        return api.rpcObject("book_return_trip", params)?.let(Ride::from) ?: throw ApiException("Booking failed. Please try again.")
    }

    // ---------- Rides ----------

    suspend fun ride(rideId: String): Ride? =
        api.select("rides?id=eq.$rideId&select=${Ride.COLUMNS}").optJSONObject(0)?.let(Ride::from)

    /** Open bookings, newest first. */
    suspend fun openRides(): List<Ride> =
        api.select("rides?customer_id=eq.${me()}&status=not.in.(completed,cancelled)&select=${Ride.COLUMNS}&order=requested_at.desc")
            .mapObjects(Ride::from)

    suspend fun rideHistory(limit: Int = 50): List<Ride> =
        api.select("rides?customer_id=eq.${me()}&select=${Ride.COLUMNS}&order=requested_at.desc&limit=$limit").mapObjects(Ride::from)

    suspend fun payments(rideId: String): List<Payment> =
        api.select("payments?ride_id=eq.$rideId&select=${Payment.COLUMNS}&order=created_at.asc").mapObjects(Payment::from)

    suspend fun refundsForRide(rideId: String): List<Refund> =
        api.select("refunds?ride_id=eq.$rideId&select=${Refund.COLUMNS}&order=requested_at.desc").mapObjects(Refund::from)

    suspend fun participants(rideId: String): Participants? =
        api.rpcObject("get_ride_participant_info", JSONObject().put("p_ride_id", rideId))?.let(Participants::from)

    suspend fun startCode(rideId: String): String? =
        api.rpc("get_ride_otp", JSONObject().put("p_ride_id", rideId)).trim().trim('"').takeIf { it.length == 4 }

    suspend fun driverLocation(driverId: String): DriverLocation? =
        api.select("driver_status?driver_id=eq.$driverId&select=current_lat,current_lng,updated_at").optJSONObject(0)
            ?.let(DriverLocation::from)

    suspend fun cancelRide(rideId: String, reason: String) {
        api.rpc(
            "update_ride_status",
            JSONObject().put("p_ride_id", rideId).put("p_status", "cancelled").put("p_cancel_reason", reason),
        )
    }

    suspend fun invoice(rideId: String): Invoice? =
        api.rpcObject("get_ride_invoice", JSONObject().put("p_ride_id", rideId))?.let(Invoice::from)

    suspend fun hasRated(rideId: String): Boolean =
        api.select("ratings?ride_id=eq.$rideId&rated_by=eq.${me()}&select=id").length() > 0

    suspend fun rateDriver(rideId: String, driverId: String, stars: Int, comment: String?) {
        api.rpc(
            "submit_rating",
            JSONObject().put("p_ride_id", rideId).put("p_rated_user", driverId).put("p_rating", stars)
                .put("p_comment", comment?.takeIf { it.isNotBlank() } ?: JSONObject.NULL),
        )
    }

    suspend fun triggerSos(rideId: String?, location: Place?) {
        api.rpc(
            "trigger_sos",
            JSONObject().put("p_ride_id", rideId ?: JSONObject.NULL)
                .put("p_lat", location?.lat ?: JSONObject.NULL).put("p_lng", location?.lng ?: JSONObject.NULL),
        )
    }

    // ---------- Payments ----------

    suspend fun createCheckout(paymentId: String): CheckoutOrder =
        CheckoutOrder.from(paymentId, api.function("razorpay-order", JSONObject().put("payment_id", paymentId)))

    suspend fun verifyCheckout(paymentId: String, orderId: String, razorpayPaymentId: String, signature: String) {
        api.function(
            "razorpay-verify",
            JSONObject().put("payment_id", paymentId).put("razorpay_order_id", orderId)
                .put("razorpay_payment_id", razorpayPaymentId).put("razorpay_signature", signature),
        )
    }

    suspend fun payWithWallet(paymentId: String) {
        api.rpc("pay_with_wallet", JSONObject().put("p_payment_id", paymentId))
    }

    // ---------- Wallet ----------

    suspend fun walletBalance(): Double =
        api.select("wallets?user_id=eq.${me()}&select=balance").optJSONObject(0)?.dbl("balance") ?: 0.0

    suspend fun walletTransactions(): List<WalletTxn> =
        api.select("wallet_transactions?user_id=eq.${me()}&select=id,amount,type,reason,created_at&order=created_at.desc&limit=100")
            .mapObjects(WalletTxn::from)

    suspend fun refunds(): List<Refund> =
        api.select("refunds?customer_id=eq.${me()}&select=${Refund.COLUMNS}&order=requested_at.desc&limit=50").mapObjects(Refund::from)

    // ---------- Saved places & safety ----------

    suspend fun savedPlaces(): List<SavedPlace> =
        api.select("saved_places?user_id=eq.${me()}&select=id,label,address,lat,lng&order=created_at.asc").mapObjects(SavedPlace::from)

    suspend fun addSavedPlace(label: String, place: Place) {
        api.insert(
            "saved_places",
            JSONObject().put("label", label.trim()).put("address", place.address).put("lat", place.lat).put("lng", place.lng),
            returnRows = false,
        )
    }

    suspend fun removeSavedPlace(id: String) = api.remove("saved_places", "id=eq.$id")

    suspend fun emergencyContacts(): List<EmergencyContact> =
        api.select("emergency_contacts?user_id=eq.${me()}&select=id,name,phone&order=created_at.asc").mapObjects(EmergencyContact::from)

    suspend fun addEmergencyContact(name: String, phone: String) {
        api.insert("emergency_contacts", JSONObject().put("name", name.trim()).put("phone", phone), returnRows = false)
    }

    suspend fun removeEmergencyContact(id: String) = api.remove("emergency_contacts", "id=eq.$id")

    // ---------- Notifications & support ----------

    suspend fun notifications(): List<AppNotification> =
        api.select("notifications?user_id=eq.${me()}&select=id,title,body,type,data,is_read,created_at&order=created_at.desc&limit=50")
            .mapObjects(AppNotification::from)

    suspend fun markNotificationsRead() =
        api.update("notifications", "user_id=eq.${me()}&is_read=eq.false", JSONObject().put("is_read", true))

    suspend fun complaints(): List<Complaint> =
        api.select("complaints?raised_by=eq.${me()}&select=id,ride_id,category,description,status,admin_response,created_at&order=created_at.desc")
            .mapObjects(Complaint::from)

    suspend fun raiseComplaint(rideId: String?, category: String, description: String) {
        api.rpc(
            "raise_complaint",
            JSONObject().put("p_ride_id", rideId ?: JSONObject.NULL).put("p_against", JSONObject.NULL)
                .put("p_category", category).put("p_description", description.trim()),
        )
    }
}

/** What the customer picked on the booking screen. */
data class BookingRequest(
    val pickup: Place,
    val drop: Place,
    val vehicleTypeId: String,
    val paymentOption: PaymentOption,
    val promoCode: String?,
    val passengers: Int?,
    val goodsWeightKg: Double?,
    val goodsDescription: String?,
    val scheduledAt: Instant?,
    val durationMin: Double?,
)

/** RPCs returning a set come back as a JSON array. */
private inline fun <T> rows(text: String, transform: (JSONObject) -> T): List<T> {
    val trimmed = text.trim()
    if (!trimmed.startsWith("[")) return emptyList()
    return JSONArray(trimmed).mapObjects(transform)
}
