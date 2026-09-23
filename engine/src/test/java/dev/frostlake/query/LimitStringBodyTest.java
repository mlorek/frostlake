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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A LIMIT or OFFSET value written as a string is judged with the text the account compiles it in. A task's and an
 * alert's statements are stored as written; a flow chain is judged whole before its first stage runs; EXECUTE
 * IMMEDIATE's text is judged when it runs and placed in that text as written; a procedure's body is judged when the
 * procedure is created, after its signature, and placed in the body's own text; and a policy's body is compiled as a
 * SQL UDF's, in the UDF's frame and words (all live-verified).
 */
public class LimitStringBodyTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t VALUES (1, 2), (3, 4)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    /** Each row's cells joined by " | ", rows by " / ", in the order answered. */
    private String rows(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final List<String> lines = new ArrayList<>();
        for (int r = 0; r < result.getRowCount(); r++) {
            final StringBuilder line = new StringBuilder();
            for (int c = 0; c < result.getColumnCount(); c++) {
                line.append(c > 0 ? " | " : "").append(result.getRows().get(r).getValue(c));
            }
            lines.add(line.toString());
        }
        return String.join(" / ", lines);
    }

    private static String unexpected(final int line, final int position, final String token) {
        return "SQL compilation error:\nsyntax error line " + line + " at position " + position + " unexpected '"
            + token + "'.";
    }

    private static String inUdfBody(final int line, final int position, final String token) {
        return "Compilation of SQL UDF failed: " + unexpected(line, position, token);
    }

    private static String uncaughtAtSix(final String inner) {
        return "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : " + inner;
    }

    @Test
    public void aTasksAndAnAlertsStatementsAreStoredAsWritten() {
        engine.execute("CREATE OR REPLACE TASK tk_lim SCHEDULE = '60 MINUTE' AS SELECT (SELECT a FROM t LIMIT 'x')");
        engine.execute("CREATE OR REPLACE TASK tk_blk SCHEDULE = '60 MINUTE' AS "
            + "BEGIN LET r RESULTSET := (SELECT (SELECT a FROM t LIMIT 'x')); RETURN 1; END");
        engine.execute("CREATE OR REPLACE TASK tk_ok SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("ALTER TASK tk_ok MODIFY AS SELECT (SELECT a FROM t LIMIT 'x')");
        assertEquals("TK_BLK | BEGIN LET r RESULTSET := (SELECT (SELECT a FROM t LIMIT 'x')); RETURN 1; END"
                + " / TK_LIM | SELECT (SELECT a FROM t LIMIT 'x') / TK_OK | SELECT (SELECT a FROM t LIMIT 'x')",
            rows("SHOW TASKS LIKE 'TK_%' ->> SELECT \"name\", \"definition\" FROM $1 ORDER BY \"name\""));
        engine.execute("CREATE OR REPLACE ALERT al_lim SCHEDULE = '60 MINUTE' "
            + "IF (EXISTS (SELECT a FROM t LIMIT 'x')) THEN SELECT 1");
        engine.execute("CREATE OR REPLACE ALERT al_lim2 SCHEDULE = '60 MINUTE' "
            + "IF (EXISTS (SELECT 1)) THEN SELECT (SELECT a FROM t LIMIT 'x')");
        engine.execute("CREATE OR REPLACE ALERT al_ok SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        engine.execute("ALTER ALERT al_ok MODIFY CONDITION EXISTS (SELECT a FROM t LIMIT 'x')");
        engine.execute("ALTER ALERT al_ok MODIFY ACTION SELECT (SELECT a FROM t LIMIT 'x')");
        assertEquals("AL_LIM | SELECT a FROM t LIMIT 'x' | SELECT 1"
                + " / AL_LIM2 | SELECT 1 | SELECT (SELECT a FROM t LIMIT 'x')"
                + " / AL_OK | SELECT a FROM t LIMIT 'x' | SELECT (SELECT a FROM t LIMIT 'x')",
            rows("SHOW ALERTS LIKE 'AL_%' ->> SELECT \"name\", \"condition\", \"action\" FROM $1 ORDER BY \"name\""));
        assertEquals("1", rows("BEGIN CREATE OR REPLACE TASK tk_inblk SCHEDULE = '60 MINUTE' AS "
            + "SELECT a FROM t LIMIT 'x'; RETURN 1; END"));
    }

    @Test
    public void aTasksConditionIsJudgedWithItsStatement() {
        assertEquals(unexpected(1, 82, "'x'"), refusal("CREATE OR REPLACE TASK tk_when SCHEDULE = '60 MINUTE' "
            + "WHEN (SELECT a FROM t LIMIT 'x') = 1 AS SELECT 1"));
        engine.execute("CREATE OR REPLACE TASK tk_ok SCHEDULE = '60 MINUTE' AS SELECT 1");
        assertEquals(unexpected(1, 52, "'x'"), refusal("ALTER TASK tk_ok MODIFY WHEN (SELECT a FROM t LIMIT 'x') = 1"));
    }

    @Test
    public void aFlowChainIsJudgedWholeBeforeItsFirstStageRuns() {
        assertEquals(unexpected(1, 69, "'x'"),
            refusal("CREATE OR REPLACE TABLE t3 (x INT) ->> SELECT (SELECT a FROM t LIMIT 'x')"));
        final String missing = refusal("SELECT COUNT(*) FROM t3");
        assertTrue(missing.contains("Object 'T3' does not exist or not authorized."), missing);
        assertEquals(unexpected(1, 69, "'x'"),
            refusal("CREATE OR REPLACE TABLE t4 (x INT) ->> SELECT (SELECT a FROM t LIMIT 'x') ->> SELECT 1"));
        assertEquals(unexpected(1, 62, "'x'"), refusal("INSERT INTO t VALUES (9, 9) ->> SELECT (SELECT a FROM t LIMIT 'x')"));
        assertEquals("0", rows("SELECT COUNT(*) FROM t WHERE a = 9"));
    }

    @Test
    public void executeImmediatesTextIsPlacedAsWritten() {
        assertEquals(unexpected(1, 22, "'x'"), refusal("/* comment */ EXECUTE IMMEDIATE 'SELECT a FROM t LIMIT ''x'''"));
        assertEquals(unexpected(1, 22, "'x'"), refusal("/* comment */ EXECUTE IMMEDIATE $$SELECT a FROM t LIMIT 'x'$$"));
        assertEquals(unexpected(1, 30, "'x'"),
            refusal("/* comment */ EXECUTE IMMEDIATE 'SELECT (SELECT a FROM t LIMIT ''x'')'"));
        assertEquals(unexpected(1, 38, "'x'"),
            refusal("EXECUTE IMMEDIATE '/* c */ SELECT (SELECT a FROM t LIMIT ''x'')'"));
        assertEquals(unexpected(2, 6, "'x'"), refusal("""
            /* c */
            EXECUTE IMMEDIATE $$SELECT a FROM t
            LIMIT 'x'$$"""));
        assertEquals(unexpected(2, 30, "'x'"), refusal("""
            EXECUTE IMMEDIATE $$
            SELECT (SELECT a FROM t LIMIT 'x')$$"""));
        engine.execute("SET q = 'SELECT (SELECT a FROM t LIMIT ''x'')'");
        assertEquals(unexpected(1, 30, "'x'"), refusal("/* c */ EXECUTE IMMEDIATE $q"));
    }

    /** Inside a block the text is the running statement's failure, which a STATEMENT_ERROR handler catches. */
    @Test
    public void executeImmediatesTextFailsItsStatementInABlock() {
        assertEquals(uncaughtAtSix(unexpected(1, 30, "'x'")),
            refusal("BEGIN EXECUTE IMMEDIATE 'SELECT (SELECT a FROM t LIMIT ''x'')'; RETURN 1; END"));
        assertEquals("statement", rows("""
            BEGIN
              EXECUTE IMMEDIATE 'SELECT (SELECT a FROM t LIMIT ''x'')';
              RETURN 'none';
            EXCEPTION
              WHEN STATEMENT_ERROR THEN RETURN 'statement';
              WHEN OTHER THEN RETURN 'other';
            END"""));
    }

    @Test
    public void aProceduresBodyIsPlacedInItsOwnText() {
        assertEquals(unexpected(1, 56, "'x'"), refusal("CREATE OR REPLACE PROCEDURE p3() RETURNS INT LANGUAGE SQL AS "
            + "BEGIN LET r RESULTSET := (SELECT (SELECT a FROM t LIMIT 'x')); RETURN 1; END"));
        assertEquals(unexpected(2, 52, "'x'"), refusal("""
            CREATE OR REPLACE PROCEDURE p3m() RETURNS INT LANGUAGE SQL AS
            BEGIN
              LET r RESULTSET := (SELECT (SELECT a FROM t LIMIT 'x'));
              RETURN 1;
            END"""));
        assertEquals(unexpected(2, 52, "'x'"), refusal("""
            CREATE OR REPLACE PROCEDURE p3n() RETURNS INT LANGUAGE SQL AS BEGIN
              LET r RESULTSET := (SELECT (SELECT a FROM t LIMIT 'x'));
              RETURN 1;
            END"""));
        assertEquals(unexpected(1, 56, "'x'"), refusal("/* c */ CREATE OR REPLACE PROCEDURE p3c() RETURNS INT LANGUAGE SQL "
            + "AS BEGIN LET r RESULTSET := (SELECT (SELECT a FROM t LIMIT 'x')); RETURN 1; END"));
        assertEquals(unexpected(1, 43, "'x'"), refusal("CREATE OR REPLACE PROCEDURE p3d() RETURNS INT LANGUAGE SQL AS "
            + "DECLARE c CURSOR FOR SELECT a FROM t LIMIT 'x'; BEGIN RETURN 1; END"));
        assertEquals(unexpected(1, 57, "'y'"), refusal("CREATE OR REPLACE PROCEDURE p3o() RETURNS INT LANGUAGE SQL AS "
            + "BEGIN LET r RESULTSET := (SELECT a FROM t LIMIT 1 OFFSET 'y'); RETURN 1; END"));
        assertEquals(unexpected(1, 36, "'x'"), refusal("CREATE OR REPLACE PROCEDURE p3q() RETURNS INT LANGUAGE SQL AS "
            + "$$BEGIN RETURN (SELECT a FROM t LIMIT 'x'); END$$"));
        assertEquals(unexpected(1, 36, "'x'"), refusal("CREATE OR REPLACE PROCEDURE p3s() RETURNS INT LANGUAGE SQL AS "
            + "'BEGIN RETURN (SELECT a FROM t LIMIT ''x''); END'"));
        assertEquals(unexpected(3, 32, "'x'"), refusal("""
            CREATE OR REPLACE PROCEDURE p3w() RETURNS INT LANGUAGE SQL AS $$
            BEGIN
              RETURN (SELECT a FROM t LIMIT 'x');
            END
            $$"""));
        assertEquals(unexpected(1, 23, "'x'"), refusal("CREATE OR REPLACE PROCEDURE p3v() RETURNS INT LANGUAGE SQL AS "
            + "$$ SELECT a FROM t LIMIT 'x'; $$"));
        assertEquals(unexpected(1, 36, "'x'"), refusal("EXECUTE IMMEDIATE $$CREATE OR REPLACE PROCEDURE p3e() RETURNS INT "
            + "LANGUAGE SQL AS BEGIN RETURN (SELECT a FROM t LIMIT 'x'); END$$"));
    }

    /** The body is compiled when the procedure is created: after its signature, and inside a block as a statement. */
    @Test
    public void aProceduresBodyIsJudgedWhenTheProcedureIsCreated() {
        assertEquals("Argument 'A' repeats in the function signature.", refusal("CREATE OR REPLACE PROCEDURE px(a INT, a INT) "
            + "RETURNS INT LANGUAGE SQL AS BEGIN RETURN (SELECT a FROM t LIMIT 'x'); END"));
        assertEquals(uncaughtAtSix(unexpected(1, 36, "'x'")), refusal("BEGIN CREATE OR REPLACE PROCEDURE ph() RETURNS INT "
            + "LANGUAGE SQL AS BEGIN RETURN (SELECT a FROM t LIMIT 'x'); END; RETURN 1; END"));
        assertEquals(uncaughtAtSix(unexpected(1, 36, "'x'")), refusal("BEGIN CREATE OR REPLACE PROCEDURE pt() RETURNS INT "
            + "LANGUAGE SQL AS $$BEGIN RETURN (SELECT a FROM t LIMIT 'x'); END$$; RETURN 1; END"));
        assertEquals("statement", rows("""
            BEGIN
              CREATE OR REPLACE PROCEDURE pq() RETURNS INT LANGUAGE SQL AS BEGIN RETURN (SELECT a FROM t LIMIT 'x'); END;
              RETURN 'none';
            EXCEPTION
              WHEN STATEMENT_ERROR THEN RETURN 'statement';
              WHEN OTHER THEN RETURN 'other';
            END"""));
    }

    @Test
    public void aPolicysBodyIsCompiledAsASqlUdfBody() {
        assertEquals(inUdfBody(1, 24, "'x'"),
            refusal("CREATE OR REPLACE MASKING POLICY mp1 AS (val INT) RETURNS INT ->(SELECT a FROM t LIMIT 'x')"));
        assertEquals(inUdfBody(1, 24, "'x'"), refusal("""
            CREATE OR REPLACE MASKING POLICY mp3 AS (val INT) RETURNS INT ->
              (SELECT a FROM t LIMIT 'x')"""));
        assertEquals(inUdfBody(2, 6, "'x'"), refusal("""
            CREATE OR REPLACE MASKING POLICY mp4 AS (val INT) RETURNS INT -> (SELECT a FROM t
            LIMIT 'x')"""));
        assertEquals(inUdfBody(1, 40, "'x'"), refusal("CREATE OR REPLACE MASKING POLICY mp5 AS (val INT) RETURNS INT -> "
            + "CASE WHEN val > (SELECT a FROM t LIMIT 'x') THEN 1 ELSE 0 END"));
        assertEquals(inUdfBody(1, 24, "'x'"), refusal("CREATE OR REPLACE MASKING POLICY mpc AS (val INT) RETURNS INT -> "
            + "/* c */ (SELECT a FROM t LIMIT 'x')"));
        assertEquals(inUdfBody(1, 33, "'y'"), refusal("CREATE OR REPLACE MASKING POLICY mp7 AS (val INT) RETURNS INT -> "
            + "(SELECT a FROM t LIMIT 1 OFFSET 'y')"));
        assertEquals(inUdfBody(1, 31, "'x'"), refusal("CREATE OR REPLACE MASKING POLICY mp8 AS (val INT) RETURNS INT -> "
            + "(SELECT a FROM nosuch_t LIMIT 'x')"));
        assertEquals(inUdfBody(1, 24, "'x'"), refusal("CREATE OR REPLACE MASKING POLICY mpx AS (val INT) RETURNS STRING -> "
            + "(SELECT a FROM t LIMIT 'x')"));
        assertEquals(inUdfBody(1, 31, "'x'"), refusal("/* c */ CREATE OR REPLACE ROW ACCESS POLICY rap1 AS (val INT) "
            + "RETURNS BOOLEAN -> EXISTS (SELECT a FROM t LIMIT 'x')"));
        assertEquals(inUdfBody(1, 58, "'x'"), refusal("CREATE OR REPLACE PROJECTION POLICY pp1 AS () RETURNS "
            + "PROJECTION_CONSTRAINT -> PROJECTION_CONSTRAINT(ALLOW => (SELECT TRUE FROM t LIMIT 'x'))"));
        assertEquals(inUdfBody(1, 65, "'x'"), refusal("CREATE OR REPLACE AGGREGATION POLICY ap1 AS () RETURNS "
            + "AGGREGATION_CONSTRAINT -> AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => (SELECT a FROM t LIMIT 'x'))"));
        assertEquals(inUdfBody(1, 60, "'x'"), refusal("CREATE OR REPLACE JOIN POLICY jp1 AS () RETURNS JOIN_CONSTRAINT -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => (SELECT TRUE FROM t LIMIT 'x'))"));
        assertEquals(uncaughtAtSix(inUdfBody(1, 24, "'x'")), refusal("BEGIN CREATE OR REPLACE MASKING POLICY mpb AS (val INT) "
            + "RETURNS INT -> (SELECT a FROM t LIMIT 'x'); RETURN 1; END"));
    }

    @Test
    public void aPolicysNewBodyIsCompiledAsASqlUdfBody() {
        engine.execute("CREATE OR REPLACE MASKING POLICY mp_ok AS (val INT) RETURNS INT -> val");
        assertEquals(inUdfBody(1, 24, "'x'"), refusal("ALTER MASKING POLICY mp_ok SET BODY -> (SELECT a FROM t LIMIT 'x')"));
        assertEquals(inUdfBody(1, 24, "'x'"), refusal("""
            ALTER MASKING POLICY mp_ok SET BODY ->
              (SELECT a FROM t LIMIT 'x')"""));
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rap_ok AS (val INT) RETURNS BOOLEAN -> TRUE");
        assertEquals(inUdfBody(1, 31, "'x'"),
            refusal("ALTER ROW ACCESS POLICY rap_ok SET BODY -> EXISTS (SELECT a FROM t LIMIT 'x')"));
        engine.execute("CREATE OR REPLACE PROJECTION POLICY pp_ok AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "PROJECTION_CONSTRAINT(ALLOW => true)");
        assertEquals(inUdfBody(1, 58, "'x'"), refusal("ALTER PROJECTION POLICY pp_ok SET BODY -> "
            + "PROJECTION_CONSTRAINT(ALLOW => (SELECT TRUE FROM t LIMIT 'x'))"));
        engine.execute("CREATE OR REPLACE AGGREGATION POLICY ap_ok AS () RETURNS AGGREGATION_CONSTRAINT -> "
            + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5)");
        assertEquals(inUdfBody(1, 65, "'x'"), refusal("ALTER AGGREGATION POLICY ap_ok SET BODY -> "
            + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => (SELECT a FROM t LIMIT 'x'))"));
        engine.execute("CREATE OR REPLACE JOIN POLICY jp_ok AS () RETURNS JOIN_CONSTRAINT -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => true)");
        assertEquals(inUdfBody(1, 60, "'x'"), refusal("ALTER JOIN POLICY jp_ok SET BODY -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => (SELECT TRUE FROM t LIMIT 'x'))"));
    }
}
