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
 * A date call with the bare word NULL among its arguments folds to an untyped NULL - WEEKISO, DAYOFWEEKISO and
 * YEAROFWEEKISO as the other date parts, TIME_SLICE as DATEADD and DATE_TRUNC - and it folds before anything
 * else of it is judged: a unit no function knows and an operand of another family are never read, and only the
 * argument count is held to the function's. A typed NULL keeps the call's type and its refusals. MONTHS_BETWEEN
 * does not fold: over the bare word, or a variable of a block that holds NULL, it is refused as the EXTRACT it
 * is planned as. It declares NUMBER(27,6), and a timestamp's time of day counts as a fraction of its day unless
 * the result is whole. Every answer is live-verified.
 */
public class DateCallsOverUntypedNullTest extends MeasuredCellsTestSupport {

    @Test
    public void anUntypedNullFoldsOrIsRefused() {
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | Function EXTRACT does not support NULL argument type",
                "SELECT MONTHS_BETWEEN(NULL, '2024-01-01'::DATE)");
            assertAnswer(
                "REFUSED SQL compilation error: | Function EXTRACT does not support NULL argument type",
                "SELECT MONTHS_BETWEEN('2024-01-01'::DATE, NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: | Function EXTRACT does not support NULL argument type",
                "SELECT MONTHS_BETWEEN(NULL, NULL)");
            assertAnswer(
                "null",
                "SELECT MONTHS_BETWEEN(NULL::DATE, '2024-01-01'::DATE)");
            assertAnswer(
                "null",
                "SELECT MONTHS_BETWEEN('2024-01-01'::DATE, NULL::DATE)");
            assertAnswer(
                "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(134217728) argument "
                    + "type",
                "SELECT MONTHS_BETWEEN(NULL::VARCHAR, '2024-01-01'::DATE)");
            assertAnswer(
                "REFUSED SQL compilation error: | Function EXTRACT does not support NULL argument type",
                "SELECT SYSTEM$TYPEOF(MONTHS_BETWEEN(NULL, '2024-01-01'::DATE))");
            assertAnswer(
                "NUMBER(27,6)[SB1]",
                "SELECT SYSTEM$TYPEOF(MONTHS_BETWEEN(NULL::DATE, '2024-01-01'::DATE))");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 40 : SQL compilation "
                    + "error: | Function EXTRACT does not support NULL argument type",
                "DECLARE s DATE; r RESULTSET; BEGIN r := (SELECT MONTHS_BETWEEN(:s, '2024-01-01'::DATE)); RETURN "
                    + "TABLE(r); END;");
            assertAnswer(
                "null",
                "DECLARE s DATE; BEGIN RETURN MONTHS_BETWEEN(s, '2024-01-01'::DATE); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'DATE_DIFFDATEINMONTHS': (DATE, NUMBER(1,0))",
                "SELECT MONTHS_BETWEEN(1, '2024-01-01'::DATE)");
            assertAnswer(
                "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(10) argument type",
                "SELECT MONTHS_BETWEEN('2024-01-01', NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(10) argument type",
                "SELECT MONTHS_BETWEEN(NULL, '2024-01-01')");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'DATE_DIFFDATEINMONTHS': (DATE, BOOLEAN)",
                "SELECT MONTHS_BETWEEN(TRUE, '2024-01-01'::DATE)");
            assertAnswer(
                "null",
                "SELECT YEAR(NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(YEAR(NULL))");
            assertShape(
                "YEAR(NULL):VARCHAR(0)",
                "SELECT YEAR(NULL)");
            assertAnswer(
                "null",
                "SELECT MONTH(NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(MONTH(NULL))");
            assertShape(
                "MONTH(NULL):VARCHAR(0)",
                "SELECT MONTH(NULL)");
            assertAnswer(
                "null",
                "SELECT DAY(NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(DAY(NULL))");
            assertShape(
                "DAY(NULL):VARCHAR(0)",
                "SELECT DAY(NULL)");
            assertAnswer(
                "null",
                "SELECT DAYOFMONTH(NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(DAYOFMONTH(NULL))");
            assertShape(
                "DAYOFMONTH(NULL):VARCHAR(0)",
                "SELECT DAYOFMONTH(NULL)");
            assertAnswer(
                "null",
                "SELECT DAYOFWEEK(NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(DAYOFWEEK(NULL))");
            assertShape(
                "DAYOFWEEK(NULL):VARCHAR(0)",
                "SELECT DAYOFWEEK(NULL)");
            assertAnswer(
                "null",
                "SELECT DAYOFWEEKISO(NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(DAYOFWEEKISO(NULL))");
            assertShape(
                "DAYOFWEEKISO(NULL):VARCHAR(0)",
                "SELECT DAYOFWEEKISO(NULL)");
            assertAnswer(
                "null",
                "SELECT DAYOFYEAR(NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(DAYOFYEAR(NULL))");
            assertShape(
                "DAYOFYEAR(NULL):VARCHAR(0)",
                "SELECT DAYOFYEAR(NULL)");
            assertAnswer(
                "null",
                "SELECT WEEK(NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(WEEK(NULL))");
            assertShape(
                "WEEK(NULL):VARCHAR(0)",
                "SELECT WEEK(NULL)");
            assertAnswer(
                "null",
                "SELECT WEEKOFYEAR(NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(WEEKOFYEAR(NULL))");
            assertShape(
                "WEEKOFYEAR(NULL):VARCHAR(0)",
                "SELECT WEEKOFYEAR(NULL)");
            assertAnswer(
                "null",
                "SELECT WEEKISO(NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(WEEKISO(NULL))");
            assertShape(
                "WEEKISO(NULL):VARCHAR(0)",
                "SELECT WEEKISO(NULL)");
            assertAnswer(
                "null",
                "SELECT QUARTER(NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(QUARTER(NULL))");
            assertShape(
                "QUARTER(NULL):VARCHAR(0)",
                "SELECT QUARTER(NULL)");
            assertAnswer(
                "null",
                "SELECT YEAROFWEEK(NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(YEAROFWEEK(NULL))");
            assertShape(
                "YEAROFWEEK(NULL):VARCHAR(0)",
                "SELECT YEAROFWEEK(NULL)");
            assertAnswer(
                "null",
                "SELECT YEAROFWEEKISO(NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(YEAROFWEEKISO(NULL))");
            assertShape(
                "YEAROFWEEKISO(NULL):VARCHAR(0)",
                "SELECT YEAROFWEEKISO(NULL)");
            assertAnswer(
                "null",
                "SELECT HOUR(NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(HOUR(NULL))");
            assertShape(
                "HOUR(NULL):VARCHAR(0)",
                "SELECT HOUR(NULL)");
            assertAnswer(
                "null",
                "SELECT MINUTE(NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(MINUTE(NULL))");
            assertShape(
                "MINUTE(NULL):VARCHAR(0)",
                "SELECT MINUTE(NULL)");
            assertAnswer(
                "null",
                "SELECT SECOND(NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(SECOND(NULL))");
            assertShape(
                "SECOND(NULL):VARCHAR(0)",
                "SELECT SECOND(NULL)");
            assertAnswer(
                "null",
                "SELECT TIME_SLICE(NULL, 1, 'HOUR')");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(TIME_SLICE(NULL, 1, 'HOUR'))");
            assertAnswer(
                "TIMESTAMP_NTZ(9)[SB16]",
                "SELECT SYSTEM$TYPEOF(TIME_SLICE(NULL::TIMESTAMP_NTZ, 1, 'HOUR'))");
            assertAnswer(
                "null",
                "SELECT DATEDIFF(DAY, NULL, CURRENT_DATE)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(DATEDIFF(DAY, NULL, CURRENT_DATE))");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(DATEADD(DAY, 1, NULL))");
            assertAnswer(
                "null",
                "SELECT DATEADD(DAY, 1, NULL)");
            assertAnswer(
                "null",
                "SELECT LAST_DAY(NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(LAST_DAY(NULL))");
            assertAnswer(
                "null",
                "SELECT NEXT_DAY(NULL, 'mo')");
            assertAnswer(
                "DATE[SB4]",
                "SELECT SYSTEM$TYPEOF(NEXT_DAY(NULL, 'mo'))");
            assertAnswer(
                "null",
                "SELECT PREVIOUS_DAY(NULL, 'mo')");
            assertAnswer(
                "null",
                "SELECT ADD_MONTHS(NULL, 1)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(ADD_MONTHS(NULL, 1))");
            assertAnswer(
                "null",
                "SELECT DATE_TRUNC('day', NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(DATE_TRUNC('day', NULL))");
            assertAnswer(
                "null",
                "SELECT DAYNAME(NULL)");
            assertAnswer(
                "VARCHAR(3)[LOB]",
                "SELECT SYSTEM$TYPEOF(DAYNAME(NULL))");
            assertAnswer(
                "null",
                "SELECT MONTHNAME(NULL)");
            assertAnswer(
                "VARCHAR(3)[LOB]",
                "SELECT SYSTEM$TYPEOF(MONTHNAME(NULL))");
            assertAnswer(
                "null",
                "SELECT EXTRACT(year FROM NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(year FROM NULL))");
            assertAnswer(
                "null",
                "SELECT DATE_PART('year', NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('year', NULL))");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(EXTRACT(week_iso FROM NULL))");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('dayofweekiso', NULL))");
            assertAnswer(
                "null",
                "SELECT TO_DATE(NULL)");
            assertAnswer(
                "DATE[SB4]",
                "SELECT SYSTEM$TYPEOF(TO_DATE(NULL))");
            assertAnswer(
                "TIMESTAMP_NTZ(9)[SB16]",
                "SELECT SYSTEM$TYPEOF(TO_TIMESTAMP(NULL))");
            assertAnswer(
                "TIME(9)[SB8]",
                "SELECT SYSTEM$TYPEOF(TO_TIME(NULL))");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(TRUNC(NULL))");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(TRUNC(NULL, 'month'))");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(TIMEDIFF(hour, NULL, NULL))");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(TIMESTAMPDIFF(hour, NULL, CURRENT_TIMESTAMP))");
            assertAnswer(
                "null|null|null",
                "DECLARE s DATE; r RESULTSET; BEGIN r := (SELECT WEEKISO(:s), YEAROFWEEKISO(:s), "
                    + "DAYOFWEEKISO(:s)); RETURN TABLE(r); END;");
            assertAnswer(
                "NULL[LOB]|NULL[LOB]|NULL[LOB]|NULL[LOB]",
                "DECLARE s DATE; r RESULTSET; BEGIN r := (SELECT SYSTEM$TYPEOF(WEEKISO(:s)), "
                    + "SYSTEM$TYPEOF(YEAROFWEEKISO(:s)), SYSTEM$TYPEOF(DAYOFWEEKISO(:s)), SYSTEM$TYPEOF(WEEK(:s))); "
                    + "RETURN TABLE(r); END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void monthsBetweenAndABlocksVariables() {
        engine.execute(
            "CREATE OR REPLACE TABLE tq (s VARCHAR(10), n INT, d DATE)");
        engine.execute(
            "INSERT INTO tq VALUES ('abc', NULL, NULL)");
        engine.execute(
            "CREATE OR REPLACE TABLE tw (s VARCHAR(10), n NUMBER(10,2), d DATE, t TIMESTAMP_NTZ)");
        engine.execute(
            "INSERT INTO tw VALUES ('abc', 1.5, '2024-05-06', '2024-05-06 10:11:12')");
        try {
            assertAnswer(
                "NUMBER(27,6)[SB16]",
                "SELECT SYSTEM$TYPEOF(MONTHS_BETWEEN('2024-03-15'::DATE, '2024-01-01'::DATE))");
            assertAnswer(
                "NUMBER(27,6)[SB16]",
                "SELECT SYSTEM$TYPEOF(MONTHS_BETWEEN('2024-03-15 10:00:00'::TIMESTAMP_NTZ, '2024-01-01'::DATE))");
            assertAnswer(
                "2.451613|2.000000",
                "SELECT MONTHS_BETWEEN('2024-03-15'::DATE, '2024-01-01'::DATE), MONTHS_BETWEEN('2024-03-01'::DATE,"
                    + " '2024-01-01'::DATE)");
            assertShape(
                "MONTHS_BETWEEN('2024-03-15'::DATE, '2024-01-01'::DATE):NUMBER(27,6), MONTHS_BETWEEN(NULL::DATE, "
                    + "'2024-01-01'::DATE):NUMBER(27,6)",
                "SELECT MONTHS_BETWEEN('2024-03-15'::DATE, '2024-01-01'::DATE), MONTHS_BETWEEN(NULL::DATE, "
                    + "'2024-01-01'::DATE)");
            assertAnswer(
                "NUMBER(27,6)[SB1]",
                "SELECT SYSTEM$TYPEOF(MONTHS_BETWEEN(d, CURRENT_DATE)) FROM tq");
            assertAnswer(
                "NUMBER(27,6)[SB16]",
                "SELECT SYSTEM$TYPEOF(MONTHS_BETWEEN(d, t)) FROM tw");
            assertShape(
                "MONTHS_BETWEEN(D, T):NUMBER(27,6)",
                "SELECT MONTHS_BETWEEN(d, t) FROM tw");
            assertAnswer(
                "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(134217728) argument "
                    + "type",
                "SELECT MONTHS_BETWEEN(NULL::VARCHAR, '2024-01-01'::DATE)");
            assertAnswer(
                "REFUSED SQL compilation error: | Function EXTRACT does not support NULL argument type",
                "SELECT MONTHS_BETWEEN(NULL::TIMESTAMP_NTZ, NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: | Function EXTRACT does not support NULL argument type",
                "SELECT MONTHS_BETWEEN(NULL, NULL::DATE)");
            assertAnswer(
                "REFUSED SQL compilation error: | Function EXTRACT does not support NULL argument type",
                "SELECT MONTHS_BETWEEN(d, NULL) FROM tq");
            assertAnswer(
                "REFUSED SQL compilation error: | Function EXTRACT does not support NULL argument type",
                "SELECT MONTHS_BETWEEN(NULL, d) FROM tq");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : SQL compilation "
                    + "error: | Function EXTRACT does not support NULL argument type",
                "DECLARE s VARCHAR; BEGIN RETURN MONTHS_BETWEEN(s, '2024-01-01'::DATE); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 40 : SQL compilation "
                    + "error: | Function EXTRACT does not support NULL argument type",
                "DECLARE s DATE; r RESULTSET; BEGIN r := (SELECT MONTHS_BETWEEN('2024-01-01'::DATE, :s)); RETURN "
                    + "TABLE(r); END;");
            assertAnswer(
                "2.000000",
                "DECLARE s DATE DEFAULT '2024-03-01'; r RESULTSET; BEGIN r := (SELECT MONTHS_BETWEEN(:s, "
                    + "'2024-01-01'::DATE)); RETURN TABLE(r); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 49 : SQL compilation "
                    + "error: | Function EXTRACT does not support NULL argument type",
                "DECLARE s TIMESTAMP_NTZ; r RESULTSET; BEGIN r := (SELECT MONTHS_BETWEEN(:s, "
                    + "'2024-01-01'::DATE)); RETURN TABLE(r); END;");
            assertAnswer(
                "null",
                "DECLARE s DATE; BEGIN RETURN YEAR(s); END;");
            assertAnswer(
                "DATE[SB4]",
                "DECLARE s DATE; BEGIN RETURN SYSTEM$TYPEOF(s); END;");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "DECLARE s DATE; BEGIN RETURN SYSTEM$TYPEOF(YEAR(s)); END;");
            assertAnswer(
                "null",
                "DECLARE s DATE; BEGIN RETURN DATEADD(DAY, 1, s); END;");
            assertAnswer(
                "null",
                "DECLARE s DATE; BEGIN RETURN TO_VARCHAR(s); END;");
            assertAnswer(
                "null",
                "DECLARE s TIMESTAMP_NTZ; BEGIN RETURN HOUR(s); END;");
            assertAnswer(
                "null",
                "DECLARE s TIME; BEGIN RETURN HOUR(s); END;");
            assertAnswer(
                "DATE[SB4]",
                "DECLARE s DATE; BEGIN RETURN SYSTEM$TYPEOF(DATEADD(DAY, 1, s)); END;");
            assertAnswer(
                "null",
                "DECLARE s DATE; BEGIN RETURN LAST_DAY(s); END;");
            assertAnswer(
                "null",
                "DECLARE s DATE; BEGIN RETURN COALESCE(s, s); END;");
            assertAnswer(
                "null",
                "DECLARE s DATE; BEGIN RETURN IFF(s IS NULL, 'null', 'set'); END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void theFoldComesBeforeTheUnitAndTheOperand() {
        engine.execute(
            "CREATE OR REPLACE TABLE tw (d DATE, t TIMESTAMP_NTZ, s VARCHAR(10), n NUMBER(10,2), i INT)");
        engine.execute(
            "INSERT INTO tw VALUES ('2024-05-06', '2024-05-06 10:11:12', 'abc', 1.5, 7), (NULL, NULL, NULL, NULL, "
                + "NULL)");
        try {
            assertAnswer(
                "null",
                "SELECT TIME_SLICE(CURRENT_TIMESTAMP, NULL, 'HOUR')");
            assertAnswer(
                "null",
                "SELECT TIME_SLICE('abc', NULL, 'HOUR')");
            assertAnswer(
                "null",
                "SELECT TIME_SLICE(1, NULL, 'HOUR')");
            assertAnswer(
                "null",
                "SELECT TIME_SLICE('10:00:00'::TIME, NULL, 'HOUR')");
            assertAnswer(
                "null",
                "SELECT TIME_SLICE(CURRENT_TIMESTAMP, 1, NULL)");
            assertAnswer(
                "null",
                "SELECT TIME_SLICE(NULL, 1, 'HOUR')");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(TIME_SLICE(NULL, 1, 'HOUR'))");
            assertAnswer(
                "null",
                "SELECT TIME_SLICE('2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ, NULL, 'HOUR')");
            assertAnswer(
                "null",
                "SELECT TIME_SLICE(CURRENT_TIMESTAMP, 1, 'HOUR', NULL)");
            assertAnswer(
                "NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(TIME_SLICE('2024-01-01 10:00:00'::TIMESTAMP_NTZ, 1, 'HOUR', NULL))");
            assertAnswer(
                "null",
                "SELECT TIME_SLICE('2024-01-01 10:00:00'::TIMESTAMP_NTZ, NULL, 'NOSUCH')");
            assertAnswer(
                "null",
                "SELECT TIME_SLICE('2024-01-01 10:00:00'::TIMESTAMP_NTZ, NULL::INT, 'HOUR')");
            assertAnswer(
                "TIMESTAMP_NTZ(9)[SB16]",
                "SELECT SYSTEM$TYPEOF(TIME_SLICE('2024-01-01 10:00:00'::TIMESTAMP_NTZ, NULL::INT, 'HOUR'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Function TIME_SLICE does not support TIMESTAMP_LTZ(9) argument "
                    + "type",
                "SELECT TIME_SLICE(CURRENT_TIMESTAMP, NULL::INT, 'HOUR')");
            assertAnswer(
                "REFUSED SQL compilation error: | Function TIME_SLICE does not support TIMESTAMP_LTZ(9) argument "
                    + "type",
                "SELECT TIME_SLICE(NULL::TIMESTAMP_LTZ, 1, 'HOUR')");
            assertAnswer(
                "null ;; null",
                "SELECT TIME_SLICE(t, NULL, 'HOUR') FROM tw");
            assertAnswer(
                "null",
                "SELECT DATEADD(day, NULL, 'abc')");
            assertAnswer(
                "null",
                "SELECT DATE_TRUNC('nosuch', NULL)");
            assertAnswer(
                "null",
                "SELECT DATEDIFF(day, 1, NULL)");
            assertAnswer(
                "null",
                "SELECT LAST_DAY(NULL, 'nosuch')");
            assertAnswer(
                "null",
                "SELECT EXTRACT(nosuch FROM NULL)");
            assertAnswer(
                "null",
                "SELECT DATE_PART('nosuch', NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                    + "[YEAR(null, 1)] expected 1, got 2",
                "SELECT YEAR(NULL, 1)");
            assertAnswer(
                "null",
                "SELECT DATEADD(nosuch, NULL, CURRENT_DATE)");
            assertAnswer(
                "null|null|null",
                "SELECT WEEKISO(NULL), DAYOFWEEKISO(NULL), YEAROFWEEKISO(NULL)");
            assertAnswer(
                "NULL[LOB]|NULL[LOB]",
                "SELECT SYSTEM$TYPEOF(WEEKISO(NULL)), SYSTEM$TYPEOF(YEAROFWEEKISO(NULL))");
            assertShape(
                "WEEKOFYEAR(D):NUMBER(2,0), DAYOFWEEKISO(D):NUMBER(2,0), WEEKISO(D):NUMBER(2,0), "
                    + "YEAROFWEEKISO(D):NUMBER(4,0), YEAROFWEEK(D):NUMBER(4,0), DAYOFWEEK(D):NUMBER(2,0), "
                    + "QUARTER(D):NUMBER(2,0), MONTHS_BETWEEN(D, D):NUMBER(27,6), MONTHS_BETWEEN(T, D):NUMBER(27,6), "
                    + "MONTHS_BETWEEN(T, T):NUMBER(27,6)",
                "SELECT WEEKOFYEAR(d), DAYOFWEEKISO(d), WEEKISO(d), YEAROFWEEKISO(d), YEAROFWEEK(d), DAYOFWEEK(d),"
                    + " QUARTER(d), MONTHS_BETWEEN(d, d), MONTHS_BETWEEN(t, d), MONTHS_BETWEEN(t, t) FROM tw");
            assertAnswer(
                "1.000000|1.048387|-2.451613",
                "SELECT MONTHS_BETWEEN('2024-03-31'::DATE, '2024-02-29'::DATE), MONTHS_BETWEEN('2024-03-15 "
                    + "12:00:00'::TIMESTAMP_NTZ, '2024-02-14'::DATE), MONTHS_BETWEEN('2024-01-01'::DATE, "
                    + "'2024-03-15'::DATE)");
            assertAnswer(
                "NUMBER(27,6)[SB16]",
                "SELECT SYSTEM$TYPEOF(MONTHS_BETWEEN('2024-03-15'::TIMESTAMP_TZ, '2024-01-01'::TIMESTAMP_LTZ))");
            assertAnswer(
                "NUMBER(28,6)[SB16]",
                "SELECT SYSTEM$TYPEOF(MONTHS_BETWEEN('2024-03-15'::DATE, '2024-01-01'::DATE) + 1)");
            assertAnswer(
                "NUMBER(28,0)[SB16]",
                "SELECT SYSTEM$TYPEOF(ROUND(MONTHS_BETWEEN('2024-03-15'::DATE, '2024-01-01'::DATE)))");
            assertShape(
                "MONTHS_BETWEEN(D, D) + 1:NUMBER(28,6), MONTHS_BETWEEN(D, D) * 2:NUMBER(28,6), "
                    + "ROUND(MONTHS_BETWEEN(D, D), 2):NUMBER(28,2), SUM(MONTHS_BETWEEN(D, D)):NUMBER(38,6)",
                "SELECT MONTHS_BETWEEN(d, d) + 1, MONTHS_BETWEEN(d, d) * 2, ROUND(MONTHS_BETWEEN(d, d), 2), "
                    + "SUM(MONTHS_BETWEEN(d, d)) FROM tw GROUP BY ALL");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
