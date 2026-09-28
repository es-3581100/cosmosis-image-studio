package studio.cosmosis

import java.nio.file.Path
import java.time.Instant
import java.util.UUID

fun newId(prefix: String): String = "$prefix-${UUID.randomUUID()}"
fun nowIso(): String = Instant.now().toString()

enum class PromptOrigin { LOCAL, UPSTREAM, DERIVED, GENERATED, AGENT }
enum class AssetKind { ORIGINAL, GENERATED, MASK, PREVIEW, EXPORT, REFERENCE }
enum class VersionOperation { IMPORT, GENERATE, EDIT, MASK_EDIT, UPSCALE, STYLE_TRANSFER, BACKGROUND_REPLACE, REMIX, ROTATE, FLIP, CROP }
enum class JobState { QUEUED, RUNNING, WAITING, COMPLETE, FAILED, CANCELLED, INTERRUPTED }
enum class WorkerRole { DIRECTOR, PROMPT_COMPILER, VISUAL_ANALYZER, MASK_WORKER, PROVIDER_ROUTER, GENERATION, CRITIC, CURATOR, LINEAGE_WRITER, EXPORT }
enum class WorkflowMode { QUICK_GENERATE, PRECISION_GENERATE, EDIT_EXISTING, MASK_EDIT, REFERENCE_REMIX, STYLE_TRANSFER, BACKGROUND_REPLACE, SUBJECT_PRESERVE, TEXT_POSTER, IMAGE_TO_PROMPT, BATCH_VARIATIONS, UPSCALE, AGENT_BUILD }

data class PromptAsset(
    val id: String = newId("prompt"),
    var title: String,
    var summary: String = "",
    var body: String,
    var negativeConstraints: String = "",
    var intent: String = "",
    var workflow: String = "",
    var providerHints: List<String> = emptyList(),
    var modelHints: List<String> = emptyList(),
    var aspectRatioHints: List<String> = emptyList(),
    var styleTags: List<String> = emptyList(),
    var subjectTags: List<String> = emptyList(),
    var compositionTags: List<String> = emptyList(),
    var lightingTags: List<String> = emptyList(),
    var cameraTags: List<String> = emptyList(),
    var materialTags: List<String> = emptyList(),
    var textRenderingTags: List<String> = emptyList(),
    var variables: Map<String, String> = emptyMap(),
    var referenceImageHints: List<String> = emptyList(),
    val createdAt: String = nowIso(),
    var updatedAt: String = nowIso(),
    val origin: PromptOrigin = PromptOrigin.LOCAL,
    val source: String? = null,
    val sourceUrl: String? = null,
    val sourceVersion: String? = null,
    val sourceLicense: String? = null,
    val parentId: String? = null,
    val derivedFrom: String? = null,
    var notes: String = "",
    var treePath: String = "MY PROMPTS/General",
    var explicitKeywords: Set<String> = emptySet(),
    var importedKeywords: Set<String> = emptySet(),
    var regexKeywords: Set<String> = emptySet(),
    var semanticKeywords: Set<String> = emptySet(),
    var visualKeywords: Set<String> = emptySet(),
    var favorite: Boolean = false,
    var readOnly: Boolean = false,
    var deletedAt: String? = null
) {
    fun allKeywords(): Set<String> = explicitKeywords + importedKeywords + regexKeywords + semanticKeywords + visualKeywords
}

data class PromptRevision(val id: String = newId("prev"), val promptId: String, val snapshot: PromptAsset, val createdAt: String = nowIso())

data class ImageAsset(
    val id: String = newId("asset"),
    val kind: AssetKind,
    val path: String,
    val mime: String,
    val width: Int,
    val height: Int,
    val sha256: String,
    val sourceAssetId: String? = null,
    val provenance: String = "local",
    val createdAt: String = nowIso()
)

data class MaskRecord(
    val id: String = newId("mask"),
    val sourceAssetId: String,
    val path: String,
    val width: Int,
    val height: Int,
    val featherRadius: Int = 0,
    val inverted: Boolean = false,
    val method: String = "brush",
    val derivedMetadata: Map<String, String> = emptyMap(),
    val createdAt: String = nowIso()
)

data class VersionNode(
    val id: String = newId("V"),
    val parentId: String?,
    val assetId: String,
    val operation: VersionOperation,
    var name: String,
    val promptId: String? = null,
    val generationId: String? = null,
    var favorite: Boolean = false,
    val createdAt: String = nowIso()
)

data class ProjectRecord(
    val id: String = newId("project"),
    var name: String,
    val root: String,
    var currentVersionId: String? = null,
    val createdAt: String = nowIso(),
    var updatedAt: String = nowIso()
)

data class GenerationRecord(
    val id: String = newId("gen"),
    val parentImageId: String?,
    val inputImageIds: List<String>,
    val maskId: String?,
    val promptId: String?,
    val userPrompt: String,
    val compiledPrompt: String,
    val providerId: String,
    val model: String,
    val endpoint: String,
    val settings: Map<String, String>,
    val outputImageIds: List<String>,
    val durationMs: Long,
    val retryCount: Int,
    val workerId: String,
    val providerRevisedPrompt: String? = null,
    val providerContextId: String? = null,
    val error: String? = null,
    val createdAt: String = nowIso()
)

data class WorkerJob(
    val id: String = newId("job"),
    val type: String,
    var state: JobState = JobState.QUEUED,
    val payload: Map<String, String> = emptyMap(),
    val maxRetries: Int = 2,
    var retryCount: Int = 0,
    var error: String? = null,
    val createdAt: String = nowIso(),
    var updatedAt: String = nowIso()
)

data class JobBudget(
    val maxGenerations: Int = 4,
    val maxRetries: Int = 2,
    val maxParallelWorkers: Int = 2,
    val maxProviderSpendUsd: Double? = null,
    val timeoutSeconds: Long = 180,
    val fallbackPolicy: String = "fail-closed"
)

data class StructuredPrompt(
    var subject: String = "",
    var styleDna: String = "",
    var composition: String = "",
    var camera: String = "",
    var lighting: String = "",
    var color: String = "",
    var materials: String = "",
    var typography: String = "",
    var qualityConstraints: String = "",
    var avoid: String = "",
    var variables: MutableMap<String, String> = linkedMapOf()
) {
    fun toPlainText(): String = listOf(
        "SUBJECT: $subject", "STYLE DNA: $styleDna", "COMPOSITION: $composition",
        "CAMERA: $camera", "LIGHTING: $lighting", "COLOR: $color", "MATERIALS: $materials",
        "TYPOGRAPHY: $typography", "QUALITY: $qualityConstraints", "AVOID: $avoid"
    ).filterNot { it.endsWith(": ") }.joinToString("\n")
}

data class ProjectPaths(val root: Path, val db: Path, val originals: Path, val generated: Path, val masks: Path, val previews: Path, val exports: Path, val metadata: Path, val docs: Path)
