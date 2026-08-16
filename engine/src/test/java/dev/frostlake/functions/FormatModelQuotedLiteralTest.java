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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Double-quoted text in a date/time format model is LITERAL TEXT and the quotes are consumed
 * (live-verified): {@code 'YYYY-MM-DD"T"HH24:MI'} emits a bare T — the shape that lets a caller
 * write an ISO-8601 separator at all, since a bare D, M or Y would be an element.
 *
 * <p>★ THE EDGES, each measured: elements never match inside a run ({@code '"YYYY"'} is the four
 * characters YYYY); an UNCLOSED run is literal to the end; a DOUBLED quote at top level is one
 * literal quote character, while adjacent runs simply concatenate ({@code '"a""b"'} is ab); and the
 * single quote plays no format-model role — it passes through as itself.
 *
 * <p>★ THE SAME ESCAPE SERVES THE PARSING SIDE: an explicit-format TO_DATE / TO_TIMESTAMP consumes
 * a quoted run as required literal text.
 */
public class FormatModelQuotedLiteralTest extends BaseDatabaseTest {

    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    private static final String TS = "'2026-08-18 16:19:05'::TIMESTAMP_NTZ";

    @Test
    public void theQuotedRunEmitsItsTextBare() {
        assertEquals("2026-08-18T16:19",
            answer("SELECT TO_VARCHAR(" + TS + ", 'YYYY-MM-DD\"T\"HH24:MI')"));
        assertEquals("18x08", answer("SELECT TO_VARCHAR('2026-08-18'::DATE, 'DD\"x\"MM')"));
    }

    @Test
    public void elementsNeverMatchInsideARun() {
        assertEquals("YYYY", answer("SELECT TO_VARCHAR(" + TS + ", '\"YYYY\"')"));
        assertEquals("YYYY MM DD", answer("SELECT TO_VARCHAR(" + TS + ", '\"YYYY MM DD\"')"));
        assertEquals("at 16 hours",
            answer("SELECT TO_VARCHAR(" + TS + ", '\"at \"HH24\" hours\"')"));
    }

    @Test
    public void anUnclosedRunIsLiteralToTheEnd() {
        assertEquals("2026T", answer("SELECT TO_VARCHAR(" + TS + ", 'YYYY\"T')"));
        assertEquals("YYYY", answer("SELECT TO_VARCHAR(" + TS + ", '\"YYYY')"));
    }

    @Test
    public void aDoubledQuoteAtTopLevelIsOneLiteralQuote() {
        assertEquals("2026\"08", answer("SELECT TO_VARCHAR(" + TS + ", 'YYYY\"\"MM')"));
        assertEquals("2026ab08", answer("SELECT TO_VARCHAR(" + TS + ", 'YYYY\"a\"\"b\"MM')"));
    }

    @Test
    public void theSingleQuoteIsOnlyTheSqlDelimiter() {
        assertEquals("16'19", answer("SELECT TO_VARCHAR(" + TS + ", 'HH24''MI')"));
    }

    @Test
    public void theParsingSideConsumesTheRunAsRequiredText() {
        assertEquals("2020-01-15",
            answer("SELECT TO_DATE('2020T01T15', 'YYYY\"T\"MM\"T\"DD')"));
        assertEquals("2020-01-15T10:30",
            answer("SELECT TO_TIMESTAMP('2020-01-15T10:30', 'YYYY-MM-DD\"T\"HH24:MI')"));
    }

    @Test
    public void unrecognisedBareTextStillPassesThrough() {
        assertEquals("2026 QQQ 08", answer("SELECT TO_VARCHAR(" + TS + ", 'YYYY QQQ MM')"));
    }
}
