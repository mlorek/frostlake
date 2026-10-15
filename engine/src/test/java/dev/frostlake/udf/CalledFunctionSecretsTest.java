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
package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * When a function's secrets are judged. A function that names secrets and no external access integration is
 * created, and cannot be called: the call is refused while the statement compiles - over an empty table, in an
 * EXPLAIN, in a DELETE's predicate, in a block - and a view, a function, a procedure or a table whose
 * definition calls it is not created. The refusal is the function's by name, ahead of an invalid identifier
 * and of the call's argument count, and behind a table that is not there. A secret that is gone is told first:
 * {@code Object 'NF_SEC' does not exist or not authorized.} Every answer is live-verified.
 */
public class CalledFunctionSecretsTest extends MeasuredCellsTestSupport {

    @Test
    public void aCallIsRefusedBeforeARowIsRead() {
        answer(
            "CREATE OR REPLACE SECRET nf_sec TYPE = GENERIC_STRING SECRET_STRING = 'v'");
        answer(
            "CREATE OR REPLACE TABLE nf_t (a NUMBER)");
        assertAnswer(
            "Function NF_SJ successfully created.",
            "CREATE OR REPLACE FUNCTION nf_sj(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.h' SECRETS = "
                + "('k' = nf_sec) AS $$ class H { public static long h(long x) { return x; } } $$");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "SELECT nf_sj(a) FROM nf_t WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "SELECT nf_sj(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "SELECT COUNT(*) FROM nf_t WHERE nf_sj(a) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "EXPLAIN SELECT nf_sj(a) FROM nf_t");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "CREATE OR REPLACE VIEW nf_v AS SELECT nf_sj(a) b FROM nf_t");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NF_V' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE NF_V.",
            "SELECT * FROM nf_v");
        assertAnswer(
            "Function NF_SQ successfully created.",
            "CREATE OR REPLACE FUNCTION nf_sq(x NUMBER) RETURNS NUMBER SECRETS = ('k' = nf_sec) AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "SELECT nf_sq(a) FROM nf_t WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "SELECT nf_sq(1)");
        assertAnswer(
            "Function NF_SP successfully created.",
            "CREATE OR REPLACE FUNCTION nf_sp(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "HANDLER = 'h' SECRETS = ('k' = nf_sec) AS $$\ndef h(x):\n    return x\n$$");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "SELECT nf_sp(a) FROM nf_t WHERE 1 = 0");
        assertAnswer(
            "Function NF_SJS successfully created.",
            "CREATE OR REPLACE FUNCTION nf_sjs(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT SECRETS = ('k' = "
                + "nf_sec) AS 'return X;'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "SELECT nf_sjs(a) FROM nf_t WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "SELECT nf_sjs(1)");
        assertAnswer(
            "Function NF_SPR successfully created.",
            "CREATE OR REPLACE PROCEDURE nf_spr() RETURNS FLOAT LANGUAGE JAVASCRIPT SECRETS = ('k' = nf_sec) AS "
                + "'return 1;'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "CALL nf_spr()");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "CREATE OR REPLACE FUNCTION nf_sw(x NUMBER) RETURNS NUMBER AS 'nf_sj(x)'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NF_SW.",
            "SELECT nf_sw(a) FROM nf_t WHERE 1 = 0");
        assertAnswer(
            "NF_SEC successfully dropped.",
            "DROP SECRET nf_sec");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NF_SEC' does not exist or not authorized.",
            "SELECT nf_sj(a) FROM nf_t WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NF_SEC' does not exist or not authorized.",
            "SELECT nf_sj(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NF_SEC' does not exist or not authorized.",
            "SELECT nf_sq(a) FROM nf_t WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NF_SEC' does not exist or not authorized.",
            "SELECT nf_sq(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NF_SEC' does not exist or not authorized.",
            "SELECT nf_sjs(a) FROM nf_t WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NF_SEC' does not exist or not authorized.",
            "SELECT nf_sp(a) FROM nf_t WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NF_SEC' does not exist or not authorized.",
            "CALL nf_spr()");
        assertAnswer(
            "signature|(X NUMBER) ;; returns|NUMBER(38,0) ;; language|JAVA ;; null handling|CALLED ON NULL INPUT "
                + ";; volatility|VOLATILE ;; body| class H { public static long h(long x) { return x; } }  ;; "
                + "imports|[] ;; handler|H.h ;; target_path|null ;; runtime_version|null ;; packages|[]",
            "DESCRIBE FUNCTION nf_sj(NUMBER)");
        assertAnswer(
            "{\"k\":\"NF_SEC\"}",
            "SHOW USER FUNCTIONS LIKE 'NF_SJ' ->> SELECT \"secrets\" FROM $1");
    }

    @Test
    public void everyStatementThatCompilesACall() {
        answer(
            "CREATE OR REPLACE SECRET nf_sec TYPE = GENERIC_STRING SECRET_STRING = 'v'");
        answer(
            "CREATE OR REPLACE TABLE nf_t (a NUMBER)");
        assertAnswer(
            "Function NF_SE successfully created.",
            "CREATE OR REPLACE FUNCTION nf_se(x NUMBER) RETURNS NUMBER SECRETS = () AS 'x'");
        assertAnswer(
            "",
            "SELECT nf_se(a) FROM nf_t WHERE 1 = 0");
        assertAnswer(
            "Function NF_SQ successfully created.",
            "CREATE OR REPLACE FUNCTION nf_sq(x NUMBER) RETURNS NUMBER SECRETS = ('k' = nf_sec) AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "SELECT nf_sq(a) FROM nf_t WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "ALTER FUNCTION nf_sq(NUMBER) UNSET SECRETS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "SELECT nf_sq(a) FROM nf_t WHERE 1 = 0");
        assertAnswer(
            "Function NF_S2 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_s2(x NUMBER) RETURNS NUMBER SECRETS = ('k' = nf_sec) AS 'x'");
        assertAnswer(
            "Function NF_S3 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_s3(x NUMBER) RETURNS NUMBER AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "SELECT nf_s3(a), nf_s2(a) FROM nf_t WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "SELECT nf_s2(nosuch) FROM nf_t");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "SELECT nf_s2(a, 1) FROM nf_t");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "SELECT nf_s2('x') FROM nf_t");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NF_NOSUCH' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NF_NOSUCH.",
            "SELECT nf_s2(a) FROM nf_nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "SELECT 1 FROM nf_t WHERE nf_s2(a) = 1 AND nosuch = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "DELETE FROM nf_t WHERE nf_s2(a) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "INSERT INTO nf_t SELECT nf_s2(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "BEGIN LET v := nf_s2(1); RETURN v; END");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "CREATE OR REPLACE PROCEDURE nf_s4() RETURNS NUMBER LANGUAGE SQL AS 'BEGIN RETURN nf_s2(1); END'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NF_S4.",
            "CALL nf_s4()");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'SECRETS' for 'FUNCTION'",
            "CREATE OR REPLACE TABLE nf_s5 (a NUMBER DEFAULT nf_s2(1))");
        assertAnswer(
            "Task NF_S6 successfully created.",
            "CREATE OR REPLACE TASK nf_s6 SCHEDULE = '60 MINUTE' AS SELECT nf_s2(1)");
        assertAnswer(
            "NF_S6 successfully dropped.",
            "DROP TASK IF EXISTS nf_s6");
    }
}
