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
package dev.frostlake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The live harness's record of the account-level objects a run creates: read off each statement's tokens, so the
 * teardown drops those names and nothing another session made. The reading needs no account.
 */
public class LiveAccountObjectsTest {

    @BeforeEach
    public void forget() {
        LiveAccountObjects.forgetRecorded();
    }

    @AfterEach
    public void forgetAgain() {
        LiveAccountObjects.forgetRecorded();
    }

    private static List<String> created(final String sql) {
        final List<String> out = new ArrayList<String>();
        for (final String[] made : LiveAccountObjects.createdBy(sql)) {
            out.add(String.join(" ", made));
        }
        return out;
    }

    private static Set<String> names(final String... expected) {
        final Set<String> out = new LinkedHashSet<String>();
        for (final String name : expected) {
            out.add(name);
        }
        return out;
    }

    @Test
    public void aCreateNamesItsObject() {
        assertEquals(List.of("DATABASE D1"), created("CREATE DATABASE d1"));
        assertEquals(List.of("DATABASE MixedDb"), created("create or replace transient database \"MixedDb\""));
        assertEquals(List.of("DATABASE a\"b"), created("CREATE DATABASE \"a\"\"b\""));
        assertEquals(List.of("ROLE R1"), created("CREATE ROLE IF NOT EXISTS r1 COMMENT = 'x'"));
        assertEquals(List.of("USER U1"), created("/* a user */ CREATE USER u1 PASSWORD = 'CREATE ROLE'"));
        assertEquals(List.of("WAREHOUSE WH"), created("CREATE OR REPLACE WAREHOUSE wh WITH WAREHOUSE_SIZE = XSMALL"));
        assertEquals(List.of("DATABASE D2"), created("CREATE DATABASE d2 CLONE d1"));
        assertEquals(List.of("DATABASE D3"), created("UNDROP DATABASE d3"));
        assertEquals(List.of("NETWORK POLICY NP_A"),
            created("CREATE OR REPLACE NETWORK POLICY np_a ALLOWED_IP_LIST = ('1.2.3.4')"));
        assertEquals(List.of("NETWORK POLICY NP_B NP_A"), created("ALTER NETWORK POLICY np_a RENAME TO np_b"));
    }

    @Test
    public void aStatementWrittenInsideATextIsRead() {
        assertEquals(List.of("ROLE R2"), created("EXECUTE IMMEDIATE 'CREATE ROLE r2'"));
        assertEquals(List.of("DATABASE D4", "ROLE R4"),
            created("EXECUTE IMMEDIATE $$ BEGIN CREATE DATABASE d4; CREATE ROLE r4; END $$"));
        assertEquals(List.of("WAREHOUSE W5"), created("CREATE PROCEDURE p() RETURNS INT LANGUAGE SQL AS"
            + " 'BEGIN CREATE WAREHOUSE w5; RETURN 1; END'"));
    }

    @Test
    public void whatLivesInsideADatabaseIsNotAnAccountObject() {
        assertTrue(created("CREATE TABLE t (\"user\" INT, role VARCHAR)").isEmpty());
        assertTrue(created("CREATE DATABASE ROLE test_db.dr").isEmpty());
        assertTrue(created("CREATE SCHEMA test_db.database").isEmpty());
        assertTrue(created("DROP DATABASE d1").isEmpty());
        assertTrue(created("ALTER ROLE r1 SET COMMENT = 'x'").isEmpty());
        assertTrue(created("SELECT 'not sql at all', 1").isEmpty());
        assertTrue(created("CREATE DATABASE IDENTIFIER('x')").isEmpty());
        assertTrue(created("CREATE NETWORK RULE test_db.test_schema.r TYPE = IPV4 MODE = INGRESS"
            + " VALUE_LIST = ('1.2.3.4')").isEmpty());
    }

    @Test
    public void theHarnessOwnDatabaseIsNeitherRecordedNorCounted() {
        LiveAccountObjects.beginTest();
        LiveAccountObjects.observe("CREATE OR REPLACE DATABASE test_db");
        assertTrue(LiveAccountObjects.recorded("DATABASE").isEmpty());
        assertFalse(LiveAccountObjects.sawAccountObjectStatement());
        LiveAccountObjects.observe("CREATE DATABASE de_db1");
        assertEquals(names("DE_DB1"), LiveAccountObjects.recorded("DATABASE"));
        assertTrue(LiveAccountObjects.sawAccountObjectStatement());
        LiveAccountObjects.beginTest();
    }

    @Test
    public void aRenamedObjectIsFollowedOnlyWhenItWasThisRuns() {
        assertEquals(List.of("DATABASE D9 D1"), created("ALTER DATABASE IF EXISTS d1 RENAME TO d9"));
        LiveAccountObjects.observe("CREATE DATABASE d1");
        LiveAccountObjects.observe("CREATE ROLE r1");
        LiveAccountObjects.observe("ALTER DATABASE d1 RENAME TO d9");
        LiveAccountObjects.observe("ALTER ROLE someone_elses RENAME TO r7");
        assertEquals(names("D9"), LiveAccountObjects.recorded("DATABASE"));
        assertEquals(names("R1"), LiveAccountObjects.recorded("ROLE"));
        assertTrue(LiveAccountObjects.recorded("USER").isEmpty());
    }
}
