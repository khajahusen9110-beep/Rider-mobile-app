package com.example.data

/** Input rules shared by the screens. They mirror the checks the backend RPCs enforce. */
object Validators {
    private val INDIAN_MOBILE = Regex("^[6-9]\\d{9}$")
    private val CONTACT_PHONE = Regex("^\\+?[0-9]{10,15}$")
    private val PROMO = Regex("^[A-Z0-9_-]{3,20}$")

    /** Returns the number in E.164 form (+91XXXXXXXXXX), or null if it is not a valid Indian mobile number. */
    fun normalizePhone(input: String): String? {
        var digits = input.filter { it.isDigit() }
        if (digits.length == 12 && digits.startsWith("91")) digits = digits.drop(2)
        if (digits.length == 11 && digits.startsWith("0")) digits = digits.drop(1)
        return if (INDIAN_MOBILE.matches(digits)) "+91$digits" else null
    }

    fun isValidOtp(code: String) = code.length == 6 && code.all { it.isDigit() }

    /** Emergency contacts: Indian mobiles are stored as +91…, other numbers as typed (digits and a leading +). */
    fun normalizeContactPhone(input: String): String? {
        normalizePhone(input)?.let { return it }
        val cleaned = input.trim().let { if (it.startsWith("+")) "+" + it.drop(1).filter(Char::isDigit) else it.filter(Char::isDigit) }
        return cleaned.takeIf { CONTACT_PHONE.matches(it) }
    }

    fun normalizePromo(code: String): String = code.trim().uppercase()

    fun isValidPromo(code: String) = PROMO.matches(normalizePromo(code))

    fun isValidStartCode(code: String) = code.length == 4 && code.all { it.isDigit() }
}
