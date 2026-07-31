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
 * {@code FL_GET_LAST_MODIFIED(file)} — when the staged file was last modified.
 *
 * <p>Every expectation was measured against live Snowflake. The descriptor built from a real stage file stores the
 * moment as an RFC-1123 HTTP date in GMT ({@code Mon, 03 Aug 2026 11:24:13 GMT}) but the function
 * returns a TIMESTAMP, so the assertions below render it through {@code TO_VARCHAR} with an explicit
 * format rather than depending on any default display shape.
 *
 * <p>This getter PROPAGATES NULL: {@code FL_GET_LAST_MODIFIED(NULL)} and
 * {@code FL_GET_LAST_MODIFIED(TO_FILE(NULL))} are both NULL. That CONTRASTS with
 * {@code FL_GET_FILE_TYPE}, which answers the string {@code 'unknown'} for a NULL file, and with the
 * {@code FL_IS_*} family, which answers FALSE.
 */
public class FlGetLastModifiedTest extends FileFunctionTestSupport {

    /** Snowflake's date-format model, matching {@code ToCharDateTest}'s convention. */
    private static final String FORMAT = "YYYY-MM-DD HH24:MI:SS";

    private String lastModified(final String fileExpression) {
        return scalar("SELECT TO_VARCHAR(FL_GET_LAST_MODIFIED(" + fileExpression + "), '" + FORMAT + "')");
    }

    /**
     * Live: {@code @sse/hello.txt} carried {@code Mon, 03 Aug 2026 11:24:13 GMT}, which is the UTC
     * instant {@code 11:24:13}.
     */
    @Test
    public void readsTheDescriptorsRfc1123Instant() {
        assertEquals("2026-08-03 11:24:13", lastModified(helloTextFile()));
    }

    /** Live: the string is GMT, and the timestamp is that same UTC wall clock — no zone shift is applied. */
    @Test
    public void keepsTheGmtWallClock() {
        assertEquals("2026-01-01 00:00:00",
            lastModified(fileWithLastModified("Thu, 01 Jan 2026 00:00:00 GMT")));
        assertEquals("2026-12-31 23:59:59",
            lastModified(fileWithLastModified("Thu, 31 Dec 2026 23:59:59 GMT")));
    }

    /**
     * Live: the constructor accepts ANY LAST_MODIFIED string, and THIS
     * accessor is where leniency lives — the ISO shape {@code 11:24:13} parses to the
     * same wall clock the RFC-1123 shape does (whose zone token live simply discards), epoch-second
     * digits parse as the UTC instant, and junk text is NULL, not an error.
     */
    @Test
    public void parsesNonRfc1123ShapesLeniently() {
        assertEquals("2026-08-03 11:24:13",
            lastModified(fileWithLastModified("2026-08-03 11:24:13")));
        // 1722684253 = 2024-08-03 11:24:13 UTC.
        assertEquals("2024-08-03 11:24:13", lastModified(fileWithLastModified("1722684253")));
        assertNull(lastModified(fileWithLastModified("not a date")));
    }

    /** Live: NULL in, NULL out — unlike FL_GET_FILE_TYPE and the FL_IS_* family. */
    @Test
    public void propagatesNull() {
        assertNull(lastModified("NULL"));
        assertNull(lastModified("TO_FILE(NULL)"));
        assertNull(scalar("SELECT FL_GET_LAST_MODIFIED(NULL)"));
        assertNull(scalar("SELECT FL_GET_LAST_MODIFIED(TO_FILE(NULL))"));
    }

    private static String fileWithLastModified(final String text) {
        return "TRY_TO_FILE(OBJECT_CONSTRUCT('STAGE', '@D.S.ST', 'RELATIVE_PATH', 'x.txt', 'SIZE', 1,"
            + " 'LAST_MODIFIED', '" + text + "', 'CONTENT_TYPE', 'text/plain', 'ETAG', 'e1'))";
    }
}
