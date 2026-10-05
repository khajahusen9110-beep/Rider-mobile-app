package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

// ---------- JSON helpers (org.json returns "null" strings and 0 for missing values) ----------

fun JSONObject.str(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

fun JSONObject.dbl(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

fun JSONObject.int(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key)

fun JSONObject.bool(key: String, default: Boolean = false): Boolean =
    if (!has(key) || isNull(key)) default else optBoolean(key, default)

fun JSONObject.instant(key: String): Instant? = str(key)?.let { parseInstant(it) }

fun parseInstant(value: String): Instant? =
    runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(value) }.getOrNull()
        // Postgres can send "2026-10-05 06:40:00+00"
        ?: runCatching { OffsetDateTime.parse(value.replace(' ', 'T').let { if (Regex("[+-]\\d\\d$").containsMatchIn(it)) "$it:00" else it }).toInstant() }.getOrNull()

inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(transform) }

// ---------- Profile ----------

data class Profile(
    val id: String,
    val role: String,
    val fullName: String,
    val phone: String?,
    val avatarUrl: String?,
    val isSuspended: Boolean,
    val suspendedUntil: Instant?,
    val suspensionReason: String?,
    val referralCode: String?,
    val isDeleted: Boolean,
) {
    val isCustomer get() = role == "customer"

    /** Suspension that is still in force (an expired timed suspension no longer counts). */
    fun isCurrentlySuspended(now: Instant = Instant.now()) =
        isSuspended && (suspendedUntil == null || suspendedUntil.isAfter(now))

    /** A first-time account still has the placeholder name from signup. */
    val needsName get() = fullName.isBlank() || fullName == "New User"

    companion object {
        const val COLUMNS = "id,role,full_name,phone,avatar_url,is_suspended,suspended_until,suspension_reason,referral_code,is_deleted"

        fun from(j: JSONObject) = Profile(
            id = j.optString("id"),
            role = j.str("role") ?: "customer",
            fullName = j.str("full_name") ?: "",
            phone = j.str("phone"),
            avatarUrl = j.str("avatar_url"),
            isSuspended = j.bool("is_suspended"),
            suspendedUntil = j.instant("suspended_until"),
            suspensionReason = j.str("suspension_reason"),
            referralCode = j.str("referral_code"),
            isDeleted = j.bool("is_deleted"),
        )
    }
}

// ---------- Places ----------

data class Place(val lat: Double, val lng: Double, val address: String) {
    /** Straight-line distance in km. */
    fun distanceTo(other: Place): Double {
        val r = 6371.0
        val dLat = Math.toRadians(other.lat - lat)
        val dLng = Math.toRadians(other.lng - lng)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(other.lat)) * Math.sin(dLng / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(a))
    }
}

data class SavedPlace(val id: String, val label: String, val place: Place) {
    companion object {
        fun from(j: JSONObject) = SavedPlace(
            id = j.optString("id"),
            label = j.str("label") ?: "Saved",
            place = Place(j.optDouble("lat"), j.optDouble("lng"), j.str("address") ?: ""),
        )
    }
}

data class EmergencyContact(val id: String, val name: String, val phone: String) {
    companion object {
        fun from(j: JSONObject) = EmergencyContact(j.optString("id"), j.str("name") ?: "", j.str("phone") ?: "")
    }
}

// ---------- Booking ----------

enum class VehicleCategory(val key: String, val label: String) {
    PASSENGER("passenger", "Ride"), GOODS("goods", "Goods");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.key == value } ?: PASSENGER
    }
}

enum class PaymentOption(val key: String, val label: String) {
    FULL("full", "Pay full fare"), ADVANCE("advance", "Pay 20% now");
}

/** One row of `preview_fares`. */
data class FareOption(
    val vehicleTypeId: String,
    val name: String,
    val icon: String,
    val capacityLabel: String?,
    val maxCapacity: Double?,
    val capacityUnit: String?,
    val fits: Boolean,
    val baseFare: Double,
    val distanceFare: Double,
    val tollCharge: Double,
    val discount: Double,
    val finalFare: Double,
    val advanceAmount: Double,
    val routePriced: Boolean,
) {
    companion object {
        fun from(j: JSONObject) = FareOption(
            vehicleTypeId = j.optString("vehicle_type_id"),
            name = j.str("name") ?: "Vehicle",
            icon = j.str("icon_emoji") ?: "🚗",
            capacityLabel = j.str("capacity_label"),
            maxCapacity = j.dbl("max_capacity"),
            capacityUnit = j.str("capacity_unit"),
            fits = j.bool("fits", true),
            baseFare = j.dbl("base_fare") ?: 0.0,
            distanceFare = j.dbl("distance_fare") ?: 0.0,
            tollCharge = j.dbl("toll_charge") ?: 0.0,
            discount = j.dbl("discount_amount") ?: 0.0,
            finalFare = j.dbl("final_fare") ?: 0.0,
            advanceAmount = j.dbl("advance_amount") ?: 0.0,
            routePriced = j.bool("route_priced"),
        )
    }
}

