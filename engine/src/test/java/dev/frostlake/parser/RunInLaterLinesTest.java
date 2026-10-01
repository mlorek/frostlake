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

package dev.frostlake.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A token run in after a complete statement ends the syntax-error report, whatever the statements after it hold: a
 * word, a comma met as the script's first fault, an opening '(' refused where no signature reads it, and anything after
 * a routine's quoted body. Live reads the later statements on only straight after a DROP's object name (not a stage's),
 * a SHOW's one-word kind, an UNSET's one name and BEGIN WORK or TRANSACTION, and, for a '(', after a membership or null
 * test, a VALUES row or the name of a plain CREATE DATABASE or CREATE SCHEMA (live-verified).
 */
public class RunInLaterLinesTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String lines(final String... lines) {
        return "SQL compilation error:\n" + String.join("\n", lines);
    }

    private static String at(final int position, final String token) {
        return "syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aTokenRunInAfterARoutinesQuotedBodyEndsTheReport() {
        assertEquals(lines(at(39, "x")), refusal("CREATE FUNCTION f() RETURNS INT AS '1' x y; SELECT 1 x y"));
        assertEquals(lines(at(39, "x")), refusal("CREATE FUNCTION f() RETURNS INT AS '1' x; SELECT 1 x y"));
        assertEquals(lines(at(39, "LANGUAGE")),
            refusal("CREATE FUNCTION f() RETURNS INT AS '1' LANGUAGE SQL; SELECT 1 x y"));
        assertEquals(lines(at(39, "2")), refusal("CREATE FUNCTION f() RETURNS INT AS '1' 2; SELECT 1 x y"));
        assertEquals(lines(at(39, "x")), refusal("CREATE FUNCTION f() RETURNS INT AS '1' x; SELECT 1 +"));
        assertEquals(lines(at(39, "COMMENT")),
            refusal("CREATE FUNCTION f() RETURNS INT AS '1' COMMENT = 'x'; SELECT 1 x y"));
        assertEquals(lines(at(71, "x")),
            refusal("CREATE PROCEDURE p() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END' x; SELECT 1 x y"));
        assertEquals(lines(at(41, "x")), refusal("CREATE FUNCTION f() RETURNS INT AS $$1$$ x; SELECT 1 x y"));
        assertEquals(lines(at(50, "x")), refusal("CREATE OR REPLACE FUNCTION f() RETURNS INT AS '1' x; SELECT 1 x y"));
        assertEquals(lines(at(46, "x")), refusal("CREATE SECURE FUNCTION f() RETURNS INT AS '1' x; SELECT 1 x y"));
        assertEquals(lines(at(66, "x")),
            refusal("CREATE FUNCTION f() RETURNS INT LANGUAGE JAVASCRIPT AS 'return 1' x; SELECT 1 x y"));
        assertEquals(lines(at(56, "x")),
            refusal("CREATE FUNCTION f() RETURNS TABLE (a INT) AS 'SELECT 1' x; SELECT 1 x y"));
        assertEquals(lines(at(44, "x")), refusal("CREATE FUNCTION f(a INT) RETURNS INT AS 'a' x; SELECT 1 x y"));
        assertEquals(lines(at(73, "x")),
            refusal("CREATE PROCEDURE p() RETURNS INT LANGUAGE SQL AS $$BEGIN RETURN 1; END$$ x; SELECT 1 x y"));
        assertEquals(lines(at(67, "x")),
            refusal("CREATE PROCEDURE p() RETURNS INT LANGUAGE JAVASCRIPT AS 'return 1' x; SELECT 1 x y"));
        assertEquals(lines(at(39, "(")), refusal("CREATE FUNCTION f() RETURNS INT AS '1' ('x'); SELECT 1 x y"));
        assertEquals(lines(at(39, "'q'")), refusal("CREATE FUNCTION f() RETURNS INT AS '1' 'q'; SELECT 1 x y"));
        assertEquals(lines(at(39, "IMMUTABLE")),
            refusal("CREATE FUNCTION f() RETURNS INT AS '1' IMMUTABLE; SELECT 1 x y"));
        assertEquals(lines(at(38, ",")), refusal("CREATE FUNCTION f() RETURNS INT AS '1', x; SELECT 1 x y"));
        assertEquals(lines(at(39, "x")), refusal("CREATE FUNCTION f() RETURNS INT AS '1' x; SELECT 1; SELECT 1 x y"));
        assertEquals(lines(at(39, "x")), refusal("CREATE FUNCTION f() RETURNS INT AS '1' x; SELECT 1 FROM"));
        assertEquals(lines(at(39, "x")), refusal("CREATE FUNCTION f() RETURNS INT AS '1' x; SELECT 1 +; SELECT 1 x y"));
        assertEquals(lines(at(39, "x")),
            refusal("CREATE FUNCTION f() RETURNS INT AS '1' x; CREATE TABLE; SELECT 1 x y"));
        assertEquals(lines(at(39, "x")),
            refusal("CREATE FUNCTION f() RETURNS INT AS '1' x; DROP TABLE t x; SELECT 1 x y"));
        assertEquals(lines(at(39, "x")),
            refusal("CREATE FUNCTION f() RETURNS INT AS '1' x; SELECT 1 x y; SELECT 2 x y"));
        assertEquals(lines(at(10, ";"), at(51, "x")),
            refusal("SELECT 1 +; CREATE FUNCTION f() RETURNS INT AS '1' x; SELECT 1 x y"));
        assertEquals(lines(at(49, "x")), refusal("SELECT 1; CREATE FUNCTION f() RETURNS INT AS '1' x; SELECT 1 x y"));
        assertEquals(lines(at(51, "y")), refusal("CREATE FUNCTION f() RETURNS INT AS '1'; SELECT 1 x y"));
    }

    @Test
    public void anOpeningParenthesisRefusedAfterACompleteStatementEndsTheReport() {
        assertEquals(lines(at(14, "(")), refusal("DELETE FROM t ('x'); SELECT 1 x y"));
        assertEquals(lines(at(14, "(")), refusal("DELETE FROM t ('x'); SELECT 1; SELECT 1 x y"));
        assertEquals(lines(at(14, "(")), refusal("DELETE FROM t ('x'); SELECT 1 +"));
        assertEquals(lines(at(14, "(")), refusal("DELETE FROM t ('x'); SELECT 1 FROM"));
        assertEquals(lines(at(14, "(")), refusal("DELETE FROM t (1); SELECT 1 x y"));
        assertEquals(lines(at(18, "(")), refusal("USE SCHEMA PUBLIC ('x'); SELECT 1 x y"));
        assertEquals(lines(at(15, "(")), refusal("USE DATABASE d ('x'); SELECT 1 x y"));
        assertEquals(lines(at(11, "(")), refusal("USE ROLE r ('x'); SELECT 1 x y"));
        assertEquals(lines(at(16, "(")), refusal("USE WAREHOUSE w ('x'); SELECT 1 x y"));
        assertEquals(lines(at(6, "(")), refusal("USE d ('x'); SELECT 1 x y"));
        assertEquals(lines(at(24, "(")), refusal("USE SECONDARY ROLES ALL ('x'); SELECT 1 x y"));
        assertEquals(lines(at(16, "(")), refusal("SELECT 1 INTO t (1); SELECT 1 x y"));
        assertEquals(lines(at(16, "(")), refusal("SELECT 1 INTO t (1); SELECT 1 FROM"));
        assertEquals(lines(at(16, "(")), refusal("SELECT 1 INTO t ('x'); SELECT 1; SELECT 1 x y"));
        assertEquals(lines(at(17, "(")), refusal("TRUNCATE TABLE t ('x'); SELECT 1 x y"));
        assertEquals(lines(at(15, "(")), refusal("UNDROP TABLE t ('x'); SELECT 1 x y"));
        assertEquals(lines(at(40, "(")), refusal("SELECT 1 FROM t WHERE EXISTS (SELECT 1) ('x'); SELECT 1 x y"));
        assertEquals(lines(at(44, "(")), refusal("SELECT 1 FROM t WHERE NOT EXISTS (SELECT 1) ('x'); SELECT 1 x y"));
        assertEquals(lines(at(24, "(")), refusal("SELECT 1 FROM t LIMIT 1 ('x'); SELECT 1 x y"));
        assertEquals(lines(at(32, "(")), refusal("SELECT 1 FROM t ORDER BY a DESC ('x'); SELECT 1 x y"));
        assertEquals(lines(at(59, "(")),
            refusal("MERGE INTO t USING u ON t.a = u.a WHEN MATCHED THEN DELETE ('x'); SELECT 1 x y"));
        assertEquals(lines(at(7, "(")), refusal("COMMIT ('x'); SELECT 1 x y"));
        assertEquals(lines(at(9, "(")), refusal("ROLLBACK ('x'); SELECT 1 x y"));
        assertEquals(lines(at(18, "(")), refusal("START TRANSACTION ('x'); SELECT 1 x y"));
        assertEquals(lines(at(25, "(")), refusal("BEGIN TRANSACTION NAME n ('x'); SELECT 1 x y"));
        assertEquals(lines(at(9, "(")), refusal("CALL p() ('x'); SELECT 1 x y"));
        assertEquals(lines(at(34, "(")), refusal("GRANT SELECT ON TABLE t TO ROLE r ('x'); SELECT 1 x y"));
        assertEquals(lines(at(23, "(")), refusal("GRANT ROLE r TO ROLE s ('x'); SELECT 1 x y"));
        assertEquals(lines(at(26, "(")), refusal("REVOKE ROLE r FROM ROLE s ('x'); SELECT 1 x y"));
        assertEquals(lines(at(8, "(")), refusal("LIST @s ('x'); SELECT 1 x y"));
        assertEquals(lines(at(26, "(")), refusal("COMMENT ON TABLE t IS 'x' ('y'); SELECT 1 x y"));
        assertEquals(lines(at(31, "(")), refusal("ALTER TABLE t ADD COLUMN c INT ('x'); SELECT 1 x y"));
        assertEquals(lines(at(23, "(")), refusal("CREATE TABLE t (a INT) ('x'); SELECT 1 x y"));
        assertEquals(lines(at(22, "(")), refusal("CREATE TABLE t LIKE u ('x'); SELECT 1 x y"));
        assertEquals(lines(at(23, "(")), refusal("CREATE TABLE t CLONE u ('x'); SELECT 1 x y"));
        assertEquals(lines(at(14, "(")), refusal("CREATE ROLE r ('x'); SELECT 1 x y"));
        assertEquals(lines(at(19, "(")), refusal("CREATE WAREHOUSE w ('x'); SELECT 1 x y"));
        assertEquals(lines(at(18, "(")), refusal("CREATE SEQUENCE s ('x'); SELECT 1 x y"));
        assertEquals(lines(at(15, "(")), refusal("CREATE STAGE s ('x'); SELECT 1 x y"));
        assertEquals(lines(at(14, "(")), refusal("CREATE USER u ('x'); SELECT 1 x y"));
        assertEquals(lines(at(27, "(")), refusal("CREATE OR REPLACE SCHEMA s ('x'); SELECT 1 x y"));
        assertEquals(lines(at(26, "(")), refusal("CREATE TRANSIENT SCHEMA s ('x'); SELECT 1 x y"));
        assertEquals(lines(at(24, "(")), refusal("CREATE SCHEMA s CLONE u ('x'); SELECT 1 x y"));
        assertEquals(lines(at(21, "(")), refusal("SHOW TABLES LIKE 'a' ('x'); SELECT 1 x y"));
        assertEquals(lines(at(20, "(")), refusal("SHOW TABLES HISTORY ('x'); SELECT 1 x y"));
        assertEquals(lines(at(24, "(")), refusal("SHOW MATERIALIZED VIEWS ('x'); SELECT 1 x y"));
        assertEquals(lines(at(21, "(")), refusal("DROP TABLE t CASCADE ('x'); SELECT 1 x y"));
        assertEquals(lines(at(22, "(")), refusal("DROP TABLE t RESTRICT ('x'); SELECT 1 x y"));
        assertEquals(lines(at(21, "(")), refusal("DROP FUNCTION f(INT) ('x'); SELECT 1 x y"));
        assertEquals(lines(at(10, ";"), at(26, "(")), refusal("SELECT 1 +; DELETE FROM t ('x'); SELECT 1 x y"));
        assertEquals(lines(at(28, "(")), refusal("SELECT 1; USE SCHEMA PUBLIC ('x'); SELECT 1 x y"));
    }

    @Test
    public void anOpeningParenthesisAfterATestAValuesRowOrAPlainDatabasesNameReadsOn() {
        assertEquals(lines(at(34, "("), at(52, "y")), refusal("SELECT 1 FROM t WHERE a IN (1, 2) ('x'); SELECT 1 x y"));
        assertEquals(lines(at(35, "("), at(53, "y")),
            refusal("SELECT 1 FROM t WHERE a NOT IN (1) ('x'); SELECT 1 x y"));
        assertEquals(lines(at(38, "("), at(56, "y")),
            refusal("SELECT 1 FROM t WHERE a IN (SELECT 1) ('x'); SELECT 1 x y"));
        assertEquals(lines(at(41, "("), at(59, "y")),
            refusal("SELECT 1 FROM t WHERE (a, b) IN ((1, 2)) ('x'); SELECT 1 x y"));
        assertEquals(lines(at(32, "("), at(50, "y")), refusal("SELECT 1 FROM t WHERE a IS NULL ('x'); SELECT 1 x y"));
        assertEquals(lines(at(36, "("), at(54, "y")),
            refusal("SELECT 1 FROM t WHERE a IS NOT NULL ('x'); SELECT 1 x y"));
        assertEquals(lines(at(45, "("), at(63, "y")),
            refusal("SELECT 1 FROM t WHERE a IN (1) AND b IS NULL ('x'); SELECT 1 x y"));
        assertEquals(lines(at(53, "("), at(71, "y")),
            refusal("SELECT 1 FROM t WHERE EXISTS (SELECT 1) AND a IN (1) ('x'); SELECT 1 x y"));
        assertEquals(lines(at(43, "("), at(61, "y")),
            refusal("SELECT 1 FROM t GROUP BY a HAVING a IN (1) ('x'); SELECT 1 x y"));
        assertEquals(lines(at(33, "("), at(51, "y")), refusal("SELECT 1 FROM t QUALIFY a IN (1) ('x'); SELECT 1 x y"));
        assertEquals(lines(at(16, "("), at(41, "y")), refusal("SELECT a IN (1) ('x') FROM t; SELECT 1 x y"));
        assertEquals(lines(at(31, "("), at(59, "y")),
            refusal("SELECT 1 FROM t WHERE a IN (1) ('x'); SELECT 1; SELECT 1 x y"));
        assertEquals(lines(at(31, "("), at(51, "<EOF>")),
            refusal("SELECT 1 FROM t WHERE a IN (1) ('x'); SELECT 1 FROM"));
        assertEquals(lines(at(34, "("), at(52, "y")), refusal("UPDATE t SET a = 1 WHERE b IN (1) ('x'); SELECT 1 x y"));
        assertEquals(lines(at(35, "("), at(53, "y")),
            refusal("UPDATE t SET a = 1 WHERE b IS NULL ('x'); SELECT 1 x y"));
        assertEquals(lines(at(29, "("), at(47, "y")), refusal("DELETE FROM t WHERE a IN (1) ('x'); SELECT 1 x y"));
        assertEquals(lines(at(30, "("), at(48, "y")), refusal("DELETE FROM t WHERE b IS NULL ('x'); SELECT 1 x y"));
        assertEquals(lines(at(25, "("), at(43, "y")), refusal("INSERT INTO t VALUES (1) ('x'); SELECT 1 x y"));
        assertEquals(lines(at(30, "("), at(48, "y")), refusal("INSERT INTO t VALUES (1), (2) ('x'); SELECT 1 x y"));
        assertEquals(lines(at(29, "("), at(47, "y")), refusal("INSERT INTO t (a) VALUES (1) ('x'); SELECT 1 x y"));
        assertEquals(lines(at(12, "("), at(30, "y")), refusal("SHOW TABLES ('x'); SELECT 1 x y"));
        assertEquals(lines(at(18, "("), at(36, "y")), refusal("SHOW TERSE TABLES ('x'); SELECT 1 x y"));
        assertEquals(lines(at(11, "("), at(29, "y")), refusal("BEGIN WORK ('x'); SELECT 1 x y"));
        assertEquals(lines(at(18, "("), at(36, "y")), refusal("BEGIN TRANSACTION ('x'); SELECT 1 x y"));
        assertEquals(lines(at(18, "("), at(36, "y")), refusal("CREATE DATABASE d ('x'); SELECT 1 x y"));
        assertEquals(lines(at(16, "("), at(34, "y")), refusal("CREATE SCHEMA s ('x'); SELECT 1 x y"));
        assertEquals(lines(at(30, "("), at(48, "y")), refusal("CREATE SCHEMA IF NOT EXISTS s ('x'); SELECT 1 x y"));
        assertEquals(lines(at(14, "("), at(33, "y")), refusal("DROP TABLE t (('a')); SELECT 1 x y"));
        assertEquals(lines(at(14, "'x'"), at(31, "y")), refusal("DROP TABLE t ('x'); SELECT 1 x y"));
        assertEquals(lines(at(17, "'x'"), at(34, "y")), refusal("DROP SEQUENCE s ('x'); SELECT 1 x y"));
        assertEquals(lines(at(18, "'x'"), at(35, "y")), refusal("DESCRIBE TABLE t ('x'); SELECT 1 x y"));
    }

    @Test
    public void aRunInReadsOnStraightAfterADropsNameButNotAStagesOrAfterItsBehaviour() {
        assertEquals(lines(at(23, "x"), at(37, "y")), refusal("DROP TABLE IF EXISTS t x; SELECT 1 x y"));
        assertEquals(lines(at(15, "x"), at(29, "y")), refusal("DROP TABLE s.t x; SELECT 1 x y"));
        assertEquals(lines(at(15, "x"), at(29, "y")), refusal("DROP TABLE \"t\" x; SELECT 1 x y"));
        assertEquals(lines(at(27, "x"), at(41, "y")), refusal("DROP TABLE IDENTIFIER('t') x; SELECT 1 x y"));
        assertEquals(lines(at(12, "x"), at(26, "y")), refusal("DROP VIEW v x; SELECT 1 x y"));
        assertEquals(lines(at(14, "x"), at(28, "y")), refusal("DROP SCHEMA s x; SELECT 1 x y"));
        assertEquals(lines(at(16, "x"), at(30, "y")), refusal("DROP DATABASE d x; SELECT 1 x y"));
        assertEquals(lines(at(12, "x"), at(26, "y")), refusal("DROP ROLE r x; SELECT 1 x y"));
        assertEquals(lines(at(12, "x"), at(26, "y")), refusal("DROP USER u x; SELECT 1 x y"));
        assertEquals(lines(at(16, "x"), at(30, "y")), refusal("DROP SEQUENCE s x; SELECT 1 x y"));
        assertEquals(lines(at(14, "x"), at(28, "y")), refusal("DROP STREAM s x; SELECT 1 x y"));
        assertEquals(lines(at(12, "x"), at(26, "y")), refusal("DROP TASK s x; SELECT 1 x y"));
        assertEquals(lines(at(17, "x"), at(31, "y")), refusal("DROP WAREHOUSE s x; SELECT 1 x y"));
        assertEquals(lines(at(19, "x"), at(33, "y")), refusal("DROP FILE FORMAT s x; SELECT 1 x y"));
        assertEquals(lines(at(12, "x"), at(26, "y")), refusal("DROP PIPE s x; SELECT 1 x y"));
        assertEquals(lines(at(25, "x"), at(39, "y")), refusal("DROP MATERIALIZED VIEW v x; SELECT 1 x y"));
        assertEquals(lines(at(22, "x"), at(36, "y")), refusal("DROP EXTERNAL TABLE t x; SELECT 1 x y"));
        assertEquals(lines(at(21, "x"), at(35, "y")), refusal("DROP DYNAMIC TABLE t x; SELECT 1 x y"));
        assertEquals(lines(at(11, "x"), at(25, "y")), refusal("DROP TAG g x; SELECT 1 x y"));
        assertEquals(lines(at(22, "x"), at(36, "y")), refusal("DROP MASKING POLICY m x; SELECT 1 x y"));
        assertEquals(lines(at(22, "x"), at(36, "y")), refusal("DROP NETWORK POLICY n x; SELECT 1 x y"));
        assertEquals(lines(at(14, "x"), at(28, "y")), refusal("DROP SECRET s x; SELECT 1 x y"));
        assertEquals(lines(at(13, "x"), at(27, "y")), refusal("DROP ALERT a x; SELECT 1 x y"));
        assertEquals(lines(at(21, "x"), at(35, "y")), refusal("DROP DATABASE ROLE r x; SELECT 1 x y"));
        assertEquals(lines(at(19, "x"), at(33, "y")), refusal("DROP INTEGRATION i x; SELECT 1 x y"));
        assertEquals(lines(at(13, "1"), at(27, "y")), refusal("DROP TABLE t 1; SELECT 1 x y"));
        assertEquals(lines(at(13, "'q'"), at(29, "y")), refusal("DROP TABLE t 'q'; SELECT 1 x y"));
        assertEquals(lines(at(13, "COMMENT"), at(33, "y")), refusal("DROP TABLE t COMMENT; SELECT 1 x y"));
        assertEquals(lines(at(13, "x"), at(29, "y")), refusal("DROP TABLE t x y; SELECT 1 x y"));
        assertEquals(lines(at(13, "x"), at(26, "<EOF>")), refusal("DROP TABLE t x; SELECT 1 +"));
        assertEquals(lines(at(23, "x"), at(37, "y")), refusal("SELECT 1; DROP TABLE t x; SELECT 1 x y"));
        assertEquals(lines(at(10, ";"), at(25, "x"), at(39, "y")), refusal("SELECT 1 +; DROP TABLE t x; SELECT 1 x y"));
        assertEquals(lines(at(21, "x")), refusal("DROP TABLE t CASCADE x; SELECT 1 x y"));
        assertEquals(lines(at(22, "x")), refusal("DROP TABLE t RESTRICT x; SELECT 1 x y"));
        assertEquals(lines(at(22, "x")), refusal("DROP SCHEMA s CASCADE x; SELECT 1 x y"));
        assertEquals(lines(at(21, "COMMENT")), refusal("DROP TABLE t CASCADE COMMENT; SELECT 1 x y"));
        assertEquals(lines(at(21, "x")), refusal("DROP TABLE t CASCADE x; SELECT 1 FROM"));
        assertEquals(lines(at(21, "x")), refusal("DROP FUNCTION f(INT) x; SELECT 1 x y"));
        assertEquals(lines(at(22, "x")), refusal("DROP PROCEDURE p(INT) x; SELECT 1 x y"));
        assertEquals(lines(at(19, "x")), refusal("DROP TABLE t (INT) x; SELECT 1 x y"));
        assertEquals(lines(at(13, "x")), refusal("DROP STAGE s x; SELECT 1 x y"));
        assertEquals(lines(at(23, "x")), refusal("DROP STAGE IF EXISTS s x; SELECT 1 x y"));
        assertEquals(lines(at(19, "x")), refusal("DROP STAGE db.sc.s x; SELECT 1 x y"));
        assertEquals(lines(at(13, "x")), refusal("DROP STAGE s x; SELECT 1 +"));
        assertEquals(lines(at(10, ";"), at(33, "x")), refusal("SELECT 1 +; DROP TABLE t CASCADE x; SELECT 1 x y"));
    }

    @Test
    public void aRunInReadsOnStraightAfterAShowsOneWordKindAlone() {
        assertEquals(lines(at(18, "x"), at(32, "y")), refusal("SHOW TERSE TABLES x; SELECT 1 x y"));
        assertEquals(lines(at(15, "x"), at(29, "y")), refusal("SHOW DATABASES x; SELECT 1 x y"));
        assertEquals(lines(at(13, "x"), at(27, "y")), refusal("SHOW SCHEMAS x; SELECT 1 x y"));
        assertEquals(lines(at(12, "x"), at(26, "y")), refusal("SHOW GRANTS x; SELECT 1 x y"));
        assertEquals(lines(at(16, "x"), at(30, "y")), refusal("SHOW PARAMETERS x; SELECT 1 x y"));
        assertEquals(lines(at(16, "x"), at(30, "y")), refusal("SHOW WAREHOUSES x; SELECT 1 x y"));
        assertEquals(lines(at(11, "x"), at(25, "y")), refusal("SHOW USERS x; SELECT 1 x y"));
        assertEquals(lines(at(12, "x"), at(26, "y")), refusal("SHOW STAGES x; SELECT 1 x y"));
        assertEquals(lines(at(11, "x"), at(25, "y")), refusal("SHOW ROLES x; SELECT 1 x y"));
        assertEquals(lines(at(15, "x"), at(29, "y")), refusal("SHOW VARIABLES x; SELECT 1 x y"));
        assertEquals(lines(at(18, "x"), at(32, "y")), refusal("SHOW INTEGRATIONS x; SELECT 1 x y"));
        assertEquals(lines(at(11, "x"), at(25, "y")), refusal("SHOW LOCKS x; SELECT 1 x y"));
        assertEquals(lines(at(15, "x"), at(29, "y")), refusal("SHOW FUNCTIONS x; SELECT 1 x y"));
        assertEquals(lines(at(11, "x"), at(25, "y")), refusal("SHOW TASKS x; SELECT 1 x y"));
        assertEquals(lines(at(13, "x"), at(27, "y")), refusal("SHOW OBJECTS x; SELECT 1 x y"));
        assertEquals(lines(at(13, "x"), at(27, "y")), refusal("SHOW COLUMNS x; SELECT 1 x y"));
        assertEquals(lines(at(11, "x"), at(25, "y")), refusal("SHOW VIEWS x; SELECT 1 x y"));
        assertEquals(lines(at(12, "x"), at(26, "y")), refusal("SHOW ALERTS x; SELECT 1 x y"));
        assertEquals(lines(at(17, "x"), at(31, "y")), refusal("SHOW TERSE USERS x; SELECT 1 x y"));
        assertEquals(lines(at(12, "1"), at(26, "y")), refusal("SHOW TABLES 1; SELECT 1 x y"));
        assertEquals(lines(at(12, "'a'"), at(28, "y")), refusal("SHOW TABLES 'a'; SELECT 1 x y"));
        assertEquals(lines(at(12, "x"), at(28, "y")), refusal("SHOW TABLES x y; SELECT 1 x y"));
        assertEquals(lines(at(12, "x"), at(36, "y")), refusal("SHOW TABLES x; SELECT 1; SELECT 1 x y"));
        assertEquals(lines(at(21, "x")), refusal("SHOW TABLES LIKE 'a' x; SELECT 1 x y"));
        assertEquals(lines(at(21, "COMMENT")), refusal("SHOW TABLES LIKE 'a' COMMENT; SELECT 1 x y"));
        assertEquals(lines(at(24, "x")), refusal("SHOW TABLES IN SCHEMA s x; SELECT 1 x y"));
        assertEquals(lines(at(28, "x")), refusal("SHOW TABLES STARTS WITH 'a' x; SELECT 1 x y"));
        assertEquals(lines(at(20, "x")), refusal("SHOW TABLES LIMIT 1 x; SELECT 1 x y"));
        assertEquals(lines(at(20, "x")), refusal("SHOW TABLES HISTORY x; SELECT 1 x y"));
        assertEquals(lines(at(22, "x")), refusal("SHOW SCHEMAS LIKE 'a' x; SELECT 1 x y"));
        assertEquals(lines(at(33, "x")), refusal("SHOW TABLES LIKE 'a' IN SCHEMA s x; SELECT 1 x y"));
        assertEquals(lines(at(22, "x")), refusal("SHOW GRANTS TO ROLE r x; SELECT 1 x y"));
        assertEquals(lines(at(25, "x")), refusal("SHOW PARAMETERS LIKE 'a' x; SELECT 1 x y"));
        assertEquals(lines(at(24, "x")), refusal("SHOW COLUMNS IN TABLE t x; SELECT 1 x y"));
        assertEquals(lines(at(29, "x")), refusal("SHOW TABLES LIKE 'a' LIMIT 1 x; SELECT 1 x y"));
        assertEquals(lines(at(21, "x")), refusal("SHOW TABLES LIKE 'a' x y; SELECT 1 x y"));
        assertEquals(lines(at(21, "x")), refusal("SHOW TABLES LIKE 'a' x; SELECT 1 FROM"));
        assertEquals(lines(at(21, "x")), refusal("SHOW TABLES LIKE 'a' x; SELECT 1; SELECT 1 x y"));
        assertEquals(lines(at(20, "x")), refusal("SHOW USER FUNCTIONS x; SELECT 1 x y"));
        assertEquals(lines(at(23, "x")), refusal("SHOW BUILTIN FUNCTIONS x; SELECT 1 x y"));
        assertEquals(lines(at(24, "x")), refusal("SHOW MATERIALIZED VIEWS x; SELECT 1 x y"));
        assertEquals(lines(at(30, "x")), refusal("SHOW TERSE MATERIALIZED VIEWS x; SELECT 1 x y"));
        assertEquals(lines(at(18, "x")), refusal("SHOW PRIMARY KEYS x; SELECT 1 x y"));
        assertEquals(lines(at(18, "x")), refusal("SHOW FILE FORMATS x; SELECT 1 x y"));
        assertEquals(lines(at(22, "x")), refusal("SHOW MASKING POLICIES x; SELECT 1 x y"));
        assertEquals(lines(at(19, "x")), refusal("SHOW COMPUTE POOLS x; SELECT 1 x y"));
        assertEquals(lines(at(21, "x")), refusal("SHOW EXTERNAL TABLES x; SELECT 1 x y"));
        assertEquals(lines(at(20, "x")), refusal("SHOW DYNAMIC TABLES x; SELECT 1 x y"));
        assertEquals(lines(at(7, "y")), refusal("SHOW x y; SELECT 1 x y"));
        assertEquals(lines(at(10, ";"), at(33, "x")), refusal("SELECT 1 +; SHOW TABLES LIKE 'a' x; SELECT 1 x y"));
    }

    @Test
    public void aRunInReadsOnStraightAfterAnUnsetsOneNameOrABeginTransaction() {
        assertEquals(lines(at(8, "1"), at(22, "y")), refusal("UNSET x 1; SELECT 1 x y"));
        assertEquals(lines(at(10, "y"), at(24, "y")), refusal("UNSET \"x\" y; SELECT 1 x y"));
        assertEquals(lines(at(8, "y"), at(24, "y")), refusal("UNSET x y z; SELECT 1 x y"));
        assertEquals(lines(at(8, "("), at(26, "y")), refusal("UNSET x ('y'); SELECT 1 x y"));
        assertEquals(lines(at(8, "'q'"), at(24, "y")), refusal("UNSET x 'q'; SELECT 1 x y"));
        assertEquals(lines(at(8, "y"), at(21, "<EOF>")), refusal("UNSET x y; SELECT 1 +"));
        assertEquals(lines(at(10, "y")), refusal("UNSET (x) y; SELECT 1 x y"));
        assertEquals(lines(at(13, "z")), refusal("UNSET (x, y) z; SELECT 1 x y"));
        assertEquals(lines(at(18, "x"), at(32, "y")), refusal("BEGIN TRANSACTION x; SELECT 1 x y"));
    }

    @Test
    public void aCommaRunInAtTheFirstFaultEndsTheReportButAfterADropsNameOrAShowsKind() {
        assertEquals(lines(at(7, ",")), refusal("UNSET x, y; SELECT 1 x y"));
        assertEquals(lines(at(7, ",")), refusal("UNSET x, y z; SELECT 1 x y"));
        assertEquals(lines(at(9, ",")), refusal("UNSET \"x\", y; SELECT 1 x y"));
        assertEquals(lines(at(12, ",")), refusal("USE SCHEMA s, t; SELECT 1 x y"));
        assertEquals(lines(at(10, ",")), refusal("USE ROLE r, s; SELECT 1 x y"));
        assertEquals(lines(at(13, ",")), refusal("DELETE FROM t, u; SELECT 1 x y"));
        assertEquals(lines(at(16, ",")), refusal("TRUNCATE TABLE t, u; SELECT 1 x y"));
        assertEquals(lines(at(27, ",")), refusal("SELECT 1 FROM t WHERE a = 1, b; SELECT 1 x y"));
        assertEquals(lines(at(6, ",")), refusal("COMMIT, x; SELECT 1 x y"));
        assertEquals(lines(at(8, ",")), refusal("CALL p(), x; SELECT 1 x y"));
        assertEquals(lines(at(22, ",")), refusal("SELECT 1; USE SCHEMA s, t; SELECT 1 x y"));
        assertEquals(lines(at(23, ",")), refusal("SHOW MATERIALIZED VIEWS, x; SELECT 1 x y"));
        assertEquals(lines(at(12, ",")), refusal("DROP STAGE s, t; SELECT 1 x y"));
        assertEquals(lines(at(20, ",")), refusal("DROP TABLE t CASCADE, u; SELECT 1 x y"));
        assertEquals(lines(at(12, ","), at(28, "y")), refusal("DROP TABLE t, u; SELECT 1 x y"));
        assertEquals(lines(at(11, ","), at(27, "y")), refusal("SHOW TABLES, x; SELECT 1 x y"));
        assertEquals(lines(at(17, ","), at(33, "y")), refusal("SHOW TERSE TABLES, x; SELECT 1 x y"));
    }
}
