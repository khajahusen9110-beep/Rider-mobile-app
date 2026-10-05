package com.example.data.supabase

import android.util.Log
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseService {

    companion object {
        const val BASE_URL = "https://rhftkhfabmrziobhopnr.supabase.co"
        const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJoZnRraGZhYm1yemlvYmhvcG5yIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNDA3OTgsImV4cCI6MjEwNTkxNjc5OH0.A23YNHmOexCfWv70a1TEi62Sx6cePl6WnoK2NPMPYhQ"
        private const val TAG = "SupabaseService"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun buildRequest(
        url: String,
        method: String = "GET",
        bodyJson: String? = null,
        userToken: String? = null
    ): Request {
        val authHeader = if (!userToken.isNullOrBlank()) "Bearer $userToken" else "Bearer $ANON_KEY"
        val builder = Request.Builder()
            .url(url)
            .addHeader("apikey", ANON_KEY)
            .addHeader("Authorization", authHeader)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")

        if (method == "POST") {
            val body = (bodyJson ?: "{}").toRequestBody(jsonMediaType)
            builder.post(body)
        } else if (method == "PATCH") {
            val body = (bodyJson ?: "{}").toRequestBody(jsonMediaType)
            builder.patch(body)
        } else {
            builder.get()
        }

        return builder.build()
    }

    private fun extractErrorMessage(errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return "Unknown error occurred"
        return try {
            val json = JSONObject(errorBody)
            when {
                json.has("message") -> json.getString("message")
                json.has("error_description") -> json.getString("error_description")
                json.has("msg") -> json.getString("msg")
                json.has("error") -> json.getString("error")
                json.has("details") -> json.getString("details")
                else -> errorBody
            }
        } catch (_: Exception) {
            errorBody
        }
    }

    // 1. Auth - Send OTP
    suspend fun sendOtp(phone: String, fullName: String, deviceId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("phone", phone)
                put("data", JSONObject().apply {
                    put("full_name", fullName)
                    put("role", "customer")
                    put("device_id", deviceId)
                })
            }
            val request = buildRequest("$BASE_URL/auth/v1/otp", "POST", body.toString())
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success("OTP sent successfully to $phone")
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Auth - Verify OTP
    suspend fun verifyOtp(phone: String, token: String): Result<UserSession> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("phone", phone)
                put("token", token)
                put("type", "sms")
            }
            val request = buildRequest("$BASE_URL/auth/v1/verify", "POST", body.toString())
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(respBody)
                val accessToken = json.optString("access_token", "")
                val refreshToken = json.optString("refresh_token", null)
                val userObj = json.optJSONObject("user")
                val userId = userObj?.optString("id", "") ?: ""
                val metadata = userObj?.optJSONObject("user_metadata")
                val fullName = metadata?.optString("full_name", "") ?: ""

                Result.success(UserSession(
                    userId = userId,
                    phone = phone,
                    fullName = fullName,
                    accessToken = accessToken,
                    refreshToken = refreshToken
                ))
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Profile check
    suspend fun getProfile(userId: String, token: String?): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/rest/v1/profiles?id=eq.$userId&select=*"
            val request = buildRequest(url, "GET", userToken = token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respBody)
                if (array.length() > 0) {
                    val p = array.getJSONObject(0)
                    val profile = UserProfile(
                        id = p.optString("id", userId),
                        phone = p.optString("phone", ""),
                        fullName = p.optString("full_name", ""),
                        role = p.optString("role", "customer"),
                        isSuspended = p.optBoolean("is_suspended", false),
                        suspensionReason = p.optString("suspension_reason", null),
                        suspendedUntil = p.optString("suspended_until", null),
                        referralCode = p.optString("referral_code", "")
                    )
                    Result.success(profile)
                } else {
                    Result.success(UserProfile(id = userId))
                }
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // T&C Legal document
    suspend fun getLegalDocument(key: String, token: String? = null): Result<LegalDoc> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/rest/v1/legal_documents?key=eq.$key&select=*"
            val request = buildRequest(url, "GET", userToken = token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respBody)
                if (array.length() > 0) {
                    val doc = array.getJSONObject(0)
                    Result.success(LegalDoc(
                        key = doc.optString("key", key),
                        title = doc.optString("title", "Terms and Conditions"),
                        content = doc.optString("content", "")
                    ))
                } else {
                    Result.success(LegalDoc(
                        key = key,
                        title = "Terms & Conditions",
                        content = "By booking a ride or cargo vehicle through GoRide & Cargo, you agree to fair usage, timely payment, and driver safety protocols. Cancellation after 20% advance booking is non-refundable. Full payment cancellations will retain a 20% processing fee. Drivers are independent providers."
                    ))
                }
            } else {
                // Fallback graceful default if table row empty
                Result.success(LegalDoc(
                    key = key,
                    title = "Terms & Conditions",
                    content = "By booking a ride or cargo vehicle, you agree to all terms and conditions."
                ))
            }
        } catch (_: Exception) {
            Result.success(LegalDoc(
                key = key,
                title = "Terms & Conditions",
                content = "Terms and conditions accepted for GoRide platform services."
            ))
        }
    }

    // 3. Preview Fares RPC
    suspend fun previewFares(
        category: String,
        distanceKm: Double,
        pickupLat: Double,
        pickupLng: Double,
        dropLat: Double,
        dropLng: Double,
        goodsWeightKg: Double? = null,
        promoCode: String? = null,
        token: String? = null
    ): Result<List<VehicleFare>> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("p_category", category)
                put("p_distance_km", distanceKm)
                put("p_pickup_lat", pickupLat)
                put("p_pickup_lng", pickupLng)
                put("p_drop_lat", dropLat)
                put("p_drop_lng", dropLng)
                if (goodsWeightKg != null) put("p_goods_weight_kg", goodsWeightKg) else put("p_goods_weight_kg", JSONObject.NULL)
                if (!promoCode.isNullOrBlank()) put("p_promo_code", promoCode) else put("p_promo_code", JSONObject.NULL)
            }

            val request = buildRequest("$BASE_URL/rest/v1/rpc/preview_fares", "POST", body.toString(), token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val fares = mutableListOf<VehicleFare>()
                val array = JSONArray(respBody)
                for (i in 0 until array.length()) {
                    val row = array.getJSONObject(i)
                    fares.add(VehicleFare(
                        vehicleTypeId = row.optString("vehicle_type_id", row.optString("id", "veh_$i")),
                        name = row.optString("name", "Standard"),
                        iconEmoji = row.optString("icon_emoji", if (category == "passenger") "🚗" else "🚛"),
                        capacityLabel = row.optString("capacity_label", "Standard"),
                        baseFare = row.optDouble("base_fare", 0.0),
                        distanceFare = row.optDouble("distance_fare", 0.0),
                        tollCharge = row.optDouble("toll_charge", 0.0),
                        discountAmount = row.optDouble("discount_amount", 0.0),
                        finalFare = row.optDouble("final_fare", 0.0),
                        advanceAmount = row.optDouble("advance_amount", 0.0),
                        routePriced = row.optBoolean("route_priced", false)
                    ))
                }
                Result.success(fares)
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 4. Request Ride RPC
    suspend fun requestRide(
        pickupLat: Double,
        pickupLng: Double,
        pickupAddress: String,
        dropLat: Double,
        dropLng: Double,
        dropAddress: String,
        vehicleTypeId: String,
        termsAccepted: Boolean,
        paymentOption: String, // "full" or "advance"
        distanceKm: Double,
        durationMin: Double,
        promoCode: String? = null,
        goodsDescription: String? = null,
        goodsWeightKg: Double? = null,
        passengerCount: Int = 1,
        scheduledAt: String? = null,
        token: String? = null
    ): Result<RideItem> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("p_pickup_lat", pickupLat)
                put("p_pickup_lng", pickupLng)
                put("p_pickup_address", pickupAddress)
                put("p_drop_lat", dropLat)
                put("p_drop_lng", dropLng)
                put("p_drop_address", dropAddress)
                put("p_vehicle_type_id", vehicleTypeId)
                put("p_terms_accepted", termsAccepted)
                put("p_payment_option", paymentOption)
                put("p_distance_km", distanceKm)
                put("p_duration_min", durationMin)
                if (!promoCode.isNullOrBlank()) put("p_promo_code", promoCode) else put("p_promo_code", JSONObject.NULL)
                if (!goodsDescription.isNullOrBlank()) put("p_goods_description", goodsDescription) else put("p_goods_description", JSONObject.NULL)
                if (goodsWeightKg != null) put("p_goods_weight_kg", goodsWeightKg) else put("p_goods_weight_kg", JSONObject.NULL)
                put("p_passenger_count", passengerCount)
                if (!scheduledAt.isNullOrBlank()) put("p_scheduled_at", scheduledAt) else put("p_scheduled_at", JSONObject.NULL)
            }

            val request = buildRequest("$BASE_URL/rest/v1/rpc/request_ride", "POST", body.toString(), token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(respBody)
                val ride = RideItem(
                    id = json.optString("id", ""),
                    customerId = json.optString("customer_id", ""),
                    driverId = json.optString("driver_id", null),
                    vehicleTypeId = json.optString("vehicle_type_id", vehicleTypeId),
                    status = json.optString("status", "pending_payment"),
                    startOtp = json.optString("start_otp", null),
                    pickupAddress = json.optString("pickup_address", pickupAddress),
                    dropAddress = json.optString("drop_address", dropAddress),
                    pickupLat = json.optDouble("pickup_lat", pickupLat),
                    pickupLng = json.optDouble("pickup_lng", pickupLng),
                    dropLat = json.optDouble("drop_lat", dropLat),
                    dropLng = json.optDouble("drop_lng", dropLng),
                    distanceKm = json.optDouble("distance_km", distanceKm),
                    durationMin = json.optDouble("duration_min", durationMin),
                    finalFare = json.optDouble("final_fare", 0.0),
                    advanceAmount = json.optDouble("advance_amount", 0.0),
                    paymentOption = json.optString("payment_option", paymentOption),
                    requestedAt = json.optString("requested_at", null)
                )
                Result.success(ride)
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 5. Payments for Ride
    suspend fun getPaymentsForRide(rideId: String, token: String? = null): Result<List<PaymentItem>> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/rest/v1/payments?ride_id=eq.$rideId&order=created_at.desc&select=*"
            val request = buildRequest(url, "GET", userToken = token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val list = mutableListOf<PaymentItem>()
                val array = JSONArray(respBody)
                for (i in 0 until array.length()) {
                    val p = array.getJSONObject(i)
                    list.add(PaymentItem(
                        id = p.optString("id", ""),
                        rideId = p.optString("ride_id", rideId),
                        amount = p.optDouble("amount", 0.0),
                        status = p.optString("status", "processing"),
                        paymentType = p.optString("payment_type", "full"),
                        gateway = p.optString("gateway", "Razorpay"),
                        createdAt = p.optString("created_at", "")
                    ))
                }
                Result.success(list)
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 6. Ride Details (status, start_otp, etc.)
    suspend fun getRide(rideId: String, token: String? = null): Result<RideItem> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/rest/v1/rides?id=eq.$rideId&select=*"
            val request = buildRequest(url, "GET", userToken = token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val array = JSONArray(respBody)
                if (array.length() > 0) {
                    val r = array.getJSONObject(0)
                    val item = RideItem(
                        id = r.optString("id", rideId),
                        customerId = r.optString("customer_id", ""),
                        driverId = r.optString("driver_id", null),
                        vehicleTypeId = r.optString("vehicle_type_id", ""),
                        status = r.optString("status", "pending_payment"),
                        startOtp = r.optString("start_otp", null),
                        pickupAddress = r.optString("pickup_address", ""),
                        dropAddress = r.optString("drop_address", ""),
                        pickupLat = r.optDouble("pickup_lat", 0.0),
                        pickupLng = r.optDouble("pickup_lng", 0.0),
                        dropLat = r.optDouble("drop_lat", 0.0),
                        dropLng = r.optDouble("drop_lng", 0.0),
                        distanceKm = r.optDouble("distance_km", 0.0),
                        durationMin = r.optDouble("duration_min", 0.0),
                        finalFare = r.optDouble("final_fare", 0.0),
                        advanceAmount = r.optDouble("advance_amount", 0.0),
                        paymentOption = r.optString("payment_option", "full"),
                        requestedAt = r.optString("requested_at", null),
                        scheduledAt = r.optString("scheduled_at", null),
                        cancelReason = r.optString("cancel_reason", null)
                    )
                    Result.success(item)
                } else {
                    Result.failure(Exception("Ride not found"))
                }
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Driver & vehicle info RPC
    suspend fun getRideParticipantInfo(rideId: String, token: String? = null): Result<RideParticipantInfo> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply { put("p_ride_id", rideId) }
            val request = buildRequest("$BASE_URL/rest/v1/rpc/get_ride_participant_info", "POST", body.toString(), token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(respBody)
                val info = RideParticipantInfo(
                    driverName = json.optString("driver_name", json.optString("name", "Assigned Driver")),
                    driverPhone = json.optString("driver_phone", json.optString("phone", "+919876543210")),
                    driverRating = json.optDouble("driver_rating", json.optDouble("rating", 4.9)),
                    vehiclePlate = json.optString("vehicle_plate", json.optString("plate_number", "MH02 AB 1234")),
                    vehicleMake = json.optString("vehicle_make", json.optString("make", "Toyota")),
                    vehicleModel = json.optString("vehicle_model", json.optString("model", "Etios")),
                    vehicleColor = json.optString("vehicle_color", json.optString("color", "Silver"))
                )
                Result.success(info)
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Driver status / location
    suspend fun getDriverStatus(driverId: String, token: String? = null): Result<DriverLocation> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/rest/v1/driver_status?driver_id=eq.$driverId&select=*"
            val request = buildRequest(url, "GET", userToken = token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val array = JSONArray(respBody)
                if (array.length() > 0) {
                    val d = array.getJSONObject(0)
                    Result.success(DriverLocation(
                        driverId = driverId,
                        lat = d.optDouble("current_lat", d.optDouble("lat", 0.0)),
                        lng = d.optDouble("current_lng", d.optDouble("lng", 0.0)),
                        isOnline = d.optBoolean("is_online", true),
                        updatedAt = d.optString("updated_at", null)
                    ))
                } else {
                    Result.failure(Exception("Driver location not available"))
                }
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 7. Cancel ride RPC
    suspend fun updateRideStatus(
        rideId: String,
        status: String,
        cancelReason: String? = null,
        token: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("p_ride_id", rideId)
                put("p_status", status)
                if (!cancelReason.isNullOrBlank()) put("p_cancel_reason", cancelReason) else put("p_cancel_reason", JSONObject.NULL)
            }
            val request = buildRequest("$BASE_URL/rest/v1/rpc/update_ride_status", "POST", body.toString(), token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 8. Booking History
    suspend fun getCustomerRides(customerId: String, token: String? = null): Result<List<RideItem>> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/rest/v1/rides?customer_id=eq.$customerId&order=requested_at.desc&select=*"
            val request = buildRequest(url, "GET", userToken = token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val list = mutableListOf<RideItem>()
                val array = JSONArray(respBody)
                for (i in 0 until array.length()) {
                    val r = array.getJSONObject(i)
                    list.add(RideItem(
                        id = r.optString("id", ""),
                        customerId = customerId,
                        driverId = r.optString("driver_id", null),
                        vehicleTypeId = r.optString("vehicle_type_id", ""),
                        status = r.optString("status", "pending_payment"),
                        startOtp = r.optString("start_otp", null),
                        pickupAddress = r.optString("pickup_address", "Pickup location"),
                        dropAddress = r.optString("drop_address", "Destination"),
                        distanceKm = r.optDouble("distance_km", 0.0),
                        durationMin = r.optDouble("duration_min", 0.0),
                        finalFare = r.optDouble("final_fare", 0.0),
                        advanceAmount = r.optDouble("advance_amount", 0.0),
                        paymentOption = r.optString("payment_option", "full"),
                        requestedAt = r.optString("requested_at", ""),
                        cancelReason = r.optString("cancel_reason", null)
                    ))
                }
                Result.success(list)
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Ride Invoice RPC
    suspend fun getRideInvoice(rideId: String, token: String? = null): Result<RideInvoice> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply { put("p_ride_id", rideId) }
            val request = buildRequest("$BASE_URL/rest/v1/rpc/get_ride_invoice", "POST", body.toString(), token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val j = JSONObject(respBody)
                Result.success(RideInvoice(
                    rideId = rideId,
                    pickupAddress = j.optString("pickup_address", "Pickup Point"),
                    dropAddress = j.optString("drop_address", "Drop Point"),
                    baseFare = j.optDouble("base_fare", 0.0),
                    distanceFare = j.optDouble("distance_fare", 0.0),
                    tollCharge = j.optDouble("toll_charge", 0.0),
                    discountAmount = j.optDouble("discount_amount", 0.0),
                    finalFare = j.optDouble("final_fare", 0.0),
                    advancePaid = j.optDouble("advance_paid", 0.0),
                    balancePaid = j.optDouble("balance_paid", 0.0),
                    driverName = j.optString("driver_name", "Driver"),
                    vehicleInfo = j.optString("vehicle_info", "Commercial Vehicle"),
                    date = j.optString("date", "")
                ))
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 9. Submit Rating RPC
    suspend fun submitRating(
        rideId: String,
        ratedUser: String,
        rating: Int,
        comment: String,
        token: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("p_ride_id", rideId)
                put("p_rated_user", ratedUser)
                put("p_rating", rating)
                put("p_comment", comment)
            }
            val request = buildRequest("$BASE_URL/rest/v1/rpc/submit_rating", "POST", body.toString(), token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 10. Wallet balance
    suspend fun getWallet(userId: String, token: String? = null): Result<WalletInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/rest/v1/wallets?user_id=eq.$userId&select=balance"
            val request = buildRequest(url, "GET", userToken = token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val array = JSONArray(respBody)
                val balance = if (array.length() > 0) array.getJSONObject(0).optDouble("balance", 0.0) else 0.0
                Result.success(WalletInfo(balance = balance))
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Wallet Transactions
    suspend fun getWalletTransactions(userId: String, token: String? = null): Result<List<WalletTx>> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/rest/v1/wallet_transactions?user_id=eq.$userId&order=created_at.desc&select=*"
            val request = buildRequest(url, "GET", userToken = token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val list = mutableListOf<WalletTx>()
                val array = JSONArray(respBody)
                for (i in 0 until array.length()) {
                    val t = array.getJSONObject(i)
                    list.add(WalletTx(
                        id = t.optString("id", ""),
                        amount = t.optDouble("amount", 0.0),
                        type = t.optString("type", "credit"),
                        description = t.optString("description", "Wallet operation"),
                        createdAt = t.optString("created_at", "")
                    ))
                }
                Result.success(list)
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Apply Referral Code RPC
    suspend fun applyReferralCode(code: String, token: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply { put("p_code", code) }
            val request = buildRequest("$BASE_URL/rest/v1/rpc/apply_referral_code", "POST", body.toString(), token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success("Referral code applied! Bonus credited to wallet.")
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 11. Refunds
    suspend fun getRefunds(customerId: String, token: String? = null): Result<List<RefundItem>> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/rest/v1/refunds?customer_id=eq.$customerId&order=created_at.desc&select=*"
            val request = buildRequest(url, "GET", userToken = token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val list = mutableListOf<RefundItem>()
                val array = JSONArray(respBody)
                for (i in 0 until array.length()) {
                    val r = array.getJSONObject(i)
                    list.add(RefundItem(
                        id = r.optString("id", ""),
                        rideId = r.optString("ride_id", null),
                        amount = r.optDouble("amount", 0.0),
                        status = r.optString("status", "processed"),
                        reason = r.optString("reason", null),
                        createdAt = r.optString("created_at", "")
                    ))
                }
                Result.success(list)
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Raise Complaint RPC
    suspend fun raiseComplaint(
        rideId: String,
        againstId: String?,
        category: String,
        description: String,
        token: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("p_ride_id", rideId)
                if (!againstId.isNullOrBlank()) put("p_against", againstId) else put("p_against", JSONObject.NULL)
                put("p_category", category)
                put("p_description", description)
            }
            val request = buildRequest("$BASE_URL/rest/v1/rpc/raise_complaint", "POST", body.toString(), token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success("Complaint submitted. Our support team will investigate promptly.")
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 12. Bonus - Return Ride (outstation discounts) RPC
    suspend fun findReturnTrips(
        pickupLat: Double,
        pickupLng: Double,
        dropLat: Double,
        dropLng: Double,
        radiusKm: Double = 10.0,
        token: String? = null
    ): Result<List<ReturnTripOffer>> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("p_pickup_lat", pickupLat)
                put("p_pickup_lng", pickupLng)
                put("p_drop_lat", dropLat)
                put("p_drop_lng", dropLng)
                put("p_radius_km", radiusKm)
            }
            val request = buildRequest("$BASE_URL/rest/v1/rpc/find_return_trips", "POST", body.toString(), token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val list = mutableListOf<ReturnTripOffer>()
                val array = JSONArray(respBody)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    list.add(ReturnTripOffer(
                        id = item.optString("id", "ret_$i"),
                        driverName = item.optString("driver_name", "Heading Back Driver"),
                        vehicleName = item.optString("vehicle_name", item.optString("vehicle_details", "Sedan / Hatchback")),
                        normalFare = item.optDouble("normal_fare", 0.0),
                        discountedFare = item.optDouble("discounted_fare", 0.0),
                        savings = item.optDouble("savings", 0.0)
                    ))
                }
                Result.success(list)
            } else {
                // If RPC not configured or no trips found
                Result.success(emptyList())
            }
        } catch (_: Exception) {
            Result.success(emptyList())
        }
    }

    // 13. Delete Account RPC
    suspend fun requestAccountDeletion(reason: String, token: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply { put("p_reason", reason) }
            val request = buildRequest("$BASE_URL/rest/v1/rpc/request_account_deletion", "POST", body.toString(), token)
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success("Account deleted successfully.")
            } else {
                Result.failure(Exception(extractErrorMessage(respBody)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
