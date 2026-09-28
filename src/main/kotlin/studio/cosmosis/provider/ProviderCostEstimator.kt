package studio.cosmosis.provider

/**
 * Conservative optional estimates used only when a user explicitly sets a
 * provider-spend cap. Unknown pricing returns null so the worker can fail
 * closed rather than pretend to enforce a budget it cannot calculate.
 * Values are data, not billing authority; keep this table dated and small.
 */
object ProviderCostEstimator {
    const val PRICE_DATA_AS_OF="2026-09-27"
    fun estimateUsd(providerId:String,request:GenerationRequest):Double? {
        val each=when(providerId.lowercase()){
            "local" -> 0.0
            "gemini" -> when(request.model){
                "gemini-3.1-flash-image" -> when((request.metadata["imageSize"]?:"1K").uppercase()){
                    "0.5K","512","512PX" -> .045
                    "1K" -> .067
                    "2K" -> .101
                    "4K" -> .151
                    else -> return null
                }
                else -> return null
            }
            else -> return null
        }
        return each*request.variants
    }
}
