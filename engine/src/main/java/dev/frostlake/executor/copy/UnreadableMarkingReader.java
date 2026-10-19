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


package dev.frostlake.executor.copy;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

/**
 * A staged file's text in a character set, each byte the set cannot read kept as a marker that remembers it (see
 * {@link StagedFileIo#UNREADABLE_START}): the first byte of an unreadable sequence as one mark, every later byte of
 * the same sequence as another. A reader of the text finds them, and refuses the field holding them in the account's
 * words, the bytes spelled {@code 0xE9}, or replaces each sequence with U+FFFD, as REPLACE_INVALID_CHARACTERS says.
 */
final class UnreadableMarkingReader extends Reader {

    private static final int BUFFER = 8192;

    private final InputStream in;
    private final CharsetDecoder decoder;
    private final ByteBuffer bytes = ByteBuffer.allocate(BUFFER);
    private final CharBuffer chars = CharBuffer.allocate(BUFFER);
    private boolean ended;
    private boolean flushed;

    UnreadableMarkingReader(final InputStream in, final Charset charset) {
        this.in = in;
        this.decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        bytes.flip();
        chars.flip();
    }

    @Override
    public int read(final char[] target, final int offset, final int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        if (!chars.hasRemaining() && !fill()) {
            return -1;
        }
        final int taken = Math.min(length, chars.remaining());
        chars.get(target, offset, taken);
        return taken;
    }

    /** Decodes the next characters; false once the file is over. */
    private boolean fill() throws IOException {
        chars.clear();
        try {
            while (chars.position() == 0 && !flushed) {
                if (ended && !bytes.hasRemaining()) {
                    decoder.decode(bytes, chars, true);
                    decoder.flush(chars);
                    flushed = true;
                    break;
                }
                final CoderResult result = decoder.decode(bytes, chars, ended);
                if (result.isMalformed() || result.isUnmappable()) {
                    if (chars.remaining() < result.length()) {
                        // No room for the marks: what is decoded goes first, the marks with the next pass.
                        break;
                    }
                    for (int i = 0; i < result.length(); i++) {
                        final int unread = bytes.get() & 0xFF;
                        chars.put((char) ((i == 0 ? StagedFileIo.UNREADABLE_START : StagedFileIo.UNREADABLE_MORE)
                            + unread));
                    }
                } else if (result.isUnderflow() && !ended) {
                    bytes.compact();
                    final int read = in.read(bytes.array(), bytes.position(), bytes.remaining());
                    if (read < 0) {
                        ended = true;
                    } else {
                        bytes.position(bytes.position() + read);
                    }
                    bytes.flip();
                } else if (result.isUnderflow()) {
                    // Every byte decoded: the next pass flushes the decoder.
                    continue;
                }
            }
        } finally {
            chars.flip();
        }
        return chars.hasRemaining();
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
