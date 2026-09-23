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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The HyperLogLog STATE family — {@code HLL_ACCUMULATE}, {@code HLL_COMBINE}, {@code HLL_ESTIMATE},
 * {@code HLL_EXPORT} and {@code HLL_IMPORT} — which lets a sketch be built once, rolled up, and read for
 * its cardinality later.
 *
 * <p>★ A STATE IS A BINARY and an estimate read out of one is NUMBER(18,0), the width the approximate
 * count declares. {@code HLL_ESTIMATE(HLL_ACCUMULATE(x))} answers exactly what
 * {@code APPROX_COUNT_DISTINCT(x)} answers, as on the account, because it is the same sketch.
 *
 * <p>★ A GROUP WITH NO VALUE accumulates to an EMPTY state, whose estimate is 0 rather than NULL, and a
 * NULL state estimates NULL.
 *
 * <p>★ THE EXPORTED OBJECT IS THE INTERCHANGE FORM: {@code {"precision": p, "sparse": {"indices": […],
 * "maxLzCounts": […]}, "version": 4}} while few registers are set, {@code {"dense": […], …}} once many
 * are, and IMPORT reads either back — including one the ACCOUNT exported, which is read at the precision
 * it declares. The account's own three-value state, imported here, estimates 3.
 *
 * <p>The STATE'S BYTES are this engine's: which registers a value lands in depends on the hash, and the
 * account's hash is not reproduced, so a state written here does not match one written there byte for
 * byte. The estimates, the shapes and the object are what travel.
 */
public class HllStateFamilyTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE hz (a INT, b INT)");
        engine.execute("INSERT INTO hz VALUES (1, 10), (2, 20), (3, 30), (3, 30)");
    }

    /** The first cell, as text; a refusal comes back with its lines joined. */
    private String cell(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return rs.next() ? String.valueOf(rs.getValue(0)) : "<no row>";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** ★ A state estimates what the approximate count answers. */
    @Test
    public void aStateEstimatesWhatTheApproximateCountAnswers() {
        assertEquals("3", cell("SELECT HLL_ESTIMATE(HLL_ACCUMULATE(a)) FROM hz"));
        assertEquals(cell("SELECT APPROX_COUNT_DISTINCT(a) FROM hz"),
            cell("SELECT HLL_ESTIMATE(HLL_ACCUMULATE(a)) FROM hz"));
        assertEquals("3", cell("SELECT HLL_ESTIMATE(HLL_ACCUMULATE(a, b)) FROM hz"),
            "a tuple is counted as the approximate count counts one");
        assertEquals("NUMBER(18,0)[SB8]",
            cell("SELECT SYSTEM$TYPEOF(HLL_ESTIMATE(HLL_ACCUMULATE(a))) FROM hz"));
        assertEquals("BINARY[LOB]", cell("SELECT SYSTEM$TYPEOF(HLL_ACCUMULATE(a)) FROM hz"));
        assertEquals("BINARY[LOB]",
            cell("SELECT SYSTEM$TYPEOF(HLL_COMBINE(s)) FROM (SELECT HLL_ACCUMULATE(a) AS s FROM hz)"));
    }

    /** ★ An empty group is an empty state, and NULL stays NULL. */
    @Test
    public void anEmptyGroupIsAnEmptyState() {
        assertEquals("0", cell("SELECT HLL_ESTIMATE(HLL_ACCUMULATE(a)) FROM hz WHERE a > 100"));
        assertEquals("0", cell("SELECT HLL_ESTIMATE(HLL_ACCUMULATE(c)) FROM (SELECT NULL AS c)"),
            "a group of nothing but NULL counts nothing");
        assertEquals("null", cell("SELECT HLL_ESTIMATE(NULL)"));
    }

    /** ★ A state rolls up: the combined state is the state of the union. */
    @Test
    public void statesRollUp() {
        assertEquals("3", cell("SELECT HLL_ESTIMATE(HLL_COMBINE(s))"
            + " FROM (SELECT HLL_ACCUMULATE(a) AS s FROM hz GROUP BY a)"));
        assertEquals("3", cell("SELECT HLL_ESTIMATE(HLL_COMBINE(s)) FROM ("
            + "SELECT HLL_ACCUMULATE(a) AS s FROM hz WHERE a < 3"
            + " UNION ALL SELECT HLL_ACCUMULATE(a) FROM hz WHERE a >= 3)"),
            "two halves of the same table combine back to the whole");
        assertEquals("0", cell("SELECT HLL_ESTIMATE(HLL_COMBINE(s))"
            + " FROM (SELECT HLL_ACCUMULATE(a) AS s FROM hz WHERE a > 100)"));
    }

    /** ★ The exported object is the state, and IMPORT reads it back. */
    @Test
    public void theExportedObjectRoundTrips() {
        final String exported = cell("SELECT HLL_EXPORT(HLL_ACCUMULATE(a)) FROM hz");
        assertTrue(exported.contains("\"precision\""), exported);
        assertTrue(exported.contains("\"sparse\""), exported);
        assertTrue(exported.contains("\"indices\""), exported);
        assertTrue(exported.contains("\"maxLzCounts\""), exported);
        assertTrue(exported.contains("\"version\":4"), exported);
        assertEquals("3", cell("SELECT HLL_ESTIMATE(HLL_IMPORT(HLL_EXPORT(HLL_ACCUMULATE(a)))) FROM hz"));
    }

    /** ★ A state the ACCOUNT exported imports here, at the precision it declares. */
    @Test
    public void anAccountsExportedStateImports() {
        assertEquals("3", cell("SELECT HLL_ESTIMATE(HLL_IMPORT(PARSE_JSON("
            + "'{\"precision\":12,\"sparse\":{\"indices\":[43,3182,4066],\"maxLzCounts\":[1,2,2]},"
            + "\"version\":4}')))"));
        assertEquals("BINARY[LOB]", cell("SELECT SYSTEM$TYPEOF(HLL_IMPORT(PARSE_JSON("
            + "'{\"precision\":12,\"sparse\":{\"indices\":[43],\"maxLzCounts\":[1]},\"version\":4}')))"));
        assertEquals("1", cell("SELECT HLL_ESTIMATE(HLL_IMPORT(PARSE_JSON("
            + "'{\"precision\":12,\"sparse\":{\"indices\":[43],\"maxLzCounts\":[1]},\"version\":4}')))"));
    }

    /** A text argument is no state, and is refused by its type at the call. */
    @Test
    public void aTextArgumentIsRefusedByItsType() {
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for"
            + " function 'HLL_IMPORT': (VARCHAR(8))", cell("SELECT HLL_IMPORT('nonsense')"));
    }
}
