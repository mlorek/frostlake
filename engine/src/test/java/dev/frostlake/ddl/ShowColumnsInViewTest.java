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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SHOW COLUMNS IN VIEW describes a view's columns the same way it describes a table's — the same JSON
 * descriptor, off the column list the view froze when it was created. A view carries its columns
 * whether or not it declared a column list, and it must be named by its FULL search path, which is
 * the one place this statement is stricter than its TABLE form.
 *
 * <p>Every source column here is NULLABLE on purpose. Live carries a source column's NOT NULL through
 * a view when the projected item is a bare column reference, and Frostlake's derived columns do not
 * carry nullability yet — so a NOT NULL source would be the one cell the two sides disagree on.
 */
public class ShowColumnsInViewTest extends BaseDatabaseTest {

    private static final String NUMBER_5_1 = """
        {"type":"FIXED","precision":5,"scale":1,"nullable":true}""";
    private static final String VARCHAR_9 = """
        {"type":"TEXT","length":9,"byteLength":36,"nullable":true,"fixed":false}""";
    private static final String DATE = """
        {"type":"DATE","nullable":true}""";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE vc_base (id NUMBER(5,1), txt VARCHAR(9), d DATE)");
    }

    private ResultSet columnsOf(final String viewName) {
        return engine.executeQuery("SHOW COLUMNS IN VIEW test_db.test_schema." + viewName);
    }

    /** A view without a declared column list still describes every column it projects. */
    @Test
    public void aViewWithoutAColumnListDescribesItsColumns() {
        engine.execute("CREATE VIEW vc_plain AS SELECT id, txt, d FROM vc_base");
        final ResultSet rs = columnsOf("vc_plain");
        assertEquals(3, rs.getRows().size());
        assertEquals(NUMBER_5_1, cell(rs, soleRowWhere(rs, "column_name", "ID"), "data_type"));
        assertEquals(VARCHAR_9, cell(rs, soleRowWhere(rs, "column_name", "TXT"), "data_type"));
        assertEquals(DATE, cell(rs, soleRowWhere(rs, "column_name", "D"), "data_type"));
    }

    /** SELECT * projects the base table's columns, types and all. */
    @Test
    public void selectStarCarriesEveryColumnOfTheBase() {
        engine.execute("CREATE VIEW vc_star AS SELECT * FROM vc_base");
        final ResultSet rs = columnsOf("vc_star");
        assertEquals(3, rs.getRows().size());
        assertEquals(VARCHAR_9, cell(rs, soleRowWhere(rs, "column_name", "TXT"), "data_type"));
    }

    /** A declared column list renames the columns; each keeps the type it was projected from. */
    @Test
    public void aDeclaredColumnListRenamesButKeepsTheTypes() {
        engine.execute("CREATE VIEW vc_named (a, b) AS SELECT id, txt FROM vc_base");
        final ResultSet rs = columnsOf("vc_named");
        assertEquals(2, rs.getRows().size());
        assertEquals(NUMBER_5_1, cell(rs, soleRowWhere(rs, "column_name", "A"), "data_type"));
        assertEquals(VARCHAR_9, cell(rs, soleRowWhere(rs, "column_name", "B"), "data_type"));
    }

    /** An alias in the SELECT list renames the same way. */
    @Test
    public void anAliasedItemIsReportedUnderItsAlias() {
        engine.execute("CREATE VIEW vc_alias AS SELECT txt AS renamed FROM vc_base");
        final ResultSet rs = columnsOf("vc_alias");
        assertEquals(VARCHAR_9, cell(rs, soleRowWhere(rs, "column_name", "RENAMED"), "data_type"));
    }

    /** The view is reported under its OWN database and schema, and its columns are plain COLUMNs. */
    @Test
    public void theRowNamesTheViewAndItsContainer() {
        engine.execute("CREATE VIEW vc_where AS SELECT txt FROM vc_base");
        final ResultSet rs = columnsOf("vc_where");
        assertEquals("VC_WHERE", cell(rs, rs.getRows().get(0), "table_name"));
        assertEquals("TEST_SCHEMA", cell(rs, rs.getRows().get(0), "schema_name"));
        assertEquals("TEST_DB", cell(rs, rs.getRows().get(0), "database_name"));
        assertEquals("COLUMN", cell(rs, rs.getRows().get(0), "kind"));
    }

    /**
     * The comment cell carries the view's OWN declared column comment, and the empty string — never
     * NULL — where there is none. A base column's comment does not reach a view's row.
     */
    @Test
    public void theCommentCellIsTheViewsOwn() {
        engine.execute("CREATE TABLE vc_cmt (k NUMBER COMMENT 'base cmt', w VARCHAR(4))");
        engine.execute("CREATE VIEW vc_vc (a COMMENT 'col cmt', b) AS SELECT k, w FROM vc_cmt");
        final ResultSet declared = columnsOf("vc_vc");
        assertEquals("col cmt", cell(declared, soleRowWhere(declared, "column_name", "A"), "comment"));
        assertEquals("", cell(declared, soleRowWhere(declared, "column_name", "B"), "comment"));

        engine.execute("CREATE VIEW vc_vp AS SELECT k, w FROM vc_cmt");
        final ResultSet plain = columnsOf("vc_vp");
        assertEquals("", cell(plain, soleRowWhere(plain, "column_name", "K"), "comment"));
        assertEquals("", cell(plain, soleRowWhere(plain, "column_name", "W"), "comment"));
    }

    /** The one asymmetry with the TABLE form: a view must be named by its full search path. */
    @Test
    public void anUnqualifiedViewNameIsRefused() {
        engine.execute("CREATE VIEW vc_short AS SELECT txt FROM vc_base");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SHOW COLUMNS IN VIEW vc_short");
            }
        });
        assertEquals("SQL compilation error:\nMust specify the full search path"
            + " starting from database for VC_SHORT", ex.getMessage());
    }
}
