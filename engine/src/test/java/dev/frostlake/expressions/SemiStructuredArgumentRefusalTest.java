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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A function handed a CONTAINER is refused at COMPILE time, in a view as in a query. Frostlake raised
 * the right sentence but without the compile-time marker, so the view path read it as a data-dependent
 * failure, swallowed it, and created a view WITH NO COLUMNS — an object that exists and answers
 * nothing.
 *
 * <p>The sentence is anchored on the CALL: {@code SELECT UPPER(o) …} reads position 7, the offset of
 * the function's own name. And AVG reports as SUM, because live names the function its plan rewrites
 * the call into rather than the one that was written.
 */
public class SemiStructuredArgumentRefusalTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ss (o OBJECT, a ARRAY, s VARCHAR(10), n NUMBER)");
        engine.execute("INSERT INTO ss SELECT OBJECT_CONSTRUCT('k', 1), ARRAY_CONSTRUCT(1, 2),"
            + " 'abc', 1");
    }

    private String queryRefusal(final String expression) {
        try {
            engine.executeQuery("SELECT " + expression + " AS c FROM ss");
        } catch (final RuntimeException refused) {
            return refused.getMessage().replace('\n', ' ');
        }
        return "accepted";
    }

    private String viewRefusal(final String expression) {
        try {
            engine.execute("CREATE OR REPLACE VIEW ss_v AS SELECT " + expression + " AS c FROM ss");
        } catch (final RuntimeException refused) {
            return refused.getMessage().replace('\n', ' ');
        }
        return "accepted";
    }

    /** The query names the function and its argument types, positioned at the call. */
    @Test
    public void aQueryIsRefusedAtTheCall() {
        assertTrue(queryRefusal("UPPER(o)").contains(
            "error line 1 at position 7 Invalid argument types for function 'UPPER': (OBJECT)"),
            queryRefusal("UPPER(o)"));
        assertTrue(queryRefusal("TRIM(a)").contains(
            "error line 1 at position 7 Invalid argument types for function 'TRIM': (ARRAY)"),
            queryRefusal("TRIM(a)"));
        assertTrue(queryRefusal("LOWER(o)").contains(
            "Invalid argument types for function 'LOWER': (OBJECT)"), queryRefusal("LOWER(o)"));
        assertTrue(queryRefusal("CONCAT(s, o)").contains(
            "Invalid argument types for function 'CONCAT': (VARCHAR(10), OBJECT)"),
            queryRefusal("CONCAT(s, o)"));
    }

    /**
     * And the VIEW is refused too, at COMPILE time — the half that was missing. Without the marker the
     * statement was accepted and left a view with no columns behind it.
     */
    @Test
    public void aViewOverTheSameBodyIsRefusedAtCompileTime() {
        for (final String expression
                : new String[]{"UPPER(o)", "TRIM(a)", "LOWER(o)", "ABS(o)", "LENGTH(o)",
                    "CONCAT(s, o)", "SUM(o)", "AVG(o)"}) {
            final String refusal = viewRefusal(expression);
            assertTrue(refusal.contains("SQL compilation error"),
                expression + " must refuse at compile time, but gave: " + refusal);
            assertTrue(refusal.contains("Invalid argument types for function"),
                expression + " gave: " + refusal);
        }
    }

    /** No empty view is left behind — the refusal must not create the object it refused. */
    @Test
    public void noEmptyViewIsCreated() {
        viewRefusal("UPPER(o)");
        String outcome = "no such view";
        try {
            final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE test_db.test_schema.ss_v");
            outcome = "created with " + rs.getRowCount() + " columns";
        } catch (final RuntimeException absent) {
            outcome = "no such view";
        }
        assertEquals("no such view", outcome, "the refused view must not exist");
    }

    /** AVG reports as SUM: live names the function its plan rewrites the bare call into. */
    @Test
    public void anAggregateReportsTheFunctionItIsRewrittenInto() {
        assertTrue(queryRefusal("AVG(o)").contains(
            "Invalid argument types for function 'SUM': (OBJECT)"), queryRefusal("AVG(o)"));
    }

    /**
     * The ANCHOR is the function name's own place in the WHOLE STATEMENT — not within the fragment the
     * engine happens to be compiling. A view's body is re-parsed from its own text, so every offset
     * inside it is body-relative and has to be COMPOSED with where that body starts; the composition
     * existed but the projection walk replaced the origin instead of nesting inside it, so a view's
     * refusal read as though the body were the whole statement.
     */
    @Test
    public void theAnchorIsTheFunctionsPlaceInTheStatement() {
        assertAnchoredAt("SELECT UPPER(o) FROM ss", 1, 7);
        assertAnchoredAt("CREATE VIEW v1 AS SELECT UPPER(o) FROM ss", 1, 25);
        assertAnchoredAt("CREATE OR REPLACE VIEW v3 AS SELECT UPPER(o) FROM ss", 1, 36);
        assertAnchoredAt("CREATE VIEW v4 AS SELECT s, UPPER(o) FROM ss", 1, 28);
        assertAnchoredAt("CREATE VIEW v6 AS SELECT UPPER(o) FROM (SELECT o FROM ss)", 1, 25);
        assertAnchoredAt("INSERT INTO ss SELECT UPPER(o), a, s, n FROM ss", 1, 22);
    }

    /** A body on a LATER line keeps its own column — the origin displaces only the line it starts on. */
    @Test
    public void aMultiLineBodyKeepsItsOwnColumn() {
        assertAnchoredAt("CREATE VIEW v5 AS\n  SELECT UPPER(o) FROM ss", 2, 9);
    }

    /**
     * An AGGREGATE anchors the same way. It reaches the refusal through the GROUP BY path rather than
     * the projection one, and that path set no origin at all — so the sentence came out unpositioned in
     * a query and pointed at the SELECT in a view.
     */
    @Test
    public void anAggregateIsAnchoredToo() {
        assertAnchoredAt("SELECT SUM(o) FROM ss", 1, 7);
        assertAnchoredAt("CREATE VIEW v2 AS SELECT SUM(o) FROM ss", 1, 25);
    }

    private void assertAnchoredAt(final String sql, final int line, final int position) {
        String got = "accepted";
        try {
            engine.execute(sql);
        } catch (final RuntimeException refused) {
            got = refused.getMessage().replace('\n', ' ');
        }
        assertTrue(got.contains("error line " + line + " at position " + position
                + " Invalid argument types for function"),
            "[" + sql.replace("\n", "\\n") + "] expected line " + line + " position " + position
                + ", got: " + got);
    }

    /** The legal uses of the same column stay legal — the refusal must not widen. */
    @Test
    public void theLegalUsesAreUntouched() {
        assertEquals("accepted", queryRefusal("GET(o, 'k')"));
        assertEquals("accepted", queryRefusal("TYPEOF(o)"));
        assertEquals("accepted", queryRefusal("TO_VARCHAR(o)"));
        assertEquals("accepted", viewRefusal("GET(o, 'k')"));
    }
}
