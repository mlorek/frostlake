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

package dev.frostlake.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A statement run into the next one without its semicolon is refused at the next one's first word, and live's
 * recovery then resumes the interrupted statement at the first token after that word it can take (live-verified):
 * a name as the statement's alias, and equally a clause's keyword, an operator or a comma — never a name or a
 * bracket after an unaliased table reference. Whatever fault the resumed statement meets is one more line.
 */
public class StatementResumptionTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String line(final int line, final int position, final String token) {
        return "\nsyntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    private static String refused(final String... lines) {
        final StringBuilder message = new StringBuilder("SQL compilation error:");
        for (final String each : lines) {
            message.append(each);
        }
        return message.toString();
    }

    @Test
    public void aClauseKeywordOrOperatorResumesTheStatement() {
        assertEquals(refused(line(1, 13, "DELETE"), line(1, 29, "y")),
            refusal("SELECT 'foo' DELETE FROM t x y z"));
        assertEquals(refused(line(1, 13, "CREATE"), line(1, 36, "v")),
            refusal("SELECT 'foo' CREATE OR REPLACE VIEW v AS SELECT 1"));
        assertEquals(refused(line(1, 16, "UPDATE"), line(1, 47, "e")),
            refusal("SELECT a FROM t UPDATE t SET a = b WHERE c = d e f"));
        assertEquals(refused(line(1, 28, "DELETE"), line(1, 54, "c")),
            refusal("SELECT a FROM t WHERE a = 1 DELETE FROM t WHERE a = b c"));
        assertEquals(refused(line(1, 13, "DELETE"), line(1, 27, "USING")),
            refusal("SELECT 'foo' DELETE FROM t USING u WHERE a = b"));
        assertEquals(refused(line(1, 13, "SELECT")),
            refusal("SELECT 'foo' SELECT * x y FROM t"));
    }

    @Test
    public void theBlockFormStacksOneLineMore() {
        assertEquals(refused(line(1, 20, "DELETE"), line(1, 36, "y"), line(1, 38, "z")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN SELECT 'foo' DELETE FROM t x y z; END; $$"));
        assertEquals(refused(line(1, 20, "CREATE"), line(1, 43, "v"), line(1, 45, "AS")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN SELECT 'foo' CREATE OR REPLACE VIEW v AS SELECT 1; END; $$"));
        assertEquals(refused(line(1, 20, "SELECT")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN SELECT 'foo' SELECT * x y FROM t; END; $$"));
    }

    @Test
    public void afterAnUnaliasedTableNoNameResumes() {
        assertEquals(refused(line(1, 16, "DELETE")),
            refusal("SELECT a FROM t DELETE FROM t x y z"));
        assertEquals(refused(line(1, 16, "CREATE")),
            refusal("SELECT a FROM t CREATE OR REPLACE TABLE u (a INT)"));
        assertEquals(refused(line(1, 16, "CREATE")),
            refusal("SELECT a FROM t CREATE OR REPLACE VIEW v AS SELECT 1"));
        assertEquals(refused(line(1, 16, "DROP")),
            refusal("SELECT a FROM t DROP TABLE t x"));
        assertEquals(refused(line(1, 16, "GRANT")),
            refusal("SELECT a FROM t GRANT SELECT ON TABLE t TO ROLE r x y"));
        assertEquals(refused(line(1, 16, "ALTER")),
            refusal("SELECT a FROM t ALTER TABLE t RENAME TO u x y"));
        assertEquals(refused(line(1, 16, "SELECT")),
            refusal("SELECT a FROM t SELECT 1 x y z"));
    }

    @Test
    public void otherShapes() {
        assertEquals(refused(line(1, 24, "(")),
            refusal("SELECT 1 INTO IDENTIFIER('t')"));
    }

    @Test
    public void aBlocksLetOrReturnResumesItsValueAtTheFirstTokenItCanTake() {
        assertEquals(refused(line(1, 17, "CREATE"), line(1, 35, "TABLE"), line(1, 43, "(")),
            refusal("BEGIN LET a := 1 CREATE OR REPLACE TABLE u (a INT); END"));
        assertEquals(refused(line(1, 17, "CREATE"), line(1, 35, "VIEW"), line(1, 40, "v")),
            refusal("BEGIN LET a := 1 CREATE OR REPLACE VIEW v AS SELECT 1; END"));
        assertEquals(refused(line(1, 17, "CREATE"), line(1, 35, "SCHEMA"), line(1, 42, "s")),
            refusal("BEGIN LET a := 1 CREATE OR REPLACE SCHEMA s; END"));
        assertEquals(refused(line(1, 17, "CREATE"), line(1, 35, "TABLE"), line(1, 44, "END")),
            refusal("BEGIN LET a := 1 CREATE OR REPLACE TABLE u; END"));
        assertEquals(refused(line(1, 17, "CREATE"), line(1, 35, "TABLE"), line(1, 43, "x")),
            refusal("BEGIN LET a := 1 CREATE OR REPLACE TABLE u x y; END"));
        assertEquals(refused(line(1, 15, "CREATE"), line(1, 33, "TABLE"), line(1, 41, "(")),
            refusal("BEGIN RETURN 1 CREATE OR REPLACE TABLE u (a INT); END"));
        assertEquals(refused(line(1, 17, "CREATE"), line(1, 35, "VIEW"), line(1, 40, "v")),
            refusal("BEGIN RETURN 'x' CREATE OR REPLACE VIEW v AS SELECT 1; END"));
        assertEquals(refused(line(1, 21, "CREATE"), line(1, 39, "TABLE"), line(1, 47, "(")),
            refusal("BEGIN LET a := 1 + 2 CREATE OR REPLACE TABLE u (a INT); END"));
        assertEquals(refused(line(1, 21, "CREATE"), line(1, 39, "TABLE"), line(1, 47, "(")),
            refusal("BEGIN LET a INT := 1 CREATE OR REPLACE TABLE u (a INT); END"));
        assertEquals(refused(line(1, 22, "CREATE"), line(1, 40, "TABLE"), line(1, 48, "(")),
            refusal("BEGIN LET a DEFAULT 1 CREATE OR REPLACE TABLE u (a INT); END"));
        assertEquals(refused(line(3, 0, "CREATE"), line(3, 18, "TABLE"), line(3, 26, "(")),
            refusal("BEGIN\nLET a := 1\nCREATE OR REPLACE TABLE u (a INT);\nEND;"));
        assertEquals(refused(line(3, 0, "CREATE"), line(3, 18, "VIEW"), line(3, 23, "v")),
            refusal("BEGIN\nLET a := 1\nCREATE OR REPLACE VIEW v AS SELECT 1;\nEND;"));
        assertEquals(refused(line(1, 53, "CREATE"), line(1, 71, "TABLE"), line(1, 79, "(")),
            refusal("BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN LET a := 1 CREATE OR REPLACE TABLE u (a INT); END"));
        assertEquals(refused(line(1, 18, "CREATE"), line(1, 36, "TABLE"), line(1, 44, "(")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET a := 1 CREATE OR REPLACE TABLE u (a INT); END $$"));
    }

    @Test
    public void theResumedValueMayEndInABracketABindOrACast() {
        assertEquals(refused(line(1, 19, "CREATE"), line(1, 37, "TABLE"), line(1, 45, "(")),
            refusal("BEGIN LET a := (b) CREATE OR REPLACE TABLE u (a INT); END"));
        assertEquals(refused(line(1, 18, "CREATE"), line(1, 36, "TABLE"), line(1, 44, "(")),
            refusal("BEGIN LET a := :b CREATE OR REPLACE TABLE u (a INT); END"));
        assertEquals(refused(line(1, 22, "CREATE"), line(1, 40, "TABLE"), line(1, 48, "(")),
            refusal("BEGIN LET a := 1::INT CREATE OR REPLACE TABLE u (a INT); END"));
        assertEquals(refused(line(1, 20, "CREATE"), line(1, 38, "TABLE"), line(1, 46, "(")),
            refusal("BEGIN LET a := NULL CREATE OR REPLACE TABLE u (a INT); END"));
    }

    @Test
    public void theSearchPassesReservedWordsAndStopsAtAName() {
        assertEquals(refused(line(1, 17, "CREATE"), line(1, 35, "y"), line(1, 38, "END")),
            refusal("BEGIN LET a := 1 CREATE TABLE OR x y; END"));
        assertEquals(refused(line(1, 17, "CREATE"), line(1, 28, "x"), line(1, 31, "END")),
            refusal("BEGIN LET a := 1 CREATE + 2 x; END"));
        assertEquals(refused(line(1, 17, "DELETE"), line(1, 30, "y"), line(1, 32, "z")),
            refusal("BEGIN LET a := 1 DELETE AND x y z; END"));
        assertEquals(refused(line(1, 17, "CREATE"), line(1, 26, ";")),
            refusal("BEGIN LET a := 1 CREATE OR; END"));
        assertEquals(refused(line(1, 17, "CREATE")),
            refusal("BEGIN LET a := 1 CREATE OR 2; END"));
        assertEquals(refused(line(1, 17, "UPDATE"), line(1, 26, "OR")),
            refusal("BEGIN LET a := 1 UPDATE t OR x y; END"));
        assertEquals(refused(line(1, 17, "CREATE"), line(1, 32, "(")),
            refusal("BEGIN LET a := 1 CREATE TABLE u (a INT); END"));
        assertEquals(refused(line(1, 17, "DELETE"), line(1, 31, "x")),
            refusal("BEGIN LET a := 1 DELETE FROM t x y z; END"));
    }

    @Test
    public void aValueEndingInAWordIsNotResumed() {
        assertEquals(refused(line(1, 17, "CREATE"), line(1, 35, "TABLE")),
            refusal("BEGIN LET a := b CREATE OR REPLACE TABLE u (a INT); END"));
        assertEquals(refused(line(1, 20, "CREATE"), line(1, 38, "TABLE")),
            refusal("BEGIN LET a := TRUE CREATE OR REPLACE TABLE u (a INT); END"));
        assertEquals(refused(line(1, 15, "CREATE"), line(1, 33, "TABLE")),
            refusal("BEGIN RETURN b CREATE OR REPLACE TABLE u (a INT); END"));
    }

    @Test
    public void aValueEndingInACaseExpressionsEndIsResumed() {
        assertEquals(refused(line(1, 41, "CREATE"), line(1, 59, "TABLE"), line(1, 67, "(")),
            refusal("BEGIN LET a := CASE WHEN TRUE THEN 1 END CREATE OR REPLACE TABLE u (a INT); END"));
        assertEquals(refused(line(1, 39, "CREATE"), line(1, 57, "TABLE"), line(1, 65, "(")),
            refusal("BEGIN RETURN CASE WHEN TRUE THEN 1 END CREATE OR REPLACE TABLE u (a INT); END"));
        assertEquals(refused(line(1, 52, "CREATE"), line(1, 70, "VIEW"), line(1, 75, "v")),
            refusal("BEGIN LET a := CASE WHEN TRUE THEN 'x' ELSE 'y' END CREATE OR REPLACE VIEW v AS SELECT 1; END"));
    }

    @Test
    public void inTheScriptsOwnBlockTheStackedLinesEndTheReport() {
        assertEquals(refused(line(1, 19, "DELETE"), line(1, 35, "y"), line(1, 37, "z")),
            refusal("BEGIN SELECT 'foo' DELETE FROM t x y z; RETURN 1 1; END"));
        assertEquals(refused(line(1, 19, "CREATE"), line(1, 42, "v"), line(1, 44, "AS")),
            refusal("BEGIN SELECT 'foo' CREATE OR REPLACE VIEW v AS SELECT 1; RETURN 1 1; END"));
        assertEquals(refused(line(1, 17, "CREATE"), line(1, 32, "(")),
            refusal("BEGIN LET a := 1 CREATE TABLE u (a INT); RETURN 1 1; END"));
        assertEquals(refused(line(1, 17, "CREATE"), line(1, 35, "TABLE"), line(1, 43, "(")),
            refusal("BEGIN LET a := 1 CREATE OR REPLACE TABLE u (a INT); LET b := 1 1; END"));
        assertEquals(refused(line(1, 17, "CREATE"), line(1, 35, "TABLE"), line(1, 44, "RETURN")),
            refusal("BEGIN LET a := 1 CREATE OR REPLACE TABLE u; RETURN 1 1; END"));
        assertEquals(refused(line(1, 28, "TABLE"), line(1, 36, "(")),
            refusal("BEGIN LET a := 1 OR REPLACE TABLE u (a INT); RETURN 1 1; END"));
        assertEquals(refused(line(1, 26, ":"), line(1, 29, "+")),
            refusal("BEGIN SELECT 'foo' RETURN :v + 1; RETURN 1 1; END"));
        assertEquals(refused(line(1, 17, "CREATE"), line(1, 35, "TABLE"), line(1, 43, "(")),
            refusal("BEGIN LET a := 1 CREATE OR REPLACE TABLE u (a INT); EXCEPTION WHEN OTHER THEN RETURN 1 1; END"));
        assertEquals(refused(line(3, 0, "CREATE"), line(3, 18, "TABLE"), line(3, 26, "(")),
            refusal("BEGIN\nLET a := 1\nCREATE OR REPLACE TABLE u (a INT);\nRETURN 1 1;\nEND;"));
    }

    @Test
    public void aFaultWithNoStackedLineLetsTheNextStatementSpeak() {
        assertEquals(refused(line(1, 15, "1"), line(1, 27, "1")), refusal("BEGIN RETURN 1 1; RETURN 1 1; END"));
        assertEquals(refused(line(1, 17, "y"), line(1, 20, "RETURN")), refusal("BEGIN SELECT 1 x y; RETURN 1 1; END"));
    }
}
