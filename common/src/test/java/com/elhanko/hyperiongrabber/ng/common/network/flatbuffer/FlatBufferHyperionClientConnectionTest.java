package com.elhanko.hyperiongrabber.ng.common.network.flatbuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.elhanko.hyperiongrabber.ng.common.network.HyperionProtocolException;
import com.elhanko.hyperiongrabber.ng.common.network.HyperionServerException;
import com.elhanko.hyperiongrabber.ng.common.network.HyperionTimeoutException;

import org.junit.Test;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import hyperionnet.Command;
import hyperionnet.Register;
import hyperionnet.Request;

public class FlatBufferHyperionClientConnectionTest {
    @Test
    public void rejectsNullOrEmptyHost() {
        assertThrows(IllegalArgumentException.class,
                () -> newClientWithParameters(null, 1, 150, "origin", 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> newClientWithParameters("  ", 1, 150, "origin", 1, 1));
    }

    @Test
    public void rejectsPortOutsideUnsignedTcpRange() {
        assertThrows(IllegalArgumentException.class,
                () -> newClientWithParameters("localhost", 0, 150, "origin", 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> newClientWithParameters("localhost", 65_536, 150, "origin", 1, 1));
    }

    @Test
    public void rejectsPriorityOutsideFlatBufferRange() {
        assertThrows(IllegalArgumentException.class,
                () -> newClientWithParameters("localhost", 1, 99, "origin", 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> newClientWithParameters("localhost", 1, 200, "origin", 1, 1));
    }

    @Test
    public void rejectsNullOrEmptyOrigin() {
        assertThrows(IllegalArgumentException.class,
                () -> newClientWithParameters("localhost", 1, 150, null, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> newClientWithParameters("localhost", 1, 150, "\t", 1, 1));
    }

    @Test
    public void rejectsNonPositiveTimeouts() {
        assertThrows(IllegalArgumentException.class,
                () -> newClientWithParameters("localhost", 1, 150, "origin", 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> newClientWithParameters("localhost", 1, 150, "origin", 1, 0));
    }

    @Test
    public void connectsAndBecomesReadyAfterRegistration() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.expectRegister();
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);
            connection.waitForClientClose();
        }); FlatBufferHyperionClient client = server.connect()) {
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void registerIsFirstRequestWithExactOriginAndPriority() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            Request request = connection.readRequest();
            assertEquals(Command.Register, request.commandType());
            Register register = (Register) request.command(new Register());
            assertEquals(FlatBufferFakeServer.ORIGIN, register.origin());
            assertEquals(FlatBufferFakeServer.PRIORITY, register.priority());
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);
        }); FlatBufferHyperionClient client = server.connect()) {
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void requestHeaderIsBigEndianAndPayloadIsNotSizePrefixed() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            FlatBufferFakeServer.Frame frame = connection.readFrame();
            assertEquals(frame.payload.length, frame.bigEndianLength());

            int regularRootOffset = ByteBuffer.wrap(frame.payload)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getInt();
            assertNotEquals(frame.payload.length - 4, regularRootOffset);
            assertEquals(Command.Register,
                    FlatBufferFakeServer.parseRequest(frame.payload).commandType());
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);
        }); FlatBufferHyperionClient client = server.connect()) {
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void retriesThenRejectsWrongRegisteredValue() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            for (int attempt = 0;
                    attempt < FlatBufferHyperionClient.MAX_REGISTRATION_ATTEMPTS;
                    attempt++) {
                connection.expectRegister();
                connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY - 1);
            }
            connection.waitForClientClose();
        })) {
            HyperionProtocolException failure = assertThrows(
                    HyperionProtocolException.class,
                    server::connect);
            assertTrue(failure.getMessage().contains("not acknowledged"));
        }
    }

    @Test
    public void repeatedRegistrationRequiredRepliesAreBounded() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            for (int attempt = 0;
                    attempt < FlatBufferHyperionClient.MAX_REGISTRATION_ATTEMPTS;
                    attempt++) {
                connection.expectRegister();
                connection.writeReply(null, -1, -1);
            }
            connection.setReadTimeout(200);
            assertThrows(IOException.class, connection::readRequest);
        })) {
            assertThrows(HyperionProtocolException.class, server::connect);
        }
    }

    @Test
    public void registerErrorClosesConstructionSocket() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.expectRegister();
            connection.writeReply("priority is unavailable", -1, -1);
            connection.waitForClientClose();
        })) {
            HyperionServerException failure = assertThrows(
                    HyperionServerException.class,
                    server::connect);
            assertEquals("priority is unavailable", failure.getMessage());
        }
    }

    @Test
    public void emptyRegisterErrorUsesGenericServerMessage() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.expectRegister();
            connection.writeEmptyErrorReply();
            connection.waitForClientClose();
        })) {
            HyperionServerException failure = assertThrows(
                    HyperionServerException.class,
                    server::connect);
            assertTrue(failure.getMessage().contains("rejected"));
        }
    }

    @Test
    public void eofDuringRegisterClosesConstructionSocket() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(
                FlatBufferFakeServer.Connection::expectRegister)) {
            assertThrows(EOFException.class, server::connect);
        }
    }

    @Test
    public void truncatedRegisterHeaderIsReportedAsEof() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.expectRegister();
            connection.writeBytes(new byte[] {0, 0});
            connection.shutdownOutput();
        })) {
            EOFException failure = assertThrows(EOFException.class, server::connect);
            assertTrue(failure.getMessage().contains("4-byte header"));
        }
    }

    @Test
    public void readTimeoutDuringRegisterClosesConstructionSocket() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.expectRegister();
            connection.waitForClientClose();
        })) {
            assertThrows(HyperionTimeoutException.class, () -> server.connect(1_000, 80));
        }
    }

    @Test
    public void connectFailureDoesNotLeaveAConnectedClient() throws Exception {
        String host;
        int port;
        try (ServerSocket closedServer = new ServerSocket(
                0, 1, InetAddress.getLoopbackAddress())) {
            host = closedServer.getInetAddress().getHostAddress();
            port = closedServer.getLocalPort();
        }

        assertThrows(IOException.class, () -> new FlatBufferHyperionClient(
                host,
                port,
                FlatBufferFakeServer.PRIORITY,
                FlatBufferFakeServer.ORIGIN,
                250,
                250));
    }

    @Test
    public void readsFragmentedRegisterReplyHeader() throws Exception {
        byte[] reply = FlatBufferFakeServer.replyPayload(
                null, -1, FlatBufferFakeServer.PRIORITY);
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.expectRegister();
            connection.writeFragmentedFrame(reply, true, false);
        }); FlatBufferHyperionClient client = server.connect()) {
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void readsFragmentedRegisterReplyPayload() throws Exception {
        byte[] reply = FlatBufferFakeServer.replyPayload(
                null, -1, FlatBufferFakeServer.PRIORITY);
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.expectRegister();
            connection.writeFragmentedFrame(reply, false, true);
        }); FlatBufferHyperionClient client = server.connect()) {
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void videoStateReplyDoesNotReplaceRegistrationAcknowledgment() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.expectRegister();
            connection.writeReply(null, 1, -1);
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);
        }); FlatBufferHyperionClient client = server.connect()) {
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void rejectsZeroReplyLengthBeforeAllocation() throws Exception {
        assertInvalidRegisterReplyLength(0);
    }

    @Test
    public void rejectsUnsignedReplyLengthBeforeAllocation() throws Exception {
        assertInvalidRegisterReplyLength(0xffff_ffffL);
    }

    @Test
    public void rejectsReplyAboveLocalMaximumBeforeAllocation() throws Exception {
        assertInvalidRegisterReplyLength(
                FlatBufferHyperionClient.MAX_REPLY_PAYLOAD_SIZE + 1L);
    }

    @Test
    public void truncatedRegisterPayloadIsReportedAsEof() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.expectRegister();
            connection.writeHeader(16);
            connection.writeBytes(new byte[] {1, 2});
            connection.shutdownOutput();
        })) {
            EOFException failure = assertThrows(EOFException.class, server::connect);
            assertTrue(failure.getMessage().contains("payload"));
        }
    }

    @Test
    public void invalidFlatBufferRegisterReplyBecomesProtocolException() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.expectRegister();
            connection.writeFrame(new byte[] {0, 0, 0, 0});
            connection.waitForClientClose();
        })) {
            assertThrows(HyperionProtocolException.class, server::connect);
        }
    }

    private static void assertInvalidRegisterReplyLength(long size) throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.expectRegister();
            connection.writeHeader(size);
            connection.waitForClientClose();
        })) {
            HyperionProtocolException failure = assertThrows(
                    HyperionProtocolException.class,
                    server::connect);
            assertTrue(failure.getMessage().contains("length"));
        }
    }

    private static FlatBufferHyperionClient newClientWithParameters(
            String host,
            int port,
            int priority,
            String origin,
            int connectTimeoutMs,
            int readTimeoutMs) throws IOException {
        return new FlatBufferHyperionClient(
                host, port, priority, origin, connectTimeoutMs, readTimeoutMs);
    }
}
