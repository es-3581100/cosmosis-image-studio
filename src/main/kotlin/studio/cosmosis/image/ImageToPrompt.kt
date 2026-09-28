package studio.cosmosis.image

import studio.cosmosis.StructuredPrompt
import studio.cosmosis.analysis.LocalOcrAnalyzer
import studio.cosmosis.analysis.LocalVisualAnalyzer
import studio.cosmosis.analysis.VisualAnalysis
import studio.cosmosis.analysis.VisualRegion
import java.nio.file.Path

enum class PromptOutputMode { LITERAL_RECONSTRUCTION, STYLE_DNA, SUBJECT_REPLACEABLE, PRODUCT_POSTER, PORTRAIT, ILLUSTRATION, ARCHITECTURE_INTERIOR, SOCIAL_MEDIA, STRUCTURED_JSON, PLAIN_LANGUAGE, PROVIDER_OPTIMIZED }

data class ImagePromptResult(
    val structure:StructuredPrompt,
    val mode:PromptOutputMode,
    val plainText:String,
    val json:String,
    val regions:List<VisualRegion> = emptyList(),
    val analysis:VisualAnalysis? = null,
    val ocrText:String="",
    val ocrAvailable:Boolean=false
)

object ImageToPrompt {
    fun extract(path:Path,mode:PromptOutputMode=PromptOutputMode.SUBJECT_REPLACEABLE,subjectHint:String="replaceable subject",includeOcr:Boolean=true):ImagePromptResult {
        val a=LocalVisualAnalyzer.analyze(path); val s=LocalVisualAnalyzer.toStructuredPrompt(a,subjectHint)
        val ocr=if(includeOcr)LocalOcrAnalyzer.analyze(path) else null
        if(!ocr?.text.isNullOrBlank())s.typography="visible source text: ${ocr!!.text.take(500)}; preserve exact text only when requested"
        when(mode){
            PromptOutputMode.LITERAL_RECONSTRUCTION -> { s.subject="reconstruct visible source content faithfully";s.qualityConstraints += "; preserve object count, placement, text zones and proportions" }
            PromptOutputMode.STYLE_DNA -> s.subject="{{subject}}"
            PromptOutputMode.PRODUCT_POSTER -> { s.subject="{{product}}";s.typography=(s.typography+"; clear editable headline zone; exact user-supplied text only").trim(';',' ');s.composition += "; product hero with negative space" }
            PromptOutputMode.PORTRAIT -> { s.subject="{{person}}";s.qualityConstraints += "; preserve identity cues, face geometry, eyes and hands" }
            PromptOutputMode.ILLUSTRATION -> { s.subject="{{subject}}";s.styleDna += "; preserve medium, mark-making and edge character" }
            PromptOutputMode.ARCHITECTURE_INTERIOR -> { s.subject="{{space}}";s.qualityConstraints += "; preserve perspective and structural lines" }
            PromptOutputMode.SOCIAL_MEDIA -> { s.composition += "; safe crop zones for social formats" }
            PromptOutputMode.PROVIDER_OPTIMIZED -> { s.qualityConstraints += "; explicit spatial relationships and editable text instructions" }
            PromptOutputMode.STRUCTURED_JSON,PromptOutputMode.PLAIN_LANGUAGE,PromptOutputMode.SUBJECT_REPLACEABLE -> Unit
        }
        val regions=buildList {
            add(VisualRegion("saliency","primary-subject-candidate",a.saliencyBox[0],a.saliencyBox[1],a.saliencyBox[2]-a.saliencyBox[0],a.saliencyBox[3]-a.saliencyBox[1],null))
            addAll(ocr?.regions.orEmpty())
        }
        val json=toJson(s,regions)
        return ImagePromptResult(s,mode,s.toPlainText(),json,regions,a,ocr?.text.orEmpty(),ocr?.available==true)
    }
    fun toJson(s:StructuredPrompt,regions:List<VisualRegion> = emptyList()):String {
        val r=regions.joinToString(prefix="[",postfix="]") { "{\"kind\":\"${q(it.kind)}\",\"label\":\"${q(it.label)}\",\"x\":${it.x},\"y\":${it.y},\"width\":${it.width},\"height\":${it.height}${it.confidence?.let{c->",\"confidence\":${"%.4f".format(java.util.Locale.US,c)}"}?:""}}" }
        return """{"subject":"${q(s.subject)}","styleDna":"${q(s.styleDna)}","composition":"${q(s.composition)}","camera":"${q(s.camera)}","lighting":"${q(s.lighting)}","color":"${q(s.color)}","materials":"${q(s.materials)}","typography":"${q(s.typography)}","qualityConstraints":"${q(s.qualityConstraints)}","avoid":"${q(s.avoid)}","regions":$r}"""
    }
    private fun q(s:String)=s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")
}
