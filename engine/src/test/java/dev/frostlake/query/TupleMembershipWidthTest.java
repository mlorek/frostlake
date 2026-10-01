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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A tuple compared by IN with a subquery of another width, both wider than one column, is refused as the
 * conversion of the subquery's ROW into the tuple's: unpositioned, the subquery re-printed from its plan inside
 * ANY — ALL for NOT IN — with every item named and every column qualified. Every cell is live-verified.
 */
public class TupleMembershipWidthTest extends BaseDatabaseTest {

    private static final String REFUSED = "SQL compilation error:|Can not convert parameter '";

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE TABLE re (a INT)");
        engine.execute("CREATE OR REPLACE TABLE ft (b INT, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE st (s VARCHAR(5), n NUMBER(6,2))");
    }

    /** Every row, its cells joined by a colon, the rows by a bar; or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                if (out.length() > 0) {
                    out.append('|');
                }
                for (int i = 0; i < row.getValues().size(); i++) {
                    if (i > 0) {
                        out.append(':');
                    }
                    out.append(row.getValue(i));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void aWiderOrNarrowerSubqueryRowIsNotConverted() {
        assertEquals(REFUSED + "ANY(SELECT 1 AS \"1\", 2 AS \"2\", 3 AS \"3\" FROM FT AS FT)' of type "
                + "[ROW(NUMBER(1,0), NUMBER(1,0), NUMBER(1,0))] into expected type [ROW(NUMBER(38,0), NUMBER(38,0))]",
            answer("SELECT 1 FROM re WHERE (a, a) IN (SELECT 1, 2, 3 FROM ft)"));
        assertEquals(REFUSED + "ANY(SELECT FT.B AS \"B\", FT.D AS \"D\" FROM FT AS FT)' of type "
                + "[ROW(NUMBER(38,0), DATE)] into expected type [ROW(NUMBER(38,0), NUMBER(38,0), NUMBER(38,0))]",
            answer("SELECT 1 FROM re WHERE (a, a, a) IN (SELECT b, d FROM ft)"));
        assertEquals(REFUSED + "ALL(SELECT 1 AS \"1\", 2 AS \"2\", 3 AS \"3\" FROM FT AS FT)' of type "
                + "[ROW(NUMBER(1,0), NUMBER(1,0), NUMBER(1,0))] into expected type [ROW(NUMBER(38,0), NUMBER(38,0))]",
            answer("SELECT 1 FROM re WHERE (a, a) NOT IN (SELECT 1, 2, 3 FROM ft)"));
        assertEquals(REFUSED + "ANY(SELECT 1 AS \"1\", 2 AS \"2\", 3 AS \"3\" FROM FT AS FT)' of type "
                + "[ROW(NUMBER(1,0), NUMBER(1,0), NUMBER(1,0))] into expected type [ROW(NUMBER(38,0), NUMBER(38,0))]",
            answer("SELECT (a, a) IN (SELECT 1, 2, 3 FROM ft) FROM re"));
        assertEquals(REFUSED + "ANY(SELECT 1 AS \"1\", 2 AS \"2\", 3 AS \"3\" FROM (VALUES (null)) DUAL)' of type "
                + "[ROW(NUMBER(1,0), NUMBER(1,0), NUMBER(1,0))] into expected type [ROW(NUMBER(1,0), NUMBER(1,0))]",
            answer("SELECT 1 FROM re WHERE (1, 2) IN (SELECT 1, 2, 3)"));
    }

    @Test
    public void theSubqueryIsRePrintedFromItsPlan() {
        assertEquals(REFUSED + "ANY(SELECT FT.B AS \"X\", FT.D AS \"Y\", FT.B AS \"B\" FROM FT AS FT)' of type "
                + "[ROW(NUMBER(38,0), DATE, NUMBER(38,0))] into expected type [ROW(NUMBER(38,0), NUMBER(38,0))]",
            answer("SELECT 1 FROM re WHERE (a, a) IN (SELECT b AS x, d AS y, b FROM ft)"));
        assertEquals(REFUSED + "ANY(SELECT FT.B AS \"B\", FT.B AS \"B\", FT.D AS \"D\" FROM FT AS FT WHERE FT.B > 1)' of type "
                + "[ROW(NUMBER(38,0), NUMBER(38,0), DATE)] into expected type [ROW(NUMBER(38,0), NUMBER(38,0))]",
            answer("SELECT 1 FROM re WHERE (a, a) IN (SELECT b, b, d FROM ft WHERE b > 1)"));
        assertEquals(REFUSED + "ANY(SELECT FT.B AS \"B\", ST.N AS \"N\", ST.S AS \"S\" FROM FT AS FT INNER JOIN ST AS ST "
                + "ON ((CAST(FT.B AS NUMBER(38,2))) = ST.N))' of type [ROW(NUMBER(38,0), NUMBER(6,2), VARCHAR(5))] "
                + "into expected type [ROW(NUMBER(38,0), NUMBER(38,0))]",
            answer("SELECT 1 FROM re WHERE (a, a) IN (SELECT ft.b, st.n, st.s FROM ft JOIN st ON ft.b = st.n)"));
        assertEquals(REFUSED + "ANY(SELECT ST.S AS \"S\", ST.N AS \"N\", ST.S AS \"S\" FROM ST AS ST)' of type "
                + "[ROW(VARCHAR(5), NUMBER(6,2), VARCHAR(5))] into expected type [ROW(NUMBER(38,0), VARCHAR(1))]",
            answer("SELECT 1 FROM re WHERE (a, 'x') IN (SELECT s, n, s FROM st)"));
        assertEquals(REFUSED + "ANY(SELECT FT.B AS \"B\", FT.D AS \"D\", 1 AS \"1\" FROM FT AS FT)' of type "
                + "[ROW(NUMBER(38,0), DATE, NUMBER(1,0))] into expected type [ROW(NUMBER(38,0), NUMBER(38,0))]",
            answer("SELECT 1 FROM re WHERE (a, a) IN (SELECT *, 1 FROM ft)"));
        assertEquals(REFUSED + "ANY(SELECT F.B AS \"B\", F.D AS \"D\", 'z' AS \"'Z'\" FROM FT AS F)' of type "
                + "[ROW(NUMBER(38,0), DATE, VARCHAR(1))] into expected type [ROW(NUMBER(38,0), NUMBER(38,0))]",
            answer("SELECT 1 FROM re WHERE (a, a) IN (SELECT b, d, 'z' FROM ft f)"));
        assertEquals(REFUSED + "ANY(SELECT 1 AS \"1\", 2 AS \"2\", 3 AS \"3\" FROM FT AS FT  ORDER BY 1 ASC NULLS LAST "
                + "LIMIT 1 OFFSET 0)' of type [ROW(NUMBER(1,0), NUMBER(1,0), NUMBER(1,0))] into expected type "
                + "[ROW(NUMBER(38,0), NUMBER(38,0))]",
            answer("SELECT 1 FROM re WHERE (a, a) IN (SELECT 1, 2, 3 FROM ft ORDER BY 1 LIMIT 1)"));
    }

    @Test
    public void aSubqueryOfTheSameWidthOrOneColumnKeepsItsOwnAnswer() {
        assertEquals("", answer("SELECT 1 FROM re WHERE (a, a) IN (SELECT 1, 2 FROM ft)"));
        assertEquals("SQL compilation error: error line 1 at position 30|Invalid argument types for function '=': "
                + "(ROW(NUMBER(38,0), NUMBER(38,0)), NUMBER(1,0))",
            answer("SELECT 1 FROM re WHERE (a, a) IN (SELECT 1 FROM ft)"));
    }
}
