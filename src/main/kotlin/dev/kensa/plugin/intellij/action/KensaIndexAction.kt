package dev.kensa.plugin.intellij.action

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys.RUN_PROFILE
import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import dev.kensa.plugin.intellij.console.KensaOutputFilter
import java.io.File

class KensaIndexAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val output = KensaOutputFilter.currentKensaOutput
        if (output != null) {
            openInBrowser(output)
        } else {
            thisLogger().warn("No Kensa output available")
        }
    }

    override fun update(e: AnActionEvent) {
        val runProfile = e.getData(RUN_PROFILE)
        val isRelevantConfiguration = isRelevantConfiguration(runProfile)

        // Only show if we have both a relevant config and detected Kensa output
        val enabled = isRelevantConfiguration && KensaOutputFilter.currentKensaOutput != null

        e.presentation.isVisible = enabled
        e.presentation.isEnabled = enabled
    }

    private fun isRelevantConfiguration(runProfile: RunProfile?): Boolean {
        if (runProfile == null) return false

        val runConfiguration = when (runProfile) {
            is RunConfiguration -> runProfile
            is RunnerAndConfigurationSettings -> runProfile.configuration
            else -> return false
        }

        return when (runConfiguration) {
            is JUnitConfiguration -> true
            is GradleRunConfiguration -> runConfiguration.isRunAsTest
            is MavenRunConfiguration -> {
                runConfiguration.runnerParameters.goals.any { it.contains("test") }
            }
            else -> false
        }
    }

    private fun openInBrowser(output: String) {
        try {
            when {
                output.startsWith("http://") || output.startsWith("https://") -> {
                    BrowserUtil.browse(output)
                }
                File(output).exists() -> {
                    BrowserUtil.browse(File(output).toURI())
                }
                else -> {
                    BrowserUtil.browse(File(output).toURI())
                }
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to open in browser: $output", e)
        }
    }
}