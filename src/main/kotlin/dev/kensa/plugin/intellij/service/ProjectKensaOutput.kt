package dev.kensa.plugin.intellij.service

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.util.Disposer
import java.util.concurrent.ConcurrentHashMap

@Service(PROJECT)
class ProjectKensaOutput {

    private val descriptorOutputs = ConcurrentHashMap<RunContentDescriptor, String>()

    @Volatile
    var temporaryOutput: String? = null

    operator fun get(descriptor: RunContentDescriptor): String? {
        claimTemporaryFor(descriptor)
        return descriptorOutputs[descriptor]
    }

    operator fun set(descriptor: RunContentDescriptor, output: String) {
        descriptorOutputs.put(descriptor, output) ?: registerCleanup(descriptor)
    }

    fun hasOutputFor(descriptor: RunContentDescriptor): Boolean =
        descriptorOutputs.containsKey(descriptor) || claimTemporaryFor(descriptor)

    private fun claimTemporaryFor(descriptor: RunContentDescriptor): Boolean {
        val tmp = temporaryOutput ?: return false
        if (Disposer.isDisposed(descriptor)) return false
        temporaryOutput = null

        descriptorOutputs.put(descriptor, tmp) ?: registerCleanup(descriptor)
        return true
    }

    private fun registerCleanup(descriptor: RunContentDescriptor) {
        if (Disposer.isDisposed(descriptor)) {
            descriptorOutputs.remove(descriptor)
            return
        }
        Disposer.register(descriptor as Disposable) {
            descriptorOutputs.remove(descriptor)
        }
    }
}