/** A car marker from `nearby_cars` (position rounded to ~100 m by the backend). */
data class NearbyCar(val vehicleTypeId: String, val lat: Double, val lng: Double, val distanceKm: Double, val etaMin: Int) {
    companion object {
        fun from(j: JSONObject) = NearbyCar(
            vehicleTypeId = j.optString("vehicle_type_id"),
            lat = j.optDouble("lat"),
            lng = j.optDouble("lng"),
            distanceKm = j.dbl("distance_km") ?: 0.0,
            etaMin = j.int("eta_min") ?: 0,
        )
    }
}

data class ReturnOffer(
    val offerId: String,
    val driverName: String,
    val vehicleTypeName: String,
    val plateNumber: String?,
    val discountPercent: Double,
    val normalFare: Double,
    val discountedFare: Double,
    val savings: Double,
    val expiresAt: Instant?,
) {
    companion object {
        fun from(j: JSONObject) = ReturnOffer(
            offerId = j.optString("offer_id"),
            driverName = j.str("driver_name") ?: "Driver",
            vehicleTypeName = j.str("vehicle_type_name") ?: "",
            plateNumber = j.str("plate_number"),
            discountPercent = j.dbl("discount_percent") ?: 0.0,
            normalFare = j.dbl("normal_fare") ?: 0.0,
            discountedFare = j.dbl("discounted_fare") ?: 0.0,
            savings = j.dbl("savings") ?: 0.0,
            expiresAt = j.instant("expires_at"),
        )
    }
}

// ---------- Rides ----------

enum class RideStatus(val key: String, val label: String) {
    PENDING_PAYMENT("pending_payment", "Awaiting payment"),
    REQUESTED("requested", "Finding a driver"),
    ACCEPTED("accepted", "Driver on the way"),
    ARRIVED("arrived", "Driver has arrived"),
    ONGOING("ongoing", "On trip"),
    COMPLETED("completed", "Completed"),
    CANCELLED("cancelled", "Cancelled");

    val isOpen get() = this != COMPLETED && this != CANCELLED
    val hasDriver get() = this == ACCEPTED || this == ARRIVED || this == ONGOING
    val canCancel get() = this == PENDING_PAYMENT || this == REQUESTED || this == ACCEPTED || this == ARRIVED

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.key == value } ?: REQUESTED
    }
}

data class Ride(
    val id: String,
    val customerId: String,
    val driverId: String?,
    val pickup: Place,
    val drop: Place,
    val status: RideStatus,
    val distanceKm: Double?,
    val durationMin: Double?,
    val fareEstimate: Double?,
    val fareFinal: Double?,
    val discount: Double,
    val promoCode: String?,
    val cancelReason: String?,
    val cancelledBy: String?,
    val requestedAt: Instant?,
    val acceptedAt: Instant?,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val cancelledAt: Instant?,
    val scheduledAt: Instant?,
    val vehicleTypeId: String?,
    val returnOfferId: String?,
    val passengerCount: Int?,
    val goodsDescription: String?,
    val goodsWeightKg: Double?,
) {
    val fare get() = fareFinal ?: fareEstimate ?: 0.0
    val isScheduled get() = scheduledAt != null

    companion object {
        // start_otp is never selected: the start code is read from ride_otps.
        const val COLUMNS = "id,customer_id,driver_id,pickup_lat,pickup_lng,pickup_address,drop_lat,drop_lng,drop_address," +
            "status,distance_km,estimated_duration_min,fare_estimate,fare_final,discount_amount,promo_code,cancel_reason," +
            "cancelled_by,requested_at,accepted_at,started_at,completed_at,cancelled_at,scheduled_at," +
            "requested_vehicle_type_id,return_offer_id,passenger_count,goods_description,goods_weight_kg"

        fun from(j: JSONObject) = Ride(
            id = j.optString("id"),
            customerId = j.optString("customer_id"),
            driverId = j.str("driver_id"),
            pickup = Place(j.optDouble("pickup_lat"), j.optDouble("pickup_lng"), j.str("pickup_address") ?: "Pickup"),
            drop = Place(j.optDouble("drop_lat"), j.optDouble("drop_lng"), j.str("drop_address") ?: "Drop"),
            status = RideStatus.from(j.str("status")),
            distanceKm = j.dbl("distance_km"),
            durationMin = j.dbl("estimated_duration_min"),
            fareEstimate = j.dbl("fare_estimate"),
            fareFinal = j.dbl("fare_final"),
            discount = j.dbl("discount_amount") ?: 0.0,
            promoCode = j.str("promo_code"),
            cancelReason = j.str("cancel_reason"),
            cancelledBy = j.str("cancelled_by"),
            requestedAt = j.instant("requested_at"),
            acceptedAt = j.instant("accepted_at"),
            startedAt = j.instant("started_at"),
            completedAt = j.instant("completed_at"),
            cancelledAt = j.instant("cancelled_at"),
            scheduledAt = j.instant("scheduled_at"),
            vehicleTypeId = j.str("requested_vehicle_type_id"),
            returnOfferId = j.str("return_offer_id"),
            passengerCount = j.int("passenger_count"),
            goodsDescription = j.str("goods_description"),
            goodsWeightKg = j.dbl("goods_weight_kg"),
        )
    }
}

