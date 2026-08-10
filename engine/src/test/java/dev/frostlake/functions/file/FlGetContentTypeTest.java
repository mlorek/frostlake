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
 * {@code FL_GET_CONTENT_TYPE(file)} — the descriptor's {@code CONTENT_TYPE} field.
 *
 * <p>Every expectation was measured against live Snowflake. The accessor is a
 * plain field read; the interesting behaviour is upstream, in how {@code TO_FILE} DERIVES that field for
 * a staged file — from the file NAME's extension alone, never from its bytes.
 *
 * <p>This getter PROPAGATES NULL: {@code FL_GET_CONTENT_TYPE(NULL)} and
 * {@code FL_GET_CONTENT_TYPE(TO_FILE(NULL))} are both NULL. That CONTRASTS with
 * {@code FL_GET_FILE_TYPE}, which answers the string {@code 'unknown'} for a NULL file, and with the
 * {@code FL_IS_*} family, which answers FALSE — the classifiers never return NULL, the getters always do.
 */
public class FlGetContentTypeTest extends StagedFileTestSupport {

    private String contentType(final String fileExpression) {
        return scalar("SELECT FL_GET_CONTENT_TYPE(" + fileExpression + ")");
    }

    /** Live: {@code @sse/hello.txt} reported {@code text/plain}, and the accessor hands it straight back. */
    @Test
    public void readsTheDescriptorsContentType() {
        assertEquals("text/plain", contentType(helloTextFile()));
    }

    /** Live: the field is returned verbatim — no normalisation, no case folding, no parameter stripping. */
    @Test
    public void returnsTheFieldVerbatim() {
        assertEquals("image/png", contentType(fileOf("image/png")));
        assertEquals("IMAGE/PNG", contentType(fileOf("IMAGE/PNG")));
        assertEquals("application/gzip;charset=utf-8", contentType(fileOf("application/gzip;charset=utf-8")));
    }

    /**
     * Live: {@code TO_FILE} maps a staged file's EXTENSION to its content type, and an extension it does
     * not know — including no extension at all — is {@code application/octet-stream}.
     */
    @Test
    public void derivesTheContentTypeFromTheExtension() {
        stage("s.jpg", "jpeg placeholder\n");
        stage("s.md", "# heading\n");
        stage("s.zip", "zip placeholder\n");
        stage("s.tar", "tar placeholder\n");
        stage("noext", "no extension here\n");

        assertEquals("image/jpeg", contentType("TO_FILE('@st/s.jpg')"));
        assertEquals("text/markdown", contentType("TO_FILE('@st/s.md')"));
        assertEquals("application/zip", contentType("TO_FILE('@st/s.zip')"));
        assertEquals("application/x-tar", contentType("TO_FILE('@st/s.tar')"));
        assertEquals("application/octet-stream", contentType("TO_FILE('@st/noext')"));
    }

    /**
     * Live: the NAME wins over the BYTES. A file holding real PNG bytes but named {@code .txt} is
     * {@code text/plain} — the pair that rules out any content sniffing.
     */
    @Test
    public void extensionWinsOverTheBytes() {
        stageBytes("png_named.txt", PNG_BYTES);

        assertEquals("text/plain", contentType("TO_FILE('@st/png_named.txt')"));
    }

    /** Live: NULL in, NULL out — unlike FL_GET_FILE_TYPE, which would answer {@code 'unknown'} here. */
    @Test
    public void propagatesNull() {
        assertNull(contentType("NULL"));
        assertNull(contentType("TO_FILE(NULL)"));
    }
}
