package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `phone numbers are normalised to E164`() {
        assertEquals("+919876543210", Validators.normalizePhone("9876543210"))
        assertEquals("+919876543210", Validators.normalizePhone("+91 98765 43210"))
        assertEquals("+919876543210", Validators.normalizePhone("09876543210"))
        assertNull(Validators.normalizePhone("12345"))
        assertNull(Validators.normalizePhone("5876543210"))
    }

    @Test
    fun `emergency contacts accept Indian and international numbers`() {
        assertEquals("+919876543210", Validators.normalizeContactPhone("98765 43210"))
        assertEquals("+447700900123", Validators.normalizeContactPhone("+44 7700 900123"))
        assertNull(Validators.normalizeContactPhone("12345"))
    }

    @Test
    fun `promo codes are upper-cased and checked`() {
        assertEquals("SAVE20", Validators.normalizePromo("  save20 "))
        assertTrue(Validators.isValidPromo("save20"))
        assertTrue(Validators.isValidPromo("NEW-USER_1"))
        assertFalse(Validators.isValidPromo("ab"))
        assertFalse(Validators.isValidPromo("bad code"))
    }

    @Test
    fun `otp and start code lengths`() {
        assertTrue(Validators.isValidOtp("123456"))
        assertFalse(Validators.isValidOtp("12345"))
        assertFalse(Validators.isValidOtp("12a456"))
        assertTrue(Validators.isValidStartCode("0420"))
        assertFalse(Validators.isValidStartCode("420"))
    }
}
