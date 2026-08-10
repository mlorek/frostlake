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

package dev.frostlake.jdbc;

import dev.frostlake.BaseJdbcTest;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ArrayStructTest extends BaseJdbcTest {

    /**
     * Only the calls the Snowflake driver genuinely cannot answer are held back from live. Measured
     * against driver 3.20.0, {@code createArrayOf} DOES work and agrees with Frostlake on
     * {@code getBaseTypeName} ("INTEGER"), {@code getBaseType} (4) and {@code getArray()} — so those
     * tests run on both. What differs:
     *
     * <pre>
     *   getArray(index, count)   Snowflake: SQLFeatureNotSupportedException   Frostlake: returns the slice
     *   free() then getArray()   Snowflake: still returns the elements        Frostlake: throws
     *   createStruct(...)        Snowflake: SnowflakeLoggedFeatureNotSupported Frostlake: builds a struct
     * </pre>
     *
     * <p>Those three are a driver surface rather than SQL behaviour, and Frostlake is the more permissive
     * side of each. Tests that build a {@code DirectStruct} directly stay embedded-only for the same reason.
     */
    private static final String DRIVER_OBJECTS =
        "the Snowflake driver rejects getArray(index, count) and createStruct, and keeps serving elements "
        + "after free(); these assert Frostlake's own DirectArray / DirectStruct behaviour";

    // === ARRAY TESTS ===

    @Test
    public void testCreateIntegerArray() throws SQLException {
        final Object[] elements = {1, 2, 3, 4, 5};
        final Array array = connection.createArrayOf("INTEGER", elements);

        assertNotNull(array);
        assertEquals("INTEGER", array.getBaseTypeName());
        assertEquals(Types.INTEGER, array.getBaseType());

        final Object[] retrieved = (Object[]) array.getArray();
        assertArrayEquals(elements, retrieved);
    }

    @Test
    public void testCreateStringArray() throws SQLException {
        final Object[] elements = {"apple", "banana", "cherry"};
        final Array array = connection.createArrayOf("VARCHAR", elements);

        assertNotNull(array);
        assertEquals("VARCHAR", array.getBaseTypeName());
        assertEquals(Types.VARCHAR, array.getBaseType());

        final Object[] retrieved = (Object[]) array.getArray();
        assertArrayEquals(elements, retrieved);
    }

    @Test
    public void testCreateEmptyArray() throws SQLException {
        final Object[] elements = {};
        final Array array = connection.createArrayOf("INTEGER", elements);

        assertNotNull(array);
        final Object[] retrieved = (Object[]) array.getArray();
        assertEquals(0, retrieved.length);
    }

    @Test
    public void testArrayGetArraySubset() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), DRIVER_OBJECTS);
        final Object[] elements = {10, 20, 30, 40, 50};
        final Array array = connection.createArrayOf("INTEGER", elements);

        // Get subset starting at index 2 (1-based), count 3
        final Object[] subset = (Object[]) array.getArray(2, 3);

        assertArrayEquals(new Object[]{20, 30, 40}, subset);
    }

    @Test
    public void testArrayGetArraySubsetAtEnd() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), DRIVER_OBJECTS);
        final Object[] elements = {1, 2, 3, 4, 5};
        final Array array = connection.createArrayOf("INTEGER", elements);

        // Get subset starting at index 4, count 10 (should only get 2 elements)
        final Object[] subset = (Object[]) array.getArray(4, 10);

        assertArrayEquals(new Object[]{4, 5}, subset);
    }

    @Test
    public void testArrayInvalidIndex() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), DRIVER_OBJECTS);
        final Object[] elements = {1, 2, 3};
        final Array array = connection.createArrayOf("INTEGER", elements);

        // Index 0 is invalid (1-based)
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                array.getArray(0, 2);
            }
        });

        // Index beyond array length
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                array.getArray(10, 2);
            }
        });
    }

    @Test
    public void testArrayFree() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), DRIVER_OBJECTS);
        final Object[] elements = {1, 2, 3};
        final Array array = connection.createArrayOf("INTEGER", elements);

        array.free();

        // After free, operations should throw exception
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                array.getArray();
            }
        });
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                array.getBaseTypeName();
            }
        });
    }

    @Test
    public void testArrayWithNullElements() throws SQLException {
        final Object[] elements = {1, null, 3, null, 5};
        final Array array = connection.createArrayOf("INTEGER", elements);

        final Object[] retrieved = (Object[]) array.getArray();
        assertArrayEquals(elements, retrieved);
    }

    @Test
    public void testArrayWithDoubles() throws SQLException {
        final Object[] elements = {1.5, 2.5, 3.5};
        final Array array = connection.createArrayOf("DOUBLE", elements);

        assertEquals("DOUBLE", array.getBaseTypeName());
        assertEquals(Types.DOUBLE, array.getBaseType());

        final Object[] retrieved = (Object[]) array.getArray();
        assertArrayEquals(elements, retrieved);
    }

    @Test
    public void testArrayWithBooleans() throws SQLException {
        final Object[] elements = {true, false, true};
        final Array array = connection.createArrayOf("BOOLEAN", elements);

        assertEquals("BOOLEAN", array.getBaseTypeName());
        assertEquals(Types.BOOLEAN, array.getBaseType());

        final Object[] retrieved = (Object[]) array.getArray();
        assertArrayEquals(elements, retrieved);
    }

    @Test
    public void testArrayLength() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), DRIVER_OBJECTS);
        final Object[] elements = {1, 2, 3, 4, 5};
        final DirectArray array = (DirectArray) connection.createArrayOf("INTEGER", elements);

        assertEquals(5, array.length());
    }

    // === STRUCT TESTS ===

    @Test
    public void testCreateStruct() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), DRIVER_OBJECTS);
        final Object[] attributes = {"John", 30, true};
        final Struct struct = connection.createStruct("PERSON", attributes);

        assertNotNull(struct);
        assertEquals("PERSON", struct.getSQLTypeName());

        final Object[] retrieved = struct.getAttributes();
        assertArrayEquals(attributes, retrieved);
    }

    @Test
    public void testCreateStructWithMap() throws SQLException {
        final Map<String, Object> fields = new HashMap<>();
        fields.put("name", "Alice");
        fields.put("age", 25);
        fields.put("active", true);

        final DirectStruct struct = new DirectStruct("EMPLOYEE", fields);

        assertEquals("EMPLOYEE", struct.getSQLTypeName());
        assertEquals(3, struct.size());
    }

    @Test
    public void testStructGetAttributeByName() throws SQLException {
        final String[] names = {"id", "name", "value"};
        final Object[] values = {1, "Test", 42.5};
        final DirectStruct struct = new DirectStruct("RECORD", names, values);

        assertEquals(1, struct.getAttribute("id"));
        assertEquals("Test", struct.getAttribute("name"));
        assertEquals(42.5, struct.getAttribute("value"));
    }

    @Test
    public void testStructGetAttributeByIndex() throws SQLException {
        final String[] names = {"field1", "field2", "field3"};
        final Object[] values = {10, 20, 30};
        final DirectStruct struct = new DirectStruct("TUPLE", names, values);

        // 1-based indexing
        assertEquals(10, struct.getAttribute(1));
        assertEquals(20, struct.getAttribute(2));
        assertEquals(30, struct.getAttribute(3));
    }

    @Test
    public void testStructInvalidAttributeName() throws SQLException {
        final String[] names = {"field1", "field2"};
        final Object[] values = {1, 2};
        final DirectStruct struct = new DirectStruct("RECORD", names, values);

        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                struct.getAttribute("nonexistent");
            }
        });
    }

    @Test
    public void testStructInvalidAttributeIndex() throws SQLException {
        final String[] names = {"field1", "field2"};
        final Object[] values = {1, 2};
        final DirectStruct struct = new DirectStruct("RECORD", names, values);

        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                struct.getAttribute(0);
            }
        }); // 1-based
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                struct.getAttribute(10);
            }
        });
    }

    @Test
    public void testStructAsMap() throws SQLException {
        final String[] names = {"x", "y", "z"};
        final Object[] values = {1, 2, 3};
        final DirectStruct struct = new DirectStruct("POINT3D", names, values);

        final Map<String, Object> map = struct.asMap();

        assertEquals(3, map.size());
        assertEquals(1, map.get("x"));
        assertEquals(2, map.get("y"));
        assertEquals(3, map.get("z"));
    }

    @Test
    public void testStructWithNullAttributes() throws SQLException {
        final String[] names = {"field1", "field2", "field3"};
        final Object[] values = {"value1", null, "value3"};
        final DirectStruct struct = new DirectStruct("RECORD", names, values);

        final Object[] retrieved = struct.getAttributes();
        assertArrayEquals(values, retrieved);
        assertEquals(null, struct.getAttribute("field2"));
    }

    @Test
    public void testStructSize() throws SQLException {
        final String[] names = {"a", "b", "c", "d"};
        final Object[] values = {1, 2, 3, 4};
        final DirectStruct struct = new DirectStruct("TUPLE", names, values);

        assertEquals(4, struct.size());
    }

    @Test
    public void testStructFree() throws SQLException {
        final String[] names = {"field1"};
        final Object[] values = {"value1"};
        final DirectStruct struct = new DirectStruct("RECORD", names, values);

        struct.free();

        // After free, operations should throw exception
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                struct.getAttributes();
            }
        });
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                struct.getSQLTypeName();
            }
        });
    }

    @Test
    public void testStructAttributeNamesAndValuesLengthMismatch() {
        final String[] names = {"field1", "field2"};
        final Object[] values = {1, 2, 3}; // 3 values but 2 names

        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                new DirectStruct("RECORD", names, values);
                
            }
        });
    }

    @Test
    public void testEmptyStruct() throws SQLException {
        final String[] names = {};
        final Object[] values = {};
        final DirectStruct struct = new DirectStruct("EMPTY", names, values);

        assertEquals(0, struct.size());
        assertEquals(0, struct.getAttributes().length);
    }

    // === NESTED STRUCTURES TESTS ===

    @Test
    public void testArrayOfStructs() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), DRIVER_OBJECTS);
        // Create structs
        final String[] names = {"id", "name"};
        final DirectStruct struct1 = new DirectStruct("PERSON", names, new Object[]{1, "Alice"});
        final DirectStruct struct2 = new DirectStruct("PERSON", names, new Object[]{2, "Bob"});
        final DirectStruct struct3 = new DirectStruct("PERSON", names, new Object[]{3, "Charlie"});

        // Create array of structs
        final Object[] structArray = {struct1, struct2, struct3};
        final Array array = connection.createArrayOf("PERSON", structArray);

        final Object[] retrieved = (Object[]) array.getArray();
        assertEquals(3, retrieved.length);
        assertEquals("Alice", ((DirectStruct) retrieved[0]).getAttribute("name"));
        assertEquals("Bob", ((DirectStruct) retrieved[1]).getAttribute("name"));
        assertEquals("Charlie", ((DirectStruct) retrieved[2]).getAttribute("name"));
    }

    @Test
    public void testStructWithArrayField() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), DRIVER_OBJECTS);
        // Create array
        final Object[] numbers = {1, 2, 3, 4, 5};
        final Array array = connection.createArrayOf("INTEGER", numbers);

        // Create struct with array field
        final String[] names = {"id", "values"};
        final Object[] values = {1, array};
        final DirectStruct struct = new DirectStruct("RECORD", names, values);

        assertEquals(1, struct.getAttribute("id"));
        final Array retrievedArray = (Array) struct.getAttribute("values");
        assertArrayEquals(numbers, (Object[]) retrievedArray.getArray());
    }

    @Test
    public void testNestedStructs() throws SQLException {
        // Create inner struct (address)
        final String[] addressNames = {"street", "city", "zip"};
        final Object[] addressValues = {"123 Main St", "Springfield", "12345"};
        final DirectStruct address = new DirectStruct("ADDRESS", addressNames, addressValues);

        // Create outer struct (person with address)
        final String[] personNames = {"name", "age", "address"};
        final Object[] personValues = {"John", 30, address};
        final DirectStruct person = new DirectStruct("PERSON", personNames, personValues);

        assertEquals("John", person.getAttribute("name"));
        assertEquals(30, person.getAttribute("age"));

        final DirectStruct retrievedAddress = (DirectStruct) person.getAttribute("address");
        assertEquals("Springfield", retrievedAddress.getAttribute("city"));
    }

    @Test
    public void testStructToString() throws SQLException {
        final String[] names = {"field1", "field2"};
        final Object[] values = {"value1", 42};
        final DirectStruct struct = new DirectStruct("TEST", names, values);

        final String str = struct.toString();

        // Verify toString contains type name and fields
        assert(str.contains("TEST"));
        assert(str.contains("field1"));
        assert(str.contains("value1"));
    }

    @Test
    public void testArrayToString() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), DRIVER_OBJECTS);
        final Object[] elements = {1, 2, 3};
        final DirectArray array = (DirectArray) connection.createArrayOf("INTEGER", elements);

        final String str = array.toString();

        // Verify toString contains type name and length
        assert(str.contains("INTEGER"));
        assert(str.contains("3"));
    }

    @Test
    public void testStructCaseInsensitiveAttributeName() throws SQLException {
        final String[] names = {"FieldName", "AnotherField"};
        final Object[] values = {"value1", "value2"};
        final DirectStruct struct = new DirectStruct("RECORD", names, values);

        // Should work with different case
        assertEquals("value1", struct.getAttribute("fieldname"));
        assertEquals("value1", struct.getAttribute("FIELDNAME"));
        assertEquals("value2", struct.getAttribute("anotherfield"));
    }
}
