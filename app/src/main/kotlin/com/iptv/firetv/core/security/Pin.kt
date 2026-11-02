package com.iptv.firetv.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

/** Salted, iterated SHA-256 (PBKDF2 with SHA-256 needs API 26; minSdk is 25). A PIN is a convenience lock, not a vault. */
object Pin {
    private const val ROUNDS = 20_000

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let { Base64.encodeToString(it, Base64.NO_WRAP) }

    fun hash(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        var d = (salt + pin).toByteArray(Charsets.UTF_8)
        repeat(ROUNDS) { d = md.digest(d) }
        return d.joinToString("") { "%02x".format(it) }
    }

    fun matches(pin: String, salt: String?, expected: String?): Boolean =
        salt != null && expected != null && MessageDigest.isEqual(hash(pin, salt).toByteArray(), expected.toByteArray())

    fun isValidFormat(pin: String) = pin.length in 4..6 && pin.all { it.isDigit() }
}

/** Throttles PIN guessing: 5 wrong attempts lock the PIN pad for 30 seconds. In-memory only. */
class PinThrottle(private val clock: () -> Long = System::currentTimeMillis) {
    private var failures = 0
    private var lockedUntil = 0L
    fun remainingLockSeconds(): Int = ((lockedUntil - clock()) / 1000).toInt().coerceAtLeast(0).let { if (lockedUntil > clock() && it == 0) 1 else it }
    fun onFailure() { if (++failures >= 5) { lockedUntil = clock() + 30_000; failures = 0 } }
    fun onSuccess() { failures = 0; lockedUntil = 0 }
}
