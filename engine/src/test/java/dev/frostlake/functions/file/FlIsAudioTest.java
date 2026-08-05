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
 * {@code FL_IS_AUDIO(file)} — whether the file's {@code CONTENT_TYPE} is one of the audio types.
 *
 * <p>Every expectation was measured against live Snowflake, and this family is
 * the one that most rewards measuring over guessing. Membership is ARBITRARY: {@code audio/mpeg} is
 * audio, yet {@code audio/mp4}, {@code audio/mp3}, {@code audio/mpeg3} and {@code audio/x-mpeg} — all of
 * them plausible spellings of the same thing — are NOT. {@code audio/wav} and {@code audio/x-wav} are
 * both in, but of {@code audio/ogg} / {@code audio/vorbis} only the former is. And the set reaches
 * outside its own family: {@code video/x-msvideo} (an {@code .avi}) is AUDIO as well as video. None of
 * this is derivable from the names; every row below was probed.
 */
public class FlIsAudioTest extends FileFunctionTestSupport {

    private String isAudio(final String contentType) {
        return scalar("SELECT FL_IS_AUDIO(" + fileOf(contentType) + ")").toUpperCase();
    }

    /** Live: every content type measured TRUE, enumerated — including the {@code .avi} outlier. */
    @Test
    public void trueForEveryMeasuredAudioContentType() {
        assertEquals("TRUE", isAudio("audio/mpeg"));
        assertEquals("TRUE", isAudio("audio/wav"));
        assertEquals("TRUE", isAudio("audio/x-wav"));
        assertEquals("TRUE", isAudio("audio/ogg"));
        assertEquals("TRUE", isAudio("audio/flac"));
        assertEquals("TRUE", isAudio("audio/aac"));
        assertEquals("TRUE", isAudio("audio/x-m4a"));
        assertEquals("TRUE", isAudio("audio/midi"));
        assertEquals("TRUE", isAudio("audio/webm"));
        assertEquals("TRUE", isAudio("audio/x-aiff"));
        assertEquals("TRUE", isAudio("audio/opus"));
        assertEquals("TRUE", isAudio("audio/x-ms-wma"));
        assertEquals("TRUE", isAudio("video/x-msvideo"));
    }

    /**
     * Live: the near misses that make the point. {@code audio/mpeg} is audio but the three other MP3
     * spellings are not, and {@code audio/mp4} is not either — despite {@code audio/x-m4a} being in the
     * set. {@code audio/ogg} is in, {@code audio/vorbis} is not. And {@code video/mp4} is NOT audio,
     * even though {@code video/x-msvideo} is.
     */
    @Test
    public void falseForNearMissesAndForOtherFamilies() {
        assertEquals("FALSE", isAudio("audio/mp4"));
        assertEquals("FALSE", isAudio("audio/mp3"));
        assertEquals("FALSE", isAudio("audio/mpeg3"));
        assertEquals("FALSE", isAudio("audio/x-mpeg"));
        assertEquals("FALSE", isAudio("audio/basic"));
        assertEquals("FALSE", isAudio("audio/vorbis"));
        assertEquals("FALSE", isAudio("audio/x-bogus"));
        assertEquals("FALSE", isAudio("video/mp4"));
    }

    /**
     * Live: FL_IS_AUDIO does NOT propagate NULL — a NULL argument, and the NULL that {@code TO_FILE(NULL)}
     * yields, both answer FALSE rather than NULL.
     */
    @Test
    public void nullFileIsFalseNotNull() {
        assertEquals("FALSE", scalar("SELECT FL_IS_AUDIO(NULL)").toUpperCase());
        assertEquals("FALSE", scalar("SELECT FL_IS_AUDIO(TO_FILE(NULL))").toUpperCase());
    }
}
