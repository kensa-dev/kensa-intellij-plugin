package dev.kensa.plugin.intellij.gutter

import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude.ERROR_INDEX
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude.FAILED_INDEX
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

class KensaTestRunListener(private val project: Project) : SMTRunnerEventsAdapter() {

    override fun onTestFinished(test: SMTestProxy) {
        val (classFqn, methodName) = parseLocation(test.locationUrl ?: return) ?: return
        methodName ?: return
        project.service<KensaTestResultsService>().updateMethod(classFqn, methodName, test.toStatus() ?: return)
    }

    override fun onSuiteFinished(suite: SMTestProxy) {
        val (classFqn, methodName) = parseLocation(suite.locationUrl ?: return) ?: return
        if (methodName != null) return // only handle class-level suites
        project.service<KensaTestResultsService>().updateClass(classFqn, suite.toStatus() ?: return)
    }

    private fun SMTestProxy.toStatus(): TestStatus? = when {
        isPassed -> TestStatus.PASSED
        getMagnitudeInfo() == FAILED_INDEX || getMagnitudeInfo() == ERROR_INDEX -> TestStatus.FAILED
        else -> null
    }

    private fun parseLocation(url: String): Pair<String, String?>? {
        val path = when {
            url.startsWith("java:test://") -> url.removePrefix("java:test://")
            else -> return null
        }
        val slashIdx = path.lastIndexOf('/')
        return if (slashIdx >= 0) {
            path.substring(0, slashIdx) to path.substring(slashIdx + 1).takeIf { it.isNotEmpty() }
        } else {
            path to null
        }
    }
}
