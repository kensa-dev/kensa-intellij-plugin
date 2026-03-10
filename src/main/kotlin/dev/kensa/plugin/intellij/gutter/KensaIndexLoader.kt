package dev.kensa.plugin.intellij.gutter

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object KensaIndexLoader {

    private val gson = Gson()
    private val log = thisLogger()

    fun loadFromFile(project: Project, indicesJson: VirtualFile) {
        val indexHtml = indicesJson.parent?.findChild("index.html") ?: return

        try {
            val json = indicesJson.inputStream.reader().use { it.readText() }
            val root = gson.fromJson(json, KensaIndicesRoot::class.java) ?: return

            val service = project.service<KensaTestResultsService>()
            root.indices?.forEach { entry ->
                val classFqn = entry.testClass ?: return@forEach
                val classStatus = entry.state?.toTestStatus()
                val methodStatuses = entry.children
                    ?.mapNotNull { child ->
                        val method = child.testMethod ?: return@mapNotNull null
                        val status = child.state?.toTestStatus() ?: return@mapNotNull null
                        method to status
                    }
                    ?.toMap()
                    ?: emptyMap()

                service.updateFromIndex(classFqn, classStatus, indexHtml.path, methodStatuses)
            }
        } catch (e: Exception) {
            log.warn("Failed to parse Kensa indices.json at ${indicesJson.path}", e)
        }
    }

    private fun String.toTestStatus(): TestStatus? = when (this) {
        "Passed" -> TestStatus.PASSED
        "Failed" -> TestStatus.FAILED
        else -> null
    }
}

private data class KensaIndicesRoot(
    @SerializedName("indices") val indices: List<KensaIndexEntry>?
)

private data class KensaIndexEntry(
    @SerializedName("testClass") val testClass: String?,
    @SerializedName("state") val state: String?,
    @SerializedName("children") val children: List<KensaMethodEntry>?
)

private data class KensaMethodEntry(
    @SerializedName("testMethod") val testMethod: String?,
    @SerializedName("state") val state: String?
)
