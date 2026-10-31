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
 * A stream on a view over a view reads the inner view as it stands: replaced, its new query; dropped, the stream's
 * read fails the view's expansion naming the view in full and the missing object, where CREATE STREAM and CHANGES
 * name the view bare. A view whose table is gone fails the same way (all live-verified).
 */
public class NestedViewExpansionFailureTest extends MeasuredCellsTestSupport {

    @Test
    public void replacedThenDroppedInnerView() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.ct (id INT, secret STRING) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.cv AS SELECT id, secret FROM "
            + "test_db.test_schema.ct");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.cvv AS SELECT * FROM test_db.test_schema.cv");
        assertAnswer("Stream CS1 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.cs1 ON VIEW test_db.test_schema.cvv");
        assertAnswer("1", "INSERT INTO test_db.test_schema.ct VALUES (1, 'a')");
        assertAnswer("View CV successfully created.",
            "CREATE OR REPLACE VIEW test_db.test_schema.cv AS SELECT id, UPPER(secret) secret FROM "
                + "test_db.test_schema.ct");
        assertAnswer("1", "INSERT INTO test_db.test_schema.ct VALUES (2, 'b')");
        assertAnswer("1|A|INSERT ;; 2|B|INSERT",
            "SELECT id, secret, METADATA$ACTION FROM test_db.test_schema.cs1 ORDER BY id");
        assertAnswer("false|TEST_DB.TEST_SCHEMA.CT",
            "SHOW STREAMS LIKE 'CS1' IN SCHEMA test_db.test_schema ->> SELECT \"stale\", \"base_tables\" FROM $1");
        assertAnswer("CV successfully dropped.", "DROP VIEW test_db.test_schema.cv");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'TEST_DB.TEST_SCHEMA.CVV': "
                + "SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.CV' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.CV.",
            "SELECT id FROM test_db.test_schema.cs1");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'CVV': SQL compilation error: "
                + "| Object 'TEST_DB.TEST_SCHEMA.CV' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.CV.",
            "CREATE OR REPLACE STREAM test_db.test_schema.cs2 ON VIEW test_db.test_schema.cvv");
    }

    @Test
    public void goneTableOrInnerView() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.ht (id INT) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.hv AS SELECT id FROM test_db.test_schema.ht");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.hvv AS SELECT id FROM test_db.test_schema.hv");
        engine.execute("ALTER VIEW test_db.test_schema.hv SET CHANGE_TRACKING = TRUE");
        engine.execute("ALTER VIEW test_db.test_schema.hvv SET CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.kt (id INT) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.kv AS SELECT id FROM test_db.test_schema.kt");
        engine.execute("ALTER VIEW test_db.test_schema.kv SET CHANGE_TRACKING = TRUE");
        engine.execute("SET ts772 = CURRENT_TIMESTAMP()");
        assertAnswer("1", "INSERT INTO test_db.test_schema.ht VALUES (1)");
        assertAnswer("1", "INSERT INTO test_db.test_schema.kt VALUES (1)");
        assertAnswer("HV successfully dropped.", "DROP VIEW test_db.test_schema.hv");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'HVV': SQL compilation error: "
                + "| Object 'TEST_DB.TEST_SCHEMA.HV' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.HV.",
            "SELECT id FROM test_db.test_schema.hvv CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP => $ts772)");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'HVV': SQL compilation error: "
                + "| Object 'TEST_DB.TEST_SCHEMA.HV' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.HV.",
            "CREATE OR REPLACE STREAM test_db.test_schema.hs1 ON VIEW test_db.test_schema.hvv");
        assertAnswer("KT successfully dropped.", "DROP TABLE test_db.test_schema.kt");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'KV': SQL compilation error: |"
                + " Object 'TEST_DB.TEST_SCHEMA.KT' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.KT.",
            "SELECT id FROM test_db.test_schema.kv CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP => $ts772)");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'KV': SQL compilation error: |"
                + " Object 'TEST_DB.TEST_SCHEMA.KT' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.KT.",
            "CREATE OR REPLACE STREAM test_db.test_schema.ks1 ON VIEW test_db.test_schema.kv");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'KV': SQL compilation error: |"
                + " Object 'TEST_DB.TEST_SCHEMA.KT' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.KT.",
            "SELECT id FROM test_db.test_schema.kv");
    }
}
