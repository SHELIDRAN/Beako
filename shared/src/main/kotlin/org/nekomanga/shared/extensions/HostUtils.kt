package org.nekomanga.shared.extensions

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The helper functions Mangayomi gives scripts (crypto, packed JS, dates). */
internal object HostUtils {

    /** AES-CBC with a UTF-8 key and IV; base64 in and out. */
    fun cryptoHandler(text: String, iv: String, key: String, encrypt: Boolean): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
            SecretKeySpec(key.toByteArray(), "AES"),
            IvParameterSpec(iv.toByteArray()),
        )
        return if (encrypt) Base64.getEncoder().encodeToString(cipher.doFinal(text.toByteArray()))
        else String(cipher.doFinal(Base64.getDecoder().decode(text)))
    }

    /**
     * CryptoJS's passphrase format: "Salted__" + salt, key and IV from OpenSSL's EVP_BytesToKey.
     */
    fun decryptAESCryptoJS(encrypted: String, passphrase: String): String {
        val data = Base64.getDecoder().decode(encrypted.trim())
        val salt = data.copyOfRange(8, 16)
        val (key, iv) = evpBytesToKey(passphrase.toByteArray(), salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return String(cipher.doFinal(data.copyOfRange(16, data.size)))
    }

    fun encryptAESCryptoJS(plain: String, passphrase: String): String {
        val salt = ByteArray(8).also { java.security.SecureRandom().nextBytes(it) }
        val (key, iv) = evpBytesToKey(passphrase.toByteArray(), salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val body = cipher.doFinal(plain.toByteArray())
        return Base64.getEncoder().encodeToString("Salted__".toByteArray() + salt + body)
    }

    fun decryptAESGCM(encrypted: String, keyHex: String, ivHex: String, tagHex: String): String {
        val data = Base64.getDecoder().decode(encrypted) + hex(tagHex)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(hex(keyHex), "AES"),
            GCMParameterSpec(128, hex(ivHex)),
        )
        return String(cipher.doFinal(data))
    }

    private fun hex(text: String): ByteArray =
        text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun evpBytesToKey(password: ByteArray, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val md5 = MessageDigest.getInstance("MD5")
        var block = ByteArray(0)
        var out = ByteArray(0)
        while (out.size < 48) {
            md5.reset()
            block = md5.digest(block + password + salt)
            out += block
        }
        return out.copyOfRange(0, 32) to out.copyOfRange(32, 48)
    }

    /** Dean Edwards' `eval(function(p,a,c,k,e,d)…)` packer, as video and image hosts use it. */
    fun unpackJs(packed: String): String {
        val match =
            Regex(
                    """}\('(.*)',\s*(\d+|\[\]),\s*(\d+),\s*'(.*?)'\.split\('\|'\)""",
                    RegexOption.DOT_MATCHES_ALL,
                )
                .find(packed) ?: return ""
        val payload = match.groupValues[1].replace("\\'", "'")
        val radix = match.groupValues[2].toIntOrNull() ?: 62
        val count = match.groupValues[3].toInt()
        val words = match.groupValues[4].split('|')
        if (words.size != count) return ""
        return Regex("""\b\w+\b""").replace(payload) { m ->
            val index = decode(m.value, radix)
            words.getOrNull(index)?.takeIf { it.isNotEmpty() } ?: m.value
        }
    }

    private fun decode(word: String, radix: Int): Int {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return if (radix <= 36) word.toIntOrNull(radix) ?: -1
        else
            word.fold(0) { acc, c ->
                alphabet.indexOf(c).let { if (it < 0) return -1 else acc * radix + it }
            }
    }

    /**
     * Dates in [format] (Java/ICU pattern) as epoch-millisecond strings; "" when unparseable.
     * Mangayomi locales are written `en_us`; relative dates ("3 days ago", "yesterday") count from
     * [now], as Mangayomi reads them.
     */
    fun parseDates(
        values: List<String>,
        format: String,
        locale: String,
        now: Long = System.currentTimeMillis(),
    ): List<String> {
        val tag = locale.ifBlank { "en" }.replace('_', '-')
        val parser = runCatching {
            SimpleDateFormat(format, Locale.forLanguageTag(tag))
        }
            .getOrNull()
        return values.map { value ->
            relative(value, now)?.toString()
                ?: parser?.let {
                    runCatching { it.parse(value.trim())?.time?.toString() }.getOrNull()
                }
                ?: ""
        }
    }

    private val ago =
        Regex("""(\d+|an?|one)\s+(sec|min|hour|day|week|month|year)""", RegexOption.IGNORE_CASE)

    private fun relative(value: String, now: Long): Long? {
        val text = value.trim().lowercase()
        if (text == "today" || text == "just now") return now
        if (text == "yesterday") return now - DAY
        if (!text.contains("ago")) return null
        val match = ago.find(text) ?: return null
        val count = match.groupValues[1].toLongOrNull() ?: 1L
        val unit =
            when (match.groupValues[2]) {
                "sec" -> 1_000L
                "min" -> 60_000L
                "hour" -> 3_600_000L
                "day" -> DAY
                "week" -> 7 * DAY
                "month" -> 30 * DAY
                else -> 365 * DAY
            }
        return now - count * unit
    }

    private const val DAY = 86_400_000L
}
