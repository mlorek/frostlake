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

import static dev.frostlake.query.QueryAnswers.assertCells;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * What a read of a structured value finds has the type the value declares for it, rather than VARIANT (documented):
 * an element of a structured ARRAY, a field of a structured OBJECT and a value of a MAP — read by subscript, by path,
 * by GET, GET_IGNORE_CASE and GET_PATH, and as the VALUE of a FLATTEN that is not RECURSIVE — so arithmetic over it
 * is exact, a CTAS declares it, and SYSTEM$TYPEOF tags an element of a stored column by the column's statistics. A
 * field the OBJECT does not declare is refused (live-verified).
 */
public class StructuredElementTypeTest extends BaseDatabaseTest {

    private static final String OI =
        "{'id': 12345678901234567891, 'name': 'x'}::OBJECT(id NUMBER(38,0), name VARCHAR)";

    @Test
    public void anElementOfAStoredStructuredColumnHasItsDeclaredType() {
        try {
            assertCells(engine, new String[][] {
                {"CREATE OR REPLACE TABLE so (o OBJECT(id NUMBER(38,0), name VARCHAR), a ARRAY(NUMBER(38,0)))",
                    "Table SO successfully created."},
                {"INSERT INTO so SELECT " + OI + ", [9007199254740993]::ARRAY(NUMBER(38,0))", "1"},
                {"SELECT o:id, o:id + 1, a[0], a[0] + 1, a[0] * 2 FROM so", "12345678901234567891, "
                    + "12345678901234567892, 9007199254740993, 9007199254740994, 18014398509481986"},
                {"SELECT SYSTEM$TYPEOF(o:id), SYSTEM$TYPEOF(a[0]), SYSTEM$TYPEOF(o:id + 1) FROM so",
                    "NUMBER(38,0)[SB16], NUMBER(38,0)[SB8], NUMBER(38,0)[SB16]"},
                {"SELECT a[0] + 1 = 9007199254740994 AS should_be_true FROM so", "true"},
                {"SELECT SUM(a[0]), SYSTEM$TYPEOF(SUM(a[0])) FROM so", "9007199254740993, NUMBER(38,0)[SB16]"},
                {"SELECT SYSTEM$TYPEOF(a[0] + 1), a[0] + 1.5, SYSTEM$TYPEOF(a[0] + 1.5), a[0] / 2 FROM so",
                    "NUMBER(38,0)[SB8], 9007199254740994.5, NUMBER(38,1)[SB8], 4503599627370496.500000"},
                {"SELECT o['id'], SYSTEM$TYPEOF(o['id']), o:name, SYSTEM$TYPEOF(o:name), o:name || 'y' FROM so",
                    "12345678901234567891, NUMBER(38,0)[SB16], x, VARCHAR(16777216)[LOB], xy"},
                {"SELECT a[5], SYSTEM$TYPEOF(a[5]) FROM so", "null, NUMBER(38,0)[SB8]"},
                {"SELECT o:missing FROM so",
                    "Function GET: expected structured object to contain field missing but it did not."},
                {"CREATE OR REPLACE TABLE so2 AS SELECT o:id AS id, a[0] AS e, o:name AS n FROM so",
                    "Table SO2 successfully created."},
                {"DESCRIBE TABLE so2", "ID, NUMBER(38,0), COLUMN, Y, null, N, N, null, null, null, null, null, null | "
                    + "E, NUMBER(38,0), COLUMN, Y, null, N, N, null, null, null, null, null, null | "
                    + "N, VARCHAR(16777216), COLUMN, Y, null, N, N, null, null, null, null, null, null"},
            });
        } finally {
            engine.execute("DROP TABLE IF EXISTS so");
            engine.execute("DROP TABLE IF EXISTS so2");
        }
    }

