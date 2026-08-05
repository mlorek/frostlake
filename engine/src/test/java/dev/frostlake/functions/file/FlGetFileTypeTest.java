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
 * {@code FL_GET_FILE_TYPE(file)} — the file's category name.
 *
 * <p>Every expectation was measured against live Snowflake. The function
 * classifies on the descriptor's {@code CONTENT_TYPE} and nothing else, by exact match against a closed
 * set — which is why so many near-miss content types answer {@code unknown}.
 */
public class FlGetFileTypeTest extends FileFunctionTestSupport {

    private String fileType(final String contentType) {
        return scalar("SELECT FL_GET_FILE_TYPE(" + fileOf(contentType) + ")");
    }

    /** Live: the five categories, one representative content type each. */
    @Test
    public void namesEachCategory() {
        assertEquals("image", fileType("image/png"));
        assertEquals("video", fileType("video/mp4"));
        assertEquals("audio", fileType("audio/mpeg"));
        assertEquals("document", fileType("text/plain"));
        assertEquals("compressed", fileType("application/zip"));
    }

    /**
     * Live: classification reads CONTENT_TYPE, never the path. A descriptor whose RELATIVE_PATH says
     * {@code .txt} while its CONTENT_TYPE says {@code image/png} classifies as an image, and the mirror
     * case classifies as a document. This is the experiment that rules out any path-based rule.
     */
    @Test
    public void classifiesOnContentTypeNotOnThePath() {
        assertEquals("image", scalar("SELECT FL_GET_FILE_TYPE(TRY_TO_FILE(OBJECT_CONSTRUCT("
            + "'STAGE', '@D.S.ST', 'RELATIVE_PATH', 'x.txt', 'SIZE', 1,"
            + " 'LAST_MODIFIED', '" + LAST_MODIFIED + "', 'CONTENT_TYPE', 'image/png', 'ETAG', 'e1')))"));
        assertEquals("document", scalar("SELECT FL_GET_FILE_TYPE(TRY_TO_FILE(OBJECT_CONSTRUCT("
            + "'STAGE', '@D.S.ST', 'RELATIVE_PATH', 'x.png', 'SIZE', 1,"
            + " 'LAST_MODIFIED', '" + LAST_MODIFIED + "', 'CONTENT_TYPE', 'text/plain', 'ETAG', 'e1')))"));
    }

    /**
     * Live: an {@code .avi} is in BOTH the video and the audio set, and FL_GET_FILE_TYPE resolves the
     * overlap as {@code video}. This is why FL_IS_x is not FL_GET_FILE_TYPE = 'x'.
     */
    @Test
    public void aviIsVideoDespiteAlsoBeingAudio() {
        assertEquals("video", fileType("video/x-msvideo"));
        assertEquals("TRUE", scalar("SELECT FL_IS_AUDIO(" + fileOf("video/x-msvideo") + ")").toUpperCase());
    }

    /**
     * Live: the sets are CLOSED and exact — there is no {@code image/*} style prefix rule. Every one of
     * these bogus subtypes answers {@code unknown}.
     */
    @Test
    public void unknownSubtypesAreNotClassifiedByPrefix() {
        assertEquals("unknown", fileType("image/x-bogus"));
        assertEquals("unknown", fileType("video/x-bogus"));
        assertEquals("unknown", fileType("audio/x-bogus"));
        assertEquals("unknown", fileType("text/x-bogus"));
        assertEquals("unknown", fileType("application/x-bogus"));
    }

    /**
     * Live: matching is case-sensitive and untrimmed, and MIME parameters are not stripped — each of
     * these is a near-miss of a content type that IS classified.
     */
    @Test
    public void matchingIsExactTextual() {
        assertEquals("unknown", fileType("IMAGE/PNG"));
        assertEquals("unknown", fileType(" image/png"));
        assertEquals("unknown", fileType("image/png "));
        assertEquals("unknown", fileType("application/gzip;charset=utf-8"));
    }

    /**
     * Live: membership inside a family is arbitrary and must not be guessed. Each pair below has one
     * member and one non-member of the same family.
     */
    @Test
    public void familyMembershipIsArbitrary() {
        assertEquals("audio", fileType("audio/mpeg"));
        assertEquals("unknown", fileType("audio/mp4"));

        assertEquals("document", fileType("text/plain"));
        assertEquals("unknown", fileType("text/tab-separated-values"));

        assertEquals("compressed", fileType("application/gzip"));
        assertEquals("unknown", fileType("application/x-gzip"));

        assertEquals("compressed", fileType("application/zip"));
        assertEquals("unknown", fileType("application/x-7z-compressed"));
    }

    /**
     * Live: FL_GET_FILE_TYPE does NOT propagate NULL — a NULL file, and a descriptor with no
     * CONTENT_TYPE, both answer the STRING {@code 'unknown'}.
     */
    @Test
    public void nullFileIsUnknownNotNull() {
        assertEquals("unknown", scalar("SELECT FL_GET_FILE_TYPE(NULL)"));
        assertEquals("unknown", scalar("SELECT FL_GET_FILE_TYPE(TO_FILE(NULL))"));
    }
}
