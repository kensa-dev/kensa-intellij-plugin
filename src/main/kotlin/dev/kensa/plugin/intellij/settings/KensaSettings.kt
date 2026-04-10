package dev.kensa.plugin.intellij.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "KensaSettings",
    storages = [Storage("kensa.xml")]
)
class KensaSettings : SimplePersistentStateComponent<KensaSettingsState>(KensaSettingsState()) {

    val effectiveOutputDirName: String
        get() = state.outputDirName?.takeIf { it.isNotBlank() } ?: "kensa-output"

    fun resolveUrl(project: Project, classFqn: String, methodName: String?): String? {
        val template = state.ciReportUrlTemplate?.takeIf { it.isNotBlank() } ?: return null

        val simpleClassName = classFqn.substringAfterLast('.')
        val packageName = classFqn.substringBeforeLast('.', missingDelimiterValue = "")

        return template
            .replace("{projectName}", project.name)
            .replace("{testClass}", classFqn)
            .replace("{simpleClassName}", simpleClassName)
            .replace("{packageName}", packageName)
            .let { url ->
                if (methodName.isNullOrBlank()) {
                    url.replace(Regex("[?&]method=\\{testMethod}"), "")
                        .replace("{testMethod}", "")
                } else {
                    url.replace("{testMethod}", java.net.URLEncoder.encode(methodName, java.nio.charset.StandardCharsets.UTF_8))
                }
            }
    }
}

class KensaSettingsState : BaseState() {
    var ciReportUrlTemplate by string()
    var showGutterIcons by property(false)
    var outputDirName by string()
}