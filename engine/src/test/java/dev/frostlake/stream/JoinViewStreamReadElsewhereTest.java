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
 * A stream on a join view reads the tables the view names in the view's own schema, whichever schema the
 * reading session is in and whatever tables of those names it holds; the view, its CHANGES and the stream
 * agree (all live-verified).
 */
public class JoinViewStreamReadElsewhereTest extends MeasuredCellsTestSupport {

    @Test
    public void joinViewReadFromAnotherSchema() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.ja (id INT, x STRING) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.jb (id INT, y STRING) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO test_db.test_schema.ja VALUES (1, 'a1'), (2, 'a2')");
        engine.execute("INSERT INTO test_db.test_schema.jb VALUES (1, 'b1'), (2, 'b2')");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.jv AS SELECT ja.id, x, y FROM ja JOIN jb ON ja.id "
            + "= jb.id");
        engine.execute("CREATE OR REPLACE STREAM test_db.test_schema.js ON VIEW test_db.test_schema.jv");
        engine.execute("UPDATE test_db.test_schema.ja SET x = 'a1u' WHERE id = 1");
        engine.execute("CREATE OR REPLACE SCHEMA test_db.jo");
        engine.execute("CREATE OR REPLACE TABLE test_db.jo.ja (q INT)");
        engine.execute("CREATE OR REPLACE TABLE test_db.jo.jb (z DATE)");
        engine.execute("USE SCHEMA test_db.jo");
        assertAnswer("1|a1|b1|DELETE ;; 1|a1u|b1|INSERT",
            "SELECT id, x, y, METADATA$ACTION FROM test_db.test_schema.js ORDER BY METADATA$ACTION, x");
        assertAnswer("2", "SELECT COUNT(*) FROM test_db.test_schema.jv");
        assertAnswer("2", "SELECT COUNT(*) FROM test_db.test_schema.js");
        assertAnswer("1|a1|DELETE ;; 1|a1u|INSERT",
            "SELECT id, x, METADATA$ACTION FROM test_db.test_schema.jv CHANGES(INFORMATION => DEFAULT) AT(STREAM => "
                + "'test_db.test_schema.js') ORDER BY 3, 2");
        assertAnswer("1|a1u|b1 ;; 2|a2|b2", "SELECT id, x, y FROM test_db.test_schema.jv ORDER BY id");
        answer("USE SCHEMA test_db.test_schema");
        assertAnswer("1|a1|b1|DELETE ;; 1|a1u|b1|INSERT",
            "SELECT id, x, y, METADATA$ACTION FROM js ORDER BY METADATA$ACTION, x");
    }
}
