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
 * CONCAT_WS joins its values with the separator, and is NULL when any VALUE is NULL — it does not skip
 * them. A NULL SEPARATOR is subtler: it makes the call NULL only once there are two values for it to
 * separate. A lone value never reads the separator, so {@code CONCAT_WS(NULL, 'x')} is {@code 'x'},
 * folded from literals or read from a row alike. A separator with no value at all is too few arguments.
 */
public class ConcatWsTest extends BaseDatabaseTest {

    /** The first row's first column, as text. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** The refusal a statement meets, with its line breaks folded to bars. */
    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void valuesAreJoinedWithTheSeparator() {
        assertEquals("a-b-c", answer("SELECT CONCAT_WS('-', 'a', 'b', 'c')"));
        assertEquals("x", answer("SELECT CONCAT_WS('_', 'x')"));
    }

    @Test
    public void anyNullValueMakesTheCallNull() {
        assertEquals("null", answer("SELECT CONCAT_WS('_', 'x', NULL)"));
        assertEquals("null", answer("SELECT CONCAT_WS('_', NULL)"));
    }

    @Test
    public void aNullSeparatorMattersOnlyBetweenTwoValues() {
        assertEquals("x", answer("SELECT CONCAT_WS(NULL, 'x')"));
        assertEquals("null", answer("SELECT CONCAT_WS(NULL, 'x', 'y')"));
        engine.execute("CREATE OR REPLACE TABLE cws (s VARCHAR, v VARCHAR)");
        engine.execute("INSERT INTO cws VALUES (NULL, 'x')");
        assertEquals("x", answer("SELECT CONCAT_WS(s, v) FROM cws"), "read from a row alike");
        assertEquals("null", answer("SELECT CONCAT_WS(s, v, v) FROM cws"));
        assertEquals("null", answer("SELECT CONCAT_WS(NULL, NULL)"), "a lone NULL value is still NULL");
        assertEquals("", answer("SELECT CONCAT_WS(NULL, '')"));
    }

    /** A separator alone is not a call: CONCAT_WS takes at least one value. */
    @Test
    public void aSeparatorAloneIsTooFewArguments() {
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "|not enough arguments for function [CONCAT_WS('x')], expected 2, got 1",
            refusal("SELECT CONCAT_WS('x')"));
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "|not enough arguments for function [CONCAT_WS(null)], expected 2, got 1",
            refusal("SELECT CONCAT_WS(NULL)"));
    }
}
