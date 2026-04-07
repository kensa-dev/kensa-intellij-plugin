package dev.kensa.plugin.intellij.intention

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import dev.kensa.plugin.intellij.gutter.KensaReportOpener
import dev.kensa.plugin.intellij.gutter.localReportPath
import dev.kensa.plugin.intellij.gutter.resolveKensaTarget
import javax.swing.Icon

class KensaOpenLocalReportIntentionAction : PsiElementBaseIntentionAction(), Iconable {

    override fun getText() = "Open local Kensa report"
    override fun getFamilyName() = "Kensa"
    override fun startInWriteAction() = false
    override fun getIcon(flags: Int): Icon = IconLoader.getIcon("/icons/logo.svg", KensaOpenLocalReportIntentionAction::class.java)

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val target = resolveKensaTarget(element) ?: return false
        return localReportPath(project, target.classFqn) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val target = resolveKensaTarget(element) ?: return
        KensaReportOpener.openLocal(null, project, target.classFqn, target.methodName)
    }
}
