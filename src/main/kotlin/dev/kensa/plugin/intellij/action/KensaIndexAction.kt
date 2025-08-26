package dev.kensa.plugin.intellij.action

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentManager
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys.RUN_PROFILE
import com.intellij.openapi.diagnostic.thisLogger
import java.io.File

class KensaIndexAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return super.getActionUpdateThread()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedContent = RunContentManager.getInstance(project).selectedContent
        val console = selectedContent?.executionConsole

        val kensaOutput = extractKensaOutput(console)
        if (kensaOutput != null) {
            openInBrowser(kensaOutput)
        } else {
            thisLogger().warn("Could not extract Kensa output from console")
        }
    }

    override fun update(e: AnActionEvent) {
        val isTest = e.getData(RUN_PROFILE) is JUnitConfiguration

        val enabled = if (isTest) {
            val project = e.project ?: return
            val selectedContent = RunContentManager.getInstance(project).selectedContent
            val console = selectedContent?.executionConsole

            canEnable(console, e)
        } else false

        e.presentation.isVisible = enabled
        e.presentation.isEnabled = enabled
    }

    private fun canEnable(consoleView: ExecutionConsole?, e: AnActionEvent) =
        if (consoleView is BaseTestsOutputConsoleView) {
            val console = consoleView.console
            if (console is ConsoleViewImpl) {
                val document = console.editor?.document
                val consoleText = document?.text ?: ""

                consoleText.contains("Kensa Output :")
            } else false
        } else false

    private fun extractKensaOutput(consoleView: ExecutionConsole?): String? {
        if (consoleView is BaseTestsOutputConsoleView) {
            val console = consoleView.console
            if (console is ConsoleViewImpl) {
                val document = console.editor?.document
                val consoleText = document?.text ?: return null

                val lines = consoleText.lines()
                for (i in lines.indices) {
                    val line = lines[i]
                    if (line.contains("Kensa Output :")) {
                        // Get the next line if it exists
                        if (i + 1 < lines.size) {
                            return lines[i + 1].trim()
                        }
                    }
                }
            }
        }
        return null
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
