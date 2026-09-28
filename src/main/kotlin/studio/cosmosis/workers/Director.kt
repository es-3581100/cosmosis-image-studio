package studio.cosmosis.workers

import studio.cosmosis.*

data class PlanStep(val index:Int,val role:WorkerRole,val action:String,val optional:Boolean=false)
data class JobPlan(val id:String=newId("plan"),val intent:String,val mode:WorkflowMode,val steps:List<PlanStep>,val budget:JobBudget)

object Director {
    fun plan(intent:String,mode:WorkflowMode,budget:JobBudget=JobBudget(),hasImage:Boolean=true,hasMask:Boolean=false):JobPlan {
        require(budget.maxGenerations>=1){"maxGenerations must be at least 1"}
        require(budget.maxRetries>=0){"maxRetries must be non-negative"}
        require(budget.maxParallelWorkers>=1){"maxParallelWorkers must be at least 1"}
        val s=mutableListOf<PlanStep>()
        fun add(role:WorkerRole,action:String,optional:Boolean=false){s+=PlanStep(s.size+1,role,action,optional)}
        if(mode==WorkflowMode.AGENT_BUILD){
            add(WorkerRole.VISUAL_ANALYZER,"Inspect source/reference images and emit derived visual metadata",!hasImage)
            add(WorkerRole.MASK_WORKER,"Resolve requested preservation/edit region",!hasImage)
            add(WorkerRole.PROMPT_COMPILER,"Retrieve relevant prompts/directives and compile canonical intent")
            add(WorkerRole.PROVIDER_ROUTER,"Validate provider/model capabilities and choose configured route")
            repeat(budget.maxGenerations.coerceAtMost(4)){add(WorkerRole.GENERATION,"Generate bounded candidate ${it+1}")}
            add(WorkerRole.CRITIC,"Annotate candidates against requested intent",true)
            add(WorkerRole.CURATOR,"Present candidates without deleting rejects")
            add(WorkerRole.LINEAGE_WRITER,"Persist outputs and branch lineage")
        } else {
            if(hasImage)add(WorkerRole.VISUAL_ANALYZER,"Read current image context",true)
            if(hasMask)add(WorkerRole.MASK_WORKER,"Validate current mask")
            add(WorkerRole.PROMPT_COMPILER,"Compile current prompt")
            add(WorkerRole.PROVIDER_ROUTER,"Validate route capabilities")
            add(WorkerRole.GENERATION,"Execute generation/edit")
            add(WorkerRole.LINEAGE_WRITER,"Persist result")
        }
        return JobPlan(intent=intent,mode=mode,steps=s,budget=budget)
    }
}
