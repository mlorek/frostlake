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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The ALTER TABLE action surface, measured cell by cell on a real account: ADD and DROP take
 * comma-separated column lists (each with its own live refusal shape — a duplicate is
 * {@code column 'X' already exists}, a missing one {@code column 'X' does not exist}); RENAME
 * COLUMN speaks in OBJECT terms, spelling a taken target {@code table.column}; the column-action
 * list runs parenthesized or bare and includes COMMENT / UNSET COMMENT; a second PRIMARY KEY
 * refuses naming the bare table; properties refuse as the 1420/1008 families; a refused RENAME TO
 * leaves the table untouched; SWAP WITH exchanges the two tables' whole definitions; and ALTER
 * COLUMN on a column the table does not hold is a POSITIONED refusal pointing at the identifier.
 */
public class AlterTableSurfaceTest extends BaseDatabaseTest {

    private RuntimeException refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
    }

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void addAndDropTakeColumnLists() {
        engine.execute("CREATE TABLE alt_t (a INTEGER)");
        engine.execute("ALTER TABLE alt_t ADD COLUMN c1 INTEGER");
        engine.execute("ALTER TABLE alt_t ADD COLUMN c2 INTEGER, c3 INTEGER");
        engine.execute("ALTER TABLE alt_t ADD c4 INTEGER");
        engine.execute("ALTER TABLE alt_t ADD COLUMN IF NOT EXISTS c1 INTEGER");

        assertEquals("SQL compilation error:\ncolumn 'C1' already exists",
            refusal("ALTER TABLE alt_t ADD COLUMN c1 INTEGER").getMessage());
        // The duplicate check also catches a name repeated WITHIN one statement.
        assertEquals("SQL compilation error:\ncolumn 'C5' already exists",
            refusal("ALTER TABLE alt_t ADD COLUMN c5 INTEGER, c5 INTEGER").getMessage());

        engine.execute("ALTER TABLE alt_t DROP COLUMN c2, c3");
        engine.execute("ALTER TABLE alt_t DROP COLUMN IF EXISTS nosuch");
        assertEquals("SQL compilation error:\ncolumn 'NOSUCH' does not exist",
            refusal("ALTER TABLE alt_t DROP COLUMN nosuch").getMessage());
    }

    @Test
    public void renameColumnSpeaksInObjectTerms() {
        engine.execute("CREATE TABLE ren_t (a INTEGER, b VARCHAR)");
        engine.execute("ALTER TABLE ren_t RENAME COLUMN b TO c");

        assertEquals("SQL compilation error:\nObject 'NOSUCH' does not exist or not authorized.",
            refusal("ALTER TABLE ren_t RENAME COLUMN nosuch TO x").getMessage());
        assertEquals("SQL compilation error:\nObject 'REN_T.A' already exists.",
            refusal("ALTER TABLE ren_t RENAME COLUMN c TO a").getMessage());
    }

    @Test
    public void columnActionListsRunParenthesizedOrBareWithComments() {
        engine.execute("CREATE TABLE cols_t (a INTEGER, b VARCHAR(10))");
        engine.execute("ALTER TABLE cols_t ALTER COLUMN b SET DATA TYPE VARCHAR(20)");
        engine.execute("ALTER TABLE cols_t ALTER COLUMN b TYPE VARCHAR(30)");
        engine.execute("ALTER TABLE cols_t MODIFY COLUMN b VARCHAR(40)");
        engine.execute("ALTER TABLE cols_t ALTER (COLUMN a SET NOT NULL, COLUMN b SET NOT NULL)");
        engine.execute("ALTER TABLE cols_t ALTER COLUMN a DROP NOT NULL, COLUMN b DROP NOT NULL");
        engine.execute("ALTER TABLE cols_t ALTER COLUMN b COMMENT 'col comment'");
        engine.execute("ALTER TABLE cols_t ALTER COLUMN b UNSET COMMENT");
    }

    @Test
    public void aSecondPrimaryKeyRefusesNamingTheBareTable() {
        engine.execute("CREATE TABLE pk_t (a INTEGER, b VARCHAR)");
        engine.execute("ALTER TABLE pk_t ADD PRIMARY KEY (a)");
        assertEquals("SQL compilation error:\nprimary key already exists for table 'PK_T'",
            refusal("ALTER TABLE pk_t ADD PRIMARY KEY (b)").getMessage());
        engine.execute("ALTER TABLE pk_t DROP PRIMARY KEY");
        engine.execute("ALTER TABLE pk_t ADD PRIMARY KEY (b)");
    }

    @Test
    public void tablePropertiesRefuseAsTheirFamilies() {
        engine.execute("CREATE TABLE prop_t (a INTEGER)");
        engine.execute("ALTER TABLE prop_t SET DATA_RETENTION_TIME_IN_DAYS = 0");
        assertEquals("SQL compilation error:\ninvalid value [-1] for parameter"
                + " 'DATA_RETENTION_TIME_IN_DAYS'",
            refusal("ALTER TABLE prop_t SET DATA_RETENTION_TIME_IN_DAYS = -1").getMessage());
        assertEquals("SQL compilation error:\ninvalid property 'NO_SUCH_PROP' for 'TABLE'",
            refusal("ALTER TABLE prop_t SET NO_SUCH_PROP = 1").getMessage());
    }

    @Test
    public void clusterKeysSetAndDrop() {
        engine.execute("CREATE TABLE clu_t (a INTEGER)");
        engine.execute("ALTER TABLE clu_t CLUSTER BY (a)");
        engine.execute("ALTER TABLE clu_t DROP CLUSTERING KEY");
    }

    @Test
    public void aRefusedRenameLeavesTheTableUntouched() {
        engine.execute("CREATE TABLE src_t (a INTEGER)");
        engine.execute("INSERT INTO src_t VALUES (7)");
        engine.execute("CREATE TABLE taken_t (z INTEGER)");

        assertEquals("SQL compilation error:\nObject 'TAKEN_T' already exists.",
            refusal("ALTER TABLE src_t RENAME TO taken_t").getMessage());
        // The refusal mutated nothing: the source still answers under its own name.
        assertEquals("7", String.valueOf(scalar("SELECT a FROM src_t")));

        engine.execute("ALTER TABLE src_t RENAME TO renamed_t");
        assertEquals("7", String.valueOf(scalar("SELECT a FROM renamed_t")));

        assertEquals("SQL compilation error:\nTable 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist"
                + " or not authorized.",
            refusal("ALTER TABLE nosuch_t ADD COLUMN x INTEGER").getMessage());
        engine.execute("ALTER TABLE IF EXISTS nosuch_t ADD COLUMN x INTEGER");
    }

    @Test
    public void swapExchangesTheWholeDefinitions() {
        engine.execute("CREATE TABLE swp_a (a INTEGER)");
        engine.execute("INSERT INTO swp_a VALUES (1)");
        engine.execute("CREATE TABLE swp_b (z VARCHAR)");
        engine.execute("INSERT INTO swp_b VALUES ('zed')");

        engine.execute("ALTER TABLE swp_a SWAP WITH swp_b");
        // Each name now answers with the OTHER table's columns and rows.
        assertEquals("zed", scalar("SELECT z FROM swp_a"));
        assertEquals("1", String.valueOf(scalar("SELECT a FROM swp_b")));

        assertEquals("SQL compilation error:\nTable 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist"
                + " or not authorized.",
            refusal("ALTER TABLE swp_a SWAP WITH nosuch_t").getMessage());
    }

    @Test
    public void alterColumnOnAMissingColumnIsAPositionedRefusal() {
        engine.execute("CREATE TABLE pos_t (a INTEGER)");
        // The prefix line points at the identifier: position 31 is where `ghost` starts.
        assertEquals("SQL compilation error: error line 1 at position 31\ninvalid identifier 'GHOST'",
            refusal("ALTER TABLE pos_t ALTER COLUMN ghost SET NOT NULL").getMessage());
    }

    @Test
    public void alterColumnSetDefaultLiteralStaysUnsupported() {
        engine.execute("CREATE TABLE def_t (a INTEGER)");
        assertEquals("Unsupported feature 'Alter Column Set Default'.",
            refusal("ALTER TABLE def_t ALTER COLUMN a SET DEFAULT 5").getMessage());
    }

    @Test
    public void columnCommentSurvivesIntoDescribe() {
        engine.execute("CREATE TABLE dsc_t (a INTEGER)");
        engine.execute("ALTER TABLE dsc_t ALTER COLUMN a COMMENT 'the a column'");
        final ResultSet rs = engine.executeQuery("DESC TABLE dsc_t");
        final Row row = rs.getRows().get(0);
        assertEquals("the a column", row.getValue(rs.getColumnIndex("comment")));
    }
}
