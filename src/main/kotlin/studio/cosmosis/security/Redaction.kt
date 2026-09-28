package studio.cosmosis.security

import java.security.MessageDigest

object Redaction {
    private val prefixed = listOf(
        Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[A-Za-z0-9._-]+"),
        Regex("(?i)((?:api[_-]?key|token|secret|password)\\s*[:=]\\s*)[^\\s,;]+")
    )
    private val bare = listOf(Regex("sk-[A-Za-z0-9_-]{12,}"))
    fun sanitize(text:String):String {
        var out=text
        prefixed.forEach { r -> out=r.replace(out){m -> m.groupValues.getOrElse(1){""}+"[REDACTED]"} }
        bare.forEach { r -> out=r.replace(out,"[REDACTED]") }
        return out
    }
    fun fingerprint(secret:String):String { val h=MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()); return h.take(4).joinToString(""){"%02x".format(it)} }
}
