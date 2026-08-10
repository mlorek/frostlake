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

package dev.frostlake.functions.file;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code FL_GET_SIZE(file)} — the descriptor's {@code SIZE} field, the file's length in BYTES.
 *
 * <p>Every expectation was measured against live Snowflake. The count is
 * bytes, not characters and not lines: the live {@code hello.txt} of {@code "hello world\nsecond
 * line\n"} reported 24, both newlines included. The assertions compare text because
 * {@code FileFunctionTestSupport#scalar} renders the value.
 *
 * <p>This getter PROPAGATES NULL: {@code FL_GET_SIZE(NULL)} and {@code FL_GET_SIZE(TO_FILE(NULL))} are
 * both NULL. That CONTRASTS with {@code FL_GET_FILE_TYPE}, which answers {@code 'unknown'} for a NULL
 * file, and with the {@code FL_IS_*} family, which answers FALSE — a NULL file has no size, so there is
 * no zero to fall back on.
 */
public class FlGetSizeTest extends StagedFileTestSupport {

    private String size(final String fileExpression) {
        return scalar("SELECT FL_GET_SIZE(" + fileExpression + ")");
    }

    /** Live: {@code @sse/hello.txt} was 24 bytes, and the accessor reports that number. */
    @Test
    public void readsTheDescriptorsSize() {
        assertEquals("24", size(helloTextFile()));
    }

    /** Live: the size is read from the file itself, so known byte lengths come back exactly. */
    @Test
    public void reportsTheStagedFilesByteLength() {
        stage("hello.txt", "hello world\nsecond line\n");
        stage("empty.txt", "");
        stage("one.txt", "x");
        stageBytes("tiny.png", PNG_BYTES);

        assertEquals("24", size("TO_FILE('@st/hello.txt')"));
        assertEquals("0", size("TO_FILE('@st/empty.txt')"));
        assertEquals("1", size("TO_FILE('@st/one.txt')"));
        assertEquals(String.valueOf(PNG_BYTES.length), size("TO_FILE('@st/tiny.png')"));
    }

    /**
     * Live: bytes, not characters. A two-character string of one ASCII and one two-byte UTF-8 code point
     * is three bytes.
     */
    @Test
    public void countsBytesNotCharacters() {
        stage("utf8.txt", "aé");

        assertEquals("3", size("TO_FILE('@st/utf8.txt')"));
    }

    /** Live: NULL in, NULL out — never 0, and unlike FL_GET_FILE_TYPE and the FL_IS_* family. */
    @Test
    public void propagatesNull() {
        assertNull(size("NULL"));
        assertNull(size("TO_FILE(NULL)"));
    }
}
