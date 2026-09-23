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
 * A window call in a subquery that reads the outer row — in its arguments, PARTITION BY or ORDER BY — is refused
 * while the statement compiles, wherever the subquery stands and whether or not a row reaches it, naming the call
 * as the plan prints it. Every cell is live-verified.
 */
public class CorrelatedWindowCallTest extends BaseDatabaseTest {

    private static final String REFUSED = "SQL compilation error:|Window function [";

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
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

    /** {@code SELECT fz.id, <subquery> FROM fz ORDER BY 1}. */
    private String perRow(final String subquery) {
        return answer("SELECT fz.id, " + subquery + " FROM fz ORDER BY 1");
    }

    @Test
    public void aWindowReadingTheOuterRowIsRefusedByItsPlanEcho() {
        assertEquals(REFUSED + "MAX(G.V) OVER (PARTITION BY FZ.ID)] contains a correlation.",
            perRow("(SELECT MAX(v) OVER (PARTITION BY fz.id) FROM g LIMIT 1)"));
        assertEquals(REFUSED + "MAX(G.V) OVER (ORDER BY FZ.ID ASC NULLS LAST)] contains a correlation.",
            perRow("(SELECT MAX(v) OVER (ORDER BY fz.id) FROM g LIMIT 1)"));
        assertEquals(REFUSED + "MAX(G.V + FZ.ID) OVER ()] contains a correlation.",
            perRow("(SELECT MAX(v + fz.id) OVER () FROM g LIMIT 1)"));
        assertEquals(REFUSED + "ROW_NUMBER() OVER (ORDER BY FZ.ID ASC NULLS LAST)] contains a correlation.",
            perRow("(SELECT ROW_NUMBER() OVER (ORDER BY fz.id) FROM g LIMIT 1)"));
        assertEquals(REFUSED + "SUM(G.V) OVER (PARTITION BY G.ID ORDER BY FZ.ID ASC NULLS LAST)] contains a correlation.",
            perRow("(SELECT SUM(v) OVER (PARTITION BY g.id ORDER BY fz.id) FROM g LIMIT 1)"));
        assertEquals(REFUSED + "FIRST_VALUE(G.V) OVER (PARTITION BY FZ.ID ORDER BY G.ID ASC NULLS LAST)] contains a correlation.",
            perRow("(SELECT FIRST_VALUE(v) OVER (PARTITION BY fz.id ORDER BY g.id) FROM g LIMIT 1)"));
        assertEquals(REFUSED + "MAX(G.V) OVER (PARTITION BY FZ.B)] contains a correlation.",
            perRow("(SELECT MAX(v) OVER (PARTITION BY fz.b) FROM g LIMIT 1)"));
        assertEquals(REFUSED + "MAX(FZ.ID) OVER ()] contains a correlation.",
            perRow("(SELECT MAX(fz.id) OVER () FROM g LIMIT 1)"));
        assertEquals(REFUSED + "NTILE(2) OVER (ORDER BY FZ.ID DESC NULLS FIRST)] contains a correlation.",
            perRow("(SELECT NTILE(2) OVER (ORDER BY fz.id DESC) FROM g LIMIT 1)"));
        assertEquals(REFUSED + "MAX(G.V) OVER (PARTITION BY FZ.ID)] contains a correlation.",
            perRow("(SELECT MAX(v) OVER (PARTITION BY fz.id) + MIN(v) OVER (PARTITION BY fz.id) FROM g LIMIT 1)"));
        assertEquals(REFUSED + "MAX(G.V) OVER (PARTITION BY FZ.ID)] contains a correlation.",
            perRow("(SELECT MAX(v) OVER (PARTITION BY fz.id) FROM g WHERE g.id = fz.id)"));
    }

    @Test
    public void theRefusalStandsWhereverTheSubqueryIsAndWhetherARowReachesIt() {
        assertEquals(REFUSED + "MAX(G.V) OVER (PARTITION BY FZ.ID)] contains a correlation.",
            answer("SELECT fz.id FROM fz WHERE EXISTS (SELECT MAX(v) OVER (PARTITION BY fz.id) FROM g)"));
        assertEquals(REFUSED + "ROW_NUMBER() OVER (ORDER BY FZ.ID ASC NULLS LAST)] contains a correlation.",
            answer("SELECT fz.id FROM fz WHERE EXISTS (SELECT 1 FROM g QUALIFY ROW_NUMBER() OVER (ORDER BY fz.id) = 1)"));
        assertEquals(REFUSED + "LAG(G.V) OVER (ORDER BY FZ.ID ASC NULLS LAST)] contains a correlation.",
            answer("SELECT fz.id FROM fz WHERE fz.id IN (SELECT LAG(v) OVER (ORDER BY fz.id) FROM g)"));
        assertEquals(REFUSED + "MAX(G.V) OVER (PARTITION BY FZ.ID)] contains a correlation.",
            answer("SELECT fz.id, l.m FROM fz, LATERAL (SELECT MAX(v) OVER (PARTITION BY fz.id) AS m FROM g) l ORDER BY 1"));
        assertEquals(REFUSED + "MAX(G.V) OVER (PARTITION BY FZ.ID)] contains a correlation.",
            answer("SELECT fz.id, (SELECT MAX(v) OVER (PARTITION BY fz.id) FROM g LIMIT 1) FROM fz WHERE FALSE"));
        assertEquals(REFUSED + "MAX(G.V) OVER (PARTITION BY FZ.ID)] contains a correlation.",
            answer("SELECT fz.id, (SELECT MAX(v) OVER (PARTITION BY fz.id) FROM g LIMIT 1) FROM fz WHERE 1 = 0"));
    }
}
