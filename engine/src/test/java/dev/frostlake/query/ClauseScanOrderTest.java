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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When TWO clauses each carry a name that resolves to nothing, which one is reported.
 *
 * <p>★ THE ORDER IS BY CLAUSE, AND IT IS THE WRITTEN ORDER OF THE STATEMENT'S HEAD:
 *
 * <pre>
 *   SELECT list  →  WHERE  →  HAVING  →  QUALIFY
 * </pre>
 *
 * <p>Each cell here writes a DIFFERENT bad name in each clause, so the reported NAME — not just the
 * position — says which clause was scanned first. That matters: the same name in both clauses can only
 * be told apart by the offset, and an offset is easy to match by accident.
 *
 * <p>The ORDER BY list is scanned with the select list rather than after QUALIFY, which is why a bad
 * name there loses to one in the list.
 */
public class ClauseScanOrderTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE qo (a INT, b INT)");
        engine.execute("INSERT INTO qo VALUES (1, 10), (2, 20)");
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String invalid(final int position, final String name) {
        return "SQL compilation error: error line 1 at position " + position
            + "|invalid identifier '" + name + "'";
    }

    /** The SELECT list outranks every other clause. */
    @Test
    public void theSelectListIsScannedFirst() {
        assertEquals(invalid(7, "BADONE"), refusal("SELECT badone FROM qo WHERE badtwo = 1"));
        assertEquals(invalid(7, "BADONE"), refusal("SELECT badone FROM qo QUALIFY badtwo > 1"));
        assertEquals(invalid(7, "BADONE"),
            refusal("SELECT badone FROM qo GROUP BY a HAVING badtwo > 1"));
        assertEquals(invalid(7, "BADONE"), refusal("SELECT badone FROM qo ORDER BY badtwo"));
    }

    /** Then WHERE, then HAVING, then QUALIFY. */
    @Test
    public void whereThenHavingThenQualify() {
        assertEquals(invalid(23, "BADONE"),
            refusal("SELECT a FROM qo WHERE badone = 1 GROUP BY a HAVING badtwo > 1"));
        assertEquals(invalid(23, "BADONE"),
            refusal("SELECT a FROM qo WHERE badone = 1 QUALIFY badtwo > 1"));
        assertEquals(invalid(35, "BADONE"),
            refusal("SELECT a FROM qo GROUP BY a HAVING badone > 1 QUALIFY badtwo > 1"));
    }

    /**
     * The same name in the select list and in QUALIFY — only the POSITION can tell these apart, which
     * is the cell the task was filed for.
     */
    @Test
    public void theSameNameInBothClausesAnchorsOnTheSelectList() {
        assertEquals(invalid(7, "NOSUCHCOL"),
            refusal("SELECT nosuchcol FROM qo QUALIFY nosuchcol > 1"));
    }

    /** A bad NAME still outranks an unknown FUNCTION, whichever clause each sits in. */
    @Test
    public void aNameStillOutranksAnUnknownFunction() {
        assertEquals(invalid(7, "BADONE"), refusal("SELECT badone FROM qo QUALIFY nosuchfn(a) > 1"));
        assertEquals(invalid(35, "BADTWO"), refusal("SELECT nosuchfn(a) FROM qo QUALIFY badtwo > 1"));
    }

    /** A FORWARD reference to a later item's alias stays an invalid identifier. */
    @Test
    public void aForwardAliasReferenceIsStillRefused() {
        assertEquals(invalid(7, "X"), refusal("SELECT x, a AS x FROM qo"));
        assertEquals(invalid(7, "X"), refusal("SELECT x, a AS x FROM qo QUALIFY badtwo > 1"));
    }
}
