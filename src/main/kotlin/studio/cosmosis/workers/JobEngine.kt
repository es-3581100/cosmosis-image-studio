package studio.cosmosis.workers

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import studio.cosmosis.*
import studio.cosmosis.provider.*
import studio.cosmosis.storage.SqliteStore
import java.util.concurrent.ConcurrentHashMap

class JobEngine(private val providers:ProviderRegistry,private val store:SqliteStore,parallelism:Int=2):AutoCloseable {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val queue=Channel<Queued>(Channel.UNLIMITED)
    private val jobs=ConcurrentHashMap<String,WorkerJob>()
    private data class Queued(val job:WorkerJob,val providerId:String,val request:GenerationRequest,val editing:Boolean,val budget:JobBudget,val callback:(Result<GenerationResult>)->Unit)

    init { repeat(parallelism.coerceAtLeast(1)){ scope.launch { for(q in queue) runOne(q) } } }

    fun submit(providerId:String,request:GenerationRequest,editing:Boolean,budget:JobBudget=JobBudget(),callback:(Result<GenerationResult>)->Unit={}):WorkerJob {
        require(budget.maxGenerations>=1){"maxGenerations must be at least 1"}
        require(request.variants in 1..budget.maxGenerations){"request variants exceed maxGenerations budget"}
        require(budget.maxRetries>=0){"maxRetries must be non-negative"}
        require(budget.maxParallelWorkers>=1){"maxParallelWorkers must be at least 1"}
        require(budget.timeoutSeconds>0){"timeoutSeconds must be positive"}
        budget.maxProviderSpendUsd?.let { ceiling ->
            require(ceiling>=0.0){"maxProviderSpendUsd must be non-negative"}
            val estimate=ProviderCostEstimator.estimateUsd(providerId,request)
                ?: throw IllegalArgumentException("Cannot enforce provider spend ceiling for $providerId/${request.model}: no price estimate is registered")
            require(estimate<=ceiling){"Estimated provider spend $${"%.4f".format(estimate)} exceeds budget $${"%.4f".format(ceiling)}"}
        }
        val job=WorkerJob(
            type=if(editing)"image-edit" else "image-generate",
            payload=mapOf("provider" to providerId,"model" to request.model,"requestId" to request.id),
            maxRetries=budget.maxRetries
        )
        jobs[job.id]=job
        store.saveJob(job)
        queue.trySend(Queued(job,providerId,request,editing,budget,callback)).getOrThrow()
        return job
    }

    fun cancel(id:String){jobs[id]?.let{it.state=JobState.CANCELLED;it.updatedAt=nowIso();store.saveJob(it)}}
    fun snapshot():List<WorkerJob> = jobs.values.sortedBy{it.createdAt}

    private suspend fun runOne(q:Queued){
        val j=q.job
        if(j.state==JobState.CANCELLED)return
        j.state=JobState.RUNNING;j.updatedAt=nowIso();store.saveJob(j)
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
                        store.saveJob(j)
                    }
                }
                throw last?:IllegalStateException("job failed")
            }
        }
        result.onSuccess{j.state=JobState.COMPLETE;j.error=null}.onFailure{
            j.state=if(it is CancellationException)JobState.CANCELLED else JobState.FAILED
            j.error=it.message
        }
        j.updatedAt=nowIso();store.saveJob(j)
        q.callback(result)
    }

    override fun close(){queue.close();scope.cancel()}
}
