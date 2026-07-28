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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flow operator {@code ->>}: statements chain left to right, a stage reads a PRIOR stage's
 * result via a {@code $n} table reference (n counts backward — {@code $1} is the immediately
 * preceding statement, equivalent to consuming its RESULT_SCAN), and the chain's result is the
 * LAST statement's result. An error in any stage stops the chain.
 */
public class FlowOperatorTest extends BaseDatabaseTest {

    @Test
    public void showTablesChainedThroughSelects() {
        engine.execute("CREATE TABLE flow_a (id INTEGER)");
        engine.execute("CREATE TABLE flow_b (id INTEGER)");
        final ResultSet rs = engine.executeQuery("""
            show tables
            ->> select "name" from $1
            ->> select * from $1
            """);
        assertEquals(1, rs.getColumns().size());
        assertEquals("name", rs.getColumns().get(0).getName());
        assertEquals(2, rs.getRowCount());
        final Set<Object> names = new HashSet<>();
        names.add(rs.getRows().get(0).getValue(0));
        names.add(rs.getRows().get(1).getValue(0));
        assertTrue(names.contains("FLOW_A") && names.contains("FLOW_B"), "unexpected names: " + names);
    }

    @Test
    public void dollarNCountsBackward() {
        final ResultSet rs = engine.executeQuery(
            "select 1 as a ->> select 2 as b ->> select * from $2");
        assertEquals("A", rs.getColumns().get(0).getName());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void dmlRowCountsFlowThroughTheChain() {
        engine.execute("CREATE TABLE flow_dml (id INTEGER)");
        final ResultSet rs = engine.executeQuery(
            "insert into flow_dml values (1), (2) ->> select * from $1");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue(),
            "the INSERT's result set carries its row count");
    }

    @Test
    public void onlyTheLastStatementsResultIsReturned() {
        assertEquals(1, engine.execute("select 1 ->> select 2 ->> select 3").getResultSets().size());
        assertEquals(3L, ((Number) engine.executeQuery("select 1 ->> select 2 ->> select 3")
            .getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void outOfRangeAndOutOfChainReferencesFailClearly() {
        final RuntimeException range = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("select 1 ->> select * from $3");
            }
        });
        assertTrue(range.getMessage().contains("does not reference a previous statement"),
            "unexpected message: " + range.getMessage());
        final RuntimeException outside = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("select * from $1");
            }
        });
        assertTrue(outside.getMessage().contains("flow chain"),
            "unexpected message: " + outside.getMessage());
    }

    @Test
    public void plainScriptsAndPositionalReferencesAreUnaffected() {
        assertEquals(2, engine.execute("select 10; select 20;").getResultSets().size());
        assertEquals("x", engine.executeQuery("SELECT $2 FROM (VALUES (1, 'x'), (2, 'y')) ORDER BY $1")
            .getRows().get(0).getValue(0));
    }
}
