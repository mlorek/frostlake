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

package dev.frostlake.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A scalar subquery carries its one select item's declared type (live-verified, pinned on both
 * engines): {@code SYSTEM$TYPEOF((SELECT f FROM t))} is the column's FLOAT, {@code (SELECT 1.5)} is
 * NUMBER(2,1), a COUNT is NUMBER(18,0), a SUM keeps its widened width, arithmetic over the subquery is
 * typed as arithmetic over the column would be, and a table written from the item declares that type.
 * The type is what the rendering rules key off: a FLOAT through a scalar subquery prints at the FLOAT
 * width (1.414213562, -0) rather than as Java's double text.
 *
 * <p>The type also feeds the compile-time rules — {@code +(SELECT TRUE)} is refused as a sign over a
 * BOOLEAN — and a select list of more than one item is refused as "Unsupported: Scalar subquery with
 * multi-column SELECT clause." at the subquery's own SELECT, while more than one ROW is the row-time
 * "Single-row subquery returns more than one row.".
 */
public class ScalarSubqueryTypeTest extends BaseDatabaseTest {

    @BeforeEach
    public void fixture() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, f FLOAT, n NUMBER(5,2), s VARCHAR(3), d DATE,"
            + " ts TIMESTAMP_NTZ, b BOOLEAN)");
        engine.execute("INSERT INTO fz SELECT 5, SQRT(2), 1.5, 'abc', '2020-01-15', '2020-01-15 10:00:00', TRUE");
    }

    private String text(final String sql) {
        final Object value = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return e.getMessage();
    }

    private String columnType(final String table) {
        return String.valueOf(engine.executeQuery("DESC TABLE " + table).getRows().get(0).getValue(1));
    }

    @Test
    public void theSubqueryCarriesItsItemsDeclaredType() {
        assertEquals("FLOAT[DOUBLE]", text("SELECT SYSTEM$TYPEOF((SELECT f FROM fz WHERE id = 5))"));
        assertEquals("NUMBER(5,2)[SB2]", text("SELECT SYSTEM$TYPEOF((SELECT n FROM fz WHERE id = 5))"));
        assertEquals("VARCHAR(3)[LOB]", text("SELECT SYSTEM$TYPEOF((SELECT s FROM fz WHERE id = 5))"));
        assertEquals("DATE[SB4]", text("SELECT SYSTEM$TYPEOF((SELECT d FROM fz WHERE id = 5))"));
        assertEquals("TIMESTAMP_NTZ(9)[SB16]", text("SELECT SYSTEM$TYPEOF((SELECT ts FROM fz WHERE id = 5))"));
        assertEquals("BOOLEAN[SB1]", text("SELECT SYSTEM$TYPEOF((SELECT b FROM fz WHERE id = 5))"));
        assertEquals("NUMBER(2,1)[SB1]", text("SELECT SYSTEM$TYPEOF((SELECT 1.5))"));
        assertEquals("VARCHAR(3)[LOB]", text("SELECT SYSTEM$TYPEOF((SELECT 'abc'))"));
        assertEquals("NUMBER(18,0)[SB1]", text("SELECT SYSTEM$TYPEOF((SELECT COUNT(*) FROM fz))"));
        assertEquals("NUMBER(17,2)[SB8]", text("SELECT SYSTEM$TYPEOF((SELECT SUM(n) FROM fz))"));
        assertEquals("NUMBER(6,2)[SB2]", text("SELECT SYSTEM$TYPEOF((SELECT n FROM fz WHERE id = 5) + 1)"));
        assertEquals("NUMBER(5,2)[SB2]", text("SELECT SYSTEM$TYPEOF((SELECT (SELECT n FROM fz WHERE id = 5)))"));
        assertEquals("NUMBER(5,2)[SB2]", text("SELECT SYSTEM$TYPEOF(IFF(TRUE, (SELECT n FROM fz WHERE id = 5), 0))"));
        assertEquals("VARCHAR(4)[LOB]", text("SELECT SYSTEM$TYPEOF((SELECT s FROM fz WHERE id = 5) || 'x')"));
        // The type is the item's, whatever the rows: none at all still types FLOAT.
        assertNull(text("SELECT (SELECT f FROM fz WHERE id = 6)"));
        assertEquals("FLOAT[DOUBLE]", text("SELECT SYSTEM$TYPEOF((SELECT f FROM fz WHERE id = 6))"));
        assertEquals("FLOAT[DOUBLE]", text("SELECT SYSTEM$TYPEOF((SELECT MAX(f) FROM fz))"));
    }

    @Test
    public void aTableWrittenFromTheItemDeclaresThatType() {
        engine.execute("CREATE OR REPLACE TABLE c_f AS SELECT (SELECT f FROM fz WHERE id = 5) AS c");
        assertEquals("FLOAT", columnType("c_f"));
        engine.execute("CREATE OR REPLACE TABLE c_n AS SELECT (SELECT n FROM fz WHERE id = 5) AS c");
        assertEquals("NUMBER(5,2)", columnType("c_n"));
        engine.execute("CREATE OR REPLACE TABLE c_s AS SELECT (SELECT s FROM fz WHERE id = 5) AS c");
        assertEquals("VARCHAR(3)", columnType("c_s"));
        engine.execute("CREATE OR REPLACE TABLE c_lit AS SELECT (SELECT 1.5) AS c");
        assertEquals("NUMBER(2,1)", columnType("c_lit"));
        engine.execute("CREATE OR REPLACE TABLE c_d AS SELECT (SELECT d FROM fz WHERE id = 5) AS c,"
            + " (SELECT ts FROM fz WHERE id = 5) AS t, (SELECT b FROM fz WHERE id = 5) AS bb");
        assertEquals("DATE", columnType("c_d"));
        assertEquals("TIMESTAMP_NTZ(9)", String.valueOf(engine.executeQuery("DESC TABLE c_d").getRows().get(1).getValue(1)));
        assertEquals("BOOLEAN", String.valueOf(engine.executeQuery("DESC TABLE c_d").getRows().get(2).getValue(1)));
    }

    @Test
    public void aFloatThroughTheSubqueryRendersAtTheFloatWidth() {
        assertEquals("1.414213562", text("SELECT (SELECT f FROM fz WHERE id = 5)::VARCHAR"));
        assertEquals("-0", text("SELECT (SELECT SUM(x) FROM (SELECT -0.0::FLOAT AS x))::VARCHAR"));
        assertEquals("1.5", text("SELECT +(SELECT 1.5)"));
    }

    @Test
    public void theTypeFeedsTheCompileTimeRules() {
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'UNARY PLUS': (BOOLEAN)", refusal("SELECT +(SELECT TRUE)"));
        assertEquals("Numeric value 'abc' is not recognized", refusal("SELECT -(SELECT 'abc')"));
        // A non-boolean subquery is no predicate; the echo's spelling is live's canonical re-print,
        // which is not this engine's, so the sentence is pinned up to it.
        assertTrue(refusal("SELECT id FROM fz WHERE (SELECT n FROM fz WHERE id = 5)")
            .startsWith("SQL compilation error:\nInvalid data type [NUMBER(5,2)] for predicate ["));
        assertTrue(refusal("SELECT id FROM fz WHERE (SELECT s FROM fz WHERE id = 5)")
            .startsWith("SQL compilation error:\nInvalid data type [VARCHAR(3)] for predicate ["));
    }

    @Test
    public void tooManyColumnsIsACompileRefusalAndTooManyRowsARowTimeOne() {
        assertEquals("SQL compilation error: error line 1 at position 8\n"
            + "Unsupported: Scalar subquery with multi-column SELECT clause.",
            refusal("SELECT (SELECT f, n FROM fz WHERE id = 5)"));
        engine.execute("INSERT INTO fz SELECT 7, 2.5, 2.5, 'xyz', '2020-01-16', '2020-01-16 10:00:00', FALSE");
        assertEquals("Single-row subquery returns more than one row.", refusal("SELECT (SELECT f FROM fz)"));
        assertEquals("7", text("SELECT id FROM fz WHERE f = (SELECT MAX(f) FROM fz)"));
    }
}
