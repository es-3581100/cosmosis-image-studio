package studio.cosmosis.orml

/**
 * Fail-closed bridge boundary for optional ORML modules.
 * The application never claims a local-ML action succeeded unless an actual
 * adapter is installed and returns a concrete result.
 */
data class OrmlInvocation(val capabilityId:String,val inputPath:String,val options:Map<String,String> = emptyMap())
data class OrmlResult(val capabilityId:String,val ok:Boolean,val outputPaths:List<String> = emptyList(),val metadata:Map<String,String> = emptyMap(),val message:String)

interface OrmlAdapter { val capabilityId:String; fun invoke(request:OrmlInvocation):OrmlResult }

class OrmlExecutor(adapters:Collection<OrmlAdapter> = emptyList()) {
    private val installed=adapters.associateBy{it.capabilityId}
    fun status(capabilityId:String):String {
        val descriptor=OrmlRegistry.capabilities.firstOrNull{it.id==capabilityId} ?: return "UNKNOWN"
        return when { installed.containsKey(capabilityId) -> "READY"; descriptor.runtimeAvailable() -> "RUNTIME_DETECTED / ADAPTER_REQUIRED"; else -> "UNAVAILABLE" }
    }
    fun invoke(request:OrmlInvocation):OrmlResult {
        val descriptor=OrmlRegistry.capabilities.firstOrNull{it.id==request.capabilityId}
            ?: return OrmlResult(request.capabilityId,false,message="Unknown ORML capability")
        val adapter=installed[request.capabilityId]
            ?: return OrmlResult(request.capabilityId,false,message="${descriptor.module} is optional and no verified runtime adapter is installed")
        return adapter.invoke(request)
    }
}
