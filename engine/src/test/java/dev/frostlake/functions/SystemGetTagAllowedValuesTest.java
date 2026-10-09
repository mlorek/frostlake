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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SYSTEM$GET_TAG_ALLOWED_VALUES: a tag's ALLOWED_VALUES as a JSON array in text, or NULL for a tag without a list. */
public class SystemGetTagAllowedValuesTest extends BaseDatabaseTest {

    private String status(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    private List<String> column(final String sql, final String column) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(String.valueOf(row.getValue(rs.getColumnIndex(column))));
        }
        return out;
    }

    private void refused(final String sql, final String fragment) {
        final RuntimeException refusal = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(refusal.getMessage().contains(fragment), sql + " => " + refusal.getMessage());
    }

    private String allowed(final String tag) {
        final ResultSet rs = engine.executeQuery("SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('" + tag + "')");
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    @Test
    public void theListIsAJsonArrayInText() {
        engine.executeQuery("CREATE TAG e1 ALLOWED_VALUES 'a', 'it''s', 'x,y', ''");
        engine.executeQuery("CREATE TAG \"e_low\" ALLOWED_VALUES 'q'");
        engine.executeQuery("CREATE TAG e9");
        final String values = "[\"a\",\"it's\",\"x,y\",\"\"]";
        assertEquals(values, allowed("e1"));
        assertEquals(values, allowed("test_schema.e1"));
        assertEquals(values, allowed("TEST_DB.TEST_SCHEMA.E1"));
        assertEquals("[\"q\"]", allowed("test_db.test_schema.\"e_low\""));
        assertEquals(null, allowed("test_db.test_schema.e9"));
        assertEquals("[it's]", column("SELECT PARSE_JSON(SYSTEM$GET_TAG_ALLOWED_VALUES('e1'))[1]::VARCHAR AS second",
            "SECOND").toString());
        // A list emptied value by value is an empty array; unset, it is NULL again.
        engine.executeQuery("ALTER TAG e1 DROP ALLOWED_VALUES 'a', 'it''s', 'x,y', ''");
        assertEquals("[]", allowed("e1"));
        engine.executeQuery("ALTER TAG e1 UNSET ALLOWED_VALUES");
        assertEquals(null, allowed("e1"));
    }

    @Test
    public void theArgumentIsOneTextNamingATag() {
        engine.executeQuery("CREATE TAG e1 ALLOWED_VALUES 'a'");
        refused("SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('test_db.test_schema.nosuch')",
            "Tag 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized.");
        refused("SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('nosuch_db.s.t')",
            "Database 'NOSUCH_DB' does not exist or not authorized.");
        refused("SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(NULL)",
            "argument 0 to function SYSTEM$GET_TAG_ALLOWED_VALUES needs to be constant, found 'null'");
        refused("SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(1)", "argument needs to be a string: 'SYSTEM$GET_TAG_ALLOWED_VALUES'");
        refused("SELECT SYSTEM$GET_TAG_ALLOWED_VALUES()",
            "not enough arguments for function [SYSTEM$GET_TAG_ALLOWED_VALUES()], expected 1, got 0");
        refused("SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('a', 'b')",
            "too many arguments for function [SYSTEM$GET_TAG_ALLOWED_VALUES('a', 'b')] expected 1, got 2");
    }
}
