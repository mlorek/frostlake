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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Parenthesized FROM join — {@code FROM ( a JOIN b ON c )}. The parentheses are pure grouping: the query is
 * identical to {@code FROM a JOIN b ON c}, and (unlike a derived-table subquery) the inner table aliases stay
 * visible to the outer WHERE / SELECT. The engine flattens the grouping into the ordinary join pipeline.
 */
public class ParenthesizedFromJoinTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE orders (order_id INTEGER, cust_id INTEGER, status VARCHAR)");
        engine.execute("CREATE TABLE ship (order_id INTEGER, cust_id INTEGER, dest INTEGER)");
        engine.execute("INSERT INTO orders VALUES (1, 100, 'OPEN'), (2, 100, 'OPEN'), (3, 100, 'CLOSED')");
        engine.execute("INSERT INTO ship VALUES (1, 100, 5), (2, 100, 8)");
    }

    private long count(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void parenthesizedJoinKeepsInnerAliasesVisibleToOuterWhere() {
        // The real-world shape: inner aliases o / s are referenced in the OUTER WHERE and SELECT.
        final ResultSet rs = engine.executeQuery("""
            SELECT s.dest AS d, o.order_id AS oid
            FROM ( orders o
                   JOIN ship s ON s.order_id = o.order_id AND s.cust_id = o.cust_id )
            WHERE o.status = 'OPEN' AND s.dest > 6""");
        assertEquals(1, rs.getRowCount());
        assertEquals(8, ((Number) rs.getRows().get(0).getValue(0)).intValue()); // s.dest
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(1)).intValue()); // o.order_id
    }

    @Test
    public void parenthesizedJoinEqualsUnparenthesizedJoin() {
        final String paren = """
            SELECT o.order_id FROM ( orders o JOIN ship s ON s.order_id = o.order_id )
            WHERE o.status = 'OPEN' ORDER BY o.order_id""";
        final String plain = """
            SELECT o.order_id FROM orders o JOIN ship s ON s.order_id = o.order_id
            WHERE o.status = 'OPEN' ORDER BY o.order_id""";
        final ResultSet rp = engine.executeQuery(paren);
        final ResultSet rn = engine.executeQuery(plain);
        assertEquals(rn.getRowCount(), rp.getRowCount());
        assertEquals(2, rp.getRowCount());
        for (int i = 0; i < rp.getRowCount(); i++) {
            assertEquals(rn.getRows().get(i).getValue(0), rp.getRows().get(i).getValue(0));
        }
    }

    @Test
    public void nestedGroupingFlattens() {
        // ((a JOIN b) JOIN c) — grouped nesting must flatten recursively.
        assertEquals(2, count("""
            SELECT COUNT(*) FROM ( ( orders o JOIN ship s ON o.order_id = s.order_id )
                                   JOIN orders o2 ON o2.order_id = o.order_id )"""));
    }

    @Test
    public void plainSubqueryStillParses() {
        // Regression: the (SELECT ...) derived-table form must be unaffected by the new grammar alternative.
        assertEquals(2, count("SELECT COUNT(*) FROM (SELECT order_id FROM orders WHERE status = 'OPEN')"));
    }

    @Test
    public void parenthesizedJoinAsRightSideOfOuterJoinFailsClearly() {
        // Not yet supported: a parenthesized join nested on the RIGHT of another join needs the inner
        // aliases exposed to the outer ON. It must fail with a clear message, not an NPE.
        final RuntimeException ex = assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                engine.executeQuery("""
                    SELECT COUNT(*) FROM orders o
                    JOIN ( ship s JOIN orders o2 ON s.order_id = o2.order_id ) ON o.order_id = s.order_id""");
            }
        });
        assertEquals(true, ex.getMessage() != null && ex.getMessage().contains("parenthesized join"));
    }
}
