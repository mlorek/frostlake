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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A string literal beside a number folds as the number it spells, and it keeps doing so when a derived
 * table, a CTE, a view or a LATERAL projects it — at any depth — while a cast or any other expression of
 * it, a set operation over it, an aggregate of it or a table stored from it is a string column again.
 * The literal's text is read as written, so a blank beside the digits leaves it a text. Every cell is
 * live-verified over {@code cf (n NUMBER(10,2))} holding 1.5.
 */
public class DerivedLiteralFoldTest extends BaseDatabaseTest {

    private static final String FROM_LITERAL = " FROM (SELECT '12.5' AS t, n FROM cf)";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE cf (n NUMBER(10,2))");
        engine.execute("INSERT INTO cf VALUES (1.5)");
    }

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private String typeOf(final String expression, final String from) {
        return rows("SELECT SYSTEM$TYPEOF(" + expression + ")" + from);
    }

    @Test
    public void everyConditionalFoldsTheProjectedLiteral() {
        assertEquals("12.50, NUMBER(10,2)[SB8]", rows("SELECT TO_VARCHAR(COALESCE(t, n)), "
            + "SYSTEM$TYPEOF(COALESCE(t, n))" + FROM_LITERAL));
        assertEquals("1.50, NUMBER(10,2)[SB2]", rows("SELECT TO_VARCHAR(IFF(FALSE, t, n)), "
            + "SYSTEM$TYPEOF(IFF(FALSE, t, n))" + FROM_LITERAL));
        assertEquals("12.50, NUMBER(10,2)[SB8]", rows("SELECT TO_VARCHAR(IFF(TRUE, t, n)), "
            + "SYSTEM$TYPEOF(IFF(TRUE, t, n))" + FROM_LITERAL));
        assertEquals("12.50, NUMBER(10,2)[SB8]", rows("SELECT TO_VARCHAR(GREATEST(t, n)), "
            + "SYSTEM$TYPEOF(GREATEST(t, n))" + FROM_LITERAL));
        assertEquals("12.50, NUMBER(10,2)[SB8]", rows("SELECT TO_VARCHAR(NVL(t, n)), "
            + "SYSTEM$TYPEOF(NVL(t, n))" + FROM_LITERAL));
        assertEquals("1.50, NUMBER(10,2)[SB2]", rows("SELECT TO_VARCHAR(CASE WHEN n > 0 THEN n ELSE t END), "
            + "SYSTEM$TYPEOF(CASE WHEN n > 0 THEN n ELSE t END)" + FROM_LITERAL));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, NULL, n)", FROM_LITERAL));
        assertEquals("12.5, NUMBER(3,1)[SB2]", rows("SELECT TO_VARCHAR(COALESCE(t, 1.5)), "
            + "SYSTEM$TYPEOF(COALESCE(t, 1.5)) FROM (SELECT '12.5' AS t)"));
    }

    @Test
    public void theLiteralIsMeasuredAsTheNumberItSpells() {
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", " FROM (SELECT '12.500' AS t, n FROM cf)"));
        assertEquals("12.345, NUMBER(11,3)[SB8]", rows("SELECT TO_VARCHAR(COALESCE(t, n)), "
            + "SYSTEM$TYPEOF(COALESCE(t, n)) FROM (SELECT '12.345' AS t, n FROM cf)"));
        assertEquals("NUMBER(14,2)[SB8]", typeOf("COALESCE(t, n)", " FROM (SELECT '123456789012.5' AS t, n FROM cf)"));
        assertEquals("100.00, NUMBER(10,2)[SB8]", rows("SELECT TO_VARCHAR(COALESCE(t, n)), "
            + "SYSTEM$TYPEOF(COALESCE(t, n)) FROM (SELECT '100' AS t, n FROM cf)"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", " FROM (SELECT '-7' AS t, n FROM cf)"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", " FROM (SELECT '+12.5' AS t, n FROM cf)"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", " FROM (SELECT '.5' AS t, n FROM cf)"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", " FROM (SELECT '1e3' AS t, n FROM cf)"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", " FROM (SELECT $$12.5$$ AS t, n FROM cf)"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", " FROM (SELECT ('12.5') AS t, n FROM cf)"));
        assertEquals("NUMBER(19,1)[SB16]", typeOf("COALESCE(t, n)", " FROM (SELECT '12.5' AS t, COUNT(*) AS n FROM cf)"));
        assertEquals("FLOAT[DOUBLE]", typeOf("COALESCE(t, f)", " FROM (SELECT '12.5' AS t, 1.5::FLOAT AS f)"));
        assertEquals("VARCHAR(4)[LOB]", typeOf("COALESCE(t, u)", " FROM (SELECT '12.5' AS t, 'x' AS u)"));
    }

    @Test
    public void theLiteralIsCarriedThroughEveryRelation() {
        assertEquals("NUMBER(10,2)[SB8]",
            rows("WITH d AS (SELECT '12.5' AS t, n FROM cf) SELECT SYSTEM$TYPEOF(COALESCE(t, n)) FROM d"));
        assertEquals("NUMBER(10,2)[SB8]", rows("WITH a AS (SELECT '12.5' AS t), b AS (SELECT t FROM a) "
            + "SELECT SYSTEM$TYPEOF(COALESCE(t, n)) FROM b, cf"));
        engine.execute("CREATE OR REPLACE VIEW cfv AS SELECT '12.5' AS t, n FROM cf");
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", " FROM cfv"));
        engine.execute("CREATE OR REPLACE VIEW cfv2 AS SELECT t, n FROM (SELECT '12.5' AS t, n FROM cf)");
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", " FROM cfv2"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", " FROM (SELECT t, n" + FROM_LITERAL + ")"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", " FROM (SELECT *" + FROM_LITERAL + ")"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(x.t, n)", FROM_LITERAL + " x"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", FROM_LITERAL + " AS d"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", FROM_LITERAL + " AS d(t, n)"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(s.t, n)", " FROM cf, LATERAL (SELECT '12.5' AS t) s"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", " FROM (SELECT '12.5' AS t) , cf"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", " FROM (SELECT '12.5' AS t, n FROM cf GROUP BY n)"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", " FROM (SELECT DISTINCT '12.5' AS t, n FROM cf)"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", " FROM (SELECT '12.5' AS t, n FROM cf LIMIT 1)"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)",
            " FROM (SELECT '12.5' AS t, n FROM cf ORDER BY n)"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", FROM_LITERAL + " WHERE t IS NOT NULL"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(t, n)", FROM_LITERAL + " GROUP BY t, n"));
    }

    @Test
    public void anythingButTheBareLiteralIsAStringColumn() {
        assertEquals("12.50000, NUMBER(18,5)[SB8]", rows("SELECT TO_VARCHAR(COALESCE(t, n)), "
            + "SYSTEM$TYPEOF(COALESCE(t, n)) FROM (SELECT '12.5'::VARCHAR(5) AS t, n FROM cf)"));
        assertEquals("NUMBER(18,5)[SB8]", typeOf("COALESCE(t, n)", " FROM (SELECT '12.5' || '' AS t, n FROM cf)"));
        assertEquals("NUMBER(18,5)[SB8] | NUMBER(18,5)[SB8]", typeOf("COALESCE(t, n)",
            " FROM (SELECT '12.5' AS t, n FROM cf UNION ALL SELECT '3.25', n FROM cf)"));
        assertEquals("NUMBER(18,5)[SB8]", typeOf("COALESCE(MAX(t), MAX(n))", FROM_LITERAL));
        assertEquals("NUMBER(18,5)[SB8]", typeOf("COALESCE(t, n)", " FROM (SELECT ' 12.5 ' AS t, n FROM cf)"));
        engine.execute("CREATE OR REPLACE TABLE cft AS SELECT '12.5' AS t, n FROM cf");
        assertEquals("NUMBER(18,5)[SB8]", typeOf("COALESCE(t, n)", " FROM cft"));
    }

    /** A blank beside the digits leaves the literal a text written directly, too. */
    @Test
    public void aDirectLiteralIsReadAsWritten() {
        assertEquals("NUMBER(18,5)[SB4]", typeOf("COALESCE(' 12.5 ', n)", " FROM cf"));
        assertEquals("NUMBER(18,5)[SB4]", typeOf("COALESCE('12.5 ', n)", " FROM cf"));
        assertEquals("NUMBER(10,2)[SB2]", typeOf("COALESCE('+12.5', n)", " FROM cf"));
        assertEquals("12.50, NUMBER(10,2)[SB2]", rows("SELECT TO_VARCHAR(COALESCE('12.500', n)), "
            + "SYSTEM$TYPEOF(COALESCE('12.500', n)) FROM cf"));
    }
}
