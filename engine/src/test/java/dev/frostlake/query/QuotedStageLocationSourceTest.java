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

/**
 * A string in a FROM list is a stage LOCATION: {@code SELECT $1 FROM '@st/path'} reads the stage
 * exactly as the bare {@code @} form does, and a string that is no location at all is refused while
 * compiling rather than at the parse. Frostlake refused every one of them as a syntax error.
 *
 * <p>The alternative was left out of the grammar twice before, because {@code STRING_LITERAL} as a
 * table source made ALL(*) re-derive {@code FROM (VALUES (…), (…))} tuple lists as a parenthesized
 * join and reject them. The cure was not to leave the string out but to let the VALUES alternative be
 * reached FIRST, so the tuple-list shapes are pinned here beside the stage ones.
 */
public class QuotedStageLocationSourceTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE STAGE st");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR(9))");
        engine.execute("INSERT INTO t VALUES (1, 'x')");
    }

    /** "OK <n> rows", or the refusal's message. */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            int rows = 0;
            while (rs.next()) {
                rows++;
            }
            return "OK " + rows + " rows";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** A quoted stage location reads the stage — an empty one answers no rows, not an error. */
    @Test
    public void aQuotedStageLocationReadsTheStage() {
        assertEquals("OK 0 rows", outcome("SELECT $1 FROM '@st'"));
        assertEquals("OK 0 rows", outcome("SELECT $1 FROM '@st/a.csv'"));
    }

    /** A string that names no location is refused while COMPILING, wherever it stands. */
    @Test
    public void aStringThatIsNoLocationIsRefused() {
        final String refusal = "SQL compilation error: invalid URL prefix found in: 'x'";
        assertEquals(refusal, outcome("SELECT * FROM 'x'"));
        assertEquals(refusal, outcome("SELECT * FROM t, 'x'"));
        assertEquals(refusal, outcome("SELECT * FROM 'x' y"));
        assertEquals(refusal, outcome("SELECT * FROM t JOIN 'x' ON TRUE"));
    }

    /**
     * And because the string is read whole, it is never the FAULT in a refused statement: the parse
     * runs past it and stops where live stops.
     */
    @Test
    public void theStringIsNeverTheFault() {
        assertEquals("SQL compilation error: syntax error line 1 at position 19 unexpected 'FROM'."
            + " syntax error line 1 at position 32 unexpected '1'.",
            outcome("SELECT SUBSTRING(b FROM 'x' FOR 1) FROM t"));
        assertEquals("SQL compilation error: syntax error line 1 at position 19 unexpected '1'.",
            outcome("SELECT 1 FROM 'x', 1"));
        assertEquals("SQL compilation error: syntax error line 1 at position 14 unexpected '2'.",
            outcome("SELECT 1 FROM 2, 'x'"));
    }

    /** The tuple-list shapes the string alternative used to break, still read as VALUES. */
    @Test
    public void aValuesTupleListIsStillAValuesList() {
        assertEquals("OK 2 rows", outcome("SELECT * FROM (VALUES (1, 'a'), (2, 'b'))"));
        assertEquals("OK 2 rows", outcome("SELECT * FROM (VALUES (1, 'a'), (2, 'b')) AS v (x, y)"));
        assertEquals("OK 2 rows", outcome("SELECT * FROM VALUES (1, 'a'), (2, 'b')"));
        assertEquals("OK 1 rows", outcome("SELECT * FROM (t a JOIN t b ON a.a = b.a)"));
    }
}
