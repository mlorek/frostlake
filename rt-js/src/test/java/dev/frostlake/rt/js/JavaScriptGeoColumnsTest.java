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
package dev.frostlake.rt.js;

import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.GeographyType;
import dev.frostlake.types.GeometryType;
import dev.frostlake.values.GeoValue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A GEOGRAPHY and a GEOMETRY column as a JavaScript procedure's result set reads them under the default output
 * formats (live-verified): {@code getColumnValue} is the GeoJSON object, {@code getColumnValueAsString} its text - a
 * GEOGRAPHY's numbers as they are written, a GEOMETRY's each a DOUBLE in the fifteen-decimal form - and the column's
 * type is {@code json}, its SQL type {@code OBJECT}, an object and a variant column of no scale; a NULL is
 * {@code null}. The runtime's own tests cannot make a geospatial value through SQL, so the result set is built here.
 */
public class JavaScriptGeoColumnsTest {

    private JavaScriptResultSet rows;

    @BeforeEach
    public void setUp() {
        final ResultSet result = new ResultSet(Arrays.asList(new ResultSetColumn("G", new GeographyType()),
            new ResultSetColumn("M", new GeometryType())));
        result.addRow(new Row(point(true), point(false)));
        result.addRow(new Row(null, null));
        rows = new JavaScriptResultSet(result, new JavaScriptApiFailures(), "q");
    }

    private static GeoValue point(final boolean geography) {
        return GeoValue.ofNode(ArrayFunctionHelper.MAPPER.readTree("{\"coordinates\":[1.5,2.25],\"type\":\"Point\"}"),
            geography, geography ? 4326 : 0);
    }

    @Test
    public void aGeospatialValueIsItsGeoJsonObject() {
        assertTrue(rows.next());
        assertEquals(JavaScriptArgumentJson.PLAIN + "{\"coordinates\":[1.5,2.25],\"type\":\"Point\"}",
            rows.columnValue(Integer.valueOf(1), "1"));
        assertEquals(JavaScriptArgumentJson.PLAIN + "{\"coordinates\":[1.5,2.25],\"type\":\"Point\"}",
            rows.columnValue("M", "M"));
        assertEquals("{\"coordinates\":[1.5,2.25],\"type\":\"Point\"}", rows.columnText(Integer.valueOf(1), "1"));
        assertEquals("{\"coordinates\":[1.500000000000000e+00,2.250000000000000e+00],\"type\":\"Point\"}",
            rows.columnText(Integer.valueOf(2), "2"));
        assertTrue(rows.next());
        assertEquals(JavaScriptArgumentJson.PLAIN + "null", rows.columnValue(Integer.valueOf(1), "1"));
        assertEquals("null", rows.columnText(Integer.valueOf(2), "2"));
    }

    @Test
    public void aGeospatialColumnIsAnObjectColumn() {
        for (final String column : new String[] {"G", "M"}) {
            assertEquals("json", rows.column(column, column, "type"), column);
            assertEquals("OBJECT", rows.column(column, column, "sqlType"), column);
            assertEquals(Integer.valueOf(0), rows.column(column, column, "scale"), column);
            assertEquals(Boolean.TRUE, rows.column(column, column, "object"), column);
            assertEquals(Boolean.TRUE, rows.column(column, column, "variant"), column);
            assertEquals(Boolean.FALSE, rows.column(column, column, "text"), column);
            assertEquals(Boolean.FALSE, rows.column(column, column, "binary"), column);
            assertEquals(Boolean.FALSE, rows.column(column, column, "array"), column);
        }
    }
}
