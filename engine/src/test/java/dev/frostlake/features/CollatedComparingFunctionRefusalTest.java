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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A function that compares its arguments — DECODE, CONTAINS, STARTSWITH, ENDSWITH, POSITION, CHARINDEX,
 * REPLACE, SPLIT, SPLIT_PART, NULLIF and the GREATEST / LEAST family — refuses two collations that
 * disagree while the statement compiles, over a source with no rows too. DECODE settles each search value
 * against its subject in turn and names the search value first ({@code 'de' and 'en-ci'} for a subject
 * collated 'en-ci'), and when its results disagree as well, the results are refused first. An empty
 * specification is no collation, and one spelled in another case is the same. Every cell is live-verified.
 */
public class CollatedComparingFunctionRefusalTest extends BaseDatabaseTest {

    private static final String EMPTY = " FROM (SELECT 'a' AS c WHERE FALSE)";

    /** The first row's first cell, "no row", or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String incompatible(final String first, final String second) {
        return "SQL compilation error: Incompatible collations: '" + first + "' and '" + second + "'";
    }

    @Test
    public void decodeNamesTheSearchValueFirst() {
        final String[][] cells = {
            {"SELECT DECODE('a' COLLATE 'en-ci', 'A' COLLATE 'de', 1)", incompatible("de", "en-ci")},
            {"SELECT DECODE('a' COLLATE 'de', 'A' COLLATE 'en-ci', 1)", incompatible("en-ci", "de")},
            {"SELECT DECODE('a' COLLATE 'en-ci', 'b', 2, 'A' COLLATE 'de', 1)", incompatible("de", "en-ci")},
            {"SELECT DECODE('a' COLLATE 'en-ci', 'A' COLLATE 'de', 1, 2)", incompatible("de", "en-ci")},
            {"SELECT DECODE('a' COLLATE 'en-ci', NULL, 1, 'A' COLLATE 'de', 2)", incompatible("de", "en-ci")},
            {"SELECT DECODE('a' COLLATE 'en-ci', 'b' COLLATE 'en-ci', 1, 'A' COLLATE 'de', 2)",
                incompatible("de", "en-ci")},
            {"SELECT DECODE('a' COLLATE 'en-ci', 'b' COLLATE 'fr', 1, 'A' COLLATE 'de', 2)", incompatible("fr", "en-ci")},
            {"SELECT DECODE('a' COLLATE 'en-ci', 'A' COLLATE 'en-cs', 1)", incompatible("en-cs", "en-ci")},
            {"SELECT DECODE('a' COLLATE 'en', 'A' COLLATE 'en-ci', 1)", incompatible("en-ci", "en")},
            {"SELECT DECODE('a' COLLATE 'utf8', 'A' COLLATE 'de', 1)", incompatible("de", "utf8")},
            {"SELECT DECODE('a' COLLATE 'en-ci', 'x', 'r1' COLLATE 'de', 'r2' COLLATE 'fr')", incompatible("de", "fr")},
            {"SELECT DECODE('a' COLLATE 'en-ci', 'A' COLLATE 'de', 'r1' COLLATE 'fr', 'b', 'r2' COLLATE 'it')",
                incompatible("fr", "it")},
            {"SELECT CASE 'a' COLLATE 'en-ci' WHEN 'A' COLLATE 'de' THEN 1 END", incompatible("en-ci", "de")},
            {"SELECT 'a' COLLATE 'en-ci' = 'A' COLLATE 'de'", incompatible("de", "en-ci")},
            {"SELECT DECODE('a' COLLATE 'en-ci', 'A', 1)", "1"},
            {"SELECT DECODE('a', 'A' COLLATE 'de', 1)", "null"},
            {"SELECT DECODE('a' COLLATE 'en-ci', 'A' COLLATE 'en-ci', 1)", "1"},
            {"SELECT DECODE('a' COLLATE 'en-ci', 'A' COLLATE 'EN-CI', 1)", "1"},
            {"SELECT DECODE('a' COLLATE '', 'A' COLLATE 'de', 1)", "null"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void aComparingFunctionIsRefusedOverNoRows() {
        final String[] functions = {
            "CONTAINS", "STARTSWITH", "ENDSWITH", "POSITION", "CHARINDEX", "REPLACE", "SPLIT", "NULLIF", "GREATEST",
            "LEAST", "GREATEST_IGNORE_NULLS", "LEAST_IGNORE_NULLS",
        };
        for (final String function : functions) {
            final String sql = "SELECT " + function + "(c COLLATE 'en-ci', 'A' COLLATE 'de')" + EMPTY;
            assertEquals(incompatible("en-ci", "de"), answer(sql), sql);
        }
        assertEquals(incompatible("en-ci", "de"), answer("SELECT SPLIT_PART(c COLLATE 'en-ci', 'A' COLLATE 'de', 1)" + EMPTY));
        final String[][] cells = {
            {"SELECT DECODE(c COLLATE 'en-ci', 'A' COLLATE 'de', 1)" + EMPTY, incompatible("de", "en-ci")},
            {"SELECT DECODE('a' COLLATE 'en-ci', c COLLATE 'de', 1)" + EMPTY, incompatible("de", "en-ci")},
            {"SELECT DECODE(c COLLATE 'en-ci', 'b' COLLATE 'en-ci', 1, 'A' COLLATE 'de', 2)" + EMPTY,
                incompatible("de", "en-ci")},
            {"SELECT DECODE('a' COLLATE 'en-ci', 'A' COLLATE 'de', 'r1' COLLATE 'fr', 'b', 'r2' COLLATE 'it')" + EMPTY,
                incompatible("fr", "it")},
            {"SELECT DECODE(c COLLATE 'en-ci', 'A', 'r1' COLLATE 'fr', 'r2' COLLATE 'it')" + EMPTY, incompatible("fr", "it")},
            {"SELECT CASE c COLLATE 'en-ci' WHEN 'A' COLLATE 'de' THEN 1 END" + EMPTY, incompatible("en-ci", "de")},
            {"SELECT c COLLATE 'en-ci' IN ('A' COLLATE 'de')" + EMPTY, incompatible("de", "en-ci")},
            {"SELECT COALESCE(c COLLATE 'en-ci', 'A' COLLATE 'de')" + EMPTY, incompatible("de", "en-ci")},
            {"SELECT CONTAINS(c COLLATE 'en-ci', 'A')" + EMPTY, "no row"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }
}
