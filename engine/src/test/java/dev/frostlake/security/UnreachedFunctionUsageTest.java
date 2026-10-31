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

package dev.frostlake.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A user's function the role may not use is refused as the statement compiles, whether or not a row reaches the
 * call: over no rows, in a CASE or IFF branch no row takes, as an unread item of a derived table or a CTE, in a WHERE
 * no row passes, in an UPDATE's SET and a MERGE's ON, SET and VALUES over no rows, in a subquery no row reaches, and
 * in the ORDER BY, GROUP BY, HAVING and QUALIFY keys and a table function's arguments, each in the sentence the call
 * itself is refused with. Engine only: GRANT OWNERSHIP and USE ROLE are not run on the live account.
 */
public class UnreachedFunctionUsageTest extends BaseDatabaseTest {

    private static final String REFUSAL = "SQL access control error:\nInsufficient privileges to operate on function"
        + " 'SECRET726'. Your primary role ACCOUNTADMIN must have USAGE granted on FUNCTION"
        + " TEST_DB.TEST_SCHEMA.SECRET726(NUMBER).";

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "GRANT OWNERSHIP and USE ROLE are not run on the live account");
        engine.execute("USE ROLE ACCOUNTADMIN");
        // Privileges come from the primary role alone, and the refusals name only it.
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("CREATE ROLE IF NOT EXISTS r726");
        engine.execute("CREATE OR REPLACE TABLE t726 (a INT)");
        engine.execute("CREATE OR REPLACE TABLE e726 (a INT)");
        engine.execute("INSERT INTO t726 VALUES (1)");
        engine.execute("CREATE OR REPLACE FUNCTION secret726(x INT) RETURNS INT AS 'x + 1'");
        engine.execute("GRANT OWNERSHIP ON FUNCTION secret726(INT) TO ROLE r726 COPY CURRENT GRANTS");
    }

    @Override
    protected void teardownTest() {
        if (isLiveSnowflake()) {
            return;
        }
        quietly("USE ROLE ACCOUNTADMIN");
        quietly("DROP ROLE IF EXISTS r726");
    }

    private void quietly(final String sql) {
        try {
            engine.execute(sql);
        } catch (final RuntimeException ignored) {
            // cleanup only
        }
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void aCallNoRowReachesIsRefusedAsTheCallIs() {
        assertEquals(REFUSAL, refusal("SELECT secret726(1)"));
        assertEquals(REFUSAL, refusal("SELECT secret726(a) FROM t726 WHERE 1 = 0"));
        assertEquals(REFUSAL, refusal("SELECT secret726(a) FROM e726"));
        assertEquals(REFUSAL, refusal("SELECT CASE WHEN a > 5 THEN secret726(a) END FROM t726"));
        assertEquals(REFUSAL, refusal("SELECT IFF(FALSE, secret726(1), 0)"));
        assertEquals(REFUSAL, refusal("WITH c AS (SELECT a, secret726(a) AS x FROM t726) SELECT a FROM c"));
        assertEquals(REFUSAL, refusal("SELECT a FROM (SELECT a, secret726(a) AS x FROM t726)"));
        assertEquals(REFUSAL, refusal("SELECT a FROM t726 WHERE secret726(a) > 0 AND 1 = 0"));
        assertEquals(REFUSAL, refusal("UPDATE t726 SET a = secret726(a) WHERE 1 = 0"));
    }

    @Test
    public void aCallInAMergeOrASubqueryNoRowReachesIsRefused() {
        assertEquals(REFUSAL, refusal("MERGE INTO t726 USING e726 s ON secret726(s.a) = t726.a "
            + "WHEN MATCHED THEN UPDATE SET a = 1"));
        assertEquals(REFUSAL, refusal("MERGE INTO t726 USING e726 s ON s.a = t726.a "
            + "WHEN MATCHED THEN UPDATE SET a = secret726(s.a)"));
        assertEquals(REFUSAL, refusal("MERGE INTO t726 USING e726 s ON s.a = t726.a "
            + "WHEN NOT MATCHED THEN INSERT (a) VALUES (secret726(s.a))"));
        assertEquals(REFUSAL, refusal("SELECT COUNT(*) FROM e726 WHERE a IN (SELECT secret726(a) FROM e726)"));
        assertEquals(REFUSAL, refusal("SELECT (SELECT MAX(secret726(a)) FROM e726)"));
        assertEquals(REFUSAL, refusal("SELECT a FROM t726 WHERE EXISTS (SELECT secret726(a) FROM e726)"));
        assertEquals(REFUSAL, refusal("SELECT a FROM e726 ORDER BY secret726(a)"));
        assertEquals(REFUSAL, refusal("SELECT COUNT(*) FROM e726 GROUP BY secret726(a)"));
        assertEquals(REFUSAL, refusal("SELECT a FROM e726 GROUP BY a HAVING MAX(secret726(a)) > 0"));
        assertEquals(REFUSAL, refusal("SELECT a FROM e726 QUALIFY secret726(a) > 0"));
        assertEquals(REFUSAL,
            refusal("SELECT * FROM e726, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(secret726(e726.a))))"));
    }

    @Test
    public void aRoleThatMayUseTheFunctionCallsIt() {
        engine.execute("GRANT USAGE ON FUNCTION secret726(INT) TO ROLE ACCOUNTADMIN");
        engine.execute("MERGE INTO t726 USING e726 s ON s.a = t726.a WHEN MATCHED THEN UPDATE SET a = secret726(s.a)");
        assertEquals(0L, ((Number) engine.executeQuery("SELECT COUNT(*) FROM e726 WHERE a IN "
            + "(SELECT secret726(a) FROM e726)").getRows().get(0).getValue(0)).longValue());
        assertTrue(engine.executeQuery("SELECT secret726(a) FROM e726").getRows().isEmpty());
        assertEquals(2L, ((Number) engine.executeQuery("SELECT secret726(a) FROM t726").getRows().get(0).getValue(0))
            .longValue());
    }
}
