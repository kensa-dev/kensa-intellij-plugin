package dev.kensa.plugin.intellij.console

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.Result
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import dev.kensa.plugin.intellij.service.ProjectKensaOutput

class KensaConsoleFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> = arrayOf(KensaOutputFilter(project))
}

class KensaOutputFilter(private val project: Project) : Filter {
    private var foundKensaMarker = false
    private val logger = thisLogger()

    override fun applyFilter(line: String, entireLength: Int): Result? {
        if (line.contains(KENSA_MARKER)) {
            foundKensaMarker = true
            logger.debug("Found Kensa Output marker: $line")
            return null
        }

        if (foundKensaMarker) {
            val output = line.trim()
            if (output.isNotEmpty()) {
                logger.debug("Captured Kensa output: $output")
                foundKensaMarker = false
                project.service<ProjectKensaOutput>().temporaryOutput = output
            }
        }

        return null
    }

    companion object {
        const val KENSA_MARKER = "Kensa Output :"
    }
}