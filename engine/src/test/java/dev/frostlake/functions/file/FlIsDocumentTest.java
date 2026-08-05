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

/**
 * {@code FL_IS_DOCUMENT(file)} — whether the file's {@code CONTENT_TYPE} is one of the document types.
 *
 * <p>Every expectation was measured against live Snowflake. The set spans two
 * families ({@code text/*} and {@code application/*}) but is still a CLOSED EXACT-MATCH list, not a
 * prefix rule: {@code text/plain} is a document while {@code text/tab-separated-values} and
 * {@code text/x-python} are not, and {@code application/pdf} is while {@code application/x-pdf} is not.
 * Membership was enumerated by probing, not derived from the names.
 */
public class FlIsDocumentTest extends FileFunctionTestSupport {

    private String isDocument(final String contentType) {
        return scalar("SELECT FL_IS_DOCUMENT(" + fileOf(contentType) + ")").toUpperCase();
    }

    /** Live: every {@code text/*} content type measured TRUE, enumerated. */
    @Test
    public void trueForEveryMeasuredTextDocumentType() {
        assertEquals("TRUE", isDocument("text/plain"));
        assertEquals("TRUE", isDocument("text/html"));
        assertEquals("TRUE", isDocument("text/csv"));
        assertEquals("TRUE", isDocument("text/markdown"));
        assertEquals("TRUE", isDocument("text/xml"));
        assertEquals("TRUE", isDocument("text/calendar"));
    }

    /** Live: every {@code application/*} content type measured TRUE, enumerated. */
    @Test
    public void trueForEveryMeasuredApplicationDocumentType() {
        assertEquals("TRUE", isDocument("application/json"));
        assertEquals("TRUE", isDocument("application/pdf"));
        assertEquals("TRUE", isDocument("application/xml"));
        assertEquals("TRUE", isDocument("application/msword"));
        assertEquals("TRUE", isDocument("application/rtf"));
        assertEquals("TRUE", isDocument("application/epub+zip"));
        assertEquals("TRUE", isDocument("application/vnd.ms-excel"));
        assertEquals("TRUE", isDocument("application/vnd.ms-powerpoint"));
        assertEquals("TRUE", isDocument("application/vnd.oasis.opendocument.text"));
        assertEquals("TRUE",
            isDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertEquals("TRUE",
            isDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertEquals("TRUE",
            isDocument("application/vnd.openxmlformats-officedocument.presentationml.presentation"));
    }

    /**
     * Live: the near misses. {@code text/csv} is in the set but its sibling
     * {@code text/tab-separated-values} is not; {@code application/rtf} is in but neither
     * {@code text/rtf} nor {@code text/richtext} is; {@code application/pdf} is in but
     * {@code application/x-pdf} is not. A file with no recognised extension —
     * {@code application/octet-stream} — is not a document either.
     */
    @Test
    public void falseForNearMissesAndForOtherFamilies() {
        assertEquals("FALSE", isDocument("text/tab-separated-values"));
        assertEquals("FALSE", isDocument("text/x-python"));
        assertEquals("FALSE", isDocument("text/richtext"));
        assertEquals("FALSE", isDocument("text/rtf"));
        assertEquals("FALSE", isDocument("application/x-pdf"));
        assertEquals("FALSE", isDocument("application/octet-stream"));
        assertEquals("FALSE", isDocument("image/png"));
    }

    /**
     * Live: FL_IS_DOCUMENT does NOT propagate NULL — a NULL argument, and the NULL that
     * {@code TO_FILE(NULL)} yields, both answer FALSE rather than NULL.
     */
    @Test
    public void nullFileIsFalseNotNull() {
        assertEquals("FALSE", scalar("SELECT FL_IS_DOCUMENT(NULL)").toUpperCase());
        assertEquals("FALSE", scalar("SELECT FL_IS_DOCUMENT(TO_FILE(NULL))").toUpperCase());
    }
}
