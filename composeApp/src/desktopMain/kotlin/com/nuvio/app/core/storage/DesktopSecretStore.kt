package com.nuvio.app.core.storage

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.util.Base64

/**
 * Small at-rest secret helper for desktop preferences.
 *
 * On Windows the value is wrapped with DPAPI (`CryptProtectData`) so another user account or a
 * copied properties file cannot read it. Elsewhere it falls back to plain text, matching the
 * previous behaviour instead of failing to start.
 */
internal object DesktopSecretStore {
    private const val Prefix = "dpapi:"
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    fun protect(value: String): String {
        if (value.isBlank() || !windows) return value
        return runCatching {
            val input = DataBlob().apply { setData(value.toByteArray(Charsets.UTF_8)) }
            val output = DataBlob()
            val ok = Crypt32.INSTANCE.CryptProtectData(
                input,
                null,
                null,
                null,
                null,
                CryptProtectUiForbidden,
                output,
            )
            if (!ok || output.pbData == null) return value
            val bytes = output.pbData!!.getByteArray(0, output.cbData)
            Kernel32.INSTANCE.LocalFree(output.pbData)
            Prefix + Base64.getEncoder().encodeToString(bytes)
        }.getOrDefault(value)
    }

    fun unprotect(value: String): String {
        if (!value.startsWith(Prefix)) return value
        return runCatching {
            val bytes = Base64.getDecoder().decode(value.removePrefix(Prefix))
            val input = DataBlob().apply { setData(bytes) }
            val output = DataBlob()
            val ok = Crypt32.INSTANCE.CryptUnprotectData(
                input,
                null,
                null,
                null,
                null,
                CryptProtectUiForbidden,
                output,
            )
            if (!ok || output.pbData == null) return ""
            val plain = output.pbData!!.getByteArray(0, output.cbData)
            Kernel32.INSTANCE.LocalFree(output.pbData)
            plain.toString(Charsets.UTF_8)
        }.getOrDefault("")
    }

    private const val CryptProtectUiForbidden = 0x1

    @Structure.FieldOrder("cbData", "pbData")
    class DataBlob : Structure() {
        @JvmField
        var cbData: Int = 0

        @JvmField
        var pbData: Pointer? = null

        fun setData(data: ByteArray) {
            cbData = data.size
            pbData = Memory(data.size.coerceAtLeast(1).toLong())
            if (data.isNotEmpty()) pbData!!.write(0, data, 0, data.size)
        }
    }

    private interface Crypt32 : Library {
        fun CryptProtectData(
            dataIn: DataBlob,
            description: String?,
            entropy: DataBlob?,
            reserved: Pointer?,
            prompt: Pointer?,
            flags: Int,
            dataOut: DataBlob,
        ): Boolean

        fun CryptUnprotectData(
            dataIn: DataBlob,
            description: Pointer?,
            entropy: DataBlob?,
            reserved: Pointer?,
            prompt: Pointer?,
            flags: Int,
            dataOut: DataBlob,
        ): Boolean

        companion object {
            val INSTANCE: Crypt32 by lazy { Native.load("crypt32", Crypt32::class.java) }
        }
    }

    private interface Kernel32 : Library {
        fun LocalFree(memory: Pointer?): Pointer?

        companion object {
            val INSTANCE: Kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) }
        }
    }
}
