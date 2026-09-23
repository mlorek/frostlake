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
 * A FROM relation's select item — a view's, a derived table's, a CTE's, a LATERAL body's — is computed only
 * where the reading statement reaches that row's value. A row the outer filter drops, a branch not taken, a
 * column an EXISTS or a star passes through, one row nobody compares never raise the item's fault; reading the
 * cell does — in the select list, a two-row sort, a grouping, an aggregate, a join key, a window's order, a
 * dedup, a write. AND and OR answer from whichever operand decides them, even when the other one faults. Every
 * cell is live-verified.
 */
public class DeferredRelationFaultTest extends BaseDatabaseTest {

    private static final String TOO_LONG = "String 'abcdefgh' is too long and would be truncated";

    @BeforeEach
    public void createRelations() {
        engine.execute("CREATE OR REPLACE TABLE nc (s VARCHAR, n INT)");
        engine.execute("INSERT INTO nc VALUES ('abcdefgh', 1)");
        engine.execute("CREATE OR REPLACE VIEW nc_v AS SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc");
        engine.execute("CREATE OR REPLACE TABLE nc2 (s VARCHAR, n INT)");
        engine.execute("INSERT INTO nc2 VALUES ('abcdefgh', 1), ('xyz', 2)");
        engine.execute("CREATE OR REPLACE VIEW nc2_v AS SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc2");
    }

