
package dev.kensa.plugin.intellij.execution

import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.OpenInBrowserRequest
import com.intellij.ide.browsers.WebBrowserService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import java.util.concurrent.atomic.AtomicLong

class KensaOutputFileWatcherStartupActivity : ProjectActivity {

    private val log = thisLogger()

    override suspend fun execute(project: Project) {
        val basePath = project.basePath ?: return

        val lastNotifiedAt = AtomicLong(0)
        val debounceMs = 3000L

        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val kensaEvents = events.filter { event ->
                    (event is VFileContentChangeEvent || event is VFileCreateEvent) &&
                        event.path.endsWith("/kensa-output/index.html") &&
                        event.path.startsWith(basePath)
                }

                if (kensaEvents.isEmpty()) return

                val now = System.currentTimeMillis()
                val last = lastNotifiedAt.get()
                if (now - last < debounceMs) return
                if (!lastNotifiedAt.compareAndSet(last, now)) return

                val indexPath = kensaEvents.last().path
                log.debug("Kensa output detected: $indexPath")

                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Kensa")
                    .createNotification(
                        "Kensa Report Ready",
                        "Test report updated",
                        NotificationType.INFORMATION,
                    )
                    .addAction(OpenKensaReportNotificationAction(project, indexPath))
                    .notify(project)
            }
        })
    }

    private class OpenKensaReportNotificationAction(
        private val project: Project,
        private val indexPath: String,
    ) : com.intellij.notification.NotificationAction("Open Report") {

        override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent, notification: com.intellij.notification.Notification) {
            notification.expire()

            val vFile = LocalFileSystem.getInstance().findFileByPath(indexPath) ?: return
            val psiFile = runReadAction { PsiManager.getInstance(project).findFile(vFile) } ?: return

            val request = object : OpenInBrowserRequest(psiFile, true) {
                override val element: PsiElement = psiFile
            }

            try {
                val urls = WebBrowserService.getInstance().getUrlsToOpen(request, false)
                BrowserLauncher.instance.browse(urls.first().toExternalForm(), null, project)
            } catch (ex: Exception) {
                thisLogger().error(ex)
            }
        }
    }
}