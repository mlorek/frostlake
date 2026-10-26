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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A VARIANT number an element or a path reads straight off a stored table's column converts to a DATE (days
 * from the epoch, half away from zero), a TIME and a TIMESTAMP_NTZ (seconds from the epoch, in UTC), through the
 * derived relations, CTEs and views that project the column or the element as it is, where a constant's number, a
 * VARIANT column read whole, and an element of a computed value - a grouping's, a DISTINCT's, a sort's, a set
 * operation's, an expression's - are refused for a DATE and a TIME (all live-verified).
 */
public class StoredElementTemporalsTest extends MeasuredCellsTestSupport {

    /** Every method sets the session's time zone; it goes back to the account's after each. */
    @AfterEach
    public void unsetTimezone() {
        engine.execute("ALTER SESSION UNSET TIMEZONE");
    }

    @Test
    public void anElementOfAStoredColumnConvertsByItsOwnRule() {
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        assertAnswer("Table VN successfully created.", "CREATE OR REPLACE TABLE vn (i INT, v VARIANT)");
        assertAnswer("14",
            "INSERT INTO vn SELECT column1, PARSE_JSON(column2) FROM VALUES (0, '1'), (1, '-1'), (2, '1.5'), (3, "
                + "'86400'), (4, '1000000000'), (5, '31535999999'), (6, '31536000000'), (7, '1000000000000'), (8, "
                + "'31536000000000'), (9, '1000000000000000'), (10, '31536000000000000'), (11, "
                + "'1000000000000000000'), (12, '-86400.25'), (13, '0.001')");
        assertAnswer("Table VA successfully created.",
            "CREATE OR REPLACE TABLE va (i INT, a ARRAY, o OBJECT, v VARIANT)");
        assertAnswer("10",
            "INSERT INTO va SELECT column1, ARRAY_CONSTRUCT(PARSE_JSON(column2)), OBJECT_CONSTRUCT('n', "
                + "PARSE_JSON(column2)), PARSE_JSON('[' || column2 || ']') FROM VALUES (0, '1'), (1, '-1'), (2, "
                + "'1.5'), (3, '2.5'), (4, '0.5'), (5, '-1.5'), (6, '365'), (7, '86400'), (8, '100000'), (9, "
                + "'1000000000')");
        assertAnswer("0|19700102 ;; 1|19691231 ;; 2|19700103 ;; 3|19700104 ;; 4|19700102 ;; 5|19691230 ;; 6|19710101 "
                + ";; 7|22060723 ;; 8|22431017",
            "SELECT i, TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM va WHERE i <= 8 ORDER BY i");
        assertAnswer("0|19700101T000001.000 ;; 1|19691231T235959.000 ;; 2|19700101T000001.500 ;; 3|"
                + "19700101T000002.500 ;; 4|19700101T000000.500 ;; 5|19691231T235958.500 ;; 6|19700101T000605.000 ;; "
                + "7|19700102T000000.000 ;; 8|19700102T034640.000 ;; 9|20010909T014640.000",
            "SELECT i, TO_VARCHAR(a[0]::TIMESTAMP_NTZ, 'YYYYMMDD\"T\"HH24MISS.FF3') FROM va ORDER BY i");
        assertAnswer("0|000001.000 ;; 1|235959.000 ;; 2|000001.500 ;; 3|000002.500 ;; 4|000000.500 ;; 5|235958.500 ;; "
                + "6|000605.000 ;; 7|000000.000 ;; 8|034640.000 ;; 9|014640.000",
            "SELECT i, TO_VARCHAR(a[0]::TIME, 'HH24MISS.FF3') FROM va ORDER BY i");
        assertAnswer("0|19700102 ;; 1|19691231 ;; 2|19700103 ;; 3|19700104 ;; 4|19700102 ;; 5|19691230 ;; 6|19710101 "
                + ";; 7|22060723 ;; 8|22431017",
            "SELECT i, TO_VARCHAR(o:n::DATE, 'YYYYMMDD') FROM va WHERE i <= 8 ORDER BY i");
        assertAnswer("0|19700101T000001.000 ;; 1|19691231T235959.000 ;; 2|19700101T000001.500 ;; 3|"
                + "19700101T000002.500 ;; 4|19700101T000000.500 ;; 5|19691231T235958.500 ;; 6|19700101T000605.000 ;; "
                + "7|19700102T000000.000 ;; 8|19700102T034640.000 ;; 9|20010909T014640.000",
            "SELECT i, TO_VARCHAR(o:n::TIMESTAMP_NTZ, 'YYYYMMDD\"T\"HH24MISS.FF3') FROM va ORDER BY i");
        assertAnswer("0|000001.000 ;; 1|235959.000 ;; 2|000001.500 ;; 3|000002.500 ;; 4|000000.500 ;; 5|235958.500 ;; "
                + "6|000605.000 ;; 7|000000.000 ;; 8|034640.000 ;; 9|014640.000",
            "SELECT i, TO_VARCHAR(o:n::TIME, 'HH24MISS.FF3') FROM va ORDER BY i");
        assertAnswer("0|19700102 ;; 1|19691231 ;; 2|19700103 ;; 3|19700104 ;; 4|19700102 ;; 5|19691230 ;; 6|19710101 "
                + ";; 7|22060723 ;; 8|22431017",
            "SELECT i, TO_VARCHAR(v[0]::DATE, 'YYYYMMDD') FROM va WHERE i <= 8 ORDER BY i");
        assertAnswer("0|19700101T000001.000 ;; 1|19691231T235959.000 ;; 2|19700101T000001.500 ;; 3|"
                + "19700101T000002.500 ;; 4|19700101T000000.500 ;; 5|19691231T235958.500 ;; 6|19700101T000605.000 ;; "
                + "7|19700102T000000.000 ;; 8|19700102T034640.000 ;; 9|20010909T014640.000",
            "SELECT i, TO_VARCHAR(v[0]::TIMESTAMP_NTZ, 'YYYYMMDD\"T\"HH24MISS.FF3') FROM va ORDER BY i");
        assertAnswer("0|000001.000 ;; 1|235959.000 ;; 2|000001.500 ;; 3|000002.500 ;; 4|000000.500 ;; 5|235958.500 ;; "
                + "6|000605.000 ;; 7|000000.000 ;; 8|034640.000 ;; 9|014640.000",
            "SELECT i, TO_VARCHAR(v[0]::TIME, 'HH24MISS.FF3') FROM va ORDER BY i");
        assertAnswer("9|27398770103", "SELECT i, TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM va WHERE i = 9");
        assertAnswer("0|19691231T160001.000 -0800|19691231T160001.000 -0800 ;; 7|19700101T160000.000 -0800|"
                + "19700101T160000.000 -0800",
            "SELECT i, TO_VARCHAR(a[0]::TIMESTAMP_LTZ, 'YYYYMMDD\"T\"HH24MISS.FF3 TZHTZM'), "
                + "TO_VARCHAR(a[0]::TIMESTAMP_TZ, 'YYYYMMDD\"T\"HH24MISS.FF3 TZHTZM') FROM va WHERE i IN (0, 7) "
                + "ORDER BY i");
    }

