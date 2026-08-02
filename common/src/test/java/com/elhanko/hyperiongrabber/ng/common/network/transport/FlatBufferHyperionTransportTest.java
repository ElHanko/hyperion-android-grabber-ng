package com.elhanko.hyperiongrabber.ng.common.network.transport;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.elhanko.hyperiongrabber.ng.common.network.HyperionProtocolException;
import com.elhanko.hyperiongrabber.ng.common.network.HyperionServerException;
import com.elhanko.hyperiongrabber.ng.common.network.HyperionTimeoutException;

import org.junit.Test;

import hyperionnet.Clear;
import hyperionnet.Color;
import hyperionnet.Command;
import hyperionnet.Image;
import hyperionnet.RawImage;
import hyperionnet.Register;
import hyperionnet.Request;

public class FlatBufferHyperionTransportTest {
    @Test
    public void factoryCreatesRegisteredExperimentalAdapter() throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(connection -> {
            Register register = connection.expectFlatBufferRegister();
            assertEquals(HyperionTransportTestServer.ORIGIN, register.origin());
            assertEquals(HyperionTransportTestServer.PRIORITY, register.priority());
            connection.writeFlatBufferReply(null, -1, HyperionTransportTestServer.PRIORITY);
            connection.waitForClientClose();
        }); HyperionTransport transport = new HyperionTransportFactory().create(
                HyperionTransportType.FLATBUFFER, server.config())) {
            assertTrue(transport instanceof FlatBufferHyperionTransport);
            assertTrue(transport.isConnected());
            assertEquals("FlatBuffer (experimental)", transport.transportName());
        }
    }

    @Test
    public void rejectsMissingOriginBeforeOpeningFlatBufferConnection() throws Exception {
        try (java.net.ServerSocket endpoint = new java.net.ServerSocket(
                0, 1, java.net.InetAddress.getLoopbackAddress())) {
            HyperionTransportConfig config = new HyperionTransportConfig(
                    endpoint.getInetAddress().getHostAddress(),
                    endpoint.getLocalPort(),
                    150,
                    " ",
                    1_000,
                    500);
            assertThrows(IllegalArgumentException.class,
                    () -> new FlatBufferHyperionTransport(config));
            endpoint.setSoTimeout(100);
            assertThrows(java.net.SocketTimeoutException.class, endpoint::accept);
        }
    }

    @Test
    public void colorDelegatesPackedValueAndDuration() throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(connection -> {
            connection.acceptFlatBufferRegistration();
            Request request = connection.readFlatBufferRequest();
            assertEquals(Command.Color, request.commandType());
            Color color = (Color) request.command(new Color());
            assertEquals(0xa1b2c3, color.data());
            assertEquals(750, color.duration());
            connection.writeFlatBufferReply(null, -1, HyperionTransportTestServer.PRIORITY);
        }); HyperionTransport transport = new FlatBufferHyperionTransport(server.config())) {
            transport.setColor(0xa1b2c3, 750);
        }
    }

    @Test
    public void sendsRgb24ImageUnchanged() throws Exception {
        assertImage(new byte[] {1, 2, 3, 4, 5, 6});
    }

    @Test
    public void sendsRgb32ImageUnchanged() throws Exception {
        assertImage(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
    }

    @Test
    public void clearUsesOwnPriorityAndPreservesReregistrationSemantics() throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(connection -> {
            connection.acceptFlatBufferRegistration();
            Request clearRequest = connection.readFlatBufferRequest();
            assertEquals(Command.Clear, clearRequest.commandType());
            Clear clear = (Clear) clearRequest.command(new Clear());
            assertEquals(HyperionTransportTestServer.PRIORITY, clear.priority());
            connection.writeFlatBufferReply(null, -1, -1);

            Register register = connection.expectFlatBufferRegister();
            assertEquals(HyperionTransportTestServer.PRIORITY, register.priority());
            connection.writeFlatBufferReply(null, -1, HyperionTransportTestServer.PRIORITY);
            assertEquals(Command.Color, connection.readFlatBufferRequest().commandType());
            connection.writeFlatBufferReply(null, -1, HyperionTransportTestServer.PRIORITY);
        }); HyperionTransport transport = new FlatBufferHyperionTransport(server.config())) {
            transport.clear();
            assertFalse(transport.isConnected());
            transport.setColor(0x010203, 1);
            assertTrue(transport.isConnected());
        }
    }

    @Test
    public void preservesServerException() throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(connection -> {
            connection.acceptFlatBufferRegistration();
            connection.readFlatBufferRequest();
            connection.writeFlatBufferReply("flatbuffer rejected", -1, -1);
            connection.waitForClientClose();
        }); HyperionTransport transport = new FlatBufferHyperionTransport(server.config())) {
            HyperionServerException failure = assertThrows(
                    HyperionServerException.class,
                    () -> transport.setColor(0, 1));
            assertEquals("flatbuffer rejected", failure.getMessage());
            assertTrue(transport.isConnected());
        }
    }

    @Test
    public void preservesProtocolException() throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(connection -> {
            connection.acceptFlatBufferRegistration();
            connection.readFlatBufferRequest();
            connection.writeInvalidFrame(new byte[] {1, 2, 3});
        }); HyperionTransport transport = new FlatBufferHyperionTransport(server.config())) {
            assertThrows(HyperionProtocolException.class,
                    () -> transport.setColor(0, 1));
            assertFalse(transport.isConnected());
        }
    }

    @Test
    public void preservesTimeoutException() throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(connection -> {
            connection.acceptFlatBufferRegistration();
            connection.readFlatBufferRequest();
            connection.waitForClientClose();
        }); HyperionTransport transport = new FlatBufferHyperionTransport(
                server.config(HyperionTransportTestServer.ORIGIN, 100))) {
            assertThrows(HyperionTimeoutException.class,
                    () -> transport.setColor(0, 1));
            assertFalse(transport.isConnected());
        }
    }

    @Test
    public void closeIsIdempotentAndUpdatesReadyState() throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(connection -> {
            connection.acceptFlatBufferRegistration();
            connection.waitForClientClose();
        })) {
            HyperionTransport transport = new FlatBufferHyperionTransport(server.config());
            assertTrue(transport.isConnected());
            transport.close();
            transport.close();
            assertFalse(transport.isConnected());
        }
    }

    private static void assertImage(byte[] pixels) throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(connection -> {
            connection.acceptFlatBufferRegistration();
            Request request = connection.readFlatBufferRequest();
            assertEquals(Command.Image, request.commandType());
            Image image = (Image) request.command(new Image());
            assertEquals(900, image.duration());
            RawImage rawImage = (RawImage) image.data(new RawImage());
            assertEquals(2, rawImage.width());
            assertEquals(1, rawImage.height());
            byte[] actual = new byte[rawImage.dataLength()];
            for (int index = 0; index < actual.length; index++) {
                actual[index] = (byte) rawImage.data(index);
            }
            assertArrayEquals(pixels, actual);
            connection.writeFlatBufferReply(null, -1, HyperionTransportTestServer.PRIORITY);
        }); HyperionTransport transport = new FlatBufferHyperionTransport(server.config())) {
            transport.setImage(pixels, 2, 1, 900);
        }
    }
}
