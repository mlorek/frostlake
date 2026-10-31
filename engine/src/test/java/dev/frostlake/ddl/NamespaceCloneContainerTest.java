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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A database or schema clone keeps what its source is: a stage's options, managed access, transience, a database
 * without PUBLIC, database roles. It is owned by the role that cloned it, and it leaves temporary objects behind.
 * The clone copied a stage's name and URL only, overwrote managed access and transience with the statement's
 * own (absent) modifiers, gave a PUBLIC-less database an empty PUBLIC and then failed to copy its data, cloned
 * temporary views and functions as permanent ones, left database roles out, and was owned by SYSADMIN whoever
 * cloned it.
 */
public class NamespaceCloneContainerTest {

    private Path stageRoot;
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() throws IOException {
        stageRoot = Files.createTempDirectory("ns_clone_stages_");
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_S3_LOCAL_ROOT, stageRoot.toString());
        engine = new DatabaseEngine(cfg);
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
        deleteRecursively(stageRoot.toFile());
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    /** One column of a statement's result, as text. */
    private List<String> column(final String sql, final String name) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        while (rs.next()) {
            out.add(String.valueOf(rs.getValue(name)));
        }
        return out;
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void aClonedStageKeepsItsOptions() {
        engine.execute("CREATE DATABASE d1");
        engine.execute("USE SCHEMA d1.PUBLIC");
        engine.execute("CREATE STAGE stg FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|' SKIP_HEADER = 1)"
            + " COPY_OPTIONS = (ON_ERROR = CONTINUE) DIRECTORY = (ENABLE = TRUE)");
        engine.execute("CREATE STAGE ext URL = 's3://bucket/clone' DIRECTORY = (ENABLE = TRUE)");
        // An internal stage is part of a clone that includes the internal stages (see SchemaCloneStagesTest).
        engine.execute("CREATE DATABASE d2 CLONE d1 INCLUDE INTERNAL STAGES");

        engine.executeQuery("DESCRIBE STAGE d2.PUBLIC.stg");
        assertEquals(List.of("ENABLE=true", "FIELD_DELIMITER=|", "ON_ERROR=CONTINUE", "SKIP_HEADER=1"),
            column("SELECT \"property\" || '=' || \"property_value\" AS p FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"
                + " WHERE \"property\" IN ('FIELD_DELIMITER', 'SKIP_HEADER', 'ON_ERROR', 'ENABLE') ORDER BY 1", "P"));
        engine.executeQuery("SHOW STAGES LIKE 'EXT' IN SCHEMA d2.PUBLIC");
        assertEquals(List.of("Y"), column("SELECT \"directory_enabled\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
            "directory_enabled"), "an external stage keeps its directory table");
    }

    @Test
    public void aCloneKeepsItsSourcesManagedAccessAndTransience() {
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.ms WITH MANAGED ACCESS");
        engine.execute("CREATE SCHEMA d1.ms2 CLONE d1.ms");
        assertEquals(List.of("MANAGED ACCESS"), column("SHOW SCHEMAS LIKE 'MS2' IN DATABASE d1", "options"));

        final String permanent = "SQL compilation error:\nTransient object cannot be cloned to a permanent object.";
        engine.execute("CREATE TRANSIENT DATABASE tdb");
        assertEquals(permanent, refusal("CREATE DATABASE tdb2 CLONE tdb"));
        engine.execute("CREATE TRANSIENT DATABASE tdb3 CLONE tdb");
        assertEquals(List.of("TRANSIENT"), column("SHOW DATABASES LIKE 'TDB3'", "options"));
        assertTrue(!"not refused".equals(refusal("ALTER DATABASE tdb3 SET DATA_RETENTION_TIME_IN_DAYS = 5")),
            "a transient clone keeps a transient database's retention limit");

        engine.execute("CREATE TRANSIENT SCHEMA d1.ts");
        assertEquals(permanent, refusal("CREATE SCHEMA d1.ts2 CLONE d1.ts"));
        engine.execute("CREATE TRANSIENT SCHEMA d1.ts3 CLONE d1.ts");
        assertEquals(List.of("TRANSIENT"), column("SHOW SCHEMAS LIKE 'TS3' IN DATABASE d1", "options"));
        assertEquals(List.of(), column("SHOW SCHEMAS LIKE 'TS2' IN DATABASE d1", "name"), "a refused clone makes nothing");
    }

