package studio.cosmosis.workers

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import studio.cosmosis.*
import studio.cosmosis.provider.*
import studio.cosmosis.storage.SqliteStore
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class JobEngine(private val providers:ProviderRegistry,private val store:SqliteStore,parallelism:Int=2,private val onJobUpdate:(WorkerJob)->Unit={}):AutoCloseable {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val queue=Channel<Queued>(Channel.UNLIMITED)
    private val jobs=ConcurrentHashMap<String,WorkerJob>()
    private data class Queued(
        val job:WorkerJob,
        val providerId:String,
        val request:GenerationRequest,
        val editing:Boolean,
        val budget:JobBudget,
        val callback:(Result<GenerationResult>)->Unit
    )

    init {
        store.loadJobs().forEach { jobs[it.id]=it }
        repeat(parallelism.coerceAtLeast(1)){ scope.launch { for(q in queue) runOne(q) } }
    }

    fun submit(
        providerId:String,
        request:GenerationRequest,
        editing:Boolean,
        budget:JobBudget=JobBudget(),
        context:Map<String,String> = emptyMap(),
        callback:(Result<GenerationResult>)->Unit={}
    ):WorkerJob {
        validateBudget(providerId,request,budget)
        val job=WorkerJob(
            type=if(editing)"image-edit" else "image-generate",
            payload=encodePayload(providerId,request,editing,budget,context),
            maxRetries=budget.maxRetries
        )
        jobs[job.id]=job
        store.saveJob(job);onJobUpdate(job.copy())
        queue.trySend(Queued(job,providerId,request,editing,budget,callback)).getOrThrow()
        return job
    }

    /**
     * Explicitly resumes an interrupted/failed job from the canonical request
     * persisted in its local job payload. No provider call is made until this
     * method is invoked by the user-facing controller.
     */
    fun resume(id:String,callback:(Result<GenerationResult>)->Unit={}):WorkerJob {
        val job=requireNotNull(jobs[id]){"Unknown job '"+id+"'"}
        require(job.state==JobState.INTERRUPTED || job.state==JobState.FAILED){"Only INTERRUPTED or FAILED jobs can be resumed"}
        val decoded=decode(job)
        validateBudget(decoded.providerId,decoded.request,decoded.budget)
        job.state=JobState.QUEUED
        job.error=null
        job.retryCount=0
        job.updatedAt=nowIso()
        store.saveJob(job);onJobUpdate(job.copy())
        queue.trySend(Queued(job,decoded.providerId,decoded.request,decoded.editing,decoded.budget,callback)).getOrThrow()
        return job
    }

    fun request(id:String):GenerationRequest?=jobs[id]?.let { runCatching{decode(it).request}.getOrNull() }
    fun context(id:String):Map<String,String> = jobs[id]?.payload.orEmpty().filterKeys{it.startsWith("ctx.")}.mapKeys{it.key.removePrefix("ctx.")}
    fun canResume(id:String):Boolean {
        val job=jobs[id]?:return false
        if(job.state !in setOf(JobState.INTERRUPTED,JobState.FAILED))return false
        return runCatching{decode(job)}.isSuccess
    }

    fun cancel(id:String){jobs[id]?.let{it.state=JobState.CANCELLED;it.updatedAt=nowIso();store.saveJob(it);onJobUpdate(it.copy())}}
    fun snapshot():List<WorkerJob> = jobs.values.sortedBy{it.createdAt}

    private suspend fun runOne(q:Queued){
        val j=q.job
        if(j.state==JobState.CANCELLED)return
        j.state=JobState.RUNNING;j.updatedAt=nowIso();store.saveJob(j);onJobUpdate(j.copy())
        val result=runCatching {
            withTimeout(q.budget.timeoutSeconds*1000){
                var last:Throwable?=null
                repeat(q.budget.maxRetries+1){attempt->
                    if(j.state==JobState.CANCELLED)throw CancellationException("cancelled")
                    try {
                        val providerResult=withContext(Dispatchers.IO){providers.route(q.providerId,q.request,q.editing)}
                        if(j.state==JobState.CANCELLED)throw CancellationException("cancelled")
                        return@withTimeout providerResult
                    } catch(t:Throwable){
                        if(t is CancellationException)throw t
                        last=t
                        j.retryCount=attempt+1
                        j.updatedAt=nowIso()
                        store.saveJob(j);onJobUpdate(j.copy())
                    }
                }
                throw last?:IllegalStateException("job failed")
            }
        }
        result.onSuccess{j.state=JobState.COMPLETE;j.error=null}.onFailure{
            j.state=if(it is CancellationException)JobState.CANCELLED else JobState.FAILED
            j.error=it.message
        }
        j.updatedAt=nowIso();store.saveJob(j);onJobUpdate(j.copy())
        q.callback(result)
    }

    private fun validateBudget(providerId:String,request:GenerationRequest,budget:JobBudget){
        require(budget.maxGenerations>=1){"maxGenerations must be at least 1"}
        require(request.variants in 1..budget.maxGenerations){"request variants exceed maxGenerations budget"}
        require(budget.maxRetries>=0){"maxRetries must be non-negative"}
        require(budget.maxParallelWorkers>=1){"maxParallelWorkers must be at least 1"}
        require(budget.timeoutSeconds>0){"timeoutSeconds must be positive"}
        budget.maxProviderSpendUsd?.let { ceiling ->
            require(ceiling>=0.0){"maxProviderSpendUsd must be non-negative"}
            val estimate=ProviderCostEstimator.estimateUsd(providerId,request)
                ?: throw IllegalArgumentException("Cannot enforce provider spend ceiling for "+providerId+"/"+request.model+": no price estimate is registered")
            require(estimate<=ceiling){"Estimated provider spend "+String.format("$%.4f",estimate)+" exceeds budget "+String.format("$%.4f",ceiling)}
        }
    }

    private data class Decoded(
        val providerId:String,
        val request:GenerationRequest,
        val editing:Boolean,
        val budget:JobBudget
    )

    private fun encodePayload(
        providerId:String,
        request:GenerationRequest,
        editing:Boolean,
        budget:JobBudget,
        context:Map<String,String>
    ):Map<String,String> = linkedMapOf<String,String>().apply {
        put("provider",providerId);put("model",request.model);put("requestId",request.id);put("editing",editing.toString())
        put("request.prompt",request.prompt);put("request.variants",request.variants.toString());put("request.outputFormat",request.outputFormat)
        put("request.transparent",request.transparent.toString())
        request.width?.let{put("request.width",it.toString())};request.height?.let{put("request.height",it.toString())}
        request.aspectRatio?.let{put("request.aspectRatio",it)};request.quality?.let{put("request.quality",it)}
        request.previousResponseId?.let{put("request.previousResponseId",it)}
        put("request.references",request.references.joinToString(SEP){it.path.toString()})
        put("request.referenceMimes",request.references.joinToString(SEP){it.mime})
        request.mask?.let{put("request.mask",it.path.toString());put("request.maskMime",it.mime)}
        request.metadata.forEach{(k,v)->put("meta."+k,v)}
        put("budget.maxGenerations",budget.maxGenerations.toString());put("budget.maxRetries",budget.maxRetries.toString())
        put("budget.maxParallelWorkers",budget.maxParallelWorkers.toString());put("budget.timeoutSeconds",budget.timeoutSeconds.toString())
        put("budget.fallbackPolicy",budget.fallbackPolicy)
        budget.maxProviderSpendUsd?.let{put("budget.maxProviderSpendUsd",it.toString())}
        context.forEach{(k,v)->put("ctx."+k,v)}
    }

    private fun decode(job:WorkerJob):Decoded {
        val p=job.payload
        val provider=requireNotNull(p["provider"]){"Persisted job is missing provider"}
        val model=requireNotNull(p["model"]){"Persisted job is missing model"}
        val prompt=requireNotNull(p["request.prompt"]){"Persisted job predates resumable request persistence"}
        val refPaths=p["request.references"].orEmpty().split(SEP).filter{it.isNotBlank()}
        val refMimes=p["request.referenceMimes"].orEmpty().split(SEP)
        val refs=refPaths.mapIndexed{i,path->ReferenceImage(Path.of(path),refMimes.getOrNull(i)?.takeIf{it.isNotBlank()}?:"image/png")}
        val mask=p["request.mask"]?.takeIf{it.isNotBlank()}?.let{ReferenceImage(Path.of(it),p["request.maskMime"]?:"image/png")}
        val metadata=p.filterKeys{it.startsWith("meta.")}.mapKeys{it.key.removePrefix("meta.")}
        val request=GenerationRequest(
            id=p["requestId"]?:newId("req"),prompt=prompt,model=model,references=refs,mask=mask,
            variants=p["request.variants"]?.toIntOrNull()?:1,width=p["request.width"]?.toIntOrNull(),height=p["request.height"]?.toIntOrNull(),
            aspectRatio=p["request.aspectRatio"],quality=p["request.quality"],outputFormat=p["request.outputFormat"]?:"png",
            transparent=p["request.transparent"].toBoolean(),previousResponseId=p["request.previousResponseId"],metadata=metadata
        )
        val budget=JobBudget(
            maxGenerations=p["budget.maxGenerations"]?.toIntOrNull()?:request.variants,
            maxRetries=p["budget.maxRetries"]?.toIntOrNull()?:job.maxRetries,
            maxParallelWorkers=p["budget.maxParallelWorkers"]?.toIntOrNull()?:1,
            maxProviderSpendUsd=p["budget.maxProviderSpendUsd"]?.toDoubleOrNull(),
            timeoutSeconds=p["budget.timeoutSeconds"]?.toLongOrNull()?:180,
            fallbackPolicy=p["budget.fallbackPolicy"]?:"fail-closed"
        )
        return Decoded(provider,request,p["editing"].toBoolean(),budget)
    }

    override fun close(){queue.close();scope.cancel()}

    companion object { private const val SEP="\u001d" }
}
