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

import org.junit.jupiter.api.Test;

/**
 * A routine reference GET_DDL and TAG_REFERENCES take, f(TIMESTAMP), reads a bare TIMESTAMP as the flavour the
 * session's TIMESTAMP_TYPE_MAPPING names, as DESCRIBE and DROP read it: under TIMESTAMP_LTZ it names the function
 * declared f(x TIMESTAMP) there, whose DDL spells its parameter TIMESTAMP_LTZ(9) (all live-verified).
 */
public class RoutineReferenceTimestampMappingTest extends MeasuredCellsTestSupport {

    @Test
    public void bareTimestampFollowsTheMapping() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
        assertAnswer("Function F41 successfully created.",
            "CREATE OR REPLACE FUNCTION f41(x TIMESTAMP) RETURNS INT AS '1'");
        assertAnswer("signature|(X TIMESTAMP_LTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESC FUNCTION f41(TIMESTAMP)");
        assertAnswer("CREATE OR REPLACE FUNCTION \"F41\"(\"X\" TIMESTAMP_LTZ(9)) | RETURNS NUMBER(38,0) | LANGUAGE "
                + "SQL | AS '1';",
            "SELECT GET_DDL('function', 'f41(TIMESTAMP)')");
        assertAnswer("0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('f41(TIMESTAMP)', 'function'))");
        assertAnswer("CREATE OR REPLACE FUNCTION \"F41\"(\"X\" TIMESTAMP_LTZ(9)) | RETURNS NUMBER(38,0) | LANGUAGE "
                + "SQL | AS '1';",
            "SELECT GET_DDL('function', 'f41(TIMESTAMP_LTZ)')");
        assertAnswer("F41 successfully dropped.", "DROP FUNCTION f41(TIMESTAMP)");
        assertAnswer("Statement executed successfully.", "ALTER SESSION UNSET TIMESTAMP_TYPE_MAPPING");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
