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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A star under GROUP BY expands to its columns, each held to the grouped-select rule
 * (live-verified): the fully-grouped shapes return complete rows — one value per expanded
 * column, readable cell by cell — while a star covering an ungrouped column refuses with the
 * standard sentence at the sentinel position (line 0, position -1: nobody wrote the expanded
 * reference).
 */
public class GroupedStarProjectionTest extends BaseDatabaseTest {

    @BeforeEach
    public void createFixture() {
        engine.execute("CREATE TABLE gstar_orders (city VARCHAR, qty INTEGER)");
        engine.execute("INSERT INTO gstar_orders VALUES ('Berlin', 1), ('Berlin', 2), ('Oslo', 5)");
        engine.execute("CREATE TABLE gstar_wide (city VARCHAR, qty INTEGER, price INTEGER)");
        engine.execute("INSERT INTO gstar_wide VALUES ('Berlin', 1, 10)");
    }

    /** Every cell of every row, as strings — reading past a short row throws, which IS the point. */
    private List<String> allCells(final ResultSet rs) {
        final List<String> cells = new ArrayList<String>();
        for (final Row row : rs.getRows()) {
            for (int i = 0; i < rs.getColumns().size(); i++) {
                cells.add(String.valueOf(row.getValue(i)));
            }
        }
        return cells;
    }

    @Test
    public void groupedStarAloneReturnsFullRows() {
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM gstar_orders GROUP BY city, qty ORDER BY city, qty");
        assertEquals(2, rs.getColumns().size());
        assertEquals(List.of("Berlin", "1", "Berlin", "2", "Oslo", "5"), allCells(rs));
    }

    @Test
    public void groupedStarWithAggregateReturnsFullRows() {
        final ResultSet rs = engine.executeQuery(
            "SELECT *, COUNT(1) AS n FROM gstar_orders GROUP BY city, qty ORDER BY city, qty");
        assertEquals(3, rs.getColumns().size());
        assertEquals("N", rs.getColumns().get(2).getName().toUpperCase());
        assertEquals(List.of("Berlin", "1", "1", "Berlin", "2", "1", "Oslo", "5", "1"), allCells(rs));
    }

    @Test
    public void groupedStarWithWindowReturnsFullRows() {
        final ResultSet rs = engine.executeQuery(
            "SELECT *, COUNT(1) OVER () AS n FROM gstar_orders GROUP BY city, qty ORDER BY city, qty");
        assertEquals(3, rs.getColumns().size());
        assertEquals(List.of("Berlin", "1", "3", "Berlin", "2", "3", "Oslo", "5", "3"), allCells(rs));
    }

    @Test
    public void groupedQualifiedStarWithWindowReturnsFullRows() {
        final ResultSet rs = engine.executeQuery(
            "SELECT t.*, COUNT(1) OVER () AS n FROM gstar_orders t GROUP BY city, qty ORDER BY city, qty");
        assertEquals(3, rs.getColumns().size());
        assertEquals(List.of("Berlin", "1", "3", "Berlin", "2", "3", "Oslo", "5", "3"), allCells(rs));
    }

    @Test
    public void groupedStarOverUngroupedColumnIsRejectedAtTheSentinelPosition() {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "SELECT *, COUNT(1) OVER () AS n FROM gstar_wide GROUP BY city, qty");
            }
        });
        assertTrue(e.getMessage().contains("error line 0 at position -1"), e.getMessage());
        assertTrue(e.getMessage().contains(
            "'GSTAR_WIDE.PRICE' in select clause is neither an aggregate nor in the group by clause."),
            e.getMessage());
    }

    @Test
    public void groupedQualifiedStarOverUngroupedColumnNamesTheQualifier() {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "SELECT t.*, COUNT(1) OVER () AS n FROM gstar_wide t GROUP BY city, qty");
            }
        });
        assertTrue(e.getMessage().contains("error line 0 at position -1"), e.getMessage());
        assertTrue(e.getMessage().contains(
            "'T.PRICE' in select clause is neither an aggregate nor in the group by clause."),
            e.getMessage());
    }

    @Test
    public void groupByAllWithStarGroupsEveryColumn() {
        final ResultSet rs = engine.executeQuery(
            "SELECT *, COUNT(1) AS n FROM gstar_orders GROUP BY ALL ORDER BY city, qty");
        assertEquals(3, rs.getColumns().size());
        assertEquals(List.of("Berlin", "1", "1", "Berlin", "2", "1", "Oslo", "5", "1"), allCells(rs));
    }
}
