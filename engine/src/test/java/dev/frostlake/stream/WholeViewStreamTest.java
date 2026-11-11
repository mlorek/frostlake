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

package dev.frostlake.stream;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A stream on a view whose query reads a view that joins, a common table expression, or a common table expression
 * joined to a table is created, as it is on the account, and reads the view's changes (live-verified): its base tables
 * are every table the view's expanded query reads, in full, and change tracking turns on for the view and the views
 * below it.
 */
public class WholeViewStreamTest extends MeasuredCellsTestSupport {

    @Test
    public void aStreamOnAViewOverAJoinViewOrACommonTableExpression() {
        engine.execute("CREATE OR REPLACE TABLE sa906 (id INT, v VARCHAR) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE TABLE sb906 (id INT, w VARCHAR) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE VIEW vj906 AS SELECT a.id, b.w FROM sa906 a JOIN sb906 b ON a.id = b.id");
        engine.execute("CREATE OR REPLACE VIEW vvj906 AS SELECT * FROM vj906");
        engine.execute("CREATE OR REPLACE VIEW vc906 AS WITH c AS (SELECT id, v FROM sa906) SELECT id, v FROM c");
        engine.execute("CREATE OR REPLACE VIEW vn906 AS SELECT id, v, w FROM sa906 NATURAL JOIN sb906");
        engine.execute("CREATE OR REPLACE VIEW vd906 AS SELECT x.id, sb906.w FROM (SELECT id FROM sa906) x JOIN "
            + "sb906 ON x.id = sb906.id");
        engine.execute("CREATE OR REPLACE VIEW vcj906 AS WITH c AS (SELECT id FROM sa906) SELECT c.id, b.w FROM c "
            + "JOIN sb906 b ON c.id = b.id");
        assertCells(new String[][] {
            {"CREATE OR REPLACE STREAM st906a ON VIEW vvj906", "Stream ST906A successfully created."},
            {"CREATE OR REPLACE STREAM st906b ON VIEW vc906", "Stream ST906B successfully created."},
            {"CREATE OR REPLACE STREAM st906c ON VIEW vn906", "Stream ST906C successfully created."},
            {"CREATE OR REPLACE STREAM st906d ON VIEW vd906", "Stream ST906D successfully created."},
            {"CREATE OR REPLACE STREAM st906e ON VIEW vcj906", "Stream ST906E successfully created."},
            {"INSERT INTO sa906 VALUES (1, 'a'), (2, 'b'), (3, 'c')", "3"},
            {"INSERT INTO sb906 VALUES (1, 'p'), (3, 'r'), (4, 's')", "3"},
            {"SELECT * EXCLUDE (METADATA$ROW_ID) FROM st906a ORDER BY 1", "1|p|INSERT|false ;; 3|r|INSERT|false"},
            {"SELECT * EXCLUDE (METADATA$ROW_ID) FROM st906b ORDER BY 1",
                "1|a|INSERT|false ;; 2|b|INSERT|false ;; 3|c|INSERT|false"},
            {"SELECT * EXCLUDE (METADATA$ROW_ID) FROM st906c ORDER BY 1", "1|a|p|INSERT|false ;; 3|c|r|INSERT|false"},
            {"SELECT * EXCLUDE (METADATA$ROW_ID) FROM st906d ORDER BY 1", "1|p|INSERT|false ;; 3|r|INSERT|false"},
            {"SELECT * EXCLUDE (METADATA$ROW_ID) FROM st906e ORDER BY 1", "1|p|INSERT|false ;; 3|r|INSERT|false"},
            {"SHOW STREAMS LIKE 'ST906A' ->> SELECT \"base_tables\", \"type\", \"stale\", \"mode\" FROM $1",
                "TEST_DB.TEST_SCHEMA.SA906, TEST_DB.TEST_SCHEMA.SB906|DELTA|false|DEFAULT"},
            {"SHOW STREAMS LIKE 'ST906B' ->> SELECT \"base_tables\", \"type\", \"stale\", \"mode\" FROM $1",
                "TEST_DB.TEST_SCHEMA.SA906|DELTA|false|DEFAULT"},
            {"SHOW STREAMS LIKE 'ST906C' ->> SELECT \"base_tables\", \"type\", \"stale\", \"mode\" FROM $1",
                "TEST_DB.TEST_SCHEMA.SA906, TEST_DB.TEST_SCHEMA.SB906|DELTA|false|DEFAULT"},
            {"SHOW STREAMS LIKE 'ST906D' ->> SELECT \"base_tables\", \"type\", \"stale\", \"mode\" FROM $1",
                "TEST_DB.TEST_SCHEMA.SA906, TEST_DB.TEST_SCHEMA.SB906|DELTA|false|DEFAULT"},
            {"SHOW STREAMS LIKE 'ST906E' ->> SELECT \"base_tables\", \"type\", \"stale\", \"mode\" FROM $1",
                "TEST_DB.TEST_SCHEMA.SA906, TEST_DB.TEST_SCHEMA.SB906|DELTA|false|DEFAULT"},
            {"SHOW VIEWS LIKE 'V%%906' ->> SELECT \"name\", \"change_tracking\" FROM $1 ORDER BY 1",
                "VC906|ON ;; VCJ906|ON ;; VD906|ON ;; VJ906|ON ;; VN906|ON ;; VVJ906|ON"},
        });
    }
}
