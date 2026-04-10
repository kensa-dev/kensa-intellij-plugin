package dev.kensa.plugin.intellij.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import dev.kensa.plugin.intellij.gutter.KensaResultsListener
import dev.kensa.plugin.intellij.gutter.KensaReportOpener
import dev.kensa.plugin.intellij.gutter.KensaTestResultsService
import dev.kensa.plugin.intellij.gutter.TestStatus
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Paths
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

private data class IndexNode(val indexHtmlPath: String, val relPath: String)
private data class ClassNode(val classFqn: String, val status: TestStatus?)
private data class MethodNode(val classFqn: String, val methodName: String, val status: TestStatus)

class KensaToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val iconPass    = IconLoader.getIcon("/icons/kensa-gutter-pass.svg",    KensaToolWindowPanel::class.java)
    private val iconFail    = IconLoader.getIcon("/icons/kensa-gutter-fail.svg",    KensaToolWindowPanel::class.java)
    private val iconIgnored = IconLoader.getIcon("/icons/kensa-gutter-ignored.svg", KensaToolWindowPanel::class.java)

    private val tree = Tree().apply {
        isRootVisible = false
        cellRenderer = KensaTreeCellRenderer()
    }

    init {
        add(tree, BorderLayout.CENTER)
        rebuild()

        project.messageBus.connect().subscribe(KensaTestResultsService.KENSA_RESULTS_TOPIC, KensaResultsListener {
            rebuild()
        })

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) openSelected()
            }
        })
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) openSelected()
            }
        })
    }

    private fun rebuild() {
        val service = project.service<KensaTestResultsService>()
        val root = DefaultMutableTreeNode()

        service.allIndexPaths().sorted().forEach { indexPath ->
            val relPath = relPath(indexPath)
            val indexNode = DefaultMutableTreeNode(IndexNode(indexPath, relPath))

            service.classesForIndex(indexPath).forEach { classFqn ->
                val classNode = DefaultMutableTreeNode(ClassNode(classFqn, service.getClassStatus(classFqn)))
                service.methodsForClass(classFqn).entries
                    .sortedBy { it.key }
                    .forEach { (method, status) ->
                        classNode.add(DefaultMutableTreeNode(MethodNode(classFqn, method, status)))
                    }
                indexNode.add(classNode)
            }

            root.add(indexNode)
        }

        tree.model = DefaultTreeModel(root)
        expandAll()
    }

    private fun expandAll() {
        var i = 0
        while (i < tree.rowCount) {
            tree.expandRow(i++)
        }
    }

    private fun openSelected() {
        val node = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject ?: return
        when (node) {
            is MethodNode -> KensaReportOpener.openLocal(null, project, node.classFqn, node.methodName)
            is ClassNode  -> KensaReportOpener.openLocal(null, project, node.classFqn, null)
            is IndexNode  -> KensaReportOpener.openIndexHtml(project, node.indexHtmlPath)
        }
    }

    private fun iconFor(status: TestStatus?) = when (status) {
        TestStatus.PASSED  -> iconPass
        TestStatus.FAILED  -> iconFail
        TestStatus.IGNORED -> iconIgnored
        null               -> iconIgnored
    }

    private fun relPath(indexHtmlPath: String): String {
        val base = project.basePath ?: return indexHtmlPath
        return try {
            Paths.get(base).relativize(Paths.get(indexHtmlPath).parent).toString()
        } catch (_: Exception) {
            indexHtmlPath
        }
    }

    private inner class KensaTreeCellRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean,
        ) {
            val node = (value as? DefaultMutableTreeNode)?.userObject ?: return
            when (node) {
                is IndexNode -> {
                    icon = AllIcons.Nodes.Folder
                    append(node.relPath)
                }
                is ClassNode -> {
                    icon = iconFor(node.status)
                    append(node.classFqn.substringAfterLast('.'), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                }
                is MethodNode -> {
                    icon = iconFor(node.status)
                    append(node.methodName)
                }
            }
        }
    }
}
