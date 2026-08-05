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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An alias REPLACES the table name: with {@code FROM r AS x}, a reference qualified by the ORIGINAL
 * name is "SQL compilation error: invalid identifier 'R.C'" — measured in the SELECT list, WHERE,
 * GROUP BY and ORDER BY, over plain, JOIN and ASOF queries alike. The alias itself, the unaliased
 * original name, and derived-table aliases keep resolving.
 *
 * <p>The SELECT-list rejection is PLAN-time (it fires over an empty table, like live's
 * compile-time error); the WHERE / GROUP BY / ORDER BY forms still need rows to flow through key
 * resolution (an ORDER BY over one row short-circuits before resolving), so the fixtures use two
 * rows.
 */
public class AliasShadowingTest extends BaseDatabaseTest {

    @BeforeEach
    public void fixtures() {
        engine.execute("CREATE TABLE sr (t INTEGER, c INTEGER)");
        engine.execute("INSERT INTO sr VALUES (1, 2), (3, 4)");
    }

    private void assertInvalid(final String sql, final String identifier) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains("invalid identifier '" + identifier + "'"),
            "expected invalid identifier '" + identifier + "', got: " + error.getMessage());
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void theOriginalNameIsInvalidInEveryClauseWhenAliased() {
        assertInvalid("SELECT sr.t FROM sr AS x", "SR.T");
        assertInvalid("SELECT x.t FROM sr AS x WHERE sr.c = 2", "SR.C");
        assertInvalid("SELECT x.t FROM sr AS x GROUP BY sr.t, x.t", "SR.T");
        assertInvalid("SELECT x.t FROM sr AS x ORDER BY sr.c", "SR.C");
    }

    /** The SELECT-list rejection is PLAN-time: it fires over an empty input too, like live. */
    @Test
    public void theSelectListRejectionFiresOverAnEmptyTable() {
        engine.execute("DELETE FROM sr");
        assertInvalid("SELECT sr.t FROM sr AS x", "SR.T");
    }

    @Test
    public void theAliasAndTheUnaliasedNameKeepResolving() {
        assertEquals(1L, ((Number) scalar("SELECT x.t FROM sr AS x WHERE x.c = 2")).longValue());
        assertEquals(1L, ((Number) scalar("SELECT sr.t FROM sr WHERE sr.c = 2")).longValue());
        assertEquals(1L, ((Number) scalar(
            "SELECT x.t FROM sr AS x GROUP BY x.t ORDER BY x.t")).longValue());
        assertEquals(1L, ((Number) scalar(
            "SELECT sub.t FROM (SELECT t FROM sr) AS sub ORDER BY sub.t")).longValue());
    }
}
