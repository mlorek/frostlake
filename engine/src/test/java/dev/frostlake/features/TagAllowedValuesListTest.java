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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A tag's ALLOWED_VALUES list stays a list when its last value is dropped: the tag then lists {@code []} and takes
 * no value, where a tag without a list takes any.
 */
public class TagAllowedValuesListTest extends BaseDatabaseTest {

    private static final String NOT_ALLOWED = "Value '%s' is not allowed by the specified allowed_values for tag '%s'.";

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

    @Test
    public void anEmptiedListTakesNoValue() {
        engine.executeQuery("CREATE TAG e1 ALLOWED_VALUES 'a', 'b'");
        engine.executeQuery("CREATE TABLE tt (a INT)");
        assertEquals("[[\"a\",\"b\"]]", column("SHOW TAGS LIKE 'E1'", "allowed_values").toString());
        engine.executeQuery("ALTER TAG e1 DROP ALLOWED_VALUES 'a', 'b'");
        assertEquals("[[]]", column("SHOW TAGS LIKE 'E1'", "allowed_values").toString());
        refused("ALTER TABLE tt SET TAG e1 = 'a'", String.format(NOT_ALLOWED, "a", "E1"));
        refused("ALTER TABLE tt SET TAG e1 = ''", String.format(NOT_ALLOWED, "", "E1"));
        refused("ALTER TABLE tt MODIFY COLUMN a SET TAG e1 = 'a'", String.format(NOT_ALLOWED, "a", "E1"));
        refused("CREATE TABLE tt2 (a INT) WITH TAG (e1 = 'q')", String.format(NOT_ALLOWED, "q", "E1"));
        refused("CREATE TABLE tt3 (a INT WITH TAG (e1 = 'q'))", String.format(NOT_ALLOWED, "q", "E1"));
        refused("CREATE VIEW vv WITH TAG (e1 = 'k') AS SELECT 1 AS a", String.format(NOT_ALLOWED, "k", "E1"));
        refused("ALTER SCHEMA test_schema SET TAG e1 = 'k'", String.format(NOT_ALLOWED, "k", "E1"));
        assertEquals("create or replace tag E1  allowed_values ;", status("SELECT GET_DDL('TAG', 'e1')"));
        // A value added back is the one the tag takes; dropped again, the list is empty again.
        engine.executeQuery("ALTER TAG e1 ADD ALLOWED_VALUES 'x'");
        assertEquals("[[\"x\"]]", column("SHOW TAGS LIKE 'E1'", "allowed_values").toString());
        refused("ALTER TABLE tt SET TAG e1 = 'a'", String.format(NOT_ALLOWED, "a", "E1"));
        engine.executeQuery("ALTER TABLE tt SET TAG e1 = 'x'");
        engine.executeQuery("ALTER TAG e1 DROP ALLOWED_VALUES 'x'");
        assertEquals("[[]]", column("SHOW TAGS LIKE 'E1'", "allowed_values").toString());
        // Unset, the tag has no list and takes any value.
        engine.executeQuery("ALTER TAG e1 UNSET ALLOWED_VALUES");
        assertEquals("[null]", column("SHOW TAGS LIKE 'E1'", "allowed_values").toString());
        engine.executeQuery("ALTER TABLE tt SET TAG e1 = 'a'");
        assertEquals("create or replace tag E1 ;", status("SELECT GET_DDL('TAG', 'e1')"));
    }

    @Test
    public void aTagWithoutAListHasNoneAfterADrop() {
        engine.executeQuery("CREATE TAG e2");
        engine.executeQuery("CREATE TABLE tt (a INT)");
        engine.executeQuery("ALTER TAG e2 DROP ALLOWED_VALUES 'a'");
        assertEquals("[null]", column("SHOW TAGS LIKE 'E2'", "allowed_values").toString());
        assertEquals("create or replace tag E2 ;", status("SELECT GET_DDL('TAG', 'e2')"));
        engine.executeQuery("ALTER TABLE tt SET TAG e2 = 'a'");
        engine.executeQuery("ALTER TAG e2 ADD ALLOWED_VALUES 'a', 'a'");
        engine.executeQuery("ALTER TAG e2 DROP ALLOWED_VALUES 'zz'");
        assertEquals("[[\"a\"]]", column("SHOW TAGS LIKE 'E2'", "allowed_values").toString());
    }

    @Test
    public void anEmptiedListIsWrittenAndCloned() {
        engine.executeQuery("CREATE TAG e9 COMMENT = 'c'");
        engine.executeQuery("ALTER TAG e9 ADD ALLOWED_VALUES 'k'");
        engine.executeQuery("ALTER TAG e9 DROP ALLOWED_VALUES 'k'");
        assertEquals("create or replace tag E9  allowed_values COMMENT='c'\n;", status("SELECT GET_DDL('TAG', 'e9')"));
        engine.executeQuery("ALTER TAG e9 SET PROPAGATE = ON_DEPENDENCY");
        assertEquals("[[]]", column("SHOW TAGS LIKE 'E9'", "allowed_values").toString());
        engine.executeQuery("CREATE SCHEMA cl CLONE test_schema");
        assertEquals("[[]]", column("SHOW TAGS LIKE 'E9' IN SCHEMA test_db.cl", "allowed_values").toString());
        engine.executeQuery("DROP SCHEMA cl");
        engine.executeQuery("USE SCHEMA test_db.test_schema");
    }
}
