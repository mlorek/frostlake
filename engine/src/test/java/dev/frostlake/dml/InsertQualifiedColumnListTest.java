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

package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An item of an INSERT's column list may carry a qualifier, and the only one the account accepts is
 * the TARGET TABLE's own bare name: {@code INSERT INTO t1 (t1.a) VALUES (1)} inserts a row. Frostlake
 * read the list as bare names and refused the dot at the parse.
 *
 * <p>A schema name does not qualify it, a fully qualified target still takes the bare table name, and
 * a third part is a syntax error at the SECOND dot.
 */
public class InsertQualifiedColumnListTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, b INT)");
    }

    /** The refusal a statement raises, or "OK". */
    private String outcome(final String sql) {
        try {
            engine.execute(sql);
            return "OK";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** Every value of one column, in order. */
    private String valuesOf(final String column) {
        final ResultSet rs = engine.executeQuery(
            "SELECT " + column + " FROM t1 WHERE " + column + " IS NOT NULL ORDER BY " + column);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append(String.valueOf(rs.getValue(0)));
        }
        return out.toString();
    }

    /** The target table's own name qualifies a column, and the row really lands. */
    @Test
    public void theTargetTablesNameQualifiesAColumn() {
        assertEquals("OK", outcome("INSERT INTO t1 (t1.a) VALUES (1)"));
        assertEquals("OK", outcome("INSERT INTO t1 (t1.a, t1.b) VALUES (4, 5)"));
        assertEquals("OK", outcome("INSERT INTO t1 (t1.a, b) VALUES (6, 7)"),
            "a qualified item beside a bare one");
        assertEquals("OK", outcome("INSERT INTO t1 (t1.a) SELECT 12"), "and with a SELECT source");
        assertEquals("1 | 4 | 6 | 12", valuesOf("a"));
    }

    /** It qualifies whichever way the target and the item are spelled. */
    @Test
    public void theSpellingOfEitherSideDoesNotMatter() {
        assertEquals("OK", outcome("INSERT INTO test_db.test_schema.t1 (t1.a) VALUES (8)"),
            "a fully qualified target still takes the bare table name");
        assertEquals("OK", outcome("INSERT INTO t1 (T1.A) VALUES (9)"));
        assertEquals("OK", outcome("INSERT INTO t1 (\"T1\".\"A\") VALUES (10)"));
        assertEquals("8 | 9 | 10", valuesOf("a"));
    }

    /** Any other qualifier is an invalid identifier, spelled as the item was written. */
    @Test
    public void anyOtherQualifierIsAnInvalidIdentifier() {
        assertEquals("SQL compilation error: error line 1 at position 16 invalid identifier 'X.A'",
            outcome("INSERT INTO t1 (x.a) VALUES (1)"));
        assertEquals("SQL compilation error: error line 1 at position 16"
            + " invalid identifier 'TEST_SCHEMA.A'",
            outcome("INSERT INTO t1 (test_schema.a) VALUES (3)"),
            "a schema does not qualify a column here");
    }

    /** A column the table does not have is refused the same way, qualifier and all. */
    @Test
    public void anUnknownColumnKeepsItsQualifierInTheSentence() {
        assertEquals("SQL compilation error: error line 1 at position 16 invalid identifier 'T1.ZZ'",
            outcome("INSERT INTO t1 (t1.zz) VALUES (11)"));
    }

    /** One qualifier is the limit: a third part is a syntax error at the second dot, and one line. */
    @Test
    public void aThirdPartIsASyntaxErrorAtTheSecondDot() {
        assertEquals("SQL compilation error: syntax error line 1 at position 25 unexpected '.'.",
            outcome("INSERT INTO t1 (PUBLIC.t1.a) VALUES (2)"));
    }
}
