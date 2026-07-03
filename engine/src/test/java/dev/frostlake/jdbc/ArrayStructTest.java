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
import org.junit.jupiter.api.Test;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Types;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ArrayStructTest extends BaseJdbcTest {

    // === ARRAY TESTS ===

    @Test
    public void testCreateIntegerArray() throws SQLException {
        Object[] elements = {1, 2, 3, 4, 5};
        Array array = connection.createArrayOf("INTEGER", elements);

        assertNotNull(array);
        assertEquals("INTEGER", array.getBaseTypeName());
        assertEquals(Types.INTEGER, array.getBaseType());

        Object[] retrieved = (Object[]) array.getArray();
        assertArrayEquals(elements, retrieved);
    }

    @Test
    public void testCreateStringArray() throws SQLException {
        Object[] elements = {"apple", "banana", "cherry"};
        Array array = connection.createArrayOf("VARCHAR", elements);

        assertNotNull(array);
        assertEquals("VARCHAR", array.getBaseTypeName());
        assertEquals(Types.VARCHAR, array.getBaseType());

        Object[] retrieved = (Object[]) array.getArray();
        assertArrayEquals(elements, retrieved);
    }

    @Test
    public void testCreateEmptyArray() throws SQLException {
        Object[] elements = {};
        Array array = connection.createArrayOf("INTEGER", elements);

        assertNotNull(array);
        Object[] retrieved = (Object[]) array.getArray();
        assertEquals(0, retrieved.length);
    }

    @Test
    public void testArrayGetArraySubset() throws SQLException {
        Object[] elements = {10, 20, 30, 40, 50};
        Array array = connection.createArrayOf("INTEGER", elements);

        // Get subset starting at index 2 (1-based), count 3
        Object[] subset = (Object[]) array.getArray(2, 3);

        assertArrayEquals(new Object[]{20, 30, 40}, subset);
    }

    @Test
    public void testArrayGetArraySubsetAtEnd() throws SQLException {
        Object[] elements = {1, 2, 3, 4, 5};
        Array array = connection.createArrayOf("INTEGER", elements);

        // Get subset starting at index 4, count 10 (should only get 2 elements)
        Object[] subset = (Object[]) array.getArray(4, 10);

        assertArrayEquals(new Object[]{4, 5}, subset);
    }

    @Test
    public void testArrayInvalidIndex() throws SQLException {
        Object[] elements = {1, 2, 3};
        Array array = connection.createArrayOf("INTEGER", elements);

        // Index 0 is invalid (1-based)
        assertThrows(SQLException.class, () -> array.getArray(0, 2));

        // Index beyond array length
        assertThrows(SQLException.class, () -> array.getArray(10, 2));
    }

    @Test
    public void testArrayFree() throws SQLException {
        Object[] elements = {1, 2, 3};
        Array array = connection.createArrayOf("INTEGER", elements);

        array.free();

        // After free, operations should throw exception
        assertThrows(SQLException.class, () -> array.getArray());
        assertThrows(SQLException.class, () -> array.getBaseTypeName());
    }

    @Test
    public void testArrayWithNullElements() throws SQLException {
        Object[] elements = {1, null, 3, null, 5};
        Array array = connection.createArrayOf("INTEGER", elements);

        Object[] retrieved = (Object[]) array.getArray();
        assertArrayEquals(elements, retrieved);
    }

    @Test
    public void testArrayWithDoubles() throws SQLException {
        Object[] elements = {1.5, 2.5, 3.5};
        Array array = connection.createArrayOf("DOUBLE", elements);

        assertEquals("DOUBLE", array.getBaseTypeName());
        assertEquals(Types.DOUBLE, array.getBaseType());

        Object[] retrieved = (Object[]) array.getArray();
        assertArrayEquals(elements, retrieved);
    }

    @Test
    public void testArrayWithBooleans() throws SQLException {
        Object[] elements = {true, false, true};
        Array array = connection.createArrayOf("BOOLEAN", elements);

        assertEquals("BOOLEAN", array.getBaseTypeName());
        assertEquals(Types.BOOLEAN, array.getBaseType());

        Object[] retrieved = (Object[]) array.getArray();
        assertArrayEquals(elements, retrieved);
    }

    @Test
    public void testArrayLength() throws SQLException {
        Object[] elements = {1, 2, 3, 4, 5};
        DirectArray array = (DirectArray) connection.createArrayOf("INTEGER", elements);

        assertEquals(5, array.length());
    }

    // === STRUCT TESTS ===

    @Test
    public void testCreateStruct() throws SQLException {
        Object[] attributes = {"John", 30, true};
        Struct struct = connection.createStruct("PERSON", attributes);

        assertNotNull(struct);
        assertEquals("PERSON", struct.getSQLTypeName());

        Object[] retrieved = struct.getAttributes();
        assertArrayEquals(attributes, retrieved);
    }

    @Test
    public void testCreateStructWithMap() throws SQLException {
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", "Alice");
        fields.put("age", 25);
        fields.put("active", true);

        DirectStruct struct = new DirectStruct("EMPLOYEE", fields);

        assertEquals("EMPLOYEE", struct.getSQLTypeName());
        assertEquals(3, struct.size());
    }

    @Test
    public void testStructGetAttributeByName() throws SQLException {
        String[] names = {"id", "name", "value"};
        Object[] values = {1, "Test", 42.5};
        DirectStruct struct = new DirectStruct("RECORD", names, values);

        assertEquals(1, struct.getAttribute("id"));
        assertEquals("Test", struct.getAttribute("name"));
        assertEquals(42.5, struct.getAttribute("value"));
    }

    @Test
    public void testStructGetAttributeByIndex() throws SQLException {
        String[] names = {"field1", "field2", "field3"};
        Object[] values = {10, 20, 30};
        DirectStruct struct = new DirectStruct("TUPLE", names, values);

        // 1-based indexing
        assertEquals(10, struct.getAttribute(1));
        assertEquals(20, struct.getAttribute(2));
        assertEquals(30, struct.getAttribute(3));
    }

    @Test
    public void testStructInvalidAttributeName() throws SQLException {
        String[] names = {"field1", "field2"};
        Object[] values = {1, 2};
        DirectStruct struct = new DirectStruct("RECORD", names, values);

        assertThrows(SQLException.class, () -> struct.getAttribute("nonexistent"));
    }

    @Test
    public void testStructInvalidAttributeIndex() throws SQLException {
        String[] names = {"field1", "field2"};
        Object[] values = {1, 2};
        DirectStruct struct = new DirectStruct("RECORD", names, values);

        assertThrows(SQLException.class, () -> struct.getAttribute(0)); // 1-based
        assertThrows(SQLException.class, () -> struct.getAttribute(10));
    }

    @Test
    public void testStructAsMap() throws SQLException {
        String[] names = {"x", "y", "z"};
        Object[] values = {1, 2, 3};
        DirectStruct struct = new DirectStruct("POINT3D", names, values);

        Map<String, Object> map = struct.asMap();

        assertEquals(3, map.size());
        assertEquals(1, map.get("x"));
        assertEquals(2, map.get("y"));
        assertEquals(3, map.get("z"));
    }

    @Test
    public void testStructWithNullAttributes() throws SQLException {
        String[] names = {"field1", "field2", "field3"};
        Object[] values = {"value1", null, "value3"};
        DirectStruct struct = new DirectStruct("RECORD", names, values);

        Object[] retrieved = struct.getAttributes();
        assertArrayEquals(values, retrieved);
        assertEquals(null, struct.getAttribute("field2"));
    }

    @Test
    public void testStructSize() throws SQLException {
        String[] names = {"a", "b", "c", "d"};
        Object[] values = {1, 2, 3, 4};
        DirectStruct struct = new DirectStruct("TUPLE", names, values);

        assertEquals(4, struct.size());
    }

    @Test
    public void testStructFree() throws SQLException {
        String[] names = {"field1"};
        Object[] values = {"value1"};
        DirectStruct struct = new DirectStruct("RECORD", names, values);

        struct.free();

        // After free, operations should throw exception
        assertThrows(SQLException.class, () -> struct.getAttributes());
        assertThrows(SQLException.class, () -> struct.getSQLTypeName());
    }

    @Test
    public void testStructAttributeNamesAndValuesLengthMismatch() {
        String[] names = {"field1", "field2"};
        Object[] values = {1, 2, 3}; // 3 values but 2 names

        assertThrows(IllegalArgumentException.class, () -> {
            new DirectStruct("RECORD", names, values);
        });
    }

    @Test
    public void testEmptyStruct() throws SQLException {
        String[] names = {};
        Object[] values = {};
        DirectStruct struct = new DirectStruct("EMPTY", names, values);

        assertEquals(0, struct.size());
        assertEquals(0, struct.getAttributes().length);
    }

    // === NESTED STRUCTURES TESTS ===

    @Test
    public void testArrayOfStructs() throws SQLException {
        // Create structs
        String[] names = {"id", "name"};
        DirectStruct struct1 = new DirectStruct("PERSON", names, new Object[]{1, "Alice"});
        DirectStruct struct2 = new DirectStruct("PERSON", names, new Object[]{2, "Bob"});
        DirectStruct struct3 = new DirectStruct("PERSON", names, new Object[]{3, "Charlie"});

        // Create array of structs
        Object[] structArray = {struct1, struct2, struct3};
        Array array = connection.createArrayOf("PERSON", structArray);

        Object[] retrieved = (Object[]) array.getArray();
        assertEquals(3, retrieved.length);
        assertEquals("Alice", ((DirectStruct) retrieved[0]).getAttribute("name"));
        assertEquals("Bob", ((DirectStruct) retrieved[1]).getAttribute("name"));
        assertEquals("Charlie", ((DirectStruct) retrieved[2]).getAttribute("name"));
    }

    @Test
    public void testStructWithArrayField() throws SQLException {
        // Create array
        Object[] numbers = {1, 2, 3, 4, 5};
        Array array = connection.createArrayOf("INTEGER", numbers);

        // Create struct with array field
        String[] names = {"id", "values"};
        Object[] values = {1, array};
        DirectStruct struct = new DirectStruct("RECORD", names, values);

        assertEquals(1, struct.getAttribute("id"));
        Array retrievedArray = (Array) struct.getAttribute("values");
        assertArrayEquals(numbers, (Object[]) retrievedArray.getArray());
    }

    @Test
    public void testNestedStructs() throws SQLException {
        // Create inner struct (address)
        String[] addressNames = {"street", "city", "zip"};
        Object[] addressValues = {"123 Main St", "Springfield", "12345"};
        DirectStruct address = new DirectStruct("ADDRESS", addressNames, addressValues);

        // Create outer struct (person with address)
        String[] personNames = {"name", "age", "address"};
        Object[] personValues = {"John", 30, address};
        DirectStruct person = new DirectStruct("PERSON", personNames, personValues);

        assertEquals("John", person.getAttribute("name"));
        assertEquals(30, person.getAttribute("age"));

        DirectStruct retrievedAddress = (DirectStruct) person.getAttribute("address");
        assertEquals("Springfield", retrievedAddress.getAttribute("city"));
    }

    @Test
    public void testStructToString() throws SQLException {
        String[] names = {"field1", "field2"};
        Object[] values = {"value1", 42};
        DirectStruct struct = new DirectStruct("TEST", names, values);

        String str = struct.toString();

        // Verify toString contains type name and fields
        assert(str.contains("TEST"));
        assert(str.contains("field1"));
        assert(str.contains("value1"));
    }

    @Test
    public void testArrayToString() throws SQLException {
        Object[] elements = {1, 2, 3};
        DirectArray array = (DirectArray) connection.createArrayOf("INTEGER", elements);

        String str = array.toString();

        // Verify toString contains type name and length
        assert(str.contains("INTEGER"));
        assert(str.contains("3"));
    }

    @Test
    public void testStructCaseInsensitiveAttributeName() throws SQLException {
        String[] names = {"FieldName", "AnotherField"};
        Object[] values = {"value1", "value2"};
        DirectStruct struct = new DirectStruct("RECORD", names, values);

        // Should work with different case
        assertEquals("value1", struct.getAttribute("fieldname"));
        assertEquals("value1", struct.getAttribute("FIELDNAME"));
        assertEquals("value2", struct.getAttribute("anotherfield"));
    }
}
