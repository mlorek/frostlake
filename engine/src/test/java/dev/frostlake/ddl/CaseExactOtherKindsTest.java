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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A quoted name is the object of exactly that name for every kind a statement names (live-verified): DESCRIBE,
 * ALTER, COMMENT ON and DROP of a stream, a task, a tag, a masking, row access, projection or aggregation policy,
 * a sequence, a file format or a stage written in another case than the object's refuse it as missing, by its
 * full path, and a SELECT from a stream so written names no relation. A quoted policy name keeps its case: it is
 * another policy than its upper-case namesake to CREATE, OR REPLACE, OR ALTER, RENAME TO and DROP alike.
 * COMMENT ON SECRET is an unsupported feature.
 */
public class CaseExactOtherKindsTest extends MeasuredCellsTestSupport {

    @Test
    public void streamsTasksTagsAndPoliciesByExactName() {
        engine.execute("CREATE OR REPLACE TABLE cs_t (a INT)");
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        final String warehouse = wh == null ? "COMPUTE_WH" : String.valueOf(wh);
        engine.execute("CREATE OR REPLACE STREAM CSTR ON TABLE cs_t");
        engine.execute("CREATE OR REPLACE TASK CTSK WAREHOUSE = " + warehouse + " AS SELECT 1");
        engine.execute("CREATE OR REPLACE FUNCTION CFN() RETURNS INT AS '1'");
        engine.execute("CREATE OR REPLACE PROCEDURE CPR() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute("CREATE OR REPLACE TAG CTG");
        engine.execute("CREATE OR REPLACE MASKING POLICY CMP AS (v INT) RETURNS INT -> v");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY CRP AS (v INT) RETURNS BOOLEAN -> TRUE");
        assertCells(new String[][] {
            {"DESCRIBE STREAM \"cstr\"",
                "REFUSED SQL compilation error: | Stream '\"cstr\"' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on STREAM \"cstr\"."},
            {"ALTER STREAM \"cstr\" SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Stream 'TEST_DB.TEST_SCHEMA.\"cstr\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STREAM "
                + "TEST_DB.TEST_SCHEMA.\"cstr\"."},
            {"COMMENT ON STREAM \"cstr\" IS 'y'",
                "REFUSED SQL compilation error: | Stream 'TEST_DB.TEST_SCHEMA.\"cstr\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STREAM "
                + "TEST_DB.TEST_SCHEMA.\"cstr\"."},
            {"SELECT COUNT(*) FROM \"cstr\"",
                "REFUSED SQL compilation error: | Object '\"cstr\"' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE \"cstr\"."},
            {"DROP STREAM \"cstr\"",
                "REFUSED SQL compilation error: | Stream 'TEST_DB.TEST_SCHEMA.\"cstr\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STREAM "
                + "TEST_DB.TEST_SCHEMA.\"cstr\"."},
            {"SHOW STREAMS LIKE 'CSTR' ->> SELECT COUNT(*) FROM $1", "1"},
            {"DESCRIBE TASK \"ctsk\"",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.\"ctsk\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.\"ctsk\"."},
            {"ALTER TASK \"ctsk\" SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.\"ctsk\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.\"ctsk\"."},
            {"COMMENT ON TASK \"ctsk\" IS 'y'",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.\"ctsk\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.\"ctsk\"."},
            {"DROP TASK \"ctsk\"",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.\"ctsk\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.\"ctsk\"."},
            {"DROP TASK CTSK", "CTSK successfully dropped."},
            {"DESCRIBE FUNCTION \"cfn\"()",
                "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.\"cfn\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.\"cfn\"."},
            {"ALTER FUNCTION \"cfn\"() SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.\"cfn\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.\"cfn\"."},
            {"COMMENT ON FUNCTION \"cfn\"() IS 'y'",
                "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.\"cfn\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.\"cfn\"."},
            {"SELECT \"cfn\"()", "REFUSED SQL compilation error: | Unknown function \"cfn\"."},
            {"DROP FUNCTION \"cfn\"()",
                "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.\"cfn\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.\"cfn\"."},
            {"SHOW USER FUNCTIONS LIKE 'CFN' ->> SELECT COUNT(*) FROM $1", "1"},
            {"DESCRIBE PROCEDURE \"cpr\"()",
                "REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.\"cpr\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PROCEDURE "
                + "TEST_DB.TEST_SCHEMA.\"cpr\"."},
            {"CALL \"cpr\"()", "REFUSED SQL compilation error: | Unknown function \"cpr\"."},
            {"ALTER PROCEDURE \"cpr\"() SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.\"cpr\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PROCEDURE "
                + "TEST_DB.TEST_SCHEMA.\"cpr\"."},
            {"DROP PROCEDURE \"cpr\"()",
                "REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.\"cpr\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PROCEDURE "
                + "TEST_DB.TEST_SCHEMA.\"cpr\"."},
            {"SHOW PROCEDURES LIKE 'CPR' ->> SELECT COUNT(*) FROM $1", "1"},
            {"ALTER TAG \"ctg\" SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Tag 'TEST_DB.TEST_SCHEMA.\"ctg\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TAG "
                + "TEST_DB.TEST_SCHEMA.\"ctg\"."},
            {"COMMENT ON TAG \"ctg\" IS 'y'",
                "REFUSED SQL compilation error: | Tag 'TEST_DB.TEST_SCHEMA.\"ctg\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TAG "
                + "TEST_DB.TEST_SCHEMA.\"ctg\"."},
            {"DROP TAG \"ctg\"",
                "REFUSED SQL compilation error: | Tag 'TEST_DB.TEST_SCHEMA.\"ctg\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TAG "
                + "TEST_DB.TEST_SCHEMA.\"ctg\"."},
            {"SHOW TAGS LIKE 'CTG' ->> SELECT COUNT(*) FROM $1", "1"},
            {"DESCRIBE MASKING POLICY \"cmp\"",
                "REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.\"cmp\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cmp\"."},
            {"ALTER MASKING POLICY \"cmp\" SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.\"cmp\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cmp\"."},
            {"DROP MASKING POLICY \"cmp\"",
                "REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.\"cmp\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cmp\"."},
            {"SHOW MASKING POLICIES LIKE 'CMP' ->> SELECT COUNT(*) FROM $1", "1"},
            {"DESCRIBE ROW ACCESS POLICY \"crp\"",
                "REFUSED SQL compilation error: | Row access policy 'TEST_DB.TEST_SCHEMA.\"crp\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"crp\"."},
            {"ALTER ROW ACCESS POLICY \"crp\" SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Row access policy 'TEST_DB.TEST_SCHEMA.\"crp\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"crp\"."},
            {"DROP ROW ACCESS POLICY \"crp\"",
                "REFUSED SQL compilation error: | Row access policy 'TEST_DB.TEST_SCHEMA.\"crp\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"crp\"."},
            {"SHOW ROW ACCESS POLICIES LIKE 'CRP' ->> SELECT COUNT(*) FROM $1", "1"},
        });
    }

    @Test
    public void otherKindsByExactName() {
        engine.execute("CREATE OR REPLACE SECRET CSEC TYPE = GENERIC_STRING SECRET_STRING = 'x'");
        engine.execute("CREATE OR REPLACE NETWORK RULE CNR MODE = INGRESS TYPE = IPV4 VALUE_LIST = ('10.0.0.1')");
        engine.execute("CREATE OR REPLACE PROJECTION POLICY CPP AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "PROJECTION_CONSTRAINT(ALLOW => true)");
        engine.execute("CREATE OR REPLACE AGGREGATION POLICY CAP AS () RETURNS AGGREGATION_CONSTRAINT -> "
            + "NO_AGGREGATION_CONSTRAINT()");
        engine.execute("CREATE OR REPLACE TABLE UNT (a INT)");
        engine.execute("DROP TABLE UNT");
        engine.execute("CREATE OR REPLACE TABLE RNA (a INT)");
        engine.execute("CREATE OR REPLACE TABLE RNB (a INT)");
        engine.execute("CREATE OR REPLACE SEQUENCE CSQ");
        engine.execute("CREATE OR REPLACE FILE FORMAT CFF TYPE = CSV");
        engine.execute("CREATE OR REPLACE STAGE CSTG");
        assertCells(new String[][] {
            {"DESCRIBE SECRET \"csec\"",
                "REFUSED SQL compilation error: | Secret 'TEST_DB.TEST_SCHEMA.\"csec\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SECRET "
                + "TEST_DB.TEST_SCHEMA.\"csec\"."},
            {"ALTER SECRET \"csec\" SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Secret 'TEST_DB.TEST_SCHEMA.\"csec\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SECRET "
                + "TEST_DB.TEST_SCHEMA.\"csec\"."},
            {"COMMENT ON SECRET \"csec\" IS 'y'", "REFUSED Unsupported feature 'SECRET'."},
            {"DROP SECRET \"csec\"",
                "REFUSED SQL compilation error: | Secret 'TEST_DB.TEST_SCHEMA.\"csec\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SECRET "
                + "TEST_DB.TEST_SCHEMA.\"csec\"."},
            {"SHOW SECRETS LIKE 'CSEC' ->> SELECT COUNT(*) FROM $1", "1"},
            {"ALTER NETWORK RULE \"cnr\" SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Network rule 'TEST_DB.TEST_SCHEMA.\"cnr\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have MONITOR granted on ACCOUNT <ACCOUNT>."},
            {"DROP NETWORK RULE \"cnr\"",
                "REFUSED SQL compilation error: | Network rule 'TEST_DB.TEST_SCHEMA.\"cnr\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have MONITOR granted on ACCOUNT <ACCOUNT>."},
            {"SHOW NETWORK RULES LIKE 'CNR' ->> SELECT COUNT(*) FROM $1", "1"},
            {"DESCRIBE PROJECTION POLICY \"cpp\"",
                "REFUSED SQL compilation error: | Projection policy 'TEST_DB.TEST_SCHEMA.\"cpp\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cpp\"."},
            {"ALTER PROJECTION POLICY \"cpp\" SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Projection policy 'TEST_DB.TEST_SCHEMA.\"cpp\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cpp\"."},
            {"DROP PROJECTION POLICY \"cpp\"",
                "REFUSED SQL compilation error: | Projection policy 'TEST_DB.TEST_SCHEMA.\"cpp\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cpp\"."},
            {"SHOW PROJECTION POLICIES LIKE 'CPP' ->> SELECT COUNT(*) FROM $1", "1"},
            {"DESCRIBE AGGREGATION POLICY \"cap\"",
                "REFUSED SQL compilation error: | Aggregation policy 'TEST_DB.TEST_SCHEMA.\"cap\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cap\"."},
            {"ALTER AGGREGATION POLICY \"cap\" SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Aggregation policy 'TEST_DB.TEST_SCHEMA.\"cap\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cap\"."},
            {"DROP AGGREGATION POLICY \"cap\"",
                "REFUSED SQL compilation error: | Aggregation policy 'TEST_DB.TEST_SCHEMA.\"cap\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cap\"."},
            {"SHOW AGGREGATION POLICIES LIKE 'CAP' ->> SELECT COUNT(*) FROM $1", "1"},
            {"UNDROP TABLE \"unt\"", "REFUSED Table unt did not exist or was purged."},
            {"SHOW TABLES LIKE 'UNT' ->> SELECT COUNT(*) FROM $1", "0"},
            {"UNDROP TABLE UNT", "Table UNT successfully restored."},
            {"ALTER TABLE RNA RENAME TO \"rnb\"", "Statement executed successfully."},
            {"SHOW TABLES LIKE 'rnb' ->> SELECT COUNT(*) FROM $1", "2"},
            {"ALTER TABLE \"rna\" RENAME TO rnc",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.\"rna\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.\"rna\"."},
            {"COMMENT ON SEQUENCE \"csq\" IS 'y'",
                "REFUSED SQL compilation error: | Sequence 'TEST_DB.TEST_SCHEMA.\"csq\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SEQUENCE "
                + "TEST_DB.TEST_SCHEMA.\"csq\"."},
            {"COMMENT ON FILE FORMAT \"cff\" IS 'y'",
                "REFUSED SQL compilation error: | File format 'TEST_DB.TEST_SCHEMA.\"cff\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT "
                + "TEST_DB.TEST_SCHEMA.\"cff\"."},
            {"COMMENT ON STAGE \"cstg\" IS 'y'",
                "REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.\"cstg\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.\"cstg\"."},
            {"DESCRIBE FILE FORMAT \"cff\"",
                "REFUSED SQL compilation error: | File format 'TEST_DB.TEST_SCHEMA.\"cff\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT "
                + "TEST_DB.TEST_SCHEMA.\"cff\"."},
            {"DESCRIBE STAGE \"cstg\" ->> SELECT COUNT(*) FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL compilation "
                + "error: | Stage 'TEST_DB.TEST_SCHEMA.\"cstg\"' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on STAGE TEST_DB.TEST_SCHEMA.\"cstg\"."},
            {"ALTER FILE FORMAT \"cff\" SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | File format 'TEST_DB.TEST_SCHEMA.\"cff\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT "
                + "TEST_DB.TEST_SCHEMA.\"cff\"."},
            {"ALTER STAGE \"cstg\" SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.\"cstg\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.\"cstg\"."},
            {"SHOW SEQUENCES LIKE 'csq' ->> SELECT COUNT(*) FROM $1", "1"},
        });
    }

    @Test
    public void policiesCommentedOnByExactName() {
        engine.execute("CREATE OR REPLACE MASKING POLICY CMP3 AS (v INT) RETURNS INT -> v");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY CRP3 AS (v INT) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE OR REPLACE JOIN POLICY CJP3 AS () RETURNS JOIN_CONSTRAINT -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        engine.execute("CREATE OR REPLACE CONTACT CCT3 EMAIL_DISTRIBUTION_LIST = 'a@example.com'");
        assertCells(new String[][] {
            {"COMMENT ON MASKING POLICY \"cmp3\" IS 'y'",
                "REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.\"cmp3\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cmp3\"."},
            {"COMMENT ON ROW ACCESS POLICY \"crp3\" IS 'y'",
                "REFUSED SQL compilation error: | Row access policy 'TEST_DB.TEST_SCHEMA.\"crp3\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"crp3\"."},
            {"COMMENT ON MASKING POLICY CMP3 IS 'y'", "Statement executed successfully."},
            {"COMMENT ON ROW ACCESS POLICY CRP3 IS 'y'", "Statement executed successfully."},
            {"COMMENT IF EXISTS ON MASKING POLICY \"cmp3\" IS 'y'", "Statement executed successfully."},
            {"COMMENT ON MASKING POLICY test_schema.\"cmp3\" IS 'y'",
                "REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.\"cmp3\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cmp3\"."},
            {"ALTER MASKING POLICY \"cmp3\" RENAME TO cmp4",
                "REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.\"cmp3\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cmp3\"."},
            {"ALTER STAGE IF EXISTS \"nosuch_stg\" SET COMMENT = 'x'", "Statement executed successfully."},
            {"ALTER FILE FORMAT IF EXISTS \"nosuch_ff\" SET COMMENT = 'x'", "Statement executed successfully."},
            {"COMMENT IF EXISTS ON SECRET nosuch_sec IS 'y'", "REFUSED Unsupported feature 'SECRET'."},
        });
    }

    @Test
    public void quotedPolicyNamesKeepTheirCase() {
        engine.execute("CREATE OR REPLACE MASKING POLICY QMP AS (v INT) RETURNS INT -> v");
        engine.execute("CREATE OR REPLACE PROJECTION POLICY QPP AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "PROJECTION_CONSTRAINT(ALLOW => true)");
        engine.execute("CREATE OR REPLACE AGGREGATION POLICY QAP AS () RETURNS AGGREGATION_CONSTRAINT -> "
            + "NO_AGGREGATION_CONSTRAINT()");
        assertCells(new String[][] {
            {"CREATE MASKING POLICY \"qmp\" AS (v INT) RETURNS INT -> 0", "Masking policy qmp successfully created."},
            {"SHOW MASKING POLICIES LIKE 'qmp' ->> SELECT \"name\" FROM $1 ORDER BY 1", "QMP ;; qmp"},
            {"DESCRIBE MASKING POLICY \"qmp\"", "qmp|(V NUMBER)|NUMBER(38,0)|0"},
            {"DESCRIBE MASKING POLICY QMP", "QMP|(V NUMBER)|NUMBER(38,0)|v"},
            {"CREATE OR REPLACE PROJECTION POLICY \"qpp\" AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => false)",
                "Projection policy 'qpp' is successfully created."},
            {"SHOW PROJECTION POLICIES LIKE 'qpp' ->> SELECT \"name\" FROM $1 ORDER BY 1", "QPP ;; qpp"},
            {"DESCRIBE PROJECTION POLICY \"qpp\"",
                "qpp|()|PROJECTION_CONSTRAINT|PROJECTION_CONSTRAINT(ALLOW => false)"},
            {"DESCRIBE PROJECTION POLICY QPP", "QPP|()|PROJECTION_CONSTRAINT|PROJECTION_CONSTRAINT(ALLOW => true)"},
            {"CREATE AGGREGATION POLICY \"qap\" AS () RETURNS AGGREGATION_CONSTRAINT -> "
                + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 2)",
                "Aggregation policy 'qap' is successfully created."},
            {"SHOW AGGREGATION POLICIES LIKE 'qap' ->> SELECT \"name\" FROM $1 ORDER BY 1", "QAP ;; qap"},
            {"CREATE OR ALTER MASKING POLICY \"qmp\" AS (v INT) RETURNS INT -> 1", "Statement executed successfully."},
            {"DESCRIBE MASKING POLICY QMP", "QMP|(V NUMBER)|NUMBER(38,0)|v"},
            {"DESCRIBE MASKING POLICY \"qmp\"", "qmp|(V NUMBER)|NUMBER(38,0)|1"},
            {"ALTER PROJECTION POLICY \"qpp\" RENAME TO \"qpp4\"", "Statement executed successfully."},
            {"SHOW PROJECTION POLICIES LIKE 'qpp%' ->> SELECT \"name\" FROM $1 ORDER BY 1", "QPP ;; qpp4"},
            {"ALTER PROJECTION POLICY QPP RENAME TO \"qpp4\"",
                "REFUSED SQL compilation error: | Object '\"qpp4\"' already exists."},
            {"ALTER PROJECTION POLICY QPP RENAME TO QPP4", "Statement executed successfully."},
            {"SHOW PROJECTION POLICIES LIKE 'qpp%' ->> SELECT \"name\" FROM $1 ORDER BY 1", "QPP4 ;; qpp4"},
            {"CREATE ROW ACCESS POLICY \"qmp\" AS (v INT) RETURNS BOOLEAN -> TRUE",
                "REFUSED SQL compilation error: | Object '\"qmp\"' already exists."},
            {"CREATE ROW ACCESS POLICY qmp AS (v INT) RETURNS BOOLEAN -> TRUE",
                "REFUSED SQL compilation error: | Object 'QMP' already exists."},
            {"DROP MASKING POLICY \"qmp\"", "qmp successfully dropped."},
            {"SHOW MASKING POLICIES LIKE 'qmp' ->> SELECT \"name\" FROM $1 ORDER BY 1", "QMP"},
            {"DROP PROJECTION POLICY \"qpp4\"", "qpp4 successfully dropped."},
            {"DROP AGGREGATION POLICY \"qap\"", "qap successfully dropped."},
            {"SHOW AGGREGATION POLICIES LIKE 'qap' ->> SELECT \"name\" FROM $1 ORDER BY 1", "QAP"},
        });
    }
}
