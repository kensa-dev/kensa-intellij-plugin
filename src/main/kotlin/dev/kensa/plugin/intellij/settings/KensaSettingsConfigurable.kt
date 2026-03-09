package dev.kensa.plugin.intellij.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows

class KensaSettingsConfigurable(private val project: Project) : BoundConfigurable("Kensa") {

    override fun createPanel() = panel {
        group("Editor") {
            row {
                checkBox("Show gutter icons on Kensa test classes and methods")
                    .bindSelected(
                        getter = { project.service<KensaSettings>().state.showGutterIcons },
                        setter = { project.service<KensaSettings>().state.showGutterIcons = it }
                    )
                    .comment("Adds a clickable icon in the editor gutter to open Kensa reports. Requires reopening the file.")
            }
        }
        group("CI Report Integration") {
            row("Report URL template:") {
                textArea()
                    .bindText(
                        getter = { project.service<KensaSettings>().state.ciReportUrlTemplate ?: "" },
                        setter = { project.service<KensaSettings>().state.ciReportUrlTemplate = it.ifBlank { null } }
                    )
                    .rows(3)
                    .resizableColumn()
                    .comment(
                        """
                        Available tokens: <b>{testClass}</b>, <b>{testMethod}</b>, <b>{simpleClassName}</b>, <b>{packageName}</b><br/>
                        Example: https://myserver.com/repo/download/MyTeam_MyProject/lastSuccessful/kensa-output/index.html#/test/{testClass}?method={testMethod}
                        """.trimIndent()
                    )
            }
        }
    }
}