package studio.cosmosis.provider

data class JsonImageBlock(val data:String,val mimeType:String="image/png")

object JsonUtil {
    fun esc(s:String):String = buildString { s.forEach { c -> when(c){ '"'->append("\\\""); '\\'->append("\\\\"); '\n'->append("\\n"); '\r'->append("\\r"); '\t'->append("\\t"); else-> if(c.code<32) append("\\u%04x".format(c.code)) else append(c) } } }
    fun quote(s:String)="\"${esc(s)}\""
    fun obj(vararg values:Pair<String,String?>):String = values.filter{it.second!=null}.joinToString(prefix="{",postfix="}") { quote(it.first)+":"+it.second }
    fun arr(values:Iterable<String>):String=values.joinToString(prefix="[",postfix="]")
    fun stringField(json:String,name:String):String? {
        val r=Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
        return r.find(json)?.groupValues?.get(1)?.let(::unescape)
    }
    fun allStringFields(json:String,name:String):List<String> {
        val r=Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
        return r.findAll(json).map { unescape(it.groupValues[1]) }.toList()
    }

    /**
     * Extract Gemini Interactions image content blocks without treating unrelated
     * fields named `data` (tool payloads, metadata, etc.) as generated images.
     * The Interactions wire format keeps image content blocks flat, so a bounded
     * object scan is sufficient and avoids adding a JSON dependency to the core.
     */
    fun imageBlocks(json:String):List<JsonImageBlock> {
        val found=linkedMapOf<String,JsonImageBlock>()
        val flatObject=Regex("\\{[^{}]{0,400000}\\}")
        for(match in flatObject.findAll(json)) {
            val objectJson=match.value
            val type=stringField(objectJson,"type")
            val isTypedImage=type=="image"
            val looksLikeOutputImage=objectJson.contains("\"data\"") &&
                (objectJson.contains("\"mime_type\"") || objectJson.contains("\"mimeType\"")) &&
                !objectJson.contains("\"type\":\"function_result\"")
            if(!isTypedImage && !looksLikeOutputImage) continue
            val data=stringField(objectJson,"data")?.takeIf{it.isNotBlank()} ?: continue
            val mime=stringField(objectJson,"mime_type") ?: stringField(objectJson,"mimeType") ?: "image/png"
            if(mime.startsWith("image/")) found.putIfAbsent(data,JsonImageBlock(data,mime))
        }
        return found.values.toList()
    }

    private fun unescape(value:String):String = buildString {
        var i=0
        while(i<value.length){
            val c=value[i]
            if(c!='\\' || i+1>=value.length){ append(c);i++;continue }
            val n=value[i+1]
            when(n){
                'n'->append('\n');'r'->append('\r');'t'->append('\t');'"'->append('"');'\\'->append('\\');'/'->append('/')
                'b'->append('\b');'f'->append('\u000c')
                'u'-> if(i+5<value.length){ value.substring(i+2,i+6).toIntOrNull(16)?.let{append(it.toChar());i+=4} ?: append('u') } else append('u')
                else->append(n)
            }
            i+=2
        }
    }
}
