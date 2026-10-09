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
package dev.frostlake.ddl;

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
 * A table's column keeps the length its statement declared, however long, on every surface that describes it; a
 * view's column and a column created from a query stop at the default.
 */
public class DeclaredStringWidthTest extends BaseDatabaseTest {

    private List<String> described(final String relation) {
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE " + relation);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(row.getValue(rs.getColumnIndex("name")) + " " + row.getValue(rs.getColumnIndex("type")));
        }
        return out;
    }

    private List<String> column(final String sql, final String column) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(String.valueOf(row.getValue(rs.getColumnIndex(column))));
        }
        return out;
    }

    private List<String> rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final StringBuilder line = new StringBuilder();
            for (int i = 0; i < rs.getColumns().size(); i++) {
                line.append(i == 0 ? "" : "|").append(row.getValue(i));
            }
            out.add(line.toString());
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

    private static final String LENGTHS = """
        SELECT column_name, data_type, character_maximum_length, character_octet_length
          FROM information_schema.columns
         WHERE table_name = '%s'
         ORDER BY ordinal_position""";

    @Override
    protected void setupTest() {
        engine.executeQuery("CREATE TABLE wide_p (v VARCHAR(134217728), w VARCHAR(16777217), s VARCHAR,"
            + " b BINARY(67108864), n INT)");
    }

    @Test
    public void aTablesColumnReadsBackAsDeclared() {
        assertEquals("[V VARCHAR(134217728), W VARCHAR(16777217), S VARCHAR(16777216), B BINARY(67108864),"
            + " N NUMBER(38,0)]", described("wide_p").toString());
        assertEquals("[{\"type\":\"TEXT\",\"length\":134217728,\"byteLength\":134217728,\"nullable\":true,\"fixed\":false},"
            + " {\"type\":\"TEXT\",\"length\":16777217,\"byteLength\":67108868,\"nullable\":true,\"fixed\":false},"
            + " {\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,\"nullable\":true,\"fixed\":false},"
            + " {\"type\":\"BINARY\",\"length\":67108864,\"byteLength\":67108864,\"nullable\":true,\"fixed\":true},"
            + " {\"type\":\"FIXED\",\"precision\":38,\"scale\":0,\"nullable\":true}]",
            column("SHOW COLUMNS IN TABLE wide_p", "data_type").toString());
        assertEquals("[V|TEXT|134217728|134217728, W|TEXT|16777217|67108868, S|TEXT|16777216|67108864,"
            + " B|BINARY|null|null, N|NUMBER|null|null]", rows(String.format(LENGTHS, "WIDE_P")).toString());
        engine.executeQuery("INSERT INTO wide_p (v, w, s, n) VALUES ('a', 'b', 'c', 1)");
        assertEquals("[VARCHAR(134217728)[LOB]|VARCHAR(16777217)[LOB]|VARCHAR(16777216)[LOB]]",
            rows("SELECT SYSTEM$TYPEOF(v), SYSTEM$TYPEOF(w), SYSTEM$TYPEOF(s) FROM wide_p").toString());
    }

    @Test
    public void anAlteredOrCopiedColumnKeepsADeclaredLength() {
        engine.executeQuery("CREATE TABLE wide_l LIKE wide_p");
        assertEquals("[V VARCHAR(134217728), W VARCHAR(16777217), S VARCHAR(16777216), B BINARY(67108864),"
            + " N NUMBER(38,0)]", described("wide_l").toString());
        engine.executeQuery("ALTER TABLE wide_p ADD COLUMN z VARCHAR(134217728)");
        engine.executeQuery("ALTER TABLE wide_p ALTER COLUMN s SET DATA TYPE VARCHAR(134217728)");
        assertEquals("[V VARCHAR(134217728), W VARCHAR(16777217), S VARCHAR(134217728), B BINARY(67108864),"
            + " N NUMBER(38,0), Z VARCHAR(134217728)]", described("wide_p").toString());
    }

    @Test
    public void aViewsColumnAndAQuerysStopAtTheDefault() {
        engine.executeQuery("CREATE VIEW wide_v AS SELECT v, w, s, b, v || 'x' AS vx, n::VARCHAR AS nv FROM wide_p");
        assertEquals("[V VARCHAR(16777216), W VARCHAR(16777216), S VARCHAR(16777216), B BINARY(8388608),"
            + " VX VARCHAR(16777216), NV VARCHAR(16777216)]", described("wide_v").toString());
        assertEquals("[V|TEXT|16777216|67108864, W|TEXT|16777216|67108864, S|TEXT|16777216|67108864,"
            + " B|BINARY|null|null, VX|TEXT|16777216|67108864, NV|TEXT|16777216|67108864]",
            rows(String.format(LENGTHS, "WIDE_V")).toString());
        engine.executeQuery("CREATE TABLE wide_c AS SELECT v, w, s, b, v || 'x' AS vx FROM wide_p");
        assertEquals("[V VARCHAR(16777216), W VARCHAR(16777216), S VARCHAR(16777216), B BINARY(8388608),"
            + " VX VARCHAR(16777216)]", described("wide_c").toString());
    }
}
