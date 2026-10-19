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
 * The text a CSV unload writes, read back as one undivided field. A NULL is NULL_IF's first value — the
 * two characters backslash and N by default, written with the backslash escaped — or nothing for NULL_IF
 * = (); outside an enclosure the field delimiter, the record delimiter and the escape itself are written
 * behind ESCAPE_UNENCLOSED_FIELD; with FIELD_OPTIONALLY_ENCLOSED_BY a text, a date, a time, a timestamp, a
 * binary and a semi-structured value are enclosed, the enclosure doubled or behind ESCAPE, and a number and
 * a boolean are not; an empty text is refused where nothing could tell it from a NULL. A named format and
 * the stage's own format supply every option. Every answer is live-verified.
 */
public class CsvUnloadTextTest extends MeasuredCellsTestSupport {

    @Test
    public void eachOptionShapesTheText() {
        engine.execute("CREATE OR REPLACE STAGE ust");
        engine.execute("CREATE OR REPLACE FILE FORMAT raw_file TYPE = CSV FIELD_DELIMITER = NONE RECORD_DELIMITER = NONE "
            + "ESCAPE_UNENCLOSED_FIELD = NONE NULL_IF = () EMPTY_FIELD_AS_NULL = FALSE");
        engine.execute("CREATE OR REPLACE FILE FORMAT plain_csv TYPE = CSV");
        assertAnswer(
            "1|8|8",
            "COPY INTO @ust/u1 FROM (SELECT NULL AS v, 'x' AS w, 1 AS n) FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE) SINGLE = TRUE");
        assertAnswer(
            "\\\\N,x,1 | ",
            "SELECT $1 FROM @ust/u1 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "true|x|1",
            "SELECT $1 IS NULL, $2, $3 FROM @ust/u1 (FILE_FORMAT => 'plain_csv')");
        assertAnswer(
            "1|7|7",
            "COPY INTO @ust/u2 FROM (SELECT NULL AS v, 'x' AS w, 1 AS n) FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE ESCAPE_UNENCLOSED_FIELD = NONE) SINGLE = TRUE");
        assertAnswer(
            "\\N,x,1 | ",
            "SELECT $1 FROM @ust/u2 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "1|7|7",
            "COPY INTO @ust/u3 FROM (SELECT NULL AS v, 'x' AS w, 1 AS n) FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE NULL_IF = ('NA', 'none')) SINGLE = TRUE");
        assertAnswer(
            "NA,x,1 | ",
            "SELECT $1 FROM @ust/u3 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "1|5|5",
            "COPY INTO @ust/u4 FROM (SELECT NULL AS v, 'x' AS w, 1 AS n) FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE NULL_IF = ()) SINGLE = TRUE");
        assertAnswer(
            ",x,1 | ",
            "SELECT $1 FROM @ust/u4 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "REFUSED Cannot unload empty string without file format option field_optionally_enclosed_by being "
                + "specified.",
            "COPY INTO @ust/u5 FROM (SELECT NULL AS v, 'x' AS w, 1 AS n) FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE NULL_IF = ('')) SINGLE = TRUE");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust/u5 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "1|28|28",
            "COPY INTO @ust/u6 FROM (SELECT 'a,b' AS c, 'a\"b' AS q, 'a\\\\b' AS b, 'a' || CHR(10) || 'b' AS n, "
                + "' a ' AS sp, 'it''s' AS ap) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        assertAnswer(
            "a\\,b,a\"b,a\\\\b,a\\ | b, a ,it's | ",
            "SELECT $1 FROM @ust/u6 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "1|38|38",
            "COPY INTO @ust/u7 FROM (SELECT 'a,b' AS c, 'a\"b' AS q, 'a\\\\b' AS b, 'a' || CHR(10) || 'b' AS n, "
                + "' a ' AS sp, 'it''s' AS ap) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE "
                + "FIELD_OPTIONALLY_ENCLOSED_BY = '\"') SINGLE = TRUE");
        assertAnswer(
            "\"a,b\",\"a\"\"b\",\"a\\b\",\"a | b\",\" a \",\"it's\" | ",
            "SELECT $1 FROM @ust/u7 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "1|38|38",
            "COPY INTO @ust/u8 FROM (SELECT 'a,b' AS c, 'a\"b' AS q, 'a\\\\b' AS b, 'a' || CHR(10) || 'b' AS n, "
                + "' a ' AS sp, 'it''s' AS ap) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE "
                + "FIELD_OPTIONALLY_ENCLOSED_BY = '\\'') SINGLE = TRUE");
        assertAnswer(
            "'a,b','a\"b','a\\b','a | b',' a ','it''s' | ",
            "SELECT $1 FROM @ust/u8 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "1|25|25",
            "COPY INTO @ust/u9 FROM (SELECT 'a,b' AS c, 'a\"b' AS q, 'a\\\\b' AS b, 'a' || CHR(10) || 'b' AS n, "
                + "' a ' AS sp, 'it''s' AS ap) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE "
                + "ESCAPE_UNENCLOSED_FIELD = NONE) SINGLE = TRUE");
        assertAnswer(
            "a,b,a\"b,a\\b,a | b, a ,it's | ",
            "SELECT $1 FROM @ust/u9 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "1|38|38",
            "COPY INTO @ust/u10 FROM (SELECT 'a,b' AS c, 'a\"b' AS q, 'a\\\\b' AS b, 'a' || CHR(10) || 'b' AS n, "
                + "' a ' AS sp, 'it''s' AS ap) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE "
                + "FIELD_OPTIONALLY_ENCLOSED_BY = '\"' ESCAPE = '!') SINGLE = TRUE");
        assertAnswer(
            "\"a,b\",\"a!\"b\",\"a\\b\",\"a | b\",\" a \",\"it's\" | ",
            "SELECT $1 FROM @ust/u10 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "1|129|129",
            "COPY INTO @ust/u11 FROM (SELECT 1.50::NUMBER(10,2) AS n, 1.5::FLOAT AS f, TRUE AS b, "
                + "'2024-01-02'::DATE AS d, '2024-01-02 03:04:05.678'::TIMESTAMP_NTZ AS t, '03:04:05'::TIME AS "
                + "tm, TO_BINARY('ab', 'UTF-8') AS bin, PARSE_JSON('{\"a\":1}') AS v, ARRAY_CONSTRUCT(1, 'x') AS "
                + "arr, '2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ AS tz, 100000000000::NUMBER AS big, "
                + "1e20::FLOAT AS ef) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        assertAnswer(
            "1|146|146",
            "COPY INTO @ust/u12 FROM (SELECT 1.50::NUMBER(10,2) AS n, 1.5::FLOAT AS f, TRUE AS b, "
                + "'2024-01-02'::DATE AS d, '2024-01-02 03:04:05.678'::TIMESTAMP_NTZ AS t, '03:04:05'::TIME AS "
                + "tm, TO_BINARY('ab', 'UTF-8') AS bin, PARSE_JSON('{\"a\":1}') AS v, ARRAY_CONSTRUCT(1, 'x') AS "
                + "arr, '2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ AS tz, 100000000000::NUMBER AS big, "
                + "1e20::FLOAT AS ef) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE FIELD_OPTIONALLY_ENCLOSED_BY "
                + "= '\"') SINGLE = TRUE");
        assertAnswer(
            "REFUSED Cannot unload empty string without file format option field_optionally_enclosed_by being "
                + "specified.",
            "COPY INTO @ust/u13 FROM (SELECT '' AS v, 'x' AS w) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) "
                + "SINGLE = TRUE");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust/u13 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "1|11|11",
            "COPY INTO @ust/u14 FROM (SELECT '' AS v, 'x' AS w, NULL AS n) FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE FIELD_OPTIONALLY_ENCLOSED_BY = '\"') SINGLE = TRUE");
        assertAnswer(
            "\"\",\"x\",\\\\N | ",
            "SELECT $1 FROM @ust/u14 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "1|7|7",
            "COPY INTO @ust/u15 FROM (SELECT '' AS v, 'x' AS w, NULL AS n) FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE EMPTY_FIELD_AS_NULL = FALSE) SINGLE = TRUE");
        assertAnswer(
            ",x,\\\\N | ",
            "SELECT $1 FROM @ust/u15 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "REFUSED Cannot unload empty string without file format option field_optionally_enclosed_by being "
                + "specified.",
            "COPY INTO @ust/u16 FROM (SELECT '' AS v, 'x' AS w) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE "
                + "NULL_IF = ()) SINGLE = TRUE");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust/u16 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "REFUSED Cannot unload empty string without file format option field_optionally_enclosed_by being "
                + "specified.",
            "COPY INTO @ust/u17 FROM (SELECT '' AS v, 'x' AS w) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE "
                + "NULL_IF = ('')) SINGLE = TRUE");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust/u17 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "REFUSED Cannot unload empty string without file format option field_optionally_enclosed_by being "
                + "specified.",
            "COPY INTO @ust/u18 FROM (SELECT '' AS v, 'x' AS w) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE "
                + "ESCAPE_UNENCLOSED_FIELD = NONE) SINGLE = TRUE");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust/u18 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "1|13|13",
            "COPY INTO @ust/u19 FROM (SELECT 'a|b' AS c, 'x,y' AS w, NULL AS n) FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE FIELD_DELIMITER = '|') SINGLE = TRUE");
        assertAnswer(
            "a\\|b|x,y|\\\\N | ",
            "SELECT $1 FROM @ust/u19 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "1|10|10",
            "COPY INTO @ust/u20 FROM (SELECT 'a|b' AS c, 'x' AS w, NULL AS n) FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE FIELD_DELIMITER = '\\t') SINGLE = TRUE");
        assertAnswer(
            "a|b	x	\\\\N | ",
            "SELECT $1 FROM @ust/u20 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "1|15|15",
            "COPY INTO @ust/u21 FROM (SELECT 'a|b' AS c, 'x||y' AS w, NULL AS n) FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE FIELD_DELIMITER = '||') SINGLE = TRUE");
        assertAnswer(
            "a|b||x||y||\\\\N | ",
            "SELECT $1 FROM @ust/u21 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "2|11|11",
            "COPY INTO @ust/u22 FROM (SELECT 1 AS a, 'x' AS b UNION ALL SELECT 2, 'y;z' ORDER BY 1) "
                + "FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE RECORD_DELIMITER = ';') SINGLE = TRUE");
        assertAnswer(
            "1,x;2,y\\;z;",
            "SELECT $1 FROM @ust/u22 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "2|10|10",
            "COPY INTO @ust/u23 FROM (SELECT 1 AS a, 'x' AS b UNION ALL SELECT 2, 'y' ORDER BY 1) FILE_FORMAT "
                + "= (TYPE = CSV COMPRESSION = NONE RECORD_DELIMITER = '\\r\\n') SINGLE = TRUE");
        assertAnswer(
            "1|18|18",
            "COPY INTO @ust/u24 FROM (SELECT 1 AS \"a b\", 'x' AS w, NULL AS \"n,m\") FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE) HEADER = TRUE SINGLE = TRUE");
        assertAnswer(
            "a b,W,n,m | 1,x,\\\\N | ",
            "SELECT $1 FROM @ust/u24 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "1|26|26",
            "COPY INTO @ust/u25 FROM (SELECT 1 AS \"a b\", 'x' AS w, NULL AS \"n,m\") FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE FIELD_OPTIONALLY_ENCLOSED_BY = '\"') HEADER = TRUE SINGLE = TRUE");
        assertAnswer(
            "\"a b\",\"W\",\"n,m\" | 1,\"x\",\\\\N | ",
            "SELECT $1 FROM @ust/u25 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "1|42|42",
            "COPY INTO @ust/u26 FROM (SELECT '2024-01-02'::DATE AS d, '2024-01-02 "
                + "03:04:05.678'::TIMESTAMP_NTZ AS t, '03:04:05'::TIME AS tm, TO_BINARY('ab', 'UTF-8') AS bin) "
                + "FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE DATE_FORMAT = 'DD/MM/YYYY' TIMESTAMP_FORMAT = "
                + "'YYYY-MM-DD\"T\"HH24:MI:SS' TIME_FORMAT = 'HH24-MI' BINARY_FORMAT = BASE64) SINGLE = TRUE");
        engine.execute("CREATE OR REPLACE FILE FORMAT uff TYPE = CSV COMPRESSION = NONE FIELD_DELIMITER = '|' NULL_IF = "
            + "('NIL') FIELD_OPTIONALLY_ENCLOSED_BY = '\"'");
        assertAnswer(
            "1|10|10",
            "COPY INTO @ust/named1 FROM (SELECT NULL AS v, 'x' AS w, 1 AS n) FILE_FORMAT = (FORMAT_NAME = "
                + "'uff') SINGLE = TRUE");
        assertAnswer(
            "NIL|\"x\"|1 | ",
            "SELECT $1 FROM @ust/named1 (FILE_FORMAT => 'raw_file')");
        engine.execute("CREATE OR REPLACE STAGE ust2 FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE FIELD_DELIMITER = '|' "
            + "NULL_IF = ('NIL'))");
        assertAnswer(
            "1|8|8",
            "COPY INTO @ust2/s1 FROM (SELECT NULL AS v, 'x' AS w, 1 AS n) SINGLE = TRUE");
        assertAnswer(
            "NIL|x|1 | ",
            "SELECT $1 FROM @ust2/s1 (FILE_FORMAT => 'raw_file')");
        engine.execute("CREATE OR REPLACE TABLE ut_src (a INT, s VARCHAR, n VARCHAR)");
        engine.execute("INSERT INTO ut_src VALUES (1, 'x', NULL), (2, 'y z', 'q')");
        assertAnswer(
            "2|16|16",
            "COPY INTO @ust/tab1 FROM ut_src FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        assertAnswer(
            "1,x,\\\\N | 2,y z,q | ",
            "SELECT $1 FROM @ust/tab1 (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "1|8|8",
            "COPY INTO @ust/nt1 FROM (SELECT NULL AS v, 'x' AS w, 1 AS n) FILE_FORMAT = (COMPRESSION = NONE) "
                + "SINGLE = TRUE");
        assertAnswer(
            "\\\\N,x,1 | ",
            "SELECT $1 FROM @ust/nt1 (FILE_FORMAT => 'raw_file')");
    }

    @Test
    public void datesTimesAndBinariesAreWrittenByTheirFormats() {
        try {
            engine.execute("CREATE OR REPLACE STAGE tst");
            engine.execute("CREATE OR REPLACE FILE FORMAT raw_file TYPE = CSV FIELD_DELIMITER = NONE RECORD_DELIMITER = NONE "
            + "ESCAPE_UNENCLOSED_FIELD = NONE NULL_IF = () EMPTY_FIELD_AS_NULL = FALSE");
            engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "1|143|143",
                "COPY INTO @tst/t1 FROM (SELECT '2024-01-02'::DATE AS d, '2024-01-02 03:04:05.678'::TIMESTAMP_NTZ "
                + "AS t, '03:04:05'::TIME AS tm, '2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ AS tz, '2024-01-02 "
                + "03:04:05'::TIMESTAMP_LTZ AS ltz, '2024-01-02 03:04:05.123456789'::TIMESTAMP_NTZ(9) AS t9, "
                + "TO_BINARY('ÿb', 'UTF-8') AS bin, NULL::DATE AS nd, NULL::NUMBER AS nn, NULL::VARCHAR AS nv) "
                + "FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
            assertAnswer(
                "2024-01-02,2024-01-02 03;04;05.678,03;04;05,2024-01-02 03;04;05.000 +0200,2024-01-02 "
                + "03;04;05.000 Z,2024-01-02 03;04;05.123,C3BF62,\\\\N,\\\\N,\\\\N | ",
                "SELECT REPLACE($1, ':', ';') FROM @tst/t1 (FILE_FORMAT => 'raw_file')");
            assertAnswer(
                "1|159|159",
                "COPY INTO @tst/t2 FROM (SELECT '2024-01-02'::DATE AS d, '2024-01-02 03:04:05.678'::TIMESTAMP_NTZ "
                + "AS t, '03:04:05'::TIME AS tm, '2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ AS tz, '2024-01-02 "
                + "03:04:05'::TIMESTAMP_LTZ AS ltz, '2024-01-02 03:04:05.123456789'::TIMESTAMP_NTZ(9) AS t9, "
                + "TO_BINARY('ÿb', 'UTF-8') AS bin, NULL::DATE AS nd, NULL::NUMBER AS nn, NULL::VARCHAR AS nv) "
                + "FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE FIELD_OPTIONALLY_ENCLOSED_BY = '\"') SINGLE = TRUE");
            assertAnswer(
                "\"2024-01-02\",\"2024-01-02 03;04;05.678\",\"03;04;05\",\"2024-01-02 03;04;05.000 +0200\",\"2024-01-02 "
                + "03;04;05.000 Z\",\"2024-01-02 03;04;05.123\",\"C3BF62\",\"\\N\",\\\\N,\"\\N\" | ",
                "SELECT REPLACE($1, ':', ';') FROM @tst/t2 (FILE_FORMAT => 'raw_file')");
            assertAnswer(
                "1|114|114",
                "COPY INTO @tst/t3 FROM (SELECT '2024-01-02'::DATE AS d, '2024-01-02 03:04:05.678'::TIMESTAMP_NTZ "
                + "AS t, '03:04:05'::TIME AS tm, '2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ AS tz, '2024-01-02 "
                + "03:04:05'::TIMESTAMP_LTZ AS ltz, '2024-01-02 03:04:05.123456789'::TIMESTAMP_NTZ(9) AS t9, "
                + "TO_BINARY('ÿb', 'UTF-8') AS bin, NULL::DATE AS nd, NULL::NUMBER AS nn, NULL::VARCHAR AS nv) "
                + "FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE DATE_FORMAT = 'DD/MM/YYYY' TIMESTAMP_FORMAT = "
                + "'YYYY-MM-DD\"T\"HH24:MI:SS' TIME_FORMAT = 'HH24-MI' BINARY_FORMAT = BASE64) SINGLE = TRUE");
            assertAnswer(
                "02/01/2024,2024-01-02T03;04;05,03-04,2024-01-02T03;04;05,2024-01-02T03;04;05,2024-01-02T03;04;05,w79i,\\\\N,\\\\N,\\\\N | ",
                "SELECT REPLACE($1, ':', ';') FROM @tst/t3 (FILE_FORMAT => 'raw_file')");
            assertAnswer(
                "1|143|143",
                "COPY INTO @tst/t4 FROM (SELECT '2024-01-02'::DATE AS d, '2024-01-02 03:04:05.678'::TIMESTAMP_NTZ "
                + "AS t, '03:04:05'::TIME AS tm, '2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ AS tz, '2024-01-02 "
                + "03:04:05'::TIMESTAMP_LTZ AS ltz, '2024-01-02 03:04:05.123456789'::TIMESTAMP_NTZ(9) AS t9, "
                + "TO_BINARY('ÿb', 'UTF-8') AS bin, NULL::DATE AS nd, NULL::NUMBER AS nn, NULL::VARCHAR AS nv) "
                + "FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE DATE_FORMAT = AUTO TIMESTAMP_FORMAT = AUTO "
                + "TIME_FORMAT = AUTO BINARY_FORMAT = HEX) SINGLE = TRUE");
            assertAnswer(
                "2024-01-02,2024-01-02 03;04;05.678,03;04;05,2024-01-02 03;04;05.000 +0200,2024-01-02 "
                + "03;04;05.000 Z,2024-01-02 03;04;05.123,C3BF62,\\\\N,\\\\N,\\\\N | ",
                "SELECT REPLACE($1, ':', ';') FROM @tst/t4 (FILE_FORMAT => 'raw_file')");
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aNullAndAnEmptyTextAreToldApart() {
        engine.execute("CREATE OR REPLACE STAGE ust");
        engine.execute("CREATE OR REPLACE FILE FORMAT raw_csv TYPE = CSV NULL_IF = () FIELD_DELIMITER = '|' "
            + "EMPTY_FIELD_AS_NULL = FALSE");
        assertAnswer(
            "1|6|6",
            "COPY INTO @ust/nul FROM (SELECT NULL AS v, 'x' AS w) FILE_FORMAT = (TYPE = CSV COMPRESSION = "
                + "NONE FIELD_DELIMITER = '|') SINGLE = TRUE");
        assertAnswer(
            "\\N|x",
            "SELECT $1, $2 FROM @ust/nul (FILE_FORMAT => 'raw_csv')");
        assertAnswer(
            "REFUSED Cannot unload empty string without file format option field_optionally_enclosed_by being "
                + "specified.",
            "COPY INTO @ust/emp FROM (SELECT '' AS v, 'x' AS w) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE "
                + "FIELD_DELIMITER = '|') SINGLE = TRUE");
        assertAnswer(
            "1|11|11",
            "COPY INTO @ust/enc FROM (SELECT '' AS v, NULL AS n, 'x' AS w) FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE FIELD_DELIMITER = '|' FIELD_OPTIONALLY_ENCLOSED_BY = '\"') SINGLE = TRUE");
        assertAnswer(
            "\"\"|\\N|\"x\"",
            "SELECT $1, $2, $3 FROM @ust/enc (FILE_FORMAT => 'raw_csv')");
        assertAnswer(
            "1|5|5",
            "COPY INTO @ust/nif FROM (SELECT NULL AS v, 'x' AS w) FILE_FORMAT = (TYPE = CSV COMPRESSION = "
                + "NONE FIELD_DELIMITER = '|' NULL_IF = ('NA', 'none')) SINGLE = TRUE");
        assertAnswer(
            "NA|x",
            "SELECT $1, $2 FROM @ust/nif (FILE_FORMAT => 'raw_csv')");
        assertAnswer(
            "1|3|3",
            "COPY INTO @ust/nin FROM (SELECT NULL AS v, 'x' AS w) FILE_FORMAT = (TYPE = CSV COMPRESSION = "
                + "NONE FIELD_DELIMITER = '|' NULL_IF = ()) SINGLE = TRUE");
        assertAnswer(
            "|x",
            "SELECT $1, $2 FROM @ust/nin (FILE_FORMAT => 'raw_csv')");
        assertAnswer(
            "1|3|3",
            "COPY INTO @ust/efn FROM (SELECT '' AS v, 'x' AS w) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE "
                + "FIELD_DELIMITER = '|' EMPTY_FIELD_AS_NULL = FALSE) SINGLE = TRUE");
        assertAnswer(
            "1|6|6",
            "COPY INTO @ust/num FROM (SELECT NULL::INT AS v, 5 AS w) FILE_FORMAT = (TYPE = CSV COMPRESSION = "
                + "NONE FIELD_DELIMITER = '|') SINGLE = TRUE");
        assertAnswer(
            "\\N|5",
            "SELECT $1, $2 FROM @ust/num (FILE_FORMAT => 'raw_csv')");
        assertAnswer(
            "1|6|6",
            "COPY INTO @ust/bsn FROM (SELECT '\\\\N' AS v, 'x' AS w) FILE_FORMAT = (TYPE = CSV COMPRESSION = "
                + "NONE FIELD_DELIMITER = '|') SINGLE = TRUE");
        assertAnswer(
            "\\N|x",
            "SELECT $1, $2 FROM @ust/bsn (FILE_FORMAT => 'raw_csv')");
    }

    @Test
    public void aNullOfATextColumnIsEnclosedLikeItsValues() {
        engine.execute("CREATE OR REPLACE STAGE est");
        engine.execute("CREATE OR REPLACE FILE FORMAT raw_file TYPE = CSV FIELD_DELIMITER = NONE RECORD_DELIMITER = NONE "
            + "ESCAPE_UNENCLOSED_FIELD = NONE NULL_IF = () EMPTY_FIELD_AS_NULL = FALSE");
        engine.execute("CREATE OR REPLACE TABLE src (id INT, name VARCHAR, note VARCHAR)");
        engine.execute("INSERT INTO src VALUES (1, 'a,b', NULL), (2, 'x\\\\y', 'n'), (3, 'plain', 'q\"r')");
        assertAnswer(
            "1|a,b|3|null ;; 2|x\\y|3|n ;; 3|plain|5|q\"r",
            "SELECT id, name, LENGTH(name), note FROM src ORDER BY id");
        assertAnswer(
            "3|42|42",
            "COPY INTO @est/roundq FROM (SELECT * FROM src ORDER BY id) FILE_FORMAT = (TYPE = CSV COMPRESSION "
                + "= NONE FIELD_OPTIONALLY_ENCLOSED_BY = '\"') SINGLE = TRUE");
        assertAnswer(
            "1,\"a,b\",\"\\N\" | 2,\"x\\y\",\"n\" | 3,\"plain\",\"q\"\"r\" | ",
            "SELECT $1 FROM @est/roundq (FILE_FORMAT => 'raw_file')");
        assertAnswer(
            "3|42|42",
            "COPY INTO @est/tab FROM src FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE "
                + "FIELD_OPTIONALLY_ENCLOSED_BY = '\"') SINGLE = TRUE");
        assertAnswer(
            "1,\"a,b\",\"\\N\" | 2,\"x\\y\",\"n\" | 3,\"plain\",\"q\"\"r\" | ",
            "SELECT $1 FROM @est/tab (FILE_FORMAT => 'raw_file')");
    }
}
