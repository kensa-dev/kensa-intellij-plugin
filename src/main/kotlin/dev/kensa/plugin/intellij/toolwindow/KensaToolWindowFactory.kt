package dev.kensa.plugin.intellij.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class KensaToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = toolWindow.contentManager.factory
            .createContent(KensaToolWindowPanel(project), "", false)
        toolWindow.contentManager.addContent(content)
    }
}
