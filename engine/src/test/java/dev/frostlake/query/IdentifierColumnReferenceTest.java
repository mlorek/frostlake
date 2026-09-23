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
 * {@code IDENTIFIER(<value>)} in an expression names a COLUMN, and the column is then read exactly as a
 * written reference to it is: the value folds unquoted to upper case and keeps a quoted part's case, it
 * may carry the relation or the whole path before the column, and it reaches a SELECT alias. The item
 * takes the column's own name and the column's type, so the predicate rule sees the type behind it, and
 * a value that reads as no reference, or reaches no column, is refused where the value was written.
 * Frostlake looked the text up in the row's columns and answered nothing more (live-verified).
 */
public class IdentifierColumnReferenceTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t1 (a INT, b BOOLEAN, \"c\" INT)");
        engine.execute("INSERT INTO t1 VALUES (1, TRUE, 3), (2, FALSE, 4)");
        engine.execute("SET cn = 'a'");
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

    /** The result's column names, joined by commas. */
    private String names(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int c = 0; c < result.getColumnCount(); c++) {
            text.append(c > 0 ? ", " : "").append(result.getColumns().get(c).getName());
        }
        return text.toString();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void theValueNamesAColumnAsAWrittenReferenceDoes() {
        assertEquals("1 | 2", rows("SELECT IDENTIFIER('a') FROM t1 ORDER BY 1"));
        assertEquals("1 | 2", rows("SELECT IDENTIFIER('A') FROM t1 ORDER BY 1"));
        assertEquals("1 | 2", rows("SELECT IDENTIFIER(' a ') FROM t1 ORDER BY 1"));
        assertEquals("1 | 2", rows("SELECT IDENTIFIER($cn) FROM t1 ORDER BY 1"));
        assertEquals("3 | 4", rows("SELECT IDENTIFIER('\"c\"') FROM t1 ORDER BY 1"));
        // Qualified by the relation, by its alias, or by the whole path.
        assertEquals("1 | 2", rows("SELECT IDENTIFIER('t1.a') FROM t1 ORDER BY 1"));
        assertEquals("1 | 2", rows("SELECT IDENTIFIER('x.a') FROM t1 x ORDER BY 1"));
        assertEquals("3 | 4", rows("SELECT IDENTIFIER('t1.\"c\"') FROM t1 ORDER BY 1"));
        assertEquals("1 | 2", rows("SELECT IDENTIFIER('test_db.test_schema.t1.a') FROM t1 ORDER BY 1"));
        // A SELECT alias is in reach, and so is every clause a written reference reads in.
        assertEquals("1, 1 | 2, 2", rows("SELECT a AS a2, IDENTIFIER('a2') FROM t1 ORDER BY 1"));
        assertEquals("1", rows("SELECT IDENTIFIER('a') FROM t1 WHERE IDENTIFIER('b') ORDER BY 1"));
        assertEquals("1 | 2", rows("SELECT IDENTIFIER('a') FROM t1 GROUP BY a ORDER BY 1"));
        assertEquals("1", rows("SELECT IDENTIFIER('a') FROM t1 QUALIFY ROW_NUMBER() OVER (ORDER BY IDENTIFIER('a')) = 1"));
        assertEquals("2 | 1", rows("SELECT a FROM t1 ORDER BY IDENTIFIER('a') DESC"));
        assertEquals("3", rows("SELECT SUM(IDENTIFIER('t1.a')) FROM t1"));
        engine.execute("UPDATE t1 SET a = 9 WHERE IDENTIFIER('a') = 1");
        assertEquals("2 | 9", rows("SELECT a FROM t1 ORDER BY 1"));
    }

    @Test
    public void theColumnItNamesGivesTheItemItsNameAndItsType() {
        assertEquals("A", names("SELECT IDENTIFIER('a') FROM t1"));
        assertEquals("A", names("SELECT IDENTIFIER('t1.a') FROM t1"));
        assertEquals("A", names("SELECT IDENTIFIER($cn) FROM t1"));
        assertEquals("c", names("SELECT IDENTIFIER('\"c\"') FROM t1"));
        assertEquals("A, A", names("SELECT IDENTIFIER('a'), IDENTIFIER('a') FROM t1"));
        assertEquals("A2, A2", names("SELECT a AS a2, IDENTIFIER('a2') FROM t1"));
        assertEquals("Z", names("SELECT IDENTIFIER('a') AS z FROM t1"));
        // Inside a larger expression the item keeps its own text, upper-cased as any other item is.
        assertEquals("IDENTIFIER('A') + 1", names("SELECT IDENTIFIER('a') + 1 FROM t1"));
        assertEquals("SUM(IDENTIFIER('T1.A'))", names("SELECT SUM(IDENTIFIER('t1.a')) FROM t1"));
        // The type behind the name is the column's, which is what the predicate rule reads.
        assertEquals("SQL compilation error:\nInvalid data type [NUMBER(38,0)] for predicate [T1.A]",
            refusal("SELECT 1 FROM t1 WHERE IDENTIFIER('a')"));
        assertEquals("1", rows("SELECT 1 FROM t1 WHERE IDENTIFIER('b')"));
    }

    @Test
    public void whatTheValueWillNotReachIsRefusedWhereItWasWritten() {
        assertEquals("SQL compilation error: error line 1 at position 18\ninvalid identifier 'NOSUCH'",
            refusal("SELECT IDENTIFIER('nosuch') FROM t1"));
        assertEquals("SQL compilation error: error line 1 at position 18\ninvalid identifier 'T1.NOSUCH'",
            refusal("SELECT IDENTIFIER('t1.nosuch') FROM t1"));
        // Matched exactly: an unquoted value reaches no column written in quotes.
        assertEquals("SQL compilation error: error line 1 at position 18\ninvalid identifier 'C'",
            refusal("SELECT IDENTIFIER('c') FROM t1"));
        // A path that names another schema reaches nothing here, and a table is not a column.
        assertEquals("SQL compilation error: error line 1 at position 18\ninvalid identifier 'OTHER.S.T1.A'",
            refusal("SELECT IDENTIFIER('other.s.t1.a') FROM t1"));
        assertEquals("SQL compilation error: error line 1 at position 18\ninvalid identifier 'T1'",
            refusal("SELECT IDENTIFIER('t1') FROM t1"));
        // A value that reads as no reference at all is echoed as written, at its own place.
        assertEquals("SQL compilation error: error line 1 at position 18\ninvalid identifier '1'",
            refusal("SELECT IDENTIFIER(1) FROM t1"));
        assertEquals("SQL compilation error: error line 1 at position 19\ninvalid identifier '1'",
            refusal("SELECT identifier (1)"));
        assertEquals("SQL compilation error: error line 1 at position 18\ninvalid identifier ''''",
            refusal("SELECT IDENTIFIER('') FROM t1"));
        assertEquals("SQL compilation error: error line 1 at position 40\ninvalid identifier ''MAX(a)''",
            refusal("SELECT MAX(a) FROM t1 HAVING IDENTIFIER('MAX(a)') > 0"));
        // A variable's value is canonicalised before it is echoed.
        engine.execute("SET tn = 't1'");
        assertEquals("SQL compilation error: error line 1 at position 18\ninvalid identifier 'T1'",
            refusal("SELECT IDENTIFIER($tn) FROM t1"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 18 unexpected 'NULL'.",
            refusal("SELECT IDENTIFIER(NULL) FROM t1"));
    }
}
