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
 * A secrets list that names a variable twice. ALTER FUNCTION and ALTER PROCEDURE judge the list before they look
 * the routine up: {@code Duplicate secret name 'a' passed in.} comes for a routine that is not there, under IF
 * EXISTS, in a database that is not there, and ahead of a secret that is missing. Two names are one when they
 * differ in case alone, and the later spelling is the one told. Every answer is live-verified.
 */
public class RoutineSecretListTest extends MeasuredCellsTestSupport {

    @Test
    public void aRepeatedSecretNameComesFirst() {
        answer(
            "CREATE OR REPLACE SECRET nf_sec TYPE = GENERIC_STRING SECRET_STRING = 'v'");
        answer(
            "CREATE OR REPLACE TABLE nf_t (a NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: Duplicate secret name 'a' passed in.",
            "ALTER FUNCTION nf_nosuch(NUMBER) SET SECRETS = ('a' = nf_sec, 'a' = nf_sec)");
        assertAnswer(
            "REFUSED SQL compilation error: Duplicate secret name 'A' passed in.",
            "ALTER FUNCTION nf_nosuch(NUMBER) SET SECRETS = ('a' = nf_sec, 'A' = nf_sec)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.NF_NOSUCH' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.NF_NOSUCH.",
            "ALTER FUNCTION nf_nosuch(NUMBER) SET SECRETS = ('a' = nf_nosec)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.NF_NOSUCH' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.NF_NOSUCH.",
            "ALTER FUNCTION nf_nosuch(NUMBER) SET SECRETS = ()");
        assertAnswer(
            "REFUSED SQL compilation error: Duplicate secret name 'a' passed in.",
            "ALTER PROCEDURE nf_nosuch(NUMBER) SET SECRETS = ('a' = nf_sec, 'a' = nf_sec)");
        assertAnswer(
            "REFUSED SQL compilation error: Duplicate secret name 'a' passed in.",
            "ALTER FUNCTION IF EXISTS nf_nosuch(NUMBER) SET SECRETS = ('a' = nf_sec, 'a' = nf_sec)");
        assertAnswer(
            "REFUSED SQL compilation error: Duplicate secret name 'a' passed in.",
            "ALTER FUNCTION nf_nosuch(NUMBER) SET SECRETS = ('a' = nf_sec, 'a' = nf_sec) COMMENT = 'c'");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.NF_NOSUCH' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.NF_NOSUCH.",
            "ALTER FUNCTION nf_nosuch(NUMBER) SET COMMENT = 'c'");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.NF_NOSUCH' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.NF_NOSUCH.",
            "ALTER FUNCTION nf_nosuch(NUMBER) SET EXTERNAL_ACCESS_INTEGRATIONS = (nf_noint)");
        assertAnswer(
            "REFUSED SQL compilation error: Duplicate secret name 'a' passed in.",
            "ALTER FUNCTION nf_nosuch(NUMBER) SET SECRETS = ('a' = nf_sec, 'b' = nf_nosec, 'a' = nf_sec)");
        assertAnswer(
            "Function NF_AQ successfully created.",
            "CREATE OR REPLACE FUNCTION nf_aq(x NUMBER) RETURNS NUMBER AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: Duplicate secret name 'a' passed in.",
            "ALTER FUNCTION nf_aq(NUMBER) SET SECRETS = ('a' = nf_sec, 'a' = nf_sec)");
        assertAnswer(
            "REFUSED SQL compilation error: Duplicate secret name 'a' passed in.",
            "ALTER FUNCTION nf_aq(NUMBER) SET SECRETS = ('a' = nf_sec, 'b' = nf_sec, 'a' = nf_sec)");
        assertAnswer(
            "REFUSED SQL compilation error: Duplicate secret name 'a' passed in.",
            "ALTER FUNCTION nf_aq(NUMBER) SET SECRETS = ('a' = nf_nosec, 'a' = nf_sec)");
        assertAnswer(
            "REFUSED SQL compilation error: Duplicate secret name 'a' passed in.",
            "CREATE OR REPLACE FUNCTION nf_dup(x NUMBER) RETURNS NUMBER SECRETS = ('a' = nf_sec, 'a' = nf_sec) AS "
                + "'x'");
        assertAnswer(
            "REFUSED SQL compilation error: Duplicate secret name 'a' passed in.",
            "CREATE OR REPLACE FUNCTION nf_dup(x NUMBER) RETURNS NUMBER SECRETS = ('a' = nf_nosec, 'a' = "
                + "nf_nosec) AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: Duplicate secret name 'a' passed in.",
            "ALTER FUNCTION nf_aq(VARCHAR) SET SECRETS = ('a' = nf_sec, 'a' = nf_sec)");
        assertAnswer(
            "REFUSED SQL compilation error: Duplicate secret name 'a' passed in.",
            "ALTER FUNCTION nf_nodb.s.f(NUMBER) SET SECRETS = ('a' = nf_sec, 'a' = nf_sec)");
    }
}
