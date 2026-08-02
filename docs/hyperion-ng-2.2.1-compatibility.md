# Hyperion NG 2.2.1 Protocol Buffers compatibility

## Reference baseline

This compatibility work is based exclusively on the official Hyperion NG tag
[`2.2.1`](https://github.com/hyperion-project/hyperion.ng/tree/2.2.1). The protocol
and server behavior were checked against these tagged upstream files:

- [`libsrc/protoserver/message.proto`](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/protoserver/message.proto)
- [`libsrc/protoserver/ProtoClientConnection.cpp`](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/protoserver/ProtoClientConnection.cpp)
- [`libsrc/protoserver/ProtoClientConnection.h`](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/protoserver/ProtoClientConnection.h)

No nightly build or unversioned branch was used as a protocol reference.

## Wire protocol

Each request and reply is one Protocol Buffers message prefixed by a four-byte,
unsigned, big-endian payload length. The length does not include the header.
Header and body can be split across arbitrary TCP packets, and several framed
request/reply exchanges can use the same TCP connection.

Hyperion NG 2.2.1 accepts these `HyperionRequest.Command` values and extension
fields:

| Command | Value | Request data |
| --- | ---: | --- |
| `COLOR` | 1 | `ColorRequest` extension 10 |
| `IMAGE` | 2 | `ImageRequest` extension 11 |
| `CLEAR` | 3 | `ClearRequest` extension 12 |
| `CLEARALL` | 4 | no extension |

Color, image, and clear requests retain their tagged upstream field numbers.
The Hyperion ProtoServer accepts priorities from 100 through 199 for color and
image input. The Android client applies that range consistently to every
operation carrying a priority, including `CLEAR`.

An image has positive width and height and contains exactly either three bytes
per pixel (RGB) or four bytes per pixel (RGBA). For RGBA input, Hyperion uses
the RGB components and discards alpha.

The tagged `HyperionReply` contains required `type` and optional `success`,
`error`, and `video` fields. Normal command responses use type `REPLY` and set
`success` to true or false. On failure, the server supplies explanatory text in
`error`.

## Schema differences fixed in the Android client

The previous Android schema had drifted from Hyperion NG:

- it declared obsolete reply type `GRABBING = 2` and field `grabbing = 4`;
- it assigned `VIDEO = 3` instead of 2;
- it assigned `video` field number 5 instead of 4;
- its image comment did not record the server's RGB and RGBA support.

The synchronized schema is otherwise the exact tagged upstream schema. Only
the Android/Java generation options `java_package` and
`java_outer_classname` are added.

## Transport behavior

The Android transport owns one socket and one pair of buffered streams per
connection. Requests are serialized as complete header/body/flush/exact-reply
exchanges, so concurrent callers cannot interleave bytes or consume one
another's replies. TCP no-delay is enabled. Connect and read timeouts are
configurable.

Before writing, the client validates the command-specific extension, the
100–199 priority range, positive image dimensions, exact RGB/RGBA byte count,
and a maximum serialized request size of 64 MiB. Replies must have a positive
payload length no greater than the documented 1 MiB reply limit. Both header
and body are read fully without using `InputStream.available()`.

EOF, socket I/O failure, read timeout, invalid length, and malformed or
semantically invalid replies close the connection because it can no longer be
considered synchronized. A read timeout is reported distinctly. A valid
`success = false` reply becomes a server exception carrying Hyperion's error
text; it is not silently ignored. Missing `success`, a non-`REPLY` response, or
an unparseable payload is a protocol error. Closing is deterministic and
idempotent; finalization is not used. Application reconnect logic can create a
fresh transport after a failed connection.

## Remaining scope and migration note

Hyperion NG 2.2.1 still includes and supports the ProtoServer. Its
`ProtoClientConnection.cpp` source contains a TODO to remove that class after
third-party applications have migrated; this is a future intent, not evidence
that Protocol Buffers support is removed or unusable in 2.2.1.

This update deliberately uses Protocol Buffers through the ProtoServer.
FlatBuffer transport is a separate follow-up that needs its own compatibility
and migration evaluation.
Discovery, UI, SDK targets, MediaProjection, foreground services, package
names, and unrelated Android modernization are outside this work.
