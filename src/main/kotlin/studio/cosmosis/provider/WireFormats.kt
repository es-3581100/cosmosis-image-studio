package studio.cosmosis.provider

import java.nio.file.Files
import java.util.Base64

object OpenAiWire {
    fun responsesImageTool(r:GenerationRequest):String {
        val size=if(r.width!=null&&r.height!=null) "${r.width}x${r.height}" else r.metadata["size"]?:"auto"
        return JsonUtil.obj(
            "type" to JsonUtil.quote("image_generation"),"model" to JsonUtil.quote(r.model),
            "action" to JsonUtil.quote(if(r.references.isEmpty())"generate" else "auto"),
            "quality" to r.quality?.let(JsonUtil::quote),"size" to JsonUtil.quote(size),
            "background" to JsonUtil.quote(if(r.transparent)"transparent" else "auto"),
            "output_format" to JsonUtil.quote(r.outputFormat),
            "output_compression" to r.metadata["compression"]
        )
    }

    fun generationBody(r:GenerationRequest):String {
        val size=if(r.width!=null&&r.height!=null) "${r.width}x${r.height}" else r.metadata["size"] ?: "auto"
        return JsonUtil.obj(
            "model" to JsonUtil.quote(r.model), "prompt" to JsonUtil.quote(r.prompt), "n" to r.variants.toString(),
            "size" to JsonUtil.quote(size), "quality" to r.quality?.let(JsonUtil::quote),
            "output_format" to JsonUtil.quote(r.outputFormat), "background" to JsonUtil.quote(if(r.transparent)"transparent" else "auto"),
            "output_compression" to r.metadata["compression"]
        )
    }
}

object GeminiWire {
    fun interactionsBody(r:GenerationRequest):String {
        val input=mutableListOf<String>()
        r.references.forEach { ref ->
            val data=Base64.getEncoder().encodeToString(Files.readAllBytes(ref.path))
            input += JsonUtil.obj("type" to JsonUtil.quote("image"),"mime_type" to JsonUtil.quote(ref.mime),"data" to JsonUtil.quote(data))
        }
        input += JsonUtil.obj("type" to JsonUtil.quote("text"),"text" to JsonUtil.quote(r.prompt))

        val fmt=JsonUtil.obj(
            "type" to JsonUtil.quote("image"),
            "mime_type" to JsonUtil.quote(if(r.outputFormat=="jpeg")"image/jpeg" else "image/png"),
            "aspect_ratio" to r.aspectRatio?.let(JsonUtil::quote),
            "image_size" to r.metadata["imageSize"]?.let(JsonUtil::quote)
        )
        val thinking=r.metadata["thinkingLevel"]?.takeIf{it.isNotBlank()}?.let {
            JsonUtil.obj("thinking_level" to JsonUtil.quote(it))
        }
        val tools=when(r.metadata["searchGrounding"]?.lowercase()){
            "1","true","yes","on" -> JsonUtil.arr(listOf(JsonUtil.obj("type" to JsonUtil.quote("google_search"))))
            else -> null
        }
        val store=r.metadata["store"]?.lowercase()?.let { when(it){"1","true","yes","on"->"true";"0","false","no","off"->"false";else->null} }
        return JsonUtil.obj(
            "model" to JsonUtil.quote(r.model),
            "input" to JsonUtil.arr(input),
            "previous_interaction_id" to r.previousResponseId?.takeIf{it.isNotBlank()}?.let(JsonUtil::quote),
            "response_format" to fmt,
            "tools" to tools,
            "generation_config" to thinking,
            "store" to store
        )
    }
}
