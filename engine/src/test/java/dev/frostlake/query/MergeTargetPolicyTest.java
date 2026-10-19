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
 * A MERGE changes only the target rows its row access policy lets the session see, as an UPDATE and a DELETE do:
 * a hidden row is neither updated nor deleted and counts for nothing, and a source row that matches a hidden row
 * alone is NOT MATCHED, so its INSERT lands beside the hidden row (all live-verified).
 */
public class MergeTargetPolicyTest extends MeasuredCellsTestSupport {

    @Test
    public void aMergeChangesOnlyTheTargetRowsThePolicyShows() {
        engine.execute("CREATE OR REPLACE TABLE mtr (id INT, v INT)");
        engine.execute("INSERT INTO mtr VALUES (1, 10), (2, 20)");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY mrap AS (i INT) RETURNS BOOLEAN -> i = 1");
        engine.execute("ALTER TABLE mtr ADD ROW ACCESS POLICY mrap ON (id)");
        assertAnswer("1|10", "SELECT * FROM mtr ORDER BY id");
        assertAnswer("0", "MERGE INTO mtr USING (SELECT 2 id) s ON mtr.id = s.id WHEN MATCHED THEN UPDATE SET v = 99");
        assertAnswer("1",
            "MERGE INTO mtr USING (SELECT 1 id UNION ALL SELECT 2 id) s ON mtr.id = s.id WHEN MATCHED THEN UPDATE "
                + "SET v = v + 1");
        assertAnswer("1|11", "SELECT * FROM mtr ORDER BY id");
        assertAnswer("1|1",
            "MERGE INTO mtr USING (SELECT 1 id UNION ALL SELECT 2 id) s ON mtr.id = s.id WHEN MATCHED THEN UPDATE "
                + "SET v = v + 1 WHEN NOT MATCHED THEN INSERT VALUES (s.id, 0)");
        assertAnswer("1|12", "SELECT * FROM mtr ORDER BY id, v");
        assertAnswer("1",
            "MERGE INTO mtr USING (SELECT 2 id) s ON mtr.id = s.id WHEN NOT MATCHED THEN INSERT VALUES (s.id, -1)");
        assertAnswer("1",
            "MERGE INTO mtr USING (SELECT 1 id UNION ALL SELECT 2 id) s ON mtr.id = s.id WHEN MATCHED THEN DELETE");
        assertAnswer("", "SELECT * FROM mtr ORDER BY id, v");
        assertAnswer("1|0",
            "MERGE INTO mtr USING (SELECT 2 id) s ON mtr.id = s.id WHEN MATCHED THEN DELETE WHEN NOT MATCHED THEN "
                + "INSERT VALUES (s.id, 7)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mtr DROP ROW ACCESS POLICY mrap");
        assertAnswer("2|-1 ;; 2|0 ;; 2|7 ;; 2|20", "SELECT * FROM mtr ORDER BY id, v");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mtr ADD ROW ACCESS POLICY mrap ON (id)");
        assertAnswer("0|0", "UPDATE mtr SET v = 5");
        assertAnswer("0", "DELETE FROM mtr WHERE id = 2");
        assertAnswer("0", "DELETE FROM mtr");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mtr DROP ROW ACCESS POLICY mrap");
        assertAnswer("2|-1 ;; 2|0 ;; 2|7 ;; 2|20", "SELECT * FROM mtr ORDER BY id, v");
    }
}
