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
 * A name that is taken is refused before the privilege to create is asked. A plain CREATE over a name its kind
 * already holds names the object as the statement wrote it, {@code Object 'TEST_DB.CRW_S.ST' already exists.},
 * whatever the kind - a stage, a file format, a function, a stream, a task, a tag, a policy, a secret - where
 * IF NOT EXISTS and OR REPLACE ask the privilege first. A name another kind of relation holds is refused under
 * all three: {@code Object 'SEED' already exists as TABLE}, an event table and a stream among the relations;
 * a function and a procedure of one signature exclude each other the same way; and the policies of a schema
 * share one namespace. The session stays ACCOUNTADMIN with no secondary roles over a schema another role owns.
 * Every answer is live-verified.
 */
public class TakenNameBeforePrivilegeTest extends MeasuredCellsTestSupport {

    @Test
    public void aTakenNameComesBeforeThePrivilege() {
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
                "CREATE TABLE test_db.crw_s.seed (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.SV' already exists.",
                "CREATE VIEW test_db.crw_s.sv AS SELECT 1 AS a");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.SQ' already exists.",
                "CREATE SEQUENCE test_db.crw_s.sq");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.ST' already exists.",
                "CREATE STAGE test_db.crw_s.st");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.FF' already exists.",
                "CREATE FILE FORMAT test_db.crw_s.ff TYPE = CSV");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.FN' already exists.",
                "CREATE FUNCTION test_db.crw_s.fn() RETURNS INT AS '1'");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.PR' already exists.",
                "CREATE PROCEDURE test_db.crw_s.pr() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.SM' already exists.",
                "CREATE STREAM test_db.crw_s.sm ON TABLE test_db.crw_o.base");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.TK' already exists.",
                "CREATE TASK test_db.crw_s.tk SCHEDULE = '60 MINUTE' AS SELECT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.TG' already exists.",
                "CREATE TAG test_db.crw_s.tg");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.MP' already exists.",
                "CREATE MASKING POLICY test_db.crw_s.mp AS (v INT) RETURNS INT -> v");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.RP' already exists.",
                "CREATE ROW ACCESS POLICY test_db.crw_s.rp AS (v INT) RETURNS BOOLEAN -> TRUE");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.MV' already exists.",
                "CREATE MATERIALIZED VIEW test_db.crw_s.mv AS SELECT a FROM test_db.crw_o.base");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.SC' already exists.",
                "CREATE SECRET test_db.crw_s.sc TYPE = GENERIC_STRING SECRET_STRING = 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.NR' already exists.",
                "CREATE NETWORK RULE test_db.crw_s.nr TYPE = IPV4 VALUE_LIST = ('1.2.3.4') MODE = INGRESS");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.PW' already exists.",
                "CREATE PASSWORD POLICY test_db.crw_s.pw PASSWORD_MIN_LENGTH = 12");
            answer(
                "CREATE SESSION POLICY test_db.crw_s.sp SESSION_IDLE_TIMEOUT_MINS = 30");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.ET' already exists.",
                "CREATE EVENT TABLE test_db.crw_s.et");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.JP' already exists.",
                "CREATE JOIN POLICY test_db.crw_s.jp AS () RETURNS JOIN_CONSTRAINT -> "
                    + "JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.AP' already exists.",
                "CREATE AGGREGATION POLICY test_db.crw_s.ap AS () RETURNS AGGREGATION_CONSTRAINT -> "
                    + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.PJ' already exists.",
                "CREATE PROJECTION POLICY test_db.crw_s.pj AS () RETURNS PROJECTION_CONSTRAINT -> "
                    + "PROJECTION_CONSTRAINT(ALLOW => TRUE)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.PP' already exists.",
                "CREATE PIPE test_db.crw_s.pp AS COPY INTO test_db.crw_o.base FROM @test_db.crw_o.src");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.SEED' already exists.",
                "CREATE TABLE crw_s.seed (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.SV' already exists.",
                "CREATE VIEW crw_s.sv AS SELECT 1 AS a");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.SQ' already exists.",
                "CREATE SEQUENCE crw_s.sq");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.ST' already exists.",
                "CREATE STAGE crw_s.st");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.FF' already exists.",
                "CREATE FILE FORMAT crw_s.ff TYPE = CSV");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.FN' already exists.",
                "CREATE FUNCTION crw_s.fn() RETURNS INT AS '1'");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.PR' already exists.",
                "CREATE PROCEDURE crw_s.pr() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.SM' already exists.",
                "CREATE STREAM crw_s.sm ON TABLE test_db.crw_o.base");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.TK' already exists.",
                "CREATE TASK crw_s.tk SCHEDULE = '60 MINUTE' AS SELECT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.TG' already exists.",
                "CREATE TAG crw_s.tg");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.MP' already exists.",
                "CREATE MASKING POLICY crw_s.mp AS (v INT) RETURNS INT -> v");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.RP' already exists.",
                "CREATE ROW ACCESS POLICY crw_s.rp AS (v INT) RETURNS BOOLEAN -> TRUE");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.MV' already exists.",
                "CREATE MATERIALIZED VIEW crw_s.mv AS SELECT a FROM test_db.crw_o.base");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.SC' already exists.",
                "CREATE SECRET crw_s.sc TYPE = GENERIC_STRING SECRET_STRING = 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.NR' already exists.",
                "CREATE NETWORK RULE crw_s.nr TYPE = IPV4 VALUE_LIST = ('1.2.3.4') MODE = INGRESS");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.PW' already exists.",
                "CREATE PASSWORD POLICY crw_s.pw PASSWORD_MIN_LENGTH = 12");
            answer(
                "CREATE SESSION POLICY crw_s.sp SESSION_IDLE_TIMEOUT_MINS = 30");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.ET' already exists.",
                "CREATE EVENT TABLE crw_s.et");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.JP' already exists.",
                "CREATE JOIN POLICY crw_s.jp AS () RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED => "
                    + "TRUE)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.AP' already exists.",
                "CREATE AGGREGATION POLICY crw_s.ap AS () RETURNS AGGREGATION_CONSTRAINT -> "
                    + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.PJ' already exists.",
                "CREATE PROJECTION POLICY crw_s.pj AS () RETURNS PROJECTION_CONSTRAINT -> "
                    + "PROJECTION_CONSTRAINT(ALLOW => TRUE)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'CRW_S.PP' already exists.",
                "CREATE PIPE crw_s.pp AS COPY INTO test_db.crw_o.base FROM @test_db.crw_o.src");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE TABLE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE TABLE test_db.crw_s.seed_new (a INT)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE VIEW granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE VIEW test_db.crw_s.sv_new AS SELECT 1 AS a");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE SEQUENCE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE SEQUENCE test_db.crw_s.sq_new");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE STAGE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE STAGE test_db.crw_s.st_new");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE FILE FORMAT granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE FILE FORMAT test_db.crw_s.ff_new TYPE = CSV");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE FUNCTION granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE FUNCTION test_db.crw_s.fn_new() RETURNS INT AS '1'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE PROCEDURE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE PROCEDURE test_db.crw_s.pr_new() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE STREAM granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE STREAM test_db.crw_s.sm_new ON TABLE test_db.crw_o.base");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE TASK granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE TASK test_db.crw_s.tk_new SCHEDULE = '60 MINUTE' AS SELECT 1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE TAG granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE TAG test_db.crw_s.tg_new");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE MASKING POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE MASKING POLICY test_db.crw_s.mp_new AS (v INT) RETURNS INT -> v");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE ROW ACCESS POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE ROW ACCESS POLICY test_db.crw_s.rp_new AS (v INT) RETURNS BOOLEAN -> TRUE");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE MATERIALIZED VIEW granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE MATERIALIZED VIEW test_db.crw_s.mv_new AS SELECT a FROM test_db.crw_o.base");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE SECRET granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE SECRET test_db.crw_s.sc_new TYPE = GENERIC_STRING SECRET_STRING = 'x'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE NETWORK RULE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE NETWORK RULE test_db.crw_s.nr_new TYPE = IPV4 VALUE_LIST = ('1.2.3.4') MODE = INGRESS");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE PASSWORD POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE PASSWORD POLICY test_db.crw_s.pw_new PASSWORD_MIN_LENGTH = 12");
            answer(
                "CREATE SESSION POLICY test_db.crw_s.sp_new SESSION_IDLE_TIMEOUT_MINS = 30");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE EVENT TABLE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE EVENT TABLE test_db.crw_s.et_new");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE JOIN POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE JOIN POLICY test_db.crw_s.jp_new AS () RETURNS JOIN_CONSTRAINT -> "
                    + "JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE AGGREGATION POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE AGGREGATION POLICY test_db.crw_s.ap_new AS () RETURNS AGGREGATION_CONSTRAINT -> "
                    + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE PROJECTION POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE PROJECTION POLICY test_db.crw_s.pj_new AS () RETURNS PROJECTION_CONSTRAINT -> "
                    + "PROJECTION_CONSTRAINT(ALLOW => TRUE)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE PIPE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE PIPE test_db.crw_s.pp_new AS COPY INTO test_db.crw_o.base FROM @test_db.crw_o.src");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE TABLE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE TABLE IF NOT EXISTS test_db.crw_s.seed (a INT)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE VIEW granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE VIEW IF NOT EXISTS test_db.crw_s.sv AS SELECT 1 AS a");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE SEQUENCE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE SEQUENCE IF NOT EXISTS test_db.crw_s.sq");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE STAGE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE STAGE IF NOT EXISTS test_db.crw_s.st");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE FILE FORMAT granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE FILE FORMAT IF NOT EXISTS test_db.crw_s.ff TYPE = CSV");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE FUNCTION granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE FUNCTION IF NOT EXISTS test_db.crw_s.fn() RETURNS INT AS '1'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE PROCEDURE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE PROCEDURE IF NOT EXISTS test_db.crw_s.pr() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; "
                    + "END'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE STREAM granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE STREAM IF NOT EXISTS test_db.crw_s.sm ON TABLE test_db.crw_o.base");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE TASK granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE TASK IF NOT EXISTS test_db.crw_s.tk SCHEDULE = '60 MINUTE' AS SELECT 1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE TAG granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE TAG IF NOT EXISTS test_db.crw_s.tg");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE MASKING POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE MASKING POLICY IF NOT EXISTS test_db.crw_s.mp AS (v INT) RETURNS INT -> v");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE ROW ACCESS POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE ROW ACCESS POLICY IF NOT EXISTS test_db.crw_s.rp AS (v INT) RETURNS BOOLEAN -> TRUE");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE MATERIALIZED VIEW granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE MATERIALIZED VIEW IF NOT EXISTS test_db.crw_s.mv AS SELECT a FROM test_db.crw_o.base");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE SECRET granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE SECRET IF NOT EXISTS test_db.crw_s.sc TYPE = GENERIC_STRING SECRET_STRING = 'x'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE NETWORK RULE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE NETWORK RULE IF NOT EXISTS test_db.crw_s.nr TYPE = IPV4 VALUE_LIST = ('1.2.3.4') MODE = "
                    + "INGRESS");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE PASSWORD POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE PASSWORD POLICY IF NOT EXISTS test_db.crw_s.pw PASSWORD_MIN_LENGTH = 12");
            answer(
                "CREATE SESSION POLICY IF NOT EXISTS test_db.crw_s.sp SESSION_IDLE_TIMEOUT_MINS = 30");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE EVENT TABLE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE EVENT TABLE IF NOT EXISTS test_db.crw_s.et");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE JOIN POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE JOIN POLICY IF NOT EXISTS test_db.crw_s.jp AS () RETURNS JOIN_CONSTRAINT -> "
                    + "JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE AGGREGATION POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE AGGREGATION POLICY IF NOT EXISTS test_db.crw_s.ap AS () RETURNS AGGREGATION_CONSTRAINT -> "
                    + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE PROJECTION POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE PROJECTION POLICY IF NOT EXISTS test_db.crw_s.pj AS () RETURNS PROJECTION_CONSTRAINT -> "
                    + "PROJECTION_CONSTRAINT(ALLOW => TRUE)");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE PIPE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE PIPE IF NOT EXISTS test_db.crw_s.pp AS COPY INTO test_db.crw_o.base FROM "
                    + "@test_db.crw_o.src");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SEED' already exists as TABLE",
                "CREATE VIEW test_db.crw_s.seed AS SELECT 1 AS a");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SV' already exists as VIEW",
                "CREATE TABLE test_db.crw_s.sv (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SEED' already exists as TABLE",
                "CREATE MATERIALIZED VIEW test_db.crw_s.seed AS SELECT a FROM test_db.crw_o.base");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SEED' already exists as TABLE",
                "CREATE EVENT TABLE test_db.crw_s.seed");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'ET' already exists as EVENT_TABLE",
                "CREATE TABLE test_db.crw_s.et (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'MV' already exists as MATERIALIZED_VIEW",
                "CREATE TABLE test_db.crw_s.mv (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'MV' already exists as MATERIALIZED_VIEW",
                "CREATE VIEW test_db.crw_s.mv AS SELECT 1 AS a");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE SEQUENCE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE SEQUENCE test_db.crw_s.seed");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE STAGE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE STAGE test_db.crw_s.seed");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'PR' already exists as PROCEDURE",
                "CREATE FUNCTION test_db.crw_s.pr() RETURNS INT AS '1'");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'FN' already exists as FUNCTION",
                "CREATE PROCEDURE test_db.crw_s.fn() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE FUNCTION granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE FUNCTION test_db.crw_s.fn(x INT) RETURNS INT AS '1'");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.MP' already exists.",
                "CREATE ROW ACCESS POLICY test_db.crw_s.mp AS (v INT) RETURNS BOOLEAN -> TRUE");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.RP' already exists.",
                "CREATE MASKING POLICY test_db.crw_s.rp AS (v INT) RETURNS INT -> v");
            answer(
                "CREATE PASSWORD POLICY test_db.crw_s.sp PASSWORD_MIN_LENGTH = 12");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SEED' already exists as TABLE",
                "CREATE STREAM test_db.crw_s.seed ON TABLE test_db.crw_o.base");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE TAG granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE TAG test_db.crw_s.mp");
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
    public void aNameAnotherKindHolds() {
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
                "REFUSED SQL compilation error: | Object 'SEED' already exists as TABLE",
                "CREATE VIEW IF NOT EXISTS test_db.crw_s.seed AS SELECT 1 AS a");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SEED' already exists as TABLE",
                "CREATE OR REPLACE VIEW test_db.crw_s.seed AS SELECT 1 AS a");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SV' already exists as VIEW",
                "CREATE TABLE IF NOT EXISTS test_db.crw_s.sv (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SV' already exists as VIEW",
                "CREATE OR REPLACE TABLE test_db.crw_s.sv (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'PR' already exists as PROCEDURE",
                "CREATE FUNCTION IF NOT EXISTS test_db.crw_s.pr() RETURNS INT AS '1'");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'PR' already exists as PROCEDURE",
                "CREATE OR REPLACE FUNCTION test_db.crw_s.pr() RETURNS INT AS '1'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE ROW ACCESS POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE OR REPLACE ROW ACCESS POLICY test_db.crw_s.mp AS (v INT) RETURNS BOOLEAN -> TRUE");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE ROW ACCESS POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE ROW ACCESS POLICY IF NOT EXISTS test_db.crw_s.mp AS (v INT) RETURNS BOOLEAN -> TRUE");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SEED' already exists as TABLE",
                "CREATE OR REPLACE STREAM test_db.crw_s.seed ON TABLE test_db.crw_o.base");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SEED' already exists as TABLE",
                "CREATE STREAM IF NOT EXISTS test_db.crw_s.seed ON TABLE test_db.crw_o.base");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE VIEW granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE OR REPLACE VIEW test_db.crw_s.sv AS SELECT 1 AS a");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE STAGE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE OR REPLACE STAGE test_db.crw_s.st");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE FUNCTION granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE OR REPLACE FUNCTION test_db.crw_s.fn() RETURNS INT AS '1'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE TAG granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE OR REPLACE TAG test_db.crw_s.tg");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE MASKING POLICY granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE OR REPLACE MASKING POLICY test_db.crw_s.mp AS (v INT) RETURNS INT -> v");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE SEQUENCE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE OR REPLACE SEQUENCE test_db.crw_s.sq");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE FILE FORMAT granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE OR REPLACE FILE FORMAT test_db.crw_s.ff TYPE = CSV");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SM' already exists as STREAM",
                "CREATE TABLE test_db.crw_s.sm (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SM' already exists as STREAM",
                "CREATE VIEW test_db.crw_s.sm AS SELECT 1 AS a");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SV' already exists as VIEW",
                "CREATE STREAM test_db.crw_s.sv ON TABLE test_db.crw_o.base");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'ET' already exists as EVENT_TABLE",
                "CREATE STREAM test_db.crw_s.et ON TABLE test_db.crw_o.base");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SV' already exists as VIEW",
                "CREATE EVENT TABLE test_db.crw_s.sv");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SM' already exists as STREAM",
                "CREATE EVENT TABLE test_db.crw_s.sm");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SV' already exists as VIEW",
                "CREATE MATERIALIZED VIEW test_db.crw_s.sv AS SELECT a FROM test_db.crw_o.base");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'ET' already exists as EVENT_TABLE",
                "CREATE VIEW test_db.crw_s.et AS SELECT 1 AS a");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.MP' already exists.",
                "CREATE PASSWORD POLICY test_db.crw_s.mp PASSWORD_MIN_LENGTH = 12");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_S.PW' already exists.",
                "CREATE MASKING POLICY test_db.crw_s.pw AS (v INT) RETURNS INT -> v");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE TAG granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE TAG test_db.crw_s.sq");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE SECRET granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE SECRET test_db.crw_s.nr TYPE = GENERIC_STRING SECRET_STRING = 'x'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE TASK granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE TASK test_db.crw_s.pp SCHEDULE = '60 MINUTE' AS SELECT 1");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE PIPE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE PIPE test_db.crw_s.tk AS COPY INTO test_db.crw_o.base FROM @test_db.crw_o.src");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE FILE FORMAT granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE FILE FORMAT test_db.crw_s.st TYPE = CSV");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE STAGE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE STAGE test_db.crw_s.ff");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE SEQUENCE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE SEQUENCE test_db.crw_s.sv");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE PROCEDURE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE PROCEDURE test_db.crw_s.pr(x INT) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CRW_S'. Your "
                    + "primary role <ROLE> must have CREATE PROCEDURE granted on SCHEMA TEST_DB.CRW_S.",
                "CREATE PROCEDURE test_db.crw_s.fn(x INT) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SEED' already exists as TABLE",
                "CREATE VIEW test_db.crw_o.seed AS SELECT 1 AS a");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SV' already exists as VIEW",
                "CREATE TABLE test_db.crw_o.sv (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SEED' already exists as TABLE",
                "CREATE STREAM test_db.crw_o.seed ON TABLE test_db.crw_o.base");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SM' already exists as STREAM",
                "CREATE TABLE test_db.crw_o.sm (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SM' already exists as STREAM",
                "CREATE VIEW test_db.crw_o.sm AS SELECT 1 AS a");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'ET' already exists as EVENT_TABLE",
                "CREATE TABLE test_db.crw_o.et (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SEED' already exists as TABLE",
                "CREATE EVENT TABLE test_db.crw_o.seed");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'MV' already exists as MATERIALIZED_VIEW",
                "CREATE TABLE test_db.crw_o.mv (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'PR' already exists as PROCEDURE",
                "CREATE FUNCTION test_db.crw_o.pr() RETURNS INT AS '1'");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'FN' already exists as FUNCTION",
                "CREATE PROCEDURE test_db.crw_o.fn() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_O.MP' already exists.",
                "CREATE ROW ACCESS POLICY test_db.crw_o.mp AS (v INT) RETURNS BOOLEAN -> TRUE");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_DB.CRW_O.MP' already exists.",
                "CREATE PASSWORD POLICY test_db.crw_o.mp PASSWORD_MIN_LENGTH = 12");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SEED' already exists as TABLE",
                "CREATE VIEW IF NOT EXISTS test_db.crw_o.seed AS SELECT 1 AS a");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SV' already exists as VIEW",
                "CREATE TABLE IF NOT EXISTS test_db.crw_o.sv (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'PR' already exists as PROCEDURE",
                "CREATE FUNCTION IF NOT EXISTS test_db.crw_o.pr() RETURNS INT AS '1'");
            assertAnswer(
                "MP already exists, statement succeeded.",
                "CREATE ROW ACCESS POLICY IF NOT EXISTS test_db.crw_o.mp AS (v INT) RETURNS BOOLEAN -> TRUE");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SEED' already exists as TABLE",
                "CREATE STREAM IF NOT EXISTS test_db.crw_o.seed ON TABLE test_db.crw_o.base");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SEED' already exists as TABLE",
                "CREATE OR REPLACE VIEW test_db.crw_o.seed AS SELECT 1 AS a");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SV' already exists as VIEW",
                "CREATE OR REPLACE TABLE test_db.crw_o.sv (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'PR' already exists as PROCEDURE",
                "CREATE OR REPLACE FUNCTION test_db.crw_o.pr() RETURNS INT AS '1'");
            assertAnswer(
                "REFUSED SQL compilation error: Object found is of type 'MASKING_POLICY', not specified type "
                    + "'ROW_ACCESS_POLICY'.",
                "CREATE OR REPLACE ROW ACCESS POLICY test_db.crw_o.mp AS (v INT) RETURNS BOOLEAN -> TRUE");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SEED' already exists as TABLE",
                "CREATE OR REPLACE STREAM test_db.crw_o.seed ON TABLE test_db.crw_o.base");
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
