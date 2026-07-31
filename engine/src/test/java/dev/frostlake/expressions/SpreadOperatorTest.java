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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spread operator {@code **}: in the SELECT list a constant array expands to one column per
 * element (labeled {@code <text>[N]}, 1-based); inside array constructors and function argument
 * lists the array's elements are spliced in; inside object constructors an object's pairs are
 * merged (last key wins). The row-spread forms ({@code t.**} / {@code ** t}) are unaffected.
 */
public class SpreadOperatorTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void selectListSpreadExpandsToOneColumnPerElement() {
        final ResultSet rs = engine.executeQuery("SELECT ** [3, 4]");
        assertEquals(2, rs.getColumns().size());
        assertEquals("** [3, 4][1]", rs.getColumns().get(0).getName());
        assertEquals("** [3, 4][2]", rs.getColumns().get(1).getName());
        assertEquals(3L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(4L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void selectListSpreadWorksWithAFromClause() {
        engine.execute("CREATE TABLE sp_t (id INTEGER)");
        engine.execute("INSERT INTO sp_t VALUES (10), (20)");
        final ResultSet rs = engine.executeQuery("SELECT id, ** [3, 4] FROM sp_t ORDER BY id");
        assertEquals(3, rs.getColumns().size());
        assertEquals("** [3, 4][1]", rs.getColumns().get(1).getName());
        assertEquals(2, rs.getRowCount());
        assertEquals(3L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(4L, ((Number) rs.getRows().get(1).getValue(2)).longValue());
    }

    @Test
    public void arrayConstructorSplicesSpreadElements() {
        assertEquals("[1,2,3,4]", String.valueOf(scalar("SELECT [1, 2, ** [3, 4]]")));
        assertEquals("[1,2,3,4]", String.valueOf(scalar("SELECT [** [1, 2], ** [3, 4]]")));
        assertEquals("[3,4]", String.valueOf(scalar("SELECT ARRAY_CONSTRUCT(** [3, 4])")));
    }

    @Test
    public void objectConstructorMergesSpreadPairs() {
        assertEquals("{\"a\":1,\"b\":2}", String.valueOf(scalar("SELECT {'a': 1, ** {'b': 2}}")));
        assertEquals("{\"a\":1,\"b\":2}", String.valueOf(scalar("SELECT OBJECT_CONSTRUCT('a', 1, ** {'b': 2})")));
        assertEquals("{\"a\":9}", String.valueOf(scalar("SELECT {'a': 1, ** {'a': 9}}")),
            "the last occurrence of a key wins, as in the plain constructor");
    }

    @Test
    public void functionArgumentSpreadFeedsPositionalArguments() {
        assertEquals(7L, ((Number) scalar("SELECT COALESCE(** [null, 7])")).longValue());
    }

    @Test
    public void nonArraySpreadFailsClearly() {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT ** 5");
            }
        });
        assertTrue(e.getMessage().contains("spread"), "unexpected message: " + e.getMessage());
    }

    @Test
    public void rowSpreadFormsAreUnaffected() {
        engine.execute("CREATE TABLE sp_r (a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO sp_r VALUES (1, 2)");
        assertEquals(2, engine.executeQuery("SELECT sp_r.** FROM sp_r").getColumns().size());
        assertEquals(2, engine.executeQuery("SELECT ** sp_r FROM sp_r").getColumns().size());
    }
}
