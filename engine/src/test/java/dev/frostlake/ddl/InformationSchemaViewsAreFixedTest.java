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
 * INFORMATION_SCHEMA's own views are the account's: no statement changes one, whichever kind of object it names,
 * nothing is created in the schema and no privilege is granted on it, GET_DDL has no definition to give, and
 * DESCRIBE lists each column with the type, the nullability and the description the account gives it.
 */
public class InformationSchemaViewsAreFixedTest extends BaseDatabaseTest {

    private static final String ROLE = "FL_M3D_IS_ROLE";
    private static final String VIEW_REFUSED =
        "SQL access control error:\nInsufficient privileges to operate on view '";
    private static final String SCHEMA_REFUSED =
        "SQL access control error:\nInsufficient privileges to operate on schema 'INFORMATION_SCHEMA'.";

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("CREATE ROLE IF NOT EXISTS " + ROLE);
    }

    @Override
    protected void teardownTest() {
        try {
            engine.execute("DROP ROLE IF EXISTS " + ROLE);
        } catch (final RuntimeException ignored) {
            // nothing to tidy
        }
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private static String view(final String name) {
        return VIEW_REFUSED + name + "'.";
    }

    /** DESCRIBE's rows as name|type|kind|null?|comment. */
    private List<String> described(final String statement) {
        final ResultSet rs = engine.executeQuery(statement);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(cell(rs, row, "name") + "|" + cell(rs, row, "type") + "|" + cell(rs, row, "kind") + "|"
                + cell(rs, row, "null?") + "|" + cell(rs, row, "comment"));
        }
        return out;
    }

    @Test
    public void noStatementChangesOne() {
        assertEquals(view("TABLES"), refusalOf("COMMENT ON VIEW test_db.INFORMATION_SCHEMA.TABLES IS 'x'"));
        assertEquals(view("TABLES"), refusalOf("COMMENT ON TABLE test_db.INFORMATION_SCHEMA.TABLES IS 'x'"));
        assertEquals(view("TABLES"),
            refusalOf("COMMENT ON COLUMN test_db.INFORMATION_SCHEMA.TABLES.TABLE_NAME IS 'x'"));
        assertEquals(view("TABLES"), refusalOf("COMMENT ON COLUMN test_db.INFORMATION_SCHEMA.TABLES.NOSUCH IS 'x'"));
        assertEquals(view("TABLES"), refusalOf("ALTER VIEW test_db.INFORMATION_SCHEMA.TABLES SET COMMENT = 'x'"));
        assertEquals(view("TABLES"), refusalOf("ALTER VIEW test_db.INFORMATION_SCHEMA.TABLES SET SECURE"));
        assertEquals(view("TABLES"), refusalOf("ALTER VIEW test_db.INFORMATION_SCHEMA.TABLES UNSET SECURE"));
        assertEquals(view("TABLES"), refusalOf("ALTER TABLE test_db.INFORMATION_SCHEMA.TABLES ADD COLUMN zz INT"));
        assertEquals(view("TABLES"), refusalOf("DROP VIEW test_db.INFORMATION_SCHEMA.TABLES"));
        assertEquals(view("TABLES"),
            refusalOf("CREATE OR REPLACE VIEW test_db.INFORMATION_SCHEMA.TABLES AS SELECT 1 a"));
        assertEquals(view("TABLES"), refusalOf("TRUNCATE TABLE test_db.INFORMATION_SCHEMA.TABLES"));
        assertEquals(view("SEQUENCES"),
            refusalOf("INSERT INTO test_db.INFORMATION_SCHEMA.SEQUENCES (SEQUENCE_NAME) VALUES ('x')"));
        assertEquals(view("SEQUENCES"), refusalOf("DELETE FROM test_db.INFORMATION_SCHEMA.SEQUENCES"));
        assertEquals(view("SEQUENCES"), refusalOf("UPDATE test_db.INFORMATION_SCHEMA.SEQUENCES SET COMMENT = 'x'"));
    }

    @Test
    public void aNameTheSchemaDoesNotHoldIsMissingAsAnywhere() {
        assertEquals(hinted("SQL compilation error:\nView 'TEST_DB.INFORMATION_SCHEMA.NOSUCH' does not exist or not"
            + " authorized."), refusalOf("COMMENT ON VIEW test_db.INFORMATION_SCHEMA.NOSUCH IS 'x'"));
        assertEquals(hinted("SQL compilation error:\nView 'TEST_DB.INFORMATION_SCHEMA.NOSUCH' does not exist or not"
            + " authorized."), refusalOf("ALTER VIEW test_db.INFORMATION_SCHEMA.NOSUCH SET SECURE"));
        assertEquals(hinted("SQL compilation error:\nView 'TEST_DB.INFORMATION_SCHEMA.NOSUCH' does not exist or not"
            + " authorized."), refusalOf("SELECT GET_DDL('VIEW', 'test_db.INFORMATION_SCHEMA.NOSUCH')"));
    }

    @Test
    public void nothingIsCreatedInTheSchemaAndNoPrivilegeIsGrantedOnIt() {
        assertEquals(SCHEMA_REFUSED, refusalOf("CREATE VIEW test_db.INFORMATION_SCHEMA.MYV AS SELECT 1 a"));
        assertEquals(SCHEMA_REFUSED, refusalOf("CREATE TABLE test_db.INFORMATION_SCHEMA.MYT (a INT)"));
        assertEquals(SCHEMA_REFUSED, refusalOf("CREATE SEQUENCE test_db.INFORMATION_SCHEMA.myseq"));
        assertEquals(SCHEMA_REFUSED, refusalOf("CREATE STAGE test_db.INFORMATION_SCHEMA.mystage"));
        assertEquals(SCHEMA_REFUSED,
            refusalOf("CREATE FUNCTION test_db.INFORMATION_SCHEMA.myfn() RETURNS INT AS '1'"));
        assertEquals(SCHEMA_REFUSED, refusalOf("COMMENT ON SCHEMA test_db.INFORMATION_SCHEMA IS 'x'"));
        assertEquals(SCHEMA_REFUSED, refusalOf("ALTER SCHEMA test_db.INFORMATION_SCHEMA SET COMMENT = 'x'"));
        assertEquals(SCHEMA_REFUSED,
            refusalOf("GRANT USAGE ON SCHEMA test_db.INFORMATION_SCHEMA TO ROLE " + ROLE));
        assertEquals(SCHEMA_REFUSED,
            refusalOf("GRANT SELECT ON ALL VIEWS IN SCHEMA test_db.INFORMATION_SCHEMA TO ROLE " + ROLE));
        assertEquals(view("TABLES"),
            refusalOf("GRANT SELECT ON VIEW test_db.INFORMATION_SCHEMA.TABLES TO ROLE " + ROLE));
        assertEquals(view("TABLES"),
            refusalOf("GRANT SELECT ON TABLE test_db.INFORMATION_SCHEMA.TABLES TO ROLE " + ROLE));
        assertEquals(view("TABLES"),
            refusalOf("REVOKE SELECT ON VIEW test_db.INFORMATION_SCHEMA.TABLES FROM ROLE " + ROLE));
    }

    @Test
    public void getDdlHasNoDefinitionToGive() {
        final String refused = "SQL compilation error:\nObject does not exist, or operation cannot be performed.";
        assertEquals(refused, refusalOf("SELECT GET_DDL('VIEW', 'test_db.INFORMATION_SCHEMA.TABLES')"));
        assertEquals(refused, refusalOf("SELECT GET_DDL('TABLE', 'test_db.INFORMATION_SCHEMA.TABLES')"));
    }

    @Test
    public void describeListsEachColumnWithItsDescription() {
        final List<String> views = described("DESCRIBE TABLE test_db.INFORMATION_SCHEMA.VIEWS");
        assertEquals(14, views.size());
        assertEquals("TABLE_CATALOG|VARCHAR(16777216)|COLUMN|N|Database that the view belongs to", views.get(0));
        assertEquals("IS_SECURE|VARCHAR(3)|COLUMN|Y|Whether this view is secure.", views.get(8));
        assertEquals("CREATED|TIMESTAMP_LTZ(9)|COLUMN|Y|Creation time of the view", views.get(9));
        assertEquals("COMMENT|VARCHAR(16777216)|COLUMN|Y|Comment for this view", views.get(13));
        final List<String> sequences = described("DESCRIBE VIEW test_db.INFORMATION_SCHEMA.SEQUENCES");
        assertEquals(18, sequences.size());
        assertEquals("SEQUENCE_CATALOG|VARCHAR(16777216)|COLUMN|N|Database that the sequence belongs to",
            sequences.get(0));
        assertEquals(sequences, described("DESCRIBE VIEW INFORMATION_SCHEMA.SEQUENCES"));
        // Every view the schema lists is described.
        final ResultSet listed = engine.executeQuery("SELECT TABLE_NAME FROM test_db.INFORMATION_SCHEMA.VIEWS"
            + " WHERE TABLE_SCHEMA = 'INFORMATION_SCHEMA' ORDER BY 1");
        final List<Row> names = listed.getRows();
        assertEquals(63, names.size());
        final String first = String.valueOf(names.get(0).getValue(0));
        final String last = String.valueOf(names.get(names.size() - 1).getValue(0));
        assertTrue(!described("DESCRIBE VIEW test_db.INFORMATION_SCHEMA." + first).isEmpty(), first);
        assertTrue(!described("DESCRIBE VIEW test_db.INFORMATION_SCHEMA." + last).isEmpty(), last);
    }
}
