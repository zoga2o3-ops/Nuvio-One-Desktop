package com.nuvio.app.core.diagnostics

import java.security.MessageDigest
import java.util.Locale

/** The same short, non-reversible account code the TV and mobile apps put in reports. */
internal object SupportCode {
    fun forUserId(userId: String): String {
        val normalized = userId.trim().lowercase(Locale.US)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
            .take(10)
            .uppercase(Locale.US)
    }
}
