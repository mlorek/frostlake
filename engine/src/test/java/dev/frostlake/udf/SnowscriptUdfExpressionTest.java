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
package dev.frostlake.udf;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The expressions a Snowscript UDF — a SQL function whose body is a block — may not hold, refused at CREATE
 * (live-verified): a call of anything the account does not build in, a user-defined function and a name nothing
 * declares alike; a call written with OVER; and a subquery. The refusal names the whole expression the statement
 * holds, re-printed: a name upper-cased, a call that is an operand in its own parentheses. Built-ins, aggregates
 * and a built-in called with a wrong count are created.
 */
public class SnowscriptUdfExpressionTest extends BaseDatabaseTest {

    private static final String UNSUPPORTED = "Unsupported expression for Snowscript UDF: ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE FUNCTION sue_f2() RETURNS INT AS $$ 1 $$");
    }

    private static String udf(final String body) {
        return "CREATE OR REPLACE FUNCTION sue(a INT) RETURNS INT AS $$ BEGIN " + body + " END $$";
    }

    private String refusal(final String body) {
        final String sql = udf(body);
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void aCallOfANameNothingDeclaresIsRefused() {
        assertEquals(UNSUPPORTED + "NOSUCH(1)", refusal("RETURN nosuch(1);"));
        assertEquals(UNSUPPORTED + "NOSUCH()", refusal("RETURN nosuch();"));
        assertEquals(UNSUPPORTED + "NOSUCH(1)", refusal("RETURN (nosuch(1));"));
        assertEquals(UNSUPPORTED + "NOSUCH(A)", refusal("RETURN nosuch(a);"));
        assertEquals(UNSUPPORTED + "NOSUCH(X)", refusal("LET x INT := 1; RETURN nosuch(x);"));
        assertEquals(UNSUPPORTED + "NOSUCH(1.5, 'a''b', null, TRUE)", refusal("RETURN nosuch(1.50, 'a''b', NULL, TRUE);"));
        assertEquals(UNSUPPORTED + "NOSUCH(A + 1, A * 2)", refusal("RETURN nosuch(a + 1, a * 2);"));
        assertEquals(UNSUPPORTED + "NOSUCH(NOSUCH2(1))", refusal("RETURN nosuch(nosuch2(1));"));
        assertEquals(UNSUPPORTED + "TEST_SCHEMA.NOSUCH(1)", refusal("RETURN test_schema.nosuch(1);"));
        assertEquals(UNSUPPORTED + "NOSUCH(1, 2)", refusal("RETURN nosuch(\n    1,\n    2);"));
    }

    @Test
    public void theWholeExpressionOfTheStatementIsNamed() {
        assertEquals(UNSUPPORTED + "(NOSUCH(1)) + 1", refusal("RETURN nosuch(1) + 1;"));
        assertEquals(UNSUPPORTED + "1 + (NOSUCH(1))", refusal("RETURN 1 + nosuch(1);"));
        assertEquals(UNSUPPORTED + "(NOSUCH(1)) || 'x'", refusal("RETURN nosuch(1) || 'x';"));
        assertEquals(UNSUPPORTED + "(NOSUCH(1)) > 2", refusal("RETURN nosuch(1) > 2;"));
        assertEquals(UNSUPPORTED + "NEGATE(NOSUCH(1))", refusal("RETURN -nosuch(1);"));
        assertEquals(UNSUPPORTED + "ABS((NOSUCH(1)) + 1)", refusal("RETURN ABS(nosuch(1) + 1);"));
        assertEquals(UNSUPPORTED + "(ABS(1)) + (NOSUCH(1))", refusal("RETURN ABS(1) + nosuch(1);"));
        assertEquals(UNSUPPORTED + "TO_NUMBER(NOSUCH(A))", refusal("RETURN TO_NUMBER(nosuch(a));"));
        assertEquals(UNSUPPORTED + "NOSUCH(A) IS NULL", refusal("RETURN nosuch(a) IS NULL;"));
        assertEquals(UNSUPPORTED + "NOT(NOSUCH(A))", refusal("RETURN NOT nosuch(a);"));
        assertEquals(UNSUPPORTED + "(NOSUCH(A)) AND TRUE", refusal("RETURN nosuch(a) AND TRUE;"));
        assertEquals(UNSUPPORTED + "(NOSUCH(A)) IN (1, 2)", refusal("RETURN nosuch(a) IN (1, 2);"));
        assertEquals(UNSUPPORTED + "(NOSUCH(A)) LIKE 'x%'", refusal("RETURN nosuch(a) LIKE 'x%';"));
        assertEquals(UNSUPPORTED + "NOSUCH(CAST(A AS VARCHAR(134217728)))", refusal("RETURN nosuch(a::VARCHAR);"));
    }

    @Test
    public void everyStatementOfTheBlockIsJudgedTheFirstFaultWinning() {
        assertEquals(UNSUPPORTED + "(NOSUCH(1)) > 0", refusal("IF (nosuch(1) > 0) THEN RETURN 1; END IF; RETURN 2;"));
        assertEquals(UNSUPPORTED + "NOSUCH1(1)", refusal("LET x INT := nosuch1(1); RETURN nosuch2(2);"));
        assertEquals(UNSUPPORTED + "NOSUCH(1)", refusal("a := nosuch(1); RETURN a;"));
        assertEquals(UNSUPPORTED + "(NOSUCH(A)) < 3", refusal("WHILE (nosuch(a) < 3) DO a := a + 1; END WHILE; RETURN a;"));
        assertEquals(UNSUPPORTED + "NOSUCH(3)", refusal("FOR i IN 1 TO nosuch(3) DO a := a + 1; END FOR; RETURN a;"));
        assertEquals(UNSUPPORTED + "(NOSUCH(A)) = 1",
            refusal("CASE WHEN nosuch(a) = 1 THEN RETURN 1; ELSE RETURN 2; END CASE;"));
    }

    @Test
    public void aUserDefinedFunctionIsRefusedAsAnUnknownOneIs() {
        assertEquals(UNSUPPORTED + "SUE_F2()", refusal("RETURN sue_f2();"));
        assertEquals(UNSUPPORTED + "SUE_F2(1)", refusal("RETURN sue_f2(1);"));
        assertEquals(UNSUPPORTED + "SUE_F2() + 1", refusal("RETURN sue_f2() + 1;"));
        assertEquals(UNSUPPORTED + "ABS(SUE_F2())", refusal("RETURN ABS(sue_f2());"));
        assertEquals(UNSUPPORTED + "A + (SUE_F2() * 2)", refusal("RETURN a + sue_f2() * 2;"));
        assertEquals(UNSUPPORTED + "SUE_F2()", refusal("LET x INT := sue_f2(); RETURN x;"));
        assertEquals(UNSUPPORTED + "TEST_SCHEMA.SUE_F2()", refusal("RETURN test_schema.sue_f2();"));
        assertEquals(UNSUPPORTED + "SUE(1)", refusal("RETURN sue(1);"));
    }

    @Test
    public void aWindowCallAndASubqueryAreRefused() {
        assertEquals(UNSUPPORTED + "ROW_NUMBER() OVER (ORDER BY 1 ASC NULLS LAST)",
            refusal("RETURN ROW_NUMBER() OVER (ORDER BY 1);"));
        assertEquals(UNSUPPORTED + "ROW_NUMBER() OVER (PARTITION BY A ORDER BY A DESC NULLS FIRST)",
            refusal("RETURN ROW_NUMBER() OVER (PARTITION BY a ORDER BY a DESC);"));
        assertEquals(UNSUPPORTED + "SUM(A) OVER (PARTITION BY 1)", refusal("RETURN SUM(a) OVER (PARTITION BY 1);"));
        assertEquals(UNSUPPORTED + "LAG(A) OVER (ORDER BY A ASC NULLS LAST)", refusal("RETURN LAG(a) OVER (ORDER BY a);"));
        assertEquals(UNSUPPORTED + "(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)", refusal("RETURN (SELECT 1);"));
        assertEquals(UNSUPPORTED + "EXISTS(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)",
            refusal("RETURN EXISTS (SELECT 1);"));
    }

    @Test
    public void builtInsAndAggregatesAreCreated() {
        engine.execute(udf("RETURN ABS(a) + LENGTH(UPPER('x'));"));
        assertEquals("4", String.valueOf(engine.executeQuery("SELECT sue(-3)").getRows().get(0).getValue(0)));
        engine.execute(udf("RETURN SUM(a);"));
        engine.execute(udf("RETURN ABS(1, 2);"));
        engine.execute(udf("RETURN SYSTEM$TYPEOF(a);"));
        engine.execute(udf("RETURN RANDOM();"));
        engine.execute(udf("RETURN nosuchcol;"));
    }
}
