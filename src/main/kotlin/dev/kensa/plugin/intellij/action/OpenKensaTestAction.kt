package dev.kensa.plugin.intellij.action

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.ui.RunContentManager
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.OpenInBrowserRequest
import com.intellij.ide.browsers.WebBrowserService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil.isInheritorOrSelf
import com.intellij.psi.util.PsiModificationTracker
import dev.kensa.plugin.intellij.action.OpenKensaTestAction.HierarchyCache.isKensaTest
import dev.kensa.plugin.intellij.service.ProjectKensaOutput
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ConcurrentHashMap

class OpenKensaTestAction : AnAction() {
    private val proxyKey = DataKey.create<SMTestProxy>("testProxy")

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val proxy = e.getData(proxyKey)

        val enabled = project != null && proxy != null && proxy.locationUrl.isKensaTest(project)
        e.presentation.isEnabledAndVisible = enabled
    }

    private object HierarchyCache {
        private const val KENSA_TEST_CLASS_NAME = "dev.kensa.junit.KensaTest"

        private data class Entry(val modCount: Long, val isKensa: Boolean)
        private val perProject: MutableMap<Project, ConcurrentHashMap<String, Entry>> = ConcurrentHashMap()

        fun String?.isKensaTest(project: Project): Boolean {
            val fqn = asFqn() ?: return false

            val tracker = PsiModificationTracker.getInstance(project)
            val curMod = tracker.modificationCount

            val cache = perProject.getOrPut(project) { ConcurrentHashMap() }
            val cached = cache[fqn]
            if (cached != null && cached.modCount == curMod) return cached.isKensa

            val computed = fqn.inheritsKensaTest(project)
            cache[fqn] = Entry(curMod, computed)
            return computed
        }

        private fun String.inheritsKensaTest(project: Project): Boolean {
            val scope = GlobalSearchScope.allScope(project)
            val facade = JavaPsiFacade.getInstance(project)

            val testClass: PsiClass = facade.findClass(this, scope) ?: return false
            val kensa: PsiClass = facade.findClass(KENSA_TEST_CLASS_NAME, scope) ?: return false

            return isInheritorOrSelf(testClass, kensa, true)
        }

        private fun String?.asFqn(): String? {
            if (isNullOrBlank()) return null

            val prefix = when {
                startsWith("java:test://") -> "java:test://"
                startsWith("java:suite://") -> "java:suite://"
                else -> return null
            }

            val rest = removePrefix(prefix)
            val classPart = rest.substringBefore('/')
            return classPart.takeIf { it.isNotBlank() }
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val proxy = e.getData(proxyKey) ?: return

        val (classFqn, methodName) = proxy.locationUrl.parseJavaTestLocation() ?: run {
            Messages.showInfoMessage(project, "Could not determine test location from selection.", "Kensa")
            return
        }

        val indexPath = project.currentRunKensaIndexPath() ?: run {
            Messages.showInfoMessage(
                project,
                "No Kensa report path captured for the current run.\nRun tests and ensure Kensa prints:\nKensa Output :\n<absolute path to index.html>",
                "Kensa"
            )
            return
        }

        val psiFile = indexPath.asPsiFileIn(project) ?: run {
            Messages.showInfoMessage(project, "Kensa index file not found:\n$indexPath", "Kensa")
            return
        }

        val request = createOpenInBrowserRequest(psiFile) ?: return
        val baseUrl = try {
            WebBrowserService.getInstance().getUrlsToOpen(request, false).firstOrNull()?.toExternalForm()
        } catch (ex: Exception) {
            thisLogger().warn("Failed to compute URL for Kensa index", ex)
            null
        }

        if (baseUrl.isNullOrBlank()) {
            Messages.showErrorDialog(project, "Unable to compute URL for Kensa report.", "Kensa")
            return
        }

        val route = buildString {
            append("#/test/")
            append(URLEncoder.encode(classFqn, UTF_8))
            if (!methodName.isNullOrBlank()) {
                append("?method=")
                append(URLEncoder.encode(methodName, UTF_8))
            }
        }

        val finalUrl = baseUrl.substringBefore('#') + route

        try {
            BrowserLauncher.instance.browse(finalUrl, null, project)
        } catch (ex: Exception) {
            thisLogger().error(ex)
        }
    }

    private fun Project.currentRunKensaIndexPath(): String? {
        val descriptor = RunContentManager.getInstance(this).selectedContent ?: return null
        return service<ProjectKensaOutput>()[descriptor]
    }

    private fun String.asPsiFileIn(project: Project): PsiFile? =
        LocalFileSystem.getInstance().findFileByPath(this)?.let { PsiManager.getInstance(project).findFile(it) }

    private fun createOpenInBrowserRequest(element: PsiElement): OpenInBrowserRequest? {
        val psiFile = com.intellij.openapi.application.runReadAction {
            if (element.isValid) {
                element.containingFile?.let { if (it.virtualFile == null) null else it }
            } else null
        } ?: return null

        return object : OpenInBrowserRequest(psiFile, true) {
            override val element = element
        }
    }

    private fun String?.parseJavaTestLocation(): Pair<String, String?>? {
        if (this.isNullOrBlank()) return null

        val prefix = when {
            startsWith("java:test://") -> "java:test://"
            startsWith("java:suite://") -> "java:suite://"
            else -> return null
        }

        val rest = removePrefix(prefix)
        val classPart = rest.substringBefore('/').takeIf { it.isNotBlank() } ?: return null
        val methodPart = rest.substringAfter('/', missingDelimiterValue = "").takeIf { it.isNotBlank() }

        return classPart to methodPart
    }
}
