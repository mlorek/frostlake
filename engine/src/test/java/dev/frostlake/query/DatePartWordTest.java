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
 * A date part's word is a word only where a date function's unit stands: DATEADD, DATEDIFF, DATE_TRUNC,
 * DATE_PART and their TIME and TIMESTAMP spellings in the first place, LAST_DAY in the second. There it names
 * the part whatever column or variable has that name, quoted or bare, and declares the part's width either
 * way. Anywhere else it is a name like any other - UPPER(ms), SYSTEM$TYPEOF(s), COUNT(day), TRUNC(d, month),
 * TIME_SLICE(t, 1, hour) - refused as an invalid identifier at its place when no column has it, in a query, in
 * DML and in a statement a block runs, where a block's variable of that name is no column either. Every
 * answer is live-verified.
 */
public class DatePartWordTest extends MeasuredCellsTestSupport {

    @Test
    public void aBarePartDeclaresThePartsWidth() {
        try {
            assertShape(
                "Y1:NUMBER(4,0), Y2:NUMBER(4,0), M1:NUMBER(2,0), E1:NUMBER(4,0)",
                "SELECT DATE_PART(year, '2024-05-06'::DATE) AS y1, DATE_PART('year', '2024-05-06'::DATE) AS y2, "
                    + "DATE_PART(month, '2024-05-06'::DATE) AS m1, EXTRACT(year FROM '2024-05-06'::DATE) AS e1");
            assertAnswer(
                "2024|2024|5|2024",
                "SELECT DATE_PART(year, '2024-05-06'::DATE) AS y1, DATE_PART('year', '2024-05-06'::DATE) AS y2, "
                    + "DATE_PART(month, '2024-05-06'::DATE) AS m1, EXTRACT(year FROM '2024-05-06'::DATE) AS e1");
            assertAnswer(
                "Table DPC successfully created.",
                "CREATE OR REPLACE TABLE dpc AS SELECT DATE_PART(year, '2024-05-06'::DATE) AS y1, "
                    + "DATE_PART('year', '2024-05-06'::DATE) AS y2");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(year, '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(month, '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(year, NULL::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(month, NULL::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('year', '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('month', '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(day, '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('day', '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(hour, '2024-05-06 10:00:00'::TIMESTAMP_NTZ))");
            assertAnswer(
                "NUMBER(18,0)[SB8]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(epoch_second, '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(18,0)[SB8]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('epoch_second', '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(yy, '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DATE_PART('yy', '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(YEAR, '2024-05-06'::DATE))");
            assertAnswer(
                "NUMBER(4,0)[SB2]",
                "SELECT SYSTEM$TYPEOF(DATE_PART(\"YEAR\", '2024-05-06'::DATE))");
            assertShape(
                "DATE_PART(DAY, '2024-05-06'::DATE):NUMBER(2,0), DATE_PART(HOUR, '2024-05-06 "
                    + "10:00:00'::TIMESTAMP_NTZ):NUMBER(2,0), DATE_PART(EPOCH_SECOND, '2024-05-06'::DATE):NUMBER(18,"
                    + "0), DATE_PART(QUARTER, '2024-05-06'::DATE):NUMBER(2,0), DATE_PART(WEEK, "
                    + "'2024-05-06'::DATE):NUMBER(2,0)",
                "SELECT DATE_PART(day, '2024-05-06'::DATE), DATE_PART(hour, '2024-05-06 10:00:00'::TIMESTAMP_NTZ),"
                    + " DATE_PART(epoch_second, '2024-05-06'::DATE), DATE_PART(quarter, '2024-05-06'::DATE), "
                    + "DATE_PART(week, '2024-05-06'::DATE)");
            assertAnswer(
                "Table DPD successfully created.",
                "CREATE OR REPLACE TABLE dpd AS SELECT DATE_PART(month, d) AS m, DATE_PART(dayofweek, d) AS w, "
                    + "DATE_PART(epoch_second, d) AS e FROM (SELECT '2024-05-06'::DATE AS d)");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void aVariablesNameInAStatement() {
        engine.execute(
            "CREATE OR REPLACE TABLE ts (a INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        engine.execute(
            "INSERT INTO tp VALUES (1, 10), (2, 20)");
        try {
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 40 : SQL compilation "
                    + "error: error line 1 at position 21 | invalid identifier 'S'",
                "DECLARE s VARCHAR(10); t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(s) INTO :t; RETURN t; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 49 : SQL compilation "
                    + "error: error line 1 at position 21 | invalid identifier 'S'",
                "DECLARE s VARCHAR(10); BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(s) AS ty); RETURN "
                    + "TABLE(r); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 40 : SQL compilation "
                    + "error: error line 1 at position 7 | invalid identifier 'S'",
                "DECLARE s VARCHAR(10); t VARCHAR; BEGIN SELECT s INTO :t; RETURN t; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 40 : SQL compilation "
                    + "error: error line 1 at position 21 | invalid identifier 'NOSUCH'",
                "DECLARE s VARCHAR(10); t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(nosuch) INTO :t; RETURN t; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'NOSUCH'",
                "SELECT SYSTEM$TYPEOF(nosuch)");
            assertAnswer(
                "VARCHAR[LOB]",
                "DECLARE s VARCHAR(10); BEGIN RETURN SYSTEM$TYPEOF(s); END;");
            assertAnswer(
                "VARCHAR(10)[LOB]",
                "DECLARE s VARCHAR(10); t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:s) INTO :t; RETURN t; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 54 : SQL compilation "
                    + "error: error line 1 at position 21 | invalid identifier 'S'",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(s) INTO :t; RETURN t; "
                    + "END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : SQL compilation "
                    + "error: error line 1 at position 21 | invalid identifier 'S'",
                "DECLARE s INT DEFAULT 1; t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(s + 1) INTO :t; RETURN t; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 42 |  Repeated variable with name 'T' "
                    + "inside the INTO clause.",
                "DECLARE s INT DEFAULT 1; t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(k), SYSTEM$TYPEOF(s) INTO :t, :t "
                    + "FROM tp LIMIT 1; RETURN t; END;");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "DECLARE k INT DEFAULT 1; t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(k) INTO :t FROM tp LIMIT 1; "
                    + "RETURN t; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 57 : SQL compilation "
                    + "error: error line 1 at position 21 | invalid identifier 'S'",
                "DECLARE s INT DEFAULT 1; t VARCHAR; BEGIN IF (TRUE) THEN SELECT SYSTEM$TYPEOF(s) INTO :t; END "
                    + "IF; RETURN t; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 1 at position 43 | invalid identifier 'S'",
                "DECLARE s INT DEFAULT 1; BEGIN INSERT INTO ts SELECT LENGTH(SYSTEM$TYPEOF(s)); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : SQL compilation "
                    + "error: error line 1 at position 14 | invalid identifier 'S'",
                "DECLARE s INT DEFAULT 1; t VARCHAR; BEGIN SELECT TYPEOF(s) INTO :t; RETURN t; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : SQL compilation "
                    + "error: error line 1 at position 35 | invalid identifier 'S'",
                "DECLARE s INT DEFAULT 1; t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(SYSTEM$TYPEOF(s)) INTO :t; RETURN "
                    + "t; END;");
            assertAnswer(
                "Function PTY successfully created.",
                "CREATE OR REPLACE PROCEDURE pty(s INT) RETURNS VARCHAR LANGUAGE SQL AS 'DECLARE t VARCHAR; BEGIN "
                    + "SELECT SYSTEM$TYPEOF(s) INTO :t; RETURN t; END;'");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : SQL compilation "
                    + "error: error line 1 at position 21 | invalid identifier 'S'",
                "CALL pty(1)");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void aPartsWordOutsideAUnitPosition() {
        engine.execute(
            "CREATE OR REPLACE TABLE ts (a INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        engine.execute(
            "INSERT INTO tp VALUES (1, 10), (2, 20)");
        engine.execute(
            "CREATE OR REPLACE TABLE tv (c VARCHAR(10))");
        engine.execute(
            "INSERT INTO tv VALUES ('MS'), ('x')");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 33 | invalid identifier 'MONTH'",
                "SELECT TRUNC('2024-05-06'::DATE, month)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 59 | invalid identifier 'HOUR'",
                "SELECT TIME_SLICE('2024-05-06 10:20:00'::TIMESTAMP_NTZ, 1, hour)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | invalid identifier 'MO'",
                "SELECT NEXT_DAY('2024-05-06'::DATE, mo)");
            assertAnswer(
                "2024-05-31",
                "SELECT LAST_DAY('2024-05-06'::DATE, month)");
            assertAnswer(
                "2024-05-07|2024|0",
                "SELECT DATEADD(day, 1, '2024-05-06'::DATE), DATE_PART(year, '2024-05-06'::DATE), DATEDIFF(day, "
                    + "'2024-05-06'::DATE, '2024-05-06'::DATE)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 20 | invalid identifier 'DAY'",
                "SELECT DATEADD(day, day, '2024-05-06'::DATE)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'YEAR'",
                "SELECT DATEADD(day, 1, year)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 25 | invalid identifier 'MONTH'",
                "SELECT DATE_TRUNC(month, month)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'MS'",
                "SELECT UPPER(ms)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'S'",
                "SELECT SYSTEM$TYPEOF(s)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'YEAR'",
                "SELECT TYPEOF(year)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 16 | invalid identifier 'MONTH'",
                "SELECT LAST_DAY(month, month)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'S'",
                "SELECT DATE_PART(year, s)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | invalid identifier 'FR'",
                "SELECT PREVIOUS_DAY('2024-05-06'::DATE, fr)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 17 | invalid identifier 'MONTH'",
                "SELECT MONTHNAME(month)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 24 | invalid identifier 'HOUR'",
                "SELECT CONVERT_TIMEZONE(hour, '2024-05-06'::TIMESTAMP_NTZ)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier 'DAY'",
                "SELECT TO_DATE(day)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'DAY'",
                "SELECT COUNT(day)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 11 | invalid identifier 'MS'",
                "SELECT SUM(ms) OVER ()");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'YEAR'",
                "SELECT LENGTH(year)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 16 | invalid identifier 'DAY'",
                "SELECT COALESCE(day, 1)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 18 | invalid identifier 'MONTH'",
                "SELECT TO_VARCHAR(month)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 17 | invalid identifier 'HOUR'",
                "SELECT IFF(TRUE, hour, 1)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'S'",
                "SELECT 1 WHERE UPPER(s) = 1");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 33 | invalid identifier 'MS'",
                "SELECT c FROM tv WHERE c = UPPER(ms)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 33 | invalid identifier 'MS'",
                "INSERT INTO tv (c) VALUES (UPPER(ms))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 24 | invalid identifier 'MS'",
                "UPDATE tv SET c = UPPER(ms)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'MS'",
                "DELETE FROM tv WHERE c = UPPER(ms)");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM tv");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : SQL compilation "
                    + "error: error line 1 at position 33 | invalid identifier 'S'",
                "DECLARE s VARCHAR DEFAULT 'q'; BEGIN INSERT INTO tv (c) VALUES (UPPER(s)); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 22 | invalid identifier 'YEAR'",
                "SELECT DATEDIFF(year, year, '2024-05-06'::DATE)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 24 | invalid identifier 'S'",
                "SELECT TIMEDIFF(second, s, s)");
            assertAnswer(
                "2024-06-06|2024-05-06T01:00|0",
                "SELECT DATEADD(month, 1, '2024-05-06'::DATE), TIMEADD(hour, 1, "
                    + "'2024-05-06'::DATE::TIMESTAMP_NTZ), TIMESTAMPDIFF(day, '2024-05-06'::DATE, '2024-05-06'::DATE)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'DAY'",
                "SELECT ARRAY_CONSTRUCT(day, month)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 29 | invalid identifier 'YEAR'",
                "SELECT OBJECT_CONSTRUCT('a', year)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 16 | invalid identifier 'WEEK'",
                "SELECT GREATEST(week, 1)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'QUARTER'",
                "SELECT CASE WHEN TRUE THEN quarter END");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 19 | invalid identifier 'MINUTE'",
                "SELECT (SELECT MAX(minute))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 24 | invalid identifier 'D'",
                "SELECT 1 ORDER BY UPPER(d)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | invalid identifier 'DD'",
                "SELECT v FROM tp GROUP BY v HAVING MAX(dd) > 1");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 64 | invalid identifier 'YEAR'",
                "SELECT EXTRACT(year FROM '2024-05-06'::DATE), EXTRACT(year FROM year)");
            assertAnswer(
                "12",
                "SELECT DATE_PART(s, '2024-05-06 10:11:12'::TIMESTAMP_NTZ)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 30 | invalid identifier 'W'",
                "SELECT k FROM tp WHERE LENGTH(w) = 1");
            assertAnswer(
                "1 ;; 1",
                "SELECT LENGTH(k) FROM tp ORDER BY 1");
            assertAnswer(
                "Table TU successfully created.",
                "CREATE OR REPLACE TABLE tu (day INT, s VARCHAR)");
            assertAnswer(
                "1",
                "INSERT INTO tu VALUES (3, 'ab')");
            assertAnswer(
                "AB|1|2024-05-09|VARCHAR(16777216)[LOB]",
                "SELECT UPPER(s), LENGTH(day), DATEADD(day, day, '2024-05-06'::DATE), SYSTEM$TYPEOF(s) FROM tu");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