    @Test
    public void theGetFunctionsReadTheDeclaredType() {
        try {
            assertCells(engine, new String[][] {
                {"CREATE OR REPLACE TABLE so (o OBJECT(id NUMBER(38,0), name VARCHAR), a ARRAY(NUMBER(38,0)))",
                    "Table SO successfully created."},
                {"INSERT INTO so SELECT " + OI + ", [9007199254740993]::ARRAY(NUMBER(38,0))", "1"},
                {"SELECT GET(o, 'id'), SYSTEM$TYPEOF(GET(o, 'id')), GET(a, 0), SYSTEM$TYPEOF(GET(a, 0)) FROM so",
                    "12345678901234567891, NUMBER(38,0)[SB16], 9007199254740993, NUMBER(38,0)[SB8]"},
                {"SELECT GET_PATH(o, 'id'), SYSTEM$TYPEOF(GET_PATH(o, 'id')), GET_IGNORE_CASE(o, 'ID'), "
                    + "SYSTEM$TYPEOF(GET_IGNORE_CASE(o, 'ID')) FROM so",
                    "12345678901234567891, NUMBER(38,0)[SB16], 12345678901234567891, NUMBER(38,0)[SB16]"},
                {"SELECT GET(a, x) FROM so, (SELECT 0 AS x)", "9007199254740993"},
            });
        } finally {
            engine.execute("DROP TABLE IF EXISTS so");
        }
    }

    @Test
    public void aFlattenOfAStructuredArrayOrMapHasTypedValues() {
        try {
            assertCells(engine, new String[][] {
                {"CREATE OR REPLACE TABLE so (a ARRAY(NUMBER(38,0)))", "Table SO successfully created."},
                {"INSERT INTO so SELECT [9007199254740993]::ARRAY(NUMBER(38,0))", "1"},
                {"SELECT f.value + 1, SYSTEM$TYPEOF(f.value) FROM so, LATERAL FLATTEN(so.a) f",
                    "9007199254740994, NUMBER(38,0)[SB16]"},
                {"SELECT value, SYSTEM$TYPEOF(value) FROM TABLE(FLATTEN(INPUT => [1.08, 2.13, 3.14]"
                    + "::ARRAY(DOUBLE)))",
                    "1.08, FLOAT[DOUBLE] | 2.13, FLOAT[DOUBLE] | 3.14, FLOAT[DOUBLE]"},
                {"SELECT key, SYSTEM$TYPEOF(key), value, SYSTEM$TYPEOF(value) FROM TABLE(FLATTEN(INPUT => "
                    + "{'my_key': 'my_value'}::MAP(VARCHAR, VARCHAR)))",
                    "my_key, VARCHAR[LOB], my_value, VARCHAR[LOB]"},
                {"SELECT key, SYSTEM$TYPEOF(key), value, SYSTEM$TYPEOF(value) FROM TABLE(FLATTEN(INPUT => "
                    + "{'k': 5}::MAP(VARCHAR, NUMBER)))", "k, VARCHAR[LOB], 5, NUMBER(38,0)[SB16]"},
                {"SELECT key, value, SYSTEM$TYPEOF(value) FROM TABLE(FLATTEN(INPUT => {'a': 1, 'b': 'x'}"
                    + "::OBJECT(a NUMBER, b VARCHAR))) ORDER BY key", "a, 1, VARIANT[LOB] | b, x, VARIANT[LOB]"},
                {"SELECT key, value, SYSTEM$TYPEOF(value) FROM TABLE(FLATTEN(INPUT => [[1, 2]]::ARRAY(ARRAY(NUMBER)), "
                    + "RECURSIVE => TRUE)) ORDER BY path",
                    "null, [1,2], VARIANT[LOB] | null, 1, VARIANT[LOB] | null, 2, VARIANT[LOB]"},
            });
        } finally {
            engine.execute("DROP TABLE IF EXISTS so");
        }
    }

