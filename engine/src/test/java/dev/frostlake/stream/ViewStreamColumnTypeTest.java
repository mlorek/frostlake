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

package dev.frostlake.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

/**
 * A stream over a view reads each projected column with the type the view gives it: an arithmetic item, a
 * function call and a literal come through as NUMBER, VARCHAR and VARCHAR (live-verified). Frostlake typed
 * every item that is not a bare column VARIANT, so a JDBC client read the literal's cell as quoted JSON text.
 */
public class ViewStreamColumnTypeTest extends BaseDatabaseTest {

    private static String typeName(final ResultSet rs, final String column) {
        return rs.getColumns().get(rs.getColumnIndex(column)).getDataType().getName();
    }

    @Test
    public void projectedItemsKeepTheTypesTheViewGivesThem() {
        engine.execute("CREATE TABLE vsc_t (id INTEGER, name VARCHAR(5))");
        engine.execute("CREATE VIEW vsc_v AS SELECT id + 1 AS nid, UPPER(name) AS uname, 'lit' AS l FROM vsc_t");
        engine.execute("CREATE STREAM vsc_s ON VIEW vsc_v");
        engine.execute("INSERT INTO vsc_t VALUES (1, 'ab')");

        final ResultSet rs = engine.executeQuery("SELECT nid, uname, l FROM vsc_s");
        assertEquals("NUMBER", typeName(rs, "NID"));
        assertEquals("VARCHAR", typeName(rs, "UNAME"));
        assertEquals("VARCHAR", typeName(rs, "L"));
        final Row row = rs.getRows().get(0);
        assertEquals(2L, ((Number) row.getValue(0)).longValue());
        assertEquals("AB", row.getValue(1));
        assertEquals("lit", row.getValue(2));
    }

    @Test
    public void aUnionAllViewTypesItsLiteralColumnFromTheView() {
        engine.execute("CREATE TABLE vsc_t1 (id INTEGER)");
        engine.execute("CREATE TABLE vsc_t2 (id INTEGER)");
        engine.execute("""
            CREATE VIEW vsc_u AS
            SELECT id, 'FROM_T1' AS src FROM vsc_t1 UNION ALL SELECT id, 'FROM_T2' AS src FROM vsc_t2""");
        engine.execute("CREATE STREAM vsc_us ON VIEW vsc_u");
        engine.execute("INSERT INTO vsc_t2 VALUES (7)");

        final ResultSet rs = engine.executeQuery("SELECT id, src FROM vsc_us");
        assertEquals("NUMBER", typeName(rs, "ID"));
        assertEquals("VARCHAR", typeName(rs, "SRC"));
        assertEquals("FROM_T2", rs.getRows().get(0).getValue(1));
    }
}
