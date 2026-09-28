package studio.cosmosis.lineage

import studio.cosmosis.VersionNode

data class VersionGraphPoint(
    val id:String,
    val parentId:String?,
    val x:Double,
    val y:Double,
    val depth:Int,
    val lane:Int
)

/**
 * Deterministic dependency-free layout for the branching version graph.
 * Coordinates are normalized to 0..1 so OPENRNDR and HTML exports can share it.
 */
object VersionGraphLayout {
    fun layout(nodes:List<VersionNode>):List<VersionGraphPoint> {
        if(nodes.isEmpty()) return emptyList()
        val byId=nodes.associateBy{it.id}
        val index=nodes.mapIndexed{i,n->n.id to i}.toMap()
        val children=nodes.groupBy{it.parentId}
        val depthMemo=mutableMapOf<String,Int>()
        fun depth(id:String,seen:Set<String> = emptySet()):Int {
            depthMemo[id]?.let{return it}
            if(id in seen) return 0
            val p=byId[id]?.parentId
            val d=if(p==null || p !in byId) 0 else depth(p,seen+id)+1
            depthMemo[id]=d
            return d
        }
        val roots=nodes.filter{it.parentId==null || it.parentId !in byId}.sortedBy{index[it.id]}
        val laneMemo=mutableMapOf<String,Double>()
        var nextLane=0.0
        fun lane(id:String,seen:Set<String> = emptySet()):Double {
            laneMemo[id]?.let{return it}
            if(id in seen) return nextLane++
            val kids=children[id].orEmpty().sortedBy{index[it.id]}
            val y=if(kids.isEmpty()) nextLane++ else kids.map{lane(it.id,seen+id)}.average()
            laneMemo[id]=y
            return y
        }
        roots.forEach{lane(it.id)}
        // Disconnected/cycle-protected nodes still get a stable lane.
        nodes.forEach{if(it.id !in laneMemo) laneMemo[it.id]=nextLane++}
        val maxDepth=nodes.maxOf{depth(it.id)}.coerceAtLeast(1)
        val maxLane=laneMemo.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        return nodes.map { n ->
            val d=depth(n.id);val raw=laneMemo.getValue(n.id)
            VersionGraphPoint(n.id,n.parentId,d.toDouble()/maxDepth,raw/maxLane,d,raw.toInt())
        }
    }
}
