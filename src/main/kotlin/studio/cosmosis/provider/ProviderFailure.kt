package studio.cosmosis.provider

import studio.cosmosis.security.Redaction

class ProviderHttpException(
    val providerName:String,
    val status:Int,
    val endpoint:String,
    responseBody:String
):RuntimeException(
    buildString {
        append(providerName).append(" HTTP ").append(status)
        val detail=ProviderFailureText.clean(responseBody,800)
        if(detail.isNotBlank())append(": ").append(detail)
    }
){
    val retryable:Boolean = status==408 || status==425 || status==429 || status in 500..599
}

object ProviderFailureText {
    fun clean(value:String?,limit:Int=600):String =
        Redaction.sanitize(value.orEmpty())
            .replace(Regex("\\s+")," ")
            .trim()
            .take(limit)

    fun describe(t:Throwable,limit:Int=800):String {
        val parts=generateSequence(t as Throwable?){it?.cause}
            .take(4)
            .mapNotNull { cause ->
                val message=clean(cause.message,limit)
                val type=cause.javaClass.simpleName.ifBlank{cause.javaClass.name.substringAfterLast('.')}
                when {
                    message.isBlank() -> type
                    message.startsWith(type) -> message
                    else -> "$type: $message"
                }
            }
            .distinct()
            .toList()
        return clean(parts.joinToString(" <- ").ifBlank{"Unknown provider failure"},limit)
    }
}
