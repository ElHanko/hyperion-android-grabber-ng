package com.elhanko.hyperiongrabber.ng.common.flatbuffers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.flatbuffers.FlatBufferBuilder;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import hyperionnet.Clear;
import hyperionnet.Color;
import hyperionnet.Command;
import hyperionnet.Image;
import hyperionnet.ImageType;
import hyperionnet.RawImage;
import hyperionnet.Register;
import hyperionnet.Reply;
import hyperionnet.Request;

public class HyperionFlatBufferSchemaTest {
    @Test
    public void generatedVersionGuardsMatchPinnedRuntime() {
        Request.ValidateVersion();
        Reply.ValidateVersion();
        Register.ValidateVersion();
        Color.ValidateVersion();
        Image.ValidateVersion();
        RawImage.ValidateVersion();
        Clear.ValidateVersion();
    }

    @Test
    public void registerRequestRoundTripsThroughOfficialSchema() {
        FlatBufferBuilder builder = new FlatBufferBuilder(128);
        int origin = builder.createString("Hyperion Grabber NG schema test");
        int register = Register.createRegister(builder, origin, 199);

        Request request = finishAndParseRequest(builder, Command.Register, register);
        assertEquals(Command.Register, request.commandType());

        Register parsed = (Register) request.command(new Register());
        assertEquals("Hyperion Grabber NG schema test", parsed.origin());
        assertEquals(199, parsed.priority());
    }

    @Test
    public void colorRequestRoundTripsThroughOfficialSchema() {
        FlatBufferBuilder builder = new FlatBufferBuilder(128);
        int color = Color.createColor(builder, 0x00112233, 1_000);

        Request request = finishAndParseRequest(builder, Command.Color, color);
        assertEquals(Command.Color, request.commandType());

        Color parsed = (Color) request.command(new Color());
        assertEquals(0x00112233, parsed.data());
        assertEquals(1_000, parsed.duration());
    }

    @Test
    public void rawImagePreservesThreeBytesPerPixelPayload() {
        assertRawImageRoundTrip(new byte[] {
                0x00, 0x11, 0x22,
                0x33, 0x44, 0x55
        }, 2, 1);
    }

    @Test
    public void rawImagePreservesFourBytesPerPixelPayload() {
        assertRawImageRoundTrip(new byte[] {
                0x00, 0x11, 0x22, 0x33,
                0x44, 0x55, 0x66, 0x77
        }, 2, 1);
    }

    @Test
    public void clearRequestRoundTripsThroughOfficialSchema() {
        FlatBufferBuilder builder = new FlatBufferBuilder(64);
        int clear = Clear.createClear(builder, 199);

        Request request = finishAndParseRequest(builder, Command.Clear, clear);
        assertEquals(Command.Clear, request.commandType());

        Clear parsed = (Clear) request.command(new Clear());
        assertEquals(199, parsed.priority());
    }

    @Test
    public void successAndErrorRepliesRoundTripThroughOfficialSchema() {
        FlatBufferBuilder successBuilder = new FlatBufferBuilder(64);
        int success = Reply.createReply(successBuilder, 0, 7, 199);
        Reply.finishReplyBuffer(successBuilder, success);

        ByteBuffer successBuffer = successBuilder.dataBuffer();
        int successPosition = successBuffer.position();
        Reply successReply = Reply.getRootAsReply(successBuffer);
        assertEquals(successPosition, successBuffer.position());
        assertEquals(ByteOrder.LITTLE_ENDIAN, successBuffer.order());
        assertNull(successReply.error());
        assertEquals(7, successReply.video());
        assertEquals(199, successReply.registered());

        FlatBufferBuilder errorBuilder = new FlatBufferBuilder(128);
        int message = errorBuilder.createString("schema-level test error");
        int error = Reply.createReply(errorBuilder, message, -1, -1);
        Reply.finishReplyBuffer(errorBuilder, error);

        ByteBuffer errorBuffer = errorBuilder.dataBuffer();
        int errorPosition = errorBuffer.position();
        Reply errorReply = Reply.getRootAsReply(errorBuffer);
        assertEquals(errorPosition, errorBuffer.position());
        assertEquals(ByteOrder.LITTLE_ENDIAN, errorBuffer.order());
        assertEquals("schema-level test error", errorReply.error());
        assertEquals(-1, errorReply.video());
        assertEquals(-1, errorReply.registered());
    }

    private static Request finishAndParseRequest(
            FlatBufferBuilder builder, byte commandType, int commandOffset) {
        int request = Request.createRequest(builder, commandType, commandOffset);
        Request.finishRequestBuffer(builder, request);

        ByteBuffer encoded = builder.dataBuffer();
        int position = encoded.position();
        Request parsed = Request.getRootAsRequest(encoded);
        assertEquals(position, encoded.position());
        assertEquals(ByteOrder.LITTLE_ENDIAN, encoded.order());
        return parsed;
    }

    private static void assertRawImageRoundTrip(byte[] pixels, int width, int height) {
        FlatBufferBuilder builder = new FlatBufferBuilder(128);
        int data = RawImage.createDataVector(builder, pixels);
        int rawImage = RawImage.createRawImage(builder, data, width, height);
        int image = Image.createImage(builder, ImageType.RawImage, rawImage, 1_000);

        Request request = finishAndParseRequest(builder, Command.Image, image);
        assertEquals(Command.Image, request.commandType());

        Image parsedImage = (Image) request.command(new Image());
        assertEquals(ImageType.RawImage, parsedImage.dataType());
        assertEquals(1_000, parsedImage.duration());

        RawImage parsedRawImage = (RawImage) parsedImage.data(new RawImage());
        assertEquals(width, parsedRawImage.width());
        assertEquals(height, parsedRawImage.height());
        assertEquals(pixels.length, parsedRawImage.dataLength());

        int[] expected = new int[pixels.length];
        int[] actual = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            expected[i] = pixels[i] & 0xff;
            actual[i] = parsedRawImage.data(i);
        }
        assertArrayEquals(expected, actual);
    }
}
