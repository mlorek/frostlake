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
 * The syntax-error lines of a packages policy written out of its order, LANGUAGE first, and of DESCRIBE DATASET's
 * words after its name, and a session policy's agent scope given as a dotted name, read as written (live-verified):
 * a fully qualified name is looked up and refused as a scope that does not exist, any other dotted name as no
 * valid scope value.
 */
public class PolicyClauseOrderLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aPolicysClausesOutOfOrderAndADottedAgentScope() {
        engine.execute("CREATE OR REPLACE SESSION POLICY sp906");
        assertCells(new String[][] {
            {"CREATE OR REPLACE PACKAGES POLICY pk906 COMMENT = 'c' LANGUAGE PYTHON",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'COMMENT'. | "
                + "syntax error line 1 at position 63 unexpected 'PYTHON'."},
            {"CREATE OR REPLACE PACKAGES POLICY pq906 LANGUAGE PYTHON, ALLOWLIST = ('a')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected ','."},
            {"DESCRIBE DATASET nosuch_ds x 'y' z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''y''. | syntax "
                + "error line 1 at position 34 unexpected '<EOF>'."},
            {"ALTER SESSION POLICY sp906 SET AGENT_RESTRICTED_SESSION_SCOPE = test_db.test_schema.nosuch_scope",
                "REFUSED SQL compilation error: | Restricted Session Scope 'test_db.test_schema.nosuch_scope' does "
                + "not exist or not authorized."},
            {"CREATE OR REPLACE PACKAGES POLICY pk906 COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'COMMENT'."},
            {"CREATE OR REPLACE PACKAGES POLICY pk906 ALLOWLIST = ('a') LANGUAGE PYTHON",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'ALLOWLIST'. | "
                + "syntax error line 1 at position 50 unexpected '='."},
            {"CREATE OR REPLACE PACKAGES POLICY pq906 LANGUAGE PYTHON, COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected ','."},
            {"CREATE OR REPLACE PACKAGES POLICY pq906 LANGUAGE PYTHON ALLOWLIST = ('a'), BLOCKLIST = ('b')",
                "Packages policy 'PQ906' created successfully"},
            {"DESCRIBE DATASET nosuch_ds 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ''y''."},
            {"DESCRIBE DATASET nosuch_ds x 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''y''."},
            {"DESCRIBE DATASET nosuch_ds 'x' 'y' z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ''x''."},
            {"ALTER SESSION POLICY sp906 SET AGENT_RESTRICTED_SESSION_SCOPE = test_schema.nosuch_scope",
                "REFUSED SQL compilation error: 'test_schema.nosuch_scope' is not a valid RESTRICTED_SESSION_SCOPE "
                + "value. Expected inline YAML, a fully-qualified name (db.schema.name), or one of the predefined "
                + "names: SNOWFLAKE$DATA_READ, SNOWFLAKE$DATA_READ_PROGRAM_USAGE, SNOWFLAKE$DATA_READ_WITH_AI."},
            {"ALTER SESSION POLICY sp906 SET AGENT_RESTRICTED_SESSION_SCOPE = a.b.c.d",
                "REFUSED SQL compilation error: 'a.b.c.d' is not a valid RESTRICTED_SESSION_SCOPE value. Expected "
                + "inline YAML, a fully-qualified name (db.schema.name), or one of the predefined names: "
                + "SNOWFLAKE$DATA_READ, SNOWFLAKE$DATA_READ_PROGRAM_USAGE, SNOWFLAKE$DATA_READ_WITH_AI."},
            {"CREATE OR REPLACE SESSION POLICY sp906b AGENT_RESTRICTED_SESSION_SCOPE = "
                + "test_db.test_schema.nosuch_scope",
                "REFUSED SQL compilation error: | Restricted Session Scope 'test_db.test_schema.nosuch_scope' does "
                + "not exist or not authorized."},
        });
    }
}
