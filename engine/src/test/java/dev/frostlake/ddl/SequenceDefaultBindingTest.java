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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A column DEFAULT that draws from a sequence is bound to the sequence itself (live-verified). Every surface
 * names the sequence in full under its current name; a renamed sequence is followed; a table made LIKE or as a
 * CLONE draws from the same sequence; and once the sequence is dropped the column has nothing to draw from, even
 * when a sequence of the same name is created again. A name no sequence carries is refused at CREATE TABLE.
 */
public class SequenceDefaultBindingTest extends BaseDatabaseTest {

    private static final String LOST = "SQL compilation error:\nSequence used as a default value in table"
        + " 'TEST_DB.TEST_SCHEMA.SDB_TD' column 'A' was not found or could not be accessed.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE SEQUENCE sdb_s7");
        engine.execute("CREATE TABLE sdb_td (a INT DEFAULT sdb_s7.nextval, b INT)");
    }

    private String describedDefault(final String table) {
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE " + table);
        return String.valueOf(rs.getRows().get(0).getValue(rs.getColumnIndex("default")));
    }

    private String ddl(final String table) {
        return String.valueOf(engine.executeQuery("SELECT GET_DDL('TABLE', '" + table + "')").getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                final ResultSet rs = engine.executeQuery(sql);
                while (rs.next()) {
                    continue;
                }
            }
        }, sql).getMessage();
    }

    @Test
    public void everySurfaceNamesTheSequenceInFull() {
        assertEquals("TEST_DB.TEST_SCHEMA.SDB_S7.NEXTVAL", describedDefault("sdb_td"));
        assertEquals("TEST_DB.TEST_SCHEMA.SDB_S7.NEXTVAL", String.valueOf(engine.executeQuery(
            "SELECT column_default FROM information_schema.columns WHERE table_name = 'SDB_TD' AND column_name = 'A'")
            .getRows().get(0).getValue(0)));
        assertTrue(ddl("sdb_td").contains("A NUMBER(38,0) DEFAULT TEST_DB.TEST_SCHEMA.SDB_S7.NEXTVAL,"), ddl("sdb_td"));
        final ResultSet columns = engine.executeQuery("SHOW COLUMNS IN TABLE sdb_td");
        final Row a = columns.getRows().get(0);
        assertEquals("", String.valueOf(a.getValue(columns.getColumnIndex("default"))));
        assertEquals("TEST_DB.TEST_SCHEMA.SDB_S7.NEXTVAL", String.valueOf(a.getValue(columns.getColumnIndex("autoincrement"))));
    }

    @Test
    public void aRenamedSequenceIsFollowed() {
        engine.execute("INSERT INTO sdb_td (b) VALUES (1)");
        engine.execute("ALTER SEQUENCE sdb_s7 RENAME TO sdb_s8");
        engine.execute("INSERT INTO sdb_td (b) VALUES (2)");
        assertEquals("2", String.valueOf(engine.executeQuery("SELECT COUNT(a) FROM sdb_td").getRows().get(0).getValue(0)));
        assertEquals("TEST_DB.TEST_SCHEMA.SDB_S8.NEXTVAL", describedDefault("sdb_td"));
        assertTrue(ddl("sdb_td").contains("DEFAULT TEST_DB.TEST_SCHEMA.SDB_S8.NEXTVAL,"), ddl("sdb_td"));
        engine.execute("CREATE TABLE sdb_like LIKE sdb_td");
        engine.execute("CREATE TABLE sdb_clone CLONE sdb_td");
        assertTrue(ddl("sdb_like").contains("DEFAULT TEST_DB.TEST_SCHEMA.SDB_S8.NEXTVAL,"), ddl("sdb_like"));
        assertTrue(ddl("sdb_clone").contains("DEFAULT TEST_DB.TEST_SCHEMA.SDB_S8.NEXTVAL,"), ddl("sdb_clone"));
    }

    @Test
    public void aDroppedSequenceLeavesTheColumnNothingToDrawFrom() {
        engine.execute("DROP SEQUENCE sdb_s7");
        assertEquals(LOST, refusal("INSERT INTO sdb_td (b) VALUES (3)"));
        assertEquals(LOST, refusal("SELECT GET_DDL('TABLE', 'sdb_td')"));
        assertEquals("[sequence cannot be found or accessed]", describedDefault("sdb_td"));
        engine.execute("CREATE SEQUENCE sdb_s7");
        assertEquals(LOST, refusal("INSERT INTO sdb_td (b) VALUES (3)"));
        engine.execute("CREATE SEQUENCE sdb_s9");
        engine.execute("ALTER TABLE sdb_td ALTER COLUMN a SET DEFAULT sdb_s9.NEXTVAL");
        assertTrue(ddl("sdb_td").contains("DEFAULT TEST_DB.TEST_SCHEMA.SDB_S9.NEXTVAL,"), ddl("sdb_td"));
        engine.execute("INSERT INTO sdb_td (b) VALUES (3)");
    }

    @Test
    public void aSequenceElsewhereAndAQuotedOneAreNamedAsTheyAre() {
        engine.execute("CREATE SCHEMA sdb_other");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE SEQUENCE sdb_other.so1");
        engine.execute("CREATE TABLE sdb_td2 (a INT DEFAULT sdb_other.so1.nextval, b INT)");
        assertEquals("TEST_DB.SDB_OTHER.SO1.NEXTVAL", describedDefault("sdb_td2"));
        engine.execute("CREATE SEQUENCE \"lowSeq\"");
        engine.execute("CREATE TABLE sdb_td4 (a INT DEFAULT \"lowSeq\".nextval, b INT)");
        assertTrue(ddl("sdb_td4").contains("DEFAULT TEST_DB.TEST_SCHEMA.\"lowSeq\".NEXTVAL,"), ddl("sdb_td4"));
    }

    @Test
    public void aClonedSchemaDrawsFromItsOwnSequences() {
        try {
            engine.execute("CREATE SCHEMA sdb_seqs");
            engine.execute("CREATE SEQUENCE sdb_seqs.outer_seq");
            engine.execute("CREATE SCHEMA sdb_src");
            engine.execute("CREATE SEQUENCE sdb_src.own_seq");
            engine.execute("CREATE TABLE sdb_src.t (a INT DEFAULT sdb_src.own_seq.nextval,"
                + " b INT DEFAULT sdb_seqs.outer_seq.nextval)");
            engine.execute("CREATE SCHEMA sdb_cl CLONE sdb_src");
            engine.execute("USE SCHEMA test_db.test_schema");
            final String cloned = ddl("sdb_cl.t");
            // The schema's own sequence was cloned with it; the one outside it was not.
            assertTrue(cloned.contains("A NUMBER(38,0) DEFAULT TEST_DB.SDB_CL.OWN_SEQ.NEXTVAL,"), cloned);
            assertTrue(cloned.contains("B NUMBER(38,0) DEFAULT TEST_DB.SDB_SEQS.OUTER_SEQ.NEXTVAL"), cloned);
            assertTrue(ddl("sdb_src.t").contains("DEFAULT TEST_DB.SDB_SRC.OWN_SEQ.NEXTVAL,"), ddl("sdb_src.t"));
        } finally {
            engine.execute("USE SCHEMA test_db.test_schema");
        }
    }

    @Test
    public void aClonedDatabaseDrawsFromItsOwnSequencesAcrossItsSchemas() {
        try {
            engine.execute("CREATE SCHEMA sdb_a");
            engine.execute("CREATE SCHEMA sdb_b");
            engine.execute("CREATE SEQUENCE sdb_b.s");
            engine.execute("CREATE TABLE sdb_a.t (a INT DEFAULT sdb_b.s.nextval, b INT)");
            engine.execute("CREATE OR REPLACE DATABASE sdb_clone_db CLONE test_db");
            engine.execute("USE SCHEMA test_db.test_schema");
            final String cloned = ddl("sdb_clone_db.sdb_a.t");
            assertTrue(cloned.contains("A NUMBER(38,0) DEFAULT SDB_CLONE_DB.SDB_B.S.NEXTVAL,"), cloned);
        } finally {
            engine.execute("USE SCHEMA test_db.test_schema");
            engine.execute("DROP DATABASE IF EXISTS sdb_clone_db");
            engine.execute("USE SCHEMA test_db.test_schema");
        }
    }

    @Test
    public void aNameNoSequenceCarriesIsRefusedAtCreate() {
        assertEquals("SQL compilation error: error line 1 at position 36\ninvalid identifier 'NOSUCHSEQ.NEXTVAL'",
            refusal("CREATE TABLE sdb_td3 (a INT DEFAULT nosuchseq.nextval, b INT)"));
    }
}
