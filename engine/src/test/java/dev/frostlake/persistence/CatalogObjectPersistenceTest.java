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
package dev.frostlake.persistence;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.task.TaskScheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalog objects that used to live only in memory — accounts, integrations, external volumes, compute pools,
 * database roles and grant details, container settings, notebooks, Streamlit apps, container services and
 * repositories, alerts, materialized views, and a table's event-table, Iceberg, data metric and stage settings —
 * survive a restart in each of the three ways state reaches the next engine: a persisted snapshot, a WAL checkpoint
 * (whose snapshot replaces the log it truncates), and a WAL replayed statement by statement.
 *
 * <p>Each case runs its statements on one engine, reads its listings, restarts, and reads them again: the two
 * readings must agree cell for cell. Most cases compare the definitions and leave out the moments; the cases on
 * the moments themselves — creation, change, resume and grant times, URL ids — keep every cell, since an object
 * keeps those for its lifetime.
 */
public class CatalogObjectPersistenceTest {

    private static final String GENERATED_ID = "[A-Z0-9]+_SFCRole=\\d+_[A-Za-z0-9+/]{27}=";

    private static final String LOCATIONS = "STORAGE_LOCATIONS = ((NAME = 'loc1' STORAGE_PROVIDER = 'S3'"
        + " STORAGE_BASE_URL = 's3://bucket/path/' STORAGE_AWS_ROLE_ARN = 'arn:aws:iam::1:role/r'"
        + " ENCRYPTION = (TYPE = 'AWS_SSE_KMS' KMS_KEY_ID = 'key1')),"
        + " (NAME = 'loc2' STORAGE_PROVIDER = 'GCS' STORAGE_BASE_URL = 'gcs://bucket/'))";

