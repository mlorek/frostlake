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
 * A statement inside a block must end with its semicolon. When a query runs into the next word without
 * one, a word the grammar can read as a name becomes the query's bare alias whenever the query ends in an
 * unaliased select item or table reference, so the error lands on the token after it; otherwise the word
 * itself is the error. A procedure body with such a statement is refused at CREATE. Every cell is
 * live-verified (the first line of each refusal; live sometimes stacks a second).
 */
public class MissingTerminatorTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT)");
    }

    private void assertRefused(final String sql, final String firstLine) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(firstLine), sql + " -> " + refused.getMessage());
    }

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void theNextWordBecomesTheQuerysAlias() {
        assertRefused("EXECUTE IMMEDIATE $$\nDECLARE\n  v OBJECT;\nBEGIN\n  SELECT 'foo'\n  RETURN "
                + ":v;\nEND;\n$$",
            "syntax error line 6 at position 9 unexpected ':'.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 'foo'\n  RETURN 1;\nEND;\n$$",
            "syntax error line 4 at position 9 unexpected '1'.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 'foo'\n  RETURN 'x';\nEND;\n$$",
            "syntax error line 4 at position 9 unexpected ''x''.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 1 UNION SELECT 2\n  RETURN 1;\nEND;\n$$",
            "syntax error line 4 at position 9 unexpected '1'.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 'foo'\n  BREAK 1;\nEND;\n$$",
            "syntax error line 4 at position 8 unexpected '1'.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 'foo'\n  CONTINUE :v;\nEND;\n$$",
            "syntax error line 4 at position 11 unexpected ':'.");
        assertRefused("EXECUTE IMMEDIATE $$\nDECLARE\n  abc INT;\nBEGIN\n  SELECT 'foo'\n  abc := "
                + "1;\nEND;\n$$",
            "syntax error line 6 at position 6 unexpected ':='.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT a FROM t\n  RETURN 1;\nEND;\n$$",
            "syntax error line 4 at position 9 unexpected '1'.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 'foo'\n  LET x := 1;\nEND;\n$$",
            "syntax error line 4 at position 6 unexpected 'x'.");
    }

    @Test
    public void afterAnAliasAClauseOrAnotherStatementTheWordItselfIsTheError() {
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  LET a := 1\n  RETURN a;\nEND;\n$$",
            "syntax error line 4 at position 2 unexpected 'RETURN'.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 1\n  SELECT 2;\nEND;\n$$",
            "syntax error line 4 at position 2 unexpected 'SELECT'.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 'foo' AS a\n  RETURN 1;\nEND;\n$$",
            "syntax error line 4 at position 2 unexpected 'RETURN'.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT a FROM t AS x\n  RETURN 1;\nEND;\n$$",
            "syntax error line 4 at position 2 unexpected 'RETURN'.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 'foo' b\n  RETURN 1;\nEND;\n$$",
            "syntax error line 4 at position 2 unexpected 'RETURN'.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 1 ORDER BY 1\n  RETURN 1;\nEND;\n$$",
            "syntax error line 4 at position 2 unexpected 'RETURN'.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 1 LIMIT 1\n  RETURN 1;\nEND;\n$$",
            "syntax error line 4 at position 2 unexpected 'RETURN'.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  INSERT INTO t VALUES (1)\n  RETURN 1;\nEND;\n$$",
            "syntax error line 4 at position 2 unexpected 'RETURN'.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 'foo' WHERE TRUE\n  RETURN 1;\nEND;\n$$",
            "syntax error line 4 at position 2 unexpected 'RETURN'.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  UPDATE t SET a = 2\n  RETURN 1;\nEND;\n$$",
            "syntax error line 4 at position 2 unexpected 'RETURN'.");
    }

    @Test
    public void aTerminatedAliasIsLegal() {
        engine.executeQuery("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 'foo'\n  BREAK;\nEND;\n$$");
        engine.executeQuery("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 'foo'\n  RETURN;\nEND;\n$$");
        engine.executeQuery("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 'foo'\n  RAISE;\nEND;\n$$");
        assertEquals("2", scalar("EXECUTE IMMEDIATE $$\nBEGIN\n  SELECT 'foo' RETURN;\n  RETURN 2;\nEND;\n$$"));
        assertEquals("foo", scalar("SELECT 'foo' RETURN"));
    }

    @Test
    public void aProcedureIsRefusedAtCreate() {
        assertRefused("CREATE OR REPLACE PROCEDURE PROC_TEST() RETURNS OBJECT LANGUAGE SQL EXECUTE AS "
                + "CALLER AS $$\nDECLARE\n    v_test OBJECT;\nBEGIN\n    SELECT 'foo' --This is "
                + "failing:\n    --SELECT 'foo'; --This is fine:\n\n    RETURN :v_test;\nEND;\n$$",
            "syntax error line 8 at position 11 unexpected ':'.");
        assertRefused("CREATE OR REPLACE PROCEDURE p12() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  "
                + "SELECT 'foo'\n  RETURN 1;\nEND;\n$$",
            "syntax error line 4 at position 9 unexpected '1'.");
        assertRefused("CREATE OR REPLACE PROCEDURE p18() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  "
                + "SELECT 'foo' FROM (SELECT 1)\n  RETURN 'x';\nEND;\n$$",
            "syntax error line 4 at position 9 unexpected ''x''.");
        assertRefused("CREATE OR REPLACE PROCEDURE p19() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  "
                + "LET a := 1\n  RETURN a;\nEND;\n$$",
            "syntax error line 4 at position 2 unexpected 'RETURN'.");
        assertRefused("CREATE OR REPLACE PROCEDURE p20() RETURNS VARCHAR LANGUAGE SQL AS $$\nDECLARE\n "
                + " v OBJECT;\nBEGIN\n  SELECT 'foo'\n  RETURN :v;\nEND;\n$$",
            "syntax error line 6 at position 9 unexpected ':'.");
        assertRefused("CREATE OR REPLACE PROCEDURE q16() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  "
                + "SELECT 'foo' AS a\n  RETURN 1;\nEND;\n$$",
            "syntax error line 4 at position 2 unexpected 'RETURN'.");
        assertRefused("CREATE OR REPLACE PROCEDURE q17() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  "
                + "INSERT INTO t VALUES (1)\n  RETURN 1;\nEND;\n$$",
            "syntax error line 4 at position 2 unexpected 'RETURN'.");
        assertEquals(0, engine.executeQuery("SHOW PROCEDURES LIKE 'PROC_TEST'").getRows().size());
        engine.execute("CREATE OR REPLACE PROCEDURE p13() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  "
            + "SELECT 'foo' RETURN;\n  RETURN 'ok';\nEND;\n$$");
        assertEquals("ok", scalar("CALL p13()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p15() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  "
            + "SELECT 'foo'\n  RETURN;\nEND;\n$$");
        engine.executeQuery("CALL p15()");
    }
}
