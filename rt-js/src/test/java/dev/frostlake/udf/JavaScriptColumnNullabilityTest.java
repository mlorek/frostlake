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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A JavaScript procedure's result set tells a column nullable as the account works it out from the select item
 * (live-verified): a column by its NOT NULL and its join; NULL, TRUE and FALSE nullable, a number or a string not;
 * an operator nullable when an operand is; a comparison or a predicate nullable; CASE, IFF and DECODE by their
 * results; COALESCE, NVL and IFNULL only when every argument is; the aggregates and the functions that can answer
 * NULL nullable, and COUNT, HASH, the ranking functions and the current date and time not.
 */
public class JavaScriptColumnNullabilityTest extends MeasuredCellsTestSupport {

    @Test
    public void expressionsAndFunctions() {
        engine.execute("CREATE OR REPLACE TABLE jsn (k INT, knn INT NOT NULL, s VARCHAR, snn VARCHAR NOT NULL, d "
            + "DATE, dnn DATE NOT NULL, b BOOLEAN, v VARIANT)");
        engine.execute("INSERT INTO jsn SELECT 1, 2, 'a', 'b', '2024-01-01'::DATE, '2024-01-02'::DATE, TRUE, "
            + "PARSE_JSON('{\"a\":1}')");
        engine.execute("CREATE OR REPLACE PROCEDURE nul(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs "
            + "= snowflake.execute({sqlText: Q}); var out = []; for (var i = 1; i <= rs.getColumnCount(); i++) { "
            + "out.push(rs.getColumnName(i) + '=' + (rs.isColumnNullable(i) ? 1 : 0)); } return out.join(' '); $$");
        assertCells(new String[][] {
            {"CALL nul('SELECT k AS c1, knn AS c2, s AS c3, TRUE AS c4, FALSE AS c5, NULL AS c6, NULL::INT AS c7, 1 "
                + "AS c8, ''a'' AS c9, 1.5 AS c10, k + 1 AS c11, knn + 1 AS c12 FROM jsn')",
                "C1=1 C2=0 C3=1 C4=1 C5=1 C6=1 C7=1 C8=0 C9=0 C10=0 C11=1 C12=0"},
            {"CALL nul('SELECT k + knn AS c1, -k AS c2, -knn AS c3, k * 2 AS c4, knn / 2 AS c5, ''a'' || ''b'' AS c6, "
                + "s || ''x'' AS c7, snn || ''x'' AS c8, UPPER(s) AS c9, UPPER(snn) AS c10, UPPER(''a'') AS c11, "
                + "LOWER(snn) AS c12 FROM jsn')",
                "C1=1 C2=1 C3=0 C4=1 C5=0 C6=0 C7=1 C8=0 C9=1 C10=0 C11=0 C12=0"},
            {"CALL nul('SELECT LENGTH(snn) AS c1, LENGTH(s) AS c2, SUBSTR(snn, 1, 1) AS c3, CONCAT(snn, ''x'') AS c4, "
                + "CONCAT(s, ''x'') AS c5, COALESCE(k, 0) AS c6, COALESCE(k, knn) AS c7, COALESCE(k, NULL) AS c8, "
                + "NVL(k, 0) AS c9, IFF(TRUE, 1, NULL) AS c10, IFF(TRUE, 1, 2) AS c11, IFF(b, 1, 2) AS c12 FROM jsn')",
                "C1=0 C2=1 C3=0 C4=0 C5=1 C6=0 C7=0 C8=1 C9=0 C10=1 C11=0 C12=0"},
            {"CALL nul('SELECT CASE WHEN knn > 0 THEN 1 END AS c1, CASE WHEN knn > 0 THEN 1 ELSE 2 END AS c2, knn > 0 "
                + "AS c3, k > 0 AS c4, knn = knn AS c5, k IS NULL AS c6, k IS NOT NULL AS c7, NOT b AS c8, b AND TRUE "
                + "AS c9, knn::VARCHAR AS c10, k::VARCHAR AS c11, TRY_CAST(snn AS INT) AS c12 FROM jsn')",
                "C1=1 C2=0 C3=1 C4=1 C5=1 C6=1 C7=1 C8=1 C9=1 C10=0 C11=1 C12=1"},
            {"CALL nul('SELECT TRY_TO_NUMBER(snn) AS c1, TO_VARCHAR(1) AS c2, TO_VARCHAR(knn) AS c3, TO_NUMBER(''1'') "
                + "AS c4, TO_DATE(''2024-01-01'') AS c5, CURRENT_DATE() AS c6, CURRENT_TIMESTAMP() AS c7, "
                + "CURRENT_USER() AS c8, RANDOM() AS c9, UUID_STRING() AS c10, PARSE_JSON(''1'') AS c11, "
                + "ARRAY_CONSTRUCT(1) AS c12 FROM jsn')",
                "C1=1 C2=0 C3=0 C4=0 C5=0 C6=0 C7=0 C8=1 C9=0 C10=0 C11=1 C12=1"},
            {"CALL nul('SELECT OBJECT_CONSTRUCT(''a'', 1) AS c1, TO_VARIANT(1) AS c2, ''x''::VARIANT AS c3, v AS c4, "
                + "v:a AS c5, GET(v, ''a'') AS c6, ARRAY_SIZE(ARRAY_CONSTRUCT(1)) AS c7, DATEADD(day, 1, d) AS c8, "
                + "DATEADD(day, 1, ''2024-01-01''::DATE) AS c9, YEAR(d) AS c10, NULLIF(knn, 0) AS c11, ZEROIFNULL(k) "
                + "AS c12 FROM jsn')",
                "C1=1 C2=0 C3=0 C4=1 C5=1 C6=1 C7=1 C8=1 C9=0 C10=1 C11=1 C12=0"},
            {"CALL nul('SELECT NULLIFZERO(knn) AS c1, GREATEST(knn, 1) AS c2, GREATEST(k, 1) AS c3, ABS(knn) AS c4, "
                + "ROUND(knn) AS c5, SQRT(knn) AS c6, 1::BOOLEAN AS c7, HASH(k) AS c8, MD5(snn) AS c9, SPLIT(snn, '',"
                + "'') AS c10, IFNULL(s, ''x'') AS c11, DECODE(knn, 1, ''a'') AS c12 FROM jsn')",
                "C1=1 C2=0 C3=1 C4=0 C5=0 C6=0 C7=0 C8=0 C9=0 C10=0 C11=0 C12=1"},
            // A scalar subquery item is told nullable as its own item is: (SELECT 1) is not.
            {"CALL nul('SELECT DECODE(knn, 1, ''a'', ''b'') AS c1, (SELECT 1) AS c2, LEFT(snn, 1) AS c3, "
                + "REPLACE(snn, ''a'', ''b'') AS c4, TRIM(snn) AS c5, SPLIT_PART(snn, '','', 1) AS c6, "
                + "REGEXP_SUBSTR(snn, ''a'') AS c7, POSITION(''a'' IN snn) AS c8, CHARINDEX(''a'', snn) AS c9, "
                + "STRTOK(snn, '','', 1) AS c10, TRY_TO_DATE(snn) AS c11, TO_BOOLEAN(''true'') AS c12 FROM jsn')",
                "C1=0 C2=0 C3=0 C4=0 C5=0 C6=0 C7=1 C8=0 C9=0 C10=1 C11=1 C12=0"},
            {"CALL nul('SELECT SEQ4() AS c1, ROW_NUMBER() OVER (ORDER BY knn) AS c2, LAG(knn) OVER (ORDER BY knn) AS "
                + "c3, dnn AS c4, DATEADD(day, 1, dnn) AS c5, TO_CHAR(dnn) AS c6, knn BETWEEN 1 AND 2 AS c7, snn LIKE "
                + "''a%'' AS c8, s LIKE ''a%'' AS c9, knn IN (1, 2) AS c10, k IN (1, 2) AS c11, EQUAL_NULL(k, 1) AS "
                + "c12 FROM jsn')",
                "C1=0 C2=0 C3=1 C4=0 C5=0 C6=0 C7=1 C8=1 C9=1 C10=1 C11=1 C12=1"},
            {"CALL nul('SELECT ARRAY_CONTAINS(1::VARIANT, ARRAY_CONSTRUCT(1)) AS c1, "
                + "OBJECT_KEYS(OBJECT_CONSTRUCT(''a'', 1)) AS c2, TO_JSON(v) AS c3, TYPEOF(v) AS c4, IS_NULL_VALUE(v) "
                + "AS c5, BITAND(knn, 1) AS c6, MOD(knn, 2) AS c7, DIV0(knn, 0) AS c8, SIGN(knn) AS c9, EXP(knn) AS "
                + "c10, LN(knn) AS c11, TRUNC(knn) AS c12 FROM jsn')",
                "C1=1 C2=1 C3=1 C4=1 C5=1 C6=0 C7=0 C8=0 C9=0 C10=0 C11=0 C12=0"},
            {"CALL nul('SELECT CEIL(knn) AS c1, FLOOR(knn) AS c2, POWER(knn, 2) AS c3, PI() AS c4, ASCII(snn) AS c5, "
                + "CHR(65) AS c6, REVERSE(snn) AS c7, INITCAP(snn) AS c8, LPAD(snn, 3) AS c9, TRANSLATE(snn, ''a'', "
                + "''b'') AS c10, BASE64_ENCODE(snn) AS c11, HEX_ENCODE(snn) AS c12 FROM jsn')",
                "C1=0 C2=0 C3=0 C4=0 C5=0 C6=0 C7=0 C8=0 C9=0 C10=0 C11=0 C12=0"},
            {"CALL nul('SELECT TO_BINARY(''FF'', ''HEX'') AS c1, SHA2(snn) AS c2, UNIFORM(1, 10, RANDOM()) AS c3 FROM "
                + "jsn')",
                "C1=0 C2=0 C3=0"},
            {"CALL nul('SELECT COUNT(*) AS c1, COUNT(k) AS c2, SUM(knn) AS c3, MAX(knn) AS c4, MIN(snn) AS c5, "
                + "AVG(knn) AS c6, LISTAGG(snn) AS c7, ARRAY_AGG(knn) AS c8, ANY_VALUE(knn) AS c9, COUNT_IF(b) AS c10,"
                + " BOOLAND_AGG(b) AS c11, HASH_AGG(knn) AS c12, APPROX_COUNT_DISTINCT(knn) AS c13, OBJECT_AGG(snn, "
                + "v) AS c14, MEDIAN(knn) AS c15 FROM jsn')",
                "C1=0 C2=0 C3=1 C4=1 C5=1 C6=1 C7=0 C8=0 C9=1 C10=1 C11=1 C12=0 C13=0 C14=0 C15=1"},
            {"CALL nul('SELECT COUNT(*) AS c1, COUNT(k) AS c2, SUM(knn) AS c3, MAX(knn) AS c4, MIN(snn) AS c5 FROM "
                + "jsn GROUP BY knn')",
                "C1=0 C2=0 C3=1 C4=1 C5=1"},
            {"CALL nul('SELECT k AS c1, knn AS c2, s AS c3 FROM jsn WHERE k IS NOT NULL')", "C1=1 C2=0 C3=1"},
            {"CALL nul('SELECT j.k AS c1, j.knn AS c2, o.knn AS c3 FROM jsn j LEFT JOIN jsn o ON j.knn = o.knn')",
                "C1=1 C2=0 C3=1"},
            {"CALL nul('SELECT c1 AS c1, c2 AS c2 FROM (SELECT knn AS c1, k AS c2 FROM jsn)')", "C1=0 C2=1"},
        });
    }

