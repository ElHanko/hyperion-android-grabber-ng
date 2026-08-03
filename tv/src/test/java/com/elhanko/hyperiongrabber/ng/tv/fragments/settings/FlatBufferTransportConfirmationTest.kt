package com.elhanko.hyperiongrabber.ng.tv.fragments.settings

import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlatBufferTransportConfirmationTest {

    private val savedFlatBufferPort = "19400"

    @Test
    fun enablingFromProtocolBuffersRequiresConfirmationWithoutPersistingFlatBuffer() {
        val state = FlatBufferTransportConfirmation.requestToggle(
            HyperionTransportType.PROTOBUF.persistedValue(),
            savedFlatBufferPort
        )

        assertTrue(state.confirmationRequired)
        assertEquals(HyperionTransportType.PROTOBUF.persistedValue(), state.persistedTransport)
        assertFalse(state.flatBufferEnabled)
        assertFalse(state.flatBufferPortEnabled)
        assertEquals(savedFlatBufferPort, state.flatBufferPort)
    }

    @Test
    fun confirmationPersistsExactFlatBufferValueAndEnablesThePort() {
        val state = FlatBufferTransportConfirmation.confirmed(savedFlatBufferPort)

        assertFalse(state.confirmationRequired)
        assertEquals(HyperionTransportType.FLATBUFFER.persistedValue(), state.persistedTransport)
        assertTrue(state.flatBufferEnabled)
        assertTrue(state.flatBufferPortEnabled)
        assertEquals(savedFlatBufferPort, state.flatBufferPort)
    }

    @Test
    fun cancelAndDialogDismissalKeepProtocolBuffersAndThePortDisabled() {
        val cancelled = FlatBufferTransportConfirmation.cancelled(savedFlatBufferPort)
        val dismissed = FlatBufferTransportConfirmation.cancelled(savedFlatBufferPort)

        assertProtocolBuffersDisabled(cancelled)
        assertProtocolBuffersDisabled(dismissed)
    }

    @Test
    fun disablingExistingFlatBufferIsImmediateAndDoesNotRequireConfirmation() {
        val state = FlatBufferTransportConfirmation.requestToggle(
            HyperionTransportType.FLATBUFFER.persistedValue(),
            savedFlatBufferPort
        )

        assertFalse(state.confirmationRequired)
        assertProtocolBuffersDisabled(state)
    }

    private fun assertProtocolBuffersDisabled(state: FlatBufferTransportConfirmation.State) {
        assertEquals(HyperionTransportType.PROTOBUF.persistedValue(), state.persistedTransport)
        assertFalse(state.flatBufferEnabled)
        assertFalse(state.flatBufferPortEnabled)
        assertEquals(savedFlatBufferPort, state.flatBufferPort)
    }
}
