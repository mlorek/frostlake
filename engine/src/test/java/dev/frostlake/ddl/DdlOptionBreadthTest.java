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
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.View;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DDL/DML option breadth from the Snowflake-corpus audit: CLONE with time travel (accepted; clones
 * CURRENT state), TAG clauses (accepted and inert), COPY GRANTS, ROW ACCESS POLICY attached at
 * CREATE (same effect as the ALTER form), UNDROP TAG, SAMPLE method/seed variants incl. a
 * session-variable size, CREATE STAGE property forms, COPY INTO parenthesized option groups,
 * DESCRIBE ... TYPE=STAGE, ALTER TABLE SET property forms and per-column IF NOT EXISTS.
 */
public class DdlOptionBreadthTest extends BaseDatabaseTest {

    private long count(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void cloneAcceptsTimeTravelAndClonesCurrentState() {
        engine.execute("CREATE TABLE tt_src (id INTEGER)");
        engine.execute("INSERT INTO tt_src VALUES (1), (2)");
        engine.execute("CREATE TABLE tt_at CLONE tt_src AT (OFFSET => -3600)");
        assertEquals(2, count("SELECT COUNT(*) FROM tt_at"));
        engine.execute("CREATE TABLE tt_before CLONE tt_src BEFORE (STATEMENT => '8e5d0ca9-005e-44e6-b858-a8f5b37c5726')");
        assertEquals(2, count("SELECT COUNT(*) FROM tt_before"));
        engine.execute("CREATE SCHEMA tt_schema_clone CLONE test_schema AT (TIMESTAMP => CURRENT_TIMESTAMP())");
        engine.execute("CREATE DATABASE tt_db_clone CLONE test_db AT (OFFSET => -60)");
    }

    @Test
    public void tagClausesAreAcceptedEverywhere() {
        engine.execute("CREATE TABLE tagged (id INTEGER) TAG (key1='value_1', key2='value_2')");
        engine.execute("ALTER TABLE tagged ADD col1 VARCHAR NOT NULL TAG (key1='value_1'), col2 VARCHAR TAG (key2='v2')");
        assertEquals(0, count("SELECT COUNT(*) FROM tagged"));
        engine.execute("CREATE TABLE col_tagged (id INTEGER TAG (k='v'), name VARCHAR)");
        engine.execute("""
            CREATE OR REPLACE MATERIALIZED VIEW mv_tagged COPY GRANTS (a, b) COMMENT='foo' TAG (a='b')
            AS SELECT id AS a, name AS b FROM col_tagged
            """);
        engine.execute("CREATE OR REPLACE VIEW v_grants (uid) COPY GRANTS AS (SELECT 1)");
        assertEquals(1, count("SELECT COUNT(*) FROM v_grants"));
    }

    @Test
    public void rowAccessPolicyAttachesAtCreate() {
        // Attach metadata is asserted directly — the test session runs as an admin role, which
        // BYPASSES row-access filtering (Snowflake semantics), so filtered counts can't be used here.
        engine.execute("CREATE ROW ACCESS POLICY eu_only AS (r VARCHAR) RETURNS BOOLEAN -> r = 'EU'");
        engine.execute("CREATE TABLE regions (r VARCHAR) ROW ACCESS POLICY eu_only ON (r)");
        final Table table =
            engine.getCatalog().getDatabase("TEST_DB").getSchema("TEST_SCHEMA").getTable("REGIONS");
        assertTrue(table.hasRowAccessPolicy(), "CREATE TABLE ... ROW ACCESS POLICY must attach");
        assertEquals("EU_ONLY", table.getRowAccessPolicyName());
        assertEquals(1, table.getRowAccessPolicyColumns().size());

        engine.execute("CREATE VIEW eu_view WITH ROW ACCESS POLICY eu_only ON (r) AS SELECT r FROM regions");
        final View view =
            engine.getCatalog().getDatabase("TEST_DB").getSchema("TEST_SCHEMA").getView("EU_VIEW");
        assertTrue(view.hasRowAccessPolicy(), "CREATE VIEW ... WITH ROW ACCESS POLICY must attach");
        assertEquals("EU_ONLY", view.getRowAccessPolicyName());
    }

    @Test
    public void undropTagRestoresIt() {
        engine.execute("CREATE TAG my_tag");
        engine.execute("DROP TAG my_tag");
        assertEquals(0, engine.executeQuery("SHOW TAGS LIKE 'MY_TAG'").getRowCount());
        engine.execute("UNDROP TAG my_tag");
        assertEquals(1, engine.executeQuery("SHOW TAGS LIKE 'MY_TAG'").getRowCount());
    }

    @Test
    public void sampleVariants() {
        engine.execute("CREATE TABLE s_t (id INTEGER)");
        engine.execute("INSERT INTO s_t VALUES (1), (2), (3), (4)");
        assertEquals(4, count("SELECT COUNT(*) FROM s_t SAMPLE BLOCK (100) SEED (42)"));
        assertEquals(4, count("SELECT COUNT(*) FROM s_t SAMPLE BLOCK (100) REPEATABLE (99992)"));
        engine.execute("SET s = 100");
        assertEquals(4, count("SELECT COUNT(*) FROM s_t SAMPLE BERNOULLI ($s) SEED (0)"));
    }

    @Test
    public void createStagePropertyForms() {
        engine.execute("CREATE FILE FORMAT fmt1 TYPE = 'CSV'");
        engine.execute("CREATE STAGE st_string FILE_FORMAT='fmt1'");
        engine.execute("CREATE STAGE st_paren FILE_FORMAT=(TYPE=PARQUET)");
        engine.execute("CREATE STAGE st_named FILE_FORMAT=(FORMAT_NAME=test_schema.fmt1)");
        engine.execute("CREATE STAGE st_named_str FILE_FORMAT=(FORMAT_NAME='test_schema.fmt1')");
        engine.execute("CREATE STAGE st_qual FILE_FORMAT=test_schema.fmt1");
        engine.execute("CREATE TEMPORARY STAGE st_temp FILE_FORMAT=(TYPE=PARQUET)");
        engine.execute("CREATE STAGE st_creds URL='s3://bucket-123' FILE_FORMAT=(TYPE='JSON') "
            + "CREDENTIALS=(aws_key_id='test' aws_secret_key='test')");
        assertTrue(engine.executeQuery("SHOW STAGES").getRowCount() >= 7);
    }

    @Test
    public void copyOptionGroupsParse() {
        engine.execute("CREATE TABLE load1 (c1 VARCHAR)");
        // The statements must get PAST the parser; execution then fails on the missing stage/files,
        // which is the expected (non-syntax) outcome in a fixture-less engine.
        try {
            engine.execute("COPY INTO load1 FROM @%load1/data1/ "
                + "CREDENTIALS = (AWS_KEY_ID='id' AWS_SECRET_KEY='key' AWS_TOKEN='token') "
                + "FILES = ('test1.csv', 'test2.csv') FORCE = TRUE");
        } catch (final RuntimeException e) {
            assertTrue(!(e instanceof dev.frostlake.parser.SqlSyntaxException)
                && !(e.getCause() instanceof dev.frostlake.parser.SqlSyntaxException),
                "must not be a syntax error: " + e.getMessage());
        }
        try {
            engine.execute("COPY INTO load1 (c1) FROM 's3://mybucket/data/files' "
                + "STORAGE_INTEGRATION = \"storage\" ENCRYPTION = (TYPE='NONE' MASTER_KEY='key') "
                + "FILES = ('file1.csv') CREDENTIALS = ()");
        } catch (final RuntimeException e) {
            assertTrue(!(e instanceof dev.frostlake.parser.SqlSyntaxException)
                && !(e.getCause() instanceof dev.frostlake.parser.SqlSyntaxException),
                "must not be a syntax error: " + e.getMessage());
        }
    }

    @Test
    public void describeTypeStageAndAlterSetForms() {
        engine.execute("CREATE TABLE d_t (id INTEGER)");
        assertTrue(engine.executeQuery("DESCRIBE TABLE d_t type=stage").getRowCount() >= 1);
        engine.execute("ALTER TABLE d_t SET DATA_RETENTION_TIME_IN_DAYS=1");
        engine.execute("ALTER TABLE d_t SET STAGE_COPY_OPTIONS = (ON_ERROR=SKIP_FILE SIZE_LIMIT=5 PURGE=TRUE "
            + "MATCH_BY_COLUMN_NAME=CASE_SENSITIVE)");
        engine.execute("ALTER TABLE d_t SET STAGE_FILE_FORMAT = (TYPE=CSV FIELD_DELIMITER='|' NULL_IF=('') "
            + "FIELD_OPTIONALLY_ENCLOSED_BY='\"' TIMESTAMP_FORMAT='TZHTZM YYYY-MM-DD HH24:MI:SS.FF9')");
        engine.execute("ALTER TABLE d_t ADD IF NOT EXISTS c1 INTEGER, IF NOT EXISTS c2 INTEGER");
        assertEquals(3, engine.executeQuery("DESCRIBE TABLE d_t").getRowCount());
    }

    @Test
    public void tablePropertiesBeforeOrAfterTheBodyAreAccepted() {
        engine.execute("CREATE TABLE prop_tail (id INTEGER) CHANGE_TRACKING=TRUE");
        engine.execute("CREATE TABLE prop_head CHANGE_TRACKING=TRUE DATA_RETENTION_TIME_IN_DAYS=1 (id INTEGER)");
        engine.execute("INSERT INTO prop_head VALUES (7)");
        assertEquals(1, count("SELECT COUNT(*) FROM prop_head"));
        // UNSET of unmodeled properties is a no-op, and the column survives (unlike DROP COLUMN).
        engine.execute("ALTER TABLE prop_head UNSET DATA_RETENTION_TIME_IN_DAYS, CHANGE_TRACKING");
        assertEquals(7, count("SELECT id FROM prop_head"));
    }

    @Test
    public void viewPropertiesAndQualifiedRowAccessPolicy() {
        engine.execute("CREATE TABLE vp_src (c INTEGER, region VARCHAR)");
        engine.execute("CREATE VIEW vp_head CHANGE_TRACKING=TRUE (c) AS SELECT c FROM vp_src");
        engine.execute("CREATE VIEW vp_tail (c) CHANGE_TRACKING=TRUE AS SELECT c FROM vp_src");
        assertEquals(0, count("SELECT COUNT(*) FROM vp_tail"));

        // A policy literally named POLICY, attached via its fully qualified db.schema.policy name,
        // with a per-column view comment and COMMENT= before AS (SELECT ...).
        engine.execute("CREATE ROW ACCESS POLICY policy AS (r VARCHAR) RETURNS BOOLEAN -> r = 'EU'");
        engine.execute("CREATE VIEW vp_rap (region COMMENT 'per-column comment') "
            + "WITH ROW ACCESS POLICY test_db.test_schema.policy ON (region) "
            + "COMMENT='view comment' AS (SELECT region FROM vp_src)");
        final View view =
            engine.getCatalog().getDatabase("TEST_DB").getSchema("TEST_SCHEMA").getView("VP_RAP");
        assertEquals("TEST_DB.TEST_SCHEMA.POLICY", view.getRowAccessPolicyName());
    }

    @Test
    public void sequenceWithLeadInCommasAndInlineComment() {
        engine.execute("CREATE SEQUENCE seq_with WITH START=1 INCREMENT=1 ORDER");
        assertEquals(1, count("SELECT seq_with.NEXTVAL"));
        assertEquals(2, count("SELECT seq_with.NEXTVAL"));
        engine.execute("CREATE SEQUENCE seq_commas WITH START=5, INCREMENT=10 ORDER");
        assertEquals(5, count("SELECT seq_commas.NEXTVAL"));
        assertEquals(15, count("SELECT seq_commas.NEXTVAL"));
        engine.execute("CREATE SEQUENCE seq_inline START=3 COMMENT = 'counts things' INCREMENT=2");
        assertEquals(3, count("SELECT seq_inline.NEXTVAL"));
        assertEquals("counts things", engine.getCatalog().getDatabase("TEST_DB").getSchema("TEST_SCHEMA")
            .getSequence("SEQ_INLINE").getComment());
    }

    @Test
    public void dynamicTableColumnListOnEitherSideOfOptions() {
        engine.execute("CREATE TABLE dyn_src (id INTEGER)");
        engine.execute("INSERT INTO dyn_src VALUES (1), (2)");
        engine.execute("CREATE DYNAMIC TABLE dyn_after TARGET_LAG='1 minute' WAREHOUSE=my_wh (id) "
            + "AS SELECT id FROM dyn_src");
        engine.execute("CREATE DYNAMIC TABLE dyn_before (a) TARGET_LAG='20 minutes' WAREHOUSE=my_wh "
            + "AS SELECT id FROM dyn_src");
        assertEquals(2, count("SELECT COUNT(*) FROM dyn_after"));
        assertEquals(2, count("SELECT COUNT(*) FROM dyn_before"));
    }
}
