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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code FL_GET_ETAG(file)} — the descriptor's {@code ETAG} field.
 *
 * <p>Every expectation was measured against live Snowflake. The ETAG is the
 * file's MD5 HEX: live it equalled the {@code md5} column of {@code LIST @stage} for every staged file on
 * a server-side-encrypted stage, which is why a fixture of known bytes can be pinned to a literal digest
 * here. It is a function of the BYTES only — not of the name, the stage or the modification time.
 *
 * <p>This getter PROPAGATES NULL: {@code FL_GET_ETAG(NULL)} and {@code FL_GET_ETAG(TO_FILE(NULL))} are
 * both NULL. That CONTRASTS with {@code FL_GET_FILE_TYPE} ({@code 'unknown'} for a NULL file) and the
 * {@code FL_IS_*} family (FALSE), neither of which ever returns NULL.
 */
public class FlGetEtagTest extends StagedFileTestSupport {

    /** The MD5 the live account reported for a {@code hello.txt} of exactly {@link #HELLO_BYTES}. */
    private static final String HELLO_ETAG = "b7dddf722cfdc51710087d369f8d9e6b";

    /** The 24 bytes behind {@link #HELLO_ETAG}. */
    private static final String HELLO_BYTES = "hello world\nsecond line\n";

    private String etag(final String fileExpression) {
        return scalar("SELECT FL_GET_ETAG(" + fileExpression + ")");
    }

    /** Live: the descriptor of {@code @sse/hello.txt} carried this MD5, and the accessor returns it. */
    @Test
    public void readsTheDescriptorsEtag() {
        assertEquals(HELLO_ETAG, etag(helloTextFile()));
    }

    /**
     * Live: the same 24 bytes on a real stage produce the same digest here — the literal is the live
     * account's own answer for identical content, not a locally computed expectation.
     */
    @Test
    public void computesTheMd5OfTheStagedBytes() {
        stage("hello.txt", HELLO_BYTES);

        assertEquals(HELLO_ETAG, etag("TO_FILE('@st/hello.txt')"));
    }

    /** Live: the digest is over the bytes alone, so two names holding the same content share an ETAG. */
    @Test
    public void identicalBytesUnderDifferentNamesShareAnEtag() {
        stage("hello.txt", HELLO_BYTES);
        stage("copy.log", HELLO_BYTES);
        stage("sub/deeper.txt", HELLO_BYTES);

        assertEquals(HELLO_ETAG, etag("TO_FILE('@st/copy.log')"));
        assertEquals(HELLO_ETAG, etag("TO_FILE('@st/sub/deeper.txt')"));
    }

    /** Live: change one byte and the ETAG changes — it is a digest, not a stable file id. */
    @Test
    public void changingTheBytesChangesTheEtag() {
        stage("hello.txt", HELLO_BYTES);
        stage("changed.txt", "hello world\nsecond line!\n");

        assertNotEquals(HELLO_ETAG, etag("TO_FILE('@st/changed.txt')"));
    }

    /** Live: NULL in, NULL out — unlike FL_GET_FILE_TYPE and the FL_IS_* family. */
    @Test
    public void propagatesNull() {
        assertNull(etag("NULL"));
        assertNull(etag("TO_FILE(NULL)"));
    }
}
