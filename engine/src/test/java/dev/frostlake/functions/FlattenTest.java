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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FLATTEN expands semi-structured data into one row per element.
 *
 * <p>Every INPUT below is a VARIANT because Snowflake will not read JSON out of a VARCHAR: the JSON
 * TEXT this class used to pass was accepted by the engine and refused by every account. The other half
 * of the same rule is that a VARIANT holding a SCALAR expands to nothing — a string that merely looks
 * like JSON is not re-read as JSON — so the two are tested together.
 */
public class FlattenTest extends BaseDatabaseTest {

    @Test
    public void testFlattenSimpleObject() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"name\":\"John\",\"age\":30}'))) ORDER BY PATH");
        assertEquals(2, result.getRowCount());

        assertEquals(6, result.getColumns().size());
        assertEquals("SEQ", result.getColumns().get(0).getName());
        assertEquals("KEY", result.getColumns().get(1).getName());
        assertEquals("PATH", result.getColumns().get(2).getName());
        assertEquals("INDEX", result.getColumns().get(3).getName());
        assertEquals("VALUE", result.getColumns().get(4).getName());
        assertEquals("THIS", result.getColumns().get(5).getName());

        // An object's members come back in KEY order, and SEQ numbers the input record rather than
        // the element, so both rows carry 1.
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals("age", result.getRows().get(0).getValue(1));
        assertEquals("age", result.getRows().get(0).getValue(2));
        assertNull(result.getRows().get(0).getValue(3));   // INDEX is null for an object
        assertNotNull(result.getRows().get(0).getValue(4));
        assertEquals("name", result.getRows().get(1).getValue(1));
    }

    @Test
    public void testFlattenSimpleArray() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1,2,3]'))) ORDER BY PATH");
        assertEquals(3, result.getRowCount());

        for (int i = 0; i < 3; i++) {
            assertEquals(1L, result.getRows().get(i).getValue(0));         // SEQ: the input record
            assertNull(result.getRows().get(i).getValue(1));               // KEY is null for an array
            assertEquals("[" + i + "]", result.getRows().get(i).getValue(2));
            assertEquals((long) i, result.getRows().get(i).getValue(3));   // INDEX is 0-based
            assertNotNull(result.getRows().get(i).getValue(4));
        }
    }

    @Test
    public void testFlattenNestedObject() {
        final ResultSet result = engine.executeQuery("SELECT * FROM TABLE(FLATTEN(INPUT =>"
            + " PARSE_JSON('{\"person\":{\"name\":\"Alice\",\"address\":{\"city\":\"NYC\"}}}')))");
        assertEquals(1, result.getRowCount());   // only the top level without RECURSIVE
        assertEquals("person", result.getRows().get(0).getValue(1));
    }

    /** RECURSIVE takes a BOOLEAN — the string {@code 'true'} is refused, see {@link #aNonBooleanFlagIsRefused}. */
    @Test
    public void testFlattenRecursive() {
        final ResultSet result = engine.executeQuery("SELECT SEQ, KEY, PATH, INDEX, VALUE FROM"
            + " TABLE(FLATTEN(INPUT => PARSE_JSON('{\"person\":{\"name\":\"Alice\",\"age\":25}}'),"
            + " RECURSIVE => TRUE)) ORDER BY PATH");
        assertEquals(3, result.getRowCount());
        // Depth first, the container before what is under it.
        assertEquals("person", result.getRows().get(0).getValue(2));
        assertEquals("person.age", result.getRows().get(1).getValue(2));
        assertEquals("person.name", result.getRows().get(2).getValue(2));
    }

    @Test
    public void testFlattenArrayOfObjects() {
        final ResultSet result = engine.executeQuery("SELECT * FROM TABLE(FLATTEN(INPUT =>"
            + " PARSE_JSON('[{\"id\":1,\"name\":\"A\"},{\"id\":2,\"name\":\"B\"}]'))) ORDER BY PATH");
        assertEquals(2, result.getRowCount());
        assertNull(result.getRows().get(0).getValue(1));
        assertEquals(0L, result.getRows().get(0).getValue(3));
        assertNull(result.getRows().get(1).getValue(1));
        assertEquals(1L, result.getRows().get(1).getValue(3));
    }

    @Test
    public void testFlattenModeObject() {
        final ResultSet result = engine.executeQuery("SELECT * FROM TABLE(FLATTEN(INPUT =>"
            + " PARSE_JSON('{\"a\":1,\"b\":[2,3]}'), MODE => 'OBJECT')) ORDER BY PATH");
        assertEquals(2, result.getRowCount());   // the object's keys, not the array's elements
        assertEquals("a", result.getRows().get(0).getValue(1));
        assertEquals("b", result.getRows().get(1).getValue(1));
    }

    /**
     * RECURSIVE follows what the MODE emitted. Under {@code MODE => 'ARRAY'} an object's members are
     * neither emitted nor descended into, so an array nested under one is never reached — the engine
     * used to descend regardless and answer three rows here.
     */
    @Test
    public void testFlattenModeArray() {
        assertEquals(0, engine.executeQuery("SELECT * FROM TABLE(FLATTEN(INPUT =>"
            + " PARSE_JSON('{\"arr\":[1,2,3]}'), MODE => 'ARRAY', RECURSIVE => TRUE)) ORDER BY PATH").getRowCount());

        // An array under an array IS reached, because the outer element was emitted.
        final ResultSet nested = engine.executeQuery("SELECT SEQ, KEY, PATH, INDEX, VALUE FROM"
            + " TABLE(FLATTEN(INPUT => PARSE_JSON('[[1,2],[3]]'), MODE => 'ARRAY', RECURSIVE => TRUE)) ORDER BY PATH");
        assertEquals(5, nested.getRowCount());
        assertEquals("[0]", nested.getRows().get(0).getValue(2));
        assertEquals("[0][0]", nested.getRows().get(1).getValue(2));
        assertEquals("[0][1]", nested.getRows().get(2).getValue(2));
        assertEquals("[1]", nested.getRows().get(3).getValue(2));
        assertEquals("[1][0]", nested.getRows().get(4).getValue(2));
    }

    @Test
    public void testFlattenEmptyObject() {
        assertEquals(0, engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{}')))").getRowCount());
    }

    /** OUTER's stand-in row is all NULL, but still reports the container it came from as THIS. */
    @Test
    public void testFlattenEmptyObjectOuter() {
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{}'), OUTER => TRUE))");
        assertEquals(1, result.getRowCount());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertNull(result.getRows().get(0).getValue(1));    // KEY
        assertEquals("", result.getRows().get(0).getValue(2));   // PATH: empty, not null
        assertNull(result.getRows().get(0).getValue(3));    // INDEX
        assertNull(result.getRows().get(0).getValue(4));    // VALUE
        assertNotNull(result.getRows().get(0).getValue(5)); // THIS: the empty object
    }

    @Test
    public void testFlattenEmptyArray() {
        assertEquals(0, engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[]')))").getRowCount());
    }

    @Test
    public void testFlattenWithWhereClause() {
        final ResultSet result = engine.executeQuery("SELECT VALUE FROM TABLE(FLATTEN(INPUT =>"
            + " PARSE_JSON('[{\"id\":1},{\"id\":2},{\"id\":3}]'))) WHERE INDEX < 2");
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testFlattenWithOrderBy() {
        final ResultSet result = engine.executeQuery(
            "SELECT SEQ, VALUE FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[3,1,2]'))) ORDER BY VALUE");
        assertEquals(3, result.getRowCount());
    }

    /**
     * The positional form fills the same five parameters in order — input, path, outer, recursive,
     * mode — and each one given counts, so a positional PATH selects a sub-element like the named one.
     */
    @Test
    public void testFlattenPositionalArg() {
        assertEquals(2, engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(PARSE_JSON('{\"name\":\"John\",\"age\":30}'))) ORDER BY PATH").getRowCount());

        final ResultSet path = engine.executeQuery("SELECT SEQ, KEY, PATH, INDEX, VALUE FROM"
            + " TABLE(FLATTEN(PARSE_JSON('{\"a\":[1,2]}'), 'a')) ORDER BY PATH");
        assertEquals(2, path.getRowCount());
        assertEquals("a[0]", path.getRows().get(0).getValue(2));
        assertEquals("a[1]", path.getRows().get(1).getValue(2));

        assertEquals(1, engine.executeQuery("SELECT * FROM"
            + " TABLE(FLATTEN(PARSE_JSON('[1]'), '', FALSE, FALSE, 'BOTH'))").getRowCount());

        // A named INPUT alongside a positional one wins.
        final ResultSet both = engine.executeQuery(
            "SELECT VALUE FROM TABLE(FLATTEN(PARSE_JSON('[1]'), INPUT => PARSE_JSON('[2]')))");
        assertEquals(1, both.getRowCount());
        assertEquals(2L, ((Number) both.getRows().get(0).getValue(0)).longValue());
    }

    /** PATH selects a sub-element and PREFIXES the reported path rather than replacing it. */
    @Test
    public void aPathArgumentPrefixesTheReportedPath() {
        final ResultSet result = engine.executeQuery("SELECT SEQ, KEY, PATH, INDEX, VALUE FROM"
            + " TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":{\"b\":[1,2]}}'), PATH => 'a.b')) ORDER BY PATH");
        assertEquals(2, result.getRowCount());
        assertEquals("a.b[0]", result.getRows().get(0).getValue(2));
        assertEquals("a.b[1]", result.getRows().get(1).getValue(2));

        // A path that reaches nothing, and one that reaches a scalar, both expand to nothing.
        assertEquals(0, engine.executeQuery("SELECT * FROM TABLE(FLATTEN(INPUT =>"
            + " PARSE_JSON('[1,2]'), PATH => 'nosuch'))").getRowCount());
        assertEquals(0, engine.executeQuery("SELECT * FROM TABLE(FLATTEN(INPUT =>"
            + " PARSE_JSON('{\"a\":1}'), PATH => 'a'))").getRowCount());
    }

    @Test
    public void scalarValuesKeepTheirTypes() {
        // Snowflake's FLATTEN VALUE is a typed VARIANT: a JSON boolean/number element must stay
        // Boolean/Number, not become the string "true"/"5" (which re-quotes on re-aggregation).
        final ResultSet rs = engine.executeQuery("""
            SELECT f.value FROM (SELECT 1 AS i),
            LATERAL FLATTEN(input => PARSE_JSON('[true, 5, "s"]')) f ORDER BY f.index""");
        assertEquals(Boolean.TRUE, rs.getRows().get(0).getValue(0));
        assertEquals(5L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertEquals("s", rs.getRows().get(2).getValue(0));
    }

    // ---- the refusals ------------------------------------------------------------------------

    /**
     * INPUT must be VARIANT, OBJECT or ARRAY. The JSON TEXT this class was originally written against
     * is a VARCHAR, and the refusal names its measured width — {@code PARSE_JSON} is the way in.
     */
    @Test
    public void aVarcharInputIsRefused() {
        assertEquals("SQL compilation error:\ninvalid type [VARCHAR(7)] for parameter 'INPUT'",
            messageOf("SELECT * FROM TABLE(FLATTEN(INPUT => '[1,2,3]'))"));
        assertEquals("SQL compilation error:\ninvalid type [VARCHAR(7)] for parameter 'INPUT'",
            messageOf("SELECT COUNT(*) FROM (SELECT 1 AS i), LATERAL FLATTEN(INPUT => '[1,2,3]')"));
        assertEquals("SQL compilation error:\ninvalid type [NUMBER(1,0)] for parameter 'INPUT'",
            messageOf("SELECT * FROM TABLE(FLATTEN(INPUT => 1))"));
        // The positional form names the parameter by its POSITION instead.
        assertEquals("SQL compilation error:\ninvalid type [VARCHAR(7)] for parameter '1'",
            messageOf("SELECT * FROM TABLE(FLATTEN('[1,2,3]'))"));
    }

    /**
     * The other side of the same rule: a VARIANT that holds a SCALAR is not expandable, so it yields
     * no rows at all. Under OUTER it is the stand-in row, and unlike the empty-container one it
     * reports no THIS and no PATH.
     *
     * <p>A string whose CONTENT reads as JSON belongs here too — live answers no rows for
     * {@code PARSE_JSON('"[1,2,3]"')} — but Frostlake makes that value an ARRAY before FLATTEN ever
     * sees it, which is a defect in PARSE_JSON rather than in this function.
     */
    @Test
    public void aVariantHoldingAScalarExpandsToNothing() {
        assertEquals(0, engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('\"abc\"')))").getRowCount());
        assertEquals(0, engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('7')))").getRowCount());
        assertEquals(0, engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => NULL))").getRowCount());

        final ResultSet outer = engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('7'), OUTER => TRUE))");
        assertEquals(1, outer.getRowCount());
        assertNull(outer.getRows().get(0).getValue(2));   // PATH
        assertNull(outer.getRows().get(0).getValue(4));   // VALUE
        assertNull(outer.getRows().get(0).getValue(5));   // THIS
    }

    /** OUTER and RECURSIVE take a BOOLEAN; the string {@code 'true'} is a VARCHAR and is refused. */
    @Test
    public void aNonBooleanFlagIsRefused() {
        assertEquals("SQL compilation error:\ninvalid type [VARCHAR(4)] for parameter 'OUTER'",
            messageOf("SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[]'), OUTER => 'TRUE'))"));
        assertEquals("SQL compilation error:\ninvalid type [VARCHAR(4)] for parameter 'RECURSIVE'",
            messageOf("SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[]'), RECURSIVE => 'true'))"));
        assertEquals("SQL compilation error:\ninvalid type [NUMBER(1,0)] for parameter 'PATH'",
            messageOf("SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1,2]'), PATH => 1))"));
    }

    /** Unlike SPLIT_TO_TABLE, FLATTEN does take named arguments — but only its own five. */
    @Test
    public void testFlattenMissingInput() {
        assertEquals("SQL compilation error: error line 1 at position 20\n"
            + "missing required argument [INPUT] for function [FLATTEN]",
            messageOf("SELECT * FROM TABLE(FLATTEN(MODE => 'OBJECT')) ORDER BY PATH"));
        assertEquals("SQL compilation error: error line 1 at position 20\n"
            + "invalid argument for function [FLATTEN] unexpected argument [NOSUCH] at position 2,",
            messageOf("SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'), NOSUCH => 1))"));
    }

    @Test
    public void testFlattenInvalidMode() {
        assertEquals("SQL compilation error:\n"
            + "Bad flattening mode 'INVALID' (not 'BOTH', 'ARRAY', or 'OBJECT')",
            messageOf("SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[]'), MODE => 'INVALID'))"));
    }

    /** The root-cause message of a statement that must fail. */
    private String messageOf(final String sql) {
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
        assertTrue(root.getMessage() != null, "the refusal must carry a message: " + sql);
        return root.getMessage();
    }
}
