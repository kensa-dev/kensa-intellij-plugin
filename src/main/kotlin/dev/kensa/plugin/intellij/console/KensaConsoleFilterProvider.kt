package dev.kensa.plugin.intellij.console

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.Result
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

class KensaConsoleFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> = arrayOf(KensaOutputFilter())
}

class KensaOutputFilter : Filter {
    
    companion object {
        @Volatile
        var currentKensaOutput: String? = null
        
        private var foundKensaMarker = false
        private val logger = thisLogger()
    }

    override fun applyFilter(line: String, entireLength: Int): Result? {
        if (line.contains("Kensa Output :")) {
            foundKensaMarker = true
            logger.warn("Found Kensa Output marker: $line")
            return null
        }
        
        // If we found the marker on the previous line, this should be our output
        if (foundKensaMarker) {
            val output = line.trim()
            if (output.isNotEmpty()) {
                currentKensaOutput = output
                logger.warn("Captured Kensa output: $output")
                foundKensaMarker = false
            }
        }
        
        return null
    }
}