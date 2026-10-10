package studio.cosmosis.provider

data class JsonImageBlock(val data:String,val mimeType:String="image/png")

object JsonUtil {
    fun esc(s:String):String = buildString { s.forEach { c -> when(c){ '"'->append("\\\""); '\\'->append("\\\\"); '\n'->append("\\n"); '\r'->append("\\r"); '\t'->append("\\t"); else-> if(c.code<32) append("\\u%04x".format(c.code)) else append(c) } } }
    fun quote(s:String)="\"${esc(s)}\""
    fun obj(vararg values:Pair<String,String?>):String = values.filter{it.second!=null}.joinToString(prefix="{",postfix="}") { quote(it.first)+":"+it.second }
    fun arr(values:Iterable<String>):String=values.joinToString(prefix="[",postfix="]")
    fun stringField(json:String,name:String):String? =
        stringFields(json,name,firstOnly=true).firstOrNull()

    fun allStringFields(json:String,name:String):List<String> =
        stringFields(json,name,firstOnly=false)

    /**
     * Linear scanner for JSON string fields.
     *
     * Successful image responses can contain multi-megabyte base64 strings.
     * A repeated-regex capture can exhaust the JVM regex call stack on those
     * payloads, so field extraction is deliberately iterative.
     */
    private fun stringFields(json:String,name:String,firstOnly:Boolean):List<String> {
        val out=mutableListOf<String>()
        var i=0
        while(i<json.length){
            val quoteIndex=json.indexOf('"',i)
            if(quoteIndex<0)break
            val key=readJsonString(json,quoteIndex) ?: break
            var cursor=skipWhitespace(json,key.endExclusive)
            if(cursor<json.length && json[cursor]==':'){
                cursor=skipWhitespace(json,cursor+1)
                if(key.value==name && cursor<json.length && json[cursor]=='"'){
                    val value=readJsonString(json,cursor)
                    if(value!=null){
                        out+=value.value
                        if(firstOnly)return out
                        i=value.endExclusive
                        continue
                    }
                }
            }
            i=key.endExclusive.coerceAtLeast(quoteIndex+1)
        }
        return out
    }

    private data class ParsedJsonString(val value:String,val endExclusive:Int)

    private fun skipWhitespace(json:String,start:Int):Int {
        var i=start
        while(i<json.length && json[i].isWhitespace())i++
        return i
    }

    private fun readJsonString(json:String,startQuote:Int):ParsedJsonString? {
        if(startQuote !in json.indices || json[startQuote]!='"')return null
        val out=StringBuilder()
        var i=startQuote+1
        while(i<json.length){
            val c=json[i]
            when(c){
                '"' -> return ParsedJsonString(out.toString(),i+1)
                '\\' -> {
                    if(i+1>=json.length)return null
                    val n=json[i+1]
                    when(n){
                        '"' -> out.append('"')
                        '\\' -> out.append('\\')
                        '/' -> out.append('/')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000c')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if(i+5>=json.length)return null
                            val code=json.substring(i+2,i+6).toIntOrNull(16) ?: return null
                            out.append(code.toChar())
                            i+=4
                        }
                        else -> out.append(n)
                    }
                    i+=2
                }
                else -> { out.append(c);i++ }
            }
        }
        return null
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

}
