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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A value operator written after a quantified comparison is "Invalid query block: &lt;operator&gt;.", refused
 * while the statement compiles and before any name in it is resolved. A comparison-level operator after it reads
 * as written, and a parenthesized quantified comparison is an ordinary BOOLEAN. Live-verified.
 */
public class QuantifiedComparisonOperandTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        return rs.getRows().get(0).getValue(0);
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    private static String block(final String operator) {
        return "SQL compilation error:\nInvalid query block: " + operator + ".";
    }

    @Test
    public void aValueOperatorAfterTheComparisonIsRefused() {
        assertEquals(block("||"), refusal("SELECT 1 = ANY (SELECT 1) || '' AS r"));
        assertEquals(block("::"), refusal("SELECT 1 = ANY (SELECT 1) ::VARCHAR AS r"));
        assertEquals(block("::"), refusal("SELECT 1 = ANY (SELECT 1)::VARCHAR AS r"));
        assertEquals(block("+"), refusal("SELECT 1 = ANY (SELECT 1) + 1 AS r"));
        assertEquals(block("-"), refusal("SELECT 1 = ANY (SELECT 1) - 1 AS r"));
        assertEquals(block("*"), refusal("SELECT 1 = ANY (SELECT 1) * 1 AS r"));
        assertEquals(block("/"), refusal("SELECT 1 = ANY (SELECT 1) / 1 AS r"));
        assertEquals(block("%"), refusal("SELECT 1 = ANY (SELECT 1) % 1 AS r"));
        assertEquals(block("COLLATE"), refusal("SELECT 1 = ANY (SELECT 1) COLLATE 'de' AS r"));
        assertEquals(block("IS"), refusal("SELECT 1 = ANY (SELECT 1) IS NULL AS r"));
        assertEquals(block("IS"), refusal("SELECT 1 = ANY (SELECT 1) IS NOT NULL AS r"));
        assertEquals(block("IS"), refusal("SELECT 1 = ANY (SELECT 1) IS DISTINCT FROM TRUE AS r"));
        assertEquals(block(":"), refusal("SELECT 1 = ANY (SELECT 1) :x AS r"));
        assertEquals(block("["), refusal("SELECT 1 = ANY (SELECT 1) [0] AS r"));
        assertEquals(block("+"), refusal("SELECT 1 = ANY (SELECT 1) (+) AS r"));
        assertEquals(block("||"), refusal("SELECT 1 < ALL (SELECT 2) || '' AS r"));
        assertEquals(block("||"), refusal("SELECT 1 = SOME (SELECT 1) || '' AS r"));
        assertEquals(block("||"), refusal("SELECT 1 = ANY (SELECT 1) || '' || '' AS r"));
    }

    @Test
    public void everyClauseRefusesItBeforeANameIsResolved() {
        assertEquals(block("||"), refusal("SELECT 1 FROM (SELECT 1 AS c) WHERE 1 = ANY (SELECT 1) || '' = 'true'"));
        assertEquals(block("IS"), refusal("SELECT 1 FROM (SELECT 1 AS c) WHERE 1 = ANY (SELECT 1) IS NULL"));
        assertEquals(block("||"), refusal("SELECT 1 FROM (SELECT 1 AS c) ORDER BY 1 = ANY (SELECT 1) || ''"));
        assertEquals(block("||"), refusal("SELECT CASE WHEN 1 = ANY (SELECT 1) || '' THEN 1 END AS r"));
        assertEquals(block("IS"), refusal("SELECT IFF(1 = ANY (SELECT 1) IS NULL, 1, 2) AS r"));
        assertEquals(block("||"), refusal("SELECT 1 = ANY (SELECT 1) || '' AS r FROM nosuch_quantified_t"));
        assertEquals(block("||"), refusal("SELECT 1 = ANY (SELECT nosuch_col) || '' AS r"));
    }

    @Test
    public void aComparisonLevelOperatorOrParenthesesReadAsWritten() {
        assertEquals(Boolean.TRUE, scalar("SELECT 1 = ANY (SELECT 1) = TRUE AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 1 = ANY (SELECT 1) IN (TRUE) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 1 = ANY (SELECT 1) BETWEEN FALSE AND TRUE AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT TRUE = 1 = ANY (SELECT 1) AS r"));
        assertEquals("true", scalar("SELECT (1 = ANY (SELECT 1))::VARCHAR AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 1 = ANY (SELECT 1) OR TRUE || '' AS r"));
        assertEquals("SQL compilation error: error line 1 at position 28\nInvalid argument types for function '||': "
            + "(BOOLEAN, VARCHAR(1))", refusal("SELECT (1 = ANY (SELECT 1)) || '' AS r"));
    }
}
