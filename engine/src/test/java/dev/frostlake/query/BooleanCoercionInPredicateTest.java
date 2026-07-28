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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Snowflake implicitly coerces a value in predicate position to BOOLEAN: a VARCHAR via TO_BOOLEAN's
 * text forms ({@code 'true'/'t'/'yes'/'y'/'on'/'1'}, case-insensitive), a number as zero/non-zero.
 * A bare {@code WHERE is_direct} over a VARCHAR column holding {@code 'true'} is a real loader idiom
 * (boolean-ish flags declared VARCHAR); treating any non-Boolean as FALSE silently dropped every row.
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

    @Test
    public void aBareVarcharColumnFiltersAsAPredicate() {
        assertEquals(3, rows("SELECT id FROM t WHERE is_direct"));                       // 1, 3, 5
    }

    @Test
    public void andChainedVarcharBooleansCoerceToo() {
        assertEquals(3, rows("SELECT id FROM t WHERE id > 0 AND is_direct"));
        assertEquals(2, rows("SELECT a.id FROM t a JOIN t b ON a.id = b.id WHERE a.is_direct AND a.id < 5"));
    }

    @Test
    public void numbersCoerceAsZeroNonzeroAndNotInverts() {
        assertEquals(3, rows("SELECT id FROM t WHERE n"));                                // 1, 3, 5
        assertEquals(2, rows("SELECT id FROM t WHERE NOT is_direct"));                    // 2, 4
    }
}
