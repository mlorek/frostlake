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

package dev.frostlake.types;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * DECFLOAT is a decimal of 38 significant digits whose point floats (live-verified): SYSTEM$TYPEOF answers
 * DECFLOAT(38)[DECFLOAT18] for a cast, a column, arithmetic beside a NUMBER, a FLOAT or a numeric text, the
 * conditionals and SUM, AVG, MIN and MAX; a FLOAT converts at its exact binary value; its text is plain up to 38
 * digits and scientific past them; 0.1::DECFLOAT + 0.2::DECFLOAT = 0.3 holds, and a column keeps every digit. Its
 * value stays exact through a scripting variable, a procedure's argument, VALUES, a set operation and a recursive
 * CTE, and arithmetic in a block's own expression keeps 38 digits.
 */
public class DecfloatTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void itsTypeFollowsItThroughExpressions() {
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(1::DECFLOAT)", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(1::DECFLOAT(38))", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(TO_DECFLOAT('1.5'))", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(1::DECFLOAT + 1)", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(1::DECFLOAT * 1.5)", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(1::DECFLOAT + 1.5::FLOAT)", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(1::DECFLOAT / 3)", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(-(1::DECFLOAT))", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(ABS(-1::DECFLOAT))", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(ROUND(1.55::DECFLOAT, 1))", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(FLOOR(1.5::DECFLOAT))", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(CEIL(1.5::DECFLOAT))", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(TRUNC(1.5::DECFLOAT))", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(MOD(7::DECFLOAT, 2))", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(1::DECFLOAT + '1')", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, 1::DECFLOAT, 1))", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(COALESCE(1::DECFLOAT, 1.5))", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(1::DECFLOAT::NUMBER(10,2))", "NUMBER(10,2)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(1::DECFLOAT::FLOAT)", "FLOAT[DOUBLE]"},
            {"SELECT SYSTEM$TYPEOF(1.5::DECFLOAT::VARCHAR)", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(TO_VARIANT(1.5::DECFLOAT))", "VARIANT[LOB]"},
            {"SELECT TO_VARIANT(1.5::DECFLOAT)::VARCHAR", "1.5"},
            {"SELECT SYSTEM$TYPEOF(PARSE_JSON('1.5')::DECFLOAT)", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(NULL::DECFLOAT)", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(1::DECFLOAT > 0)", "BOOLEAN[ROWINDEX]"},
            {"SELECT SYSTEM$TYPEOF(GREATEST(1::DECFLOAT, 2))", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SYSTEM$TYPEOF(1::DECFLOAT * 2::DECFLOAT)", "DECFLOAT(38)[DECFLOAT18]"},
        });
    }

    @Test
    public void itsTextIsPlainUpTo38DigitsAndScientificPast() {
        assertCells(new String[][] {
            {"SELECT 1e30::DECFLOAT::VARCHAR", "1000000000000000000000000000000"},
            {"SELECT 1E-10::DECFLOAT::VARCHAR", "0.0000000001"},
            {"SELECT 12345678901234567890123456789012345678::DECFLOAT::VARCHAR",
                "12345678901234567890123456789012345678"},
            {"SELECT (1::DECFLOAT / 3)::VARCHAR", "3.3333333333333333333333333333333333333e-1"},
            {"SELECT 2::DECFLOAT::VARCHAR", "2"},
            {"SELECT 1.5::DECFLOAT::VARCHAR", "1.5"},
            {"SELECT 100::DECFLOAT::VARCHAR", "100"},
            {"SELECT -0.5::DECFLOAT::VARCHAR", "-0.5"},
            {"SELECT 0::DECFLOAT::VARCHAR", "0"},
            {"SELECT 123.456::DECFLOAT::VARCHAR", "123.456"},
            {"SELECT 1.50::DECFLOAT::VARCHAR", "1.5"},
            {"SELECT 10.0::DECFLOAT::VARCHAR", "10"},
            {"SELECT 1e-7::DECFLOAT::VARCHAR", "0.0000001"},
            {"SELECT 1e-5::DECFLOAT::VARCHAR", "0.00001"},
            {"SELECT 0.001::DECFLOAT::VARCHAR", "0.001"},
            {"SELECT 1e38::DECFLOAT::VARCHAR", "99999999999999997748809823456034029568"},
            {"SELECT 1e39::DECFLOAT::VARCHAR", "9.9999999999999993970916637160317858611e38"},
            {"SELECT 1e100::DECFLOAT::VARCHAR", "1.0000000000000000159028911097599180468e100"},
            {"SELECT 1e-100::DECFLOAT::VARCHAR", "1.0000000000000000199918998026028836196e-100"},
            {"SELECT (2::DECFLOAT / 3)::VARCHAR", "6.6666666666666666666666666666666666667e-1"},
            {"SELECT (1::DECFLOAT / 7)::VARCHAR", "1.4285714285714285714285714285714285714e-1"},
            {"SELECT (10::DECFLOAT / 4)::VARCHAR", "2.5"},
            {"SELECT (1::DECFLOAT / 8)::VARCHAR", "0.125"},
            {"SELECT (1::DECFLOAT / 3 * 3)::VARCHAR", "9.9999999999999999999999999999999999999e-1"},
            {"SELECT (0.1::DECFLOAT + 0.2::DECFLOAT)::VARCHAR", "0.3"},
            {"SELECT 1.555::DECFLOAT::NUMBER(10,2)::VARCHAR", "1.56"},
            {"SELECT 1.5::DECFLOAT::FLOAT::VARCHAR", "1.5"},
            {"SELECT 1.5::DECFLOAT::INT::VARCHAR", "2"},
            {"SELECT TO_DECFLOAT('1e5')::VARCHAR", "100000"},
            {"SELECT (1e30::DECFLOAT + 1)::VARCHAR", "1000000000000000000000000000001"},
            {"SELECT (1::DECFLOAT / 3e30)::VARCHAR", "3.3333333333333333333333333333333333333e-31"},
            {"SELECT -1e-20::DECFLOAT::VARCHAR", "-1.0E-20"},
            {"SELECT 1.0e1::DECFLOAT::VARCHAR", "10"},
            {"SELECT 0.1::DECFLOAT + 0.2::DECFLOAT = 0.3", "true"},
            {"SELECT 1::DECFLOAT / 3 * 3 = 1", "false"},
            {"SELECT 0.1::DECFLOAT = 0.1::FLOAT", "false"},
            {"SELECT 'abc'::DECFLOAT", "REFUSED Numeric value 'abc' is not recognized"},
            {"SELECT TRY_TO_DECFLOAT('abc')", "null"},
            {"SELECT TO_DECFLOAT('abc')", "REFUSED Numeric value 'abc' is not recognized"},
            {"SELECT 1.5::DECFLOAT", "1.5"},
            {"SELECT 1::DECFLOAT / 3", "0.33333333333333333333333333333333333333"},
            {"SELECT 1e30::DECFLOAT", "1000000000000000000000000000000"},
            {"SELECT 1E-10::DECFLOAT", "1E-10"},
        });
    }

    @Test
    public void aColumnKeepsEveryDigit() {
        engine.execute("CREATE OR REPLACE TABLE tdf (d DECFLOAT, n NUMBER(10,2))");
        engine.execute("INSERT INTO tdf VALUES (1.5, 1), (0.1, 2), (-3, 3)");
        engine.execute("INSERT INTO tdf SELECT 1e30::DECFLOAT, 4");
        assertCells(new String[][] {
            {"SELECT d::VARCHAR FROM tdf ORDER BY d", "-3 ;; 0.1 ;; 1.5 ;; 1000000000000000000000000000000"},
            {"SELECT SYSTEM$TYPEOF(d) FROM tdf LIMIT 1", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT data_type, numeric_precision, numeric_scale FROM information_schema.columns WHERE table_name = "
                + "'TDF' AND column_name = 'D'",
                "DECFLOAT|null|null"},
            {"SELECT SYSTEM$TYPEOF(SUM(d)) FROM tdf",
                "DECFLOAT(38)[DECFLOAT18] ;; DECFLOAT(38)[DECFLOAT18] ;; DECFLOAT(38)[DECFLOAT18] ;; "
                + "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SUM(d)::VARCHAR FROM tdf", "999999999999999999999999999998.6"},
            {"SELECT SYSTEM$TYPEOF(AVG(d)) FROM tdf",
                "DECFLOAT(38)[DECFLOAT18] ;; DECFLOAT(38)[DECFLOAT18] ;; DECFLOAT(38)[DECFLOAT18] ;; "
                + "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT AVG(d)::VARCHAR FROM tdf", "249999999999999999999999999999.65"},
            {"SELECT SYSTEM$TYPEOF(d + n) FROM tdf LIMIT 1", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (d + n)::VARCHAR FROM tdf ORDER BY n", "2.5 ;; 2.1 ;; 0 ;; 1000000000000000000000000000004"},
            {"INSERT INTO tdf VALUES (12345678901234567890123456789012345678, 5)", "1"},
            {"INSERT INTO tdf SELECT 12345678901234567890123456789012345678::DECFLOAT, 6", "1"},
            {"SELECT d::VARCHAR FROM tdf WHERE n = 6", "12345678901234567890123456789012345678"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT 1::DECFLOAT x UNION ALL SELECT 2) LIMIT 1",
                "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT COUNT(DISTINCT d) FROM tdf", "5"},
            {"SELECT MAX(d)::VARCHAR FROM tdf", "12345678901234567890123456789012345678"},
            {"SELECT d FROM tdf WHERE d = 0.1", "0.1"},
            {"CREATE OR REPLACE TABLE tdf2 AS SELECT d * 2 AS dd FROM tdf", "Table TDF2 successfully created."},
            {"SELECT data_type FROM information_schema.columns WHERE table_name = 'TDF2'", "DECFLOAT"},
            {"SELECT d FROM tdf ORDER BY d",
                "-3 ;; 0.1 ;; 1.5 ;; 1000000000000000000000000000000 ;; 12345678901234567890123456789012345678 ;; "
                + "12345678901234567890123456789012345678"},
        });
    }

    @Test
    public void theCatalogSpellsItAndKeysCompareExactly() {
        engine.execute("CREATE OR REPLACE TABLE tdf3 (d DECFLOAT, k INT)");
        engine.execute("INSERT INTO tdf3 VALUES (1.5, 1), (1.50, 2), (0.1, 3), (NULL, 4), (-2, 5)");
        assertCells(new String[][] {
            {"DESCRIBE TABLE tdf3 ->> SELECT \"name\", \"type\" FROM $1", "D|DECFLOAT(38) ;; K|NUMBER(38,0)"},
            {"SHOW COLUMNS IN TABLE tdf3 ->> SELECT \"column_name\", \"data_type\" FROM $1",
                "D|{\"type\":\"DECFLOAT\",\"precision\":38,\"nullable\":true} ;; K|{\"type\":\"FIXED\","
                + "\"precision\":38,\"scale\":0,\"nullable\":true}"},
            {"SELECT d, COUNT(*) FROM tdf3 GROUP BY d ORDER BY d NULLS LAST", "-2|1 ;; 0.1|1 ;; 1.5|2 ;; null|1"},
            {"SELECT DISTINCT d FROM tdf3 ORDER BY 1 NULLS LAST", "-2 ;; 0.1 ;; 1.5 ;; null"},
            {"SELECT k FROM tdf3 WHERE d = 1.5 ORDER BY k", "1 ;; 2"},
            {"SELECT k FROM tdf3 WHERE d > 0.1::FLOAT ORDER BY k", "1 ;; 2"},
            {"SELECT k FROM tdf3 WHERE d < 1 ORDER BY k", "3 ;; 5"},
            {"SELECT SYSTEM$TYPEOF(CASE WHEN TRUE THEN 1::DECFLOAT ELSE 2 END)", "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT 12345678901234567890123456789012345678.5::DECFLOAT::VARCHAR",
                "12345678901234567525491324606797053952"},
            {"SELECT 123456789012345678901234567890123456789::DECFLOAT::VARCHAR",
                "REFUSED SQL compilation error: Error line 1 at position 7 | Integer literal is out of representable "
                + "range: 123456789012345678901234567890123456789"},
            {"SELECT (1::DECFLOAT / 0)::VARCHAR", "REFUSED Division by zero"},
            {"SELECT (-1::DECFLOAT)::VARCHAR", "-1"},
            {"SELECT (0.5::DECFLOAT * 0.5::DECFLOAT)::VARCHAR", "0.25"},
            {"SELECT (1e-30::DECFLOAT * 1e-30::DECFLOAT)::VARCHAR", "1e-60"},
            {"SELECT 2.5::DECFLOAT::INT, -2.5::DECFLOAT::INT, 0.125::DECFLOAT::NUMBER(10,2)", "3|-3|0.13"},
            {"SELECT d::VARCHAR, d FROM tdf3 WHERE k = 2", "1.5|1.5"},
            {"SELECT SUM(d)::VARCHAR, AVG(d)::VARCHAR, MIN(d)::VARCHAR, MAX(d)::VARCHAR FROM tdf3", "1.1|0.275|-2|1.5"},
            {"SELECT TO_DECFLOAT('1.5e3')::VARCHAR, TO_DECFLOAT(' 2 ')::VARCHAR", "1500|2"},
            {"SELECT 1::DECFLOAT = 1.0::FLOAT, 0.5::DECFLOAT = 0.5::FLOAT", "true|true"},
        });
    }

    @Test
    public void theRoundingFunctionsKeepADecfloat() {
        assertCells(new String[][] {
            {"SELECT (ABS(-1.5::DECFLOAT))::VARCHAR, SYSTEM$TYPEOF(ABS(-1.5::DECFLOAT))",
                "1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (ROUND(1.55::DECFLOAT, 1))::VARCHAR, SYSTEM$TYPEOF(ROUND(1.55::DECFLOAT, 1))",
                "1.6|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (ROUND(1.5::DECFLOAT))::VARCHAR, SYSTEM$TYPEOF(ROUND(1.5::DECFLOAT))",
                "2|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (FLOOR(1.5::DECFLOAT))::VARCHAR, SYSTEM$TYPEOF(FLOOR(1.5::DECFLOAT))",
                "1|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (CEIL(1.5::DECFLOAT))::VARCHAR, SYSTEM$TYPEOF(CEIL(1.5::DECFLOAT))", "2|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (TRUNC(1.57::DECFLOAT, 1))::VARCHAR, SYSTEM$TYPEOF(TRUNC(1.57::DECFLOAT, 1))",
                "1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (MOD(7.5::DECFLOAT, 2))::VARCHAR, SYSTEM$TYPEOF(MOD(7.5::DECFLOAT, 2))",
                "1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (SIGN(-1.5::DECFLOAT))::VARCHAR, SYSTEM$TYPEOF(SIGN(-1.5::DECFLOAT))",
                "-1|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (GREATEST(1.5::DECFLOAT, 2))::VARCHAR, SYSTEM$TYPEOF(GREATEST(1.5::DECFLOAT, 2))",
                "2|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (LEAST(1.5::DECFLOAT, 2))::VARCHAR, SYSTEM$TYPEOF(LEAST(1.5::DECFLOAT, 2))",
                "1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (-(1.5::DECFLOAT))::VARCHAR, SYSTEM$TYPEOF(-(1.5::DECFLOAT))", "-1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (ROUND(-2.5::DECFLOAT))::VARCHAR, SYSTEM$TYPEOF(ROUND(-2.5::DECFLOAT))",
                "-3|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (ROUND(1.23456::DECFLOAT, 3))::VARCHAR, SYSTEM$TYPEOF(ROUND(1.23456::DECFLOAT, 3))",
                "1.235|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (TRUNC(-1.5::DECFLOAT))::VARCHAR, SYSTEM$TYPEOF(TRUNC(-1.5::DECFLOAT))",
                "-1|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (ABS(1e30::DECFLOAT))::VARCHAR, SYSTEM$TYPEOF(ABS(1e30::DECFLOAT))",
                "1000000000000000000000000000000|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DIV0(1::DECFLOAT, 0))::VARCHAR, SYSTEM$TYPEOF(DIV0(1::DECFLOAT, 0))",
                "0|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (IFNULL(NULL, 1.5::DECFLOAT))::VARCHAR, SYSTEM$TYPEOF(IFNULL(NULL, 1.5::DECFLOAT))",
                "1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (NVL(1.5::DECFLOAT, 2))::VARCHAR, SYSTEM$TYPEOF(NVL(1.5::DECFLOAT, 2))",
                "1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (ZEROIFNULL(1.5::DECFLOAT))::VARCHAR, SYSTEM$TYPEOF(ZEROIFNULL(1.5::DECFLOAT))",
                "1.5|DECFLOAT(38)[DECFLOAT18]"},
        });
    }

    @Test
    public void scriptingAndRoutinesKeepItExact() {
        engine.execute("CREATE OR REPLACE PROCEDURE df_p(x DECFLOAT) RETURNS DECFLOAT LANGUAGE SQL AS 'BEGIN RETURN "
            + "x; END'");
        engine.execute("CREATE OR REPLACE PROCEDURE df_q(x DECFLOAT) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
            + "x::VARCHAR || '' '' || SYSTEM$TYPEOF(x); END'");
        engine.execute("CREATE OR REPLACE PROCEDURE df_sp(x DECFLOAT) RETURNS DECFLOAT LANGUAGE SQL AS 'BEGIN RETURN "
            + "x * 2; END'");
        assertCells(new String[][] {
            {"CALL df_p(1::DECFLOAT / 3)", "0.33333333333333333333333333333333333333"},
            {"CALL df_q(TO_DECFLOAT('2.25'))", "2.25 DECFLOAT(38)[DECFLOAT18]"},
            {"CALL df_sp(1::DECFLOAT / 3)", "0.66666666666666666666666666666666666666"},
            {"EXECUTE IMMEDIATE $$DECLARE x DECFLOAT; BEGIN x := 1.5; RETURN x::VARCHAR || ' ' || SYSTEM$TYPEOF(x); "
                + "END$$",
                "1.5 DECFLOAT(38)[DECFLOAT18]"},
            {"EXECUTE IMMEDIATE $$BEGIN LET y DECFLOAT := 1::DECFLOAT / 3; RETURN y::VARCHAR; END$$",
                "3.3333333333333333333333333333333333333e-1"},
            {"EXECUTE IMMEDIATE $$DECLARE x DECFLOAT DEFAULT 2.5; BEGIN x := x / 4; RETURN x; END$$", "0.625"},
            {"EXECUTE IMMEDIATE $$DECLARE x DECFLOAT; BEGIN SELECT 1.5 INTO :x; RETURN x::VARCHAR; END$$", "1.5"},
            {"EXECUTE IMMEDIATE $$DECLARE c CURSOR FOR SELECT 1::DECFLOAT / 3 AS d; r DECFLOAT; BEGIN OPEN c; FETCH c "
                + "INTO r; RETURN r::VARCHAR; END$$",
                "3.3333333333333333333333333333333333333e-1"},
            {"EXECUTE IMMEDIATE $$DECLARE x DECFLOAT DEFAULT 1.5; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE "
                + "'SELECT ?::VARCHAR AS a, SYSTEM$TYPEOF(?) AS b' USING (x, x)); RETURN TABLE(res); END$$",
                "1.5|VARCHAR(3)[LOB]"},
            {"EXECUTE IMMEDIATE $$DECLARE x DECFLOAT DEFAULT 1.5; BEGIN RETURN (SELECT :x * 2)::VARCHAR; END$$", "3"},
            {"EXECUTE IMMEDIATE $$BEGIN LET z := 1::DECFLOAT / 3; RETURN z::VARCHAR || ' ' || SYSTEM$TYPEOF(z); END$$",
                "3.3333333333333333333333333333333333333e-1 DECFLOAT(38)[DECFLOAT18]"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT DEFAULT 0.1; BEGIN RETURN y::VARCHAR; END$$", "0.1"},
            {"EXECUTE IMMEDIATE $$BEGIN LET y DECFLOAT := -0.1; RETURN y::VARCHAR; END$$", "-0.1"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 0.333333333333333333333333333; RETURN y::VARCHAR; "
                + "END$$",
                "0.333333333333333333333333333"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 0.1::FLOAT; RETURN y::VARCHAR; END$$",
                "1.0000000000000000555111512312578270212e-1"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 1::DECFLOAT / 3; RETURN (y * 3)::VARCHAR; END$$",
                "9.9999999999999999999999999999999999999e-1"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 1::DECFLOAT / 3; RETURN y || '|'; END$$",
                "3.3333333333333333333333333333333333333e-1|"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 1::DECFLOAT / 3; RETURN y; END$$",
                "0.33333333333333333333333333333333333333"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 2; RETURN y / 3; END$$",
                "0.66666666666666666666666666666666666667"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 2; RETURN -y / 3; END$$",
                "-0.66666666666666666666666666666666666667"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; z DECFLOAT; BEGIN y := 2; z := y / 3; RETURN z::VARCHAR || ' ' |"
                + "| SYSTEM$TYPEOF(z); END$$",
                "6.6666666666666666666666666666666666667e-1 DECFLOAT(38)[DECFLOAT18]"},
            {"EXECUTE IMMEDIATE $$BEGIN LET n NUMBER(38,20) := 0.1; RETURN n::VARCHAR; END$$",
                "0.10000000000000000000"},
        });
    }

    @Test
    public void setOperationsValuesAndRecursionKeepItExact() {
        engine.execute("CREATE OR REPLACE TABLE dx (d DECFLOAT)");
        engine.execute("INSERT INTO dx SELECT 1.5 UNION ALL SELECT 1::DECFLOAT / 3");
        assertCells(new String[][] {
            {"SELECT d::VARCHAR FROM dx ORDER BY 1", "1.5 ;; 3.3333333333333333333333333333333333333e-1"},
            {"SELECT column1::VARCHAR, SYSTEM$TYPEOF(column1) FROM VALUES (1.5::DECFLOAT), (2.25)",
                "1.5|DECFLOAT(38)[DECFLOAT18] ;; 2.25|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT column1::VARCHAR FROM VALUES (2.25), (1.5::DECFLOAT)", "2.25 ;; 1.5"},
            {"WITH RECURSIVE r(n) AS (SELECT 1.5::DECFLOAT UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT "
                + "n::VARCHAR, SYSTEM$TYPEOF(n) FROM r ORDER BY 1",
                "1.5|DECFLOAT(38)[DECFLOAT18] ;; 2.5|DECFLOAT(38)[DECFLOAT18] ;; 3.5|DECFLOAT(38)[DECFLOAT18]"},
            {"WITH RECURSIVE r(n) AS (SELECT 1.5 UNION ALL SELECT n + 1::DECFLOAT FROM r WHERE n < 3) SELECT "
                + "n::VARCHAR, SYSTEM$TYPEOF(n) FROM r ORDER BY 1",
                "REFUSED SQL compilation error: | Type mismatch between anchor and recursive term of column 'N' in "
                + "Recursive CTE 'R'"},
            {"SELECT x::VARCHAR, SYSTEM$TYPEOF(x) FROM (SELECT 1::DECFLOAT AS x UNION ALL SELECT 1.5 UNION ALL SELECT "
                + "'2.25') ORDER BY 1",
                "1|DECFLOAT(38)[DECFLOAT18] ;; 1.5|DECFLOAT(38)[DECFLOAT18] ;; 2.25|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT x::VARCHAR, SYSTEM$TYPEOF(x) FROM (SELECT 1.5 AS x UNION SELECT 1.5::DECFLOAT)",
                "1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT x::VARCHAR FROM (SELECT 1.5::FLOAT AS x UNION ALL SELECT 1::DECFLOAT / 3) ORDER BY 1",
                "1.5 ;; 3.3333333333333333333333333333333333333e-1"},
            {"SELECT x::VARCHAR FROM (SELECT 0.1::FLOAT AS x UNION ALL SELECT 1::DECFLOAT) ORDER BY 1",
                "1 ;; 1.0000000000000000555111512312578270212e-1"},
            {"SELECT x::VARCHAR, SYSTEM$TYPEOF(x) FROM (SELECT 1.25 AS x EXCEPT SELECT 2::DECFLOAT)",
                "1.25|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT x::VARCHAR, SYSTEM$TYPEOF(x) FROM (SELECT 1.5 AS x INTERSECT SELECT 1.5::DECFLOAT)",
                "1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT MEDIAN(x)::VARCHAR, SYSTEM$TYPEOF(MEDIAN(x)) FROM (SELECT 1.5::DECFLOAT AS x UNION ALL SELECT "
                + "2::DECFLOAT)",
                "1.75|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT 1.5::DECFLOAT AS x UNION ALL SELECT 1.50::DECFLOAT)", "1"},
            {"SELECT RANK() OVER (ORDER BY x), x::VARCHAR FROM (SELECT 1.5::DECFLOAT AS x UNION ALL SELECT "
                + "1::DECFLOAT / 3) ORDER BY 1",
                "1|3.3333333333333333333333333333333333333e-1 ;; 2|1.5"},
        });
    }

    /**
     * MEDIAN and PERCENTILE_CONT over a DECFLOAT interpolate to 38 significant digits, no NUMBER scale applying: the
     * input scale plus three refused a middle value of 1 beside 1e-50 as FIXED(38,53), refused 5e99 and a sixth past
     * 38 digits, and cut an interpolation to 3 decimals. The same holds grouped, ordered downwards and as a window.
     */
    @Test
    public void theInterpolatingPercentilesKeep38Digits() {
        engine.execute("CREATE OR REPLACE TABLE df_pc (g INT, x DECFLOAT)");
        engine.execute("INSERT INTO df_pc VALUES (1, DECFLOAT '0'), (1, DECFLOAT '1'), (2, DECFLOAT '1e100'), "
            + "(2, DECFLOAT '2'), (2, NULL)");
        assertCells(new String[][] {
            {"SELECT MEDIAN(x)::VARCHAR FROM (SELECT DECFLOAT '1' x UNION ALL SELECT DECFLOAT '2' UNION ALL "
                + "SELECT DECFLOAT '1e-50')", "1"},
            {"SELECT MEDIAN(x)::VARCHAR FROM (SELECT DECFLOAT '1e100' x UNION ALL SELECT DECFLOAT '2')", "5e99"},
            {"SELECT MEDIAN(x)::VARCHAR FROM (SELECT DECFLOAT '0' x UNION ALL SELECT DECFLOAT '1' / 3)",
                "1.6666666666666666666666666666666666667e-1"},
            {"SELECT PERCENTILE_CONT(0.123456) WITHIN GROUP (ORDER BY x)::VARCHAR, "
                + "SYSTEM$TYPEOF(PERCENTILE_CONT(0.123456) WITHIN GROUP (ORDER BY x)) FROM "
                + "(SELECT DECFLOAT '0' x UNION ALL SELECT DECFLOAT '1')",
                "0.123456|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x)::VARCHAR FROM (SELECT DECFLOAT '0' x UNION ALL "
                + "SELECT DECFLOAT '1' UNION ALL SELECT DECFLOAT '2' UNION ALL SELECT DECFLOAT '3')", "0.3"},
            {"SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x) OVER ()::VARCHAR FROM "
                + "(SELECT DECFLOAT '1e100' x UNION ALL SELECT DECFLOAT '2')", "5e99 ;; 5e99"},
            {"SELECT MEDIAN(x)::VARCHAR, PERCENTILE_CONT(0) WITHIN GROUP (ORDER BY x)::VARCHAR, "
                + "PERCENTILE_CONT(1) WITHIN GROUP (ORDER BY x)::VARCHAR FROM "
                + "(SELECT DECFLOAT '1e-16383' x UNION ALL SELECT DECFLOAT '1e16384')",
                "5e16383|1e-16383|1e16384"},
            {"SELECT g, MEDIAN(x)::VARCHAR, PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY x DESC)::VARCHAR, "
                + "SYSTEM$TYPEOF(MEDIAN(x)) FROM df_pc GROUP BY g ORDER BY g",
                "1|0.5|0.75|DECFLOAT(38)[DECFLOAT18] ;; 2|5e99|7.5e99|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT DISTINCT g, MEDIAN(x) OVER (PARTITION BY g)::VARCHAR, "
                + "PERCENTILE_CONT(0.1) WITHIN GROUP (ORDER BY x) OVER (PARTITION BY g)::VARCHAR FROM df_pc ORDER BY g",
                "1|0.5|0.1 ;; 2|5e99|1e99"},
            {"SELECT MEDIAN(x)::VARCHAR FROM (SELECT NULL::DECFLOAT x)", "null"},
            {"SELECT MEDIAN(x)::VARCHAR FROM (SELECT 1.5 x UNION ALL SELECT 2.25)", "1.87500"},
        });
    }
}
