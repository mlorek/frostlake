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

/**
 * MIN and MAX over a column whose rows carry different numeric classes. A conditional over two widths
 * ({@code COALESCE} of a NUMBER(3,1) and a NUMBER(3,0)) and a set operation's arms both leave a Long
 * beside a BigDecimal in one derived column, and the grouped scan's MIN/MAX fast path used to compare
 * them with a raw {@code Comparable} — a ClassCastException that the old blanket catch turned into a
 * NULL cell. Live-verified: the extreme is the numeric one, whatever the carriers, grouped or bare.
 */
public class ExtremeOverMixedCarriersTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE mx (g VARCHAR(2), a NUMBER(3,1), b NUMBER(3,0))");
        engine.execute("INSERT INTO mx SELECT 'x', 4.2, NULL");
        engine.execute("INSERT INTO mx SELECT 'x', NULL, 5");
        engine.execute("INSERT INTO mx SELECT 'y', 1.5, NULL");
    }

    private String cells(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder out = new StringBuilder();
            while (rs.next()) {
                if (out.length() > 0) {
                    out.append(" | ");
                }
                for (int i = 0; i < rs.getColumns().size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    out.append(String.valueOf(rs.getValue(i)));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    void groupedExtremesOverAConditionalColumn() {
        assertEquals("x, 5.0, 4.2 | y, 1.5, 1.5", cells("""
            SELECT g, MAX(c)::NUMBER(4,1), MIN(c)::NUMBER(4,1)
            FROM (SELECT g, COALESCE(a, b) AS c FROM mx)
            GROUP BY g ORDER BY g"""));
        assertEquals("x, value, value | y, value, value", cells("""
            SELECT g, IFF(MAX(c) IS NULL, 'null', 'value'), IFF(MIN(c) IS NULL, 'null', 'value')
            FROM (SELECT g, COALESCE(a, b) AS c FROM mx)
            GROUP BY g ORDER BY g"""));
    }

    @Test
    void bareExtremesOverAConditionalColumn() {
        assertEquals("5.0, 1.5", cells(
            "SELECT MAX(c)::NUMBER(4,1), MIN(c)::NUMBER(4,1) FROM (SELECT COALESCE(a, b) AS c FROM mx)"));
    }

    @Test
    void extremesOverSetOperationArms() {
        assertEquals("9.0, 4.4", cells(
            "SELECT MAX(s)::NUMBER(4,1), MIN(s)::NUMBER(4,1) FROM (SELECT 9 AS s UNION ALL SELECT 4.4)"));
        assertEquals("9.0, 4.4", cells(
            "SELECT MAX(s)::NUMBER(4,1), MIN(s)::NUMBER(4,1) FROM (SELECT 4.4 AS s UNION ALL SELECT 9)"));
        assertEquals("a, 9.0, 4.4 | b, 7.0, 7.0", cells("""
            SELECT k, MAX(s)::NUMBER(4,1), MIN(s)::NUMBER(4,1)
            FROM (SELECT 'a' AS k, 9 AS s UNION ALL SELECT 'a', 4.4 UNION ALL SELECT 'b', 7)
            GROUP BY k ORDER BY k"""));
    }
}
