package dev.kensa.plugin.intellij.action

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.browsers.*
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys.RUN_PROFILE
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import dev.kensa.plugin.intellij.service.ProjectKensaOutput
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration


class KensaIndexAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = BGT

    override fun actionPerformed(e: AnActionEvent) {
        val kensaOutputService = e.projectKensaOutput()
        val descriptor = e.runContentDescriptor
        val output = kensaOutputService[descriptor]

        if (output != null) openInBrowser(output.asPsiFileIn(e.requiredProject))
    }

    private fun openInBrowser(psiFile: PsiFile?) {
        if (psiFile != null) {
            val request = createOpenInBrowserRequest(psiFile)
            if (request != null) openInBrowser(request)
        }
    }

    private fun createOpenInBrowserRequest(element: PsiElement): OpenInBrowserRequest? {
        val psiFile = runReadAction {
            if (element.isValid) {
                element.containingFile?.let { if (it.virtualFile == null) null else it }
            } else null
        } ?: return null

        return object : OpenInBrowserRequest(psiFile, true) {
            override val element = element
        }
    }

    private fun openInBrowser(request: OpenInBrowserRequest, preferLocalUrl: Boolean = false, browser: WebBrowser? = null) {
        try {
            val urls = WebBrowserService.getInstance().getUrlsToOpen(request, preferLocalUrl)
            BrowserLauncher.instance.browse(urls.first().toExternalForm(), browser, request.project)
        } catch (e: WebBrowserUrlProvider.BrowserException) {
            Messages.showErrorDialog(e.message, IdeBundle.message("browser.error"))
        } catch (e: Exception) {
            thisLogger().error(e)
        }
    }

    override fun update(e: AnActionEvent) {
        val projectKensaOutput = e.projectKensaOutput()
        val isRelevantConfiguration = isRelevantConfiguration(e.getData(RUN_PROFILE))
        val hasKensaOutput = isRelevantConfiguration && projectKensaOutput.hasOutputFor(e.runContentDescriptor)

        e.presentation.isVisible = hasKensaOutput
        e.presentation.isEnabled = hasKensaOutput
    }

    private fun String.asPsiFileIn(project: Project): PsiFile? = LocalFileSystem.getInstance().findFileByPath(this)?.let { PsiManager.getInstance(project).findFile(it) }
    private val AnActionEvent.requiredProject get() = project!!
    private fun AnActionEvent.projectKensaOutput() = requiredProject.service<ProjectKensaOutput>()
    private val AnActionEvent.runContentDescriptor: RunContentDescriptor get() = RunContentManager.getInstance(requiredProject).selectedContent!!

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
}