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
 * A stream on a view follows its view: renamed, or moved to another schema, the view is still the stream's
 * source, read and listed under its new name, and a new view under the old name is not the stream's (all
 * live-verified).
 */
public class ViewStreamFollowsItsViewTest extends MeasuredCellsTestSupport {

    @Test
    public void renamedAndMovedView() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.vu (id INT) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.vw AS SELECT id FROM test_db.test_schema.vu");
        engine.execute("CREATE OR REPLACE STREAM test_db.test_schema.sv ON VIEW test_db.test_schema.vw");
        engine.execute("INSERT INTO test_db.test_schema.vu VALUES (1)");
        assertAnswer("Statement executed successfully.",
            "ALTER VIEW test_db.test_schema.vw RENAME TO test_db.test_schema.vw2");
        assertAnswer("1", "SELECT id FROM test_db.test_schema.sv");
        assertAnswer("TEST_DB.TEST_SCHEMA.VW2|View|TEST_DB.TEST_SCHEMA.VU|false",
            "SHOW STREAMS LIKE 'SV' IN SCHEMA test_db.test_schema ->> SELECT \"table_name\", \"source_type\", "
                + "\"base_tables\", \"stale\" FROM $1");
        assertAnswer("TEST_DB.TEST_SCHEMA.VW2",
            "DESCRIBE STREAM test_db.test_schema.sv ->> SELECT \"table_name\" FROM $1");
        assertAnswer("View VW successfully created.",
            "CREATE VIEW test_db.test_schema.vw AS SELECT id FROM test_db.test_schema.vu");
        assertAnswer("1", "SELECT id FROM test_db.test_schema.sv");
        assertAnswer("TEST_DB.TEST_SCHEMA.VW2|View|TEST_DB.TEST_SCHEMA.VU|false",
            "SHOW STREAMS LIKE 'SV' IN SCHEMA test_db.test_schema ->> SELECT \"table_name\", \"source_type\", "
                + "\"base_tables\", \"stale\" FROM $1");
        assertAnswer("Schema VO successfully created.", "CREATE OR REPLACE SCHEMA test_db.vo");
        answer("USE SCHEMA test_db.test_schema");
        assertAnswer("Statement executed successfully.", "ALTER VIEW test_db.test_schema.vw2 RENAME TO test_db.vo.vw3");
        assertAnswer("1", "SELECT id FROM test_db.test_schema.sv");
        assertAnswer("TEST_DB.VO.VW3|View|TEST_DB.TEST_SCHEMA.VU|false",
            "SHOW STREAMS LIKE 'SV' IN SCHEMA test_db.test_schema ->> SELECT \"table_name\", \"source_type\", "
                + "\"base_tables\", \"stale\" FROM $1");
        assertAnswer("1", "INSERT INTO test_db.test_schema.vu VALUES (2)");
        assertAnswer("1 ;; 2", "SELECT id FROM test_db.test_schema.sv ORDER BY id");
    }
}
