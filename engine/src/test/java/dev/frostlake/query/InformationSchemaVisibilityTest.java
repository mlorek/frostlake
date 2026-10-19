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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * The INFORMATION_SCHEMA views list what the rights a statement runs with reach, and need no grant of their
 * own. With a table, a view, a function, a procedure, a sequence, a stage, a file format and a schema given
 * away to a role the session does not hold, TABLES, COLUMNS, VIEWS, TABLE_CONSTRAINTS, FUNCTIONS, PROCEDURES,
 * SEQUENCES, STAGES, FILE_FORMATS and SCHEMATA list none of them - ACCOUNTADMIN is not exempt - while an object
 * the session was granted any privilege on is listed, and so is everything once the owning role is granted to
 * the session's. Inside a stored procedure that runs with owner's rights the views list what the OWNER
 * reaches: nothing until it may use the database and the schema, then what it owns and what it was granted.
 * The SHOW commands list every object all along, and a privilege on a view of INFORMATION_SCHEMA cannot be
 * granted. Every answer is live-verified.
 */
public class InformationSchemaVisibilityTest extends MeasuredCellsTestSupport {

    @Test
    public void tablesViewsAndFunctionsOfAnotherRole() {
        try {
            answer(
                "USE ROLE ACCOUNTADMIN");
            answer(
                "USE SECONDARY ROLES NONE");
            answer(
                "DROP ROLE IF EXISTS flq_is_r");
            assertAnswer(
                "Role FLQ_IS_R successfully created.",
                "CREATE ROLE flq_is_r");
            assertAnswer(
                "Table IS_OWN successfully created.",
                "CREATE OR REPLACE TABLE is_own (a INT)");
            assertAnswer(
                "Table IS_KEPT successfully created.",
                "CREATE OR REPLACE TABLE is_kept (b INT)");
            assertAnswer(
                "Table IS_GRANTED successfully created.",
                "CREATE OR REPLACE TABLE is_granted (c INT)");
            assertAnswer(
                "View IS_V successfully created.",
                "CREATE OR REPLACE VIEW is_v AS SELECT b FROM is_kept");
            assertAnswer(
                "Function IS_F successfully created.",
                "CREATE OR REPLACE FUNCTION is_f() RETURNS INT AS '1'");
            assertAnswer(
                "Function IS_F_KEPT successfully created.",
                "CREATE OR REPLACE FUNCTION is_f_kept() RETURNS INT AS '2'");
            assertAnswer(
                "Function IS_SEEN successfully created.",
                "CREATE OR REPLACE PROCEDURE is_seen() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER "
                    + "AS\n$$\nDECLARE names VARCHAR;\nBEGIN\n  SELECT LISTAGG(table_name, ',') WITHIN GROUP (ORDER "
                    + "BY table_name) INTO :names\n    FROM information_schema.tables WHERE table_schema = "
                    + "'TEST_SCHEMA' AND table_name LIKE 'IS%';\n  RETURN names;\nEND\n$$");
            assertAnswer(
                "Function IS_SEEN_F successfully created.",
                "CREATE OR REPLACE PROCEDURE is_seen_f() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER "
                    + "AS\n$$\nDECLARE names VARCHAR;\nBEGIN\n  SELECT LISTAGG(function_name, ',') WITHIN GROUP "
                    + "(ORDER BY function_name) INTO :names\n    FROM information_schema.functions WHERE "
                    + "function_schema = 'TEST_SCHEMA' AND function_name LIKE 'IS%';\n  RETURN COALESCE(names, "
                    + "'none');\nEND\n$$");
            assertAnswer(
                "IS_GRANTED ;; IS_KEPT ;; IS_OWN ;; IS_V",
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND "
                    + "table_name LIKE 'IS%' ORDER BY 1");
            assertAnswer(
                "IS_GRANTED|C ;; IS_KEPT|B ;; IS_OWN|A ;; IS_V|B",
                "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = "
                    + "'TEST_SCHEMA' AND table_name LIKE 'IS%' ORDER BY 1, 2");
            assertAnswer(
                "IS_V",
                "SELECT table_name FROM information_schema.views WHERE table_schema = 'TEST_SCHEMA' AND "
                    + "table_name LIKE 'IS%' ORDER BY 1");
            assertAnswer(
                "IS_F ;; IS_F_KEPT",
                "SELECT function_name FROM information_schema.functions WHERE function_schema = 'TEST_SCHEMA' AND "
                    + "function_name LIKE 'IS%' ORDER BY 1");
            assertAnswer(
                "IS_SEEN ;; IS_SEEN_F",
                "SELECT procedure_name FROM information_schema.procedures WHERE procedure_schema = 'TEST_SCHEMA' "
                    + "AND procedure_name LIKE 'IS%' ORDER BY 1");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'TEST_SCHEMA'");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'IS_OWN'");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON TABLE is_own TO ROLE flq_is_r COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON TABLE is_granted TO ROLE flq_is_r COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON TABLE is_granted TO ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON VIEW is_v TO ROLE flq_is_r COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION is_f() TO ROLE flq_is_r COPY CURRENT GRANTS");
            assertAnswer(
                "IS_GRANTED ;; IS_KEPT",
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND "
                    + "table_name LIKE 'IS%' ORDER BY 1");
            assertAnswer(
                "IS_GRANTED|C ;; IS_KEPT|B",
                "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = "
                    + "'TEST_SCHEMA' AND table_name LIKE 'IS%' ORDER BY 1, 2");
            assertAnswer(
                "",
                "SELECT table_name FROM information_schema.views WHERE table_schema = 'TEST_SCHEMA' AND "
                    + "table_name LIKE 'IS%' ORDER BY 1");
            assertAnswer(
                "IS_F_KEPT",
                "SELECT function_name FROM information_schema.functions WHERE function_schema = 'TEST_SCHEMA' AND "
                    + "function_name LIKE 'IS%' ORDER BY 1");
            assertAnswer(
                "IS_SEEN ;; IS_SEEN_F",
                "SELECT procedure_name FROM information_schema.procedures WHERE procedure_schema = 'TEST_SCHEMA' "
                    + "AND procedure_name LIKE 'IS%' ORDER BY 1");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'TEST_SCHEMA'");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'IS_OWN'");
            answer(
                "SHOW TABLES LIKE 'IS%'");
            assertAnswer(
                "IS_GRANTED ;; IS_KEPT ;; IS_OWN",
                "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'IS_OWN'. Your "
                    + "primary role <ROLE> must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.IS_OWN.",
                "SELECT COUNT(*) FROM is_own");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM is_granted");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON PROCEDURE is_seen() TO ROLE flq_is_r COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON PROCEDURE is_seen_f() TO ROLE flq_is_r COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON PROCEDURE is_seen() TO ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON PROCEDURE is_seen_f() TO ROLE ACCOUNTADMIN");
            assertAnswer(
                "",
                "CALL is_seen()");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON DATABASE test_db TO ROLE flq_is_r");
            assertAnswer(
                "",
                "CALL is_seen()");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON SCHEMA test_schema TO ROLE flq_is_r");
            assertAnswer(
                "IS_GRANTED,IS_OWN,IS_V",
                "CALL is_seen()");
            assertAnswer(
                "IS_F",
                "CALL is_seen_f()");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON TABLE is_kept TO ROLE flq_is_r");
            assertAnswer(
                "IS_GRANTED,IS_KEPT,IS_OWN,IS_V",
                "CALL is_seen()");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON FUNCTION is_f_kept() TO ROLE flq_is_r");
            assertAnswer(
                "IS_F,IS_F_KEPT",
                "CALL is_seen_f()");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on view 'TABLES'.",
                "GRANT SELECT ON VIEW information_schema.tables TO ROLE flq_is_r");
            assertAnswer(
                "",
                "SHOW GRANTS ON VIEW information_schema.tables");
            answer(
                "GRANT ROLE flq_is_r TO ROLE ACCOUNTADMIN");
            assertAnswer(
                "IS_GRANTED ;; IS_KEPT ;; IS_OWN ;; IS_V",
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND "
                    + "table_name LIKE 'IS%' ORDER BY 1");
        } finally {
            answer(
                "USE ROLE ACCOUNTADMIN");
            answer(
                "GRANT ROLE flq_is_r TO ROLE ACCOUNTADMIN");
            answer(
                "DROP ROLE IF EXISTS flq_is_r");
            answer(
                "USE SECONDARY ROLES ALL");
        }
    }

    @Test
    public void everyOtherKindOfObject() {
        try {
            answer(
                "USE ROLE ACCOUNTADMIN");
            answer(
                "USE SECONDARY ROLES NONE");
            answer(
                "DROP ROLE IF EXISTS flq_js_r");
            assertAnswer(
                "Role FLQ_JS_R successfully created.",
                "CREATE ROLE flq_js_r");
            assertAnswer(
                "Schema JS_S2 successfully created.",
                "CREATE OR REPLACE SCHEMA js_s2");
            assertAnswer(
                "Table JS_IN successfully created.",
                "CREATE OR REPLACE TABLE js_s2.js_in (a INT)");
            answer(
                "USE SCHEMA test_schema");
            assertAnswer(
                "Function JS_P successfully created.",
                "CREATE OR REPLACE PROCEDURE js_p() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            assertAnswer(
                "Function JS_P_KEPT successfully created.",
                "CREATE OR REPLACE PROCEDURE js_p_kept() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            assertAnswer(
                "Sequence JS_Q successfully created.",
                "CREATE OR REPLACE SEQUENCE js_q");
            assertAnswer(
                "Sequence JS_Q_KEPT successfully created.",
                "CREATE OR REPLACE SEQUENCE js_q_kept");
            assertAnswer(
                "Stage area JS_ST successfully created.",
                "CREATE OR REPLACE STAGE js_st");
            assertAnswer(
                "Stage area JS_ST_KEPT successfully created.",
                "CREATE OR REPLACE STAGE js_st_kept");
            assertAnswer(
                "File format JS_FF successfully created.",
                "CREATE OR REPLACE FILE FORMAT js_ff TYPE = CSV");
            assertAnswer(
                "File format JS_FF_KEPT successfully created.",
                "CREATE OR REPLACE FILE FORMAT js_ff_kept TYPE = CSV");
            assertAnswer(
                "Table JS_PK successfully created.",
                "CREATE OR REPLACE TABLE js_pk (a INT PRIMARY KEY)");
            assertAnswer(
                "Table JS_PK_KEPT successfully created.",
                "CREATE OR REPLACE TABLE js_pk_kept (a INT PRIMARY KEY)");
            assertAnswer(
                "Function JS_F successfully created.",
                "CREATE OR REPLACE FUNCTION js_f() RETURNS INT AS '1'");
            assertAnswer(
                "Function JS_F_USAGE successfully created.",
                "CREATE OR REPLACE FUNCTION js_f_usage() RETURNS INT AS '2'");
            assertAnswer(
                "Table JS_INS successfully created.",
                "CREATE OR REPLACE TABLE js_ins (a INT)");
            assertAnswer(
                "JS_P ;; JS_P_KEPT",
                "SELECT procedure_name FROM information_schema.procedures WHERE procedure_schema = 'TEST_SCHEMA' "
                    + "AND procedure_name LIKE 'JS%' ORDER BY 1");
            assertAnswer(
                "JS_Q ;; JS_Q_KEPT",
                "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = 'TEST_SCHEMA' AND "
                    + "sequence_name LIKE 'JS%' ORDER BY 1");
            assertAnswer(
                "JS_ST ;; JS_ST_KEPT",
                "SELECT stage_name FROM information_schema.stages WHERE stage_schema = 'TEST_SCHEMA' AND "
                    + "stage_name LIKE 'JS%' ORDER BY 1");
            assertAnswer(
                "JS_FF ;; JS_FF_KEPT",
                "SELECT file_format_name FROM information_schema.file_formats WHERE file_format_schema = "
                    + "'TEST_SCHEMA' AND file_format_name LIKE 'JS%' ORDER BY 1");
            assertAnswer(
                "JS_S2",
                "SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'JS%' ORDER BY 1");
            assertAnswer(
                "JS_S2|JS_IN ;; TEST_SCHEMA|JS_INS ;; TEST_SCHEMA|JS_PK ;; TEST_SCHEMA|JS_PK_KEPT",
                "SELECT table_schema, table_name FROM information_schema.tables WHERE table_name LIKE 'JS%' ORDER "
                    + "BY 1, 2");
            assertAnswer(
                "JS_PK|PRIMARY KEY ;; JS_PK_KEPT|PRIMARY KEY",
                "SELECT table_name, constraint_type FROM information_schema.table_constraints WHERE table_name "
                    + "LIKE 'JS%' ORDER BY 1, 2");
            assertAnswer(
                "JS_F ;; JS_F_USAGE",
                "SELECT function_name FROM information_schema.functions WHERE function_schema = 'TEST_SCHEMA' AND "
                    + "function_name LIKE 'JS%' ORDER BY 1");
            assertAnswer(
                "JS_IN|A ;; JS_INS|A ;; JS_PK|A ;; JS_PK_KEPT|A",
                "SELECT table_name, column_name FROM information_schema.columns WHERE table_name LIKE 'JS%' ORDER "
                    + "BY 1, 2");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON PROCEDURE js_p() TO ROLE flq_js_r COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON SEQUENCE js_q TO ROLE flq_js_r COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON STAGE js_st TO ROLE flq_js_r COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FILE FORMAT js_ff TO ROLE flq_js_r COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON TABLE js_pk TO ROLE flq_js_r COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION js_f() TO ROLE flq_js_r COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION js_f_usage() TO ROLE flq_js_r COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON FUNCTION js_f_usage() TO ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON TABLE js_ins TO ROLE flq_js_r COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT INSERT ON TABLE js_ins TO ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON TABLE js_s2.js_in TO ROLE flq_js_r COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON SCHEMA js_s2 TO ROLE flq_js_r COPY CURRENT GRANTS");
            assertAnswer(
                "JS_P_KEPT",
                "SELECT procedure_name FROM information_schema.procedures WHERE procedure_schema = 'TEST_SCHEMA' "
                    + "AND procedure_name LIKE 'JS%' ORDER BY 1");
            assertAnswer(
                "JS_Q_KEPT",
                "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = 'TEST_SCHEMA' AND "
                    + "sequence_name LIKE 'JS%' ORDER BY 1");
            assertAnswer(
                "JS_ST_KEPT",
                "SELECT stage_name FROM information_schema.stages WHERE stage_schema = 'TEST_SCHEMA' AND "
                    + "stage_name LIKE 'JS%' ORDER BY 1");
            assertAnswer(
                "JS_FF_KEPT",
                "SELECT file_format_name FROM information_schema.file_formats WHERE file_format_schema = "
                    + "'TEST_SCHEMA' AND file_format_name LIKE 'JS%' ORDER BY 1");
            assertAnswer(
                "",
                "SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'JS%' ORDER BY 1");
            assertAnswer(
                "TEST_SCHEMA|JS_INS ;; TEST_SCHEMA|JS_PK_KEPT",
                "SELECT table_schema, table_name FROM information_schema.tables WHERE table_name LIKE 'JS%' ORDER "
                    + "BY 1, 2");
            assertAnswer(
                "JS_PK_KEPT|PRIMARY KEY",
                "SELECT table_name, constraint_type FROM information_schema.table_constraints WHERE table_name "
                    + "LIKE 'JS%' ORDER BY 1, 2");
            assertAnswer(
                "JS_F_USAGE",
                "SELECT function_name FROM information_schema.functions WHERE function_schema = 'TEST_SCHEMA' AND "
                    + "function_name LIKE 'JS%' ORDER BY 1");
            assertAnswer(
                "JS_INS|A ;; JS_PK_KEPT|A",
                "SELECT table_name, column_name FROM information_schema.columns WHERE table_name LIKE 'JS%' ORDER "
                    + "BY 1, 2");
            answer(
                "SHOW SCHEMAS LIKE 'JS%'");
            assertAnswer(
                "JS_S2",
                "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
            answer(
                "SHOW USER FUNCTIONS LIKE 'JS%'");
            assertAnswer(
                "JS_F ;; JS_F_USAGE",
                "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
            answer(
                "SHOW PROCEDURES LIKE 'JS%'");
            assertAnswer(
                "JS_P ;; JS_P_KEPT",
                "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
            answer(
                "SHOW SEQUENCES LIKE 'JS%'");
            assertAnswer(
                "JS_Q ;; JS_Q_KEPT",
                "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
            assertAnswer(
                "",
                "SHOW VIEWS LIKE 'JS%'");
            answer(
                "SHOW STAGES LIKE 'JS%'");
            assertAnswer(
                "JS_ST ;; JS_ST_KEPT",
                "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
            answer(
                "GRANT ROLE flq_js_r TO ROLE ACCOUNTADMIN");
            assertAnswer(
                "JS_P ;; JS_P_KEPT",
                "SELECT procedure_name FROM information_schema.procedures WHERE procedure_schema = 'TEST_SCHEMA' "
                    + "AND procedure_name LIKE 'JS%' ORDER BY 1");
            assertAnswer(
                "JS_Q ;; JS_Q_KEPT",
                "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = 'TEST_SCHEMA' AND "
                    + "sequence_name LIKE 'JS%' ORDER BY 1");
        } finally {
            answer(
                "USE ROLE ACCOUNTADMIN");
            answer(
                "GRANT ROLE flq_js_r TO ROLE ACCOUNTADMIN");
            answer(
                "DROP ROLE IF EXISTS flq_js_r");
            answer(
                "USE SECONDARY ROLES ALL");
            answer(
                "USE SCHEMA test_schema");
        }
    }
}
