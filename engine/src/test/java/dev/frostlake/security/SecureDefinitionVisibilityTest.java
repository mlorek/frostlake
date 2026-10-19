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
package dev.frostlake.security;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * What a role that does not own a function, a procedure or a view is shown of its definition. Of a SECURE one,
 * nothing: GET_DDL is {@code Object does not exist, or operation cannot be performed.}, a schema's DDL leaves
 * the object out, DESCRIBE's body row is NULL and SHOW VIEWS' text is empty - while the object can still be
 * called and read. Of a plain one, its DDL and its DESCRIBE, except that a procedure's body is its owner's:
 * the DDL carries {@code AS ''} and DESCRIBE an empty body. INFORMATION_SCHEMA's FUNCTION_DEFINITION,
 * PROCEDURE_DEFINITION and VIEW_DEFINITION are NULL for any object another role owns, secure or not. A role
 * that inherits the owner is the owner: everything is shown to it. The session stays ACCOUNTADMIN throughout,
 * and the scratch roles are dropped whatever the assertions make of the statements between. Every answer is
 * live-verified.
 */
public class SecureDefinitionVisibilityTest extends MeasuredCellsTestSupport {

    @Test
    public void aNonOwnerIsShownNoSecureDefinition() {
        try {
            engine.execute("USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES NONE");
            assertAnswer(
                "Drop statement executed successfully (FLQ_M6J_S already dropped).",
                "DROP ROLE IF EXISTS flq_m6j_s");
            assertAnswer(
                "Role FLQ_M6J_S successfully created.",
                "CREATE ROLE flq_m6j_s");
            assertAnswer(
                "Table SEC_T successfully created.",
                "CREATE OR REPLACE TABLE sec_t (v NUMBER)");
            assertAnswer(
                "Function SEC_F successfully created.",
                "CREATE OR REPLACE SECURE FUNCTION sec_f(x NUMBER) RETURNS NUMBER AS 'x + 1'");
            assertAnswer(
                "Function SEC_P successfully created.",
                "CREATE OR REPLACE SECURE PROCEDURE sec_p() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
                    + "END; $$");
            assertAnswer(
                "View SEC_V successfully created.",
                "CREATE OR REPLACE SECURE VIEW sec_v AS SELECT v FROM sec_t WHERE v > 1");
            assertAnswer(
                "Function PLAIN_F successfully created.",
                "CREATE OR REPLACE FUNCTION plain_f(x NUMBER) RETURNS NUMBER AS 'x + 2'");
            assertAnswer(
                "Function PLAIN_P successfully created.",
                "CREATE OR REPLACE PROCEDURE plain_p() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 2; END; $$");
            assertAnswer(
                "View PLAIN_V successfully created.",
                "CREATE OR REPLACE VIEW plain_v AS SELECT v FROM sec_t WHERE v > 2");
            assertAnswer(
                "CREATE OR REPLACE SECURE FUNCTION \"SEC_F\"(\"X\" NUMBER(38,0)) | RETURNS NUMBER(38,0) | "
                    + "LANGUAGE SQL | AS 'x + 1';",
                "SELECT GET_DDL('function', 'sec_f(NUMBER)')");
            assertAnswer(
                "CREATE OR REPLACE SECURE PROCEDURE \"SEC_P\"() | RETURNS NUMBER(38,0) | LANGUAGE SQL | EXECUTE "
                    + "AS OWNER | AS ' BEGIN RETURN 1; END; ';",
                "SELECT GET_DDL('procedure', 'sec_p()')");
            assertAnswer(
                "create or replace secure view SEC_V( | \tV | ) as SELECT v FROM sec_t WHERE v > 1;",
                "SELECT GET_DDL('view', 'sec_v')");
            assertAnswer(
                "signature|(X NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|x + 1",
                "DESCRIBE FUNCTION sec_f(NUMBER)");
            assertAnswer(
                "signature|() ;; returns|NUMBER(38,0) ;; language|SQL ;; execute as|OWNER ;; body| BEGIN RETURN "
                    + "1; END; ",
                "DESCRIBE PROCEDURE sec_p()");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON DATABASE test_db TO ROLE flq_m6j_s");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON SCHEMA test_db.test_schema TO ROLE flq_m6j_s");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON TABLE sec_t TO ROLE flq_m6j_s COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION sec_f(NUMBER) TO ROLE flq_m6j_s COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON PROCEDURE sec_p() TO ROLE flq_m6j_s COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON VIEW sec_v TO ROLE flq_m6j_s COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION plain_f(NUMBER) TO ROLE flq_m6j_s COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON PROCEDURE plain_p() TO ROLE flq_m6j_s COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON VIEW plain_v TO ROLE flq_m6j_s COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON FUNCTION sec_f(NUMBER) TO ROLE PUBLIC");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON PROCEDURE sec_p() TO ROLE PUBLIC");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON VIEW sec_v TO ROLE PUBLIC");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON FUNCTION plain_f(NUMBER) TO ROLE PUBLIC");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON PROCEDURE plain_p() TO ROLE PUBLIC");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON VIEW plain_v TO ROLE PUBLIC");
            assertAnswer(
                "2|3",
                "SELECT sec_f(1), plain_f(1)");
            assertAnswer(
                "1",
                "CALL sec_p()");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM sec_v");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SELECT GET_DDL('function', 'sec_f(NUMBER)')");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SELECT GET_DDL('procedure', 'sec_p()')");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SELECT GET_DDL('view', 'sec_v')");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"PLAIN_F\"(\"X\" NUMBER(38,0)) | RETURNS NUMBER(38,0) | LANGUAGE SQL "
                    + "| AS 'x + 2';",
                "SELECT GET_DDL('function', 'plain_f(NUMBER)')");
            assertAnswer(
                "CREATE OR REPLACE PROCEDURE \"PLAIN_P\"() | RETURNS NUMBER(38,0) | LANGUAGE SQL | EXECUTE AS "
                    + "OWNER | AS '';",
                "SELECT GET_DDL('procedure', 'plain_p()')");
            assertAnswer(
                "create or replace view PLAIN_V( | \tV | ) as SELECT v FROM sec_t WHERE v > 2;",
                "SELECT GET_DDL('view', 'plain_v')");
            assertAnswer(
                "signature|(X NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|null",
                "DESCRIBE FUNCTION sec_f(NUMBER)");
            assertAnswer(
                "signature|() ;; returns|NUMBER(38,0) ;; language|SQL ;; execute as|OWNER ;; body|null",
                "DESCRIBE PROCEDURE sec_p()");
            assertAnswer(
                "signature|(X NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|x + 2",
                "DESCRIBE FUNCTION plain_f(NUMBER)");
            assertAnswer(
                "signature|() ;; returns|NUMBER(38,0) ;; language|SQL ;; execute as|OWNER ;; body|",
                "DESCRIBE PROCEDURE plain_p()");
            assertShape(
                "name:VARCHAR(16777216), type:VARCHAR(16777216), kind:VARCHAR(16777216), null?:VARCHAR(16777216), "
                    + "default:VARCHAR(16777216), primary key:VARCHAR(16777216), unique key:VARCHAR(16777216), "
                    + "check:VARCHAR(16777216), expression:VARCHAR(16777216), comment:VARCHAR(16777216), policy "
                    + "name:VARCHAR(16777216), privacy domain:OBJECT, write default:VARCHAR(16777216)",
                "DESCRIBE VIEW sec_v");
            assertAnswer(
                "PLAIN_F|null|NO ;; SEC_F|null|YES",
                "SELECT function_name, NVL(function_definition, 'null'), is_secure FROM "
                    + "information_schema.functions WHERE function_name IN ('SEC_F', 'PLAIN_F') ORDER BY 1");
            assertAnswer(
                "PLAIN_P|null ;; SEC_P|null",
                "SELECT procedure_name, NVL(procedure_definition, 'null') FROM information_schema.procedures "
                    + "WHERE procedure_name IN ('SEC_P', 'PLAIN_P') ORDER BY 1");
            assertAnswer(
                "PLAIN_V|null|NO ;; SEC_V|null|YES",
                "SELECT table_name, NVL(view_definition, 'null'), is_secure FROM information_schema.views WHERE "
                    + "table_name IN ('SEC_V', 'PLAIN_V') ORDER BY 1");
            answer(
                "SHOW VIEWS LIKE 'SEC_V' IN SCHEMA test_db.test_schema");
            assertAnswer(
                "SEC_V||true",
                "SELECT \"name\", NVL(\"text\", 'null'), \"is_secure\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            answer(
                "SHOW VIEWS LIKE 'PLAIN_V' IN SCHEMA test_db.test_schema");
            assertAnswer(
                "PLAIN_V|CREATE OR REPLACE VIEW plain_v AS SELECT v FROM sec_t WHERE v > 2|false",
                "SELECT \"name\", NVL(\"text\", 'null'), \"is_secure\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            answer(
                "SHOW USER FUNCTIONS LIKE 'SEC_F' IN SCHEMA test_db.test_schema");
            assertAnswer(
                "SEC_F|Y|SEC_F(NUMBER) RETURN NUMBER",
                "SELECT \"name\", \"is_secure\", \"arguments\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        } finally {
            // The scratch roles go, and the session is as it was, whatever the assertions made of it.
            answer("GRANT ROLE flq_m6j_s TO ROLE ACCOUNTADMIN");
            answer("DROP ROLE IF EXISTS flq_m6j_s");
            answer("USE SECONDARY ROLES ALL");
        }
    }

    @Test
    public void schemaDdlAndARoleThatInheritsTheOwner() {
        try {
            engine.execute("USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES NONE");
            assertAnswer(
                "Drop statement executed successfully (FLQ_M6J_C already dropped).",
                "DROP ROLE IF EXISTS flq_m6j_c");
            assertAnswer(
                "Role FLQ_M6J_C successfully created.",
                "CREATE ROLE flq_m6j_c");
            assertAnswer(
                "Schema SEC_S successfully created.",
                "CREATE OR REPLACE SCHEMA sec_s");
            assertAnswer(
                "Table T successfully created.",
                "CREATE OR REPLACE TABLE sec_s.t (v NUMBER)");
            assertAnswer(
                "Function SF successfully created.",
                "CREATE OR REPLACE SECURE FUNCTION sec_s.sf(x NUMBER) RETURNS NUMBER AS 'x + 1'");
            assertAnswer(
                "View SV successfully created.",
                "CREATE OR REPLACE SECURE VIEW sec_s.sv AS SELECT v FROM sec_s.t WHERE v > 1");
            assertAnswer(
                "Function SP successfully created.",
                "CREATE OR REPLACE SECURE PROCEDURE sec_s.sp() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
                    + "END; $$");
            assertAnswer(
                "Function PF successfully created.",
                "CREATE OR REPLACE FUNCTION sec_s.pf(x NUMBER) RETURNS NUMBER AS 'x + 2'");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON DATABASE test_db TO ROLE flq_m6j_c");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON SCHEMA sec_s TO ROLE flq_m6j_c");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON TABLE sec_s.t TO ROLE flq_m6j_c COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION sec_s.sf(NUMBER) TO ROLE flq_m6j_c COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON VIEW sec_s.sv TO ROLE flq_m6j_c COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON PROCEDURE sec_s.sp() TO ROLE flq_m6j_c COPY CURRENT GRANTS");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON FUNCTION sec_s.sf(NUMBER) TO ROLE PUBLIC");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON VIEW sec_s.sv TO ROLE PUBLIC");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON PROCEDURE sec_s.sp() TO ROLE PUBLIC");
            assertAnswer(
                "create or replace schema SEC_S; |  | create or replace TABLE T ( | \tV NUMBER(38,0) | ); | "
                    + "CREATE OR REPLACE FUNCTION \"PF\"(\"X\" NUMBER(38,0)) | RETURNS NUMBER(38,0) | LANGUAGE SQL | "
                    + "AS 'x + 2';",
                "SELECT GET_DDL('schema', 'test_db.sec_s')");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'sec_s.nosuch_f(NUMBER)' does not exist or not "
                    + "authorized.",
                "SELECT GET_DDL('function', 'sec_s.nosuch_f(NUMBER)')");
            assertAnswer(
                "REFUSED SQL compilation error: | View 'TEST_DB.SEC_S.NOSUCH_V' does not exist or not authorized. "
                    + "Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.SEC_S.NOSUCH_V.",
                "SELECT GET_DDL('view', 'sec_s.nosuch_v')");
            assertAnswer(
                "V|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE VIEW sec_s.sv");
            answer(
                "SHOW PROCEDURES LIKE 'SP' IN SCHEMA test_db.sec_s");
            assertAnswer(
                "SP|Y|SP() RETURN NUMBER",
                "SELECT \"name\", \"is_secure\", \"arguments\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ROLE flq_m6j_c TO ROLE ACCOUNTADMIN");
            assertAnswer(
                "CREATE OR REPLACE SECURE FUNCTION \"SF\"(\"X\" NUMBER(38,0)) | RETURNS NUMBER(38,0) | LANGUAGE "
                    + "SQL | AS 'x + 1';",
                "SELECT GET_DDL('function', 'sec_s.sf(NUMBER)')");
            assertAnswer(
                "create or replace secure view SV( | \tV | ) as SELECT v FROM sec_s.t WHERE v > 1;",
                "SELECT GET_DDL('view', 'sec_s.sv')");
            assertAnswer(
                "CREATE OR REPLACE SECURE PROCEDURE \"SP\"() | RETURNS NUMBER(38,0) | LANGUAGE SQL | EXECUTE AS "
                    + "OWNER | AS ' BEGIN RETURN 1; END; ';",
                "SELECT GET_DDL('procedure', 'sec_s.sp()')");
            assertAnswer(
                "signature|(X NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|x + 1",
                "DESCRIBE FUNCTION sec_s.sf(NUMBER)");
            assertAnswer(
                "x + 1",
                "SELECT NVL(function_definition, 'null') FROM information_schema.functions WHERE function_name = "
                    + "'SF'");
            assertAnswer(
                "CREATE OR REPLACE SECURE VIEW sec_s.sv AS SELECT v FROM sec_s.t WHERE v > 1",
                "SELECT NVL(view_definition, 'null') FROM information_schema.views WHERE table_name = 'SV'");
        } finally {
            // The scratch roles go, and the session is as it was, whatever the assertions made of it.
            answer("GRANT ROLE flq_m6j_c TO ROLE ACCOUNTADMIN");
            answer("DROP ROLE IF EXISTS flq_m6j_c");
            answer("USE SECONDARY ROLES ALL");
        }
    }
}
