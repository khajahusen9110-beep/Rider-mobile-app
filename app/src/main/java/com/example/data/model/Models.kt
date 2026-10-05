package com.example.data.model

data class UserSession(
    val userId: String,
    val phone: String,
    val fullName: String,
    val accessToken: String,
    val refreshToken: String? = null
)

data class UserProfile(
    val id: String,
    val phone: String = "",
    val fullName: String = "",
    val role: String = "customer",
    val isSuspended: Boolean = false,
    val suspensionReason: String? = null,
    val suspendedUntil: String? = null,
    val referralCode: String = ""
)

data class VehicleFare(
    val vehicleTypeId: String,
    val name: String,
    val iconEmoji: String,
    val capacityLabel: String,
    val baseFare: Double,
    val distanceFare: Double,
    val tollCharge: Double,
    val discountAmount: Double,
    val finalFare: Double,
    val advanceAmount: Double,
    val routePriced: Boolean
)

data class LegalDoc(
    val key: String,
    val title: String,
    val content: String
)

data class PaymentItem(
    val id: String,
    val rideId: String,
    val amount: Double,
    val status: String,
    val paymentType: String, // "full", "advance", "balance"
    val gateway: String? = null,
    val createdAt: String? = null
)

data class RideItem(
    val id: String,
    val customerId: String = "",
    val driverId: String? = null,
    val vehicleTypeId: String = "",
    val status: String = "pending_payment", // pending_payment, requested, accepted, arrived, ongoing, completed, cancelled
    val startOtp: String? = null,
    val pickupAddress: String = "",
    val dropAddress: String = "",
    val pickupLat: Double = 0.0,
    val pickupLng: Double = 0.0,
    val dropLat: Double = 0.0,
    val dropLng: Double = 0.0,
    val distanceKm: Double = 0.0,
    val durationMin: Double = 0.0,
    val finalFare: Double = 0.0,
    val advanceAmount: Double = 0.0,
    val paymentOption: String = "full", // "full" or "advance"
    val goodsDescription: String? = null,
    val goodsWeightKg: Double? = null,
    val passengerCount: Int = 1,
    val promoCode: String? = null,
    val requestedAt: String? = null,
    val scheduledAt: String? = null,
    val cancelReason: String? = null
)

data class RideParticipantInfo(
    val driverName: String? = null,
    val driverPhone: String? = null,
    val driverRating: Double = 4.8,
    val vehiclePlate: String? = null,
    val vehicleMake: String? = null,
    val vehicleModel: String? = null,
    val vehicleColor: String? = null
)

data class DriverLocation(
    val driverId: String,
    val lat: Double,
    val lng: Double,
    val isOnline: Boolean = true,
    val updatedAt: String? = null
)

data class ReturnTripOffer(
    val id: String,
    val driverName: String,
    val vehicleName: String,
    val normalFare: Double,
    val discountedFare: Double,
    val savings: Double
)

data class WalletInfo(
    val balance: Double = 0.0
)

data class WalletTx(
    val id: String,
    val amount: Double,
    val type: String, // "credit", "debit"
    val description: String,
    val createdAt: String
)

data class RefundItem(
    val id: String,
    val rideId: String? = null,
    val amount: Double,
    val status: String,
    val reason: String? = null,
    val createdAt: String
)

data class RideInvoice(
    val rideId: String,
    val pickupAddress: String,
    val dropAddress: String,
    val baseFare: Double,
    val distanceFare: Double,
    val tollCharge: Double,
    val discountAmount: Double,
    val finalFare: Double,
    val advancePaid: Double,
    val balancePaid: Double,
    val driverName: String?,
    val vehicleInfo: String?,
    val date: String
)
