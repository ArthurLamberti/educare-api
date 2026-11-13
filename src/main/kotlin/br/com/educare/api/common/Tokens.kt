package br.com.educare.api.common

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Random tokens for links sent by email. Only [hash] is stored; the token itself is only in the email. */
object Tokens {
    private val random = SecureRandom()

    /** 32 random bytes, base64url without padding. */
    fun newToken(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    /** SHA-256, hex. */
    fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
}