enum class PaymentStatus { PENDING, PROCESSING, COMPLETED, REFUNDED;
    companion object {
        fun from(value: String?) = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDING
    }
}

data class Payment(
    val id: String,
    val rideId: String,
    val amount: Double,
    val type: String,
    val status: PaymentStatus,
    val method: String,
    val paidAt: Instant?,
    val createdAt: Instant?,
) {
    val isDue get() = status == PaymentStatus.PROCESSING
    val typeLabel get() = when (type) {
        "advance" -> "Advance (20%)"
        "balance" -> "Balance"
        else -> "Full fare"
    }

    companion object {
        const val COLUMNS = "id,ride_id,amount,payment_type,status,method,paid_at,created_at"

        fun from(j: JSONObject) = Payment(
            id = j.optString("id"),
            rideId = j.optString("ride_id"),
            amount = j.dbl("amount") ?: 0.0,
            type = j.str("payment_type") ?: "full",
            status = PaymentStatus.from(j.str("status")),
            method = j.str("method") ?: "online",
            paidAt = j.instant("paid_at"),
            createdAt = j.instant("created_at"),
        )
    }
}

data class Refund(
    val id: String,
    val rideId: String?,
    val amount: Double,
    val reason: String?,
    val status: String,
    val refundTo: String,
    val gatewayStatus: String?,
    val requestedAt: Instant?,
    val processedAt: Instant?,
) {
    /** Where the refund stands, in words the customer understands. */
    val statusLabel get() = when {
        status == "rejected" -> "Rejected"
        status == "pending" -> "Under review"
        refundTo == "wallet" -> "Credited to wallet"
        gatewayStatus == "processed" -> "Refunded to bank"
        gatewayStatus == "failed" -> "Credited to wallet"
        else -> "Refund initiated (5-7 days)"
    }

    companion object {
        const val COLUMNS = "id,ride_id,amount,reason,status,refund_to,gateway_status,requested_at,processed_at"

        fun from(j: JSONObject) = Refund(
            id = j.optString("id"),
            rideId = j.str("ride_id"),
            amount = j.dbl("amount") ?: 0.0,
            reason = j.str("reason"),
            status = j.str("status") ?: "pending",
            refundTo = j.str("refund_to") ?: "wallet",
            gatewayStatus = j.str("gateway_status"),
            requestedAt = j.instant("requested_at"),
            processedAt = j.instant("processed_at"),
        )
    }
}

data class Participants(
    val driverName: String?,
    val driverPhone: String?,
    val driverRating: Double?,
    val vehiclePlate: String?,
    val vehicleTypeName: String?,
    val vehicleMake: String?,
    val vehicleModel: String?,
) {
    val vehicleLabel get() = listOfNotNull(vehicleMake, vehicleModel).joinToString(" ").ifBlank { vehicleTypeName ?: "" }

    companion object {
        fun from(j: JSONObject) = Participants(
            driverName = j.str("driver_name"),
            driverPhone = j.str("driver_phone"),
            driverRating = j.dbl("driver_rating"),
            vehiclePlate = j.str("vehicle_plate"),
            vehicleTypeName = j.str("vehicle_type_name"),
            vehicleMake = j.str("vehicle_make"),
            vehicleModel = j.str("vehicle_model"),
        )
    }
}

