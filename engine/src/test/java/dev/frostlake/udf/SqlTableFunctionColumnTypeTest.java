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

package dev.frostlake.udf;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A SQL table function's result columns carry their declared RETURNS TABLE types, statically too, as a procedure's
 * RETURNS TABLE does. They were rebuilt without a type. A DATE column read NULL[LOB] in SYSTEM$TYPEOF and became a
 * TIMESTAMP under DATEADD. A UNION with a TIMESTAMP arm declared DATE and lost the time. A CTAS or a view over the
 * call declared the body's NUMBER(4,3) and VARCHAR(6) instead of the NUMBER(10,2) and VARCHAR(3) it declares.
 */
public class SqlTableFunctionColumnTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE FUNCTION tfd() RETURNS TABLE (g DATE, n NUMBER(10,2))"
            + " AS $$ SELECT '2024-01-31'::DATE, 1.5::NUMBER(10,2) $$");
        engine.execute("CREATE FUNCTION tfw() RETURNS TABLE (c NUMBER(10,2), d VARCHAR(3))"
            + " AS $$ SELECT 1.234, 'abc' $$");
    }

    private String typeName(final Object typed) {
        final String text = String.valueOf(typed);
        return text.substring(0, text.indexOf('['));
    }

    private String describeView(final String view, final String column) {
        final ResultSet rs = engine.executeQuery("DESCRIBE VIEW " + view);
        return cell(rs, soleRowWhere(rs, "name", column), "type");
    }

    @Test
    public void theColumnsAreTypedByTheDeclaration() {
        final Row row = engine.executeQuery(
            "SELECT DATEADD(day, 1, g), SYSTEM$TYPEOF(g), SYSTEM$TYPEOF(n) FROM TABLE(tfd())").getRows().get(0);
        assertEquals("2024-02-01", String.valueOf(row.getValue(0)), "a DATE plus a day is a DATE");
        assertEquals("DATE", typeName(row.getValue(1)));
        assertEquals("NUMBER(10,2)", typeName(row.getValue(2)));
    }

    @Test
    public void aUnionWithATimestampArmIsATimestamp() {
        engine.execute("CREATE TABLE z AS SELECT g x FROM TABLE(tfd())"
            + " UNION ALL SELECT '2024-02-01 10:00:00'::TIMESTAMP_NTZ");
        assertEquals("TIMESTAMP_NTZ(9)", describeCell("z", "X", "type"));
        assertEquals("1", String.valueOf(engine.executeQuery(
            "SELECT COUNT(*) FROM z WHERE x = '2024-02-01 10:00:00'::TIMESTAMP_NTZ").getRows().get(0).getValue(0)),
            "the time is kept");
    }

    @Test
    public void aCtasOrAViewOverTheCallDeclaresTheReturnsTableTypes() {
        engine.execute("CREATE TABLE z2 AS SELECT * FROM TABLE(tfw())");
        assertEquals("NUMBER(10,2)", describeCell("z2", "C", "type"));
        assertEquals("VARCHAR(3)", describeCell("z2", "D", "type"));
        engine.execute("CREATE VIEW zv AS SELECT * FROM TABLE(tfw())");
        assertEquals("NUMBER(10,2)", describeView("zv", "C"));
        assertEquals("VARCHAR(3)", describeView("zv", "D"));
    }
}
