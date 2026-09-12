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
 * A STORED FLOAT rendering as text. A FLOAT column's value used to be held EXACTLY — as a BigDecimal —
 * so any layer deciding "is this approximate?" from the RUNTIME CLASS saw an exact number and rendered
 * it that way: {@code TO_VARCHAR(g)} over a stored 3.0 was "3.0" where live says "3". The column now
 * holds a double, and the declared-type rule these cells pin still guards the exact carrier a
 * FLOAT-declared value can arrive in from an expression or an older snapshot.
 *
 * <p>★ THE FORMATTER WAS NEVER WRONG. The same call over a COMPUTED float already agreed on both
 * engines — {@code TO_VARCHAR(3.0::FLOAT)} is "3" — because a computed value arrives as a Double and
 * takes the float path. Only what REACHED the formatter was wrong, which is why the fix restores the
 * operand rather than touching the rendering.
 *
 * <p>Decided from the DECLARED type, not the class: a BigDecimal from a FLOAT column and one from a
 * NUMBER column are the same object, and only the declared type separates them. That is the same
 * channel the arithmetic operand already uses, and the third time this particular fact has had to be
 * worked around in a different layer.
 */
public class StoredFloatRenderingTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE gw (g FLOAT, n NUMBER(10,1), a INT)");
        engine.execute("INSERT INTO gw VALUES (3.0, 3.0, 1), (7.0, 7.0, 2), (2.5, 2.5, 3)");
    }

    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>";
    }

    /** A whole stored float loses the decimal point live drops. */
    @Test
    public void aWholeStoredFloatDropsItsPoint() {
        assertEquals("3", answer("SELECT TO_VARCHAR(g) FROM gw WHERE a = 1"));
        assertEquals("7", answer("SELECT TO_VARCHAR(g) FROM gw WHERE a = 2"));
    }

    /** A stored float with a real fraction keeps it. */
    @Test
    public void aFractionalStoredFloatIsUnchanged() {
        assertEquals("2.5", answer("SELECT TO_VARCHAR(g) FROM gw WHERE a = 3"));
    }

    /** A COMPUTED float already agreed, and still does. */
    @Test
    public void aComputedFloatIsUnchanged() {
        assertEquals("3", answer("SELECT TO_VARCHAR(3.0::FLOAT)"));
        assertEquals("0.3333333333", answer("SELECT TO_VARCHAR(1.0/3.0::FLOAT)"));
    }

    /** Concatenation renders it the same way — a separate path from the function call. */
    @Test
    public void concatenationRendersItTheSameWay() {
        assertEquals("3", answer("SELECT g || '' FROM gw WHERE a = 1"));
        assertEquals("2.5", answer("SELECT g || '' FROM gw WHERE a = 3"));
    }

    /** ★ An EXACT column of the same value is untouched — the declared type is what separates them. */
    @Test
    public void anExactColumnOfTheSameValueIsUntouched() {
        assertEquals("3.0", answer("SELECT TO_VARCHAR(n) FROM gw WHERE a = 1"),
            "a NUMBER(10,1) holding 3.0 keeps its declared scale");
        assertEquals("3.0", answer("SELECT n || '' FROM gw WHERE a = 1"));
    }
}
