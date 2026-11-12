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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A schema CLONE at a past point takes the tables that stood in the schema then, as they stood: one dropped since and
 * the old version of one replaced since come from their dropped versions in Time Travel, as UNDROP would bring them
 * back. The clone was built from the schema's current tables alone, so the recovery a clone at a point is for left
 * out exactly the tables that changed.
 */
public class SchemaCloneAtDroppedTablesTest extends MeasuredCellsTestSupport {

    @AfterEach
    public void dropSchemas() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("DROP SCHEMA IF EXISTS test_db.sca_src");
        engine.execute("DROP SCHEMA IF EXISTS test_db.sca_cl");
    }

    @Test
    public void theCloneHoldsTheTablesThatStoodAtThePoint() {
        engine.execute("CREATE OR REPLACE SCHEMA test_db.sca_src");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE TABLE test_db.sca_src.a (id INT)");
        engine.execute("INSERT INTO test_db.sca_src.a VALUES (1)");
        engine.execute("CREATE TABLE test_db.sca_src.gone (id INT)");
        engine.execute("INSERT INTO test_db.sca_src.gone VALUES (5)");
        engine.execute("CREATE TABLE test_db.sca_src.kept (id INT)");
        engine.execute("INSERT INTO test_db.sca_src.kept VALUES (9)");
        engine.execute("SELECT SYSTEM$WAIT(1)");
        engine.execute("SET sca_ts = CURRENT_TIMESTAMP()::VARCHAR");
        engine.execute("SELECT SYSTEM$WAIT(1)");
        engine.execute("CREATE OR REPLACE TABLE test_db.sca_src.a (id INT, z INT)");
        engine.execute("DROP TABLE test_db.sca_src.gone");
        engine.execute("CREATE SCHEMA test_db.sca_cl CLONE test_db.sca_src AT(TIMESTAMP => $sca_ts::TIMESTAMP_LTZ)");
        engine.execute("USE SCHEMA test_db.test_schema");
        assertAnswer("5", "SELECT * FROM test_db.sca_cl.gone");
        assertAnswer("1", "SELECT * FROM test_db.sca_cl.a");
        assertAnswer("9", "SELECT * FROM test_db.sca_cl.kept");
        // The source is left as it is: the replaced table's new version, and no table GONE.
        assertAnswer("", "SELECT * FROM test_db.sca_src.a");
        assertAnswer("1", "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_schema = 'SCA_SRC' "
            + "AND table_name IN ('A', 'GONE')");
    }
}
