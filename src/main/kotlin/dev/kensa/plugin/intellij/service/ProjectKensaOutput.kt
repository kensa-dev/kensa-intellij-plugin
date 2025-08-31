package dev.kensa.plugin.intellij.service

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import java.util.concurrent.ConcurrentHashMap

@Service(PROJECT)
class ProjectKensaOutput {

    private val descriptorOutputs = ConcurrentHashMap<RunContentDescriptor, String>()

    @Volatile
    var temporaryOutput: String? = null

    operator fun get(descriptor: RunContentDescriptor): String? =
        if (hasOutputFor(descriptor))
            descriptorOutputs[descriptor]
        else {
            claimTemporaryFor(descriptor)
            descriptorOutputs[descriptor]
        }

    operator fun set(descriptor: RunContentDescriptor, output: String) {
        descriptorOutputs[descriptor] = output
    }

    fun hasOutputFor(descriptor: RunContentDescriptor): Boolean = descriptorOutputs.containsKey(descriptor) || claimTemporaryFor(descriptor)

    private fun claimTemporaryFor(descriptor: RunContentDescriptor): Boolean =
        temporaryOutput.takeIf { it != null }?.let { descriptorOutputs[descriptor] = it; temporaryOutput = null; true } ?: false
}