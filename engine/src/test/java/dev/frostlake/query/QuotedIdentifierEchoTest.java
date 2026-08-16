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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A name that resolves to nothing is echoed AS WRITTEN: an unquoted one folded to upper case, a quoted
 * one verbatim with its quotes and its case intact. The distinction is not decoration — {@code "a"} and
 * {@code A} are different columns, so echoing the folded name for a quoted reference names a column the
 * user never wrote and reports the opposite of what went wrong.
 *
 * <pre>
 *   SELECT "a" FROM kw          invalid identifier '"a"'
 *   SELECT "Abc" FROM kw        invalid identifier '"Abc"'
 *   SELECT "no such" FROM kw    invalid identifier '"no such"'
 *   SELECT nosuchcol FROM kw    invalid identifier 'NOSUCHCOL'   — unquoted, folded
 * </pre>
 */
public class QuotedIdentifierEchoTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE kw (a INT, b INT)");
        engine.execute("INSERT INTO kw VALUES (1, 2)");
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    private int value(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return ((Number) rs.getValue(0)).intValue();
    }

    /** A quoted name keeps its quotes and its case, whatever the case is. */
    @Test
    public void aQuotedNameIsEchoedVerbatim() {
        assertTrue(refusal("SELECT \"a\" FROM kw").contains("invalid identifier '\"a\"'"),
            refusal("SELECT \"a\" FROM kw"));
        assertTrue(refusal("SELECT \"Abc\" FROM kw").contains("invalid identifier '\"Abc\"'"),
            refusal("SELECT \"Abc\" FROM kw"));
        assertTrue(refusal("SELECT \"no such\" FROM kw").contains("invalid identifier '\"no such\"'"),
            refusal("SELECT \"no such\" FROM kw"));
    }

    /** An UNQUOTED name still folds to upper case — the two spellings must not print alike. */
    @Test
    public void anUnquotedNameStillFolds() {
        assertTrue(refusal("SELECT nosuchcol FROM kw").contains("invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT nosuchcol FROM kw"));
    }

    /** The echo holds in every clause that reports an unresolvable reference. */
    @Test
    public void everyClauseEchoesItTheSameWay() {
        assertTrue(refusal("SELECT a FROM kw WHERE \"a\" = 1").contains("invalid identifier '\"a\"'"),
            refusal("SELECT a FROM kw WHERE \"a\" = 1"));
        assertTrue(refusal("SELECT COUNT(*) FROM kw GROUP BY \"a\"")
            .contains("invalid identifier '\"a\"'"),
            refusal("SELECT COUNT(*) FROM kw GROUP BY \"a\""));
    }

    /** And the quoted name that DOES match still reads — the column is A, so "A" is the way to it. */
    @Test
    public void theMatchingQuotedNameStillReads() {
        assertEquals(1, value("SELECT \"A\" FROM kw"));
        assertEquals(1, value("SELECT a FROM kw"));
    }
}
