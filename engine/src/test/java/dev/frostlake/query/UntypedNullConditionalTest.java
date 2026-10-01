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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A conditional that can answer nothing but an untyped NULL has no type at all, as the bare word NULL has none:
 * SYSTEM$TYPEOF reads NULL[LOB] for IFF, CASE, COALESCE, NVL, IFNULL, NVL2, DECODE, GREATEST and a NULLIF whose
 * first argument is one, through a derived column too, and a concatenation, arithmetic or a cast reads it as it
 * reads the word. A typed NULL branch keeps its type. Every cell is live-verified.
 */
public class UntypedNullConditionalTest extends BaseDatabaseTest {

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

    @Test
    public void everyAllNullPickHasNoType() {
        assertEquals("NULL[LOB], NULL[LOB], NULL[LOB], NULL[LOB], NULL[LOB]",
            rows("SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL, NULL)), SYSTEM$TYPEOF(COALESCE(NULL, NULL)),"
                + " SYSTEM$TYPEOF(CASE WHEN TRUE THEN NULL END), SYSTEM$TYPEOF(NVL(NULL, NULL)),"
                + " SYSTEM$TYPEOF(IFF(FALSE, NULL, NULL))"));
        assertEquals("NULL[LOB], NULL[LOB], NULL[LOB], NULL[LOB], NULL[LOB]",
            rows("SELECT SYSTEM$TYPEOF(NULLIF(NULL, NULL)), SYSTEM$TYPEOF(NVL2(NULL, NULL, NULL)),"
                + " SYSTEM$TYPEOF(DECODE(1, 1, NULL, NULL)), SYSTEM$TYPEOF(GREATEST(NULL, NULL)),"
                + " SYSTEM$TYPEOF(IFNULL(NULL, NULL))"));
        assertEquals("NULL[LOB], NULL[LOB], NULL[LOB]",
            rows("SELECT SYSTEM$TYPEOF(NULLIF(NULL, 1)), SYSTEM$TYPEOF(IFF(NULL, NULL, NULL)),"
                + " SYSTEM$TYPEOF(CASE NULL WHEN NULL THEN NULL END)"));
        engine.execute("CREATE OR REPLACE TABLE unc_t (a INT)");
        engine.execute("INSERT INTO unc_t VALUES (1), (2)");
        assertEquals("NULL[LOB], NULL[LOB], NULL[LOB] | NULL[LOB], NULL[LOB], NULL[LOB]",
            rows("SELECT SYSTEM$TYPEOF(IFF(a > 1, NULL, NULL)), SYSTEM$TYPEOF(CASE WHEN a > 1 THEN NULL ELSE NULL END),"
                + " SYSTEM$TYPEOF(COALESCE(NULL, NULL, NULL)) FROM unc_t"));
    }

    @Test
    public void itReadsAsTheBareWordDoes() {
        assertEquals("NULL[LOB]", rows("SELECT SYSTEM$TYPEOF(x) FROM (SELECT IFF(TRUE, NULL, NULL) AS x)"));
        assertEquals("VARCHAR(134217728)[LOB], NUMBER(19,0)[SB1], NUMBER(38,0)[SB1]",
            rows("SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL, NULL) || 'a'), SYSTEM$TYPEOF(IFF(TRUE, NULL, NULL) + 1),"
                + " SYSTEM$TYPEOF(NVL(NULL, NULL)::NUMBER)"));
        assertEquals("null, null, null", rows("SELECT IFF(TRUE, NULL, NULL) AS x, COALESCE(NULL, NULL) AS y,"
            + " CASE WHEN TRUE THEN NULL END AS z"));
        engine.execute("CREATE OR REPLACE TABLE unc_copy AS SELECT IFF(TRUE, NULL, NULL) AS x, COALESCE(NULL, NULL) AS y,"
            + " CASE WHEN TRUE THEN NULL END AS z, NULL AS w");
        assertEquals("VARCHAR(16777216)", describeCell("unc_copy", "X", "type"));
        assertEquals("VARCHAR(16777216)", describeCell("unc_copy", "Z", "type"));
    }

    @Test
    public void aTypedNullBranchKeepsItsType() {
        assertEquals("NUMBER(38,0)[SB1], NUMBER(2,0)[SB1], NULL[LOB], NULL[LOB]",
            rows("SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL::INT, NULL)), SYSTEM$TYPEOF(ZEROIFNULL(NULL)),"
                + " SYSTEM$TYPEOF((SELECT NULL)), SYSTEM$TYPEOF(IFF(TRUE, NULL, (SELECT NULL)))"));
    }
}
