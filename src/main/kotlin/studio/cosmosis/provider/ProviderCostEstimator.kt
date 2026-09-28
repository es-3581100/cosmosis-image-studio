package studio.cosmosis.provider

/**
 * Request-time spend estimates are used only to enforce an explicit hard
 * provider-spend ceiling before a job is admitted.
 *
 * A number is returned only when it is a defensible total request estimate.
 * Current remote image routes include usage-dependent input/reasoning/tool
 * charges that cannot be bounded from the canonical request alone, so they
 * intentionally return null. This makes a requested hard ceiling fail closed
 * instead of treating image-output price as total spend.
 */
object ProviderCostEstimator {
    const val PRICE_DATA_AS_OF="2026-09-28"

    fun estimateUsd(providerId:String,request:GenerationRequest):Double? = when(providerId.lowercase()){
        "local" -> 0.0
        else -> null
    }
}
