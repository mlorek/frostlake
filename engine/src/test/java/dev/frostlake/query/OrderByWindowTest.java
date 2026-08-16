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
 * A window call may be the ORDER BY key itself, without appearing in the SELECT list. Frostlake used to
 * REFUSE every such query — the whole call TEXT reached the column resolver, so
 * {@code ORDER BY ROW_NUMBER() OVER (ORDER BY b)} came back "invalid identifier
 * 'ROW_NUMBER() OVER (ORDER BY b)'" — which is the worst direction: valid SQL a real account runs.
 *
 * <p>The cause was upstream of the sort. Whether a query needs the window stage was decided from the
 * SELECT clause alone, and ORDER BY hangs off the STATEMENT, so a window written only there left the
 * stage switched off and the key was never computed by anything that understands partitions.
 *
 * <p>The fixture orders differently by each key, so the assertions cannot pass by accident:
 * {@code a} is 3, 1, 2 while {@code b} ascends with the insertion order.
 */
public class OrderByWindowTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE g (a INT, b INT)");
        engine.execute("INSERT INTO g VALUES (3, 10), (1, 20), (2, 30)");
    }

    /** The first column of every row, in the order returned. */
    private String order(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        while (rs.next()) {
            if (text.length() > 0) {
                text.append(",");
            }
            text.append(String.valueOf(rs.getValue(0)));
        }
        return text.toString();
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    /** A window call sorts the result, ascending or descending, whatever the function. */
    @Test
    public void aWindowCallCanBeTheSortKey() {
        assertEquals("3,1,2", order("SELECT a FROM g ORDER BY ROW_NUMBER() OVER (ORDER BY b)"));
        assertEquals("2,1,3", order("SELECT a FROM g ORDER BY ROW_NUMBER() OVER (ORDER BY b) DESC"));
        assertEquals("3,2,1", order("SELECT a FROM g ORDER BY LAG(a) OVER (ORDER BY b) NULLS FIRST"));
        assertEquals("3,1,2", order("SELECT a FROM g ORDER BY SUM(b) OVER (PARTITION BY a)"));
    }

    /** It mixes with ordinary keys, on either side of them. */
    @Test
    public void itMixesWithOrdinaryKeys() {
        assertEquals("3,1,2", order("SELECT a FROM g ORDER BY RANK() OVER (ORDER BY b), a"));
        assertEquals("1,2,3", order("SELECT a FROM g ORDER BY a, RANK() OVER (ORDER BY b)"));
    }

    /** And it may sit INSIDE a larger key expression, which is evaluated around the window's value. */
    @Test
    public void itMaySitInsideALargerExpression() {
        assertEquals("3,1,2", order("SELECT a FROM g ORDER BY ROW_NUMBER() OVER (ORDER BY b) + 1"));
    }

    /** A QUALIFY window and an ORDER BY window coexist. */
    @Test
    public void itCoexistsWithQualify() {
        assertEquals("3,1,2", order("SELECT a FROM g"
            + " QUALIFY ROW_NUMBER() OVER (ORDER BY b) > 0 ORDER BY RANK() OVER (ORDER BY b)"));
    }

    /** The keys inside its OVER clause are checked like any others, at the reference's own offset. */
    @Test
    public void theWindowsOwnKeysAreStillChecked() {
        assertTrue(refusal("SELECT a FROM g ORDER BY ROW_NUMBER() OVER (ORDER BY nosuchcol)")
            .contains("error line 1 at position 53 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT a FROM g ORDER BY ROW_NUMBER() OVER (ORDER BY nosuchcol)"));
    }

    /** The alias forms, which always worked, still do — including under DISTINCT. */
    @Test
    public void theAliasFormsStillWork() {
        assertEquals("3,1,2", order("SELECT a, ROW_NUMBER() OVER (ORDER BY b) r FROM g ORDER BY r"));
        assertEquals("1,2,3",
            order("SELECT DISTINCT a, ROW_NUMBER() OVER (ORDER BY a) r FROM g ORDER BY r"));
    }
}
