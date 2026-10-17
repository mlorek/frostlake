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

import org.junit.jupiter.api.Test;

/**
 * A MERGE reads its source as a query reads it (live-verified): a table source's row access policy keeps only the
 * rows the session may see and its masking policy masks what they carry, whether the MERGE inserts or updates from
 * them, and a stream source keeps only the rows its table's policy allows.
 */
public class MergeSourcePolicyTest extends MeasuredCellsTestSupport {

    @Test
    public void aMergeSourceIsReadThroughItsPolicies() {
        engine.execute("CREATE OR REPLACE TABLE mt (id INT, s VARCHAR)");
        engine.execute("INSERT INTO mt VALUES (1, 'alpha'), (2, 'beta')");
        engine.execute("CREATE OR REPLACE TABLE mo (id INT, s VARCHAR)");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp1 AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("ALTER TABLE mt MODIFY COLUMN s SET MASKING POLICY mp1");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rap2 AS (i INT) RETURNS BOOLEAN -> i = 1");
        engine.execute("ALTER TABLE mt ADD ROW ACCESS POLICY rap2 ON (id)");
        assertCells(new String[][] {
            {"SELECT * FROM mt ORDER BY id", "1|***"},
            {"MERGE INTO mo USING mt ON mo.id = mt.id WHEN NOT MATCHED THEN INSERT VALUES (mt.id, mt.s)", "1"},
            {"SELECT id, s FROM mo ORDER BY id", "1|***"},
        });
        engine.execute("DELETE FROM mo");
        engine.execute("INSERT INTO mo VALUES (1, 'x'), (2, 'y')");
        assertCells(new String[][] {
            {"MERGE INTO mo USING mt AS src ON mo.id = src.id WHEN MATCHED THEN UPDATE SET mo.s = src.s", "1"},
            {"SELECT id, s FROM mo ORDER BY id", "1|*** ;; 2|y"},
        });
        engine.execute("CREATE OR REPLACE STREAM mst ON TABLE mt SHOW_INITIAL_ROWS = TRUE");
        engine.execute("DELETE FROM mo");
        assertCells(new String[][] {
            {"MERGE INTO mo USING mst ON mo.id = mst.id WHEN NOT MATCHED THEN INSERT VALUES (mst.id, mst.s)", "1"},
            {"SELECT id, s FROM mo ORDER BY id", "1|***"},
        });
    }
}
