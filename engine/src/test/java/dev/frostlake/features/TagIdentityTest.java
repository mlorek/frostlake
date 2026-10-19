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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An object keeps a tag by the tag itself, not by its name: two tags of one name in two schemas are two tags on
 * one object, an assignment follows its tag through a rename, a move and a renamed schema, goes with a dropped
 * tag and comes back with a restored one, and a clone of a schema or a database names its own copies of the tags
 * in it.
 */
public class TagIdentityTest extends BaseDatabaseTest {

    @BeforeEach
    public void twoTagsOfOneName() {
        engine.execute("CREATE SCHEMA tg_s2");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE TAG test_schema.cc");
        engine.execute("CREATE TAG tg_s2.cc");
        engine.execute("CREATE TABLE tt (a INT, b INT)");
    }

    /** The tags reaching an object, each as {@code DB.SCHEMA.NAME=value@LEVEL}, in the order of their names. */
    private List<String> refs(final String objectName, final String domain) {
        final ResultSet rs = engine.executeQuery("SELECT TAG_DATABASE, TAG_SCHEMA, TAG_NAME, TAG_VALUE, LEVEL FROM"
            + " TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('" + objectName + "', '" + domain + "')) ORDER BY 1, 2, 3");
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(row.getValue(0) + "." + row.getValue(1) + "." + row.getValue(2) + "=" + row.getValue(3) + "@"
                + row.getValue(4));
        }
        return out;
    }

    private Object value(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private Object tagOn(final String tag, final String object, final String domain) {
        return value("SELECT SYSTEM$GET_TAG('" + tag + "', '" + object + "', '" + domain + "')");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void twoTagsOfOneNameAreTwoTagsOnOneObject() {
        engine.execute("ALTER TABLE tt SET TAG test_schema.cc = 'a'");
        engine.execute("ALTER TABLE tt SET TAG tg_s2.cc = 'b'");
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=a@TABLE", "TEST_DB.TG_S2.CC=b@TABLE"), refs("tt", "TABLE"));
        assertEquals("a", tagOn("test_schema.cc", "tt", "TABLE"));
        assertEquals("b", tagOn("tg_s2.cc", "tt", "TABLE"));
        assertEquals("a", tagOn("cc", "tt", "TABLE"), "a bare name is the tag of the session's schema");
        assertEquals("b", tagOn("test_db.tg_s2.cc", "tt", "TABLE"));
        assertTrue(value("SELECT GET_DDL('TABLE', 'tt')").toString().contains(
            "WITH TAG (TEST_DB.TEST_SCHEMA.CC='a', TEST_DB.TG_S2.CC='b')"));

        engine.execute("ALTER TABLE tt UNSET TAG tg_s2.cc");
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=a@TABLE"), refs("tt", "TABLE"));
        engine.execute("ALTER TABLE tt SET TAG tg_s2.cc = 'b2', test_schema.cc = 'a2'");
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=a2@TABLE", "TEST_DB.TG_S2.CC=b2@TABLE"),
            refs("tt", "TABLE"));
        engine.execute("ALTER TABLE tt UNSET TAG cc");
        assertEquals(Arrays.asList("TEST_DB.TG_S2.CC=b2@TABLE"), refs("tt", "TABLE"),
            "a bare name takes off the tag of the session's schema alone");
    }

    @Test
    public void columnsViewsInlineClausesAndClonesKeepBoth() {
        engine.execute("ALTER TABLE tt MODIFY COLUMN a SET TAG test_schema.cc = 'ca', tg_s2.cc = 'cb'");
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=ca@COLUMN", "TEST_DB.TG_S2.CC=cb@COLUMN"),
            refs("tt.a", "COLUMN"));
        engine.execute("CREATE TABLE t2 (a INT WITH TAG (test_schema.cc = 'ia', tg_s2.cc = 'ib'))"
            + " WITH TAG (test_schema.cc = 'ta', tg_s2.cc = 'tb')");
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=ta@TABLE", "TEST_DB.TG_S2.CC=tb@TABLE"), refs("t2", "TABLE"));
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=ia@COLUMN", "TEST_DB.TG_S2.CC=ib@COLUMN"),
            refs("t2.a", "COLUMN"));
        engine.execute("CREATE TABLE t2c CLONE t2");
        assertEquals(refs("t2", "TABLE").toString().replace("T2", "T2C"), refs("t2c", "TABLE").toString());
        assertEquals(refs("t2.a", "COLUMN"), refs("t2c.a", "COLUMN"));
        engine.execute("CREATE VIEW v3 WITH TAG (test_schema.cc = 'va', tg_s2.cc = 'vb') AS SELECT a FROM tt");
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=va@TABLE", "TEST_DB.TG_S2.CC=vb@TABLE"), refs("v3", "TABLE"));
        assertTrue(value("SELECT GET_DDL('VIEW', 'v3')").toString().contains(
            "WITH TAG (TEST_DB.TEST_SCHEMA.CC='va', TEST_DB.TG_S2.CC='vb')"));
    }

    @Test
    public void aQuotedLowerCaseTagIsATagOfItsOwn() {
        engine.execute("CREATE TAG test_schema.\"cc\"");
        engine.execute("ALTER TABLE tt SET TAG test_schema.cc = 'own'");
        engine.execute("ALTER TABLE tt SET TAG \"cc\" = 'q'");
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=own@TABLE", "TEST_DB.TEST_SCHEMA.cc=q@TABLE"),
            refs("tt", "TABLE"));
        assertEquals("q", tagOn("\"cc\"", "tt", "TABLE"));
        assertEquals("own", tagOn("cc", "tt", "TABLE"));
        assertTrue(value("SELECT GET_DDL('TABLE', 'tt')").toString().contains(
            "WITH TAG (TEST_DB.TEST_SCHEMA.\"cc\"='q', TEST_DB.TEST_SCHEMA.CC='own')"));
    }

    @Test
    public void aDroppedTagTakesItsAssignmentsAndARestoredOneBringsThemBack() {
        engine.execute("ALTER TABLE tt SET TAG test_schema.cc = 'a', tg_s2.cc = 'b'");
        engine.execute("DROP TAG tg_s2.cc");
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=a@TABLE"), refs("tt", "TABLE"));
        assertEquals(hinted("SQL compilation error:\nTag 'TEST_DB.TG_S2.CC' does not exist or not authorized."),
            refusal("SELECT SYSTEM$GET_TAG('tg_s2.cc', 'tt', 'TABLE')"));
        // A tag made under the dropped one's name is another tag.
        engine.execute("CREATE TAG tg_s2.cc");
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=a@TABLE"), refs("tt", "TABLE"));
        assertNull(tagOn("tg_s2.cc", "tt", "TABLE"));
        engine.execute("ALTER TABLE tt SET TAG tg_s2.cc = 'b4'");
        engine.execute("DROP TAG tg_s2.cc");
        engine.execute("UNDROP TAG tg_s2.cc");
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=a@TABLE", "TEST_DB.TG_S2.CC=b4@TABLE"), refs("tt", "TABLE"));
        assertFalse(value("SELECT GET_DDL('TABLE', 'tt')").toString().contains("'b'"));
    }

    @Test
    public void aReplacedTagStartsWithoutAssignments() {
        engine.execute("ALTER TABLE tt SET TAG test_schema.cc = 'a', tg_s2.cc = 'b'");
        engine.execute("ALTER TABLE tt MODIFY COLUMN a SET TAG test_schema.cc = 'ca'");
        engine.execute("CREATE OR REPLACE TAG test_schema.cc");
        assertEquals(Arrays.asList("TEST_DB.TG_S2.CC=b@TABLE"), refs("tt", "TABLE"));
        assertEquals(Arrays.asList("TEST_DB.TG_S2.CC=b@TABLE"), refs("tt.a", "COLUMN"));
        engine.execute("ALTER TABLE tt SET TAG test_schema.cc = 'a'");
        engine.execute("CREATE TAG IF NOT EXISTS test_schema.cc");
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=a@TABLE", "TEST_DB.TG_S2.CC=b@TABLE"), refs("tt", "TABLE"));
    }

    @Test
    public void anAssignmentFollowsARenamedOrMovedTagAndARenamedSchema() {
        engine.execute("ALTER TABLE tt SET TAG test_schema.cc = 'a', tg_s2.cc = 'b'");
        engine.execute("ALTER TAG tg_s2.cc RENAME TO tg_s2.dd");
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=a@TABLE", "TEST_DB.TG_S2.DD=b@TABLE"), refs("tt", "TABLE"));
        assertEquals("b", tagOn("tg_s2.dd", "tt", "TABLE"));
        engine.execute("ALTER TAG tg_s2.dd RENAME TO test_schema.ee");
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=a@TABLE", "TEST_DB.TEST_SCHEMA.EE=b@TABLE"),
            refs("tt", "TABLE"));
        assertEquals("SQL compilation error:\nObject 'TEST_SCHEMA.CC' already exists.",
            refusal("ALTER TAG test_schema.ee RENAME TO test_schema.cc"));
        engine.execute("ALTER TAG test_schema.ee RENAME TO tg_s2.cc");
        engine.execute("ALTER SCHEMA tg_s2 RENAME TO tg_s9");
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=a@TABLE", "TEST_DB.TG_S9.CC=b@TABLE"), refs("tt", "TABLE"));
        assertEquals("b", tagOn("tg_s9.cc", "tt", "TABLE"));
        assertTrue(value("SELECT GET_DDL('TABLE', 'tt')").toString().contains(
            "WITH TAG (TEST_DB.TEST_SCHEMA.CC='a', TEST_DB.TG_S9.CC='b')"));
        engine.execute("DROP SCHEMA tg_s9");
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=a@TABLE"), refs("tt", "TABLE"));
        engine.execute("UNDROP SCHEMA tg_s9");
        engine.execute("USE SCHEMA test_db.test_schema");
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=a@TABLE", "TEST_DB.TG_S9.CC=b@TABLE"), refs("tt", "TABLE"));
    }

    @Test
    public void aCloneNamesItsOwnCopiesOfTheTagsClonedWithIt() {
        engine.execute("ALTER TABLE tt SET TAG test_schema.cc = 'a', tg_s2.cc = 'b'");
        engine.execute("ALTER TABLE tt MODIFY COLUMN a SET TAG test_schema.cc = 'ca'");
        engine.execute("CREATE SCHEMA tg_c CLONE test_schema");
        engine.execute("USE SCHEMA test_db.test_schema");
        // The tag that lives in the cloned schema is the clone's own; the one that lives elsewhere is kept.
        assertEquals(Arrays.asList("TEST_DB.TG_C.CC=a@TABLE", "TEST_DB.TG_S2.CC=b@TABLE"),
            refs("test_db.tg_c.tt", "TABLE"));
        assertEquals(Arrays.asList("TEST_DB.TG_C.CC=ca@COLUMN", "TEST_DB.TG_S2.CC=b@TABLE"),
            refs("test_db.tg_c.tt.a", "COLUMN"));
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=a@TABLE", "TEST_DB.TG_S2.CC=b@TABLE"), refs("tt", "TABLE"));
        engine.execute("CREATE OR REPLACE DATABASE tgi_dbc CLONE test_db");
        try {
            engine.execute("USE SCHEMA test_db.test_schema");
            assertEquals(Arrays.asList("TGI_DBC.TEST_SCHEMA.CC=a@TABLE", "TGI_DBC.TG_S2.CC=b@TABLE"),
                refs("tgi_dbc.test_schema.tt", "TABLE"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS tgi_dbc");
            engine.execute("USE SCHEMA test_db.test_schema");
        }
    }

    @Test
    public void aTagOfAnotherDatabaseIsToldThereUntilThatDatabaseIsDropped() {
        engine.execute("ALTER TABLE tt SET TAG test_schema.cc = 'a'");
        engine.execute("CREATE OR REPLACE DATABASE tgi_db2");
        try {
            engine.execute("CREATE SCHEMA tgi_db2.s");
            engine.execute("CREATE TAG tgi_db2.s.cc");
            engine.execute("USE SCHEMA test_db.test_schema");
            engine.execute("ALTER TABLE tt SET TAG tgi_db2.s.cc = 'x'");
            assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=a@TABLE", "TGI_DB2.S.CC=x@TABLE"), refs("tt", "TABLE"));
            assertEquals("x", tagOn("tgi_db2.s.cc", "tt", "TABLE"));
            assertTrue(value("SELECT GET_DDL('TABLE', 'tt')").toString().contains(
                "WITH TAG (TEST_DB.TEST_SCHEMA.CC='a', TGI_DB2.S.CC='x')"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS tgi_db2");
            engine.execute("USE SCHEMA test_db.test_schema");
        }
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=a@TABLE"), refs("tt", "TABLE"));
    }

    @Test
    public void anInheritedTagOfAnotherSchemaStandsBesideTheObjectsOwn() {
        engine.execute("ALTER SCHEMA test_schema SET TAG tg_s2.cc = 'sch'");
        engine.execute("ALTER TABLE tt SET TAG test_schema.cc = 'own'");
        assertEquals(Arrays.asList("TEST_DB.TEST_SCHEMA.CC=own@TABLE", "TEST_DB.TG_S2.CC=sch@SCHEMA"),
            refs("tt", "TABLE"));
        assertEquals(Arrays.asList("TEST_DB.TG_S2.CC=sch@SCHEMA"), refs("test_schema", "SCHEMA"));
        assertEquals("sch", tagOn("tg_s2.cc", "tt", "TABLE"));
        assertEquals("sch", tagOn("tg_s2.cc", "tt.b", "COLUMN"));
        assertEquals("own", tagOn("test_schema.cc", "tt.a", "COLUMN"));
    }
}
