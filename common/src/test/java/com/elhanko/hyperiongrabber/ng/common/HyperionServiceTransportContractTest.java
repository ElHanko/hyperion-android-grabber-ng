package com.elhanko.hyperiongrabber.ng.common;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;

public class HyperionServiceTransportContractTest {
    @Test
    public void existingAndTransportBroadcastKeysRemainStable() {
        assertEquals("SERVICE_STATUS", HyperionScreenService.BROADCAST_TAG);
        assertEquals("SERVICE_ERROR", HyperionScreenService.BROADCAST_ERROR);
        assertEquals("SERVICE_FILTER", HyperionScreenService.BROADCAST_FILTER);
        assertEquals("SERVICE_TRANSPORT", HyperionScreenService.BROADCAST_TRANSPORT);
    }

    @Test
    public void connectedCallbackCarriesTransportNameSeparately() throws Exception {
        Method method = HyperionScreenService.HyperionThreadBroadcaster.class
                .getDeclaredMethod("onConnected", String.class);
        assertArrayEquals(new Class<?>[] {String.class}, method.getParameterTypes());
    }

    @Test
    public void errorCallbackPreservesFailureAndCarriesTransportName() throws Exception {
        Method method = HyperionScreenService.HyperionThreadBroadcaster.class
                .getDeclaredMethod("onConnectionError", String.class, IOException.class);
        assertArrayEquals(
                new Class<?>[] {String.class, IOException.class},
                method.getParameterTypes());
    }
}
