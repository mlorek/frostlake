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
import dev.frostlake.storage.ResultSetColumn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DESCRIBE VIEW answers a view's COLUMN SHAPE — the same thirteen columns DESCRIBE TABLE answers, not
 * a property/value pair listing the definition.
 *
 * <p>The three relation kinds are interchangeable: DESCRIBE TABLE on a view, DESCRIBE VIEW on a table
 * and DESCRIBE MATERIALIZED VIEW on either all answer whatever the object actually is. The kind the
 * statement names decides exactly one thing — the wording when nothing by that name exists.
 *
 * <p>Source columns here are NULLABLE on purpose: live carries a NOT NULL into the view's null? cell
 * and Frostlake's derived columns do not carry nullability yet.
 */
public class DescribeViewTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE dv_base (k NUMBER(5,1), v VARCHAR(9) DEFAULT 'z',"
            + " c VARCHAR(4) COMMENT 'base comment')");
        engine.execute("CREATE VIEW dv_v AS SELECT k, v, c FROM dv_base");
    }

    private String columnNames(final ResultSet rs) {
        final StringBuilder out = new StringBuilder();
        for (final ResultSetColumn col : rs.getColumns()) {
            out.append(col.getName()).append('|');
        }
        return out.toString();
    }

    /** The shape is DESCRIBE's own thirteen, ending in the two cells that carry nothing here. */
    @Test
    public void theShapeIsTheSameThirteenAsForATable() {
        assertEquals("name|type|kind|null?|default|primary key|unique key|check|expression|comment|"
            + "policy name|privacy domain|write default|",
            columnNames(engine.executeQuery("DESCRIBE VIEW dv_v")));
        assertEquals(columnNames(engine.executeQuery("DESCRIBE TABLE dv_base")),
            columnNames(engine.executeQuery("DESCRIBE VIEW dv_v")));
    }

    /** A view describes its columns, canonically typed — not its definition. */
    @Test
    public void aViewDescribesItsColumns() {
        final ResultSet rs = engine.executeQuery("DESCRIBE VIEW dv_v");
        assertEquals(3, rs.getRows().size());
        assertEquals("NUMBER(5,1)", cell(rs, soleRowWhere(rs, "name", "K"), "type"));
        assertEquals("VARCHAR(9)", cell(rs, soleRowWhere(rs, "name", "V"), "type"));
        assertEquals("COLUMN", cell(rs, soleRowWhere(rs, "name", "K"), "kind"));
    }

    /** A derived column carries no default and no key flags — the flags read N, the default nothing. */
    @Test
    public void aDerivedColumnHasNoDefaultAndNoKeys() {
        final ResultSet rs = engine.executeQuery("DESCRIBE VIEW dv_v");
        assertNull(cell(rs, soleRowWhere(rs, "name", "V"), "default"));
        assertEquals("N", cell(rs, soleRowWhere(rs, "name", "V"), "primary key"));
        assertEquals("N", cell(rs, soleRowWhere(rs, "name", "V"), "unique key"));
    }

    /** The comment cell is the VIEW's own declared one; a base column's comment never leaks in. */
    @Test
    public void theCommentIsTheViewsOwnAndNotTheBaseColumns() {
        engine.execute("CREATE VIEW dv_cmt (a COMMENT 'col cmt', b) AS SELECT k, c FROM dv_base");
        final ResultSet rs = engine.executeQuery("DESCRIBE VIEW dv_cmt");
        assertEquals("col cmt", cell(rs, soleRowWhere(rs, "name", "A"), "comment"));
        assertNull(cell(rs, soleRowWhere(rs, "name", "B"), "comment"));
    }

    /** A materialized view carries its declared column comments the same way a plain view does. */
    @Test
    public void aMaterializedViewCarriesItsColumnComments() {
        engine.execute("CREATE MATERIALIZED VIEW dv_mv (a COMMENT 'col cmt', b)"
            + " AS SELECT k, c FROM dv_base");
        final ResultSet rs = engine.executeQuery("DESCRIBE MATERIALIZED VIEW dv_mv");
        assertEquals("col cmt", cell(rs, soleRowWhere(rs, "name", "A"), "comment"));
        assertNull(cell(rs, soleRowWhere(rs, "name", "B"), "comment"));
        assertEquals("NUMBER(5,1)", cell(rs, soleRowWhere(rs, "name", "A"), "type"));
    }

    /** DESCRIBE TABLE on a view answers the view — the object decides, not the named kind. */
    @Test
    public void describeTableDescribesAView() {
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE dv_v");
        assertEquals(3, rs.getRows().size());
        assertNull(cell(rs, soleRowWhere(rs, "name", "V"), "default"));
    }

    /** And DESCRIBE VIEW on a table answers the table, defaults and all. */
    @Test
    public void describeViewDescribesATable() {
        final ResultSet rs = engine.executeQuery("DESCRIBE VIEW dv_base");
        assertEquals(3, rs.getRows().size());
        assertEquals("'z'", cell(rs, soleRowWhere(rs, "name", "V"), "default"));
        assertEquals("base comment", cell(rs, soleRowWhere(rs, "name", "C"), "comment"));
    }

    /** The named kind is spoken only when nothing by that name exists. */
    @Test
    public void aMissingObjectIsRefusedAsTheKindTheStatementNamed() {
        assertEquals("SQL compilation error:\nView 'NOSUCH_X' does not exist or not authorized.",
            refusalOf("DESCRIBE VIEW nosuch_x"));
        assertEquals("SQL compilation error:\nTable 'NOSUCH_X' does not exist or not authorized.",
            refusalOf("DESCRIBE TABLE nosuch_x"));
        assertEquals("SQL compilation error:\n"
            + "Materialized view 'NOSUCH_X' does not exist or not authorized.",
            refusalOf("DESCRIBE MATERIALIZED VIEW nosuch_x"));
    }

    private String refusalOf(final String sql) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return ex.getMessage();
    }
}
