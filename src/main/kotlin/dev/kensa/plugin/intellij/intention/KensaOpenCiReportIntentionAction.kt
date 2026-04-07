package dev.kensa.plugin.intellij.intention

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import dev.kensa.plugin.intellij.gutter.KensaReportOpener
import dev.kensa.plugin.intellij.gutter.ciUrl
import dev.kensa.plugin.intellij.gutter.resolveKensaTarget

class KensaOpenCiReportIntentionAction : PsiElementBaseIntentionAction() {

    override fun getText() = "Open CI Kensa report"
    override fun getFamilyName() = "Kensa"
    override fun startInWriteAction() = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val target = resolveKensaTarget(element) ?: return false
        return ciUrl(project, target.classFqn, target.methodName) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val target = resolveKensaTarget(element) ?: return
        KensaReportOpener.openCi(project, target.classFqn, target.methodName)
    }
}