    @Test
    public void aCloneLeavesTemporaryObjectsBehind() {
        engine.execute("CREATE DATABASE d1");
        engine.execute("USE SCHEMA d1.PUBLIC");
        engine.execute("CREATE VIEW pv AS SELECT 1 AS x");
        engine.execute("CREATE TEMPORARY VIEW tv AS SELECT 1 AS x");
        engine.execute("CREATE FUNCTION pf() RETURNS INT AS '1'");
        engine.execute("CREATE TEMPORARY FUNCTION tf() RETURNS INT AS '1'");
        engine.execute("CREATE PROCEDURE pp() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END $$");
        engine.execute("CREATE TEMPORARY PROCEDURE tp() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END $$");
        engine.execute("CREATE DATABASE d2 CLONE d1");
        assertEquals(List.of("PV"), column("SHOW VIEWS IN SCHEMA d2.PUBLIC", "name"));
        assertEquals(List.of("PF"), column("SHOW USER FUNCTIONS IN SCHEMA d2.PUBLIC", "name"));
        assertEquals(List.of("PP"), column("SHOW USER PROCEDURES IN SCHEMA d2.PUBLIC", "name"));
    }

    @Test
    public void theCloningRoleOwnsTheCloneAndItsChildrenKeepTheirOwners() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        // SYSADMIN reaches what ACCOUNTADMIN makes through the user's other roles, active as secondary ones.
        engine.execute("USE SECONDARY ROLES ALL");
        engine.execute("CREATE DATABASE o1");
        engine.execute("CREATE TABLE o1.PUBLIC.t (a INT)");
        engine.execute("CREATE SCHEMA o1.s2");
        engine.execute("CREATE STAGE o1.PUBLIC.ext URL = 's3://bucket/pipes'");
        engine.execute("CREATE PIPE o1.PUBLIC.p AS COPY INTO o1.PUBLIC.t FROM @o1.PUBLIC.ext");
        // The cloning role makes a schema in a database another role owns, which takes CREATE SCHEMA on it.
        engine.execute("GRANT CREATE SCHEMA ON DATABASE o1 TO ROLE SYSADMIN");
        engine.execute("USE ROLE SYSADMIN");
        engine.execute("CREATE DATABASE o2 CLONE o1");
        engine.execute("CREATE SCHEMA o1.s3 CLONE o1.s2");

        assertEquals(List.of("SYSADMIN"), column("SHOW DATABASES LIKE 'O2'", "owner"), "the clone is the cloner's");
        assertEquals(List.of("SYSADMIN"), column("SHOW SCHEMAS LIKE 'S3' IN DATABASE o1", "owner"));
        assertEquals(List.of("ACCOUNTADMIN"), column("SELECT TABLE_OWNER FROM o2.INFORMATION_SCHEMA.TABLES"
            + " WHERE TABLE_NAME = 'T'", "TABLE_OWNER"), "a child keeps its owner");
        assertEquals(List.of("ACCOUNTADMIN"), column("SHOW STAGES IN SCHEMA o2.PUBLIC", "owner"));
        assertEquals(List.of("SYSADMIN"), column("SHOW PIPES IN SCHEMA o2.PUBLIC", "owner"),
            "the role that clones a pipe owns the clone (documented)");
    }

    @Test
    public void aDatabaseWithoutPublicClonesWithoutOne() {
        engine.execute("CREATE DATABASE src_db");
        engine.execute("DROP SCHEMA src_db.PUBLIC");
        engine.execute("CREATE SCHEMA src_db.other");
        engine.execute("CREATE TABLE src_db.other.t (a INT)");
        engine.execute("INSERT INTO src_db.other.t VALUES (7)");
        engine.execute("CREATE DATABASE cl_db CLONE src_db");
        assertEquals(List.of("INFORMATION_SCHEMA", "OTHER"), column("SHOW SCHEMAS IN DATABASE cl_db", "name"));
        assertEquals(List.of("7"), column("SELECT a FROM cl_db.other.t", "A"));
    }

    @Test
    public void databaseRolesAreClonedWithTheirDatabase() {
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE DATABASE ROLE d1.dr");
        engine.execute("CREATE TABLE d1.PUBLIC.drt (a INT)");
        engine.execute("GRANT SELECT ON TABLE d1.PUBLIC.drt TO DATABASE ROLE d1.dr");
        engine.execute("CREATE DATABASE d4 CLONE d1");
        assertEquals(List.of("DR"), column("SHOW DATABASE ROLES IN DATABASE d4", "name"));
        final List<String> granted = column("SHOW GRANTS TO DATABASE ROLE d4.dr", "name");
        assertTrue(granted.contains("D4.PUBLIC.DRT") && !granted.contains("D1.PUBLIC.DRT"), String.valueOf(granted));
    }
}