    @Test
    public void aConstantsElementStaysRefused() {
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        assertAnswer("Table VN successfully created.", "CREATE OR REPLACE TABLE vn (i INT, v VARIANT)");
        assertAnswer("14",
            "INSERT INTO vn SELECT column1, PARSE_JSON(column2) FROM VALUES (0, '1'), (1, '-1'), (2, '1.5'), (3, "
                + "'86400'), (4, '1000000000'), (5, '31535999999'), (6, '31536000000'), (7, '1000000000000'), (8, "
                + "'31536000000000'), (9, '1000000000000000'), (10, '31536000000000000'), (11, "
                + "'1000000000000000000'), (12, '-86400.25'), (13, '0.001')");
        assertAnswer("Table VA successfully created.",
            "CREATE OR REPLACE TABLE va (i INT, a ARRAY, o OBJECT, v VARIANT)");
        assertAnswer("10",
            "INSERT INTO va SELECT column1, ARRAY_CONSTRUCT(PARSE_JSON(column2)), OBJECT_CONSTRUCT('n', "
                + "PARSE_JSON(column2)), PARSE_JSON('[' || column2 || ']') FROM VALUES (0, '1'), (1, '-1'), (2, "
                + "'1.5'), (3, '2.5'), (4, '0.5'), (5, '-1.5'), (6, '365'), (7, '86400'), (8, '100000'), (9, "
                + "'1000000000')");
        assertAnswer("19700101T160000.000",
            "SELECT TO_VARCHAR([86400][0]::TIMESTAMP_NTZ, 'YYYYMMDD\"T\"HH24MISS.FF3')");
        assertAnswer("19700101T160000.000 -0800",
            "SELECT TO_VARCHAR([86400][0]::TIMESTAMP_LTZ, 'YYYYMMDD\"T\"HH24MISS.FF3 TZHTZM')");
        assertAnswer("19700101T160000.000 -0800",
            "SELECT TO_VARCHAR([86400][0]::TIMESTAMP_TZ, 'YYYYMMDD\"T\"HH24MISS.FF3 TZHTZM')");
        assertAnswer("19700101T160000.000",
            "SELECT TO_VARCHAR(PARSE_JSON('86400')::TIMESTAMP_NTZ, 'YYYYMMDD\"T\"HH24MISS.FF3')");
        assertAnswer("336580926T184640.000",
            "SELECT TO_VARCHAR(PARSE_JSON('1000000000000')::TIMESTAMP_NTZ, 'YYYYMMDD\"T\"HH24MISS.FF3')");
        assertAnswer("19691231T160001.500", "SELECT TO_VARCHAR([1.5][0]::TIMESTAMP_NTZ, 'YYYYMMDD\"T\"HH24MISS.FF3')");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE", "SELECT TO_VARCHAR([1][0]::DATE, 'YYYYMMDD')");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(ARRAY_CONSTRUCT(1)[0]::DATE, 'YYYYMMDD')");
        assertAnswer("336580926T184640.000",
            "SELECT TO_VARCHAR(TO_TIMESTAMP_NTZ(v), 'YYYYMMDD\"T\"HH24MISS.FF3') FROM vn WHERE i = 7");
        assertAnswer("336580927T014640.000",
            "SELECT TO_VARCHAR(TO_TIMESTAMP_NTZ(v::NUMBER), 'YYYYMMDD\"T\"HH24MISS.FF3') FROM vn WHERE i = 7");
        assertAnswer("19700102", "SELECT TO_VARCHAR(TO_DATE(a[0]), 'YYYYMMDD') FROM va WHERE i = 0");
        assertAnswer("19700102", "SELECT TO_VARCHAR(GET(a, 0)::DATE, 'YYYYMMDD') FROM va WHERE i = 0");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM va WHERE i = 0 UNION ALL SELECT TO_VARCHAR([1][0]::DATE, "
                + "'YYYYMMDD')");
        assertAnswer("19700102", "SELECT TO_VARCHAR(x::DATE, 'YYYYMMDD') FROM (SELECT a[0] AS x FROM va WHERE i = 0)");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM (SELECT [1] AS a)");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'V'",
            "SELECT SYSTEM$TYPEOF(a[0]), SYSTEM$TYPEOF(v) FROM va, vn WHERE va.i = 0 AND vn.i = 0");
    }

    @Test
    public void anElementOfAMixedArrayConverts() {
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        assertAnswer("Table VN successfully created.", "CREATE OR REPLACE TABLE vn (i INT, v VARIANT)");
        assertAnswer("16",
            "INSERT INTO vn SELECT column1, PARSE_JSON(column2) FROM VALUES (0, '1'), (1, '-1'), (2, '1.5'), (3, "
                + "'86400'), (4, '1000000000'), (5, '31535999999'), (6, '31536000000'), (7, '1000000000000'), (8, "
                + "'31536000000000'), (9, '1000000000000000'), (10, '31536000000000000'), (11, "
                + "'1000000000000000000'), (90, 'true'), (91, '\"2020-01-02\"'), (92, '\"12:00:00\"'), (93, "
                + "'\"86400\"')");
        assertAnswer("Table VT successfully created.", "CREATE OR REPLACE TABLE vt (a ARRAY, o OBJECT)");
        assertAnswer("1", "INSERT INTO vt SELECT [1, '2020-01-02', TRUE, 1.5], {'n': 1, 'b': TRUE}");
        assertAnswer("1970-01-02", "SELECT TO_VARCHAR(a[0]::DATE, 'YYYY-MM-DD') FROM vt");
        assertAnswer("1970-01-02", "SELECT TO_VARCHAR(o:n::DATE, 'YYYY-MM-DD') FROM vt");
        assertAnswer("1970-01-03", "SELECT TO_VARCHAR(a[3]::DATE, 'YYYY-MM-DD') FROM vt");
        answer("SELECT TO_VARCHAR(a[0]::TIMESTAMP_NTZ, 'YYYY-MM-DD HH24:MI:SS.FF3') FROM vt");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR([1,2][0]::DATE, 'YYYY-MM-DD')");
        answer("SELECT TO_VARCHAR([86400][0]::TIMESTAMP_NTZ, 'YYYY-MM-DD HH24:MI:SS.FF3')");
        answer("SELECT TO_VARCHAR([86400][0]::TIMESTAMP_LTZ, 'YYYY-MM-DD HH24:MI:SS.FF3 TZH:TZM')");
        answer("SELECT TO_VARCHAR([86400][0]::TIMESTAMP_TZ, 'YYYY-MM-DD HH24:MI:SS.FF3 TZH:TZM')");
        assertAnswer("REFUSED Failed to cast variant value 86400 to TIME",
            "SELECT TO_VARCHAR([86400][0]::TIME, 'HH24:MI:SS.FF3')");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(PARSE_JSON('1')::DATE, 'YYYY-MM-DD')");
        answer("SELECT TO_VARCHAR(PARSE_JSON('86400')::TIMESTAMP_NTZ, 'YYYY-MM-DD HH24:MI:SS.FF3')");
        assertAnswer("REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types "
                + "VARIANT and DATE",
            "SELECT TO_VARCHAR(TRY_CAST(v AS DATE), 'YYYY-MM-DD') FROM vn WHERE i = 0");
        assertAnswer("REFUSED Failed to cast variant value 86400 to DATE",
            "SELECT TO_VARCHAR(TO_DATE(v), 'YYYY-MM-DD') FROM vn WHERE i = 3");
        answer("SELECT TO_VARCHAR(TO_TIMESTAMP_NTZ(v), 'YYYY-MM-DD HH24:MI:SS.FF3') FROM vn WHERE i = 3");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(v::DATE, 'YYYY-MM-DD') FROM (SELECT PARSE_JSON('1') AS v)");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(v::DATE, 'YYYY-MM-DD') FROM (SELECT PARSE_JSON(column1) AS v FROM VALUES ('1'))");
    }

    @Test
    public void aDerivedRelationHandsTheStoredColumnOn() {
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        assertAnswer("Table VA successfully created.",
            "CREATE OR REPLACE TABLE va (i INT, a ARRAY, o OBJECT, v VARIANT)");
        assertAnswer("1", "INSERT INTO va SELECT 0, ARRAY_CONSTRUCT(1), OBJECT_CONSTRUCT('n', 1), PARSE_JSON('[1]')");
        assertAnswer("View VV successfully created.", "CREATE OR REPLACE VIEW vv AS SELECT i, a FROM va");
        assertAnswer("19700102", "SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM (SELECT a FROM va)");
        assertAnswer("19700102", "WITH c AS (SELECT a FROM va) SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM c");
        assertAnswer("19700102", "SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM vv");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(f.value::DATE, 'YYYYMMDD') FROM va, LATERAL FLATTEN(a) f");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM va GROUP BY a");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(ARRAY_CONSTRUCT(i + 1)[0]::DATE, 'YYYYMMDD') FROM va");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(PARSE_JSON(TO_VARCHAR(a))[0]::DATE, 'YYYYMMDD') FROM va");
        assertAnswer("19700102", "SELECT TO_VARCHAR(a[i]::DATE, 'YYYYMMDD') FROM va");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR([1][i]::DATE, 'YYYYMMDD') FROM va");
        assertAnswer("000001.000", "SELECT TO_VARCHAR(TO_TIME(a[0]), 'HH24MISS.FF3') FROM va");
        assertAnswer("19700101T000001.000",
            "SELECT TO_VARCHAR(TO_TIMESTAMP_NTZ(a[0]), 'YYYYMMDD\"T\"HH24MISS.FF3') FROM va");
        assertAnswer("REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types "
                + "VARIANT and DATE",
            "SELECT TO_VARCHAR(TRY_TO_DATE(a[0]), 'YYYYMMDD') FROM va");
        assertAnswer("19700102|19700102",
            "SELECT TO_VARCHAR(TO_DATE(v[0]), 'YYYYMMDD'), TO_VARCHAR(TO_DATE(o:n), 'YYYYMMDD') FROM va");
        assertAnswer("19700102", "SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM va AS t1 JOIN va AS t2 USING (i)");
        assertAnswer("19700102", "SELECT TO_VARCHAR(x::DATE, 'YYYYMMDD') FROM (SELECT o:n AS x FROM va)");
        assertAnswer("19700102", "SELECT TO_VARCHAR(TO_DATE(x), 'YYYYMMDD') FROM (SELECT a[0] AS x FROM va)");
    }

    @Test
    public void aComputedValuesElementStaysRefused() {
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        assertAnswer("Table VA successfully created.",
            "CREATE OR REPLACE TABLE va (i INT, a ARRAY, o OBJECT, v VARIANT, n ARRAY)");
        assertAnswer("1",
            "INSERT INTO va SELECT 0, ARRAY_CONSTRUCT(1), OBJECT_CONSTRUCT('n', 1), PARSE_JSON('1'), "
                + "ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(1))");
        assertAnswer("View VE successfully created.", "CREATE OR REPLACE VIEW ve AS SELECT i, a[0] AS x FROM va");
        assertAnswer("Table VC successfully created.", "CREATE OR REPLACE TABLE vc AS SELECT a FROM (SELECT [1] AS a)");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(v::DATE, 'YYYYMMDD') FROM (SELECT v FROM va)");
        assertAnswer("19691231T160001.000",
            "SELECT TO_VARCHAR(v::TIMESTAMP_NTZ, 'YYYYMMDD\"T\"HH24MISS.FF3') FROM (SELECT v FROM va)");
        assertAnswer("19700101T000001.000",
            "SELECT TO_VARCHAR(x::TIMESTAMP_NTZ, 'YYYYMMDD\"T\"HH24MISS.FF3') FROM (SELECT a[0] AS x FROM va)");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM (SELECT a FROM va GROUP BY a)");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(x::DATE, 'YYYYMMDD') FROM (SELECT a[0] AS x FROM va GROUP BY a[0])");
        assertAnswer("19700102", "SELECT TO_VARCHAR(MAX(a[0]::DATE), 'YYYYMMDD') FROM va");
        assertAnswer("19700102 ;; 19700102",
            "SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM va UNION ALL SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') "
                + "FROM va");
        assertAnswer("19700102", "SELECT TO_VARCHAR(x[0]::DATE, 'YYYYMMDD') FROM (SELECT a AS x FROM va)");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM (SELECT a FROM va UNION ALL SELECT a FROM va)");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM (SELECT a FROM va ORDER BY i LIMIT 1)");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM (SELECT DISTINCT a FROM va)");
        assertAnswer("19700102", "SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM va WHERE a[0]::DATE IS NOT NULL");
        assertAnswer("19700102",
            "SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM va QUALIFY ROW_NUMBER() OVER (ORDER BY i) = 1");
        assertAnswer("19700102|null",
            "SELECT TO_VARCHAR(t.a[0]::DATE, 'YYYYMMDD'), TO_VARCHAR(u.a[0]::DATE, 'YYYYMMDD') FROM va t LEFT JOIN "
                + "va u ON t.i = u.i + 100");
        assertAnswer("19700102", "SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM vc");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(COALESCE(a, a)[0]::DATE, 'YYYYMMDD') FROM va");
        assertAnswer("19700102", "SELECT TO_VARCHAR(n[0][0]::DATE, 'YYYYMMDD') FROM va");
        assertAnswer("19700102", "WITH c AS (SELECT a[0] AS x FROM va) SELECT TO_VARCHAR(x::DATE, 'YYYYMMDD') FROM c");
        assertAnswer("19700102", "SELECT TO_VARCHAR(x::DATE, 'YYYYMMDD') FROM ve");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM va GROUP BY a[0]");
        assertAnswer("19700102", "SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM va GROUP BY ALL");
        assertAnswer("19700102",
            "SELECT TO_VARCHAR(x::DATE, 'YYYYMMDD') FROM (SELECT x FROM (SELECT o:n AS x FROM va))");
        assertAnswer("19700102", "SELECT TO_VARCHAR(x[0]::DATE, 'YYYYMMDD') FROM (SELECT n[0] AS x FROM va)");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(x::DATE, 'YYYYMMDD') FROM (SELECT a[0] AS x FROM va UNION ALL SELECT a[0] FROM va)");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT TO_VARCHAR(x::DATE, 'YYYYMMDD') FROM (SELECT a[0] AS x FROM va ORDER BY i LIMIT 1)");
        assertAnswer("19700102",
            "SELECT TO_VARCHAR(x::DATE, 'YYYYMMDD') FROM (SELECT a[0] AS x, ROW_NUMBER() OVER (ORDER BY i) AS r FROM "
                + "va)");
    }

    @Test
    public void aGroupingKeyReadsTheRowsAsTheyAre() {
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        assertAnswer("Table VA successfully created.", "CREATE OR REPLACE TABLE va (i INT, a ARRAY, o OBJECT)");
        assertAnswer("1", "INSERT INTO va SELECT 0, ARRAY_CONSTRUCT(1), OBJECT_CONSTRUCT('n', 1)");
        assertAnswer("19700102", "SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM va GROUP BY 1");
        assertAnswer("1970-01-02", "SELECT a[0]::DATE AS d FROM va GROUP BY d");
        assertAnswer("1970-01-02", "SELECT a[0]::DATE FROM va GROUP BY a[0]::DATE");
        assertAnswer("1970-01-02|1", "SELECT a[0]::DATE, COUNT(*) FROM va GROUP BY 1");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT COUNT(*) FROM va GROUP BY a[0] ORDER BY a[0]::DATE");
        assertAnswer("1", "SELECT COUNT(*) FROM va GROUP BY a[0]::DATE ORDER BY a[0]::DATE");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT a[0]::DATE FROM (SELECT a FROM va ORDER BY i)");
        assertAnswer("1970-01-02", "SELECT a[0]::DATE FROM (SELECT a FROM va WHERE i = 0)");
        assertAnswer("1970-01-02", "SELECT a[0]::DATE FROM (SELECT * FROM va)");
        assertAnswer("1970-01-02", "SELECT a[0]::DATE FROM (SELECT va.* FROM va)");
        assertAnswer("1970-01-02", "SELECT a2[0]::DATE FROM va AS t (i2, a2, o2)");
        assertAnswer("00:00:01|19700101T000001.000",
            "SELECT x::TIME, TO_VARCHAR(x::TIMESTAMP_NTZ, 'YYYYMMDD\"T\"HH24MISS.FF3') FROM (SELECT a[0] AS x FROM "
                + "va)");
        assertAnswer("1970-01-02|00:00:01", "SELECT TO_DATE(x), TO_TIME(x) FROM (SELECT o:n AS x FROM va)");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT a[0]::DATE FROM (SELECT a, i FROM va QUALIFY ROW_NUMBER() OVER (ORDER BY i) = 1)");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT a[0]::DATE FROM (SELECT TOP 1 a FROM va)");
        assertAnswer("REFUSED Failed to cast variant value 1 to DATE",
            "SELECT x::DATE FROM (SELECT a[0] AS x FROM va) GROUP BY x");
        assertAnswer("1970-01-02", "SELECT x::DATE FROM (SELECT a[0] AS x FROM va) GROUP BY 1");
        assertAnswer("REFUSED SQL compilation error: | [VA.A] is not a valid group by expression",
            "SELECT a[0]::DATE FROM va HAVING COUNT(*) > 0");
        assertAnswer("1970-01-02", "SELECT MAX(a[0]::DATE) FROM va GROUP BY i");
        assertAnswer("1970-01-02", "SELECT a[0]::DATE FROM va GROUP BY a[0]::DATE, i");
        assertAnswer("1970-01-02 ;; null", "SELECT a[0]::DATE FROM va GROUP BY ROLLUP (a[0]::DATE)");
    }
}
