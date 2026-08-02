package com.elhanko.hyperiongrabber.ng.common.network.flatbuffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.elhanko.hyperiongrabber.ng.common.network.HyperionProtocolException;
import com.elhanko.hyperiongrabber.ng.common.network.HyperionServerException;

import org.junit.Test;

import java.lang.reflect.Method;

import hyperionnet.Clear;
import hyperionnet.Color;
import hyperionnet.Command;
import hyperionnet.Image;
import hyperionnet.ImageType;
import hyperionnet.RawImage;
import hyperionnet.Request;

public class FlatBufferHyperionClientCommandTest {
    @Test
    public void sendsPackedRgbColorAndDurationExactly() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            Request request = connection.readRequest();
            assertEquals(Command.Color, request.commandType());
            Color color = (Color) request.command(new Color());
            assertEquals(0x123456, color.data());
            assertEquals(250, color.duration());
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);
        }); FlatBufferHyperionClient client = server.connect()) {
            client.setColor(0x123456, 250);
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void preservesContinuingColorDuration() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            Color color = (Color) connection.readRequest().command(new Color());
            assertEquals(-1, color.duration());
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);
        }); FlatBufferHyperionClient client = server.connect()) {
            client.setColor(0xabcdef, -1);
        }
    }

    @Test
    public void validColorServerErrorLeavesConnectionReusable() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            assertEquals(Command.Color, connection.readRequest().commandType());
            connection.writeReply("color rejected", -1, -1);
            assertEquals(Command.Color, connection.readRequest().commandType());
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);
        }); FlatBufferHyperionClient client = server.connect()) {
            HyperionServerException failure = assertThrows(
                    HyperionServerException.class,
                    () -> client.setColor(0x123456, 100));
            assertEquals("color rejected", failure.getMessage());
            assertTrue(client.isConnected());

            client.setColor(0x654321, 100);
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void emptyCommandErrorUsesGenericServerMessageAndKeepsConnection() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            connection.readRequest();
            connection.writeEmptyErrorReply();
            connection.waitForClientClose();
        }); FlatBufferHyperionClient client = server.connect()) {
            HyperionServerException failure = assertThrows(
                    HyperionServerException.class,
                    () -> client.setColor(0, 1));
            assertTrue(failure.getMessage().contains("rejected"));
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void sendsRgb24RawImageExactly() throws Exception {
        byte[] pixels = {0, 1, 2, 3, 4, 5};
        assertRawImage(pixels, 2, 1, 500);
    }

    @Test
    public void sendsRgb32RawImageIncludingFourthByteExactly() throws Exception {
        byte[] pixels = {0, 1, 2, 3, 4, 5, 6, 7};
        assertRawImage(pixels, 2, 1, -1);
    }

    @Test
    public void rejectsShortRgbDataBeforeWritingRequest() throws Exception {
        assertInvalidImageBeforeNetwork(new byte[11], 2, 2);
    }

    @Test
    public void rejectsOversizedRgbaDataBeforeWritingRequest() throws Exception {
        assertInvalidImageBeforeNetwork(new byte[17], 2, 2);
    }

    @Test
    public void rejectsEmptyImageDataBeforeWritingRequest() throws Exception {
        assertInvalidImageBeforeNetwork(new byte[0], 1, 1);
    }

    @Test
    public void rejectsNullImageDataBeforeWritingRequest() throws Exception {
        try (FlatBufferFakeServer server = noRequestAfterRegistrationServer();
                FlatBufferHyperionClient client = server.connect()) {
            assertThrows(NullPointerException.class,
                    () -> client.setImage(null, 1, 1, -1));
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void rejectsZeroAndNegativeImageDimensionsBeforeWritingRequest() throws Exception {
        try (FlatBufferFakeServer server = noRequestAfterRegistrationServer();
                FlatBufferHyperionClient client = server.connect()) {
            assertThrows(IllegalArgumentException.class,
                    () -> client.setImage(new byte[3], 0, 1, -1));
            assertThrows(IllegalArgumentException.class,
                    () -> client.setImage(new byte[3], 1, -1, -1));
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void rejectsImageSizeArithmeticOverflowBeforeWritingRequest() throws Exception {
        try (FlatBufferFakeServer server = noRequestAfterRegistrationServer();
                FlatBufferHyperionClient client = server.connect()) {
            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.setImage(
                            new byte[1], Integer.MAX_VALUE, Integer.MAX_VALUE, -1));
            assertTrue(failure.getMessage().contains("overflow"));
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void rejectsRequestAboveLocalMaximumBeforeWriting() throws Exception {
        byte[] pixels = new byte[FlatBufferHyperionClient.MAX_REQUEST_PAYLOAD_SIZE];
        try (FlatBufferFakeServer server = noRequestAfterRegistrationServer();
                FlatBufferHyperionClient client = server.connect()) {
            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.setImage(pixels, 4096, 4096, -1));
            assertTrue(failure.getMessage().contains("leaves no room"));
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void clearUsesOnlyRegisteredPriorityAndBecomesNotReady() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            Request request = connection.readRequest();
            assertEquals(Command.Clear, request.commandType());
            Clear clear = (Clear) request.command(new Clear());
            assertEquals(FlatBufferFakeServer.PRIORITY, clear.priority());
            connection.writeReply(null, -1, -1);
            connection.waitForClientClose();
        }); FlatBufferHyperionClient client = server.connect()) {
            client.clear();
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void nextCommandReregistersAfterOwnPriorityClear() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            assertEquals(Command.Clear, connection.readRequest().commandType());
            connection.writeReply(null, -1, -1);

            connection.expectRegister();
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);
            assertEquals(Command.Color, connection.readRequest().commandType());
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);
        }); FlatBufferHyperionClient client = server.connect()) {
            client.clear();
            assertFalse(client.isConnected());
            client.setColor(0x010203, 100);
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void failedReregistrationAfterClearClosesClient() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            connection.readRequest();
            connection.writeReply(null, -1, -1);
            connection.expectRegister();
            connection.writeReply("registration rejected", -1, -1);
            connection.waitForClientClose();
        }); FlatBufferHyperionClient client = server.connect()) {
            client.clear();
            assertThrows(HyperionServerException.class,
                    () -> client.setColor(0, 1));
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void wrongReregistrationAcknowledgmentsAreBoundedAndCloseClient() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            connection.readRequest();
            connection.writeReply(null, -1, -1);
            for (int attempt = 0;
                    attempt < FlatBufferHyperionClient.MAX_REGISTRATION_ATTEMPTS;
                    attempt++) {
                connection.expectRegister();
                connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY - 1);
            }
            connection.waitForClientClose();
        }); FlatBufferHyperionClient client = server.connect()) {
            client.clear();
            assertThrows(HyperionProtocolException.class,
                    () -> client.setColor(0, 1));
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void unsolicitedRegistrationRequiredIsConsumedBeforeCommandReply() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            assertEquals(Command.Color, connection.readRequest().commandType());
            connection.writeReply(null, -1, -1);
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);

            connection.expectRegister();
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);
            assertEquals(Command.Color, connection.readRequest().commandType());
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);
        }); FlatBufferHyperionClient client = server.connect()) {
            client.setColor(0, 1);
            assertFalse(client.isConnected());
            client.setColor(1, 1);
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void repeatedRegistrationRequiredStateRepliesCloseClient() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            connection.readRequest();
            for (int i = 0; i < 3; i++) {
                connection.writeReply(null, -1, -1);
            }
            connection.waitForClientClose();
        }); FlatBufferHyperionClient client = server.connect()) {
            assertThrows(HyperionProtocolException.class,
                    () -> client.setColor(0, 1));
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void videoStateReplyIsConsumedBeforeCommandReply() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            connection.readRequest();
            connection.writeReply(null, 2, -1);
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);
        }); FlatBufferHyperionClient client = server.connect()) {
            client.setColor(0, 1);
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void twoBufferedRepliesRemainSeparateForTwoRequests() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            assertEquals(Command.Color, connection.readRequest().commandType());
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);
            assertEquals(Command.Color, connection.readRequest().commandType());
        }); FlatBufferHyperionClient client = server.connect()) {
            client.setColor(1, 1);
            client.setColor(2, 1);
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void publicApiDoesNotExposeClearAllOrRawRequestMethods() {
        for (Method method : FlatBufferHyperionClient.class.getMethods()) {
            assertFalse(method.getName().equals("clearAll"));
            assertFalse(method.getName().equals("sendRequest"));
        }
    }

    private static void assertRawImage(
            byte[] pixels, int width, int height, int durationMs) throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            Request request = connection.readRequest();
            assertEquals(Command.Image, request.commandType());
            Image image = (Image) request.command(new Image());
            assertEquals(ImageType.RawImage, image.dataType());
            assertEquals(durationMs, image.duration());
            RawImage rawImage = (RawImage) image.data(new RawImage());
            assertEquals(width, rawImage.width());
            assertEquals(height, rawImage.height());

            byte[] actual = new byte[rawImage.dataLength()];
            for (int i = 0; i < actual.length; i++) {
                actual[i] = (byte) rawImage.data(i);
            }
            assertArrayEquals(pixels, actual);
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);
        }); FlatBufferHyperionClient client = server.connect()) {
            client.setImage(pixels, width, height, durationMs);
            assertTrue(client.isConnected());
        }
    }

    private static void assertInvalidImageBeforeNetwork(byte[] data, int width, int height)
            throws Exception {
        try (FlatBufferFakeServer server = noRequestAfterRegistrationServer();
                FlatBufferHyperionClient client = server.connect()) {
            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.setImage(data, width, height, -1));
            assertTrue(failure.getMessage().contains("actual="));
            assertTrue(client.isConnected());
        }
    }

    private static FlatBufferFakeServer noRequestAfterRegistrationServer() throws Exception {
        return new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            connection.waitForClientClose();
        });
    }
}
