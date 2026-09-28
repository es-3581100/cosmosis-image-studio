package studio.cosmosis.prompt

import studio.cosmosis.*
import java.nio.file.Files
import java.nio.file.Path

/** Portable prompt exchange. V2 preserves structured prompt metadata and provenance. */
object PromptExchange {
    const val SCHEMA="cosmosis-prompt-v2"
    private val accepted=setOf(SCHEMA,"offworld-prompt-v1")
    fun exportPrompt(prompt:PromptAsset,path:Path){ Files.createDirectories(path.parent);Files.writeString(path,toJson(prompt)) }
    fun importPrompt(path:Path):PromptAsset=fromJson(Files.readString(path))
    fun toJson(p:PromptAsset):String="""{
  "schema":"$SCHEMA",
  "title":"${e(p.title)}",
  "summary":"${e(p.summary)}",
  "body":"${e(p.body)}",
  "negativeConstraints":"${e(p.negativeConstraints)}",
  "intent":"${e(p.intent)}",
  "workflow":"${e(p.workflow)}",
  "treePath":"${e(p.treePath)}",
  "origin":"${p.origin}",
  "source":"${e(p.source?:"")}",
  "sourceUrl":"${e(p.sourceUrl?:"")}",
  "sourceVersion":"${e(p.sourceVersion?:"")}",
  "sourceLicense":"${e(p.sourceLicense?:"")}",
  "derivedFrom":"${e(p.derivedFrom?:"")}",
  "notes":"${e(p.notes)}",
  "providerHints":"${e(csv(p.providerHints))}",
  "modelHints":"${e(csv(p.modelHints))}",
  "aspectRatioHints":"${e(csv(p.aspectRatioHints))}",
  "styleTags":"${e(csv(p.styleTags))}",
  "subjectTags":"${e(csv(p.subjectTags))}",
  "compositionTags":"${e(csv(p.compositionTags))}",
  "lightingTags":"${e(csv(p.lightingTags))}",
  "cameraTags":"${e(csv(p.cameraTags))}",
  "materialTags":"${e(csv(p.materialTags))}",
  "textRenderingTags":"${e(csv(p.textRenderingTags))}",
  "referenceImageHints":"${e(csv(p.referenceImageHints))}",
  "variables":"${e(p.variables.entries.joinToString("\\n"){it.key+"="+it.value})}",
  "explicitKeywords":"${e(csv(p.explicitKeywords))}",
  "importedKeywords":"${e(csv(p.importedKeywords))}",
  "semanticKeywords":"${e(csv(p.semanticKeywords))}",
  "visualKeywords":"${e(csv(p.visualKeywords))}"
}"""
    fun fromJson(json:String):PromptAsset{
        val schema=field(json,"schema");require(schema in accepted){"Unsupported prompt exchange schema '$schema'"}
        fun n(k:String)=field(json,k).ifBlank{null}
        fun list(k:String)=field(json,k).split(',').map{it.trim()}.filter{it.isNotEmpty()}
        fun set(k:String)=list(k).toSet()
        val variables=field(json,"variables").lineSequence().mapNotNull{line->val i=line.indexOf('=');if(i<=0)null else line.substring(0,i).trim() to line.substring(i+1)}.toMap()
        return PromptAsset(
            title=field(json,"title").ifBlank{"Imported prompt"},summary=field(json,"summary"),body=field(json,"body"),
            negativeConstraints=field(json,"negativeConstraints"),intent=field(json,"intent"),workflow=field(json,"workflow"),
            providerHints=list("providerHints"),modelHints=list("modelHints"),aspectRatioHints=list("aspectRatioHints"),
            styleTags=list("styleTags"),subjectTags=list("subjectTags"),compositionTags=list("compositionTags"),lightingTags=list("lightingTags"),cameraTags=list("cameraTags"),materialTags=list("materialTags"),textRenderingTags=list("textRenderingTags"),
            variables=variables,referenceImageHints=list("referenceImageHints"),treePath=field(json,"treePath").ifBlank{"MY PROMPTS/Imported"},
            origin=runCatching{PromptOrigin.valueOf(field(json,"origin"))}.getOrDefault(PromptOrigin.LOCAL),source=n("source"),sourceUrl=n("sourceUrl"),sourceVersion=n("sourceVersion"),sourceLicense=n("sourceLicense"),derivedFrom=n("derivedFrom"),notes=field(json,"notes"),
            explicitKeywords=set("explicitKeywords"),importedKeywords=set("importedKeywords"),semanticKeywords=set("semanticKeywords"),visualKeywords=set("visualKeywords"),readOnly=false
        ).also(SmartKeywords::refresh)
    }
    private fun csv(values:Collection<String>)=values.joinToString(",")
    private fun field(j:String,k:String):String { val m=Regex("\\\"$k\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").find(j)?:return "";return un(m.groupValues[1]) }
    private fun e(s:String)=buildString{for(c in s)append(when(c){'\\'->"\\\\";'\"'->"\\\"";'\n'->"\\n";'\r'->"\\r";'\t'->"\\t";else->c})}
    private fun un(s:String):String { val o=StringBuilder();var i=0;while(i<s.length){if(s[i]=='\\'&&i+1<s.length){i++;o.append(when(s[i]){'n'->'\n';'r'->'\r';'t'->'\t';'\"'->'\"';'\\'->'\\';else->s[i]})}else o.append(s[i]);i++};return o.toString() }
}
