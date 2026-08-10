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
 * {@code FL_IS_COMPRESSED(file)} — whether the file's {@code CONTENT_TYPE} is one of the archive types.
 *
 * <p>Every expectation was measured against live Snowflake. Membership is by exact content-type
 * value, and the set is quirky: it holds {@code application/gzip} — the spelling a staged
 * {@code .gz} really gets, so gzip files DO classify compressed — while the {@code x-}-prefixed
 * twins a hand-built object may carry ({@code application/x-gzip},
 * {@code application/x-rar-compressed}) sit outside it, as does {@code .7z}'s own measured
 * {@code application/x-7z-compressed}: the {@code .7z} extension table and this set do not line
 * up, so a staged {@code .7z} is FALSE. Membership was enumerated by probing, not derived from the
 * names.
 */
public class FlIsCompressedTest extends FileFunctionTestSupport {

    private String isCompressed(final String contentType) {
        return scalar("SELECT FL_IS_COMPRESSED(" + fileOf(contentType) + ")").toUpperCase();
    }

    /** Live: every content type measured TRUE, enumerated. */
    @Test
    public void trueForEveryMeasuredCompressedContentType() {
        assertEquals("TRUE", isCompressed("application/zip"));
        assertEquals("TRUE", isCompressed("application/x-zip-compressed"));
        assertEquals("TRUE", isCompressed("application/gzip"));
        assertEquals("TRUE", isCompressed("application/x-tar"));
        assertEquals("TRUE", isCompressed("application/x-bzip"));
        assertEquals("TRUE", isCompressed("application/x-bzip2"));
        assertEquals("TRUE", isCompressed("application/vnd.rar"));
    }

    /**
     * Live: the trap, asserted. {@code application/gzip} is compressed but the {@code x-} spelling —
     * a value only a hand-built object carries — is not, and the same asymmetry hits {@code .7z} and
     * {@code .rar} — {@code application/vnd.rar} is in the set while {@code application/x-rar-compressed}
     * is not.
     */
    @Test
    public void theXPrefixedSpellingsOfTheSameArchivesAreFalse() {
        assertEquals("FALSE", isCompressed("application/x-gzip"));
        assertEquals("FALSE", isCompressed("application/x-7z-compressed"));
        assertEquals("FALSE", isCompressed("application/x-rar-compressed"));
    }

    /**
     * Live: the rest of the near misses. Newer codecs are absent wholesale
     * ({@code application/zstd}, {@code application/x-xz}, {@code application/x-lzma},
     * {@code application/x-compress}), a compound extension is not decomposed
     * ({@code application/x-tar-gz}), and the set is not a suffix rule either — {@code multipart/x-zip}
     * is FALSE.
     */
    @Test
    public void falseForUnlistedArchiveTypes() {
        assertEquals("FALSE", isCompressed("application/x-xz"));
        assertEquals("FALSE", isCompressed("application/x-compress"));
        assertEquals("FALSE", isCompressed("application/zstd"));
        assertEquals("FALSE", isCompressed("application/x-lzma"));
        assertEquals("FALSE", isCompressed("application/x-tar-gz"));
        assertEquals("FALSE", isCompressed("multipart/x-zip"));
    }

    /**
     * Live: {@code application/epub+zip} is a ZIP archive, but it is classified as a DOCUMENT and not
     * as compressed — the categories are assigned per content type, not by what the bytes contain.
     */
    @Test
    public void epubIsADocumentAndNotCompressed() {
        assertEquals("FALSE", isCompressed("application/epub+zip"));
        assertEquals("TRUE", scalar("SELECT FL_IS_DOCUMENT(" + fileOf("application/epub+zip") + ")")
            .toUpperCase());
        assertEquals("document", scalar("SELECT FL_GET_FILE_TYPE(" + fileOf("application/epub+zip") + ")"));
    }

    /**
     * Live: FL_IS_COMPRESSED does NOT propagate NULL — a NULL argument, and the NULL that
     * {@code TO_FILE(NULL)} yields, both answer FALSE rather than NULL.
     */
    @Test
    public void nullFileIsFalseNotNull() {
        assertEquals("FALSE", scalar("SELECT FL_IS_COMPRESSED(NULL)").toUpperCase());
        assertEquals("FALSE", scalar("SELECT FL_IS_COMPRESSED(TO_FILE(NULL))").toUpperCase());
    }
}
