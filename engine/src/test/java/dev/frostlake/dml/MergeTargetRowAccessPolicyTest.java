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

package dev.frostlake.dml;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A MERGE reads its target through the target's row access policy, as an UPDATE and a DELETE do: a row the
 * policy withholds is no match, so WHEN MATCHED neither updates nor deletes it and WHEN NOT MATCHED inserts the
 * source row beside it, and the counts tell only the rows the policy shows (live-verified).
 */
public class MergeTargetRowAccessPolicyTest extends MeasuredCellsTestSupport {

    @Test
    public void aMergeMatchesOnlyTheTargetRowsItsPolicyShows() {
        assertAnswer("Table MT successfully created.", "CREATE OR REPLACE TABLE mt (i INT, s VARCHAR)");
        assertAnswer("3", "INSERT INTO mt VALUES (1, 'a'), (2, 'b'), (3, 'c')");
        assertAnswer("Table MS successfully created.", "CREATE OR REPLACE TABLE ms (i INT, s VARCHAR)");
        assertAnswer("3", "INSERT INTO ms VALUES (1, 'x'), (2, 'y'), (4, 'z')");
        assertAnswer("Row access policy 'MRAP' is successfully created.",
            "CREATE OR REPLACE ROW ACCESS POLICY mrap AS (i INT) RETURNS BOOLEAN -> i = 1");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mt ADD ROW ACCESS POLICY mrap ON (i)");
        assertAnswer("1|a", "SELECT * FROM mt ORDER BY i");
        assertAnswer("2|1",
            "MERGE INTO mt USING ms ON mt.i = ms.i WHEN MATCHED THEN UPDATE SET s = ms.s WHEN NOT MATCHED THEN "
                + "INSERT VALUES (ms.i, ms.s)");
        assertAnswer("1|x", "SELECT * FROM mt ORDER BY i, s");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mt DROP ROW ACCESS POLICY mrap");
        assertAnswer("1|x ;; 2|b ;; 2|y ;; 3|c ;; 4|z", "SELECT * FROM mt ORDER BY i, s");
        assertAnswer("Table MT2 successfully created.", "CREATE OR REPLACE TABLE mt2 (i INT, s VARCHAR)");
        assertAnswer("3", "INSERT INTO mt2 VALUES (1, 'a'), (2, 'b'), (3, 'c')");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mt2 ADD ROW ACCESS POLICY mrap ON (i)");
        assertAnswer("1", "MERGE INTO mt2 USING ms ON mt2.i = ms.i WHEN MATCHED THEN DELETE");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mt2 DROP ROW ACCESS POLICY mrap");
        assertAnswer("2|b ;; 3|c", "SELECT * FROM mt2 ORDER BY i");
        assertAnswer("Table MT3 successfully created.", "CREATE OR REPLACE TABLE mt3 (i INT, s VARCHAR)");
        assertAnswer("3", "INSERT INTO mt3 VALUES (1, 'a'), (2, 'b'), (3, 'c')");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mt3 ADD ROW ACCESS POLICY mrap ON (i)");
        assertAnswer("1|0|1",
            "MERGE INTO mt3 USING ms ON mt3.i = ms.i WHEN MATCHED AND ms.i = 2 THEN UPDATE SET s = 'two' WHEN "
                + "MATCHED THEN DELETE WHEN NOT MATCHED AND ms.i = 2 THEN INSERT VALUES (20, 'new')");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mt3 DROP ROW ACCESS POLICY mrap");
        assertAnswer("2|b ;; 3|c ;; 20|new", "SELECT * FROM mt3 ORDER BY i, s");
        assertAnswer("Table MT4 successfully created.", "CREATE OR REPLACE TABLE mt4 (i INT, s VARCHAR)");
        assertAnswer("2", "INSERT INTO mt4 VALUES (1, 'a'), (2, 'b')");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mt4 ADD ROW ACCESS POLICY mrap ON (i)");
        assertAnswer("2|0",
            "MERGE INTO mt4 USING (SELECT 2 AS i, 'p' AS s UNION ALL SELECT 2, 'q') src ON mt4.i = src.i WHEN "
                + "MATCHED THEN UPDATE SET s = src.s WHEN NOT MATCHED THEN INSERT VALUES (src.i, src.s)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mt4 DROP ROW ACCESS POLICY mrap");
        assertAnswer("1|a ;; 2|b ;; 2|p ;; 2|q", "SELECT * FROM mt4 ORDER BY i, s");
        assertAnswer("Table MT5 successfully created.", "CREATE OR REPLACE TABLE mt5 (i INT, s VARCHAR)");
        assertAnswer("2", "INSERT INTO mt5 VALUES (1, 'a'), (2, 'b')");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mt5 ADD ROW ACCESS POLICY mrap ON (i)");
        assertAnswer("1|0", "UPDATE mt5 SET s = ms.s FROM ms WHERE mt5.i = ms.i");
        assertAnswer("1", "DELETE FROM mt5 USING ms WHERE mt5.i = ms.i");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mt5 DROP ROW ACCESS POLICY mrap");
        assertAnswer("2|b", "SELECT * FROM mt5 ORDER BY i, s");
    }
}
