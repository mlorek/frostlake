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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * LATERAL over a table function, correlating to a column of a preceding table.
 *
 * <p><b>LATERAL takes the BARE call.</b> {@code FROM t, LATERAL FLATTEN(…)} and
 * {@code FROM t, TABLE(FLATTEN(…))} are alternatives; {@code LATERAL TABLE(FLATTEN(…))} is a syntax
 * error that gets no further than the keyword. This class was written entirely in that third
 * spelling, which Frostlake accepted and no account parses — so every query here is also a test that
 * the supported form is the one being exercised.
 *
 * <p>FLATTEN's INPUT is a VARIANT column for the same reason: a VARCHAR one is refused live, however
 * much its text looks like JSON.
 *
 * <p><b>GENERATOR's ROWCOUNT is not correlatable.</b> Snowflake answers "argument 1 to function
 * GENERATOR needs to be constant" unless its optimiser can fold the value — a one-row stored table or
 * {@code (SELECT 2 AS n)} folds and IS accepted, while two rows, zero rows, or a {@code LIMIT 1} over
 * a larger table are all refused. That is a property of the planner, not of the language, so
 * Frostlake stays permissive rather than guessing at it; the row-multiplication tests below use the
 * spelling every account runs instead — a join to a numbers set built from a CONSTANT rowcount.
 */
public class LateralTableFunctionTest extends BaseDatabaseTest {

    @Test
    public void testLateralWithGeneratorSimple() {
        engine.execute("CREATE TABLE numbers (n INTEGER)");
        engine.execute("INSERT INTO numbers VALUES (2)");
        engine.execute("INSERT INTO numbers VALUES (3)");

        final ResultSet result = engine.executeQuery("""
            SELECT n.n
            FROM numbers n
            JOIN (SELECT SEQ4() AS k FROM TABLE(GENERATOR(ROWCOUNT => 10))) g ON g.k < n.n
            ORDER BY n.n
            """);

        assertNotNull(result);
        assertEquals(5, result.getRowCount());  // 2 + 3 = 5 rows total

        // The generator carries no columns (Snowflake shape); each source row repeats n times.
        assertEquals(2L, result.getRows().get(0).getValue(0));
        assertEquals(2L, result.getRows().get(1).getValue(0));
        assertEquals(3L, result.getRows().get(2).getValue(0));
        assertEquals(3L, result.getRows().get(3).getValue(0));
        assertEquals(3L, result.getRows().get(4).getValue(0));
    }

    @Test
    public void testLateralWithSplitToTable() {
        engine.execute("CREATE TABLE data (id INTEGER, tag_list VARCHAR)");
        engine.execute("INSERT INTO data VALUES (1, 'red,blue,green')");
        engine.execute("INSERT INTO data VALUES (2, 'small,medium')");

        final ResultSet result = engine.executeQuery("""
            SELECT d.id, s.VALUE as tag
            FROM data d, LATERAL SPLIT_TO_TABLE(d.tag_list, ',') s
            ORDER BY d.id, s.INDEX
            """);

        assertNotNull(result);
        assertEquals(5, result.getRowCount());

        // ID 1 should have 3 tags
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals("red", result.getRows().get(0).getValue(1));
        assertEquals(1L, result.getRows().get(1).getValue(0));
        assertEquals("blue", result.getRows().get(1).getValue(1));
        assertEquals(1L, result.getRows().get(2).getValue(0));
        assertEquals("green", result.getRows().get(2).getValue(1));

        // ID 2 should have 2 tags
        assertEquals(2L, result.getRows().get(3).getValue(0));
        assertEquals("small", result.getRows().get(3).getValue(1));
        assertEquals(2L, result.getRows().get(4).getValue(0));
        assertEquals("medium", result.getRows().get(4).getValue(1));
    }

