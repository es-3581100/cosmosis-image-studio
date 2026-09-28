package studio.cosmosis.lineage

import studio.cosmosis.VersionNode

class VersionGraph(nodes: Collection<VersionNode> = emptyList()) {
    private val byId = linkedMapOf<String, VersionNode>().apply { nodes.forEach { put(it.id, it) } }
    fun add(node: VersionNode) {
        require(node.id !in byId) { "Duplicate version ${node.id}" }
        if (node.parentId != null) require(node.parentId in byId) { "Missing parent ${node.parentId}" }
        byId[node.id] = node
    }
    fun get(id: String): VersionNode? = byId[id]
    fun all(): List<VersionNode> = byId.values.toList()
    fun children(id: String): List<VersionNode> = byId.values.filter { it.parentId == id }
    fun roots(): List<VersionNode> = byId.values.filter { it.parentId == null }
    fun ancestry(id: String): List<VersionNode> {
        val out = mutableListOf<VersionNode>(); var cur = byId[id]
        val seen = mutableSetOf<String>()
        while (cur != null && seen.add(cur.id)) { out += cur; cur = cur.parentId?.let(byId::get) }
        return out
    }
    fun validateAcyclic(): Boolean = byId.keys.all { id ->
        val seen=mutableSetOf<String>(); var cur:String?=id
        while(cur!=null){ if(!seen.add(cur)) return@all false; cur=byId[cur]?.parentId }
        true
    }
}
