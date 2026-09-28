package studio.cosmosis.prompt

import studio.cosmosis.PromptAsset

object SmartKeywords {
    private val stop = setOf("the","and","for","with","from","this","that","into","your","you","are","but","not","use","using","image","prompt")
    private val tech = Regex("(?i)\\b(?:[A-Z][A-Za-z0-9+.#_-]{2,}|[a-z]+[-_][a-z0-9_-]+|\\w+\\.(?:kt|java|json|yaml|yml|html|png|jpg|webp|md))\\b")
    private val headings = Regex("(?m)^#{1,4}\\s+(.+)$")
    private val words = Regex("[A-Za-z][A-Za-z0-9_-]{2,}")

    fun extract(title: String, path: String, summary: String, body: String, limit: Int = 18): Set<String> {
        val text = "$title\n$path\n$summary\n$body"
        val weighted = linkedMapOf<String, Int>()
        fun add(raw: String, weight: Int) {
            val k = raw.trim().lowercase().trim('-', '_')
            if (k.length < 3 || k in stop) return
            weighted[k] = (weighted[k] ?: 0) + weight
        }
        words.findAll("$title $path $summary").forEach { add(it.value, 5) }
        headings.findAll(body).forEach { m -> words.findAll(m.groupValues[1]).forEach { add(it.value, 4) } }
        tech.findAll(text).forEach { add(it.value, 6) }
        words.findAll(body).forEach { add(it.value, 1) }
        return weighted.entries.sortedWith(compareByDescending<Map.Entry<String,Int>> { it.value }.thenBy { it.key }).take(limit).mapTo(linkedSetOf()) { it.key }
    }

    fun refresh(prompt: PromptAsset): PromptAsset {
        prompt.regexKeywords = extract(prompt.title, prompt.treePath, prompt.summary, prompt.body)
        return prompt
    }
}
