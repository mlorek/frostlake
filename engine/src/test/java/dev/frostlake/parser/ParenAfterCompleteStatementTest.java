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
 * A '(' written straight after a complete statement is refused at the '(' itself wherever the statement cannot
 * go on with one (live-verified), where Frostlake opened a statement at the bracket and named the first token
 * inside it. After an operand the bracket may begin the {@code (+)} outer-join marker, after a table reference's
 * alias its column list and after a DROP's or a DESCRIBE's name a signature, so there the token inside it is
 * refused on both engines.
 */
public class ParenAfterCompleteStatementTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String syntax(final int position, final String token) {
        return "SQL compilation error:\nsyntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aCompleteDeleteUseOrTruncateRefusesTheBracket() {
        assertEquals(syntax(15, "("), refusal("DELETE FROM t1 ('x')"));
        assertEquals(syntax(15, "("), refusal("DELETE FROM t1 (1)"));
        assertEquals(syntax(15, "("), refusal("DELETE FROM t1 ()"));
        assertEquals(syntax(17, "("), refusal("DELETE FROM t1 x ('x')"));
        assertEquals(syntax(15, "("), refusal("DELETE FROM t1 ('x') WHERE a = 1"));
        assertEquals(syntax(18, "("), refusal("USE SCHEMA PUBLIC ('x')"));
        assertEquals(syntax(11, "("), refusal("USE PUBLIC ('x')"));
        assertEquals(syntax(18, "("), refusal("USE SCHEMA PUBLIC ('x') x y"));
        assertEquals(syntax(18, "("), refusal("TRUNCATE TABLE t1 ('x')"));
        assertEquals(syntax(12, "("), refusal("TRUNCATE t1 ('x')"));
        assertEquals(syntax(16, "("), refusal("SELECT 1 INTO t (1)"));
    }

    @Test
    public void theWordIdentifierWithoutAWholeReferenceIsTheNameBeforeTheBracket() {
        assertEquals(syntax(22, "("), refusal("DELETE FROM IDENTIFIER('t' || '1')"));
        assertEquals(syntax(21, "("), refusal("USE SCHEMA IDENTIFIER('PUB' || 'LIC')"));
        assertEquals(syntax(14, "("), refusal("USE IDENTIFIER('PUB' || 'LIC')"));
        assertEquals(syntax(23, "("), refusal("USE DATABASE IDENTIFIER('a' || 'b')"));
    }

    @Test
    public void everyOtherStatementEndingThatTakesNoBracket() {
        assertEquals(syntax(16, "("), refusal("UNDROP TABLE t1 ('x')"));
        assertEquals(syntax(12, "("), refusal("SHOW TABLES ('x')"));
        assertEquals(syntax(21, "("), refusal("SHOW TABLES LIKE 'x' ('y')"));
        assertEquals(syntax(7, "("), refusal("COMMIT ('x')"));
        assertEquals(syntax(26, "("), refusal("INSERT INTO t1 VALUES (1) ('x')"));
        assertEquals(syntax(24, "("), refusal("CREATE TABLE t2 (a INT) ('x')"));
        assertEquals(syntax(9, "("), refusal("CALL p() ('x')"));
        assertEquals(syntax(35, "("), refusal("GRANT SELECT ON TABLE t1 TO ROLE r ('x')"));
        assertEquals(syntax(27, "("), refusal("COMMENT ON TABLE t1 IS 'x' ('x')"));
        assertEquals(syntax(8, "("), refusal("UNSET v ('x')"));
        assertEquals(syntax(22, "("), refusal("DROP TABLE t1 CASCADE ('x')"));
        assertEquals(syntax(18, "("), refusal("CREATE SEQUENCE s ('x')"));
        assertEquals(syntax(35, "("), refusal("ALTER SESSION SET TIMEZONE = 'UTC' ('x')"));
    }

    @Test
    public void aQueryClauseThatEndsInNoOperandRefusesTheBracket() {
        assertEquals(syntax(25, "("), refusal("SELECT a FROM t1 LIMIT 1 ('x')"));
        assertEquals(syntax(33, "("), refusal("SELECT a FROM t1 ORDER BY a DESC ('x')"));
        assertEquals(syntax(41, "("), refusal("SELECT a FROM t1 FETCH FIRST 1 ROWS ONLY ('x')"));
        assertEquals(syntax(35, "("), refusal("SELECT 1 FROM t1 JOIN t2 USING (a) ('x')"));
        assertEquals(syntax(35, "("), refusal("SELECT a FROM t1 WHERE a IN (1, 2) ('x')"));
        assertEquals(syntax(33, "("), refusal("SELECT a FROM t1 WHERE a IS NULL ('x')"));
        assertEquals(syntax(41, "("), refusal("SELECT a FROM t1 WHERE EXISTS (SELECT 1) ('x')"));
        assertEquals(syntax(63, "("), refusal("MERGE INTO t1 USING t2 ON t1.a = t2.a WHEN MATCHED THEN DELETE ('x')"));
    }

    @Test
    public void anOperandAnAliasOrASignableNameReadsTheBracketOn() {
        assertEquals(syntax(10, "'x'"), refusal("SELECT 1 ('x')"));
        assertEquals(syntax(10, "2"), refusal("SELECT 1 (2)"));
        assertEquals(syntax(21, "'x'"), refusal("UPDATE t1 SET a = 1 ('x')"));
        assertEquals(syntax(28, "'x'"), refusal("DELETE FROM t1 WHERE a = 1 ('x')"));
        assertEquals(syntax(23, "'x'"), refusal("SELECT CAST(1 AS INT) ('x')"));
        assertEquals(syntax(15, "'x'"), refusal("SELECT 1::INT ('x')"));
        assertEquals(syntax(20, "'x'"), refusal("SELECT a FROM t1 x ('x')"));
        assertEquals(syntax(15, "'x'"), refusal("DROP TABLE t1 ('x')"));
        assertEquals(syntax(19, "'x'"), refusal("DESCRIBE TABLE t1 ('x')"));
        assertEquals(syntax(11, "'x'"), refusal("SET v = 1 ('x')"));
        assertEquals(syntax(11, "("), refusal("SELECT 1 x ('x')"));
    }
}
