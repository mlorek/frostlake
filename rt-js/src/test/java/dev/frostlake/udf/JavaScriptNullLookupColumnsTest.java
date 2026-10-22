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
 * A JavaScript statement's isColumnNullable is true for the functions that answer NULL from columns that are not:
 * ARRAY_POSITION, ARRAY_MIN, ARRAY_MAX, ARRAY_SIZE, JSON_EXTRACT_PATH_TEXT, CHECK_XML, GETVARIABLE
 * (all live-verified).
 */
public class JavaScriptNullLookupColumnsTest extends MeasuredCellsTestSupport {

    @Test
    public void nullableLookups() {
        engine.execute("CREATE OR REPLACE TABLE jsn (k INT, knn INT NOT NULL, snn VARCHAR NOT NULL)");
        engine.execute("INSERT INTO jsn VALUES (1, 2, 'a,b')");
        engine.execute("CREATE OR REPLACE PROCEDURE nul(Q VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$\nvar st "
            + "= snowflake.createStatement({sqlText: Q});\nvar rs = st.execute();\nvar out = [];\nvar vals = "
            + "[];\nrs.next();\nfor (var i = 1; i <= st.getColumnCount(); i++) {\n  out.push(st.getColumnName(i) "
            + "+ '=' + (st.isColumnNullable(i) ? 1 : 0));\n  vals.push(rs.getColumnValue(i) === null ? 'NULL' : "
            + "String(rs.getColumnValue(i)));\n}\nreturn out.join(' ') + ' :: ' + vals.join('|');\n$$");
        assertAnswer("C1=1 C2=1 C3=1 C4=1 :: NULL|NULL|NULL|NULL",
            "CALL nul('SELECT ARRAY_POSITION(''z''::VARIANT, SPLIT(snn, '','')) AS c1, JSON_EXTRACT_PATH_TEXT(''{}'',"
                + " ''a'') AS c2, CHECK_XML(''<a/>'') AS c3, GETVARIABLE(''nope'') AS c4 FROM jsn')");
        assertAnswer("C1=1 :: NULL",
            "CALL nul('SELECT ARRAY_MIN(ARRAY_SLICE(SPLIT(snn, '',''), 5, 6)) AS c1 FROM jsn')");
        assertAnswer("C1=0 C2=1 C3=0 :: 2|1|a,b", "CALL nul('SELECT knn AS c1, k AS c2, snn AS c3 FROM jsn')");
        assertAnswer("C1=0 C2=0 C3=1 :: 3|A,B|b",
            "CALL nul('SELECT knn + 1 AS c1, UPPER(snn) AS c2, ARRAY_MAX(SPLIT(snn, '','')) AS c3 FROM jsn')");
        assertAnswer("C1=0 C2=1 C3=1 :: |NULL|NULL",
            "CALL nul('SELECT SPLIT_PART(snn, '','', 5) AS c1, TRY_TO_NUMBER(snn) AS c2, "
                + "ARRAY_MAX(ARRAY_CONSTRUCT()) AS c3 FROM jsn')");
        assertAnswer("C1=1 C2=1 C3=1 :: NULL|NULL|NULL",
            "CALL nul('SELECT GET(SPLIT(snn, '',''), 5) AS c1, NULLIF(knn, 2) AS c2, IFF(knn > 0, NULL, 1) AS c3 "
                + "FROM jsn')");
        assertAnswer("C1=1 C2=0 C3=0 :: 2|3|1",
            "CALL nul('SELECT ARRAY_SIZE(SPLIT(snn, '','')) AS c1, LENGTH(snn) AS c2, COALESCE(k, knn) AS c3 FROM "
                + "jsn')");
        assertAnswer("C1=1 C2=1 C3=0 :: NULL|NULL|0",
            "CALL nul('SELECT REGEXP_SUBSTR(snn, ''z'') AS c1, STRTOK(snn, '','', 9) AS c2, CHARINDEX(''z'', snn) AS "
                + "c3 FROM jsn')");
        assertAnswer("C1=1 C2=1 C3=1 :: NULL|[object Object]|1",
            "CALL nul('SELECT TRY_PARSE_JSON(snn) AS c1, OBJECT_CONSTRUCT() AS c2, AS_INTEGER(PARSE_JSON(''1'')) AS "
                + "c3 FROM jsn')");
        assertAnswer("C1=1 C2=1 C3=1 :: 0|1|prematurely terminated XML document in a tag, pos 2",
            "CALL nul('SELECT ARRAY_POSITION(''a''::VARIANT, SPLIT(snn, '','')) AS c1, "
                + "JSON_EXTRACT_PATH_TEXT(''{\"a\":1}'', ''a'') AS c2, CHECK_XML(''<a'') AS c3 FROM jsn')");
    }
}
