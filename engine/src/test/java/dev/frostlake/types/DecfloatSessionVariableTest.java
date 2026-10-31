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

package dev.frostlake.types;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A session variable set to a DECFLOAT keeps the type: SYSTEM$TYPEOF reads DECFLOAT(38)[DECFLOAT18], its text is
 * the DECFLOAT's, and SHOW VARIABLES lists it as decfloat (all live-verified).
 */
public class DecfloatSessionVariableTest extends MeasuredCellsTestSupport {

    @Test
    public void sessionVariables() {
        assertAnswer("Statement executed successfully.", "SET v = 1.5::DECFLOAT");
        assertAnswer("1.5|DECFLOAT(38)[DECFLOAT18]", "SELECT $v::VARCHAR, SYSTEM$TYPEOF($v)");
        assertAnswer("Statement executed successfully.", "SET w = (SELECT 1::DECFLOAT / 3)");
        assertAnswer("3.3333333333333333333333333333333333333e-1|DECFLOAT(38)[DECFLOAT18]",
            "SELECT $w::VARCHAR, SYSTEM$TYPEOF($w)");
        assertAnswer("Statement executed successfully.", "SET z = 12::DECFLOAT");
        assertAnswer("12|DECFLOAT(38)[DECFLOAT18]|13|DECFLOAT(38)[DECFLOAT18]",
            "SELECT $z::VARCHAR, SYSTEM$TYPEOF($z), $z + 1, SYSTEM$TYPEOF($z + 1)");
        answer("SHOW VARIABLES LIKE 'Z'");
        assertAnswer("Z|decfloat", "SELECT \"name\", \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("Statement executed successfully.", "SET (p, q) = (1.25::DECFLOAT, 2)");
        assertAnswer("DECFLOAT(38)[DECFLOAT18]|NUMBER(1,0)[SB1]|1.25",
            "SELECT SYSTEM$TYPEOF($p), SYSTEM$TYPEOF($q), $p::VARCHAR");
        assertAnswer("Statement executed successfully.", "SET u = TO_DECFLOAT('1e100')");
        assertAnswer("1e100|DECFLOAT(38)[DECFLOAT18]", "SELECT $u::VARCHAR, SYSTEM$TYPEOF($u)");
        assertAnswer("Statement executed successfully.", "SET n = 1.5");
        assertAnswer("NUMBER(2,1)[SB1]", "SELECT SYSTEM$TYPEOF($n)");
        assertAnswer("Statement executed successfully.", "SET v = 2.5");
        assertAnswer("NUMBER(2,1)[SB1]|2.5", "SELECT SYSTEM$TYPEOF($v), $v::VARCHAR");
    }
}
