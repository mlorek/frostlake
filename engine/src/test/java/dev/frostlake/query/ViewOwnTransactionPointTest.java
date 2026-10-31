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
 * A view read at a point inside the session's own open transaction reads its tables as the transaction saw them, its
 * uncommitted changes included, as a table read at that point does (live-verified); and a CHANGES clause over a join
 * view at a statement before one of its tables was created is refused by that table's name.
 */
public class ViewOwnTransactionPointTest extends MeasuredCellsTestSupport {

    /**
     * Points inside the own transaction, a correlated subquery at a view's point, a join view's stream inside a
     * transaction, and a point before a table.
     */
    @Test
    public void ownTransactionPointsAndJoinedTableBirth() {
        engine.execute("CREATE OR REPLACE TABLE vt807 (a INT, b VARCHAR) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO vt807 VALUES (1, 'x')");
        engine.execute("CREATE OR REPLACE VIEW vv807 AS SELECT a, b FROM vt807");
        engine.execute("CREATE OR REPLACE TABLE vu807 (a INT, c INT)");
        engine.execute("INSERT INTO vu807 VALUES (1, 10)");
        engine.execute("CREATE OR REPLACE VIEW vc807 AS SELECT a, b, (SELECT MAX(c) FROM vu807 WHERE vu807.a = "
            + "vt807.a) AS m FROM vt807");
        engine.execute("CREATE OR REPLACE TABLE ja807 (id INT, x VARCHAR) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE TABLE jb807 (id INT, y VARCHAR) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO ja807 VALUES (1, 'a'), (2, 'b')");
        engine.execute("INSERT INTO jb807 VALUES (1, 'p'), (2, 'q')");
        engine.execute("CREATE OR REPLACE VIEW jv807 AS SELECT ja807.id, x, y FROM ja807 JOIN jb807 ON ja807.id = "
            + "jb807.id");
        engine.execute("CREATE OR REPLACE STREAM js807 ON VIEW jv807");
        assertCells(new String[][] {
            {"BEGIN",
                "Statement executed successfully."},
            {"INSERT INTO vt807 VALUES (2, 'y')",
                "1"},
            {"SET q1 = LAST_QUERY_ID()",
                "Statement executed successfully."},
            {"INSERT INTO vt807 VALUES (3, 'z')",
                "1"},
            {"SELECT a, b FROM vt807 AT(STATEMENT => $q1) ORDER BY a",
                "1|x ;; 2|y"},
            {"SELECT a, b FROM vv807 AT(STATEMENT => $q1) ORDER BY a",
                "1|x ;; 2|y"},
            {"SELECT a, b FROM vt807 BEFORE(STATEMENT => $q1) ORDER BY a",
                "1|x"},
            {"SELECT a, b FROM vv807 BEFORE(STATEMENT => $q1) ORDER BY a",
                "1|x"},
            {"SELECT a, b FROM vv807 ORDER BY a",
                "1|x ;; 2|y ;; 3|z"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {"SELECT a, b FROM vt807 AT(STATEMENT => $q1) ORDER BY a",
                "1|x"},
            {"SELECT a, b FROM vv807 AT(STATEMENT => $q1) ORDER BY a",
                "1|x"},
            {"INSERT INTO vu807 VALUES (1, 20)",
                "1"},
            {"SET q2 = LAST_QUERY_ID()",
                "Statement executed successfully."},
            {"UPDATE vu807 SET c = 30 WHERE c = 20",
                "1|0"},
            {"SELECT a, b, m FROM vc807 AT(STATEMENT => $q2) ORDER BY a",
                "1|x|20"},
            {"SELECT a, b, m FROM vc807 BEFORE(STATEMENT => $q2) ORDER BY a",
                "1|x|10"},
            {"SELECT a, b, m FROM vc807 ORDER BY a",
                "1|x|30"},
            {"UPDATE ja807 SET x = 'a2' WHERE id = 1",
                "1|0"},
            {"SELECT id, x, y, METADATA$ACTION, METADATA$ISUPDATE FROM js807 ORDER BY id, METADATA$ACTION",
                "1|a|p|DELETE|true ;; 1|a2|p|INSERT|true"},
            {"SET r1 = (SELECT MIN(METADATA$ROW_ID) FROM js807 WHERE id = 1 AND METADATA$ACTION = 'INSERT')",
                "Statement executed successfully."},
            {"BEGIN",
                "Statement executed successfully."},
            {"UPDATE jb807 SET y = 'q2' WHERE id = 2",
                "1|0"},
            {"SELECT id, x, y, METADATA$ACTION, METADATA$ISUPDATE FROM js807 ORDER BY id, METADATA$ACTION",
                "1|a|p|DELETE|true ;; 1|a2|p|INSERT|true"},
            {"SELECT METADATA$ROW_ID = $r1 FROM js807 WHERE id = 1 AND METADATA$ACTION = 'INSERT'",
                "true"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM js807",
                "1|2"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {"SELECT id, x, y, METADATA$ACTION, METADATA$ISUPDATE FROM js807 ORDER BY id, METADATA$ACTION",
                "1|a|p|DELETE|true ;; 1|a2|p|INSERT|true"},
            {"SELECT METADATA$ROW_ID = $r1 FROM js807 WHERE id = 1 AND METADATA$ACTION = 'INSERT'",
                "true"},
            {"SELECT 1",
                "1"},
            {"SET q0 = LAST_QUERY_ID()",
                "Statement executed successfully."},
            {"CREATE OR REPLACE TABLE jn807 (id INT, z VARCHAR) CHANGE_TRACKING = TRUE",
                "Table JN807 successfully created."},
            {"INSERT INTO jn807 VALUES (1, 'n')",
                "1"},
            {"CREATE OR REPLACE VIEW jw807 CHANGE_TRACKING = TRUE AS SELECT ja807.id, x, z FROM ja807 JOIN jn807 ON "
                + "ja807.id = jn807.id",
                "View JW807 successfully created."},
            {"SELECT * FROM jw807 CHANGES(INFORMATION => DEFAULT) AT(STATEMENT => $q0)",
                "REFUSED Time travel data is not available for table JN807. The requested time is either beyond the "
                + "allowed time travel period or before the object creation time."},
            {"SELECT * FROM jw807 AT(STATEMENT => $q0)",
                "REFUSED Time travel data is not available for table JN807. The requested time is either beyond the "
                + "allowed time travel period or before the object creation time."},
            {"SELECT * FROM vv807 CHANGES(INFORMATION => DEFAULT) AT(STATEMENT => $q0)",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time range "
                + "requested on table 'TEST_DB.TEST_SCHEMA.VV807'."},
        });
        engine.execute("UNSET (q0, q1, q2, r1)");
    }
}
