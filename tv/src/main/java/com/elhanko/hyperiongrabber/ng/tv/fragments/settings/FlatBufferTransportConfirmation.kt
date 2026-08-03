package com.elhanko.hyperiongrabber.ng.tv.fragments.settings

import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransportPreferenceBinding

/** Pure state model for the TV FlatBuffer enable-confirmation flow. */
internal object FlatBufferTransportConfirmation {

    internal data class State(
        val persistedTransport: String,
        val flatBufferPort: String?,
        val flatBufferEnabled: Boolean,
        val confirmationRequired: Boolean
    ) {
        val flatBufferPortEnabled: Boolean
            get() = flatBufferEnabled
    }

    /**
     * A click is interpreted from the stored transport, not Leanback's already-mutated checkbox.
     */
    fun requestToggle(persistedTransport: String?, flatBufferPort: String?): State {
        return if (HyperionTransportPreferenceBinding.isFlatBufferEnabled(persistedTransport)) {
            disabled(flatBufferPort)
        } else {
            State(
                HyperionTransportPreferenceBinding.persistedValue(false),
                flatBufferPort,
                flatBufferEnabled = false,
                confirmationRequired = true
            )
        }
    }

    fun confirmed(flatBufferPort: String?): State {
        return State(
            HyperionTransportPreferenceBinding.persistedValue(true),
            flatBufferPort,
            flatBufferEnabled = true,
            confirmationRequired = false
        )
    }

    fun cancelled(flatBufferPort: String?): State = disabled(flatBufferPort)

    private fun disabled(flatBufferPort: String?): State {
        return State(
            HyperionTransportPreferenceBinding.persistedValue(false),
            flatBufferPort,
            flatBufferEnabled = false,
            confirmationRequired = false
        )
    }
}
