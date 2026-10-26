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

package dev.frostlake.jdbc;

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A failed statement reaches the client's driver with the account's vendor code and SQLSTATE of its family
 * (all live-verified): a missing table, view or object 2003 / 42S02, a missing object of any other kind 2003 /
 * 02000, a name already taken 2002 / 42710, a syntax error 1003 / 42000, an invalid identifier 904 / 42000, a
 * division by zero 100051 / 22012, a text no conversion reads 100038 / 22018, 100040, 100035 or 100108 / 22007,
 * a NULL in a NOT NULL column 100072 / 22000, an unknown function 2139 / 02000, an exception a script raised its
 * declared code with P0001 and a statement it failed on that statement's pair, among the rest the table holds.
 */
public class StatementErrorCodeTest extends BaseJdbcTest {

    @Override
    protected void setupTest() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE ec647 (n INT)");
        statement.execute("INSERT INTO ec647 VALUES (1)");
        statement.execute("CREATE OR REPLACE TABLE ec647s (s VARCHAR(2))");
        statement.execute("CREATE OR REPLACE TABLE ec647nn (a INT NOT NULL)");
        statement.execute("INSERT INTO ec647nn VALUES (1)");
        statement.execute("CREATE OR REPLACE TABLE ec647dup (a INT)");
        statement.execute("CREATE OR REPLACE PROCEDURE ec647p() RETURNS INT LANGUAGE SQL AS"
            + " $$ BEGIN RETURN 1/0; END $$");
        statement.execute("CREATE OR REPLACE TABLE ec648 (n INT)");
        statement.execute("INSERT INTO ec648 VALUES (1)");
        statement.execute("CREATE OR REPLACE SEQUENCE ec648seq");
        statement.execute("CREATE OR REPLACE VIEW ec648v AS SELECT 1 AS one");
        statement.execute("CREATE OR REPLACE FUNCTION ec648f() RETURNS INT AS '1'");
    }

    /** Each statement fails with the code and the state beside it, read as {@code "code state"}. */
    private void assertFamilies(final String[][] cases) throws SQLException {
        for (final String[] failing : cases) {
            try (final Statement s = connection.createStatement()) {
                s.execute(failing[0]);
                fail("no failure: " + failing[0]);
            } catch (final SQLException refused) {
                assertEquals(failing[1], refused.getErrorCode() + " " + refused.getSQLState(), failing[0]);
            }
        }
    }

    @Test
    public void commonFamilies() throws SQLException {
        assertFamilies(new String[][] {
            {"SELECT * FROM test_db.no_such_schema_647.t", "2003 02000"},
            {"SELECT * FROM no_such_db_647.s.t", "2003 02000"},
            {"DROP TABLE no_such_table_647", "2003 42S02"},
            {"CREATE TABLE ec647dup (a INT)", "2002 42710"},
            {"SELECT n FROM ec647 a, ec647 b", "2028 42601"},
            {"SELECT 'x'::TIMESTAMP", "100035 22007"},
            {"SELECT 'x'::BOOLEAN", "100037 22018"},
            {"SELECT CAST(1000 AS NUMBER(2,0))", "100046 22003"},
            {"SELECT nosuchfn_647(1)", "2139 02000"},
            {"SELECT PARSE_JSON('{')", "100069 22P02"},
            {"INSERT INTO ec647s VALUES ('abc')", "100078 22000"},
            {"SELECT n, COUNT(*) FROM ec647", "979 42601"},
            {"INSERT INTO ec647 VALUES (1, 2)", "2020 21S01"},
            {"CREATE TABLE ec647t (a NOSUCHTYPE)", "2040 42601"},
            {"EXECUTE IMMEDIATE $$DECLARE e EXCEPTION (-20001, 'boom'); BEGIN RAISE e; END;$$", "-20001 P0001"},
            {"SELECT 1 FROM ec647 ORDER BY 5", "2024 42601"},
            {"SELECT SUBSTR('a')", "938 22023"},
            {"SELECT 'a' ||", "1003 42000"},
            {"SELECT * FROM ec647 WHERE n = 'x'", "100038 22018"},
            {"SELECT 99999999999999999999999999999999999999 * 10", "100046 22003"},
            {"USE SCHEMA test_db.no_such_schema_647", "2043 02000"},
            {"SHOW TABLES IN SCHEMA test_db.no_such_schema_647", "2043 02000"},
            {"DESCRIBE TABLE no_such_table_647", "2003 42S02"},
            {"SELECT $1 FROM @no_such_stage_647", "2003 02000"},
            {"INSERT INTO ec647 VALUES ('x')", "100038 22018"},
            {"UPDATE ec647nn SET a = NULL", "100072 22000"},
            {"MERGE INTO ec647 t USING (SELECT 1 AS n UNION ALL SELECT 1) s ON t.n ="
                + " s.n WHEN MATCHED THEN UPDATE SET t.n = s.n + 1", "100090 42P18"},
            {"SELECT TO_TIME('x')", "100108 22007"},
            {"SELECT TRY_CAST(1 AS INT)", "1065 22023"},
            {"ALTER SESSION SET NO_SUCH_PARAM_647 = 1", "1006 22023"},
            {"SELECT 1 UNION SELECT 1, 2", "1789 42601"},
            {"SELECT SYSTEM$NO_SUCH_647()", "2139 02000"},
            {"CREATE OR REPLACE VIEW ec647v AS SELECT zz FROM ec647", "904 42000"},
            {"SELECT 1 FROM ec647 GROUP BY zz", "904 42000"},
            {"SELECT * FROM TABLE(RESULT_SCAN('01234567-0000-0000-0000-000000000000'))", "709 02000"},
            {"ALTER TABLE ec647 ADD COLUMN n INT", "1430 42601"},
            {"SELECT TO_NUMBER('12a')", "100038 22018"},
            {"SELECT 'x'::DATE", "100040 22007"},
            {"SELECT TO_TIMESTAMP('x')", "100035 22007"},
            {"SELECT 'x'::FLOAT", "100038 22018"},
            {"SELECT 1/0.0", "100051 22012"},
            {"BEGIN SELECT 1/0; END", "100051 22012"},
            {"CALL ec647p()", "100051 22012"},
            {"CREATE OR REPLACE TABLE ec647x AS SELECT 1/0 AS a", "100051 22012"},
            {"SELECT PARSE_JSON('1')::DATE", "100071 22000"},
            {"SELECT ec647.zz FROM ec647", "904 42000"},
            {"INSERT INTO no_such_table_647 VALUES (1)", "2003 42S02"},
            {"SELECT ec647nn.a FROM ec647", "904 42000"},
            {"SELECT TO_BINARY('zz', 'HEX')", "100115 22000"},
            {"DROP VIEW no_such_view_647", "2003 42S02"},
            {"ALTER TABLE no_such_table_647 ADD COLUMN x INT", "2003 42S02"},
            {"SELECT 'a' + 1", "100038 22018"},
            {"SELECT * FROM ec647 SAMPLE (200)", "1817 42601"},
            {"SELECT * FROM ec647 AT(OFFSET => -999999999)", "707 02000"},
            {"SELECT 1 FROM ec647 HAVING zz > 1", "904 42000"},
            {"SELECT CURRENT_DATE() + 'x'", "1044 42P13"},
            {"EXECUTE IMMEDIATE $$BEGIN RAISE STATEMENT_ERROR; END;$$", "-20000 P0001"},
            {"EXECUTE IMMEDIATE $$DECLARE e EXCEPTION; BEGIN RAISE e; END;$$", "-20000 P0001"},
        });
    }

    @Test
    public void furtherFamilies() throws SQLException {
        assertFamilies(new String[][] {
            {"DROP SEQUENCE no_such_seq_648", "2003 02000"},
            {"DROP FUNCTION no_such_fn_648(INT)", "2003 02000"},
            {"DROP PROCEDURE no_such_proc_648()", "2003 02000"},
            {"DROP FILE FORMAT no_such_ff_648", "2003 02000"},
            {"DROP STREAM no_such_stream_648", "2003 02000"},
            {"DROP TASK no_such_task_648", "2003 02000"},
            {"DROP PIPE no_such_pipe_648", "2003 02000"},
            {"DROP MATERIALIZED VIEW no_such_mv_648", "2003 02000"},
            {"DROP STAGE no_such_stage_648", "2003 02000"},
            {"DROP SCHEMA test_db.no_such_schema_648", "2003 02000"},
            {"DROP MASKING POLICY no_such_mp_648", "2003 02000"},
            {"DROP TAG no_such_tag_648", "2003 02000"},
            {"DESCRIBE FUNCTION no_such_fn_648(INT)", "2003 02000"},
            {"SELECT * FROM TABLE(no_such_udtf_648())", "2142 42601"},
            {"SELECT no_such_seq_648.NEXTVAL", "904 42000"},
            {"USE WAREHOUSE no_such_wh_648", "2043 02000"},
            {"USE DATABASE no_such_db_648", "2043 02000"},
            {"ALTER SESSION SET TIMEZONE = 'No/Such'", "1008 22023"},
            {"CREATE SEQUENCE ec648seq", "2002 42710"},
            {"CREATE VIEW ec648v AS SELECT 1 AS one", "2002 42710"},
            {"CREATE FUNCTION ec648f() RETURNS INT AS '1'", "2002 42710"},
            {"SELECT 123.45::NUMBER(4,2)", "100046 22003"},
            {"SELECT 'abc'::VARCHAR(2)", "100078 22000"},
            {"SELECT TO_VARIANT('a')::INT", "100071 22000"},
            {"SELECT ARRAY_CONSTRUCT(1)::INT", "1007 22023"},
            {"SELECT DATEADD('nosuchpart', 1, CURRENT_DATE())", "2151 22023"},
            {"SELECT REGEXP_LIKE('a', '(')", "100048 2201B"},
            {"UPDATE ec648 SET n = 'x'", "100038 22018"},
            {"DELETE FROM ec648 WHERE n = 'x'", "100038 22018"},
            {"TRUNCATE TABLE no_such_table_648", "2003 42S02"},
            {"UNDROP TABLE no_such_table_648", "90025 02000"},
            {"SELECT ABS(1, 2)", "939 22023"},
            {"SELECT TO_DATE('2020-01-01', 'nosuch')", "100097 22007"},
            {"SELECT 1 FROM ec648 GROUP BY 1, 2", "979 42601"},
            {"SELECT n FROM ec648 ORDER BY COUNT(*)", "979 42601"},
            {"CREATE TABLE ec648d (a INT, a INT)", "2025 42S21"},
            {"EXECUTE IMMEDIATE 'SELEC 1'", "1003 42000"},
            {"EXECUTE IMMEDIATE $$BEGIN LET x INT := 'a'; RETURN x; END$$", "100038 22018"},
            {"BEGIN RETURN 1/0; END", "100051 22012"},
            {"SELECT PARSE_XML('<a>')", "100100 22P02"},
            {"SELECT BASE64_DECODE_STRING('***')", "100114 22000"},
            {"SELECT HEX_DECODE_STRING('zz')", "100115 22000"},
            {"SELECT TRUE + 1", "1044 42P13"},
            {"SELECT 1 WHERE 'x'", "2034 22000"},
            {"SELECT ROW_NUMBER() OVER () FROM ec648", "2061 42601"},
            {"CREATE TABLE ec648m AS SELECT 1", "2022 42601"},
            {"SELECT '1000'::NUMBER(2,0)", "100039 22003"},
            {"ALTER TABLE ec648 DROP COLUMN zz", "1431 42601"},
            {"ALTER TABLE ec648 DROP COLUMN n", "1432 42601"},
            {"ALTER TABLE ec648 ALTER COLUMN n SET DATA TYPE VARCHAR", "2108 22000"},
            {"SHOW COLUMNS IN TABLE no_such_table_648", "2003 42S02"},
            {"SELECT GET_DDL('SCHEMA', 'no_such_schema_648')", "2003 02000"},
            {"INSERT INTO ec648v VALUES (1)", "1759 42601"},
            {"BEGIN SELECT * FROM no_such_table_648; END", "2003 42S02"},
            {"BEGIN LET x := 1; SELECT * FROM no_such_table_648; RETURN x; END", "2003 42S02"},
            {"EXECUTE IMMEDIATE 'SELECT * FROM no_such_table_648'", "2003 42S02"},
            {"SELECT MAX(MAX(n)) FROM ec648", "2082 42601"},
            {"SELECT CURRENT_DATE()::INT", "1007 22023"},
            {"SELECT 'a'::BINARY", "100115 22000"},
            {"SELECT TO_TIMESTAMP('2020-01-01', 'nosuch')", "100096 22007"},
            {"ALTER TABLE no_such_table_648 RENAME TO ec648z", "2003 42S02"},
            {"COMMENT ON TABLE no_such_table_648 IS 'x'", "2003 42S02"},
            {"SELECT * FROM ec648 x JOIN ec648 y USING (zz)", "2004 42601"},
            {"INSERT INTO ec648 (zz) VALUES (1)", "904 42000"},
            {"SELECT GET_DDL('FUNCTION', 'no_such_fn_648()')", "2001 02000"},
            {"SELECT SYSTEM$TYPEOF()", "938 22023"},
        });
    }
}