    /** Every row's first cell, joined with " | ", "no row", or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder cells = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                cells.append(cells.length() > 0 ? " | " : "").append(row.getValue(0));
            }
            return cells.length() > 0 ? cells.toString() : "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void aViewColumnRaisesOnlyWhereARowIsRead() {
        assertCells(new String[][] {
            {"SELECT c FROM nc_v WHERE n = 2", "no row"},
            {"SELECT n FROM nc_v WHERE n = 1 OR c IS NULL", "1"},
            {"SELECT n FROM nc_v WHERE n = 2 AND c IS NULL", "no row"},
            {"SELECT IFF(n = 1, 0, LENGTH(c)) FROM nc_v", "0"},
            {"SELECT COALESCE(n, LENGTH(c)) FROM nc_v", "1"},
            {"SELECT CASE WHEN n = 1 THEN 'x' ELSE c END FROM nc_v", "x"},
            {"SELECT IFF(EXISTS (SELECT c FROM nc_v), 'y', 'n')", "y"},
            {"SELECT n FROM (SELECT * FROM nc_v)", "1"},
            {"SELECT n FROM nc_v ORDER BY c", "1"},
            {"SELECT n FROM nc2_v WHERE n = 2 ORDER BY c", "2"},
            {"SELECT DISTINCT c FROM nc_v WHERE n = 2", "no row"},
            {"SELECT COUNT(*) FROM nc_v", "1"},
            {"SELECT MAX(n) FROM nc_v", "1"},
            {"SELECT n FROM nc_v UNION SELECT n FROM nc_v", "1"},
            {"SELECT COUNT(*) FROM (SELECT * FROM nc_v UNION ALL SELECT * FROM nc_v)", "2"},
            {"SELECT n FROM nc_v a WHERE EXISTS (SELECT 1 FROM nc_v b WHERE b.n = a.n)", "1"},
            {"SELECT (SELECT n FROM nc_v) + 1", "2"},
            {"SELECT n FROM nc_v WHERE n IN (SELECT n FROM nc_v)", "1"},
            {"SELECT COUNT(*) FROM nc_v WHERE n = 1 GROUP BY n HAVING MAX(n) = 1", "1"},
        });
    }

    @Test
    public void readingTheCellRaisesItsFault() {
        assertCells(new String[][] {
            {"SELECT c FROM nc_v", TOO_LONG},
            {"SELECT * FROM nc_v", TOO_LONG},
            {"SELECT n FROM nc_v WHERE c IS NOT NULL", TOO_LONG},
            {"SELECT LENGTH(c) FROM nc_v WHERE n = 1", TOO_LONG},
            {"SELECT n FROM nc2_v ORDER BY c", TOO_LONG},
            {"SELECT COUNT(*) FROM nc2_v GROUP BY c", TOO_LONG},
            {"SELECT COUNT(c) FROM nc_v", TOO_LONG},
            {"SELECT a.n FROM nc_v a JOIN nc_v b ON a.c = b.c", TOO_LONG},
            {"SELECT n FROM nc_v QUALIFY ROW_NUMBER() OVER (ORDER BY c) = 1", TOO_LONG},
            {"SELECT COUNT(*) FROM nc_v WHERE c = c", TOO_LONG},
            {"SELECT n, c IS NULL FROM nc_v", TOO_LONG},
            {"SELECT COUNT(*) FROM (SELECT DISTINCT * FROM nc_v)", TOO_LONG},
            {"SELECT COUNT(*) FROM (SELECT * FROM nc_v UNION SELECT * FROM nc_v)", TOO_LONG},
            {"SELECT OBJECT_CONSTRUCT(*) FROM nc_v", TOO_LONG},
        });
    }

    @Test
    public void derivedTablesCtesAndLateralBodiesWaitToo() {
        assertCells(new String[][] {
            {"SELECT c FROM (SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc) WHERE n = 2", "no row"},
            {"SELECT n FROM (SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc) WHERE n = 1 OR c IS NULL", "1"},
            {"SELECT IFF(n = 1, 0, LENGTH(c)) FROM (SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc)", "0"},
            {"SELECT n FROM (SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc) ORDER BY c", "1"},
            {"WITH w AS (SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc) SELECT c FROM w WHERE n = 2", "no row"},
            {"WITH w AS (SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc) SELECT IFF(n = 1, 0, LENGTH(c)) FROM w", "0"},
            {"WITH w AS (SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc) SELECT 1", "1"},
            {"SELECT n FROM nc, LATERAL (SELECT CAST(nc.s AS VARCHAR(5)) AS c) l", "1"},
            {"SELECT n FROM nc, LATERAL (SELECT CAST(nc.s AS VARCHAR(5)) AS c) l WHERE n = 2 AND l.c IS NULL", "no row"},
            {"SELECT n FROM nc_v, LATERAL (SELECT c AS d) l", "1"},
            {"SELECT n FROM nc_v, LATERAL (SELECT c AS d) l WHERE n = 2 AND l.d = 'x'", "no row"},
        });
    }

    @Test
    public void aWriteReadsEveryCellItWrites() {
        engine.execute("CREATE OR REPLACE TABLE tgt (n INT)");
        engine.execute("CREATE OR REPLACE TABLE tgt2 (c VARCHAR, n INT)");
        engine.execute("INSERT INTO tgt (n) SELECT n FROM nc_v");
        engine.execute("CREATE OR REPLACE TABLE ct AS SELECT n FROM nc_v");
        engine.execute("CREATE OR REPLACE TABLE ct2 AS SELECT * FROM nc_v WHERE n = 2");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM tgt", "1"},
            {"SELECT COUNT(*) FROM ct", "1"},
            {"SELECT COUNT(*) FROM ct2", "0"},
            {"INSERT INTO tgt2 SELECT * FROM nc_v", "DML operation to table TGT2 failed on column C with error: " + TOO_LONG},
            {"CREATE OR REPLACE TABLE ct3 AS SELECT * FROM nc_v",
                "DML operation to table TEST_DB.TEST_SCHEMA.CT3 failed on column C with error: " + TOO_LONG},
        });
    }

    @Test
    public void andOrAnswerFromTheDecidingOperand() {
        final String notANumber = "Numeric value 'abcdefgh' is not recognized";
        assertCells(new String[][] {
            {"SELECT n FROM nc WHERE n = 2 AND TO_NUMBER(s) = 1", "no row"},
            {"SELECT n FROM nc WHERE n = 1 OR TO_NUMBER(s) = 1", "1"},
            {"SELECT n FROM nc WHERE TO_NUMBER(s) = 1 AND n = 2", "no row"},
            {"SELECT n FROM nc WHERE TO_NUMBER(s) = 1 OR n = 1", "1"},
            {"SELECT IFF(n = 2 AND TO_NUMBER(s) = 1, 'y', 'n') FROM nc", "n"},
            {"SELECT n FROM nc WHERE NOT (n = 1 OR TO_NUMBER(s) = 1)", "no row"},
            {"SELECT CASE WHEN n = 2 AND TO_NUMBER(s) = 1 THEN 1 ELSE 0 END FROM nc", "0"},
            {"SELECT COUNT_IF(n = 2 AND TO_NUMBER(s) = 1) FROM nc", "0"},
            {"SELECT NULL AND TO_NUMBER(s) = 1 FROM nc", notANumber},
            {"SELECT n FROM nc WHERE n = 1 AND TO_NUMBER(s) = 1", notANumber},
        });
    }
}
