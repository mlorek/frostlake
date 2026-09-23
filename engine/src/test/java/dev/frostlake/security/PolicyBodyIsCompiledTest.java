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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A policy body IS a scalar SQL UDF body, and the account compiles it when the policy is given one —
 * at CREATE and at {@code ALTER … SET BODY} alike. Frostlake stored the text, so a policy whose body
 * could never work was created happily and only failed later, when a query touched the column it
 * masked.
 *
 * <p>Two refusals, both in the UDF's own words: a body of the wrong family against the declared
 * RETURNS, and a name the signature does not declare — positioned in the BODY's own frame, which is
 * why every one of these reads position 1.
 */
public class PolicyBodyIsCompiledTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp AS (val STRING) RETURNS STRING -> '***'");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rap AS (x INT) RETURNS BOOLEAN -> x > 1");
    }

    /** The refusal a statement raises, or "OK". */
    private String outcome(final String sql) {
        try {
            engine.execute(sql);
            return "OK";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** A CREATE whose body is of the wrong family is refused, for either policy kind. */
    @Test
    public void aCreateBodyOfTheWrongTypeIsRefused() {
        assertEquals("Declared return type 'VARCHAR(134217728)' is incompatible with actual return"
            + " type 'NUMBER(1,0)'",
            outcome("CREATE OR REPLACE MASKING POLICY mq AS (val STRING) RETURNS STRING -> 1"));
        assertEquals("Declared return type 'BOOLEAN' is incompatible with actual return type"
            + " 'NUMBER(1,0)'",
            outcome("CREATE OR REPLACE ROW ACCESS POLICY rq AS (x INT) RETURNS BOOLEAN -> 1"));
    }

    /** A CREATE whose body names something the signature does not declare is refused. */
    @Test
    public void aCreateBodyNamingNothingIsRefused() {
        final String refusal =
            "SQL compilation error: error line 1 at position 1 invalid identifier 'NOSUCH'";
        assertEquals(refusal,
            outcome("CREATE OR REPLACE MASKING POLICY mr AS (val STRING) RETURNS STRING -> nosuch"));
        assertEquals(refusal,
            outcome("CREATE OR REPLACE ROW ACCESS POLICY rr AS (x INT) RETURNS BOOLEAN -> nosuch"));
    }

    /** SET BODY is compiled exactly as the CREATE was, for either kind. */
    @Test
    public void aNewBodyIsCompiledToo() {
        assertEquals("Declared return type 'VARCHAR(134217728)' is incompatible with actual return"
            + " type 'NUMBER(1,0)'",
            outcome("ALTER MASKING POLICY mp SET BODY -> 1"));
        assertEquals("SQL compilation error: error line 1 at position 1 invalid identifier 'NOSUCH'",
            outcome("ALTER MASKING POLICY mp SET BODY -> nosuch"));
        assertEquals("Declared return type 'BOOLEAN' is incompatible with actual return type"
            + " 'NUMBER(1,0)'",
            outcome("ALTER ROW ACCESS POLICY rap SET BODY -> 1"));
        assertEquals("SQL compilation error: error line 1 at position 1 invalid identifier 'NOSUCH'",
            outcome("ALTER ROW ACCESS POLICY rap SET BODY -> nosuch"));
    }

    /**
     * A body calling a function the account has and this engine does not is NOT refused: this
     * engine's function set is a subset of the account's, and refusing such a body would stop a
     * schema from migrating over a function that is not the point of the policy.
     */
    @Test
    public void aBodyCallingAFunctionThisEngineLacksIsNotRefused() {
        assertEquals("OK", outcome("CREATE OR REPLACE ROW ACCESS POLICY hexa AS (key_column VARCHAR)"
            + " RETURNS BOOLEAN -> key_column = SYS_CONTEXT('SNOWFLAKE$SESSION_ATTRIBUTES', 'TENANT')"));
    }

    /** The bodies that compile still compile — a cast is what makes the wrong literal right. */
    @Test
    public void aBodyThatCompilesIsStillAccepted() {
        assertEquals("OK", outcome("ALTER MASKING POLICY mp SET BODY -> '###'"));
        assertEquals("OK", outcome("ALTER MASKING POLICY mp SET BODY -> val"),
            "the signature's own parameter");
        assertEquals("OK", outcome("ALTER MASKING POLICY mp SET BODY -> 1::STRING"),
            "the cast changes the verdict, because the check reads the body's static type");
        assertEquals("OK", outcome("ALTER ROW ACCESS POLICY rap SET BODY -> x < 100"));
        assertEquals("OK", outcome(
            "CREATE OR REPLACE MASKING POLICY mt AS (val STRING) RETURNS STRING ->"
                + " CASE WHEN CURRENT_ROLE() = 'X' THEN val ELSE '***' END"));
        assertEquals("OK", outcome(
            "CREATE OR REPLACE ROW ACCESS POLICY rs AS (x INT) RETURNS BOOLEAN -> TRUE"));
    }
}
