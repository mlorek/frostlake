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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A star written beside other function arguments is spliced into the list in place as the columns it names
 * — every relation in scope or the one its qualifier names, minus EXCLUDE and outside ILIKE — before any
 * arity rule, echo or evaluation sees the call. Scalars, aggregates and windows alike take it. Outside the
 * select list and GROUP BY a star argument is refused, lone or not. Every cell is live-verified.
 */
public class StarBesideArgumentsTest extends BaseDatabaseTest {

    private static final String OUTSIDE_SELECT = "Use of * as a function argument is only allowed in the SELECT clause.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE sa (a NUMBER(5,0), b NUMBER(5,0), c NUMBER(5,0))");
        engine.execute("INSERT INTO sa VALUES (1, 2, 3), (4, NULL, 6), (NULL, NULL, NULL)");
        engine.execute("CREATE TABLE sb (x NUMBER(5,0))");
        engine.execute("INSERT INTO sb VALUES (10), (20)");
    }

    /** Rows joined by " | ", columns by ", ", a BOOLEAN spelled the account's way. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int c = 0; c < row.getValues().size(); c++) {
                if (c > 0) {
                    out.append(", ");
                }
                final String text = String.valueOf(row.getValue(c));
                out.append("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)
                    ? text.toUpperCase(Locale.ROOT) : text);
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), sql + " -> " + refused.getMessage());
    }

    @Test
    public void aScalarTakesTheStarsColumnsInPlace() {
        assertEquals("TRUE, TRUE | TRUE, TRUE | TRUE, TRUE",
            rows("SELECT HASH(a, sa.*) = HASH(a, a, b, c), HASH(sa.*, sa.*) = HASH(a, b, c, a, b, c) FROM sa ORDER BY a"));
        assertEquals("TRUE, TRUE | TRUE, TRUE | TRUE, TRUE",
            rows("SELECT HASH(a, * EXCLUDE c) = HASH(a, a, b), HASH(a, * ILIKE 'b%') = HASH(a, b) FROM sa ORDER BY a"));
        assertEquals("[1,1,2,3], [1,2,3,1], 4 | [4,4,undefined,6], [4,undefined,6,1], 4"
                + " | [undefined,undefined,undefined,undefined], [undefined,undefined,undefined,1], 4",
            rows("SELECT ARRAY_CONSTRUCT(a, sa.*), ARRAY_CONSTRUCT(sa.*, 1), ARRAY_SIZE(ARRAY_CONSTRUCT(a, sa.*)) FROM sa ORDER BY a"));
        assertEquals("1123, 3, 1,2,3 | null, null, null | null, null, null",
            rows("SELECT CONCAT(a, sa.*), GREATEST(sa.*, 0), CONCAT_WS(',', sa.*) FROM sa ORDER BY a"));
        assertEquals("[1] | [4] | [undefined]", rows("SELECT ARRAY_CONSTRUCT(a, * EXCLUDE (a, b, c)) FROM sa ORDER BY a"));
        assertEquals("[1,1,2,3] | [4,4,undefined,6] | [undefined,undefined,undefined,undefined]",
            rows("SELECT ARRAY_CONSTRUCT(a, s.*) FROM sa s ORDER BY a"));
        assertEquals("1, 10, [1,10], [10,1,2,3,10] | 1, 20, [1,20], [20,1,2,3,20]", rows(
            "SELECT sa.a, x, ARRAY_CONSTRUCT(sa.a, sb.*), ARRAY_CONSTRUCT(x, *) FROM sa, sb WHERE sa.a = 1 ORDER BY x"));
    }

    @Test
    public void anAggregateAndAWindowTakeItToo() {
        assertEquals("1, 1, 1, TRUE", rows("""
            SELECT COUNT(a, sa.*), COUNT(DISTINCT a, sa.*), APPROX_COUNT_DISTINCT(a, * EXCLUDE c),
                HASH_AGG(a, sa.*) = HASH_AGG(a, a, b, c) FROM sa"""));
        assertEquals("1, 1, 1",
            rows("SELECT APPROX_COUNT_DISTINCT(a, sa.*), APPROX_COUNT_DISTINCT(sa.*, a), APPROX_COUNT_DISTINCT(a, *) FROM sa"));
        assertEquals("1", rows("SELECT COUNT(DISTINCT sa.*, a) FROM sa"));
        assertEquals("2, 1, TRUE | null, 0, TRUE",
            rows("SELECT b, COUNT(a, sa.*), HASH_AGG(a, sa.*) = HASH_AGG(a, a, b, c) FROM sa GROUP BY b ORDER BY b"));
        assertEquals("1, 1, [1,1,2,3] | 4, 1, [4,4,undefined,6] | null, 1, [undefined,undefined,undefined,undefined]",
            rows("SELECT a, COUNT(a, sa.*) OVER (), ARRAY_CONSTRUCT(a, sa.*) FROM sa ORDER BY a"));
        assertEquals("1 | 1 | 1", rows("SELECT COUNT(*) FROM sa GROUP BY HASH(a, sa.*)"));
    }

    @Test
    public void theArityRulesCountTheSplicedList() {
        assertRefused("SELECT MOD(a, sa.*) FROM sa",
            "too many arguments for function [MOD(SA.A, SA.A, SA.B, SA.C)] expected 2, got 4");
        assertRefused("SELECT ARRAY_AGG(a, sa.*) FROM sa",
            "too many arguments for function [ARRAY_AGG(SA.A, SA.A, SA.B, SA.C)] expected 1, got 4");
        assertRefused("SELECT OBJECT_CONSTRUCT('k', a, sa.*) FROM sa ORDER BY a",
            "invalid number of arguments for [OBJECT_CONSTRUCT], expected 6, got 5");
        assertRefused("SELECT ARRAY_CONSTRUCT(a, sa.*) FROM sa s", "Object 'SA' does not exist or not authorized.");
        // Without a star, too: an odd count is refused for its arity before any key type is judged.
        assertRefused("SELECT OBJECT_CONSTRUCT('k', a, b) FROM sa",
            "invalid number of arguments for [OBJECT_CONSTRUCT], expected 4, got 3");
        assertRefused("SELECT OBJECT_CONSTRUCT_KEEP_NULL('k', a, b) FROM sa",
            "invalid number of arguments for [OBJECT_CONSTRUCT_KEEP_NULL], expected 4, got 3");
        assertRefused("SELECT OBJECT_CONSTRUCT(a, 1) FROM sa",
            "Function OBJECT_CONSTRUCT does not support NUMBER(5,0) argument type for keys");
    }

    @Test
    public void outsideTheSelectListAStarArgumentIsRefused() {
        for (final String sql : List.of(
                "SELECT a FROM sa WHERE HASH(*) = 1",
                "SELECT a FROM sa WHERE HASH(a, *) = 1",
                "SELECT a FROM sa WHERE ARRAY_SIZE(ARRAY_CONSTRUCT(a, sa.*)) = 4",
                "SELECT a FROM sa ORDER BY HASH(a, sa.*)",
                "SELECT a FROM sa ORDER BY HASH(*)",
                "SELECT COUNT(*) FROM sa HAVING HASH_AGG(a, sa.*) = 1",
                "SELECT COUNT(*) FROM sa HAVING COUNT(a, sa.*) = 1",
                "SELECT a FROM sa QUALIFY COUNT(a, sa.*) OVER () = 1")) {
            assertRefused(sql, OUTSIDE_SELECT);
        }
        assertRefused("SELECT a FROM sa WHERE COUNT(*) > 0", "Invalid aggregate function in where clause [COUNT(*)]");
    }
}
