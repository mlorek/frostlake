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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * {@code FROM TABLE(<operand>)} takes a table function's call or a VALUE naming a relation — a string, a
 * dollar-quoted string, a session variable or a bind — and nothing else: an arbitrary expression, a number
 * and a parenthesised value are syntax errors where the reading stops, and a bare name is refused twice,
 * at the ')' and again at the token after it. A value is read exactly as {@code IDENTIFIER(<value>)} is:
 * it folds unquoted to upper case, a name reaching nothing earns the missing-object sentence, and the
 * source registers the name it resolves to, so a second source of that name is a duplicate alias.
 * Frostlake took any expression at all, and a table literal registered nothing (live-verified).
 */
public class TableOperandTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t1 (x INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2)");
        engine.execute("CREATE VIEW v1 AS SELECT x FROM t1");
    }

    /** Each row's cells joined by commas, rows by bars. */
    private String rows(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int r = 0; r < result.getRowCount(); r++) {
            text.append(r > 0 ? " | " : "");
            for (int c = 0; c < result.getColumnCount(); c++) {
                text.append(c > 0 ? ", " : "").append(result.getRows().get(r).getValue(c));
            }
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
    public void aValueOperandNamesARelationAsIdentifierDoes() {
        assertEquals("2", rows("SELECT COUNT(*) FROM TABLE('t1')"));
        assertEquals("2", rows("SELECT COUNT(*) FROM TABLE('T1')"));
        assertEquals("2", rows("SELECT COUNT(*) FROM TABLE('\"T1\"')"));
        assertEquals("2", rows("SELECT COUNT(*) FROM TABLE($$t1$$)"));
        assertEquals("2", rows("SELECT COUNT(*) FROM TABLE('v1')"));
        assertEquals("2", rows("SELECT COUNT(*) FROM TABLE('test_db.test_schema.t1')"));
        // The relation answers to the name it resolves to, and to an alias written after it.
        assertEquals("1 | 2", rows("SELECT t1.x FROM TABLE('t1') ORDER BY 1"));
        assertEquals("1 | 2", rows("SELECT a.x FROM TABLE('t1') a ORDER BY 1"));
        assertEquals("1, 1 | 1, 2 | 2, 1 | 2, 2", rows("SELECT a.x, b.x FROM TABLE('t1') a, TABLE('t1') b ORDER BY 1, 2"));
        // A table function's call is unchanged.
        assertEquals("2", rows("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 2))"));
        assertEquals("4", rows("SELECT COUNT(*) FROM TABLE('t1'), TABLE(GENERATOR(ROWCOUNT => 2))"));
    }

    @Test
    public void aNameReachingNothingIsNamedAsTheMissingObject() {
        assertEquals(hinted("SQL compilation error:\nObject 'NOSUCH' does not exist or not authorized."),
            refusal("SELECT * FROM TABLE('nosuch')"));
        assertEquals(hinted("SQL compilation error:\nObject '\"nosuch\"' does not exist or not authorized."),
            refusal("SELECT * FROM TABLE('\"nosuch\"')"));
        assertEquals(hinted("SQL compilation error:\nDatabase 'NOSUCHDB' does not exist or not authorized."),
            refusal("SELECT * FROM TABLE('nosuchdb.s.t')"));
        assertEquals(hinted("SQL compilation error:\nSchema 'TEST_DB.NOSUCH' does not exist or not authorized."),
            refusal("SELECT * FROM TABLE('nosuch.t1')"));
        assertEquals("SQL compilation error: error line 1 at position 27\ninvalid identifier ''''",
            refusal("SELECT COUNT(*) FROM TABLE('')"));
        // Each source resolves before the aliases are weighed, so a missing object beats a duplicate.
        assertEquals(hinted("SQL compilation error:\nObject 'NOSUCH' does not exist or not authorized."),
            refusal("SELECT * FROM TABLE('nosuch'), TABLE('nosuch')"));
        // A literal registers the name it resolves to: a second source of that name collides.
        assertEquals("SQL compilation error:\nduplicate alias 'T1'", refusal("SELECT * FROM TABLE('t1'), t1"));
        assertEquals("SQL compilation error:\nduplicate alias 'T1'",
            refusal("SELECT * FROM TABLE('t1'), TABLE('\"T1\"')"));
        assertEquals("SQL compilation error:\nduplicate alias 'T1'",
            refusal("SELECT * FROM TABLE('test_db.test_schema.t1'), t1"));
        assertEquals("SQL compilation error:\nduplicate alias 'T1'",
            refusal("SELECT * FROM TABLE('t1') CROSS JOIN TABLE('t1')"));
    }

    @Test
    public void whatTheOperandWillNotHoldIsASyntaxError() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 31 unexpected '||'.",
            refusal("SELECT COUNT(*) FROM TABLE('t' || '1')"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 27 unexpected '1'.",
            refusal("SELECT COUNT(*) FROM TABLE(1)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 27 unexpected 'NULL'.",
            refusal("SELECT COUNT(*) FROM TABLE(NULL)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 27 unexpected '('.",
            refusal("SELECT COUNT(*) FROM TABLE(('t1'))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 27 unexpected 'SELECT'.",
            refusal("SELECT COUNT(*) FROM TABLE(SELECT 1)"));
        // A bare name could still open a call, so the reading stops at the ')' — and again after it.
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 29 unexpected ')'."
            + "\nsyntax error line 1 at position 30 unexpected '<EOF>'.",
            refusal("SELECT COUNT(*) FROM TABLE(t1)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 31 unexpected ')'."
            + "\nsyntax error line 1 at position 33 unexpected 'WHERE'.",
            refusal("SELECT COUNT(*) FROM TABLE(\"t1\") WHERE x = 1"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 22 unexpected ')'."
            + "\nsyntax error line 1 at position 23 unexpected ','.",
            refusal("SELECT * FROM TABLE(t1), t1"));
    }
}
