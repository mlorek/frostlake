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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RESULT_SCAN of an id that names no statement is refused in live's words, "Statement &lt;id&gt; not found",
 * whatever the id looks like: a well-formed one in either case, any other text, an empty one and one with
 * spaces around it, each echoed as given, and a LAST_QUERY_ID that answers NULL as the word NULL.
 */
public class ResultScanUnknownStatementTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return String.valueOf(refused.getMessage());
    }

    @Test
    public void anIdThatNamesNoStatementIsNotFound() {
        assertEquals("Statement 01b2c3d4-0000-0000-0000-000000000000 not found",
            refusal("SELECT * FROM TABLE(RESULT_SCAN('01b2c3d4-0000-0000-0000-000000000000'))"));
        assertEquals("Statement 01B2C3D4-0000-0000-0000-000000000000 not found",
            refusal("SELECT * FROM TABLE(RESULT_SCAN('01B2C3D4-0000-0000-0000-000000000000'))"));
        assertEquals("Statement 01b2c3d4-0000-0000-0000-000000000000 not found",
            refusal("SELECT $1 FROM TABLE(RESULT_SCAN('01b2c3d4-0000-0000-0000-000000000000')) WHERE 1 = 0"));
    }

    @Test
    public void anyOtherTextIsEchoedAsGiven() {
        assertEquals("Statement abc not found", refusal("SELECT * FROM TABLE(RESULT_SCAN('abc'))"));
        assertEquals("Statement LAST not found", refusal("SELECT * FROM TABLE(RESULT_SCAN('LAST'))"));
        assertEquals("Statement x not found", refusal("SELECT COUNT(*) FROM TABLE(RESULT_SCAN('x'))"));
        assertEquals("Statement 01b2c3d4-0000-0000-0000-00000000000g not found",
            refusal("SELECT * FROM TABLE(RESULT_SCAN('01b2c3d4-0000-0000-0000-00000000000g'))"));
        assertEquals("Statement  not found", refusal("SELECT * FROM TABLE(RESULT_SCAN(''))"));
        assertEquals("Statement   01b2c3d4-0000-0000-0000-000000000000  not found",
            refusal("SELECT * FROM TABLE(RESULT_SCAN('  01b2c3d4-0000-0000-0000-000000000000 '))"));
    }

    @Test
    public void aLastQueryIdThatAnswersNullIsStatementNull() {
        // The deepest look-back LAST_QUERY_ID takes; a session with a longer history names a statement there.
        Assumptions.assumeTrue(engine.executeQuery("SELECT LAST_QUERY_ID(-10000)").getRows().get(0).getValue(0) == null,
            "the session's history reaches 10000 statements back");
        assertEquals("Statement NULL not found",
            refusal("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-10000)))"));
    }
}
