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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ORDER BY scope in GROUPED and WINDOW queries — the path whose unmatched keys resolve per source
 * group — measured cell by cell on a real account. Rejections are compile-time (they fire over
 * EMPTY inputs): an invalid qualifier and an unknown name are "invalid identifier" — inside
 * aggregate arguments too, and in window queries alike — while a bare FROM column that is neither
 * a group key nor inside an aggregate has its own sentence, "[R.V] is not a valid order by
 * expression". Legal and value-checked: grouped columns NOT in the SELECT list, aggregates NOT in
 * the SELECT list, output aliases, and expressions over any of these ({@code ORDER BY k + 1},
 * {@code max(v) + 1}, {@code m + 1} over an aggregate's alias).
 */
public class GroupedOrderByScopeTest extends BaseDatabaseTest {

    @BeforeEach
    public void fixtures() {
        engine.execute("CREATE TABLE r (k INTEGER, v INTEGER)");
    }

    private void assertRejected(final String sql, final String expectedFragment) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains(expectedFragment),
            "expected \"" + expectedFragment + "\", got: " + error.getMessage());
    }

    /** All rows rendered as {@code [c0|c1|…]} joined with spaces. */
    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append('[');
            for (int i = 0; i < rs.getColumns().size(); i++) {
                if (i > 0) {
                    out.append('|');
                }
                out.append(row.getValue(i));
            }
            out.append(']');
        }
        return out.toString();
    }

    @Test
    public void groupedOrderByRejectsScopeErrorsOverEmptyInputs() {
        assertRejected("SELECT max(x.v) FROM r AS x GROUP BY x.k ORDER BY r.k",
            "invalid identifier 'R.K'");
        assertRejected("SELECT max(v) FROM r GROUP BY k ORDER BY nosuch",
            "invalid identifier 'NOSUCH'");
        assertRejected("SELECT k FROM r GROUP BY k ORDER BY max(nosuch)",
            "invalid identifier 'NOSUCH'");
    }

    @Test
    public void ungroupedBareColumnHasItsOwnSentence() {
        assertRejected("SELECT k, max(v) FROM r GROUP BY k ORDER BY v",
            "[R.V] is not a valid order by expression");
    }

    @Test
    public void windowQueryOrderByRejectsScopeErrorsOverEmptyInputs() {
        assertRejected("SELECT k, ROW_NUMBER() OVER (ORDER BY v) FROM r ORDER BY nosuch",
            "invalid identifier 'NOSUCH'");
        assertRejected("SELECT x.k, ROW_NUMBER() OVER (ORDER BY x.v) FROM r AS x ORDER BY r.v",
            "invalid identifier 'R.V'");
    }

    @Test
    public void groupedColumnsAndAggregatesNotInTheSelectListStillOrder() {
        engine.execute("INSERT INTO r VALUES (1, 10), (1, 30), (2, 20)");
        assertEquals("[30] [20]", rows("SELECT max(v) FROM r GROUP BY k ORDER BY k"));
        assertEquals("[1] [2]", rows("SELECT k FROM r GROUP BY k ORDER BY max(v) DESC"));
    }

    @Test
    public void expressionsOverGroupKeysAliasesAndAggregatesOrder() {
        engine.execute("INSERT INTO r VALUES (1, 10), (1, 30), (2, 20)");
        assertEquals("[2|20] [1|30]", rows(
            "SELECT k, max(v) FROM r GROUP BY k ORDER BY k + 1 DESC"));
        assertEquals("[2] [1]", rows(
            "SELECT k FROM r GROUP BY k ORDER BY max(v) + 1"));
        assertEquals("[1|30] [2|20]", rows(
            "SELECT k, max(v) AS m FROM r GROUP BY k ORDER BY m + 1 DESC"));
    }
}
