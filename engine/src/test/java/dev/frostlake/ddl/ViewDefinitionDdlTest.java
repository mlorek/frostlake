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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * INFORMATION_SCHEMA.VIEWS.VIEW_DEFINITION and SHOW VIEWS' {@code text} column carry a view's FULL
 * executable {@code CREATE OR REPLACE VIEW} statement, not just its query expression — Snowflake
 * deployment tooling recreates views with {@code EXECUTE IMMEDIATE} of that text, so a bare SELECT
 * there silently turns the recreate step into a no-op query.
 */
public class ViewDefinitionDdlTest extends BaseDatabaseTest {

    private String viewDefinitionOf(final String viewName) {
        final ResultSet rs = engine.executeQuery(
            "SELECT view_definition FROM information_schema.views WHERE table_name = '" + viewName + "'");
        assertEquals(1, rs.getRowCount(), "expected exactly one metadata row for " + viewName);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void viewDefinitionIsTheOriginalCreateStatementVerbatim() {
        // Live-verified: Snowflake surfaces the statement exactly as typed — original case, no
        // normalization, no schema qualification, no added OR REPLACE.
        engine.execute("CREATE TABLE orders (id INTEGER, amount NUMBER(10,2))");
        engine.execute("CREATE VIEW big_orders AS SELECT id, amount FROM orders WHERE amount > 100");

        final String ddl = viewDefinitionOf("BIG_ORDERS");
        assertEquals("CREATE VIEW big_orders AS SELECT id, amount FROM orders WHERE amount > 100", ddl);
    }

    @Test
    public void viewDefinitionIsExecutableAndRecreatesTheView() {
        engine.execute("CREATE TABLE customers (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO customers VALUES (1, 'a'), (2, 'b')");
        // The verbatim text is re-executable when the original statement says OR REPLACE — the
        // deployment-tooling idiom this test guards.
        engine.execute("CREATE OR REPLACE VIEW customer_names AS SELECT name FROM customers");

        final String ddl = viewDefinitionOf("CUSTOMER_NAMES");
        engine.execute(ddl);

        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM customer_names");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void secureViewAndExplicitColumnListSurviveInTheDdl() {
        engine.execute("CREATE TABLE products (id INTEGER, price NUMBER)");
        engine.execute("CREATE OR REPLACE SECURE VIEW priced (pid, cost) AS SELECT id, price FROM products");

        final String ddl = viewDefinitionOf("PRICED");
        assertEquals("CREATE OR REPLACE SECURE VIEW priced (pid, cost) AS SELECT id, price FROM products",
            ddl);
        engine.execute(ddl);
    }

    @Test
    public void showViewsTextMatchesTheFullDdl() {
        engine.execute("CREATE TABLE emp (id INTEGER)");
        engine.execute("CREATE VIEW emp_view AS SELECT id FROM emp");

        final ResultSet rs = engine.executeQuery("SHOW VIEWS");
        final int nameIdx = rs.getColumnIndex("name");
        final int textIdx = rs.getColumnIndex("text");
        String text = null;
        for (final Row row : rs.getRows()) {
            if ("EMP_VIEW".equals(String.valueOf(row.getValue(nameIdx)))) {
                text = String.valueOf(row.getValue(textIdx));
            }
        }
        assertEquals("CREATE VIEW emp_view AS SELECT id FROM emp", text);
    }

    @Test
    public void showMaterializedViewsTextIsTheFullDdl() {
        engine.execute("CREATE TABLE dept (id INTEGER)");
        engine.execute("CREATE MATERIALIZED VIEW dept_mv AS SELECT id FROM dept");

        final ResultSet rs = engine.executeQuery("SHOW MATERIALIZED VIEWS");
        final int nameIdx = rs.getColumnIndex("name");
        final int textIdx = rs.getColumnIndex("text");
        String text = null;
        for (final Row row : rs.getRows()) {
            if ("DEPT_MV".equals(String.valueOf(row.getValue(nameIdx)))) {
                text = String.valueOf(row.getValue(textIdx));
            }
        }
        assertEquals("CREATE MATERIALIZED VIEW dept_mv AS SELECT id FROM dept", text);
    }

    @Test
    public void getDdlStaysBareNamed() {
        // Snowflake's GET_DDL renders the object name unqualified by default; the metadata views are
        // the schema-qualified surfaces. Guard that delegating both to one renderer kept them apart.
        // GET_DDL (and only GET_DDL) also renders the output column list (live-verified shape).
        engine.execute("CREATE TABLE items (id INTEGER)");
        engine.execute("CREATE VIEW item_view AS SELECT id FROM items");

        final ResultSet rs = engine.executeQuery("SELECT GET_DDL('VIEW', 'item_view')");
        assertEquals("create or replace view ITEM_VIEW(\n\tID\n) as SELECT id FROM items;",
            String.valueOf(rs.getRows().get(0).getValue(0)));
    }

    @Test
    public void scriptedRecreateLoopOverViewDefinitionWorks() {
        // The vendor deployment pattern: fetch VIEW_DEFINITION into a scripting variable and
        // EXECUTE IMMEDIATE it to recreate the view.
        engine.execute("CREATE TABLE clients (id INTEGER)");
        engine.execute("CREATE OR REPLACE VIEW client_view AS SELECT id FROM clients");

        final ResultSet rs = engine.executeQuery("""
            EXECUTE IMMEDIATE $$
            DECLARE
              d VARCHAR;
            BEGIN
              SELECT view_definition INTO :d FROM information_schema.views WHERE table_name = 'CLIENT_VIEW';
              EXECUTE IMMEDIATE d;
              RETURN 'recreated';
            END;
            $$""");
        assertEquals("recreated", String.valueOf(rs.getRows().get(0).getValue(0)));
        assertEquals(0, engine.executeQuery("SELECT * FROM client_view").getRowCount());
    }
}
