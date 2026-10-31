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

package dev.frostlake.procedures;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A CALL's named argument written as a predicate - NOT, AND, OR, a comparison, BETWEEN or IN - binds the value
 * it computes to the parameter it names (all live-verified).
 */
public class NamedCallPredicateTest extends MeasuredCellsTestSupport {

    @Test
    public void namedPredicates() {
        engine.execute("CREATE OR REPLACE PROCEDURE pb2066(x BOOLEAN) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
            + "x::VARCHAR; END'");
        engine.execute("CREATE OR REPLACE FUNCTION fb2066(x BOOLEAN) RETURNS BOOLEAN AS 'x'");
        assertAnswer("false", "CALL pb2066(x => NOT TRUE)");
        assertAnswer("false", "CALL pb2066(x => TRUE AND FALSE)");
        assertAnswer("true", "CALL pb2066(x => FALSE OR TRUE)");
        assertAnswer("false", "CALL pb2066(x => NOT TRUE AND TRUE)");
        assertAnswer("true", "CALL pb2066(x => 1 = 1)");
        assertAnswer("true", "CALL pb2066(x => 1 BETWEEN 0 AND 2)");
        assertAnswer("false", "CALL pb2066(x => (NOT TRUE))");
        assertAnswer("true", "CALL pb2066(x => 1 IN (1, 2))");
        assertAnswer("true", "SELECT fb2066(x => 1 = 1)");
        assertAnswer("1", "SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[]'), OUTER => 1 = 1))");
    }
}
