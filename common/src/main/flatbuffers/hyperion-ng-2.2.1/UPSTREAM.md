# Hyperion NG 2.2.1 FlatBuffer schema provenance

## Purpose

These schemas are the immutable protocol inputs used to generate Java classes
for the experimental Hyperion FlatBuffer work. Stage 1 does not connect to a
FlatBuffer server and does not make FlatBuffer a runtime transport.

## Hyperion source

- Repository: <https://github.com/hyperion-project/hyperion.ng>
- Tag: `2.2.1`
- Commit: `1a003600361118483ecfc6445a549d3703f9e063`
- Retrieved: August 2, 2026

| Local file | Upstream path | Immutable retrieval URL | SHA-256 |
| --- | --- | --- | --- |
| `hyperion_request.fbs` | `libsrc/flatbufserver/hyperion_request.fbs` | <https://raw.githubusercontent.com/hyperion-project/hyperion.ng/2.2.1/libsrc/flatbufserver/hyperion_request.fbs> | `63213a82d8c813556573ab13ead1549d5686627ce5aa5568617fddb37ffde721` |
| `hyperion_reply.fbs` | `libsrc/flatbufserver/hyperion_reply.fbs` | <https://raw.githubusercontent.com/hyperion-project/hyperion.ng/2.2.1/libsrc/flatbufserver/hyperion_reply.fbs> | `37c2328e6429682c57b2e5bcdd75ba403911095eac7d52a976c483862bf02996` |

Both local files are byte-for-byte copies. Neither schema contains an `include`
directive, and Hyperion's FlatBuffer build compiles exactly these two schema
inputs. No additional `.fbs` file is required for Java generation.

## Generator and runtime

- Official FlatBuffers release: `v25.9.23`
- Hyperion-pinned FlatBuffers commit:
  `187240970746d00bbd26b0f5873ed54d2477f9f3`
- Linux x86-64 release asset: `Linux.flatc.binary.g++-13.zip`
- Asset URL:
  <https://github.com/google/flatbuffers/releases/download/v25.9.23/Linux.flatc.binary.g%2B%2B-13.zip>
- Asset SHA-256:
  `de0c6ad114a5a686ecf64322528c602c7d4512446a93f290f54f00ee5abea487`
- Java runtime: `com.google.flatbuffers:flatbuffers-java:25.9.23`

The asset digest is published in the official GitHub release metadata and is
verified again by the Docker build before the binary is extracted or executed.

## Licenses and notices

The copied Hyperion schemas are distributed under the Hyperion project's MIT
license:

```text
MIT License

Copyright (c) 2014-2026 Hyperion Project

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

The FlatBuffers compiler and Java runtime are distributed under the Apache
License 2.0, copyright Google Inc. The complete upstream license is available at
<https://github.com/google/flatbuffers/blob/v25.9.23/LICENSE>. The Maven runtime
also carries its official license metadata. These notices supplement the
project's own [`LICENSE.txt`](../../../../../LICENSE.txt).

## Updating to a future Hyperion release

1. Select an immutable official Hyperion tag and record its commit.
2. Audit the FlatBuffer client and server sources and all schema `include`
   directives at that tag.
3. Copy only the required official schemas without editing them.
4. Recompute and record every SHA-256 digest, then compare the schema diff.
5. Determine the exact FlatBuffers version pinned by that Hyperion tag.
6. Pin an official architecture-appropriate `flatc` artifact and its published
   digest, and pin the matching Java runtime.
7. Run deterministic generation, all schema and existing tests, both APK builds,
   dependency checks, and APK-size measurements before accepting the update.
