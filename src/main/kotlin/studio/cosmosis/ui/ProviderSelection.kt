package studio.cosmosis.ui

import studio.cosmosis.provider.ModelDefinition

internal fun preferredModelId(definitions:List<ModelDefinition>,current:String?):String? {
    val validCurrent=current?.takeIf { id -> definitions.any { it.id==id } }
    return validCurrent ?: definitions.firstOrNull()?.id
}
