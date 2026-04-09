package dev.kensa.plugin.intellij.gutter

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

enum class TestStatus { PASSED, FAILED }

@Service(PROJECT)
class KensaTestResultsService(private val project: Project) {

    private val methodResults = ConcurrentHashMap<String, TestStatus>()
    private val classResults = ConcurrentHashMap<String, TestStatus>()
    private val classIndexPaths = ConcurrentHashMap<String, String>()

    @Volatile
    var latestIndexPath: String? = null

    fun getMethodStatus(classFqn: String, methodName: String): TestStatus? =
        methodResults["$classFqn#$methodName"]

    fun getClassStatus(classFqn: String): TestStatus? =
        classResults[classFqn]

    fun getIndexPath(classFqn: String): String? =
        classIndexPaths[classFqn]

    fun clearForIndexHtml(indexHtmlPath: String) {
        val staleClasses = classIndexPaths.entries
            .filter { it.value == indexHtmlPath }
            .map { it.key }
        staleClasses.forEach { classFqn ->
            classResults.remove(classFqn)
            classIndexPaths.remove(classFqn)
            methodResults.keys.removeIf { it.startsWith("$classFqn#") }
        }
    }

    fun updateFromIndex(
        classFqn: String,
        classStatus: TestStatus?,
        indexHtmlPath: String,
        methodStatuses: Map<String, TestStatus>
    ) {
        val effectiveClassStatus = classStatus
            ?: if (methodStatuses.values.any { it == TestStatus.FAILED }) TestStatus.FAILED
            else if (methodStatuses.values.all { it == TestStatus.PASSED } && methodStatuses.isNotEmpty()) TestStatus.PASSED
            else null
        if (effectiveClassStatus != null) classResults[classFqn] = effectiveClassStatus
        classIndexPaths[classFqn] = indexHtmlPath
        latestIndexPath = indexHtmlPath
        methodStatuses.forEach { (method, status) ->
            methodResults["$classFqn#$method"] = status
        }
        refreshMarkers()
    }

    // Called by SMTRunnerEventsListener for real-time updates during a run
    fun updateMethod(classFqn: String, methodName: String, status: TestStatus) {
        methodResults["$classFqn#$methodName"] = status
        refreshMarkers()
    }

    fun updateClass(classFqn: String, status: TestStatus) {
        classResults[classFqn] = status
        refreshMarkers()
    }

    fun refreshMarkers() {
        invokeLater {
            if (!project.isDisposed) {
                DaemonCodeAnalyzer.getInstance(project).restart("Kensa test results updated")
            }
        }
    }
}
