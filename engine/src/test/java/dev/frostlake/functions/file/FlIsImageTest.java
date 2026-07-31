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
 * {@code FL_IS_IMAGE(file)} — whether the file's {@code CONTENT_TYPE} is one of the image types.
 *
 * <p>Every expectation was measured against live Snowflake. The predicate is
 * an EXACT MATCH against a closed set of content types — there is no {@code image/*} prefix rule, no
 * case folding and no alias handling, which is why {@code image/x-png} and {@code IMAGE/PNG} are both
 * FALSE while {@code image/png} is TRUE. Membership was enumerated by probing, not derived from the
 * names.
 */
public class FlIsImageTest extends FileFunctionTestSupport {

    private String isImage(final String contentType) {
        return scalar("SELECT FL_IS_IMAGE(" + fileOf(contentType) + ")").toUpperCase();
    }

    /** Live: every content type measured TRUE, enumerated. */
    @Test
    public void trueForEveryMeasuredImageContentType() {
        assertEquals("TRUE", isImage("image/png"));
        assertEquals("TRUE", isImage("image/jpeg"));
        assertEquals("TRUE", isImage("image/jpg"));
        assertEquals("TRUE", isImage("image/gif"));
        assertEquals("TRUE", isImage("image/webp"));
        assertEquals("TRUE", isImage("image/svg+xml"));
        assertEquals("TRUE", isImage("image/tiff"));
        assertEquals("TRUE", isImage("image/x-tiff"));
        assertEquals("TRUE", isImage("image/bmp"));
        assertEquals("TRUE", isImage("image/x-icon"));
        assertEquals("TRUE", isImage("image/vnd.microsoft.icon"));
        assertEquals("TRUE", isImage("image/heic"));
        assertEquals("TRUE", isImage("image/avif"));
        assertEquals("TRUE", isImage("image/apng"));
    }

    /**
     * Live: the near misses. {@code image/x-tiff} is in the set but {@code image/tif} is not;
     * {@code image/bmp} is in but {@code image/x-ms-bmp} is not; {@code image/jpeg} is in but
     * {@code image/pjpeg} is not; {@code image/png} is in but {@code image/x-png} is not. The set is
     * also case-sensitive, so {@code IMAGE/PNG} is FALSE.
     */
    @Test
    public void falseForNearMissesAndForOtherFamilies() {
        assertEquals("FALSE", isImage("image/x-ms-bmp"));
        assertEquals("FALSE", isImage("image/pjpeg"));
        assertEquals("FALSE", isImage("image/tif"));
        assertEquals("FALSE", isImage("image/x-png"));
        assertEquals("FALSE", isImage("image/x-bogus"));
        assertEquals("FALSE", isImage("IMAGE/PNG"));
        assertEquals("FALSE", isImage("text/plain"));
    }

    /**
     * Live: the predicate reads CONTENT_TYPE and never the path. A descriptor whose RELATIVE_PATH says
     * {@code .txt} while its CONTENT_TYPE says {@code image/png} IS an image, and the mirror case is
     * not — the experiment that rules out any path- or extension-based rule inside FL_IS_IMAGE itself.
     */
    @Test
    public void classifiesOnContentTypeNotOnThePath() {
        assertEquals("TRUE", scalar("SELECT FL_IS_IMAGE(TRY_TO_FILE(OBJECT_CONSTRUCT("
            + "'STAGE', '@D.S.ST', 'RELATIVE_PATH', 'x.txt', 'SIZE', 1,"
            + " 'LAST_MODIFIED', '" + LAST_MODIFIED + "', 'CONTENT_TYPE', 'image/png',"
            + " 'ETAG', 'e1')))").toUpperCase());
        assertEquals("FALSE", scalar("SELECT FL_IS_IMAGE(TRY_TO_FILE(OBJECT_CONSTRUCT("
            + "'STAGE', '@D.S.ST', 'RELATIVE_PATH', 'x.png', 'SIZE', 1,"
            + " 'LAST_MODIFIED', '" + LAST_MODIFIED + "', 'CONTENT_TYPE', 'text/plain',"
            + " 'ETAG', 'e1')))").toUpperCase());
    }

    /**
     * Live: FL_IS_IMAGE does NOT propagate NULL — a NULL argument, and the NULL that {@code TO_FILE(NULL)}
     * yields, both answer FALSE rather than NULL.
     */
    @Test
    public void nullFileIsFalseNotNull() {
        assertEquals("FALSE", scalar("SELECT FL_IS_IMAGE(NULL)").toUpperCase());
        assertEquals("FALSE", scalar("SELECT FL_IS_IMAGE(TO_FILE(NULL))").toUpperCase());
    }
}
