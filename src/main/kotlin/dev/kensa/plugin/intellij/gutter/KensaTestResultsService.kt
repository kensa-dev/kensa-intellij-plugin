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

    fun getMethodStatus(classFqn: String, methodName: String): TestStatus? =
        methodResults["$classFqn#$methodName"]

    fun getClassStatus(classFqn: String): TestStatus? =
        classResults[classFqn]

    fun updateMethod(classFqn: String, methodName: String, status: TestStatus) {
        methodResults["$classFqn#$methodName"] = status
        refreshMarkers()
    }

    fun updateClass(classFqn: String, status: TestStatus) {
        classResults[classFqn] = status
        refreshMarkers()
    }

    private fun refreshMarkers() {
        invokeLater {
            if (!project.isDisposed) {
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        }
    }
}