    @Test
    public void testLateralWithFlatten() {
        engine.execute("CREATE TABLE documents (id INTEGER, json_data VARIANT)");
        engine.execute("INSERT INTO documents SELECT 1, PARSE_JSON('{\"a\":1,\"b\":2}')");
        engine.execute("INSERT INTO documents SELECT 2, PARSE_JSON('{\"x\":10}')");

        final ResultSet result = engine.executeQuery("""
            SELECT d.id, f.KEY, f.VALUE
            FROM documents d, LATERAL FLATTEN(INPUT => d.json_data) f
            ORDER BY d.id, f.SEQ
            """);

        assertNotNull(result);
        assertEquals(3, result.getRowCount());

        // ID 1 should have 2 keys
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertNotNull(result.getRows().get(0).getValue(1));  // KEY
        assertEquals(1L, result.getRows().get(1).getValue(0));
        assertNotNull(result.getRows().get(1).getValue(1));  // KEY

        // ID 2 should have 1 key
        assertEquals(2L, result.getRows().get(2).getValue(0));
        assertEquals("x", result.getRows().get(2).getValue(1));
    }

    @Test
    public void testLateralCrossJoinSyntax() {
        engine.execute("CREATE TABLE numbers (n INTEGER)");
        engine.execute("INSERT INTO numbers VALUES (2)");

        final ResultSet result = engine.executeQuery("""
            SELECT n.n
            FROM numbers n
            CROSS JOIN LATERAL GENERATOR(ROWCOUNT => n.n) g
            """);

        assertNotNull(result);
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testLateralWithWhereFilter() {
        engine.execute("CREATE TABLE ranges (id INTEGER, count INTEGER)");
        engine.execute("INSERT INTO ranges VALUES (1, 5)");
        engine.execute("INSERT INTO ranges VALUES (2, 3)");

        final ResultSet result = engine.executeQuery("""
            SELECT r.id
            FROM ranges r
            JOIN (SELECT SEQ4() AS k FROM TABLE(GENERATOR(ROWCOUNT => 10))) g ON g.k < r.count
            WHERE r.id = 1
            """);

        assertNotNull(result);
        assertEquals(5, result.getRowCount());

        // All rows should have id=1, repeated once per generated row
        for (int i = 0; i < 5; i++) {
            assertEquals(1L, result.getRows().get(i).getValue(0));
        }
    }

    @Test
    public void testLateralWithMultipleRows() {
        engine.execute("CREATE TABLE items (item_id INTEGER, item_name VARCHAR, count INTEGER)");
        engine.execute("INSERT INTO items VALUES (1, 'Apple', 2)");
        engine.execute("INSERT INTO items VALUES (2, 'Banana', 3)");
        engine.execute("INSERT INTO items VALUES (3, 'Cherry', 1)");

        final ResultSet result = engine.executeQuery("""
            SELECT i.item_name
            FROM items i
            JOIN (SELECT SEQ4() AS k FROM TABLE(GENERATOR(ROWCOUNT => 10))) g ON g.k < i.count
            ORDER BY i.item_id
            """);

        assertNotNull(result);
        assertEquals(6, result.getRowCount());  // 2 + 3 + 1 = 6

        // Each item repeats once per generated row.
        assertEquals("Apple", result.getRows().get(0).getValue(0));
        assertEquals("Apple", result.getRows().get(1).getValue(0));
        assertEquals("Banana", result.getRows().get(2).getValue(0));
        assertEquals("Banana", result.getRows().get(3).getValue(0));
        assertEquals("Banana", result.getRows().get(4).getValue(0));
        assertEquals("Cherry", result.getRows().get(5).getValue(0));
    }

    @Test
    public void testLateralWithAggregation() {
        engine.execute("CREATE TABLE groups (group_id INTEGER, size INTEGER)");
        engine.execute("INSERT INTO groups VALUES (1, 3)");
        engine.execute("INSERT INTO groups VALUES (2, 2)");

        final ResultSet result = engine.executeQuery("""
            SELECT g.group_id, COUNT(*) as row_count
            FROM groups g
            JOIN (SELECT SEQ4() AS k FROM TABLE(GENERATOR(ROWCOUNT => 10))) gen ON gen.k < g.size
            GROUP BY g.group_id
            ORDER BY g.group_id
            """);

        assertNotNull(result);
        assertEquals(2, result.getRowCount());

        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(3L, result.getRows().get(0).getValue(1));

        assertEquals(2L, result.getRows().get(1).getValue(0));
        assertEquals(2L, result.getRows().get(1).getValue(1));
    }

    @Test
    public void testLateralWithNestedFlatten() {
        engine.execute("CREATE TABLE nested_data (id INTEGER, data VARIANT)");
        engine.execute("INSERT INTO nested_data SELECT 1, PARSE_JSON('[1,2,3]')");

        final ResultSet result = engine.executeQuery("""
            SELECT n.id, f.VALUE
            FROM nested_data n, LATERAL FLATTEN(INPUT => n.data) f
            """);

        assertNotNull(result);
        assertEquals(3, result.getRowCount());

        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(1L, result.getRows().get(1).getValue(0));
        assertEquals(1L, result.getRows().get(2).getValue(0));
    }

    @Test
    public void testLateralWithJoinCondition() {
        engine.execute("CREATE TABLE source (id INTEGER, value VARCHAR)");
        engine.execute("INSERT INTO source VALUES (1, 'a,b')");
        engine.execute("INSERT INTO source VALUES (2, 'c,d')");

        final ResultSet result = engine.executeQuery("""
            SELECT s.id, sp.VALUE
            FROM source s
            CROSS JOIN LATERAL SPLIT_TO_TABLE(s.value, ',') sp
            WHERE s.id = 1
            """);

        assertNotNull(result);
        assertEquals(2, result.getRowCount());

        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals("a", result.getRows().get(0).getValue(1));
        assertEquals(1L, result.getRows().get(1).getValue(0));
        assertEquals("b", result.getRows().get(1).getValue(1));
    }

    @Test
    public void testLateralWithEmptyResult() {
        engine.execute("CREATE TABLE empty_test (id INTEGER, count INTEGER)");
        engine.execute("INSERT INTO empty_test VALUES (1, 0)");
        engine.execute("INSERT INTO empty_test VALUES (2, 2)");

        final ResultSet result = engine.executeQuery("""
            SELECT e.id
            FROM empty_test e
            JOIN (SELECT SEQ4() AS k FROM TABLE(GENERATOR(ROWCOUNT => 10))) g ON g.k < e.count
            """);

        assertNotNull(result);
        assertEquals(2, result.getRowCount());  // Only rows from id=2

        assertEquals(2L, result.getRows().get(0).getValue(0));
        assertEquals(2L, result.getRows().get(1).getValue(0));
    }

    /**
     * The refusal itself. Snowflake gets no further than the keyword, so this is a SYNTAX error — and
     * a syntax error spells its position inside the detail rather than on the first line. Both
     * spellings of the join are refused, and the expected offset is taken from the statement so the
     * assertion is of the RULE rather than of a counted column.
     */
    @Test
    public void lateralDoesNotTakeATableWrapper() {
        engine.execute("CREATE TABLE numbers (n INTEGER)");
        engine.execute("INSERT INTO numbers VALUES (2)");

        refusedAtTable("SELECT n.n FROM numbers n, LATERAL TABLE(GENERATOR(ROWCOUNT => 2)) g");
        refusedAtTable("SELECT n.n FROM numbers n CROSS JOIN LATERAL TABLE(GENERATOR(ROWCOUNT => 2)) g");
        // Not asserted here: LATERAL TABLE(SPLIT_TO_TABLE('a,b', ',')) — live's parser recovers from
        // the same first error and then reports a SECOND one at the closing parenthesis, which is its
        // recovery talking rather than a rule.

        // The two supported spellings keep working.
        assertEquals(2, engine.executeQuery(
            "SELECT n.n FROM numbers n, LATERAL GENERATOR(ROWCOUNT => 2) g").getRowCount());
        assertEquals(2, engine.executeQuery(
            "SELECT n.n FROM numbers n, TABLE(GENERATOR(ROWCOUNT => 2)) g").getRowCount());
    }

    private void refusedAtTable(final String sql) {
        final RuntimeException thrown = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, "Snowflake refuses this statement: " + sql);
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertEquals("SQL compilation error:\nsyntax error line 1 at position "
            + sql.indexOf("TABLE(") + " unexpected 'TABLE'.", root.getMessage(), sql);
    }
}
