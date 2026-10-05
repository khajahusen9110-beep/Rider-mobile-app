package com.example.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ModelsTest {

    @Test
    fun `postgres timestamps parse in every format`() {
        val expected = Instant.parse("2026-10-05T06:40:00Z")
        assertEquals(expected, parseInstant("2026-10-05T06:40:00+00:00"))
        assertEquals(expected, parseInstant("2026-10-05T06:40:00Z"))
        assertEquals(expected, parseInstant("2026-10-05 06:40:00+00"))
        assertNull(parseInstant("not a date"))
    }

    @Test
    fun `ride parses and never needs the start code column`() {
        assertFalse(Ride.COLUMNS.split(',').contains("start_otp"))
        val ride = Ride.from(
            JSONObject()
                .put("id", "r1").put("customer_id", "c1").put("driver_id", JSONObject.NULL)
                .put("pickup_lat", 17.4).put("pickup_lng", 78.4).put("pickup_address", "Ameerpet")
                .put("drop_lat", 17.45).put("drop_lng", 78.38).put("drop_address", "Hitech City")
                .put("status", "pending_payment").put("fare_estimate", 240.0).put("discount_amount", 20)
        )
        assertNull(ride.driverId)
        assertEquals(RideStatus.PENDING_PAYMENT, ride.status)
        assertEquals(240.0, ride.fare, 0.001)
        assertTrue(ride.status.isOpen)
        assertTrue(ride.status.canCancel)
        assertFalse(ride.status.hasDriver)
    }

    @Test
    fun `ride status helpers`() {
        assertTrue(RideStatus.ONGOING.hasDriver)
        assertFalse(RideStatus.ONGOING.canCancel)
        assertFalse(RideStatus.COMPLETED.isOpen)
        assertEquals(RideStatus.REQUESTED, RideStatus.from("unknown"))
    }

    @Test
    fun `fare option reads preview_fares rows`() {
        val fare = FareOption.from(
            JSONObject().put("vehicle_type_id", "v1").put("name", "Mini").put("fits", false)
                .put("final_fare", 180.5).put("advance_amount", 36.1).put("discount_amount", 20)
        )
        assertEquals("Mini", fare.name)
        assertFalse(fare.fits)
        assertEquals(180.5, fare.finalFare, 0.001)
        assertEquals("🚗", fare.icon)
    }

    @Test
    fun `refund status is explained to the customer`() {
        fun refund(status: String, to: String, gateway: String?) =
            Refund("1", "r", 100.0, null, status, to, gateway, null, null)
        assertEquals("Under review", refund("pending", "source", null).statusLabel)
        assertEquals("Credited to wallet", refund("processed", "wallet", null).statusLabel)
        assertEquals("Refunded to bank", refund("processed", "source", "processed").statusLabel)
        assertEquals("Refund initiated (5-7 days)", refund("processed", "source", "queued").statusLabel)
        assertEquals("Credited to wallet", refund("processed", "source", "failed").statusLabel)
    }

    @Test
    fun `due payment is the one awaiting the customer`() {
        val due = Payment.from(JSONObject().put("id", "p").put("ride_id", "r").put("amount", 50).put("status", "processing").put("payment_type", "advance"))
        assertTrue(due.isDue)
        assertEquals("Advance (20%)", due.typeLabel)
    }

    @Test
    fun `checkout order reads the razorpay-order response`() {
        val order = CheckoutOrder.from(
            "pay1",
            JSONObject().put("key_id", "rzp_test_x").put("order_id", "order_1").put("amount", 24000).put("currency", "INR")
                .put("prefill", JSONObject().put("name", "Asha").put("contact", "+919876543210")),
        )
        assertEquals(24000L, order.amountPaise)
        assertEquals("Asha", order.prefillName)
        assertEquals("pay1", order.paymentId)
    }

    @Test
    fun `place distance is roughly right`() {
        val a = Place(17.385, 78.4867, "Hyderabad")
        val b = Place(17.4435, 78.3772, "Hitech City")
        val km = a.distanceTo(b)
        assertTrue("got $km", km in 12.0..14.0)
    }

    @Test
    fun `profile placeholder name needs replacing`() {
        val p = Profile.from(JSONObject().put("id", "u").put("role", "customer").put("full_name", "New User"))
        assertTrue(p.needsName)
        assertTrue(p.isCustomer)
        assertNotNull(p.id)
    }
}
