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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A collation travels with the column it is settled on. A projection stamps it on the column it
 * produces, so a subquery, a CTE, a view and a CTAS all carry it out: the enclosing query compares,
 * sorts and groups the derived column under it, {@code COLLATION()} names it, and DESCRIBE and SHOW
 * COLUMNS report it on the stored relation. Live-verified.
 */
public class DerivedCollationTest extends BaseDatabaseTest {

    /** A table whose one text column is declared case-insensitive, holding 'a' and 'A'. */
    private void createCaseInsensitiveSource() {
        engine.execute("CREATE OR REPLACE TABLE der_src (c VARCHAR COLLATE 'en-ci', n NUMBER)");
        engine.execute("INSERT INTO der_src VALUES ('a',1),('A',2)");
    }

    /** The {@code type} cell a DESCRIBE reports for one column. */
    private String describedType(final String describe, final String columnName) {
        final ResultSet rs = engine.executeQuery(describe);
        for (int i = 0; i < rs.getRowCount(); i++) {
            final Object name = rs.getRows().get(i).getValue(rs.getColumnIndex("name"));
            if (name != null && columnName.equalsIgnoreCase(name.toString())) {
                final Object type = rs.getRows().get(i).getValue(rs.getColumnIndex("type"));
                return type == null ? "" : type.toString();
            }
        }
        return "";
    }

    /** The single cell of a single-row query, as text. */
    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount());
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    /** A subquery's projected column keeps the collation the item settled on. */
    @Test
    public void aSubqueryCarriesTheCollation() {
        createCaseInsensitiveSource();
        assertEquals("1", scalar("SELECT COUNT(*) FROM (SELECT 'a' COLLATE 'en-ci' AS k) WHERE k = 'A'"));
        assertEquals("2", scalar("SELECT COUNT(*) FROM (SELECT c AS k FROM der_src) WHERE k = 'A'"));
        assertEquals("2", scalar("SELECT COUNT(*) FROM (SELECT c FROM der_src) WHERE c = 'A'"));
        assertEquals("en-ci", scalar("SELECT COLLATION(k) FROM (SELECT c AS k FROM der_src) LIMIT 1"));
        assertEquals("en-ci", scalar("SELECT COLLATION(k) FROM (SELECT 'a' COLLATE 'en-ci' AS k)"));
    }

    /** A CTE carries it too, and the outer query sorts and groups the derived column under it. */
    @Test
    public void aCteCarriesTheCollation() {
        createCaseInsensitiveSource();
        assertEquals("2", scalar("WITH x AS (SELECT c AS k FROM der_src) SELECT COUNT(*) FROM x WHERE k = 'A'"));
        final ResultSet grouped = engine.executeQuery(
            "SELECT k, COUNT(*) FROM (SELECT c AS k FROM der_src) GROUP BY 1");
        assertEquals(1, grouped.getRowCount());
        assertEquals("A", grouped.getRows().get(0).getValue(0).toString());
        assertEquals("2", grouped.getRows().get(0).getValue(1).toString());
    }

    /** A view's columns inherit it: the view's own predicates collate, and DESCRIBE reports it. */
    @Test
    public void aViewCarriesTheCollation() {
        createCaseInsensitiveSource();
        engine.execute("CREATE OR REPLACE VIEW der_view_literal AS SELECT 'a' COLLATE 'en-ci' AS k");
        engine.execute("CREATE OR REPLACE VIEW der_view_column AS SELECT c AS k FROM der_src");
        assertEquals("1", scalar("SELECT COUNT(*) FROM der_view_literal WHERE k = 'A'"));
        assertEquals("2", scalar("SELECT COUNT(*) FROM der_view_column WHERE k = 'A'"));
        assertEquals("en-ci", scalar("SELECT COLLATION(k) FROM der_view_column LIMIT 1"));
        final String viewType = describedType("DESCRIBE VIEW der_view_column", "K");
        assertTrue(viewType.contains("COLLATE 'en-ci'"),
            "a view's described column should carry its collation: " + viewType);
    }

    /** A CTAS stores it, so the created table collates, describes and reports it like a declared one. */
    @Test
    public void aCtasStoresTheCollation() {
        createCaseInsensitiveSource();
        engine.execute("CREATE OR REPLACE TABLE der_ctas_literal AS SELECT 'a' COLLATE 'en-ci' AS k");
        assertEquals("en-ci", scalar("SELECT COLLATION(k) FROM der_ctas_literal"));
        assertEquals("1", scalar("SELECT COUNT(*) FROM der_ctas_literal WHERE k = 'A'"));

        engine.execute("CREATE OR REPLACE TABLE der_ctas_column AS SELECT c FROM der_src");
        assertEquals("en-ci", scalar("SELECT COLLATION(c) FROM der_ctas_column LIMIT 1"));
        final String ctasType = describedType("DESCRIBE TABLE der_ctas_column", "C");
        assertTrue(ctasType.contains("COLLATE 'en-ci'"),
            "a CTAS column should carry its collation: " + ctasType);

        engine.execute("CREATE OR REPLACE TABLE der_ctas_expr AS SELECT c || 'x' AS k FROM der_src");
        assertEquals("en-ci", scalar("SELECT COLLATION(k) FROM der_ctas_expr LIMIT 1"));
    }

    /** SHOW COLUMNS names the collation inside the stored column's type descriptor. */
    @Test
    public void showColumnsNamesTheCollationOfACtasColumn() {
        engine.execute("CREATE OR REPLACE TABLE der_ctas_shown AS SELECT 'a' COLLATE 'en-ci' AS k");
        final ResultSet shown = engine.executeQuery("SHOW COLUMNS IN TABLE der_ctas_shown");
        assertEquals(1, shown.getRowCount());
        final Object descriptor = shown.getRows().get(0).getValue(shown.getColumnIndex("data_type"));
        assertTrue(String.valueOf(descriptor).contains("\"collation\":\"en-ci\""),
            "the descriptor should name the collation: " + descriptor);
    }
}
