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
 * {@code FL_IS_VIDEO(file)} — whether the file's {@code CONTENT_TYPE} is one of the video types.
 *
 * <p>Every expectation was measured against live Snowflake. The predicate is
 * an EXACT MATCH against a closed set of content types, so the widely-used non-standard spellings of a
 * type that IS in the set — {@code video/avi} and {@code video/msvideo} for {@code video/x-msvideo} —
 * are FALSE. Membership was enumerated by probing, not derived from the names.
 */
public class FlIsVideoTest extends FileFunctionTestSupport {

    private String isVideo(final String contentType) {
        return scalar("SELECT FL_IS_VIDEO(" + fileOf(contentType) + ")").toUpperCase();
    }

    /** Live: every content type measured TRUE, enumerated. */
    @Test
    public void trueForEveryMeasuredVideoContentType() {
        assertEquals("TRUE", isVideo("video/mp4"));
        assertEquals("TRUE", isVideo("video/mpeg"));
        assertEquals("TRUE", isVideo("video/quicktime"));
        assertEquals("TRUE", isVideo("video/x-msvideo"));
        assertEquals("TRUE", isVideo("video/webm"));
        assertEquals("TRUE", isVideo("video/x-matroska"));
        assertEquals("TRUE", isVideo("video/x-ms-wmv"));
        assertEquals("TRUE", isVideo("video/x-ms-asf"));
        assertEquals("TRUE", isVideo("video/ogg"));
        assertEquals("TRUE", isVideo("video/3gpp"));
        assertEquals("TRUE", isVideo("video/x-flv"));
        assertEquals("TRUE", isVideo("video/mp2t"));
    }

    /**
     * Live: the near misses. An {@code .avi} is {@code video/x-msvideo} and nothing else — neither
     * {@code video/avi} nor {@code video/msvideo} is in the set; {@code video/mp2t} is in but the MPEG-TS
     * spelling {@code video/vnd.dlna.mpeg-tts} is not; and a sibling family does not carry over.
     */
    @Test
    public void falseForNearMissesAndForOtherFamilies() {
        assertEquals("FALSE", isVideo("video/avi"));
        assertEquals("FALSE", isVideo("video/msvideo"));
        assertEquals("FALSE", isVideo("video/vnd.dlna.mpeg-tts"));
        assertEquals("FALSE", isVideo("video/x-bogus"));
        assertEquals("FALSE", isVideo("audio/mpeg"));
    }

    /**
     * Live: the category sets OVERLAP. {@code video/x-msvideo} (an {@code .avi}) satisfies BOTH
     * FL_IS_VIDEO and FL_IS_AUDIO, while FL_GET_FILE_TYPE resolves the overlap to the single name
     * {@code video}. So {@code FL_IS_x(f)} is NOT {@code FL_GET_FILE_TYPE(f) = 'x'} — each predicate is
     * its own membership test.
     */
    @Test
    public void aviIsVideoAndAudioAtOnce() {
        assertEquals("TRUE", isVideo("video/x-msvideo"));
        assertEquals("TRUE", scalar("SELECT FL_IS_AUDIO(" + fileOf("video/x-msvideo") + ")").toUpperCase());
        assertEquals("video", scalar("SELECT FL_GET_FILE_TYPE(" + fileOf("video/x-msvideo") + ")"));
    }

    /**
     * Live: FL_IS_VIDEO does NOT propagate NULL — a NULL argument, and the NULL that {@code TO_FILE(NULL)}
     * yields, both answer FALSE rather than NULL.
     */
    @Test
    public void nullFileIsFalseNotNull() {
        assertEquals("FALSE", scalar("SELECT FL_IS_VIDEO(NULL)").toUpperCase());
        assertEquals("FALSE", scalar("SELECT FL_IS_VIDEO(TO_FILE(NULL))").toUpperCase());
    }
}
