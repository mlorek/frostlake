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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Everything that COUNTS — the rankings, GROUPING and GROUPING_ID, and the two CONDITIONAL event
 * windows — is declared at the counter's width, NUMBER(18,0). A replacement, by contrast, is bounded
 * by nothing and is declared a WIDTHLESS VARCHAR, which is its own type on the account and not the
 * 128MB one.
 */
public class GroupingAndEventCounterTypesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE gt (g VARCHAR(5), n INT)");
        engine.execute("INSERT INTO gt VALUES ('a', 1), ('a', 2), ('b', 3)");
    }

    /** The first column of the first row, as text. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** GROUPING and GROUPING_ID carry the counter's width, not the 38-digit default. */
    @Test
    public void theGroupingFunctionsAreCounters() {
        assertEquals("NUMBER(18,0)[SB8]",
            answer("SELECT SYSTEM$TYPEOF(GROUPING(g)) FROM gt GROUP BY ROLLUP(g)"));
        assertEquals("NUMBER(18,0)[SB8]",
            answer("SELECT SYSTEM$TYPEOF(GROUPING(g, n)) FROM gt GROUP BY ROLLUP(g, n)"));
        assertEquals("NUMBER(18,0)[SB8]",
            answer("SELECT SYSTEM$TYPEOF(GROUPING_ID(g)) FROM gt GROUP BY ROLLUP(g)"));
    }

    /** The two conditional event windows count occurrences, and are typed as counters. */
    @Test
    public void theConditionalEventWindowsAreCounters() {
        assertEquals("NUMBER(18,0)[SB8]",
            answer("SELECT SYSTEM$TYPEOF(CONDITIONAL_TRUE_EVENT(n > 1) OVER (ORDER BY n)) FROM gt"));
        assertEquals("NUMBER(18,0)[SB8]",
            answer("SELECT SYSTEM$TYPEOF(CONDITIONAL_CHANGE_EVENT(g) OVER (ORDER BY n)) FROM gt"));
    }

    /** REGEXP_REPLACE is bounded by nothing; its neighbours that COUNT are counters. */
    @Test
    public void aReplacementIsWidthless() {
        assertEquals("VARCHAR[LOB]", answer("SELECT SYSTEM$TYPEOF(REGEXP_REPLACE('ab', '[a-z]', 'q'))"));
        assertEquals("VARCHAR[LOB]", answer("SELECT SYSTEM$TYPEOF(REGEXP_REPLACE('ab', '[a-z]'))"),
            "the two-argument form too");
        assertEquals("VARCHAR[LOB]",
            answer("SELECT SYSTEM$TYPEOF(REGEXP_REPLACE(g, '[a-z]', 'q')) FROM gt"),
            "and over a column, whose width it does not keep");
        assertEquals("NUMBER(18,0)[SB8]", answer("SELECT SYSTEM$TYPEOF(REGEXP_COUNT('ab', '[a-z]'))"));
        assertEquals("NUMBER(18,0)[SB8]", answer("SELECT SYSTEM$TYPEOF(REGEXP_INSTR('ab', '[a-z]'))"));
    }

    /** The values are unaffected by any of it. */
    @Test
    public void theValuesAreUnchanged() {
        assertEquals("0", answer("SELECT GROUPING(g) FROM gt GROUP BY ROLLUP(g) ORDER BY 1"));
        assertEquals("0", answer("SELECT CONDITIONAL_TRUE_EVENT(n > 1) OVER (ORDER BY n) FROM gt ORDER BY 1"));
    }
}
