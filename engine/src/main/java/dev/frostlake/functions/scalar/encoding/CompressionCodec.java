/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.functions.scalar.encoding;

import io.airlift.compress.bzip2.BZip2HadoopStreams;
import io.airlift.compress.hadoop.HadoopInputStream;
import io.airlift.compress.hadoop.HadoopOutputStream;
import io.airlift.compress.snappy.SnappyCompressor;
import io.airlift.compress.snappy.SnappyDecompressor;
import io.airlift.compress.zstd.ZstdCompressor;
import io.airlift.compress.zstd.ZstdInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * The four compression algorithms behind {@code COMPRESS} / {@code DECOMPRESS_STRING} /
 * {@code DECOMPRESS_BINARY}.
 *
 * <p>Byte fidelity against a live Snowflake account (probed, see
 * {@code CompressionByteFidelityTest} for the captured values):
 * <ul>
 *   <li><b>snappy</b> — byte-identical (raw Snappy block format, no framing).</li>
 *   <li><b>zlib</b> — byte-identical on every probed input at the default level and at levels 2-9;
 *       level 1 and some longer inputs differ because Snowflake's deflate implementation makes
 *       different match choices. The output is always a well-formed zlib stream either way.</li>
 *   <li><b>zstd</b> — byte-identical at the default level once the frame's content checksum is
 *       stripped (see {@link #stripZstdContentChecksum}); Snowflake does not emit one and
 *       aircompressor always does. Explicit levels are accepted but cannot be honoured —
 *       aircompressor exposes no level knob — so {@code zstd(19)} produces the default-level frame.
 *       Still round-trip compatible in both directions.</li>
 *   <li><b>bz2</b> — round-trip compatible only, never byte-identical: aircompressor's Huffman
 *       table selection differs from Snowflake's, and its block size is fixed at 9 whereas
 *       Snowflake defaults to 5. Verified live that Snowflake decompresses our {@code BZh9} output
 *       and that we decompress its {@code BZh5} output.</li>
 * </ul>
 */
public final class CompressionCodec {

    /** zstd's Frame_Header_Descriptor bit for "a 4-byte content checksum follows the blocks". */
    private static final int ZSTD_CONTENT_CHECKSUM_FLAG = 0x04;

    private static final int ZSTD_MAGIC_0 = 0x28;
    private static final int ZSTD_MAGIC_1 = 0xB5;
    private static final int ZSTD_MAGIC_2 = 0x2F;
    private static final int ZSTD_MAGIC_3 = 0xFD;

    private static final int COPY_BUFFER = 8192;

    private CompressionCodec() {}

    public static byte[] compress(final byte[] input, final CompressionMethod method) {
        try {
            switch (method.algorithm()) {
                case SNAPPY:
                    return snappyCompress(input);
                case ZLIB:
                    return zlibCompress(input, method.level());
                case ZSTD:
                    return zstdCompress(input);
                case BZ2:
                    return bz2Compress(input);
                default:
                    throw failure(method);
            }
        } catch (final Exception e) {
            throw failure(method);
        }
    }

    public static byte[] decompress(final byte[] compressed, final CompressionMethod method) {
        try {
            switch (method.algorithm()) {
                case SNAPPY:
                    return snappyDecompress(compressed);
                case ZLIB:
                    return drain(new InflaterInputStream(new ByteArrayInputStream(compressed)));
                case ZSTD:
                    return drain(new ZstdInputStream(new ByteArrayInputStream(compressed)));
                case BZ2:
                    return bz2Decompress(compressed);
                default:
                    throw failure(method);
            }
        } catch (final Exception e) {
            throw failure(method);
        }
    }

    /**
     * Snowflake's message for a payload the method cannot decode — it says "compress" in both
     * directions, and quotes the method argument exactly as written (SQLSTATE 22000, error 100195).
     */
    private static RuntimeException failure(final CompressionMethod method) {
        return new RuntimeException(
            "Can't compress data (too large output or invalid), method: '" + method.raw() + "'");
    }

    private static byte[] snappyCompress(final byte[] input) {
        final SnappyCompressor snappy = new SnappyCompressor();
        final byte[] out = new byte[snappy.maxCompressedLength(input.length)];
        final int n = snappy.compress(input, 0, input.length, out, 0, out.length);
        return Arrays.copyOf(out, n);
    }

    private static byte[] snappyDecompress(final byte[] compressed) {
        final int length = SnappyDecompressor.getUncompressedLength(compressed, 0);
        final byte[] out = new byte[length];
        new SnappyDecompressor().decompress(compressed, 0, compressed.length, out, 0, length);
        return out;
    }

    private static byte[] zlibCompress(final byte[] input, final int level) throws Exception {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Deflater deflater = new Deflater(zlibLevel(level));
        try (final DeflaterOutputStream dos = new DeflaterOutputStream(bos, deflater)) {
            dos.write(input);
        } finally {
            deflater.end();
        }
        return bos.toByteArray();
    }

    /**
     * Snowflake's level to zlib's: {@code 0} (or an omitted level) is the library default — NOT
     * zlib's "store uncompressed" level 0 — and anything above 9 clamps to 9.
     */
    private static int zlibLevel(final int level) {
        if (level == CompressionMethod.DEFAULT_LEVEL) {
            return Deflater.DEFAULT_COMPRESSION;
        }
        return level > 9 ? 9 : level;
    }

    private static byte[] zstdCompress(final byte[] input) {
        final ZstdCompressor compressor = new ZstdCompressor();
        final byte[] out = new byte[compressor.maxCompressedLength(input.length)];
        final int n = compressor.compress(input, 0, input.length, out, 0, out.length);
        return stripZstdContentChecksum(Arrays.copyOf(out, n));
    }

    /**
     * Drops the trailing 4-byte content checksum from a zstd frame and clears the flag that
     * announces it. aircompressor always writes one and offers no way to turn it off; Snowflake
     * never writes one, so keeping it would make every {@code COMPRESS(x, 'zstd')} five bytes
     * longer than the account's. The checksum is optional in the frame format, so both forms
     * decompress identically — this is a byte-for-byte alignment, not a semantic change.
     */
    private static byte[] stripZstdContentChecksum(final byte[] frame) {
        if (frame.length < 9
            || (frame[0] & 0xFF) != ZSTD_MAGIC_0 || (frame[1] & 0xFF) != ZSTD_MAGIC_1
            || (frame[2] & 0xFF) != ZSTD_MAGIC_2 || (frame[3] & 0xFF) != ZSTD_MAGIC_3
            || (frame[4] & ZSTD_CONTENT_CHECKSUM_FLAG) == 0) {
            return frame;
        }
        final byte[] stripped = Arrays.copyOf(frame, frame.length - 4);
        stripped[4] = (byte) (stripped[4] & ~ZSTD_CONTENT_CHECKSUM_FLAG);
        return stripped;
    }

    /**
     * bzip2 through aircompressor's stream pair. {@code BZip2HadoopStreams} is the only publicly
     * visible entry point in that package ({@code CBZip2OutputStream} is package-private), and
     * unlike {@code BZip2Codec} it does not extend the Hadoop {@code CodecAdapter}, so it keeps the
     * engine free of a runtime Hadoop dependency.
     */
    private static byte[] bz2Compress(final byte[] input) throws Exception {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final HadoopOutputStream out = new BZip2HadoopStreams().createOutputStream(bos);
        try {
            out.write(input, 0, input.length);
            out.finish();
        } finally {
            out.close();
        }
        return bos.toByteArray();
    }

    private static byte[] bz2Decompress(final byte[] compressed) throws Exception {
        final HadoopInputStream in =
            new BZip2HadoopStreams().createInputStream(new ByteArrayInputStream(compressed));
        return drain(in);
    }

    private static byte[] drain(final InputStream in) throws Exception {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            final byte[] buffer = new byte[COPY_BUFFER];
            int n;
            while ((n = in.read(buffer, 0, buffer.length)) != -1) {
                bos.write(buffer, 0, n);
            }
        } finally {
            in.close();
        }
        return bos.toByteArray();
    }
}