    /**
     * A FLATTEN of a MAP that is not RECURSIVE answers KEY as the map's key type (documented: "the key column matches
     * the MAP's key type"), so a NUMBER-keyed map's keys sort 1, 2, 10, compare and add as numbers and a CTAS declares
     * them NUMBER, as MAP_KEYS answers them. A text-keyed map's KEY stays VARCHAR, as a RECURSIVE one's does.
     */
    @Test
    public void aFlattenOfANumberKeyedMapHasNumberKeys() {
        final String m = "{'1': 'x', '10': 'y', '2': 'z'}::MAP(INT, VARCHAR)";
        try {
            assertCells(engine, new String[][] {
                {"SELECT f.key, f.value, SYSTEM$TYPEOF(f.key) FROM (SELECT " + m + " m), TABLE(FLATTEN(m)) f "
                    + "ORDER BY f.key", "1, x, NUMBER(38,0)[SB16] | 2, z, NUMBER(38,0)[SB16] | 10, y, NUMBER(38,0)[SB16]"},
                {"SELECT f.key + 1, f.key = 10 FROM (SELECT " + m + " m), LATERAL FLATTEN(INPUT => m) f ORDER BY 1",
                    "2, false | 3, false | 11, true"},
                {"SELECT key, SYSTEM$TYPEOF(key) FROM TABLE(FLATTEN({'-5': 'x', '3': 'y'}::MAP(NUMBER(10,0), VARCHAR))) "
                    + "ORDER BY key", "-5, NUMBER(10,0)[SB8] | 3, NUMBER(10,0)[SB8]"},
                {"CREATE OR REPLACE TABLE fz AS SELECT f.key k, f.value v FROM (SELECT " + m + " m), "
                    + "TABLE(FLATTEN(m)) f", "Table FZ successfully created."},
                {"SELECT k FROM fz ORDER BY k", "1 | 2 | 10"},
                {"SELECT data_type, numeric_precision, numeric_scale FROM information_schema.columns "
                    + "WHERE table_name = 'FZ' AND column_name = 'K'", "NUMBER, 38, 0"},
                {"CREATE OR REPLACE TABLE mz (m MAP(INT, VARCHAR))", "Table MZ successfully created."},
                {"INSERT INTO mz SELECT " + m, "1"},
                {"SELECT f.key FROM mz, LATERAL FLATTEN(mz.m) f ORDER BY f.key", "1 | 2 | 10"},
                {"SELECT key, this FROM TABLE(FLATTEN({}::MAP(INT, VARCHAR), OUTER => TRUE))", "null, {}"},
                {"SELECT key, SYSTEM$TYPEOF(key) FROM TABLE(FLATTEN(" + m + ", RECURSIVE => TRUE)) ORDER BY key",
                    "1, VARCHAR[LOB] | 10, VARCHAR[LOB] | 2, VARCHAR[LOB]"},
            });
        } finally {
            engine.execute("DROP TABLE IF EXISTS fz");
            engine.execute("DROP TABLE IF EXISTS mz");
        }
    }

    @Test
    public void aReadOfACastStructuredValueHasItsDeclaredType() {
        assertCells(engine, new String[][] {
            {"SELECT ({'p': 0.1}::OBJECT(p NUMBER(10,1))):p + ({'p': 0.2}::OBJECT(p NUMBER(10,1))):p", "0.3"},
            {"SELECT SYSTEM$TYPEOF(CAST(ARRAY_CONSTRUCT('San Mateo') AS ARRAY(VARCHAR))[0])", "VARCHAR[LOB]"},
            {"SELECT {'k': 5}::MAP(VARCHAR, NUMBER)['k'] + 1, SYSTEM$TYPEOF({'k': 5}::MAP(VARCHAR, NUMBER)['k'])",
                "6, NUMBER(38,0)[SB16]"},
            {"SELECT ([[1, 2]]::ARRAY(ARRAY(NUMBER)))[0], SYSTEM$TYPEOF(([[1, 2]]::ARRAY(ARRAY(NUMBER)))[0]), "
                + "([[1, 2]]::ARRAY(ARRAY(NUMBER)))[0][1] + 1", "[1,2], ARRAY(NUMBER(38,0))[LOB], 3"},
            {"SELECT ({'x': {'y': 3}}::OBJECT(x OBJECT(y NUMBER))):x.y + 1, "
                + "SYSTEM$TYPEOF(({'x': {'y': 3}}::OBJECT(x OBJECT(y NUMBER))):x.y)", "4, NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(([1.5]::ARRAY(FLOAT))[0] + 1), ([TRUE]::ARRAY(BOOLEAN))[0] AND TRUE, "
                + "SYSTEM$TYPEOF(([TRUE]::ARRAY(BOOLEAN))[0])", "FLOAT[DOUBLE], true, BOOLEAN[SB1]"},
            {"SELECT SYSTEM$TYPEOF((['2024-01-02']::ARRAY(DATE))[0]), "
                + "DATEADD(day, 1, (['2024-01-02']::ARRAY(DATE))[0])",
                "DATE[SB4], 2024-01-03"},
            {"SELECT SYSTEM$TYPEOF(PARSE_JSON('[1]')[0]), SYSTEM$TYPEOF(PARSE_JSON('{\"a\": 1}'):a)",
                "VARIANT[LOB], VARIANT[LOB]"},
        });
    }
}