    /** The main file a Streamlit app created FROM a location has to find there. */
    private static final String STAGED_APP = "COPY INTO @stg/app/streamlit_app.py FROM (SELECT 'import streamlit')"
        + " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE OVERWRITE = TRUE";

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("catalog_object_persistence_");
    }

    @AfterEach
    public void tearDown() throws IOException {
        deleteRecursively(dir);
    }

    /** The organization's accounts, the integrations, an external volume and a compute pool. */
    @Test
    public void accountLevelObjectsSurviveARestart() {
        assertSurvivesRestart(Arrays.asList(
            "CREATE API INTEGRATION it_api API_PROVIDER = aws_api_gateway"
                + " API_AWS_ROLE_ARN = 'arn:aws:iam::1:role/r' API_KEY = 'k1'"
                + " API_ALLOWED_PREFIXES = ('https://a.example.com/', 'https://b.example.com/') ENABLED = TRUE"
                + " COMMENT = 'c1'",
            "CREATE NOTIFICATION INTEGRATION it_ns TYPE = EMAIL ENABLED = FALSE",
            "CREATE CATALOG INTEGRATION ice_cat CATALOG_SOURCE = OBJECT_STORE TABLE_FORMAT = ICEBERG ENABLED = TRUE",
            "CREATE EXTERNAL VOLUME ev_a " + LOCATIONS + " ALLOW_WRITES = FALSE COMMENT = 'vol'",
            """
            CREATE ACCOUNT acc_one ADMIN_NAME = admin ADMIN_PASSWORD = 'TestPassword1' FIRST_NAME = 'Jane'
              EMAIL = 'jane@example.com' EDITION = ENTERPRISE REGION = AWS_US_WEST_2 COMMENT = 'first'""",
            """
            CREATE ACCOUNT acc_two ADMIN_NAME = admin ADMIN_PASSWORD = 'TestPassword1'
              EMAIL = 'bob@example.com' EDITION = STANDARD""",
            "DROP ACCOUNT acc_two GRACE_PERIOD_IN_DAYS = 3",
            """
            CREATE MANAGED ACCOUNT reader_one ADMIN_NAME = admin, ADMIN_PASSWORD = 'Sdfed43da!44', TYPE = READER,
              COMMENT = 'for sharing'""",
            "CREATE COMPUTE POOL p1 MIN_NODES = 1 MAX_NODES = 2 INSTANCE_FAMILY = CPU_X64_XS"
                + " INITIALLY_SUSPENDED = TRUE AUTO_SUSPEND_SECS = 60 COMMENT = 'pool'"),
            Arrays.asList("SHOW INTEGRATIONS", "DESCRIBE INTEGRATION it_api", "DESCRIBE INTEGRATION it_ns",
                "DESCRIBE INTEGRATION ice_cat", "SHOW EXTERNAL VOLUMES", "DESCRIBE EXTERNAL VOLUME ev_a",
                "SHOW ACCOUNTS HISTORY", "SHOW MANAGED ACCOUNTS", "SHOW COMPUTE POOLS"));
    }

    /** The external ID an integration and a volume were given when they were created is theirs after a restart. */
    @Test
    public void aGeneratedExternalIdSurvivesARestart() {
        assertKeepsEveryCell(Arrays.asList(
            "CREATE API INTEGRATION it_ident API_PROVIDER = aws_api_gateway"
                + " API_AWS_ROLE_ARN = 'arn:aws:iam::1:role/r'"
                + " API_ALLOWED_PREFIXES = ('https://a.example.com/') ENABLED = TRUE",
            "CREATE OR REPLACE API INTEGRATION it_ident API_PROVIDER = aws_api_gateway"
                + " API_AWS_ROLE_ARN = 'arn:aws:iam::1:role/r'"
                + " API_ALLOWED_PREFIXES = ('https://a.example.com/') ENABLED = TRUE",
            "CREATE EXTERNAL VOLUME ev_ident " + LOCATIONS),
            Arrays.asList("DESCRIBE INTEGRATION it_ident", "DESCRIBE EXTERNAL VOLUME ev_ident"));
    }

    /** The parameters set on a user and on the account, a session's own beside them and the account's alone. */
    @Test
    public void userAndAccountParametersSurviveARestart() {
        assertSurvivesRestart(Arrays.asList(
            "CREATE USER pu",
            "ALTER USER pu SET QUERY_TAG = 'ut' LOCK_TIMEOUT = 5, AUTOCOMMIT = FALSE",
            "ALTER USER pu SET WEEK_START = 3",
            "ALTER USER pu UNSET WEEK_START",
            "ALTER ACCOUNT SET QUERY_TAG = 'at', TIMEZONE = 'Asia/Tokyo', DATA_RETENTION_TIME_IN_DAYS = 3",
            "ALTER ACCOUNT SET JSON_INDENT = 4",
            "ALTER ACCOUNT UNSET JSON_INDENT"),
            Arrays.asList("SHOW PARAMETERS IN USER pu", "SHOW PARAMETERS IN ACCOUNT", "SHOW PARAMETERS",
                "SELECT TO_CHAR(TO_TIMESTAMP_LTZ('2026-01-15 12:00:00 +00:00'), 'HH24:MI TZH:TZM')"));
    }

    /**
     * A RETURNS TABLE column written with no width stays one of no width, and one written with the default width
     * stays sized: what the routine's result declares and what its DDL writes back are the same after a restart.
     */
    @Test
    public void aRoutinesUnsizedReturnColumnsSurviveARestart() {
        assertSurvivesRestart(Arrays.asList(
            "CREATE DATABASE rd",
            "CREATE SCHEMA rd.rs",
            "USE SCHEMA rd.rs",
            """
                CREATE PROCEDURE rp() RETURNS TABLE (a VARCHAR, b BINARY, c VARCHAR(16777216), d BINARY(8388608))
                LANGUAGE SQL AS $$
                DECLARE r RESULTSET DEFAULT (SELECT 'x'::VARCHAR AS a, TO_BINARY('00') AS b, 'y'::VARCHAR AS c,
                    TO_BINARY('01') AS d);
                BEGIN RETURN TABLE(r); END $$
                """,
            """
                CREATE FUNCTION rf() RETURNS TABLE (a VARCHAR, b BINARY, c VARCHAR(16777216), d BINARY(8388608))
                AS $$ SELECT 'x'::VARCHAR, TO_BINARY('00'), 'y'::VARCHAR, TO_BINARY('01') $$
                """),
            Arrays.asList(
                "SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(b), SYSTEM$TYPEOF(c), SYSTEM$TYPEOF(d) FROM TABLE(rd.rs.rp())",
                "SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(b), SYSTEM$TYPEOF(c), SYSTEM$TYPEOF(d) FROM TABLE(rd.rs.rf())",
                "SELECT GET_DDL('PROCEDURE', 'rd.rs.rp()')", "SELECT GET_DDL('FUNCTION', 'rd.rs.rf()')"));
    }

    /** An account created after a restart takes a locator no restored account holds. */
    @Test
    public void aNewAccountAfterARestartTakesAFreshLocator() {
        final Path data = dir.resolve("locator");
        final DatabaseEngine first = snapshotEngine(data);
        first.execute("CREATE ACCOUNT acc_a ADMIN_NAME = admin ADMIN_PASSWORD = 'TestPassword1'"
            + " EMAIL = 'a@example.com' EDITION = STANDARD");
        first.shutdown();
        final DatabaseEngine second = snapshotEngine(data);
        second.execute("CREATE ACCOUNT acc_b ADMIN_NAME = admin ADMIN_PASSWORD = 'TestPassword1'"
            + " EMAIL = 'b@example.com' EDITION = STANDARD");
        final List<String> locators = column(second.executeQuery("SHOW ACCOUNTS"), "account_locator");
        second.shutdown();
        assertEquals(locators.size(), new HashSet<>(locators).size(), "locators: " + locators);
    }

    /**
     * The tags on the older object kinds. A tag set by ALTER belongs to the object for its lifetime, and
     * the WAL replays the ALTER, so the loss only ever showed in snapshot-only mode and after a checkpoint
     * — which is why this goes through the restart harness rather than one engine.
     */
    @Test
    public void tagsOnTheOlderObjectKindsSurviveARestart() {
        assertSurvivesRestart(Arrays.asList(
            "CREATE DATABASE td",
            "CREATE SCHEMA td.ts",
            "USE DATABASE td",
            "USE SCHEMA ts",
            "CREATE TAG tg",
            "CREATE TABLE t1 (a INT, b INT)",
            "CREATE VIEW v1 AS SELECT a FROM t1",
            "CREATE STREAM st1 ON TABLE t1",
            "CREATE TASK tk1 SCHEDULE = '5 MINUTE' AS SELECT 1",
            "ALTER DATABASE td SET TAG tg = 'on-database'",
            "ALTER SCHEMA ts SET TAG tg = 'on-schema'",
            "ALTER TABLE t1 SET TAG tg = 'on-table'",
            "ALTER TABLE t1 MODIFY COLUMN a SET TAG tg = 'on-column'",
            "ALTER VIEW v1 SET TAG tg = 'on-view'",
            "ALTER STREAM st1 SET TAG tg = 'on-stream'",
            "ALTER TASK tk1 SET TAG tg = 'on-task'"),
            // The readings run on a restarted engine, whose session has no current database, so they
            // set one first: a tag lookup resolves its object and its tag against the session's schema.
            Arrays.asList("USE DATABASE td", "USE SCHEMA ts",
                "SELECT SYSTEM$GET_TAG('tg', 'td', 'DATABASE')",
                "SELECT SYSTEM$GET_TAG('tg', 'ts', 'SCHEMA')",
                "SELECT SYSTEM$GET_TAG('tg', 't1', 'TABLE')",
                "SELECT SYSTEM$GET_TAG('tg', 't1.a', 'COLUMN')",
                "SELECT SYSTEM$GET_TAG('tg', 'v1', 'TABLE')",
                "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t1', 'TABLE'))"));
    }

    /** The default warehouse's own settings, which the reader used to skip along with the warehouse. */
    @Test
    public void theDefaultWarehouseKeepsItsSettings() {
        assertSurvivesRestart(Arrays.asList(
            "ALTER WAREHOUSE COMPUTE_WH SET WAREHOUSE_SIZE = LARGE",
            "ALTER WAREHOUSE COMPUTE_WH SET AUTO_SUSPEND = 120",
            "ALTER WAREHOUSE COMPUTE_WH SET COMMENT = 'the default one'"),
            Arrays.asList("SHOW WAREHOUSES LIKE 'COMPUTE_WH'"));
    }

    /** Database roles, grant options, grantors, future grants and database-role grants. */
    @Test
    public void rolesAndGrantDetailsSurviveARestart() {
        assertSurvivesRestart(Arrays.asList(
            "CREATE DATABASE d1",
            "CREATE SCHEMA d1.s1",
            "CREATE TABLE d1.s1.t1 (a INT)",
            "CREATE ROLE r1",
            "CREATE ROLE r2",
            "GRANT ROLE r2 TO ROLE r1",
            "CREATE DATABASE ROLE d1.dr1 COMMENT = 'reader'",
            "GRANT USAGE ON DATABASE d1 TO DATABASE ROLE d1.dr1",
            "GRANT USAGE ON SCHEMA d1.s1 TO DATABASE ROLE d1.dr1",
            "GRANT SELECT ON TABLE d1.s1.t1 TO DATABASE ROLE d1.dr1",
            "GRANT DATABASE ROLE d1.dr1 TO ROLE r1",
            "GRANT SELECT ON TABLE d1.s1.t1 TO ROLE r1 WITH GRANT OPTION",
            "GRANT INSERT ON TABLE d1.s1.t1 TO ROLE r2",
            "GRANT SELECT, INSERT ON FUTURE TABLES IN SCHEMA d1.s1 TO ROLE r2",
            "GRANT SELECT ON FUTURE VIEWS IN DATABASE d1 TO ROLE r1 WITH GRANT OPTION"),
            Arrays.asList("SHOW DATABASE ROLES IN DATABASE d1", "SHOW GRANTS TO DATABASE ROLE d1.dr1",
                "SHOW GRANTS TO ROLE r1", "SHOW GRANTS TO ROLE r2", "SHOW GRANTS OF ROLE r2",
                "SHOW FUTURE GRANTS IN SCHEMA d1.s1", "SHOW FUTURE GRANTS IN DATABASE d1"));
    }

    /** A future grant restored from a snapshot still reaches a table created after the restart. */
    @Test
    public void aRestoredFutureGrantReachesANewTable() {
        final Path data = dir.resolve("future");
        final DatabaseEngine first = snapshotEngine(data);
        first.execute("CREATE DATABASE d1");
        first.execute("CREATE SCHEMA d1.s1");
        first.execute("CREATE ROLE r2");
        first.execute("GRANT SELECT ON FUTURE TABLES IN SCHEMA d1.s1 TO ROLE r2");
        first.shutdown();
        final DatabaseEngine second = snapshotEngine(data);
        second.execute("CREATE TABLE d1.s1.after_restart (a INT)");
        final String grants = rendered(second.executeQuery("SHOW GRANTS ON TABLE d1.s1.after_restart"));
        second.shutdown();
        assertTrue(grants.contains("R2"), grants);
    }

    /** A database's and a schema's retention, transience, managed access and parameters. */
    @Test
    public void containerSettingsSurviveARestart() {
        assertSurvivesRestart(Arrays.asList(
            "CREATE DATABASE d1 DATA_RETENTION_TIME_IN_DAYS = 3",
            "CREATE TRANSIENT DATABASE td1",
            "ALTER DATABASE d1 SET DEFAULT_DDL_COLLATION = 'en-ci'",
            "CREATE SCHEMA d1.m WITH MANAGED ACCESS DATA_RETENTION_TIME_IN_DAYS = 2 COMMENT = 'managed'",
            "CREATE TRANSIENT SCHEMA d1.ts",
            "ALTER SCHEMA d1.m SET MAX_DATA_EXTENSION_TIME_IN_DAYS = 5"),
            Arrays.asList("SHOW DATABASES LIKE '%D1'", "SHOW SCHEMAS IN DATABASE d1",
                "SHOW PARAMETERS IN DATABASE d1", "SHOW PARAMETERS IN SCHEMA d1.m"));
    }

    /** Notebooks, a Streamlit app with a live version, image repositories, services and a service function. */
    @Test
    public void applicationAndContainerObjectsSurviveARestart() {
        assertSurvivesRestart(Arrays.asList(
            "CREATE DATABASE d1",
            "CREATE SCHEMA d1.s1",
            "USE SCHEMA d1.s1",
            "CREATE WAREHOUSE IF NOT EXISTS fl_app_wh1 INITIALLY_SUSPENDED = TRUE",
            "CREATE NOTEBOOK nb1 FROM '@stg/nb' MAIN_FILE = 'nb.ipynb' QUERY_WAREHOUSE = fl_app_wh1"
                + " COMMENT = 'first' IDLE_AUTO_SHUTDOWN_TIME_SECONDS = 600",
            "CREATE STAGE stg",
            STAGED_APP,
            "CREATE STREAMLIT app FROM '@stg/app' MAIN_FILE = 'streamlit_app.py' TITLE = 'My app'"
                + " QUERY_WAREHOUSE = fl_app_wh1 IMPORTS = ('@stg/lib.py') EXTERNAL_ACCESS_INTEGRATIONS = (ext1)",
            "ALTER STREAMLIT app ADD LIVE VERSION FROM LAST",
            "CREATE IMAGE REPOSITORY repo COMMENT = 'images'",
            "CREATE COMPUTE POOL cs_pool MIN_NODES = 1 MAX_NODES = 1 INSTANCE_FAMILY = CPU_X64_XS",
            "CREATE SERVICE svc IN COMPUTE POOL cs_pool FROM SPECIFICATION 'spec: {}' MIN_INSTANCES = 1"
                + " MAX_INSTANCES = 2 COMMENT = 'svc'",
            "ALTER SERVICE svc SUSPEND",
            "EXECUTE JOB SERVICE IN COMPUTE POOL cs_pool FROM SPECIFICATION 'spec: {}' NAME = d1.s1.job1",
            "CREATE API INTEGRATION pypi_int API_PROVIDER = pypi ENABLED = TRUE",
            "CREATE ARTIFACT REPOSITORY ar TYPE = PYPI API_INTEGRATION = pypi_int COMMENT = 'x'",
            "CREATE FUNCTION echo(x VARCHAR) RETURNS VARCHAR SERVICE = svc ENDPOINT = api MAX_BATCH_ROWS = 10"
                + " AS '/echo'"),
            Arrays.asList("SHOW NOTEBOOKS IN SCHEMA d1.s1", "DESCRIBE NOTEBOOK d1.s1.nb1",
                "SHOW STREAMLITS IN SCHEMA d1.s1", "DESCRIBE STREAMLIT d1.s1.app",
                "SHOW IMAGE REPOSITORIES IN SCHEMA d1.s1", "SHOW SERVICES IN SCHEMA d1.s1",
                "SHOW JOB SERVICES IN SCHEMA d1.s1", "SHOW ARTIFACT REPOSITORIES IN SCHEMA d1.s1",
                "DESCRIBE FUNCTION d1.s1.echo(VARCHAR)"));
    }

    /** The versions of a notebook and a Streamlit app — aliases, comments, sources and a live version — survive. */
    @Test
    public void appObjectVersionsSurviveARestart() {
        assertSurvivesRestart(Arrays.asList(
            "CREATE DATABASE d1",
            "CREATE SCHEMA d1.s1",
            "USE SCHEMA d1.s1",
            "CREATE STAGE app_stage",
            "CREATE NOTEBOOK nb1",
            "ALTER NOTEBOOK nb1 ADD LIVE VERSION v1 FROM LAST COMMENT = 'draft'",
            "ALTER NOTEBOOK nb1 COMMIT COMMENT = 'first'",
            "ALTER NOTEBOOK nb1 ADD LIVE VERSION lv FROM LAST",
            "CREATE STREAMLIT app",
            "ALTER STREAMLIT app ADD VERSION v2 FROM '@app_stage/sub' COMMENT = 'staged'"),
            Arrays.asList("SHOW VERSIONS IN NOTEBOOK d1.s1.nb1", "DESCRIBE NOTEBOOK d1.s1.nb1",
                "SHOW VERSIONS IN STREAMLIT d1.s1.app", "DESCRIBE STREAMLIT d1.s1.app"));
    }

    /** An event table, an Iceberg table, and a table's data metrics, change tracking, retention and stage options. */
    @Test
    public void tableSettingsSurviveARestart() {
        assertSurvivesRestart(Arrays.asList(
            "CREATE DATABASE d1",
            "CREATE SCHEMA d1.s1",
            "USE SCHEMA d1.s1",
            "CREATE EVENT TABLE events1 COMMENT = 'telemetry' CHANGE_TRACKING = TRUE",
            "CREATE EXTERNAL VOLUME ice_vol " + LOCATIONS,
            "CREATE ICEBERG TABLE ice1 (a INT, b VARCHAR) EXTERNAL_VOLUME = 'ice_vol' CATALOG = 'SNOWFLAKE'"
                + " BASE_LOCATION = 'ice1/' COMMENT = 'managed'",
            "INSERT INTO ice1 VALUES (1, 'x'), (2, 'y')",
            "CREATE TABLE t1 (id INT, txt VARCHAR) DATA_RETENTION_TIME_IN_DAYS = 4 CHANGE_TRACKING = TRUE",
            "ALTER TABLE t1 SET DATA_METRIC_SCHEDULE = '5 MINUTE'",
            "ALTER TABLE t1 ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (id)",
            "ALTER TABLE t1 SET STAGE_FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|')",
            "CREATE TABLE t2 (id INT) MAX_DATA_EXTENSION_TIME_IN_DAYS = 5 DEFAULT_DDL_COLLATION = 'en-ci'"),
            Arrays.asList("SHOW EVENT TABLES IN SCHEMA d1.s1", "SHOW ICEBERG TABLES IN SCHEMA d1.s1",
                "SELECT * FROM d1.s1.ice1 ORDER BY a", "SHOW TABLES IN SCHEMA d1.s1",
                "USE SCHEMA d1.s1",
                "SHOW PARAMETERS IN TABLE d1.s1.t1", "SHOW PARAMETERS IN TABLE d1.s1.t2",
                "SELECT metric_name, ref_entity_name, schedule_status FROM TABLE(INFORMATION_SCHEMA"
                    + ".DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'T1', REF_ENTITY_DOMAIN => 'TABLE'))",
                "DESCRIBE TABLE d1.s1.t1 TYPE = STAGE"));
    }

    /** Alerts and materialized views. */
    @Test
    public void alertsAndMaterializedViewsSurviveARestart() {
        assertSurvivesRestart(Arrays.asList(
            "CREATE DATABASE d1",
            "CREATE SCHEMA d1.s1",
            "USE SCHEMA d1.s1",
            "CREATE TABLE src (a INT, b VARCHAR)",
            "INSERT INTO src VALUES (1, 'x'), (2, 'y')",
            "CREATE MATERIALIZED VIEW mv1 COMMENT = 'mat' AS SELECT a FROM src WHERE a > 1",
            "CREATE MATERIALIZED VIEW mv2 CLUSTER BY (a, b) AS SELECT a, b FROM src",
            "CREATE MATERIALIZED VIEW mv3 CLUSTER BY (a) AS SELECT a, b FROM src",
            "ALTER MATERIALIZED VIEW mv3 SUSPEND RECLUSTER",
            "CREATE ALERT al1 WAREHOUSE = compute_wh SCHEDULE = '60 MINUTE'"
                + " IF (EXISTS (SELECT 1 FROM src)) THEN SELECT 1",
            "CREATE ALERT al2 SCHEDULE = '30 MINUTE' IF (EXISTS (SELECT 2)) THEN SELECT 2"),
            Arrays.asList("SHOW MATERIALIZED VIEWS IN SCHEMA d1.s1", "SELECT * FROM d1.s1.mv1",
                "SHOW ALERTS IN SCHEMA d1.s1"));
    }

    /** A warehouse's settings beyond its size and state, and a stage's encryption, directory and options. */
    @Test
    public void warehouseAndStageSettingsSurviveARestart() {
        assertSurvivesRestart(Arrays.asList(
            "CREATE WAREHOUSE wh2 WAREHOUSE_SIZE = SMALL INITIALLY_SUSPENDED = TRUE MAX_CONCURRENCY_LEVEL = 4"
                + " STATEMENT_TIMEOUT_IN_SECONDS = 100 STATEMENT_QUEUED_TIMEOUT_IN_SECONDS = 50"
                + " ENABLE_QUERY_ACCELERATION = TRUE QUERY_ACCELERATION_MAX_SCALE_FACTOR = 4",
            "ALTER WAREHOUSE wh2 SET GENERATION = '1'",
            "CREATE DATABASE d1",
            "CREATE SCHEMA d1.s1",
            // REFRESH_ON_CREATE = FALSE leaves the directory table without a moment of its own, which two engines
            // running these statements would not share.
            "CREATE STAGE d1.s1.st1 ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE')"
                + " DIRECTORY = (ENABLE = TRUE AUTO_REFRESH = TRUE REFRESH_ON_CREATE = FALSE)"
                + " FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|') COPY_OPTIONS = (ON_ERROR = CONTINUE)",
            "CREATE STAGE d1.s1.st2",
            "ALTER STAGE d1.s1.st2 SET DIRECTORY = (ENABLE = FALSE)"),
            Arrays.asList("SHOW WAREHOUSES LIKE 'WH2'", "SHOW PARAMETERS IN WAREHOUSE wh2",
                "SHOW STAGES IN SCHEMA d1.s1", "DESCRIBE STAGE d1.s1.st1",
                "SELECT stage_name, directory_enabled FROM d1.information_schema.stages ORDER BY 1"));
    }

    /**
     * The moments the REST-era objects carry — creation, change, resume and suspension times, the time of each
     * grant and future grant — and a notebook's and a Streamlit app's URL id are the object's own for its
     * lifetime, so every restart reads each of those cells back unchanged.
     */
    @Test
    public void creationTimesGrantTimesAndUrlIdsSurviveARestart() {
        assertKeepsEveryCell(Arrays.asList(
            "CREATE API INTEGRATION it_api API_PROVIDER = aws_api_gateway"
                + " API_AWS_ROLE_ARN = 'arn:aws:iam::1:role/r' API_ALLOWED_PREFIXES = ('https://a.example.com/')"
                + " ENABLED = TRUE",
            "CREATE EXTERNAL VOLUME ev_a " + LOCATIONS,
            "CREATE COMPUTE POOL p1 MIN_NODES = 1 MAX_NODES = 1 INSTANCE_FAMILY = CPU_X64_XS"
                + " INITIALLY_SUSPENDED = TRUE",
            "ALTER COMPUTE POOL p1 RESUME",
            "CREATE ACCOUNT acc_one ADMIN_NAME = admin ADMIN_PASSWORD = 'TestPassword1'"
                + " EMAIL = 'a@example.com' EDITION = STANDARD",
            "CREATE MANAGED ACCOUNT reader_one ADMIN_NAME = admin, ADMIN_PASSWORD = 'Sdfed43da!44', TYPE = READER",
            "CREATE DATABASE d1",
            "CREATE SCHEMA d1.s1",
            "USE SCHEMA d1.s1",
            "CREATE NOTEBOOK nb1 FROM '@stg/nb' MAIN_FILE = 'nb.ipynb'",
            "CREATE STAGE stg",
            STAGED_APP,
            "CREATE STREAMLIT app FROM '@stg/app' MAIN_FILE = 'streamlit_app.py'",
            "CREATE IMAGE REPOSITORY repo",
            "CREATE SERVICE svc IN COMPUTE POOL p1 FROM SPECIFICATION 'spec: {}'",
            "ALTER SERVICE svc SUSPEND",
            "ALTER SERVICE svc RESUME",
            "EXECUTE JOB SERVICE IN COMPUTE POOL p1 FROM SPECIFICATION 'spec: {}'",
            "CREATE API INTEGRATION pypi_int API_PROVIDER = pypi ENABLED = TRUE",
            "CREATE ARTIFACT REPOSITORY ar TYPE = PYPI API_INTEGRATION = pypi_int",
            "CREATE TABLE src (a INT)",
            "CREATE MATERIALIZED VIEW mv1 AS SELECT a FROM src",
            "CREATE STAGE st_dir DIRECTORY = (ENABLE = TRUE)",
            "COPY INTO @st_dir/kept.csv FROM (SELECT 1) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE",
            "ALTER STAGE st_dir REFRESH",
            "CREATE ALERT al1 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1",
            "CREATE DATABASE ROLE d1.dr1",
            "GRANT USAGE ON DATABASE d1 TO DATABASE ROLE d1.dr1",
            "CREATE ROLE r1",
            "GRANT DATABASE ROLE d1.dr1 TO ROLE r1",
            "GRANT SELECT ON TABLE d1.s1.src TO ROLE r1",
            "GRANT SELECT ON FUTURE TABLES IN SCHEMA d1.s1 TO ROLE r1"),
            Arrays.asList("SHOW INTEGRATIONS", "SHOW EXTERNAL VOLUMES", "SHOW COMPUTE POOLS", "SHOW ACCOUNTS",
                "SHOW MANAGED ACCOUNTS", "SHOW NOTEBOOKS IN SCHEMA d1.s1", "SHOW STREAMLITS IN SCHEMA d1.s1",
                "SHOW IMAGE REPOSITORIES IN SCHEMA d1.s1", "SHOW SERVICES IN SCHEMA d1.s1",
                "SHOW JOB SERVICES IN SCHEMA d1.s1", "SHOW ARTIFACT REPOSITORIES IN SCHEMA d1.s1",
                "SHOW MATERIALIZED VIEWS IN SCHEMA d1.s1", "SHOW ALERTS IN SCHEMA d1.s1",
                "DESCRIBE STAGE d1.s1.st_dir", "SELECT relative_path, size, md5, etag FROM DIRECTORY(@d1.s1.st_dir)",
                "SHOW DATABASE ROLES IN DATABASE d1", "SHOW GRANTS TO DATABASE ROLE d1.dr1",
                "SHOW GRANTS TO ROLE r1", "SHOW FUTURE GRANTS IN SCHEMA d1.s1"));
    }

    /**
     * A dropped external volume, notebook and Streamlit app stay restorable across a restart, and UNDROP brings
     * back the object that was dropped: every cell of its listing, its creation time and URL id included, reads
     * as it did before the drop.
     */
    @Test
    public void droppedObjectsCanBeUndroppedAfterARestart() {
        assertUndropsAfterRestart(
            Arrays.asList(
                "CREATE EXTERNAL VOLUME ev_d " + LOCATIONS + " COMMENT = 'dropped'",
                "CREATE DATABASE d1",
                "CREATE SCHEMA d1.s1",
                "CREATE NOTEBOOK d1.s1.nb_d FROM '@stg/nb' MAIN_FILE = 'nb.ipynb' COMMENT = 'dropped'",
                "CREATE STAGE d1.s1.stg",
                "COPY INTO @d1.s1.stg/app/streamlit_app.py FROM (SELECT 'import streamlit')"
                    + " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE OVERWRITE = TRUE",
                "CREATE STREAMLIT d1.s1.st_d FROM '@d1.s1.stg/app' MAIN_FILE = 'streamlit_app.py'"),
            Arrays.asList("DROP EXTERNAL VOLUME ev_d", "DROP NOTEBOOK d1.s1.nb_d", "DROP STREAMLIT d1.s1.st_d"),
            Arrays.asList("UNDROP EXTERNAL VOLUME ev_d", "UNDROP NOTEBOOK d1.s1.nb_d", "UNDROP STREAMLIT d1.s1.st_d"),
            Arrays.asList("SHOW EXTERNAL VOLUMES", "SHOW NOTEBOOKS IN SCHEMA d1.s1", "SHOW STREAMLITS IN SCHEMA d1.s1"));
    }

    /**
     * An alert's evaluations and a task's runs, which ALERT_HISTORY and TASK_HISTORY report, survive a restart
     * through a snapshot or a checkpoint, with a task's last run and its failures. The WAL log does not record
     * the runs themselves, only the EXECUTE statements, so a replay runs them again rather than restoring them.
     */
    @Test
    public void alertAndTaskRunHistorySurvivesASnapshotOrCheckpointRestart() {
        final List<String> statements = Arrays.asList(
            "CREATE DATABASE d1",
            "CREATE SCHEMA d1.s1",
            "USE SCHEMA d1.s1",
            "CREATE TABLE log (a INT)",
            "CREATE ALERT al1 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT * FROM log)) THEN SELECT 1",
            "EXECUTE ALERT al1",
            "INSERT INTO log VALUES (1)",
            "EXECUTE ALERT al1",
            "CREATE TASK tk1 SCHEDULE = '60 MINUTE' AS INSERT INTO log VALUES (2)",
            "EXECUTE TASK tk1",
            "CREATE TASK tk2 SCHEDULE = '60 MINUTE' AS INSERT INTO no_such_table VALUES (3)",
            "EXECUTE TASK tk2");
        final List<String> listings = Arrays.asList(
            "SELECT name, state, scheduled_time, completed_time, scheduled_from"
                + " FROM TABLE(d1.INFORMATION_SCHEMA.ALERT_HISTORY()) ORDER BY scheduled_time",
            "SELECT name, state, error_message, scheduled_time, query_start_time, completed_time, scheduled_from"
                + " FROM TABLE(d1.INFORMATION_SCHEMA.TASK_HISTORY()) ORDER BY name, query_start_time");

        final Path snapshotDir = dir.resolve("snapshot-history");
        final DatabaseEngine snapshotFirst = snapshotEngine(snapshotDir);
        final List<String> snapshotBefore = run(snapshotFirst, statements, listings, true);
        snapshotFirst.shutdown();
        final DatabaseEngine snapshotSecond = snapshotEngine(snapshotDir);
        assertSameReadings(snapshotBefore, readings(snapshotSecond, listings, true), "after a snapshot restart");
        snapshotSecond.shutdown();

        final Path checkpointWal = dir.resolve("checkpoint-history").resolve("wal.log");
        final DatabaseEngine checkpointFirst = walEngine(checkpointWal);
        final List<String> checkpointBefore = run(checkpointFirst, statements, listings, true);
        checkpointFirst.checkpoint();
        checkpointFirst.shutdown();
        final DatabaseEngine checkpointSecond = walEngine(checkpointWal);
        assertSameReadings(checkpointBefore, readings(checkpointSecond, listings, true),
            "after a WAL checkpoint restart");
        checkpointSecond.shutdown();
    }

    /**
     * A warehouse, a user and a role are created once: each restart reads back the creation time they were given,
     * and a role granted to a role or to a user is listed with the time of the grant.
     */
    @Test
    public void warehousesUsersRolesAndRoleGrantsKeepTheirTimes() {
        assertKeepsEveryCell(Arrays.asList(
            "CREATE WAREHOUSE wh_kept INITIALLY_SUSPENDED = TRUE",
            "CREATE USER u_kept",
            "CREATE ROLE r_holder",
            "CREATE ROLE r_held",
            "GRANT ROLE r_held TO ROLE r_holder",
            "GRANT ROLE r_holder TO USER u_kept"),
            Arrays.asList("SHOW WAREHOUSES LIKE 'WH_KEPT' ->> SELECT \"name\", \"created_on\" FROM $1",
                "SHOW USERS LIKE 'U_KEPT'", "SHOW ROLES LIKE 'R_H%'",
                "SHOW GRANTS TO ROLE r_holder", "SHOW GRANTS OF ROLE r_held", "SHOW GRANTS OF ROLE r_holder",
                "SHOW GRANTS TO USER u_kept"));
    }

    /** A role grant carries the time it was made, which a snapshot keeps: it is not the holder's creation time. */
    @Test
    public void aRoleGrantsTimeIsItsOwn() {
        final Path data = dir.resolve("grant-times");
        final DatabaseEngine first = snapshotEngine(data);
        first.execute("CREATE ROLE r_holder");
        first.execute("CREATE ROLE r_held");
        first.execute("CREATE USER u_kept");
        first.execute("GRANT ROLE r_held TO ROLE r_holder");
        first.execute("GRANT ROLE r_held TO USER u_kept");
        final Instant toRole = first.getCatalog().getRole("R_HOLDER").getRoleGrantTime("R_HELD");
        final Instant toUser = first.getCatalog().getUser("U_KEPT").getRoleGrantTime("R_HELD");
        assertNotNull(toRole, "a role granted to a role records when");
        assertNotNull(toUser, "a role granted to a user records when");
        first.shutdown();

        final DatabaseEngine second = snapshotEngine(data);
        assertEquals(toRole, second.getCatalog().getRole("R_HOLDER").getRoleGrantTime("R_HELD"));
        assertEquals(toUser, second.getCatalog().getUser("U_KEPT").getRoleGrantTime("R_HELD"));
        second.execute("REVOKE ROLE r_held FROM ROLE r_holder");
        assertNull(second.getCatalog().getRole("R_HOLDER").getRoleGrantTime("R_HELD"));
        second.shutdown();
    }

    /**
     * A task's id and the name a constraint is given for want of one are drawn once: a replay of the statements
     * that created them reads both back as they were.
     */
    @Test
    public void taskIdsAndGeneratedConstraintNamesSurviveAReplay() {
        assertKeepsEveryCell(Arrays.asList(
            "CREATE DATABASE d1",
            "CREATE SCHEMA d1.s1",
            "USE SCHEMA d1.s1",
            "CREATE TABLE keyed (a INT PRIMARY KEY, b INT UNIQUE)",
            "CREATE TABLE referring (a INT REFERENCES keyed (a))",
            "CREATE TASK tk_kept SCHEDULE = '60 MINUTE' AS SELECT 1"),
            Arrays.asList(
                "SHOW TASKS IN SCHEMA d1.s1",
                "SHOW PRIMARY KEYS IN TABLE d1.s1.keyed", "SHOW UNIQUE KEYS IN TABLE d1.s1.keyed",
                "SHOW IMPORTED KEYS IN TABLE d1.s1.referring"));
    }

    /**
     * A run the schedule starts executes below the write-ahead log, and a run a statement starts would be run
     * again by a replay of that statement: after a restart with no checkpoint asked for, the rows each run wrote
     * are there once and the run history reads as it did.
     */
    @Test
    public void taskRunsSurviveAWalRestart() {
        final List<String> listings = Arrays.asList(
            "SELECT a FROM d1.s1.log ORDER BY a",
            "SELECT name, state, scheduled_time, query_start_time, completed_time, scheduled_from"
                + " FROM TABLE(d1.INFORMATION_SCHEMA.TASK_HISTORY()) ORDER BY name, query_start_time");
        final Path wal = dir.resolve("task-runs").resolve("wal.log");
        final DatabaseEngine first = walEngine(wal);
        first.execute("CREATE DATABASE d1");
        first.execute("CREATE SCHEMA d1.s1");
        first.execute("USE SCHEMA d1.s1");
        first.execute("CREATE TABLE log (a INT)");
        first.execute("CREATE TASK tk_scheduled SCHEDULE = '60 MINUTE' AS INSERT INTO d1.s1.log VALUES (1)");
        first.execute("CREATE TASK tk_manual SCHEDULE = '60 MINUTE' AS INSERT INTO d1.s1.log VALUES (2)");
        // The schedule's own way in, without waiting for the clock.
        first.getTaskScheduler().executeTaskNow(TaskScheduler.schedulerKey("D1", "S1", "TK_SCHEDULED"),
            first.getCatalog().getDatabase("D1").getSchema("S1").getTask("TK_SCHEDULED"));
        first.execute("EXECUTE TASK tk_manual");
        first.execute("INSERT INTO log VALUES (3)");
        final List<String> before = readings(first, listings, true);
        assertTrue(before.get(0).endsWith("| 1 | 2 | 3"), before.get(0));
        first.shutdown();

        final DatabaseEngine second = walEngine(wal);
        assertSameReadings(before, readings(second, listings, true), "after a WAL restart");
        second.shutdown();
    }

    /**
     * A pipe's refresh loads in a session of its own, as a task's run does, and the stage files it read owe the
     * checkpoint that follows it: a WAL restart reads the table as the refresh left it, and does not load the stage
     * again as it stands then.
     */
    @Test
    public void aPipeRefreshIsNotLoadedAgainByAWalRestart() throws IOException {
        final Path files = Files.createDirectories(dir.resolve("pipe-refresh-files"));
        Files.writeString(files.resolve("a.csv"), "1\n2\n");
        final Path wal = dir.resolve("pipe-refresh").resolve("wal.log");
        final EngineConfig config = walConfig(wal);
        config.setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        final DatabaseEngine first = new DatabaseEngine(config);
        first.execute("CREATE DATABASE d1");
        first.execute("CREATE SCHEMA d1.s1");
        first.execute("USE SCHEMA d1.s1");
        first.execute("CREATE TABLE loaded (a INT)");
        first.execute("CREATE STAGE st URL='file://" + files + "'");
        first.execute("CREATE PIPE pp AS COPY INTO loaded FROM @st FILE_FORMAT = (TYPE = 'CSV')");
        first.execute("ALTER PIPE pp REFRESH");
        final List<String> listings = Arrays.asList("SELECT a FROM d1.s1.loaded ORDER BY a");
        final List<String> before = readings(first, listings, true);
        assertTrue(before.get(0).endsWith("| 1 | 2"), before.get(0));
        // A file staged after the refresh is no part of what it loaded.
        Files.writeString(files.resolve("b.csv"), "3\n");
        first.shutdown();

        final EngineConfig again = walConfig(wal);
        again.setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        final DatabaseEngine second = new DatabaseEngine(again);
        assertSameReadings(before, readings(second, listings, true), "after a WAL restart");
        second.shutdown();
    }

    /**
     * A warehouse keeps when it last changed state across a snapshot restart, as it keeps when it was created:
     * SHOW WAREHOUSES reads the same resumed_on and updated_on afterwards.
     */
    @Test
    public void aWarehouseKeepsWhenItLastChangedStateAcrossASnapshotRestart() {
        final Path data = dir.resolve("warehouse-moments");
        final DatabaseEngine first = snapshotEngine(data);
        first.execute("CREATE WAREHOUSE wh_moments WITH INITIALLY_SUSPENDED = TRUE");
        final String before = warehouseMoments(first);
        first.shutdown();
        final DatabaseEngine second = snapshotEngine(data);
        assertEquals(before, warehouseMoments(second), "after a snapshot restart");
        second.shutdown();
    }

    /**
     * A task run that a statement inside an open transaction starts owes its checkpoint to the end of the
     * transaction: after a restart with no checkpoint asked for, the run's row is there once.
     */
    @Test
    public void aTaskRunInsideATransactionIsNotRunAgainByAWalRestart() {
        final Path wal = dir.resolve("task-in-transaction").resolve("wal.log");
        final DatabaseEngine first = walEngine(wal);
        first.execute("CREATE DATABASE d1");
        first.execute("CREATE SCHEMA d1.s1");
        first.execute("USE SCHEMA d1.s1");
        first.execute("CREATE TABLE log (a INT)");
        first.execute("CREATE TASK tk_in_txn SCHEDULE = '60 MINUTE' AS INSERT INTO d1.s1.log VALUES (1)");
        first.execute("BEGIN");
        first.execute("EXECUTE TASK tk_in_txn");
        first.execute("COMMIT");
        assertEquals(1L, ((Number) first.executeQuery("SELECT COUNT(*) FROM d1.s1.log").getRows().get(0)
            .getValue(0)).longValue());
        first.shutdown();
        final DatabaseEngine second = walEngine(wal);
        assertEquals(1L, ((Number) second.executeQuery("SELECT COUNT(*) FROM d1.s1.log").getRows().get(0)
            .getValue(0)).longValue(), "after a WAL restart");
        second.shutdown();
    }

    /** When a warehouse was created, last resumed and last updated, as SHOW WAREHOUSES lists them. */
    private static String warehouseMoments(final DatabaseEngine engine) {
        engine.execute("SHOW WAREHOUSES LIKE 'WH_MOMENTS'");
        final ResultSet moments = engine.executeQuery("SELECT \"created_on\", \"resumed_on\", \"updated_on\" "
            + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        return String.valueOf(moments.getRows().get(0).getValues());
    }

    // ------------------------------------------------------------------ the three restarts

    /**
     * Runs the statements, reads the listings, then checks that each of the three restarts reads them back
     * the same.
     */
    private void assertSurvivesRestart(final List<String> statements, final List<String> listings) {
        final Path snapshotDir = dir.resolve("snapshot");
        final DatabaseEngine snapshotFirst = snapshotEngine(snapshotDir);
        final List<String> expected = run(snapshotFirst, statements, listings);
        snapshotFirst.shutdown();
        final DatabaseEngine snapshotSecond = snapshotEngine(snapshotDir);
        assertSameReadings(expected, readings(snapshotSecond, listings), "after a snapshot restart");
        snapshotSecond.shutdown();

        final Path checkpointWal = dir.resolve("checkpoint").resolve("wal.log");
        final DatabaseEngine checkpointFirst = walEngine(checkpointWal);
        assertSameReadings(expected, run(checkpointFirst, statements, listings), "the WAL engine answers alike");
        checkpointFirst.checkpoint();
        checkpointFirst.shutdown();
        final DatabaseEngine checkpointSecond = walEngine(checkpointWal);
        assertSameReadings(expected, readings(checkpointSecond, listings), "after a WAL checkpoint restart");
        checkpointSecond.shutdown();

        final Path replayWal = dir.resolve("replay").resolve("wal.log");
        final DatabaseEngine replayFirst = walEngine(replayWal);
        run(replayFirst, statements, listings);
        replayFirst.shutdown();
        final DatabaseEngine replaySecond = walEngine(replayWal);
        assertSameReadings(expected, readings(replaySecond, listings), "after a WAL replay");
        replaySecond.shutdown();
    }

    /**
     * As {@link #assertSurvivesRestart}, keeping every cell: each restart must read back the very moments and ids
     * the engine before it read. Each engine is compared with its own restart only — two engines running the
     * same statements create their objects at different moments.
     */
    private void assertKeepsEveryCell(final List<String> statements, final List<String> listings) {
        final Path snapshotDir = dir.resolve("snapshot-cells");
        final DatabaseEngine snapshotFirst = snapshotEngine(snapshotDir);
        final List<String> snapshotBefore = run(snapshotFirst, statements, listings, true);
        snapshotFirst.shutdown();
        final DatabaseEngine snapshotSecond = snapshotEngine(snapshotDir);
        assertSameReadings(snapshotBefore, readings(snapshotSecond, listings, true), "after a snapshot restart");
        snapshotSecond.shutdown();

        final Path checkpointWal = dir.resolve("checkpoint-cells").resolve("wal.log");
        final DatabaseEngine checkpointFirst = walEngine(checkpointWal);
        final List<String> checkpointBefore = run(checkpointFirst, statements, listings, true);
        checkpointFirst.checkpoint();
        checkpointFirst.shutdown();
        final DatabaseEngine checkpointSecond = walEngine(checkpointWal);
        assertSameReadings(checkpointBefore, readings(checkpointSecond, listings, true),
            "after a WAL checkpoint restart");
        checkpointSecond.shutdown();

        final Path replayWal = dir.resolve("replay-cells").resolve("wal.log");
        final DatabaseEngine replayFirst = walEngine(replayWal);
        final List<String> replayBefore = run(replayFirst, statements, listings, true);
        replayFirst.shutdown();
        final DatabaseEngine replaySecond = walEngine(replayWal);
        assertSameReadings(replayBefore, readings(replaySecond, listings, true), "after a WAL replay");
        replaySecond.shutdown();
    }

    /**
     * Creates the objects and reads their listings, drops them and restarts; after each of the three restarts the
     * UNDROPs must bring back listings reading every cell as before the drop.
     */
    private void assertUndropsAfterRestart(final List<String> creates, final List<String> drops,
                                           final List<String> undrops, final List<String> listings) {
        final String[] modes = {"snapshot", "checkpoint", "replay"};
        for (final String mode : modes) {
            final Path data = dir.resolve("undrop-" + mode);
            final DatabaseEngine first = "snapshot".equals(mode) ? snapshotEngine(data)
                : walEngine(data.resolve("wal.log"));
            final List<String> before = run(first, creates, listings, true);
            for (final String drop : drops) {
                first.execute(drop);
            }
            if ("checkpoint".equals(mode)) {
                first.checkpoint();
            }
            first.shutdown();
            final DatabaseEngine second = "snapshot".equals(mode) ? snapshotEngine(data)
                : walEngine(data.resolve("wal.log"));
            assertSameReadings(before, run(second, undrops, listings, true), "UNDROP after a " + mode + " restart");
            second.shutdown();
        }
    }

    /** Compares listing by listing, so a failure names the one listing that differs. */
    private static void assertSameReadings(final List<String> expected, final List<String> actual, final String when) {
        assertEquals(expected.size(), actual.size(), when);
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), actual.get(i), when);
        }
    }

    private List<String> run(final DatabaseEngine engine, final List<String> statements,
                             final List<String> listings) {
        return run(engine, statements, listings, false);
    }

    private List<String> run(final DatabaseEngine engine, final List<String> statements,
                             final List<String> listings, final boolean everyCell) {
        for (final String statement : statements) {
            engine.execute(statement);
        }
        return readings(engine, listings, everyCell);
    }

    private DatabaseEngine snapshotEngine(final Path data) {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_PERSISTENCE_DIRECTORY, data.toString());
        config.setProperty(EngineConfig.PROP_PERSISTENCE_AUTO_SAVE, "false");
        return new DatabaseEngine(config);
    }

    private DatabaseEngine walEngine(final Path walFile) {
        return new DatabaseEngine(walConfig(walFile));
    }

    /** A configuration that keeps a write-ahead log in the given file and checkpoints only when a run asks. */
    private static EngineConfig walConfig(final Path walFile) {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, walFile.toString());
        config.setProperty(EngineConfig.PROP_DURABILITY_CHECKPOINT_INTERVAL, "0");
        return config;
    }

    // ------------------------------------------------------------------ readings

    /** Each listing's rows, rendered without the cells that record a moment. */
    private List<String> readings(final DatabaseEngine engine, final List<String> listings) {
        return readings(engine, listings, false);
    }

    /** Each listing's rows, rendered with every cell or without the cells that record a moment. */
    private List<String> readings(final DatabaseEngine engine, final List<String> listings, final boolean everyCell) {
        final List<String> out = new ArrayList<>();
        for (final String listing : listings) {
            out.add(listing + " => " + rendered(engine.executeQuery(listing), everyCell));
        }
        return out;
    }

    private static String rendered(final ResultSet rs) {
        return rendered(rs, false);
    }

    private static String rendered(final ResultSet rs, final boolean everyCell) {
        final List<Integer> kept = new ArrayList<>();
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            final String name = rs.getColumns().get(i).getName().toLowerCase(Locale.ROOT);
            if (everyCell ? !readsTheClock(name) : !renewedByRestart(name)) {
                kept.add(i);
                out.append(name).append(i + 1 < rs.getColumns().size() ? "," : "");
            }
        }
        for (final Row row : rs.getRows()) {
            out.append(" |");
            for (final Integer i : kept) {
                // An external ID is generated for each creation: two engines running the same statements never
                // share one, so only the cases that keep every cell compare it.
                out.append(' ').append(everyCell ? String.valueOf(row.getValue(i))
                    : String.valueOf(row.getValue(i)).replaceAll(GENERATED_ID, "<generated id>"));
            }
        }
        return out.toString();
    }

    /** A cell measured against the clock when the listing runs, not a moment the object keeps. */
    private static boolean readsTheClock(final String column) {
        return "behind_by".equals(column);
    }

    /**
     * Creation, change and resume times, generated URL ids and the session's current container: the cells the
     * cases on definitions leave out.
     */
    private static boolean renewedByRestart(final String column) {
        return column.endsWith("_on") || column.startsWith("last_") || column.contains("scheduled_")
            || column.contains("url") || column.contains("uri") || column.endsWith("_at")
            || "is_current".equals(column) || "behind_by".equals(column);
    }

    private static List<String> column(final ResultSet rs, final String name) {
        int index = -1;
        for (int i = 0; i < rs.getColumns().size(); i++) {
            if (rs.getColumns().get(i).getName().equalsIgnoreCase(name)) {
                index = i;
            }
        }
        final List<String> values = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            values.add(String.valueOf(row.getValue(index)));
        }
        return values;
    }

    private void deleteRecursively(final Path p) throws IOException {
        if (Files.isDirectory(p)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(p)) {
                for (final Path child : children) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(p);
    }
}
