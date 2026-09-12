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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shapes a name with an empty middle part, {@code db..t}, cannot take are syntax errors, raised before
 * any name is resolved: a column reference needs its fourth part and takes no fifth, an object name ends
 * with its third, and a bare SHOW scope is refused at the token after the name. None of these statements
 * needs its objects to exist.
 */
public class EmptySchemaPartSyntaxTest extends BaseDatabaseTest {

    private void assertSyntax(final String sql, final String lines) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains("SQL compilation error:\n" + lines),
            refused.getMessage());
    }

    private void assertUnexpected(final String sql, final int position, final String token) {
        assertSyntax(sql, "syntax error line 1 at position " + position + " unexpected '" + token + "'.");
    }

    /** Whatever follows a three-part column reference is refused, where it stands. */
    @Test
    public void aThreePartColumnReferenceIsRefusedAtTheNextToken() {
        assertUnexpected("SELECT T..x FROM P440B_DB..T", 12, "FROM");
        assertUnexpected("SELECT S1..x FROM P440B_DB..T", 13, "FROM");
        assertUnexpected("SELECT P440C_DB..X FROM P440C_DB..T", 19, "FROM");
        assertUnexpected("SELECT T..x", 11, "<EOF>");
        assertUnexpected("SELECT T..x, 1 FROM P440B_DB..T", 11, ",");
        assertUnexpected("SELECT T..x AS a FROM P440B_DB..T", 12, "AS");
        assertUnexpected("SELECT T..x y FROM P440B_DB..T", 12, "y");
        assertUnexpected("SELECT 1 FROM P440B_DB..T WHERE T..x = 1", 37, "=");
        assertUnexpected("SELECT x FROM P440B_DB..T ORDER BY T..x", 39, "<EOF>");
        assertUnexpected("SELECT x FROM P440B_DB..T ORDER BY T..x DESC", 40, "DESC");
        assertUnexpected("SELECT T..x:y FROM P440B_DB..T", 11, ":");
        assertUnexpected("SELECT T..x::INT FROM P440B_DB..T", 11, "::");
        assertUnexpected("SELECT T..x[0] FROM P440B_DB..T", 11, "[");
        assertUnexpected("SELECT T..x+1 FROM P440B_DB..T", 11, "+");
        assertUnexpected("SELECT \"T\"..x FROM P440B_DB..T", 14, "FROM");
        assertUnexpected("SELECT T..\"x\" FROM P440B_DB..T", 14, "FROM");
        assertUnexpected("SELECT T..x/* c */FROM P440B_DB..T", 18, "FROM");
        assertUnexpected("SELECT T..x FROM P440B_DB..T;", 12, "FROM");
        assertUnexpected("UPDATE P440B_DB..T SET x = T..x", 31, "<EOF>");
        assertUnexpected("CREATE OR REPLACE TABLE T3 (a INT DEFAULT T..x)", 46, ")");
        assertUnexpected("INSERT ALL WHEN x = 1 THEN INTO T VALUES (T..x) SELECT 1 AS x", 46, ")");
        assertSyntax("""
            SELECT T..x
            FROM P440B_DB..T""",
            "syntax error line 2 at position 0 unexpected 'FROM'.");
    }

    /** Positions count from the statement's first word, and a dynamic statement counts from its own start. */
    @Test
    public void positionsCountFromTheStatementItself() {
        assertUnexpected("/* c */ SELECT T..x FROM P440B_DB..T", 12, "FROM");
        assertSyntax("""
            /* c */
            SELECT T..x FROM P440B_DB..T""",
            "syntax error line 1 at position 12 unexpected 'FROM'.");
        assertUnexpected("EXECUTE IMMEDIATE 'SELECT T..x FROM P440B_DB..T'", 12, "FROM");
    }

    /** A fifth part is refused at its dot; a sixth adds the dot after it, and no more. */
    @Test
    public void aColumnReferenceTakesNoFifthPart() {
        assertUnexpected("SELECT P440B_DB..T.x.y FROM P440B_DB..T", 20, ".");
        assertUnexpected("SELECT P440B_DB..T.x.y, 1 FROM P440B_DB..T", 20, ".");
        assertUnexpected("SELECT 1 FROM P440B_DB..T WHERE P440B_DB..T.x.y = 1", 45, ".");
        assertSyntax("SELECT P440B_DB..T.x.y.z FROM P440B_DB..T", """
            syntax error line 1 at position 20 unexpected '.'.
            syntax error line 1 at position 22 unexpected '.'.""");
        assertSyntax("SELECT P440B_DB..T.x.y.z.w FROM P440B_DB..T", """
            syntax error line 1 at position 20 unexpected '.'.
            syntax error line 1 at position 22 unexpected '.'.""");
    }

    /** An object name ends with the part after the empty one: a fourth is refused at its dot, in one line. */
    @Test
    public void anObjectNameTakesNoFourthPart() {
        assertUnexpected("CREATE OR REPLACE TABLE P440C_DB..T5.C (x INT)", 36, ".");
        assertUnexpected("CREATE OR REPLACE TABLE P440C_DB..T5.C.D (x INT)", 36, ".");
        assertUnexpected("CREATE OR REPLACE TABLE P440C_DB..T5 .C (x INT)", 37, ".");
        assertUnexpected("CREATE OR REPLACE VIEW P440C_DB..V5.C AS SELECT 1", 35, ".");
        assertUnexpected("CREATE OR REPLACE SEQUENCE P440C_DB..S5.C", 39, ".");
        assertUnexpected("DROP TABLE P440C_DB..T5.C", 23, ".");
        assertUnexpected("ALTER TABLE P440C_DB..T5.C ADD COLUMN y INT", 24, ".");
        assertUnexpected("INSERT INTO P440C_DB..T5.C VALUES (1)", 24, ".");
        assertUnexpected("SELECT * FROM P440C_DB..T5.C", 26, ".");
        assertUnexpected("SELECT 1 FROM P440B_DB..T5.C.D.E", 26, ".");
        assertUnexpected("DESCRIBE TABLE P440C_DB..T5.C", 27, ".");
        assertUnexpected("TRUNCATE TABLE P440C_DB..T5.C", 27, ".");
        assertUnexpected("COMMENT ON COLUMN P440C_DB..T.X IS 'c'", 29, ".");
    }

    /** A bare SHOW scope is refused at the token after db..t; a scope naming its kind, at the second dot. */
    @Test
    public void aBareShowScopeIsRefusedAfterTheName() {
        assertUnexpected("SHOW COLUMNS IN P440B_DB..T", 27, "<EOF>");
        assertUnexpected("SHOW TABLES IN P440C_DB..X", 26, "<EOF>");
        assertUnexpected("SHOW COLUMNS IN P440C_DB..T.X", 27, ".");
        assertUnexpected("SHOW COLUMNS IN P440C_DB..T.X.Y", 27, ".");
        assertUnexpected("SHOW TABLES IN P440C_DB..X;", 26, ";");
        assertUnexpected("SHOW TABLES IN P440C_DB..X FROM", 27, "FROM");
        assertUnexpected("SHOW TABLES IN P440C_DB..X 'a'", 27, "'a'");
        assertUnexpected("SHOW TABLES LIKE 'a' IN P440C_DB..X", 35, "<EOF>");
        assertUnexpected("SHOW OBJECTS IN P440C_DB..X", 27, "<EOF>");
        assertUnexpected("/* c */ SHOW TABLES IN P440C_DB..X", 26, "<EOF>");
        assertUnexpected("SHOW COLUMNS IN TABLE P440_DB..T", 30, ".");
        assertUnexpected("SHOW TABLES IN SCHEMA P440C_DB..S1", 31, ".");
    }
}
