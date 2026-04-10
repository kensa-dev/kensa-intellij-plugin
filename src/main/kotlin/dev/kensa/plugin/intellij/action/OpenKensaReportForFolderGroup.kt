package dev.kensa.plugin.intellij.action

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.kensa.plugin.intellij.gutter.KensaReportOpener
import dev.kensa.plugin.intellij.gutter.KensaTestResultsService
import dev.kensa.plugin.intellij.settings.KensaSettings
import java.io.File
import java.nio.file.Paths

class OpenKensaReportForFolderGroup : ActionGroup("Open Kensa Report", true) {

    override fun getActionUpdateThread() = BGT

    override fun update(e: AnActionEvent) {
        val folder = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project
        val hasOutput = folder != null && folder.isDirectory && project != null &&
            project.service<KensaTestResultsService>().allIndexPaths()
                .any { it.startsWith(folder.path + "/") }
        e.presentation.isEnabledAndVisible = hasOutput
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        e ?: return emptyArray()
        val folder = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return emptyArray()
        val project = e.project ?: return emptyArray()
        val outputDirName = project.service<KensaSettings>().effectiveOutputDirName
        val folderPath = Paths.get(folder.path)

        return File(folder.path).walkTopDown().maxDepth(8)
            .filter { it.name == "index.html" && it.parentFile?.name == outputDirName }
            .map { indexHtml ->
                val label = folderPath.relativize(indexHtml.parentFile.toPath()).toString()
                OpenIndexAction(project, indexHtml.path, label)
            }
            .sortedBy { it.label }
            .toList()
            .toTypedArray()
    }
}

private class OpenIndexAction(
    private val project: Project,
    private val indexHtmlPath: String,
    val label: String,
) : AnAction(label) {
    override fun getActionUpdateThread() = BGT
    override fun actionPerformed(e: AnActionEvent) = KensaReportOpener.openIndexHtml(project, indexHtmlPath)
}
