package studio.cosmosis.prompt

import studio.cosmosis.*

class PromptLibrary(initial: Collection<PromptAsset> = emptyList(), initialRevisions: Collection<PromptRevision> = emptyList()) {
    private val nodes = linkedMapOf<String, PromptAsset>()
    private val revisions = mutableListOf<PromptRevision>()
    init { initial.forEach { nodes[it.id] = it }; revisions += initialRevisions }

    fun all(includeDeleted: Boolean = false): List<PromptAsset> = nodes.values.filter { includeDeleted || it.deletedAt == null }
    fun get(id: String): PromptAsset? = nodes[id]
    fun revisions(id: String): List<PromptRevision> = revisions.filter { it.promptId == id }.sortedByDescending { it.createdAt }

    fun create(prompt: PromptAsset): PromptAsset {
        require(prompt.id !in nodes) { "Duplicate prompt id ${prompt.id}" }
        require(!prompt.readOnly) { "Read-only upstream prompts cannot be inserted as editable local nodes" }
        SmartKeywords.refresh(prompt)
        nodes[prompt.id] = prompt
        return prompt
    }

    fun update(id: String, mutator: (PromptAsset) -> Unit): PromptAsset {
        val p = requireNotNull(nodes[id]) { "Unknown prompt $id" }
        require(!p.readOnly) { "Read-only prompt must be copied before editing" }
        revisions += PromptRevision(promptId=id, snapshot=p.copy(
            providerHints=p.providerHints.toList(), modelHints=p.modelHints.toList(), aspectRatioHints=p.aspectRatioHints.toList(),
            variables=p.variables.toMap(), explicitKeywords=p.explicitKeywords.toSet(), importedKeywords=p.importedKeywords.toSet(),
            regexKeywords=p.regexKeywords.toSet(), semanticKeywords=p.semanticKeywords.toSet(), visualKeywords=p.visualKeywords.toSet()
        ))
        mutator(p)
        p.updatedAt = nowIso()
        SmartKeywords.refresh(p)
        return p
    }

    fun copyUpstream(id: String): PromptAsset {
        val source = requireNotNull(nodes[id]) { "Unknown prompt $id" }
        require(source.readOnly || source.origin == PromptOrigin.UPSTREAM) { "Source is not an upstream read-only prompt" }
        val copied = source.copy(
            id=newId("prompt"), createdAt=nowIso(), updatedAt=nowIso(), origin=PromptOrigin.DERIVED,
            parentId=null, derivedFrom=source.id, readOnly=false, deletedAt=null, treePath=source.treePath.replaceFirst(Regex("^[^/]+"), "MY PROMPTS")
        )
        nodes[copied.id] = copied
        return copied
    }

    fun duplicate(id: String): PromptAsset {
        val p = requireNotNull(nodes[id])
        val copy = p.copy(id=newId("prompt"), title="${p.title} copy", createdAt=nowIso(), updatedAt=nowIso(), origin=PromptOrigin.DERIVED, parentId=p.id, derivedFrom=p.id, readOnly=false)
        nodes[copy.id] = copy
        return copy
    }

    fun move(id: String, newTreePath: String) = update(id) { it.treePath = normalizePath(newTreePath) }
    fun rename(id: String, title: String) = update(id) { it.title = title.trim() }
    fun favorite(id: String, value: Boolean) = update(id) { it.favorite = value }
    fun trash(id: String) = update(id) { it.deletedAt = nowIso() }
    fun restore(id: String) { val p=requireNotNull(nodes[id]); p.deletedAt=null; p.updatedAt=nowIso() }

    fun search(query: String, scopePrefix: String? = null): List<PromptAsset> {
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        return all().asSequence().filter { p -> scopePrefix == null || p.treePath.startsWith(scopePrefix, true) }.map { p ->
            val hay = listOf(p.title,p.treePath,p.summary,p.body,p.notes,p.allKeywords().joinToString(" ")).joinToString("\n").lowercase()
            var score=0
            for(t in terms) score += when {
                p.title.lowercase().contains(t) -> 8
                p.treePath.lowercase().contains(t) -> 5
                p.allKeywords().any { k -> k.contains(t) } -> 4
                hay.contains(t) -> 1
                else -> 0
            }
            Pair(p,score)
        }.filter { pair -> terms.isEmpty() || pair.second > 0 }.sortedWith(compareByDescending<Pair<PromptAsset,Int>>{pair->pair.second}.thenBy{pair->pair.first.title}).map{pair->pair.first}.toList()
    }

    private fun normalizePath(path: String): String = path.split('/').map { it.trim() }.filter { it.isNotBlank() }.joinToString("/")
}
