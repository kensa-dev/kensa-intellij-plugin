package dev.kensa.plugin.intellij.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows

class KensaSettingsConfigurable(private val project: Project) : BoundConfigurable("Kensa") {

    override fun createPanel() = panel {
        group("Output") {
            row("Directory name:") {
                textField()
                    .bindText(
                        getter = { project.service<KensaSettings>().state.outputDirName ?: "" },
                        setter = { project.service<KensaSettings>().state.outputDirName = it.ifBlank { null } }
                    )
                    .align(AlignX.FILL)
                    .resizableColumn()
                    .comment("Directory written by Kensa (default: <b>kensa-output</b>). Leave blank to use the default.")
            }
        }
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
                    .rows(5)
                    .align(AlignX.FILL)
                    .resizableColumn()
                    .comment("Available tokens: <b>{projectName}</b>, <b>{testClass}</b>, <b>{testMethod}</b>, <b>{simpleClassName}</b>, <b>{packageName}</b>")
            }
        }
    }
}