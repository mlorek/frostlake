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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A positional bind {@code ?} that nothing supplied — no client bind parameter, no {@code OPEN … USING},
 * no {@code EXECUTE IMMEDIATE … USING} — is refused at compile time with the sentence an unsupplied
 * {@code :1} gets, {@code Bind variable ? not set.}, positioned on the {@code ?} itself: in a query, in a
 * DML statement, in the text of an EXECUTE IMMEDIATE (counted from that text), and in a cursor opened
 * without USING, where the block's uncaught-exception wrapper carries it.
 */
public class UnboundPositionalBindTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    private static String notSet(final int line, final int position) {
        return "SQL compilation error: error line " + line + " at position " + position
            + "\nBind variable ? not set.";
    }

    @Test
    public void aPlaceholderInTheSelectList() {
        assertEquals(notSet(1, 7), refusal("SELECT ?"));
        assertEquals(notSet(1, 7), refusal("SELECT ?, ?"));
        assertEquals(notSet(1, 7), refusal("SELECT ? + 1"));
        assertEquals(notSet(1, 7), refusal("SELECT ?::INT"));
    }

    @Test
    public void aPlaceholderInAPredicate() {
        engine.execute("CREATE TABLE t (i INT)");
        assertEquals(notSet(1, 19), refusal("SELECT 1 WHERE 1 = ?"));
        assertEquals(notSet(1, 28), refusal("SELECT 1 FROM t WHERE i IN (?)"));
    }

    @Test
    public void aPlaceholderOnALaterLineKeepsItsOwnColumn() {
        assertEquals(notSet(2, 2), refusal("SELECT\n  ?"));
    }

    @Test
    public void aPlaceholderInInsertValues() {
        engine.execute("CREATE TABLE t (i INT)");
        assertEquals(notSet(1, 22), refusal("INSERT INTO t VALUES (?)"));
    }

    @Test
    public void anExecuteImmediateTextIsPositionedFromItsOwnStart() {
        assertEquals(notSet(1, 7), refusal("EXECUTE IMMEDIATE 'SELECT ?'"));
    }

    @Test
    public void aCursorOpenedWithoutUsing() {
        assertEquals("Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 38 : " + notSet(1, 7),
            refusal("EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT ?; BEGIN OPEN c; RETURN 1; END; $$"));
    }
}
