package com.elhanko.hyperiongrabber.ng.common.network.transport;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.elhanko.hyperiongrabber.ng.common.HyperionProto.ClearRequest;
import com.elhanko.hyperiongrabber.ng.common.HyperionProto.ColorRequest;
import com.elhanko.hyperiongrabber.ng.common.HyperionProto.HyperionRequest;
import com.elhanko.hyperiongrabber.ng.common.HyperionProto.ImageRequest;
import com.elhanko.hyperiongrabber.ng.common.network.HyperionProtocolException;
import com.elhanko.hyperiongrabber.ng.common.network.HyperionServerException;
import com.elhanko.hyperiongrabber.ng.common.network.HyperionTimeoutException;

import org.junit.Test;

import java.lang.reflect.Method;

public class ProtobufHyperionTransportTest {
    @Test
    public void factoryCreatesReadyProtobufAdapterWithStableName() throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(
                HyperionTransportTestServer.Connection::waitForClientClose);
                HyperionTransport transport = new HyperionTransportFactory().create(
                        HyperionTransportType.PROTOBUF, server.config(null))) {
            assertTrue(transport instanceof ProtobufHyperionTransport);
            assertTrue(transport.isConnected());
            assertEquals("Protocol Buffers", transport.transportName());
        }
    }

    @Test
    public void firstRequestIsProtobufColorNotFlatBufferRegister() throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(connection -> {
            HyperionRequest request = connection.readProtobufRequest();
            assertEquals(HyperionRequest.Command.COLOR, request.getCommand());
            connection.writeProtobufSuccess();
        }); HyperionTransport transport = new ProtobufHyperionTransport(server.config(null))) {
            transport.setColor(0x112233, 10);
        }
    }

    @Test
    public void colorPreservesConfiguredPriorityValueAndDuration() throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(connection -> {
            HyperionRequest request = connection.readProtobufRequest();
            ColorRequest color = request.getExtension(ColorRequest.colorRequest);
            assertEquals(HyperionTransportTestServer.PRIORITY, color.getPriority());
            assertEquals(0xa1b2c3, color.getRgbColor());
            assertEquals(750, color.getDuration());
            connection.writeProtobufSuccess();
        }); HyperionTransport transport = new ProtobufHyperionTransport(server.config())) {
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
    public void clearUsesOnlyConfiguredPriority() throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(connection -> {
            HyperionRequest request = connection.readProtobufRequest();
            assertEquals(HyperionRequest.Command.CLEAR, request.getCommand());
            assertEquals(HyperionTransportTestServer.PRIORITY,
                    request.getExtension(ClearRequest.clearRequest).getPriority());
            connection.writeProtobufSuccess();
        }); HyperionTransport transport = new ProtobufHyperionTransport(server.config())) {
            transport.clear();
        }
    }

    @Test
    public void commonInterfaceExposesNeitherClearAllNorWireTypes() {
        for (Method method : HyperionTransport.class.getDeclaredMethods()) {
            assertFalse("clearAll must not be transport-neutral",
                    method.getName().equals("clearAll"));
            assertFalse(method.getReturnType().getName().contains("HyperionProto"));
            assertFalse(method.getReturnType().getName().startsWith("hyperionnet."));
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(parameter.getName().contains("HyperionProto"));
                assertFalse(parameter.getName().startsWith("hyperionnet."));
            }
        }
    }

    @Test
    public void preservesServerExceptionAndConnection() throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(connection -> {
            connection.readProtobufRequest();
            connection.writeProtobufError("protobuf rejected");
            connection.readProtobufRequest();
            connection.writeProtobufSuccess();
        }); HyperionTransport transport = new ProtobufHyperionTransport(server.config())) {
            HyperionServerException failure = assertThrows(
                    HyperionServerException.class,
                    () -> transport.setColor(0, 1));
            assertEquals("protobuf rejected", failure.getMessage());
            assertTrue(transport.isConnected());
            transport.clear();
        }
    }

    @Test
    public void preservesProtocolException() throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(connection -> {
            connection.readProtobufRequest();
            connection.writeInvalidFrame(new byte[] {1, 2, 3});
        }); HyperionTransport transport = new ProtobufHyperionTransport(server.config())) {
            assertThrows(HyperionProtocolException.class, transport::clear);
            assertFalse(transport.isConnected());
        }
    }

    @Test
    public void preservesTimeoutException() throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(connection -> {
            connection.readProtobufRequest();
            connection.waitForClientClose();
        }); HyperionTransport transport = new ProtobufHyperionTransport(
                server.config(null, 100))) {
            assertThrows(HyperionTimeoutException.class, transport::clear);
            assertFalse(transport.isConnected());
        }
    }

    @Test
    public void closeIsIdempotentAndUpdatesConnectedState() throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(
                HyperionTransportTestServer.Connection::waitForClientClose)) {
            HyperionTransport transport = new ProtobufHyperionTransport(server.config());
            assertTrue(transport.isConnected());
            transport.close();
            transport.close();
            assertFalse(transport.isConnected());
        }
    }

    private static void assertImage(byte[] pixels) throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(connection -> {
            HyperionRequest request = connection.readProtobufRequest();
            assertEquals(HyperionRequest.Command.IMAGE, request.getCommand());
            ImageRequest image = request.getExtension(ImageRequest.imageRequest);
            assertEquals(HyperionTransportTestServer.PRIORITY, image.getPriority());
            assertEquals(2, image.getImagewidth());
            assertEquals(1, image.getImageheight());
            assertEquals(900, image.getDuration());
            assertArrayEquals(pixels, image.getImagedata().toByteArray());
            connection.writeProtobufSuccess();
        }); HyperionTransport transport = new ProtobufHyperionTransport(server.config())) {
            transport.setImage(pixels, 2, 1, 900);
        }
    }
}
