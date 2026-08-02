package com.elhanko.hyperiongrabber.ng.common.network.flatbuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.elhanko.hyperiongrabber.ng.common.network.HyperionProtocolException;
import com.elhanko.hyperiongrabber.ng.common.network.HyperionServerException;
import com.elhanko.hyperiongrabber.ng.common.network.HyperionTimeoutException;

import org.junit.Test;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import hyperionnet.Command;

public class FlatBufferHyperionClientLifecycleTest {
    @Test
    public void closeIsIdempotentAndClearsReadyState() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            connection.waitForClientClose();
        })) {
            FlatBufferHyperionClient client = server.connect();
            assertTrue(client.isConnected());
            client.close();
            client.close();
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void requestAfterCloseIsRejected() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            connection.waitForClientClose();
        })) {
            FlatBufferHyperionClient client = server.connect();
            client.close();
            assertThrows(SocketException.class, () -> client.setColor(0, 1));
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void normalRequestReadTimeoutClosesConnection() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            connection.readRequest();
            connection.waitForClientClose();
        }); FlatBufferHyperionClient client = server.connect(1_000, 80)) {
            assertThrows(HyperionTimeoutException.class,
                    () -> client.setColor(0, 1));
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void eofBeforeNormalReplyHeaderClosesConnection() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            connection.readRequest();
        }); FlatBufferHyperionClient client = server.connect()) {
            assertThrows(EOFException.class, () -> client.setColor(0, 1));
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void truncatedNormalReplyHeaderClosesConnection() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            connection.readRequest();
            connection.writeBytes(new byte[] {0, 0, 0});
            connection.shutdownOutput();
        }); FlatBufferHyperionClient client = server.connect()) {
            assertThrows(EOFException.class, () -> client.setColor(0, 1));
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void truncatedNormalReplyPayloadClosesConnection() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            connection.readRequest();
            connection.writeHeader(24);
            connection.writeBytes(new byte[] {1, 2, 3});
            connection.shutdownOutput();
        }); FlatBufferHyperionClient client = server.connect()) {
            assertThrows(EOFException.class, () -> client.setColor(0, 1));
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void socketResetDuringRequestClosesConnection() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            connection.readRequest();
            connection.closeWithReset();
        }); FlatBufferHyperionClient client = server.connect()) {
            assertThrows(IOException.class, () -> client.setColor(0, 1));
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void malformedNormalReplyIsControlledProtocolFailure() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            connection.readRequest();
            connection.writeFrame(new byte[] {4, 0, 0, 0, 0, 0, 0, 0});
            connection.waitForClientClose();
        }); FlatBufferHyperionClient client = server.connect()) {
            assertThrows(HyperionProtocolException.class,
                    () -> client.setColor(0, 1));
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void unexpectedPositiveRegisteredValueClosesConnection() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            connection.readRequest();
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY - 1);
            connection.waitForClientClose();
        }); FlatBufferHyperionClient client = server.connect()) {
            assertThrows(HyperionProtocolException.class,
                    () -> client.setColor(0, 1));
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void validClearServerErrorKeepsRegisteredConnection() throws Exception {
        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            assertEquals(Command.Clear, connection.readRequest().commandType());
            connection.writeReply("clear rejected", -1, -1);
            assertEquals(Command.Color, connection.readRequest().commandType());
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);
        }); FlatBufferHyperionClient client = server.connect()) {
            assertThrows(HyperionServerException.class, client::clear);
            assertTrue(client.isConnected());
            client.setColor(0, 1);
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void concurrentCallsAreFullySerialized() throws Exception {
        CountDownLatch firstRequestReceived = new CountDownLatch(1);
        CountDownLatch secondCallerStarted = new CountDownLatch(1);

        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            assertEquals(Command.Color, connection.readRequest().commandType());
            firstRequestReceived.countDown();
            assertTrue(secondCallerStarted.await(2, TimeUnit.SECONDS));

            connection.setReadTimeout(150);
            assertThrows(SocketTimeoutException.class, connection::readRequest);
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);

            connection.setReadTimeout(2_000);
            assertEquals(Command.Color, connection.readRequest().commandType());
            connection.writeReply(null, -1, FlatBufferFakeServer.PRIORITY);
        }); FlatBufferHyperionClient client = server.connect()) {
            ExecutorService callers = Executors.newFixedThreadPool(2);
            try {
                Future<?> first = callers.submit(() -> {
                    client.setColor(1, 1);
                    return null;
                });
                assertTrue(firstRequestReceived.await(2, TimeUnit.SECONDS));
                Future<?> second = callers.submit(() -> {
                    secondCallerStarted.countDown();
                    client.setColor(2, 1);
                    return null;
                });

                first.get(3, TimeUnit.SECONDS);
                second.get(3, TimeUnit.SECONDS);
                assertTrue(client.isConnected());
            } finally {
                callers.shutdownNow();
            }
        }
    }

    @Test
    public void closeInterruptsRunningExchangeWithoutBlocking() throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);

        try (FlatBufferFakeServer server = new FlatBufferFakeServer(connection -> {
            connection.acceptRegistration();
            connection.readRequest();
            requestReceived.countDown();
            connection.waitForClientClose();
        })) {
            FlatBufferHyperionClient client = server.connect();
            ExecutorService caller = Executors.newSingleThreadExecutor();
            try {
                Future<?> exchange = caller.submit(() -> {
                    client.setColor(0, 1);
                    return null;
                });
                assertTrue(requestReceived.await(2, TimeUnit.SECONDS));
                client.close();

                ExecutionException failure = assertThrows(
                        ExecutionException.class,
                        () -> exchange.get(2, TimeUnit.SECONDS));
                assertTrue(failure.getCause() instanceof IOException);
                assertFalse(client.isConnected());
            } finally {
                client.close();
                caller.shutdownNow();
            }
        }
    }
}
