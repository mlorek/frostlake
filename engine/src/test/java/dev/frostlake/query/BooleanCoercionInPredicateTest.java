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
 * Snowflake type-checks predicate position at compile time: a bare column whose static type is
 * VARCHAR or NUMBER is rejected with {@code Invalid data type [VARCHAR(16777216)] for predicate
 * [IS_DIRECT]} — the predicate itself is never implicitly TO_BOOLEAN'ed. Operands nested under a
 * boolean operator (AND/NOT) still coerce through TO_BOOLEAN's text forms
 * ({@code 'true'/'t'/'yes'/'y'/'on'/'1'}, case-insensitive; numbers as zero/non-zero), and the
 * portable idioms are an explicit comparison or an explicit TO_BOOLEAN call.
 */
public class BooleanCoercionInPredicateTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE t (id INTEGER, is_direct VARCHAR, n INTEGER)");
        engine.execute("INSERT INTO t SELECT 1, True, 1 UNION ALL SELECT 2, False, 0"
            + " UNION ALL SELECT 3, 'true', 5 UNION ALL SELECT 4, 'FALSE', 0 UNION ALL SELECT 5, 'Y', 2");
    }

    private int rows(final String sql) {
        return engine.executeQuery(sql).getRowCount();
    }

    private void assertPredicateRejected(final String sql, final String expectedTypePrefix) {
        final RuntimeException rejected = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(rejected.getMessage().contains("Invalid data type")
                && rejected.getMessage().contains("[" + expectedTypePrefix)
                && rejected.getMessage().contains("for predicate"),
            "unexpected: " + rejected.getMessage());
    }

    @Test
    public void aBareVarcharColumnPredicateIsRejected() {
        assertPredicateRejected("SELECT id FROM t WHERE is_direct", "VARCHAR(");
    }

    @Test
    public void aBareNumberColumnPredicateIsRejected() {
        assertPredicateRejected("SELECT id FROM t WHERE n", "NUMBER(");
    }

    @Test
    public void andChainedVarcharBooleansCoerceToo() {
        assertEquals(3, rows("SELECT id FROM t WHERE id > 0 AND is_direct"));
        assertEquals(2, rows("SELECT a.id FROM t a JOIN t b ON a.id = b.id WHERE a.is_direct AND a.id < 5"));
    }

    @Test
    public void notOverAVarcharFlagStillCoerces() {
        assertEquals(2, rows("SELECT id FROM t WHERE NOT is_direct"));                    // 2, 4
    }

    @Test
    public void explicitFormsFilterRows() {
        // The seed's UNION ALL takes its column type from the FIRST branch — a BOOLEAN literal — so
        // every later branch's string is converted by TO_BOOLEAN before it reaches the VARCHAR column:
        // 'Y' lands as 'true'. Live-verified on a real account: the stored flags are
        // true/false/true/false/true, so BOTH forms below select 1, 3 and 5.
        assertEquals(3, rows("SELECT id FROM t WHERE is_direct = 'true'"));               // 1, 3, 5
        assertEquals(3, rows("SELECT id FROM t WHERE TO_BOOLEAN(is_direct)"));            // 1, 3, 5
    }
}
