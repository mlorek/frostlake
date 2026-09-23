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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * DROP and DESCRIBE take a parenthesised signature after an object's name — the shape a routine's arguments
 * take — and ignore it: {@code DESCRIBE TABLE t1 (x)}, {@code (VARCHAR(10))}, {@code ()} and {@code (a, b)}
 * all describe T1. Each item is one word with the parameters a type carries, so a number, a string, a nesting,
 * a second word and a trailing comma are syntax errors. Frostlake refused every list (live-verified).
 */
public class ObjectSignatureListTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t1 (x INT)");
        engine.execute("CREATE VIEW v1 AS SELECT x FROM t1");
    }

    /** The first column of each row, rows joined by bars. */
    private String rows(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int r = 0; r < result.getRowCount(); r++) {
            text.append(r > 0 ? " | " : "").append(result.getRows().get(r).getValue(0));
        }
        return text.toString();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    @Test
    public void theSignatureIsReadAndIgnored() {
        assertEquals("X", rows("DESCRIBE TABLE t1 (x)"));
        assertEquals("X", rows("DESCRIBE TABLE t1 (VARCHAR(10))"));
        assertEquals("X", rows("DESCRIBE TABLE t1 ()"));
        assertEquals("X", rows("DESCRIBE TABLE t1 (a, b)"));
        assertEquals("X", rows("DESCRIBE TABLE t1 (INT, VARCHAR)"));
        assertEquals("X", rows("DESCRIBE TABLE t1 (NUMBER(10,2))"));
        assertEquals("X", rows("DESCRIBE TABLE t1 (\"x\")"));
        assertEquals("X", rows("DESCRIBE TABLE t1 (x, x)"));
        assertEquals("X", rows("DESCRIBE VIEW v1 (a)"));
        // A DROP reads it too, and drops the table.
        engine.execute("CREATE TABLE tz (x INT)");
        engine.execute("DROP TABLE tz (VARCHAR, INT)");
        assertEquals("0", rows("SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'TZ'"));
        engine.execute("DROP VIEW v1 (a)");
        // The object is still resolved as it always was: a missing one is named, and IF EXISTS forgives it.
        assertEquals(hinted("SQL compilation error:\nTable 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized."),
            refusal("DROP TABLE nosuch (x)"));
        engine.execute("DROP TABLE IF EXISTS nosuch (x)");
        // An IDENTIFIER() the lexer finds whole is still the reference; otherwise the word is the name.
        assertEquals(hinted("SQL compilation error:\nTable 'TEST_DB.TEST_SCHEMA.IDENTIFIER' does not exist or not authorized."),
            refusal("DROP TABLE IDENTIFIER(t1)"));
        assertEquals(hinted("SQL compilation error:\nTable 'IDENTIFIER' does not exist or not authorized."),
            refusal("DESCRIBE TABLE IDENTIFIER(t1)"));
    }

    @Test
    public void whatTheSignatureWillNotHoldIsASyntaxError() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 19 unexpected '1'.",
            refusal("DESCRIBE TABLE t1 (1)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 19 unexpected ''a''.",
            refusal("DESCRIBE TABLE t1 ('a')"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 21 unexpected 'b'.",
            refusal("DESCRIBE TABLE t1 (a b)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 21 unexpected ')'.",
            refusal("DESCRIBE TABLE t1 (a,)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 22 unexpected '('.",
            refusal("DESCRIBE TABLE t1 (x) (y)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 17 unexpected 'INT'.",
            refusal("DROP TABLE t1 (x INT)"));
    }
}
