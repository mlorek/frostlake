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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an owner's rights stored procedure may not create — measured cell by cell on a real account,
 * because the documented sentence ("cannot create named temporary objects") is both narrower and wider
 * than it sounds.
 *
 * <pre>
 *   Java proc,   EXECUTE AS OWNER    refused, naming the kind
 *   Python proc, EXECUTE AS OWNER    refused the same way (covered in the rt-py module)
 *   any proc,    EXECUTE AS CALLER   allowed
 *   SQL proc,    EXECUTE AS OWNER    ALLOWED — the rule does not reach LANGUAGE SQL
 *   Java OWNER proc that CALLs a SQL proc creating a temp table   ALLOWED
 *   CREATE TRANSIENT TABLE under owner's rights                   ALLOWED
 * </pre>
 */
public class OwnersRightsTemporaryObjectTest extends BaseDatabaseTest {

    private static final String TABLE_REFUSAL =
        "Stored procedure execution error: Unsupported statement type 'temporary TABLE'.";

    /**
     * A Java handler that pushes one statement through its injected session.
     *
     * <p>The {@code .collect()} is not decoration: Snowpark's {@code session.sql} is LAZY on live, so
     * without an action the statement is never submitted and the refusal below never fires — measured,
     * the same handler returns normally without it and raises the refusal with it. Frostlake's stub runs
     * the statement inside {@code sql} itself, which is a divergence in its own right.
     *
     * <p>PACKAGES is what puts Snowpark on the compiler's classpath — measured on live, a body importing
     * {@code com.snowflake.snowpark_java} without it fails with "package com.snowflake.snowpark_java does
     * not exist", while PACKAGES alone is enough (RUNTIME_VERSION is optional). Frostlake supplies its own
     * Snowpark stub either way, so the clause changes nothing here beyond letting the same DDL run live.
     */
    private void javaProcedure(final String name, final String rights, final String statement) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS VARCHAR LANGUAGE JAVA"
            + " PACKAGES=('com.snowflake:snowpark:latest')"
            + " HANDLER='H.go' EXECUTE AS " + rights + " AS $$\n"
            + "import com.snowflake.snowpark_java.Session;\n"
            + "public class H {\n"
            + "  public String go(Session session) {\n"
            + "    session.sql(\"" + statement + "\").collect();\n"
            + "    return \"created\";\n"
            + "  }\n"
            + "}\n$$");
    }

    private String refusalOf(final String procedureName) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("CALL " + procedureName + "()");
            }
        });
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    private String outcomeOf(final String procedureName) {
        try {
            engine.executeQuery("CALL " + procedureName + "()");
            return "OK";
        } catch (final RuntimeException e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            return root.getMessage();
        }
    }

    /**
     * Assert the refusal sentence, wherever it sits in the message.
     *
     * <p>Frostlake raises exactly the sentence. Live raises the same sentence verbatim, but a Java
     * procedure surfaces its error through CALL wrapped as
     * {@code "User Error Report: Java Stack Trace: net...SnowflakeSQLException: <sentence>"} followed by
     * the whole stack, so the two agree on the claim and differ only in the packaging around it.
     */
    private void assertRefusal(final String expected, final String actual, final String context) {
        assertTrue(actual != null && actual.contains(expected),
            context + " — expected the refusal <" + expected + "> inside: " + actual);
    }

    /** Every spelling of a temporary TABLE reports the same kind — live folds them all to 'temporary TABLE'. */
    @Test
    public void everyTemporaryTableSpellingIsRefusedAsTable() {
        final String[] spellings = {
            "CREATE TEMPORARY TABLE t1 (a INTEGER)",
            "CREATE TEMP TABLE t2 (a INTEGER)",
            "CREATE LOCAL TEMPORARY TABLE t3 (a INTEGER)",
            "CREATE GLOBAL TEMPORARY TABLE t4 (a INTEGER)",
            "CREATE VOLATILE TABLE t5 (a INTEGER)",
            "CREATE TEMPORARY TABLE IF NOT EXISTS t6 (a INTEGER)",
            "CREATE OR REPLACE TEMPORARY TABLE t7 (a INTEGER)",
            "CREATE TEMPORARY TABLE t8 AS SELECT 1 a",
        };
        for (final String spelling : spellings) {
            javaProcedure("p_tbl", "OWNER", spelling);
            assertRefusal(TABLE_REFUSAL, refusalOf("p_tbl"), spelling);
        }
    }

    /** The other two kinds Frostlake's grammar can express, each naming itself. */
    @Test
    public void stageAndFileFormatNameTheirOwnKind() {
        javaProcedure("p_stage", "OWNER", "CREATE TEMPORARY STAGE s1");
        assertRefusal("Stored procedure execution error: Unsupported statement type 'temporary STAGE'.",
            refusalOf("p_stage"), "CREATE TEMPORARY STAGE");

        javaProcedure("p_ff", "OWNER", "CREATE TEMPORARY FILE FORMAT f1 TYPE=CSV");
        assertRefusal("Stored procedure execution error: Unsupported statement type 'temporary FILE_FORMAT'.",
            refusalOf("p_ff"), "CREATE TEMPORARY FILE FORMAT");
    }

    /**
     * The two kinds that used to escape this guard: before the grammar learned the temporary spellings
     * of VIEW and FUNCTION, both failed as unsupported SYNTAX long before the owner's-rights check ran,
     * so the refusal they were supposed to produce could not be asserted at all.
     */
    @Test
    public void viewAndFunctionNameTheirOwnKind() {
        javaProcedure("p_view", "OWNER", "CREATE TEMPORARY VIEW v1 AS SELECT 1 AS n");
        assertRefusal("Stored procedure execution error: Unsupported statement type 'temporary VIEW'.",
            refusalOf("p_view"), "CREATE TEMPORARY VIEW");

        javaProcedure("p_fn", "OWNER", "CREATE TEMPORARY FUNCTION f1() RETURNS INTEGER AS '1'");
        assertRefusal("Stored procedure execution error: Unsupported statement type 'temporary FUNCTION'.",
            refusalOf("p_fn"), "CREATE TEMPORARY FUNCTION");
    }

    /** TRANSIENT is NOT temporary: live creates it under owner's rights without complaint. */
    @Test
    public void transientAndPermanentObjectsAreAllowed() {
        javaProcedure("p_transient", "OWNER", "CREATE TRANSIENT TABLE tr1 (a INTEGER)");
        assertEquals("OK", outcomeOf("p_transient"));

        javaProcedure("p_perm", "OWNER", "CREATE TABLE pm1 (a INTEGER)");
        assertEquals("OK", outcomeOf("p_perm"));
    }

    @Test
    public void callersRightsIsUnrestricted() {
        javaProcedure("p_caller", "CALLER", "CREATE TEMPORARY TABLE c1 (a INTEGER)");
        assertEquals("OK", outcomeOf("p_caller"));
    }

    /**
     * The restriction belongs to the handler languages. A LANGUAGE SQL procedure creates a temporary
     * table under owner's rights on a real account, so Frostlake must not refuse it.
     */
    @Test
    public void aSqlProcedureIsUnrestrictedUnderOwnersRights() {
        engine.execute("CREATE OR REPLACE PROCEDURE sql_owner() RETURNS VARCHAR LANGUAGE SQL"
            + " EXECUTE AS OWNER AS $$BEGIN CREATE TEMPORARY TABLE sq1 (a INTEGER); RETURN 'created'; END;$$");
        assertEquals("OK", outcomeOf("sql_owner"));
    }

    /**
     * And the rule follows the STATEMENT, not the call tree: a Java owner's rights handler that CALLs a
     * SQL procedure which creates a temporary table is allowed, because that statement never passes
     * through the handler's session.
     */
    @Test
    public void callingASqlProcedureThatCreatesATempTableIsAllowed() {
        engine.execute("CREATE OR REPLACE PROCEDURE inner_sql() RETURNS VARCHAR LANGUAGE SQL"
            + " AS $$BEGIN CREATE TEMPORARY TABLE nested1 (a INTEGER); RETURN 'ok'; END;$$");
        javaProcedure("p_nested", "OWNER", "CALL inner_sql()");
        assertEquals("OK", outcomeOf("p_nested"));
    }
}
