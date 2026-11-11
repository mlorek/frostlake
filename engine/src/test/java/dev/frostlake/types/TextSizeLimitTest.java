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
package dev.frostlake.types;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.frostlake.LiveSnowflake;
import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A text holds at most 134217728 characters. A refusal of a value too long for its place shows the value whole up to
 * 200 characters and its first 200 and {@code ...} past them; a semi-structured value whose text is longer than a text
 * can be does not convert to one - by a cast to VARCHAR, TO_VARCHAR or a cast of an object holding it - while TO_JSON
 * makes the longer text, which a concatenation then refuses in 'CONCAT'. Every answer but the last test's is
 * live-verified; the account runs a cast of a billion holes past its statement timeout.
 */
public class TextSizeLimitTest extends MeasuredCellsTestSupport {

    private static final String PADDED = "ARRAY_INSERT([1, 2], 14000000, 9)";

    /** The refusal of a text too long, showing what the account shows of it. */
    private static String tooLong(final String shown, final String place) {
        return "REFUSED String '" + shown + "' is too long and would be truncated" + place;
    }

    /** The first 200 characters of a padded array's text, and the mark of the rest. */
    private static String paddedShown(final String opening) {
        final StringBuilder text = new StringBuilder(opening);
        while (text.length() < 200) {
            text.append("undefined,");
        }
        return text.substring(0, 200) + "...";
    }

    private static String bees(final int count) {
        final StringBuilder text = new StringBuilder("a");
        while (text.length() < count) {
            text.append('b');
        }
        return text.toString();
    }

    @Test
    public void aRefusalShowsTwoHundredCharactersOfTheValue() {
        assertCells(new String[][] {
            {"SELECT RPAD('a', 300, 'b')::VARCHAR(2)", tooLong(bees(200) + "...", "")},
            {"SELECT RPAD('a', 201, 'b')::VARCHAR(2)", tooLong(bees(200) + "...", "")},
            {"SELECT RPAD('a', 200, 'b')::VARCHAR(2)", tooLong(bees(200), "")},
            {"SELECT RPAD('a', 199, 'b')::VARCHAR(2)", tooLong(bees(199), "")},
            {"SELECT TO_VARIANT(RPAD('a', 300, 'b'))::VARCHAR(2)", tooLong(bees(200) + "...", "")},
            {"SELECT LENGTH(ARRAY_INSERT([1, 2], 3000000, 9)::VARCHAR(10))", tooLong(paddedShown("[1,2,"), "")},
        });
    }

    @Test
    public void aSemiStructuredValueConvertsOnlyToATextThatFits() {
        assertCells(new String[][] {
            {"SELECT LENGTH(ARRAY_INSERT([1, 2], 13000000, 9)::VARCHAR)", "129999987"},
            {"SELECT LENGTH(" + PADDED + "::VARCHAR)", tooLong(paddedShown("[1,2,"), "")},
            {"SELECT LENGTH(" + PADDED + "::STRING)", tooLong(paddedShown("[1,2,"), "")},
            {"SELECT LENGTH(TO_VARCHAR(" + PADDED + "))", tooLong(paddedShown("[1,2,"), "")},
            {"SELECT LENGTH(CAST(" + PADDED + " AS VARCHAR(134217728)))", tooLong(paddedShown("[1,2,"), "")},
            {"SELECT LENGTH(OBJECT_CONSTRUCT('k', " + PADDED + ")::VARCHAR)", tooLong(paddedShown("{\"k\":[1,2,"), "")},
            {"SELECT LENGTH(TO_JSON(" + PADDED + "))", "139999987"},
            {"SELECT LENGTH(TO_JSON(" + PADDED + ")::VARCHAR)", "139999987"},
            {"SELECT LENGTH(TO_JSON(" + PADDED + ") || 'x')", tooLong(paddedShown("[1,2,"), " in 'CONCAT'")},
            {"SELECT ARRAY_SIZE(" + PADDED + ")", "14000001"},
        });
    }

    @Test
    public void anArrayTooLongToSpellIsRefusedBeforeItsTextIsMade() {
        assumeFalse(LiveSnowflake.enabled(), "the account runs this past its statement timeout");
        assertCells(new String[][] {
            {"SELECT ARRAY_INSERT([1, 2], 1000000000, 9)::VARCHAR", tooLong(paddedShown("[1,2,"), "")},
            {"SELECT TO_VARCHAR(ARRAY_INSERT([1, 2], 1000000000, 9))", tooLong(paddedShown("[1,2,"), "")},
            {"SELECT ARRAY_SIZE(ARRAY_INSERT([1, 2], 1000000000, 9))", "1000000001"},
        });
    }
}
