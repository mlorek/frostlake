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
 * A rename creates the object under its new name. A new name that is taken - by the object itself, or by
 * another kind of relation - is refused first and as the statement wrote it, {@code Object 'TEST_DB.CRW_S.SV'
 * already exists.}; a free one asks the schema it lands in for the privilege to create the kind, in place or
 * elsewhere: CREATE STAGE for a stage, CREATE FUNCTION for a function, CREATE MASKING POLICY for a masking
 * policy. A materialized view is renamed and moved by a name with a path. The session stays ACCOUNTADMIN with
 * no secondary roles over a schema another role owns. Every answer is live-verified.
 */
public class RenamePrivilegeTest extends MeasuredCellsTestSupport {

    @Test
    public void aRenameAsksToCreateWhereItLands() {
        try {
            answer(
                "USE ROLE ACCOUNTADMIN");
            answer(
                "USE SECONDARY ROLES NONE");
            answer(
                "CREATE ROLE IF NOT EXISTS flq_crw");
            answer(
                "CREATE SCHEMA test_db.crw_s");
            answer(
                "CREATE SCHEMA test_db.crw_o");
            answer(
                "CREATE TABLE test_db.crw_o.base (a INT)");
            answer(
                "CREATE STAGE test_db.crw_o.src");
            answer(
                "CREATE TABLE test_db.crw_s.seed (a INT)");
            answer(
                "CREATE TABLE test_db.crw_o.seed2 (a INT)");
            answer(
                "CREATE VIEW test_db.crw_s.sv AS SELECT 1 AS a");
            answer(
                "CREATE VIEW test_db.crw_o.sv2 AS SELECT 1 AS a");
            answer(
                "CREATE SEQUENCE test_db.crw_s.sq");
            answer(
                "CREATE SEQUENCE test_db.crw_o.sq2");
            answer(
                "CREATE STAGE test_db.crw_s.st");
            answer(
                "CREATE STAGE test_db.crw_o.st2");
            answer(
                "CREATE FILE FORMAT test_db.crw_s.ff TYPE = CSV");
            answer(
                "CREATE FILE FORMAT test_db.crw_o.ff2 TYPE = CSV");
            answer(
                "CREATE FUNCTION test_db.crw_s.fn() RETURNS INT AS '1'");
            answer(
                "CREATE FUNCTION test_db.crw_o.fn2() RETURNS INT AS '1'");
            answer(
                "CREATE PROCEDURE test_db.crw_s.pr() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            answer(
                "CREATE PROCEDURE test_db.crw_o.pr2() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            answer(
                "CREATE STREAM test_db.crw_s.sm ON TABLE test_db.crw_o.base");
            answer(
                "CREATE STREAM test_db.crw_o.sm2 ON TABLE test_db.crw_o.base");
            answer(
                "CREATE TASK test_db.crw_s.tk SCHEDULE = '60 MINUTE' AS SELECT 1");
            answer(
                "CREATE TASK test_db.crw_o.tk2 SCHEDULE = '60 MINUTE' AS SELECT 1");
            answer(
                "CREATE TAG test_db.crw_s.tg");
            answer(
                "CREATE TAG test_db.crw_o.tg2");
            answer(
                "CREATE MASKING POLICY test_db.crw_s.mp AS (v INT) RETURNS INT -> v");
            answer(
                "CREATE MASKING POLICY test_db.crw_o.mp2 AS (v INT) RETURNS INT -> v");
            answer(
                "CREATE ROW ACCESS POLICY test_db.crw_s.rp AS (v INT) RETURNS BOOLEAN -> TRUE");
            answer(
                "CREATE ROW ACCESS POLICY test_db.crw_o.rp2 AS (v INT) RETURNS BOOLEAN -> TRUE");
            answer(
                "CREATE MATERIALIZED VIEW test_db.crw_s.mv AS SELECT a FROM test_db.crw_o.base");
            answer(
                "CREATE MATERIALIZED VIEW test_db.crw_o.mv2 AS SELECT a FROM test_db.crw_o.base");
            answer(
                "CREATE SECRET test_db.crw_s.sc TYPE = GENERIC_STRING SECRET_STRING = 'x'");
            answer(
                "CREATE SECRET test_db.crw_o.sc2 TYPE = GENERIC_STRING SECRET_STRING = 'x'");
            answer(
                "CREATE NETWORK RULE test_db.crw_s.nr TYPE = IPV4 VALUE_LIST = ('1.2.3.4') MODE = INGRESS");
            answer(
                "CREATE NETWORK RULE test_db.crw_o.nr2 TYPE = IPV4 VALUE_LIST = ('1.2.3.4') MODE = INGRESS");
            answer(
                "CREATE PASSWORD POLICY test_db.crw_s.pw PASSWORD_MIN_LENGTH = 12");
            answer(
                "CREATE PASSWORD POLICY test_db.crw_o.pw2 PASSWORD_MIN_LENGTH = 12");
            answer(
                "CREATE SESSION POLICY test_db.crw_s.sp SESSION_IDLE_TIMEOUT_MINS = 30");
            answer(
                "CREATE SESSION POLICY test_db.crw_o.sp2 SESSION_IDLE_TIMEOUT_MINS = 30");
            answer(
                "CREATE EVENT TABLE test_db.crw_s.et");
            answer(
                "CREATE EVENT TABLE test_db.crw_o.et2");
            answer(
                "CREATE JOIN POLICY test_db.crw_s.jp AS () RETURNS JOIN_CONSTRAINT -> "
                    + "JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)");
            answer(
                "CREATE JOIN POLICY test_db.crw_o.jp2 AS () RETURNS JOIN_CONSTRAINT -> "
                    + "JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)");
            answer(
                "CREATE AGGREGATION POLICY test_db.crw_s.ap AS () RETURNS AGGREGATION_CONSTRAINT -> "
                    + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5)");
            answer(
                "CREATE AGGREGATION POLICY test_db.crw_o.ap2 AS () RETURNS AGGREGATION_CONSTRAINT -> "
                    + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5)");
            answer(
                "CREATE PROJECTION POLICY test_db.crw_s.pj AS () RETURNS PROJECTION_CONSTRAINT -> "
                    + "PROJECTION_CONSTRAINT(ALLOW => TRUE)");
            answer(
                "CREATE PROJECTION POLICY test_db.crw_o.pj2 AS () RETURNS PROJECTION_CONSTRAINT -> "
                    + "PROJECTION_CONSTRAINT(ALLOW => TRUE)");
            answer(
                "CREATE PIPE test_db.crw_s.pp AS COPY INTO test_db.crw_o.base FROM @test_db.crw_o.src");
            answer(
                "CREATE PIPE test_db.crw_o.pp2 AS COPY INTO test_db.crw_o.base FROM @test_db.crw_o.src");
            answer(
                "GRANT OWNERSHIP ON SCHEMA test_db.crw_s TO ROLE flq_crw");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.SEED' already exists.",
                "ALTER TABLE test_db.crw_o.seed2 RENAME TO test_db.crw_s.seed");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.SV' already exists.",
                "ALTER VIEW test_db.crw_o.sv2 RENAME TO test_db.crw_s.sv");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.SQ' already exists.",
                "ALTER SEQUENCE test_db.crw_o.sq2 RENAME TO test_db.crw_s.sq");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.ST' already exists.",
                "ALTER STAGE test_db.crw_o.st2 RENAME TO test_db.crw_s.st");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.FF' already exists.",
                "ALTER FILE FORMAT test_db.crw_o.ff2 RENAME TO test_db.crw_s.ff");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.FN' already exists.",
                "ALTER FUNCTION test_db.crw_o.fn2() RENAME TO test_db.crw_s.fn");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.PR' already exists.",
                "ALTER PROCEDURE test_db.crw_o.pr2() RENAME TO test_db.crw_s.pr");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.TG' already exists.",
                "ALTER TAG test_db.crw_o.tg2 RENAME TO test_db.crw_s.tg");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.MP' already exists.",
                "ALTER MASKING POLICY test_db.crw_o.mp2 RENAME TO test_db.crw_s.mp");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.RP' already exists.",
                "ALTER ROW ACCESS POLICY test_db.crw_o.rp2 RENAME TO test_db.crw_s.rp");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.MV' already exists.",
                "ALTER MATERIALIZED VIEW test_db.crw_o.mv2 RENAME TO test_db.crw_s.mv");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.PW' already exists.",
                "ALTER PASSWORD POLICY test_db.crw_o.pw2 RENAME TO test_db.crw_s.pw");
            answer(
                "ALTER SESSION POLICY test_db.crw_o.sp2 RENAME TO test_db.crw_s.sp");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.ET' already exists.",
                "ALTER TABLE test_db.crw_o.et2 RENAME TO test_db.crw_s.et");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.JP' already exists.",
                "ALTER JOIN POLICY test_db.crw_o.jp2 RENAME TO test_db.crw_s.jp");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.AP' already exists.",
                "ALTER AGGREGATION POLICY test_db.crw_o.ap2 RENAME TO test_db.crw_s.ap");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.PJ' already exists.",
                "ALTER PROJECTION POLICY test_db.crw_o.pj2 RENAME TO test_db.crw_s.pj");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE TABLE granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER TABLE test_db.crw_o.seed2 RENAME TO test_db.crw_s.seed_m");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE VIEW granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER VIEW test_db.crw_o.sv2 RENAME TO test_db.crw_s.sv_m");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE SEQUENCE granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER SEQUENCE test_db.crw_o.sq2 RENAME TO test_db.crw_s.sq_m");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE STAGE granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER STAGE test_db.crw_o.st2 RENAME TO test_db.crw_s.st_m");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE FILE FORMAT granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER FILE FORMAT test_db.crw_o.ff2 RENAME TO test_db.crw_s.ff_m");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE FUNCTION granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER FUNCTION test_db.crw_o.fn2() RENAME TO test_db.crw_s.fn_m");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE PROCEDURE granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER PROCEDURE test_db.crw_o.pr2() RENAME TO test_db.crw_s.pr_m");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE TAG granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER TAG test_db.crw_o.tg2 RENAME TO test_db.crw_s.tg_m");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE MASKING POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER MASKING POLICY test_db.crw_o.mp2 RENAME TO test_db.crw_s.mp_m");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE ROW ACCESS POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER ROW ACCESS POLICY test_db.crw_o.rp2 RENAME TO test_db.crw_s.rp_m");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE MATERIALIZED VIEW granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER MATERIALIZED VIEW test_db.crw_o.mv2 RENAME TO test_db.crw_s.mv_m");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE PASSWORD POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER PASSWORD POLICY test_db.crw_o.pw2 RENAME TO test_db.crw_s.pw_m");
            answer(
                "ALTER SESSION POLICY test_db.crw_o.sp2 RENAME TO test_db.crw_s.sp_m");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE TABLE granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER TABLE test_db.crw_o.et2 RENAME TO test_db.crw_s.et_m");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE JOIN POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER JOIN POLICY test_db.crw_o.jp2 RENAME TO test_db.crw_s.jp_m");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE AGGREGATION POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER AGGREGATION POLICY test_db.crw_o.ap2 RENAME TO test_db.crw_s.ap_m");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE PROJECTION POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER PROJECTION POLICY test_db.crw_o.pj2 RENAME TO test_db.crw_s.pj_m");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.SEED' already exists.",
                "ALTER TABLE test_db.crw_s.seed RENAME TO test_db.crw_s.seed");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.SV' already exists.",
                "ALTER VIEW test_db.crw_s.sv RENAME TO test_db.crw_s.sv");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.SQ' already exists.",
                "ALTER SEQUENCE test_db.crw_s.sq RENAME TO test_db.crw_s.sq");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.ST' already exists.",
                "ALTER STAGE test_db.crw_s.st RENAME TO test_db.crw_s.st");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.FF' already exists.",
                "ALTER FILE FORMAT test_db.crw_s.ff RENAME TO test_db.crw_s.ff");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.FN' already exists.",
                "ALTER FUNCTION test_db.crw_s.fn() RENAME TO test_db.crw_s.fn");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.PR' already exists.",
                "ALTER PROCEDURE test_db.crw_s.pr() RENAME TO test_db.crw_s.pr");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.TG' already exists.",
                "ALTER TAG test_db.crw_s.tg RENAME TO test_db.crw_s.tg");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.MP' already exists.",
                "ALTER MASKING POLICY test_db.crw_s.mp RENAME TO test_db.crw_s.mp");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.RP' already exists.",
                "ALTER ROW ACCESS POLICY test_db.crw_s.rp RENAME TO test_db.crw_s.rp");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.MV' already exists.",
                "ALTER MATERIALIZED VIEW test_db.crw_s.mv RENAME TO test_db.crw_s.mv");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.PW' already exists.",
                "ALTER PASSWORD POLICY test_db.crw_s.pw RENAME TO test_db.crw_s.pw");
            answer(
                "ALTER SESSION POLICY test_db.crw_s.sp RENAME TO test_db.crw_s.sp");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.ET' already exists.",
                "ALTER TABLE test_db.crw_s.et RENAME TO test_db.crw_s.et");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.JP' already exists.",
                "ALTER JOIN POLICY test_db.crw_s.jp RENAME TO test_db.crw_s.jp");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.AP' already exists.",
                "ALTER AGGREGATION POLICY test_db.crw_s.ap RENAME TO test_db.crw_s.ap");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.PJ' already exists.",
                "ALTER PROJECTION POLICY test_db.crw_s.pj RENAME TO test_db.crw_s.pj");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE TABLE granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER TABLE test_db.crw_s.seed RENAME TO test_db.crw_s.seed_r");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE VIEW granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER VIEW test_db.crw_s.sv RENAME TO test_db.crw_s.sv_r");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE SEQUENCE granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER SEQUENCE test_db.crw_s.sq RENAME TO test_db.crw_s.sq_r");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE STAGE granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER STAGE test_db.crw_s.st RENAME TO test_db.crw_s.st_r");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE FILE FORMAT granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER FILE FORMAT test_db.crw_s.ff RENAME TO test_db.crw_s.ff_r");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE FUNCTION granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER FUNCTION test_db.crw_s.fn() RENAME TO test_db.crw_s.fn_r");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE PROCEDURE granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER PROCEDURE test_db.crw_s.pr() RENAME TO test_db.crw_s.pr_r");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE TAG granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER TAG test_db.crw_s.tg RENAME TO test_db.crw_s.tg_r");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE MASKING POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER MASKING POLICY test_db.crw_s.mp RENAME TO test_db.crw_s.mp_r");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE ROW ACCESS POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER ROW ACCESS POLICY test_db.crw_s.rp RENAME TO test_db.crw_s.rp_r");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE MATERIALIZED VIEW granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER MATERIALIZED VIEW test_db.crw_s.mv RENAME TO test_db.crw_s.mv_r");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE PASSWORD POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER PASSWORD POLICY test_db.crw_s.pw RENAME TO test_db.crw_s.pw_r");
            answer(
                "ALTER SESSION POLICY test_db.crw_s.sp RENAME TO test_db.crw_s.sp_r");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE TABLE granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER TABLE test_db.crw_s.et RENAME TO test_db.crw_s.et_r");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE JOIN POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER JOIN POLICY test_db.crw_s.jp RENAME TO test_db.crw_s.jp_r");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE AGGREGATION POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER AGGREGATION POLICY test_db.crw_s.ap RENAME TO test_db.crw_s.ap_r");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE PROJECTION POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "ALTER PROJECTION POLICY test_db.crw_s.pj RENAME TO test_db.crw_s.pj_r");
            assertAnswer(
                "ACCOUNTADMIN|{\"roles\":\"\",\"value\":\"\"}",
                "SELECT CURRENT_ROLE(), CURRENT_SECONDARY_ROLES()");
        } finally {
            answer(
                "USE ROLE ACCOUNTADMIN");
            answer(
                "GRANT ROLE flq_crw TO ROLE ACCOUNTADMIN");
            answer(
                "DROP SCHEMA IF EXISTS test_db.crw_s");
            answer(
                "DROP SCHEMA IF EXISTS test_db.crw_o");
            answer(
                "DROP ROLE IF EXISTS flq_crw");
            answer(
                "USE SECONDARY ROLES ALL");
        }
    }

    @Test
    public void aRenameOntoAnotherKindsName() {
        try {
            answer(
                "USE ROLE ACCOUNTADMIN");
            answer(
                "USE SECONDARY ROLES NONE");
            answer(
                "CREATE ROLE IF NOT EXISTS flq_crw");
            answer(
                "CREATE SCHEMA test_db.crw_s");
            answer(
                "CREATE SCHEMA test_db.crw_o");
            answer(
                "CREATE TABLE test_db.crw_o.base (a INT)");
            answer(
                "CREATE STAGE test_db.crw_o.src");
            answer(
                "CREATE TABLE test_db.crw_s.seed (a INT)");
            answer(
                "CREATE TABLE test_db.crw_o.seed (a INT)");
            answer(
                "CREATE VIEW test_db.crw_s.sv AS SELECT 1 AS a");
            answer(
                "CREATE VIEW test_db.crw_o.sv AS SELECT 1 AS a");
            answer(
                "CREATE SEQUENCE test_db.crw_s.sq");
            answer(
                "CREATE SEQUENCE test_db.crw_o.sq");
            answer(
                "CREATE STAGE test_db.crw_s.st");
            answer(
                "CREATE STAGE test_db.crw_o.st");
            answer(
                "CREATE FILE FORMAT test_db.crw_s.ff TYPE = CSV");
            answer(
                "CREATE FILE FORMAT test_db.crw_o.ff TYPE = CSV");
            answer(
                "CREATE FUNCTION test_db.crw_s.fn() RETURNS INT AS '1'");
            answer(
                "CREATE FUNCTION test_db.crw_o.fn() RETURNS INT AS '1'");
            answer(
                "CREATE PROCEDURE test_db.crw_s.pr() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            answer(
                "CREATE PROCEDURE test_db.crw_o.pr() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            answer(
                "CREATE STREAM test_db.crw_s.sm ON TABLE test_db.crw_o.base");
            answer(
                "CREATE STREAM test_db.crw_o.sm ON TABLE test_db.crw_o.base");
            answer(
                "CREATE TASK test_db.crw_s.tk SCHEDULE = '60 MINUTE' AS SELECT 1");
            answer(
                "CREATE TASK test_db.crw_o.tk SCHEDULE = '60 MINUTE' AS SELECT 1");
            answer(
                "CREATE TAG test_db.crw_s.tg");
            answer(
                "CREATE TAG test_db.crw_o.tg");
            answer(
                "CREATE MASKING POLICY test_db.crw_s.mp AS (v INT) RETURNS INT -> v");
            answer(
                "CREATE MASKING POLICY test_db.crw_o.mp AS (v INT) RETURNS INT -> v");
            answer(
                "CREATE ROW ACCESS POLICY test_db.crw_s.rp AS (v INT) RETURNS BOOLEAN -> TRUE");
            answer(
                "CREATE ROW ACCESS POLICY test_db.crw_o.rp AS (v INT) RETURNS BOOLEAN -> TRUE");
            answer(
                "CREATE MATERIALIZED VIEW test_db.crw_s.mv AS SELECT a FROM test_db.crw_o.base");
            answer(
                "CREATE MATERIALIZED VIEW test_db.crw_o.mv AS SELECT a FROM test_db.crw_o.base");
            answer(
                "CREATE SECRET test_db.crw_s.sc TYPE = GENERIC_STRING SECRET_STRING = 'x'");
            answer(
                "CREATE SECRET test_db.crw_o.sc TYPE = GENERIC_STRING SECRET_STRING = 'x'");
            answer(
                "CREATE NETWORK RULE test_db.crw_s.nr TYPE = IPV4 VALUE_LIST = ('1.2.3.4') MODE = INGRESS");
            answer(
                "CREATE NETWORK RULE test_db.crw_o.nr TYPE = IPV4 VALUE_LIST = ('1.2.3.4') MODE = INGRESS");
            answer(
                "CREATE PASSWORD POLICY test_db.crw_s.pw PASSWORD_MIN_LENGTH = 12");
            answer(
                "CREATE PASSWORD POLICY test_db.crw_o.pw PASSWORD_MIN_LENGTH = 12");
            answer(
                "CREATE EVENT TABLE test_db.crw_s.et");
            answer(
                "CREATE EVENT TABLE test_db.crw_o.et");
            answer(
                "CREATE PIPE test_db.crw_s.pp AS COPY INTO test_db.crw_o.base FROM @test_db.crw_o.src");
            answer(
                "CREATE PIPE test_db.crw_o.pp AS COPY INTO test_db.crw_o.base FROM @test_db.crw_o.src");
            answer(
                "GRANT OWNERSHIP ON SCHEMA test_db.crw_s TO ROLE flq_crw");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.SV' already exists.",
                "ALTER TABLE test_db.crw_o.seed RENAME TO test_db.crw_s.sv");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.SEED' already exists.",
                "ALTER VIEW test_db.crw_o.sv RENAME TO test_db.crw_s.seed");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.SM' already exists.",
                "ALTER TABLE test_db.crw_o.seed RENAME TO test_db.crw_s.sm");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'PR' already exists as PROCEDURE",
                "ALTER FUNCTION test_db.crw_o.fn() RENAME TO test_db.crw_s.pr");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'FN' already exists as FUNCTION",
                "ALTER PROCEDURE test_db.crw_o.pr() RENAME TO test_db.crw_s.fn");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.RP' already exists.",
                "ALTER MASKING POLICY test_db.crw_o.mp RENAME TO test_db.crw_s.rp");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.MP' already exists.",
                "ALTER PASSWORD POLICY test_db.crw_o.pw RENAME TO test_db.crw_s.mp");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.SEED' already exists.",
                "ALTER TABLE test_db.crw_o.et RENAME TO test_db.crw_s.seed");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.SEED' already exists.",
                "ALTER MATERIALIZED VIEW test_db.crw_o.mv RENAME TO test_db.crw_s.seed");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.MV' already exists.",
                "ALTER VIEW test_db.crw_o.sv RENAME TO test_db.crw_s.mv");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "TEST_DB.NOSUCH_SCHEMA.",
                "ALTER TABLE test_db.crw_o.seed RENAME TO test_db.nosuch_schema.x");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "TEST_DB.NOSUCH_SCHEMA.",
                "ALTER STAGE test_db.crw_o.st RENAME TO test_db.nosuch_schema.x");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "TEST_DB.NOSUCH_SCHEMA.",
                "ALTER FUNCTION test_db.crw_o.fn() RENAME TO test_db.nosuch_schema.x");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "TEST_DB.NOSUCH_SCHEMA.",
                "ALTER TAG test_db.crw_o.tg RENAME TO test_db.nosuch_schema.x");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
                "ALTER TABLE test_db.crw_o.seed RENAME TO nosuch_db.s.x");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_O.SV' already exists.",
                "ALTER TABLE test_db.crw_o.seed RENAME TO test_db.crw_o.sv");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_O.SEED' already exists.",
                "ALTER VIEW test_db.crw_o.sv RENAME TO test_db.crw_o.seed");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'PR' already exists as PROCEDURE",
                "ALTER FUNCTION test_db.crw_o.fn() RENAME TO test_db.crw_o.pr");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_O.RP' already exists.",
                "ALTER MASKING POLICY test_db.crw_o.mp RENAME TO test_db.crw_o.rp");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER MATERIALIZED VIEW test_db.crw_o.mv RENAME TO test_db.crw_o.mv_r");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER MATERIALIZED VIEW test_db.crw_o.mv_r RENAME TO test_db.crw_o.mv");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'STREAM'. | syntax "
                    + "error line 1 at position 13 unexpected 'test_db'.",
                "ALTER STREAM test_db.crw_o.sm RENAME TO test_db.crw_o.sm_r");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'RENAME'.",
                "ALTER TASK test_db.crw_o.tk RENAME TO test_db.crw_o.tk_r");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'RENAME'.",
                "ALTER PIPE test_db.crw_o.pp RENAME TO test_db.crw_o.pp_r");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'RENAME'.",
                "ALTER SECRET test_db.crw_o.sc RENAME TO test_db.crw_o.sc_r");
            assertAnswer(
                "REFUSED Unsupported feature 'renaming NETWORK_RULE'.",
                "ALTER NETWORK RULE test_db.crw_o.nr RENAME TO test_db.crw_o.nr_r");
            answer(
                "ALTER EVENT TABLE test_db.crw_o.et RENAME TO test_db.crw_o.et_r");
            assertAnswer(
                "ACCOUNTADMIN|{\"roles\":\"\",\"value\":\"\"}",
                "SELECT CURRENT_ROLE(), CURRENT_SECONDARY_ROLES()");
        } finally {
            answer(
                "USE ROLE ACCOUNTADMIN");
            answer(
                "GRANT ROLE flq_crw TO ROLE ACCOUNTADMIN");
            answer(
                "DROP SCHEMA IF EXISTS test_db.crw_s");
            answer(
                "DROP SCHEMA IF EXISTS test_db.crw_o");
            answer(
                "DROP ROLE IF EXISTS flq_crw");
            answer(
                "USE SECONDARY ROLES ALL");
        }
    }
}
