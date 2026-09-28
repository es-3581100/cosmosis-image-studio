package studio.cosmosis.workers

import studio.cosmosis.analysis.LocalVisualAnalyzer
import java.nio.file.Path

data class CriticObservation(val category:String,val status:String,val note:String)
data class CriticReport(val observations:List<CriticObservation>)

object ImageCritic {
    fun compare(source:Path?,candidate:Path,requested:String):CriticReport {
        val c=LocalVisualAnalyzer.analyze(candidate)
        val out=mutableListOf(
            CriticObservation("requested-object-presence","REVIEW","Human review required for semantic presence: ${requested.take(120)}"),
            CriticObservation("obvious-artifacts","INFO","Local edge density %.3f; critic does not treat this as a quality score.".format(c.edgeDensity)),
            CriticObservation("composition-match","REVIEW","Candidate is ${c.orientation}; inspect against intended framing.")
        )
        if(source!=null){
            val s=LocalVisualAnalyzer.analyze(source)
            out+=CriticObservation("subject-preservation","REVIEW","Source/candidate dimensions ${s.width}×${s.height} → ${c.width}×${c.height}; semantic identity requires model/human inspection.")
        }
        return CriticReport(out)
    }
}
