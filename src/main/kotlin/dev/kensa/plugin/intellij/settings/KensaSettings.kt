package dev.kensa.plugin.intellij.settings

import com.intellij.openapi.components.*

@Service(Service.Level.PROJECT)
@State(
    name = "KensaSettings",
    storages = [Storage("kensa.xml")]
)
class KensaSettings : SimplePersistentStateComponent<KensaSettingsState>(KensaSettingsState()) {

    fun resolveUrl(classFqn: String, methodName: String?): String? {
        val template = state.ciReportUrlTemplate?.takeIf { it.isNotBlank() } ?: return null

        val simpleClassName = classFqn.substringAfterLast('.')
        val packageName = classFqn.substringBeforeLast('.', missingDelimiterValue = "")

        return template
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
}