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
 * A complete statement followed by junk is refused at the junk's FIRST word when the statement cannot take that
 * word, however far a parse of the junk would run; a word the statement can take — an alias, a LIMIT, a FETCH — is
 * the statement's own, and the refusal lies past it. So is a word live reads as the name of a property, as after a
 * DESCRIBE's object.
 */
public class TrailingJunkAfterStatementTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("CREATE TABLE g (e INT, f INT, h INT)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage().replace('\n', '|');
    }

    private static String at(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    /** After a DESCRIBE's object a word is a property's name, however it is spelled, and live refuses further on. */
    @Test
    public void aWordLiveReadsAsAPropertyNameIsNotRefused() {
        assertEquals(at(21, "<EOF>"), refusal("DESCRIBE TABLE t CASE"));
        assertEquals(at(22, "="), refusal("DESCRIBE TABLE t WITH = 1"));
        assertEquals(at(20, "<EOF>"), refusal("DESCRIBE TABLE t FOO"));
    }

    @Test
    public void theJunksFirstWordIsRefused() {
        assertEquals(at(14, "COMMENT"), refusal("SELECT 1 AS x COMMENT = 'x'"));
        assertEquals(at(47, "COMMENT"), refusal("CREATE VIEW ug3 COMMENT = 'a' AS SELECT 1 AS x COMMENT = 'x'"));
        assertEquals(at(22, "COMMENT"), refusal("SELECT 1 AS x, 2 AS y COMMENT = 'z'"));
        assertEquals(at(14, "COMMENT"), refusal("SELECT 1 AS x COMMENT"));
        assertEquals(at(28, "COMMENT"), refusal("SELECT a FROM t WHERE a = 1 COMMENT = 'x'"));
        assertEquals(at(27, "COMMENT"), refusal("SELECT a FROM t ORDER BY a COMMENT = 'x'"));
        assertEquals(at(24, "COMMENT"), refusal("SELECT a FROM t LIMIT 1 COMMENT = 'x'"));
        assertEquals(at(25, "COMMENT"), refusal("INSERT INTO t VALUES (1) COMMENT = 'x'"));
        assertEquals(at(38, "IF"), refusal("ALTER TABLE g DROP COLUMN IF EXISTS e IF EXISTS"));
    }

    @Test
    public void aWordTheStatementTakesIsItsOwn() {
        assertEquals(at(33, "("), refusal("SELECT a FROM t ORDER BY a LIMIT (2)"));
        assertEquals(at(28, "-"), refusal("SELECT a FROM t FETCH FIRST -1 ROWS ONLY"));
    }
}
