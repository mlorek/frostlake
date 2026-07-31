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
 * Whether a bare column name carried by BOTH sides of a join is ambiguous depends on the JOIN FORM
 * — measured cell by cell on a real account: an ON join rejects the duplicate with "SQL
 * compilation error: ambiguous column name 'D'", inner and left alike, in the SELECT list and in
 * WHERE; a USING (or NATURAL) join instead resolves EVERY same-named pair to the LEFT side — keys
 * and non-keys both — so {@code SELECT d FROM a JOIN b USING (k)} answers a.d where the ON
 * spelling of the same join is rejected. ORDER BY resolves against the SELECT output first and so
 * never trips either way. The integration loaders lean on the USING form heavily, which is what
 * exposed the rule.
 *
 * <p>The SELECT-list rejection is PLAN-time (it fires over an empty join, like live's
 * compile-time error); the WHERE form still needs rows to flow through resolution, so those tests
 * use matching rows.
 */
public class AmbiguousColumnTest extends BaseDatabaseTest {

    @BeforeEach
    public void fixtures() {
        engine.execute("CREATE TABLE aa (k INTEGER, d INTEGER)");
        engine.execute("INSERT INTO aa VALUES (1, 100), (2, 200)");
        engine.execute("CREATE TABLE bb (k INTEGER, d INTEGER)");
        engine.execute("INSERT INTO bb VALUES (1, 999)");
    }

    private void assertAmbiguous(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains("ambiguous column name 'D'"),
            "expected the ambiguity error, got: " + error.getMessage());
    }

    /** All first-column values in row order, joined with {@code |}. */
    private String columnValues(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder joined = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (joined.length() > 0) {
                joined.append('|');
            }
            joined.append(row.getValue(0));
        }
        return joined.toString();
    }

    @Test
    public void anOnJoinRejectsTheBareDuplicate() {
        assertAmbiguous("SELECT d FROM aa JOIN bb ON aa.k = bb.k");
        assertAmbiguous("SELECT d FROM aa LEFT JOIN bb ON aa.k = bb.k");
        assertAmbiguous("SELECT aa.k FROM aa JOIN bb ON aa.k = bb.k WHERE d = 100");
    }

    @Test
    public void aUsingJoinResolvesEveryDuplicateToTheLeftSide() {
        // Non-key duplicate: a.d wins — 100, not b's 999.
        assertEquals("100", columnValues("SELECT d FROM aa JOIN bb USING (k) ORDER BY d"));
        assertEquals("100|200", columnValues("SELECT d FROM aa LEFT JOIN bb USING (k) ORDER BY 1"));
        assertEquals("1|2", columnValues(
            "SELECT k FROM aa LEFT JOIN bb USING (k) WHERE d > 0 ORDER BY 1"));
        // The integration-loader shape: a CTE side of a USING left join.
        assertEquals("100|200", columnValues("WITH gf AS (SELECT k, d FROM aa)"
            + " SELECT d FROM gf LEFT JOIN bb USING (k) ORDER BY 1"));
    }

    /** The SELECT-list ambiguity is PLAN-time: it fires even when the join matches no rows. */
    @Test
    public void theSelectListAmbiguityFiresOverAnEmptyJoin() {
        assertAmbiguous("SELECT d FROM aa JOIN bb ON aa.d = bb.d");
    }

    /** ORDER BY resolves against the SELECT output first, so the bare name never trips there. */
    @Test
    public void orderByResolvesAgainstTheSelectOutputFirst() {
        assertEquals("100", columnValues("SELECT aa.d FROM aa JOIN bb ON aa.k = bb.k ORDER BY d"));
    }

    /** A bare name carried by only ONE side, and any qualified reference, resolve as before. */
    @Test
    public void uniqueBareAndQualifiedNamesStillResolve() {
        assertEquals("1", columnValues("SELECT k FROM aa JOIN bb USING (k) ORDER BY 1"));
        assertEquals("999", columnValues("SELECT bb.d FROM aa JOIN bb ON aa.k = bb.k"));
        assertEquals("100", columnValues("SELECT aa.d FROM aa JOIN bb ON aa.k = bb.k"));
    }
}