/** The driver's live position, readable while the trip is active. */
data class DriverLocation(val lat: Double, val lng: Double, val updatedAt: Instant?) {
    companion object {
        fun from(j: JSONObject): DriverLocation? {
            val lat = j.dbl("current_lat") ?: return null
            val lng = j.dbl("current_lng") ?: return null
            return DriverLocation(lat, lng, j.instant("updated_at"))
        }
    }
}

data class InvoiceLine(val label: String, val amount: Double, val status: String, val method: String, val paidAt: Instant?)

data class Invoice(
    val rideId: String,
    val vehicleType: String?,
    val driverName: String?,
    val vehiclePlate: String?,
    val distanceKm: Double?,
    val fare: Double?,
    val discount: Double,
    val promoCode: String?,
    val requestedAt: Instant?,
    val completedAt: Instant?,
    val payments: List<InvoiceLine>,
) {
    companion object {
        fun from(j: JSONObject) = Invoice(
            rideId = j.optString("ride_id"),
            vehicleType = j.str("vehicle_type"),
            driverName = j.str("driver_name"),
            vehiclePlate = j.str("vehicle_plate"),
            distanceKm = j.dbl("distance_km"),
            fare = j.dbl("fare_final"),
            discount = j.dbl("discount_amount") ?: 0.0,
            promoCode = j.str("promo_code"),
            requestedAt = j.instant("requested_at"),
            completedAt = j.instant("completed_at"),
            payments = j.optJSONArray("payments")?.mapObjects {
                InvoiceLine(
                    label = when (it.str("type")) { "advance" -> "Advance"; "balance" -> "Balance"; else -> "Fare" },
                    amount = it.dbl("amount") ?: 0.0,
                    status = it.str("status") ?: "",
                    method = it.str("method") ?: "",
                    paidAt = it.instant("paid_at"),
                )
            }.orEmpty(),
        )
    }
}

/** What the Razorpay checkout needs, from the razorpay-order function. */
data class CheckoutOrder(
    val paymentId: String,
    val keyId: String,
    val orderId: String,
    val amountPaise: Long,
    val currency: String,
    val name: String,
    val description: String,
    val prefillName: String,
    val prefillContact: String,
) {
    companion object {
        fun from(paymentId: String, j: JSONObject) = CheckoutOrder(
            paymentId = paymentId,
            keyId = j.optString("key_id"),
            orderId = j.optString("order_id"),
            amountPaise = j.optLong("amount"),
            currency = j.str("currency") ?: "INR",
            name = j.str("name") ?: "GoRide",
            description = j.str("description") ?: "Ride fare",
            prefillName = j.optJSONObject("prefill")?.str("name") ?: "",
            prefillContact = j.optJSONObject("prefill")?.str("contact") ?: "",
        )
    }
}

// ---------- Wallet, notifications, misc ----------

data class WalletTxn(val id: String, val amount: Double, val type: String, val reason: String?, val createdAt: Instant?) {
    val isCredit get() = type == "credit"

    companion object {
        fun from(j: JSONObject) = WalletTxn(
            id = j.optString("id"),
            amount = j.dbl("amount") ?: 0.0,
            type = j.str("type") ?: "credit",
            reason = j.str("reason"),
            createdAt = j.instant("created_at"),
        )
    }
}

data class AppNotification(
    val id: String,
    val title: String,
    val body: String?,
    val type: String,
    val isRead: Boolean,
    val createdAt: Instant?,
    val rideId: String?,
) {
    companion object {
        fun from(j: JSONObject) = AppNotification(
            id = j.optString("id"),
            title = j.str("title") ?: "",
            body = j.str("body"),
            type = j.str("type") ?: "general",
            isRead = j.bool("is_read"),
            createdAt = j.instant("created_at"),
            rideId = j.optJSONObject("data")?.str("ride_id"),
        )
    }
}

data class LegalDoc(val title: String, val content: String, val version: Int) {
    companion object {
        fun from(j: JSONObject) = LegalDoc(j.str("title") ?: "Terms & Conditions", j.str("content") ?: "", j.int("version") ?: 1)
    }
}

data class Complaint(
    val id: String,
    val rideId: String?,
    val category: String,
    val description: String,
    val status: String,
    val adminResponse: String?,
    val createdAt: Instant?,
) {
    companion object {
        fun from(j: JSONObject) = Complaint(
            id = j.optString("id"),
            rideId = j.str("ride_id"),
            category = j.str("category") ?: "",
            description = j.str("description") ?: "",
            status = j.str("status") ?: "open",
            adminResponse = j.str("admin_response"),
            createdAt = j.instant("created_at"),
        )
    }
}
