package dev.kensa.plugin.intellij.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.OpenInBrowserRequest
import com.intellij.ide.browsers.WebBrowserService
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import com.intellij.ui.awt.RelativePoint
import dev.kensa.plugin.intellij.service.ProjectKensaOutput
import dev.kensa.plugin.intellij.settings.KensaSettings
import org.jetbrains.uast.*
import java.awt.event.MouseEvent
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import javax.swing.Icon

class KensaGutterLineMarkerProvider : LineMarkerProvider {

    companion object {
        private const val KENSA_TEST_FQN = "dev.kensa.junit.KensaTest"
        private val KENSA_ICON: Icon = IconLoader.getIcon("/icons/KensaGutter.svg", KensaGutterLineMarkerProvider::class.java)
        private val KENSA_ICON_PASS: Icon = IconLoader.getIcon("/icons/KensaGutterPass.svg", KensaGutterLineMarkerProvider::class.java)
        private val KENSA_ICON_FAIL: Icon = IconLoader.getIcon("/icons/KensaGutterFail.svg", KensaGutterLineMarkerProvider::class.java)
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Check opt-in setting
        if (!element.project.service<KensaSettings>().state.showGutterIcons) return null

        // We only want to attach to identifier tokens (leaf elements)
        if (element.firstChild != null) return null

        val parent = element.parent ?: return null
        val uParent = parent.toUElementOfExpectedTypes(UClass::class.java, UMethod::class.java) ?: return null

        // Ensure we're on the name identifier of the declaration
        val nameIdentifier = when (uParent) {
            is UClass -> uParent.uastAnchor?.sourcePsi
            is UMethod -> uParent.uastAnchor?.sourcePsi
            else -> null
        }
        val isNameElement = (nameIdentifier == element) ||
                (nameIdentifier?.textRange == element.textRange)

        if (!isNameElement) return null

        val project = element.project

        val (classFqn, methodName) = when (uParent) {
            is UMethod -> {
                val containingClass = uParent.getContainingUClass() ?: return null
                val fqn = containingClass.qualifiedName ?: return null
                fqn to uParent.name
            }
            is UClass -> {
                val fqn = uParent.qualifiedName ?: return null
                fqn to null
            }
            else -> return null
        }

        if (!isKensaTest(uParent, project)) return null

        val icon = iconFor(project, classFqn, methodName)

        return LineMarkerInfo(
            element,
            element.textRange,
            icon,
            { "Open Kensa Report" },
            { mouseEvent, psiElement -> handleClick(mouseEvent, psiElement.project, classFqn, methodName) },
            GutterIconRenderer.Alignment.RIGHT,
            { "Open Kensa Report" }
        )
    }

    private fun iconFor(project: Project, classFqn: String, methodName: String?): Icon {
        val results = project.service<KensaTestResultsService>()
        val status = if (methodName != null) {
            results.getMethodStatus(classFqn, methodName)
        } else {
            results.getClassStatus(classFqn)
        }
        return when (status) {
            TestStatus.PASSED -> KENSA_ICON_PASS
            TestStatus.FAILED -> KENSA_ICON_FAIL
            null -> KENSA_ICON
        }
    }

    private fun handleClick(mouseEvent: MouseEvent?, project: Project, classFqn: String, methodName: String?) {
        val settings = project.service<KensaSettings>()
        val hasLocal = project.service<ProjectKensaOutput>().latestIndexPath
            ?.let { java.io.File(it).exists() } == true
        val hasCiUrl = settings.resolveUrl(classFqn, methodName) != null

        if (!hasLocal && !hasCiUrl) {
            Messages.showInfoMessage(
                project,
                "No Kensa report available.\nRun tests first, or configure a CI report URL in Settings → Tools → Kensa.",
                "Kensa"
            )
            return
        }

        val items = buildList {
            if (hasLocal) add("Open Local Report" to { openLocal(project, classFqn, methodName) })
            if (hasCiUrl) add("Open CI Report" to { openCi(project, classFqn, methodName) })
        }

        // Single option — just do it
        if (items.size == 1) {
            items.first().second.invoke()
            return
        }

        // Multiple options — show popup
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items.map { it.first })
            .setTitle("Kensa Report")
            .setItemChosenCallback { chosen ->
                items.firstOrNull { it.first == chosen }?.second?.invoke()
            }
            .createPopup()

        if (mouseEvent != null) {
            popup.show(RelativePoint(mouseEvent))
        } else {
            popup.showInFocusCenter()
        }
    }

    private fun isKensaTest(uElement: UElement, project: Project): Boolean {
        val psiClass = when (uElement) {
            is UClass -> uElement.javaPsi
            is UMethod -> uElement.getContainingUClass()?.javaPsi
            else -> null
        } ?: return false

        val kensaBase = JavaPsiFacade.getInstance(project)
            .findClass(KENSA_TEST_FQN, GlobalSearchScope.projectScope(project))
            ?: return false

        return InheritanceUtil.isInheritorOrSelf(psiClass, kensaBase, true)
    }

    private fun buildRoute(classFqn: String, methodName: String?): String = buildString {
        append("#/test/")
        append(URLEncoder.encode(classFqn, UTF_8))
        if (!methodName.isNullOrBlank()) {
            append("?method=")
            append(URLEncoder.encode(methodName, UTF_8))
        }
    }

    private fun openLocal(project: Project, classFqn: String, methodName: String?) {
        val indexPath = project.service<ProjectKensaOutput>().latestIndexPath ?: return

        val vFile = LocalFileSystem.getInstance().findFileByPath(indexPath) ?: return
        val psiFile = runReadAction { PsiManager.getInstance(project).findFile(vFile) } ?: return

        val request = object : OpenInBrowserRequest(psiFile, true) {
            override val element: PsiElement = psiFile
        }

        try {
            val baseUrl = WebBrowserService.getInstance()
                .getUrlsToOpen(request, false)
                .firstOrNull()?.toExternalForm() ?: return

            val finalUrl = baseUrl.substringBefore('#') + buildRoute(classFqn, methodName)
            BrowserLauncher.instance.browse(finalUrl, null, project)
        } catch (ex: Exception) {
            thisLogger().error(ex)
        }
    }

    private fun openCi(project: Project, classFqn: String, methodName: String?) {
        val url = project.service<KensaSettings>().resolveUrl(classFqn, methodName) ?: return

        try {
            BrowserLauncher.instance.browse(url, null, project)
        } catch (ex: Exception) {
            thisLogger().error(ex)
        }
    }
}