package studio.cosmosis.docs

import java.nio.file.Files
import java.nio.file.Path

data class DocEntry(val capability:String,val module:String,val intents:Set<String>,val file:Path,val sectionIds:Set<String>,val aliases:Set<String>)

class AgentIndex(private val root:Path) {
    private val entries=mutableListOf<DocEntry>()
    fun rebuild():Int {
        entries.clear()
        if(!Files.exists(root)) return 0
        Files.walk(root).use { paths -> paths.filter{it.toString().endsWith(".html")}.forEach { parse(it)?.let(entries::add) } }
        return entries.size
    }
    fun all()=entries.toList()
    fun lookup(intent:String,limit:Int=5):List<DocEntry>{
        fun tokens(s:String)=s.lowercase().split(Regex("[^a-z0-9]+")).filter{it.isNotBlank()}.toSet()
        val q=tokens(intent)
        return entries.map { e ->
            val intentTokens=e.intents.flatMap{tokens(it)}.toSet()
            val aliasTokens=e.aliases.flatMap{tokens(it)}.toSet()
            val capTokens=tokens(e.capability)
            val score=q.count{it in intentTokens}*5 + q.count{it in aliasTokens}*3 + q.count{it in capTokens}*2
            e to score
        }.filter{it.second>0}.sortedByDescending{it.second}.take(limit).map{it.first}
    }
    fun contextPacket(intent:String,maxChars:Int=5000):String {
        val hit=lookup(intent,3)
        return buildString {
            append("INTENT: $intent\n")
            hit.forEach { e -> append("CAPABILITY ${e.capability} / ${e.module}\nSOURCE ${root.relativize(e.file)}\nSECTIONS ${e.sectionIds.joinToString()}\nINTENTS ${e.intents.joinToString()}\n\n") }
        }.take(maxChars)
    }
    private fun parse(path:Path):DocEntry? {
        val text=Files.readString(path)
        val script=Regex("<script[^>]*id=[\"']agent-index[\"'][^>]*>(.*?)</script>",RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)?:return null
        fun str(k:String)=Regex("\"$k\"\\s*:\\s*\"([^\"]+)\"").find(script)?.groupValues?.get(1).orEmpty()
        fun arr(k:String)=Regex("\"$k\"\\s*:\\s*\\[(.*?)]",RegexOption.DOT_MATCHES_ALL).find(script)?.groupValues?.get(1)?.let { body -> Regex("\"([^\"]+)\"").findAll(body).map{it.groupValues[1]}.toSet() }.orEmpty()
        val sections=Regex("<section\\s+id=[\"']([^\"']+)",RegexOption.IGNORE_CASE).findAll(text).map{it.groupValues[1]}.toSet()
        return DocEntry(str("capability"),str("module"),arr("intents"),path,sections,arr("aliases"))
    }
}