    @Test
    public void moreFunctionsAndRelations() {
        engine.execute("CREATE OR REPLACE TABLE jsn (k INT, knn INT NOT NULL, s VARCHAR, snn VARCHAR NOT NULL, d "
            + "DATE, dnn DATE NOT NULL, b BOOLEAN, v VARIANT)");
        engine.execute("INSERT INTO jsn SELECT 1, 2, 'a', 'b', '2024-01-01'::DATE, '2024-01-02'::DATE, TRUE, "
            + "PARSE_JSON('{\"a\":1}')");
        engine.execute("CREATE OR REPLACE VIEW jv AS SELECT knn, k, k + 1 AS e FROM jsn");
        engine.execute("CREATE OR REPLACE TABLE jc AS SELECT knn, k + 1 AS e, 1 AS one FROM jsn");
        engine.execute("CREATE OR REPLACE PROCEDURE nul(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs "
            + "= snowflake.execute({sqlText: Q}); var out = []; for (var i = 1; i <= rs.getColumnCount(); i++) { "
            + "out.push(rs.getColumnName(i) + '=' + (rs.isColumnNullable(i) ? 1 : 0)); } return out.join(' '); $$");
        assertCells(new String[][] {
            {"CALL nul('SELECT NVL2(k, 1, 2) AS c1, CONCAT_WS('','', snn, s) AS c2, CONCAT_WS('','', snn, ''x'') AS "
                + "c3, ARRAY_TO_STRING(ARRAY_CONSTRUCT(1), '','') AS c4, TO_TIMESTAMP(''2024-01-01'') AS c5, "
                + "DATE_TRUNC(''day'', dnn) AS c6, LAST_DAY(dnn) AS c7, ARRAY_APPEND(ARRAY_CONSTRUCT(1), 2) AS c8, "
                + "OBJECT_INSERT(OBJECT_CONSTRUCT(''a'', 1), ''b'', 2) AS c9, GET_PATH(v, ''a'') AS c10, "
                + "AS_INTEGER(v) AS c11, TRY_PARSE_JSON(''1'') AS c12 FROM jsn')",
                "C1=0 C2=1 C3=0 C4=1 C5=0 C6=0 C7=0 C8=1 C9=1 C10=1 C11=1 C12=1"},
            {"CALL nul('SELECT CHECK_JSON(''1'') AS c1, LEAST(knn, 1) AS c2, LEAST(k, 1) AS c3, SUBSTR(s, 1) AS c4, "
                + "ARRAY_SLICE(ARRAY_CONSTRUCT(1), 0, 1) AS c5, CASE WHEN knn > 0 THEN NULL ELSE 1 END AS c6, "
                + "SUM(knn) OVER () AS c7, COUNT(*) OVER () AS c8, FIRST_VALUE(knn) OVER (ORDER BY knn) AS c9, RANK() "
                + "OVER (ORDER BY knn) AS c10, MAX(knn) OVER () AS c11, IFF(knn > 0, k, 1) AS c12 FROM jsn')",
                "C1=1 C2=0 C3=1 C4=1 C5=1 C6=1 C7=1 C8=0 C9=1 C10=0 C11=1 C12=1"},
            {"CALL nul('SELECT IFF(knn > 0, knn, 1) AS c1, COALESCE(NULL, 1) AS c2, COALESCE(s, snn) AS c3, NVL(s, "
                + "snn) AS c4, ZEROIFNULL(knn) AS c5, knn IS DISTINCT FROM 1 AS c6, TO_DECIMAL(''1'') AS c7, "
                + "TO_DOUBLE(''1'') AS c8, TRY_TO_DOUBLE(''1'') AS c9, CAST(knn AS VARCHAR) AS c10, CAST(k AS "
                + "VARCHAR) AS c11, k::VARIANT AS c12 FROM jsn')",
                "C1=0 C2=0 C3=0 C4=0 C5=0 C6=1 C7=0 C8=0 C9=1 C10=0 C11=1 C12=1"},
            {"CALL nul('SELECT knn::VARIANT AS c1, TO_ARRAY(knn) AS c2, TO_OBJECT(PARSE_JSON(''{}'')) AS c3, "
                + "ARRAY_CONSTRUCT_COMPACT(1) AS c4, OBJECT_CONSTRUCT_KEEP_NULL(''a'', 1) AS c5, CURRENT_ROLE() AS c6,"
                + " CURRENT_DATABASE() AS c7, CURRENT_SCHEMA() AS c8, CURRENT_SESSION() AS c9, CURRENT_TIME() AS c10, "
                + "LOCALTIMESTAMP() AS c11, SYSDATE() AS c12 FROM jsn')",
                "C1=0 C2=0 C3=1 C4=1 C5=1 C6=1 C7=1 C8=1 C9=1 C10=0 C11=0 C12=0"},
            {"CALL nul('SELECT HASH(*) AS c1, MD5_HEX(snn) AS c2, SHA1(snn) AS c3, STRTOK_TO_ARRAY(snn) AS c4, "
                + "REGEXP_REPLACE(snn, ''a'', ''b'') AS c5, REGEXP_COUNT(snn, ''a'') AS c6, REGEXP_INSTR(snn, "
                + "''a'') AS c7, REGEXP_LIKE(snn, ''a'') AS c8, CONTAINS(snn, ''a'') AS c9, STARTSWITH(snn, "
                + "''a'') AS c10, IS_INTEGER(v) AS c11, BOOLAND(TRUE, FALSE) AS c12 FROM jsn')",
                "REFUSED Execution error in store procedure NUL: | SQL compilation error: error line 1 at "
                + "position 290 | Invalid argument types for function 'BOOLAND': (BOOLEAN, BOOLEAN) | At "
                + "Snowflake.execute, line 1 position 20 | "},
            {"CALL nul('SELECT DECODE(k, 1, ''a'', ''b'') AS c1, (SELECT MAX(k) FROM jsn) AS c2, (SELECT knn FROM jsn "
                + "LIMIT 1) AS c3, EXISTS (SELECT 1) AS c4, knn IN (SELECT knn FROM jsn) AS c5, TO_CHAR(knn) AS c6, "
                + "TO_VARCHAR(k) AS c7, TO_NUMBER(snn) AS c8, TO_DATE(snn) AS c9, DAYNAME(dnn) AS c10, DATEDIFF(day, "
                + "dnn, dnn) AS c11, TIMESTAMPADD(day, 1, dnn) AS c12 FROM jsn')",
                "REFUSED Execution error in store procedure NUL: | Numeric value 'b' is not recognized | At "
                + "Snowflake.execute, line 1 position 20 | "},
            {"CALL nul('SELECT ADD_MONTHS(dnn, 1) AS c1, NEXT_DAY(dnn, ''MO'') AS c2, DATE_PART(year, dnn) AS c3, "
                + "EXTRACT(year FROM dnn) AS c4, TO_TIME(''10:00:00'') AS c5, TIME_SLICE(dnn, 1, ''DAY'') AS c6, "
                + "MONTHNAME(dnn) AS c7, ARRAY_POSITION(1::VARIANT, ARRAY_CONSTRUCT(1)) AS c8, "
                + "ARRAY_INTERSECTION(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(1)) AS c9, "
                + "ARRAY_COMPACT(ARRAY_CONSTRUCT(1)) AS c10, ARRAY_DISTINCT(ARRAY_CONSTRUCT(1)) AS c11, "
                + "OBJECT_DELETE(OBJECT_CONSTRUCT(''a'', 1), ''a'') AS c12 FROM jsn')",
                "C1=0 C2=0 C3=0 C4=0 C5=0 C6=0 C7=0 C8=1 C9=1 C10=1 C11=1 C12=1"},
            {"CALL nul('SELECT knn AS c1 FROM jsn UNION ALL SELECT knn FROM jsn')", "C1=0"},
            {"CALL nul('SELECT knn AS c1 FROM jsn INTERSECT SELECT knn FROM jsn')", "C1=0"},
            {"CALL nul('WITH c AS (SELECT knn, k FROM jsn) SELECT knn AS c1, k AS c2 FROM c')", "C1=0 C2=1"},
            {"CALL nul('SELECT * FROM jv')", "KNN=0 K=1 E=1"},
            {"CALL nul('SELECT knn AS c1, COUNT(*) AS c2 FROM jsn GROUP BY knn')", "C1=0 C2=0"},
            {"CALL nul('SELECT j.knn AS c1, o.knn AS c2 FROM jsn j JOIN jsn o ON j.knn = o.knn')", "C1=0 C2=0"},
            {"CALL nul('SELECT j.knn AS c1, o.knn AS c2 FROM jsn j RIGHT JOIN jsn o ON j.knn = o.knn')", "C1=1 C2=0"},
            {"CALL nul('SELECT j.knn AS c1, o.knn AS c2 FROM jsn j FULL JOIN jsn o ON j.knn = o.knn')", "C1=1 C2=1"},
            {"CALL nul('SELECT * FROM jsn')", "K=1 KNN=0 S=1 SNN=0 D=1 DNN=0 B=1 V=1"},
            {"CALL nul('SELECT * FROM jc')", "KNN=1 E=1 ONE=1"},
            {"CALL nul('SELECT knn AS c1, k AS c2 FROM jsn WHERE k IS NOT NULL QUALIFY ROW_NUMBER() OVER (ORDER BY "
                + "knn) = 1')",
                "C1=0 C2=1"},
            {"CALL nul('SELECT DISTINCT knn AS c1 FROM jsn')", "C1=0"},
            {"CALL nul('SELECT knn AS c1 FROM jsn ORDER BY 1 LIMIT 1')", "C1=0"},
            // A VALUES column is told nullable only where a row holds NULL in it.
            {"CALL nul('SELECT COLUMN1 AS c1 FROM VALUES (1), (2)')", "C1=0"},
            {"CALL nul('SELECT $1 AS c1, $2 AS c2 FROM VALUES (1, 2)')", "C1=0 C2=0"},
            {"CALL nul('SELECT f.value AS c1, f.index AS c2, f.key AS c3, f.seq AS c4, f.path AS c5, f.this AS c6 "
                + "FROM jsn, LATERAL FLATTEN(input => ARRAY_CONSTRUCT(1)) f')",
                "C1=1 C2=1 C3=1 C4=1 C5=1 C6=1"},
            {"CALL nul('SELECT seq4() AS c1, uniform(1, 2, random()) AS c2 FROM TABLE(GENERATOR(ROWCOUNT => 1))')",
                "C1=0 C2=0"},
        });
    }
}
