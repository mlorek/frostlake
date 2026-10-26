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
 * The row access and masking policies of a view below the stream's own apply to the stream's rows, to CHANGES over
 * the outer view and to a plain read of it: a view that carries both a row access policy and a masking policy
 * masks its columns too (all live-verified).
 */
public class NestedViewStreamPoliciesTest extends MeasuredCellsTestSupport {

    @Test
    public void innerViewPolicies() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.bt (id INT, secret STRING) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.bv AS SELECT id, secret FROM "
            + "test_db.test_schema.bt");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.bvv AS SELECT * FROM test_db.test_schema.bv");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY test_db.test_schema.brap771 AS (i INT) RETURNS BOOLEAN "
            + "-> i < 5");
        engine.execute("CREATE OR REPLACE MASKING POLICY test_db.test_schema.bmp771 AS (s STRING) RETURNS STRING -> "
            + "'***'");
        engine.execute("ALTER VIEW test_db.test_schema.bv ADD ROW ACCESS POLICY test_db.test_schema.brap771 ON (id)");
        engine.execute("ALTER VIEW test_db.test_schema.bv MODIFY COLUMN secret SET MASKING POLICY "
            + "test_db.test_schema.bmp771");
        assertAnswer("Stream BS1 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.bs1 ON VIEW test_db.test_schema.bvv");
        assertAnswer("2", "INSERT INTO test_db.test_schema.bt VALUES (1, 'a'), (7, 'b')");
        assertAnswer("1|***|INSERT", "SELECT id, secret, METADATA$ACTION FROM test_db.test_schema.bs1 ORDER BY id");
        assertAnswer("1|***",
            "SELECT id, secret FROM test_db.test_schema.bvv CHANGES(INFORMATION => DEFAULT) AT(STREAM => "
                + "'test_db.test_schema.bs1') ORDER BY id");
        assertAnswer("1|***", "SELECT id, secret FROM test_db.test_schema.bvv ORDER BY id");
    }
}
