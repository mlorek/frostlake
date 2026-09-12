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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RESULT_SCAN of a statement that failed. The statement has a query ID, which LAST_QUERY_ID names, but it
 * has no result, and scanning it is refused with "Query &lt;id&gt; has no result because it failed" — after
 * a compile-time refusal, a runtime error, a syntax error and a failed INSERT alike, and after a refused
 * RESULT_SCAN in its turn.
 */
public class ResultScanFailedQueryTest extends BaseDatabaseTest {

    private static final String NO_RESULT = " has no result because it failed";

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return String.valueOf(refused.getMessage());
    }

    /** Scanning the last statement is refused, naming that statement's own ID; returns the message. */
    private String assertLastHasNoResult(final String scan) {
        final String message = refusal(scan);
        assertTrue(message.matches("(?s).*Query [0-9a-fA-F-]+" + NO_RESULT + ".*"), message);
        return message;
    }

    @Test
    public void aCompileTimeRefusalHasNoResult() {
        refusal("SELECT * FROM NO_SUCH_TABLE");
        assertLastHasNoResult("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    @Test
    public void aRuntimeErrorHasNoResult() {
        refusal("SELECT 1/0");
        assertLastHasNoResult("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    @Test
    public void aSyntaxErrorHasNoResult() {
        refusal("SELEC 1");
        assertLastHasNoResult("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    @Test
    public void aFailedInsertHasNoResult() {
        engine.execute("CREATE OR REPLACE TABLE rs_failed (x INT)");
        refusal("INSERT INTO rs_failed VALUES ('abc')");
        assertLastHasNoResult("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    /** The refusal names the ID LAST_QUERY_ID reported for the failed statement; a refused scan has none either. */
    @Test
    public void theRefusalNamesTheFailedStatement() {
        refusal("SELECT * FROM NO_SUCH_TABLE");
        final String failedId = String.valueOf(
            engine.executeQuery("SELECT LAST_QUERY_ID()").getRows().get(0).getValue(0));
        final String message = refusal("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-2)))");
        assertTrue(message.contains("Query " + failedId + NO_RESULT), message);
        final String again = assertLastHasNoResult("SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertFalse(again.contains(failedId), again);
    }

    @Test
    public void aSuccessfulStatementStillScans() {
        engine.execute("SELECT 42 AS a");
        assertEquals("42", String.valueOf(engine.executeQuery(
            "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))").getRows().get(0).getValue(0)));
    }
}
