package studio.cosmosis.prompt

import studio.cosmosis.PromptAsset
import studio.cosmosis.StructuredPrompt
import studio.cosmosis.provider.GenerationRequest

object PromptCompiler {
    data class Compiled(val source:String,val compiled:String,val notes:List<String>)
    fun compile(source:String, template:PromptAsset?=null, structure:StructuredPrompt?=null, variables:Map<String,String> = emptyMap(), preserve:List<String> = emptyList(), avoid:String=""):Compiled {
        var base=template?.body?.takeIf{it.isNotBlank()}?.let { "$it\n\nUSER INTENT:\n$source" } ?: source
        val merged=(template?.variables.orEmpty()+variables)
        merged.forEach { (k,v)-> base=base.replace("{{$k}}",v) }
        val chunks=mutableListOf(base.trim())
        structure?.toPlainText()?.takeIf{it.isNotBlank()}?.let{chunks += "VISUAL STRUCTURE:\n$it"}
        if(preserve.isNotEmpty()) chunks += "PRESERVE:\n- "+preserve.joinToString("\n- ")
        val neg=listOf(template?.negativeConstraints.orEmpty(),avoid).filter{it.isNotBlank()}.joinToString("; ")
        if(neg.isNotBlank()) chunks += "AVOID:\n$neg"
        val notes=buildList { if(template!=null)add("template:${template.id}"); if(structure!=null)add("structured-image-context"); if(preserve.isNotEmpty())add("preservation-context") }
        return Compiled(source,chunks.joinToString("\n\n"),notes)
    }
    fun request(compiled:Compiled,model:String,base:GenerationRequest)=base.copy(prompt=compiled.compiled,model=model)
}
