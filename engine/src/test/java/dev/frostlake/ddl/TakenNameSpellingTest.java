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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * How a name that is taken is spelled back. A CREATE, a CLONE and a RENAME over a name its schema holds name the
 * object as the statement wrote it: with the quotes the name needs, an inner quote once, and with the path when
 * the statement wrote one. A schema renamed to its own name finds it taken, and a policy is not created over
 * another, which is judged before its signature and its body are.
 */
public class TakenNameSpellingTest extends BaseDatabaseTest {

    private static final String DYNAMIC = " TARGET_LAG = '1 minute' WAREHOUSE = COMPUTE_WH AS SELECT x FROM UPP";
    private static final String ALERT =
        " WAREHOUSE = COMPUTE_WH SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1";
    private static final String MASKING = " AS (v VARCHAR) RETURNS VARCHAR -> v";
    private static final String ROW_ACCESS = " AS (v INT) RETURNS BOOLEAN -> TRUE";

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS COMPUTE_WH");
        engine.execute("CREATE OR REPLACE TABLE UPP (x INT)");
        engine.execute("CREATE OR REPLACE TABLE \"lo\" (x INT)");
    }

    @Override
    protected void teardownTest() {
        try {
            engine.execute("DROP ALERT IF EXISTS test_db.test_schema.\"lal\"");
        } catch (final RuntimeException ignored) {
            // the database went with the test
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

    private static String taken(final String name) {
        return "SQL compilation error:\nObject '" + name + "' already exists.";
    }

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aTakenNameIsSpelledWithTheQuotesItNeeds() {
        engine.execute("CREATE OR REPLACE VIEW \"lv\" AS SELECT 1 AS c");
        engine.execute("CREATE OR REPLACE SEQUENCE \"ls\"");
        engine.execute("CREATE OR REPLACE STAGE \"lst\"");
        engine.execute("CREATE OR REPLACE FILE FORMAT \"lff\" TYPE = CSV");
        engine.execute("CREATE OR REPLACE STREAM \"lstr\" ON TABLE UPP");
        engine.execute("CREATE OR REPLACE TAG \"ltag\"");
        engine.execute("CREATE OR REPLACE TASK \"ltask\" SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE OR REPLACE TABLE \"a b\" (x INT)");
        engine.execute("CREATE OR REPLACE TABLE \"t\"\"q\" (x INT)");
        assertEquals(taken("\"lo\""), refusalOf("CREATE TABLE \"lo\" (x INT)"));
        assertEquals(taken("UPP"), refusalOf("CREATE TABLE upp (x INT)"));
        assertEquals(taken("\"a b\""), refusalOf("CREATE TABLE \"a b\" (x INT)"));
        assertEquals(taken("\"t\"q\""), refusalOf("CREATE TABLE \"t\"\"q\" (x INT)"));
        assertEquals(taken("\"lv\""), refusalOf("CREATE VIEW \"lv\" AS SELECT 1 AS c"));
        assertEquals(taken("\"ls\""), refusalOf("CREATE SEQUENCE \"ls\""));
        assertEquals(taken("\"lst\""), refusalOf("CREATE STAGE \"lst\""));
        assertEquals(taken("\"lff\""), refusalOf("CREATE FILE FORMAT \"lff\" TYPE = CSV"));
        assertEquals(taken("\"lstr\""), refusalOf("CREATE STREAM \"lstr\" ON TABLE UPP"));
        assertEquals(taken("\"ltag\""), refusalOf("CREATE TAG \"ltag\""));
        assertEquals(taken("\"ltask\""), refusalOf("CREATE TASK \"ltask\" SCHEDULE = '60 MINUTE' AS SELECT 1"));
        // A clone, a LIKE and a query name the table they would make the same way.
        assertEquals(taken("\"lo\""), refusalOf("CREATE TABLE \"lo\" CLONE UPP"));
        assertEquals(taken("\"lo\""), refusalOf("CREATE TABLE \"lo\" LIKE UPP"));
        assertEquals(taken("\"lo\""), refusalOf("CREATE TABLE \"lo\" AS SELECT 1 x"));
    }

    @Test
    public void aNameWrittenWithAPathIsSpelledWithIt() {
        engine.execute("CREATE OR REPLACE VIEW \"lv\" AS SELECT 1 AS c");
        engine.execute("CREATE OR REPLACE SEQUENCE \"ls\"");
        engine.execute("CREATE OR REPLACE STAGE \"lst\"");
        engine.execute("CREATE OR REPLACE FILE FORMAT \"lff\" TYPE = CSV");
        engine.execute("CREATE OR REPLACE STREAM \"lstr\" ON TABLE UPP");
        engine.execute("CREATE OR REPLACE TAG \"ltag\"");
        engine.execute("CREATE OR REPLACE TASK \"ltask\" SCHEDULE = '60 MINUTE' AS SELECT 1");
        assertEquals(taken("TEST_DB.TEST_SCHEMA.\"lo\""), refusalOf("CREATE TABLE test_db.test_schema.\"lo\" (x INT)"));
        assertEquals(taken("TEST_DB.TEST_SCHEMA.\"lv\""),
            refusalOf("CREATE VIEW test_db.test_schema.\"lv\" AS SELECT 1 c"));
        assertEquals(taken("TEST_DB.TEST_SCHEMA.\"ls\""), refusalOf("CREATE SEQUENCE test_db.test_schema.\"ls\""));
        assertEquals(taken("TEST_DB.TEST_SCHEMA.\"lst\""), refusalOf("CREATE STAGE test_db.test_schema.\"lst\""));
        assertEquals(taken("TEST_DB.TEST_SCHEMA.\"lff\""),
            refusalOf("CREATE FILE FORMAT test_db.test_schema.\"lff\" TYPE = CSV"));
        assertEquals(taken("TEST_DB.TEST_SCHEMA.\"ltag\""), refusalOf("CREATE TAG test_db.test_schema.\"ltag\""));
        assertEquals(taken("TEST_DB.TEST_SCHEMA.\"lstr\""),
            refusalOf("CREATE STREAM test_db.test_schema.\"lstr\" ON TABLE UPP"));
        assertEquals(taken("TEST_SCHEMA.\"ltask\""),
            refusalOf("CREATE TASK test_schema.\"ltask\" SCHEDULE = '60 MINUTE' AS SELECT 1"));
    }

    @Test
    public void aRenameOntoATakenNameSpellsTheNewNameAsWritten() {
        engine.execute("CREATE OR REPLACE VIEW \"lv\" AS SELECT 1 AS c");
        engine.execute("CREATE OR REPLACE SEQUENCE \"ls\"");
        engine.execute("CREATE OR REPLACE SEQUENCE ls2");
        engine.execute("CREATE OR REPLACE STAGE \"lst\"");
        engine.execute("CREATE OR REPLACE STAGE st2");
        engine.execute("CREATE OR REPLACE FILE FORMAT \"lff\" TYPE = CSV");
        engine.execute("CREATE OR REPLACE FILE FORMAT ff2 TYPE = CSV");
        engine.execute("CREATE OR REPLACE TAG \"ltag\"");
        engine.execute("CREATE OR REPLACE TAG tg2");
        assertEquals(taken("\"lo\""), refusalOf("ALTER TABLE UPP RENAME TO \"lo\""));
        assertEquals(taken("\"lo\""), refusalOf("ALTER TABLE \"lo\" RENAME TO \"lo\""));
        assertEquals(taken("TEST_SCHEMA.\"lo\""), refusalOf("ALTER TABLE UPP RENAME TO test_schema.\"lo\""));
        assertEquals(taken("TEST_DB.TEST_SCHEMA.\"lo\""),
            refusalOf("ALTER TABLE UPP RENAME TO test_db.test_schema.\"lo\""));
        assertEquals(taken("UPP"), refusalOf("ALTER TABLE UPP RENAME TO UPP"));
        assertEquals(taken("TEST_DB.TEST_SCHEMA.UPP"), refusalOf("ALTER TABLE UPP RENAME TO test_db.test_schema.UPP"));
        assertEquals(taken("TEST_SCHEMA.\"lo\""), refusalOf("ALTER VIEW \"lv\" RENAME TO test_schema.\"lo\""));
        assertEquals(taken("TEST_DB.TEST_SCHEMA.\"ls\""),
            refusalOf("ALTER SEQUENCE ls2 RENAME TO test_db.test_schema.\"ls\""));
        assertEquals(taken("\"lst\""), refusalOf("ALTER STAGE \"lst\" RENAME TO \"lst\""));
        assertEquals(taken("TEST_DB.TEST_SCHEMA.\"lst\""),
            refusalOf("ALTER STAGE st2 RENAME TO test_db.test_schema.\"lst\""));
        assertEquals(taken("\"lff\""), refusalOf("ALTER FILE FORMAT \"lff\" RENAME TO \"lff\""));
        assertEquals(taken("TEST_DB.TEST_SCHEMA.\"lff\""),
            refusalOf("ALTER FILE FORMAT ff2 RENAME TO test_db.test_schema.\"lff\""));
        assertEquals(taken("\"ltag\""), refusalOf("ALTER TAG \"ltag\" RENAME TO \"ltag\""));
        assertEquals(taken("TEST_DB.TEST_SCHEMA.\"ltag\""),
            refusalOf("ALTER TAG tg2 RENAME TO test_db.test_schema.\"ltag\""));
    }

    @Test
    public void aSchemaRenamedToItsOwnNameFindsItTaken() {
        engine.execute("CREATE SCHEMA IF NOT EXISTS test_db.\"lsch\"");
        engine.execute("USE SCHEMA test_db.test_schema");
        assertEquals(taken("TEST_DB.\"lsch\""), refusalOf("ALTER SCHEMA test_db.\"lsch\" RENAME TO test_db.\"lsch\""));
        assertEquals(taken("\"lsch\""), refusalOf("ALTER SCHEMA test_db.\"lsch\" RENAME TO \"lsch\""));
        assertEquals(taken("TEST_DB.TEST_SCHEMA"),
            refusalOf("ALTER SCHEMA test_db.test_schema RENAME TO test_db.test_schema"));
        assertEquals(taken("TEST_SCHEMA"), refusalOf("ALTER SCHEMA test_schema RENAME TO test_schema"));
    }

    @Test
    public void aDynamicTableAMaterializedViewAndAnAlertAreNamedTheSameWay() {
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE \"ldt\"" + DYNAMIC);
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE ddt" + DYNAMIC);
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW \"lmv\" AS SELECT x FROM UPP");
        engine.execute("CREATE OR REPLACE ALERT \"lal\"" + ALERT);
        assertEquals(taken("\"ldt\""), refusalOf("CREATE DYNAMIC TABLE \"ldt\"" + DYNAMIC));
        assertEquals(taken("\"ldt\""), refusalOf("ALTER DYNAMIC TABLE ddt RENAME TO \"ldt\""));
        assertEquals(taken("TEST_DB.TEST_SCHEMA.\"ldt\""),
            refusalOf("ALTER DYNAMIC TABLE ddt RENAME TO test_db.test_schema.\"ldt\""));
        assertEquals(taken("\"lmv\""), refusalOf("CREATE MATERIALIZED VIEW \"lmv\" AS SELECT x FROM UPP"));
        assertEquals(taken("\"lal\""), refusalOf("CREATE ALERT \"lal\"" + ALERT));
    }

    @Test
    public void aPolicyIsNotCreatedOverAnother() {
        engine.execute("CREATE OR REPLACE MASKING POLICY mp1" + MASKING);
        assertEquals(taken("MP1"), refusalOf("CREATE MASKING POLICY mp1" + MASKING));
        assertEquals("MP1 already exists, statement succeeded.",
            status("CREATE MASKING POLICY IF NOT EXISTS mp1" + MASKING));
        // The name is judged before the signature and the body are.
        assertEquals(taken("MP1"), refusalOf("CREATE MASKING POLICY mp1 AS (v VARCHAR) RETURNS NUMBER -> v"));
        assertEquals(taken("MP1"), refusalOf("CREATE MASKING POLICY mp1 AS (v VARCHAR) RETURNS VARCHAR -> nosuch_c"));
        assertEquals(taken("TEST_DB.TEST_SCHEMA.MP1"),
            refusalOf("CREATE MASKING POLICY test_db.test_schema.mp1" + MASKING));
        assertEquals(taken("MP1"), refusalOf("ALTER MASKING POLICY mp1 RENAME TO mp1"));
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY r1" + ROW_ACCESS);
        assertEquals(taken("R1"), refusalOf("CREATE ROW ACCESS POLICY r1" + ROW_ACCESS));
        assertEquals("R1 already exists, statement succeeded.",
            status("CREATE ROW ACCESS POLICY IF NOT EXISTS r1" + ROW_ACCESS));
        assertEquals(taken("R1"),
            refusalOf("CREATE ROW ACCESS POLICY r1 AS (v INT) RETURNS BOOLEAN -> nosuch_c = 1"));
        assertEquals(taken("TEST_SCHEMA.R1"), refusalOf("CREATE ROW ACCESS POLICY test_schema.r1" + ROW_ACCESS));
    }
}
