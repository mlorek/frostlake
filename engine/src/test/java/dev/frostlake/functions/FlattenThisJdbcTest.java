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

import dev.frostlake.BaseJdbcTest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a CLIENT reads out of FLATTEN's THIS column: the container's JSON, exactly as it reads a VALUE
 * that holds one. THIS used to be handed on as the input's JSON text in a VARIANT column, which a driver
 * then read back as a JSON STRING — {@code "{\"a\":1}"}, quotes and escapes included.
 */
public class FlattenThisJdbcTest extends BaseJdbcTest {

    /** The first column of every row, as the driver's getString reads it. */
    private List<String> strings(final String sql) throws SQLException {
        final List<String> values = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
    }

    @Test
    public void aClientReadsTheContainer() throws SQLException {
        assertEquals(Arrays.asList("{\"a\":1}"),
            strings("SELECT this FROM TABLE(FLATTEN(input => PARSE_JSON('{\"a\":1}')))"));
        assertEquals(Arrays.asList("[1,2]", "[1,2]"),
            strings("SELECT this FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(1, 2)))"));
        assertEquals(Arrays.asList("[]"),
            strings("SELECT this FROM TABLE(FLATTEN(input => PARSE_JSON('[]'), outer => TRUE))"));
    }

    @Test
    public void aClientReadsEachLevelsContainer() throws SQLException {
        assertEquals(Arrays.asList("{\"a\":{\"b\":1}}", "{\"b\":1}"),
            strings("""
                SELECT this FROM TABLE(FLATTEN(input => PARSE_JSON('{"a":{"b":1}}'), recursive => TRUE))
                ORDER BY path"""));
        try (ResultSet rs = statement.executeQuery(
                "SELECT this FROM TABLE(FLATTEN(input => PARSE_JSON('{\"a\":1}')))")) {
            assertEquals("VARIANT", rs.getMetaData().getColumnTypeName(1));
        }
    }
}
