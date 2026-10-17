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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CREATE AGGREGATE FUNCTION with a Python handler (live-verified): an instance of the handler class reads each row
 * of a group with accumulate - a NULL argument as None - and finish answers the group's value. CREATE requires the
 * class and its accumulate, merge, finish and aggregate_state, in that order; __init__ may be left out.
 */
public class PythonAggregateFunctionTest extends MeasuredCellsTestSupport {

    private static final String SUM = """
        class S:
            def __init__(self):
                self._s = 0
            @property
            def aggregate_state(self):
                return self._s
            def accumulate(self, x):
                if x is not None:
                    self._s += x
            def merge(self, other):
                self._s += other
            def finish(self):
                return self._s""";

    /** Counts the NULLs it is given. */
    private static final String NULLS = """
        class C:
            def __init__(self):
                self._n = 0
            @property
            def aggregate_state(self):
                return self._n
            def accumulate(self, x):
                if x is None:
                    self._n += 1
            def merge(self, other):
                self._n += other
            def finish(self):
                return self._n""";

    /** Multiplies its two arguments, which a NULL cannot be. */
    private static final String DOT = """
        class D:
            def __init__(self):
                self._s = 0
            @property
            def aggregate_state(self):
                return self._s
            def accumulate(self, x, y):
                self._s += x * y
            def merge(self, other):
                self._s += other
            def finish(self):
                return self._s""";

    private static final String VALUES = """
        class A:
            def __init__(self):
                self._v = []
            @property
            def aggregate_state(self):
                return self._v
            def accumulate(self, x):
                if x is not None:
                    self._v.append(x)
            def merge(self, other):
                self._v.extend(other)
            def finish(self):
                return sorted(self._v)""";

    private static final String MEAN = """
        class V:
            def __init__(self):
                self._s = [0.0, 0]
            @property
            def aggregate_state(self):
                return self._s
            def accumulate(self, x):
                if x is not None:
                    self._s[0] += x
                    self._s[1] += 1
            def merge(self, other):
                self._s[0] += other[0]
                self._s[1] += other[1]
            def finish(self):
                return None if self._s[1] == 0 else self._s[0] / self._s[1]""";

    private static final String INIT = "    def __init__(self):\n        self._s = 0\n";
    private static final String STATE = "    @property\n    def aggregate_state(self):\n        return self._s\n";
    private static final String ACCUMULATE = "    def accumulate(self, x):\n        if x is not None:\n"
        + "            self._s += x\n";
    private static final String MERGE = "    def merge(self, other):\n        self._s += other\n";
    private static final String FINISH = "    def finish(self):\n        return self._s\n";

    private static String python(final String name, final String signature, final String returns,
                                 final String handler, final String body) {
        return "CREATE OR REPLACE AGGREGATE FUNCTION " + name + signature + " RETURNS " + returns + " LANGUAGE PYTHON"
            + " RUNTIME_VERSION = '3.11' HANDLER = '" + handler + "' AS $$\n" + body + "\n$$";
    }

    @BeforeEach
    public void createTheSum() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE ua (g INT, a INT)", "Table UA successfully created."},
            {"INSERT INTO ua VALUES (1, 10), (1, 20), (2, 5), (2, NULL), (3, NULL)", "5"},
            {python("ag_sum", "(x NUMBER)", "NUMBER", "S", SUM), "Function AG_SUM successfully created."},
        });
    }

    @Test
    public void everyRowReachesAccumulateANullAsNone() {
        assertCells(new String[][] {
            {python("ag_nulls", "(x NUMBER)", "NUMBER", "C", NULLS), "Function AG_NULLS successfully created."},
            {"SELECT ag_sum(a), ag_nulls(a) FROM ua", "35|2"},
            {"SELECT g, ag_sum(a) FROM ua GROUP BY g ORDER BY g", "1|30 ;; 2|5 ;; 3|0"},
            {"SELECT ag_sum(a) FROM ua WHERE 1 = 0", "null"},
            {python("ag_dot", "(x NUMBER, y NUMBER)", "NUMBER", "D", DOT), "Function AG_DOT successfully created."},
        });
        final String refused = answer("SELECT ag_dot(a, g) FROM ua");
        assertTrue(refused.startsWith("REFUSED ")
            && refused.contains("TypeError: unsupported operand type(s) for *: 'NoneType' and 'int'"), refused);
    }

    @Test
    public void theFinishedValueIsTheDeclaredType() {
        assertCells(new String[][] {
            {python("ag_txt", "(x NUMBER)", "VARCHAR", "S",
                SUM.substring(0, SUM.lastIndexOf("return self._s")) + "return str(self._s)"),
                "Function AG_TXT successfully created."},
            {"SELECT ag_txt(a) FROM ua", "35"},
            {python("ag_arr", "(x NUMBER)", "ARRAY", "A", VALUES), "Function AG_ARR successfully created."},
            {"SELECT ag_arr(a) FROM ua", "[5,10,20]"},
            {python("ag_avg", "(x NUMBER)", "FLOAT", "V", MEAN), "Function AG_AVG successfully created."},
            {"SELECT g, ag_avg(a) FROM ua GROUP BY g ORDER BY g", "1|15.0 ;; 2|5.0 ;; 3|null"},
        });
        assertShape("T:VARCHAR(134217728)", "SELECT ag_txt(a) AS t FROM ua");
    }

    @Test
    public void theHandlerClassNeedsItsFourMembers() {
        final String missing = "REFUSED Could not find %s in function %s with handler D";
        assertCells(new String[][] {
            {python("ag_noh", "(x NUMBER)", "NUMBER", "Missing", "class S: pass"),
                "REFUSED Could not find handler class in function AG_NOH with handler Missing"},
            {python("ae_d0", "(x NUMBER)", "NUMBER", "D", "class D:\n" + INIT),
                String.format(missing, "accumulate method", "AE_D0")},
            {python("ae_d1", "(x NUMBER)", "NUMBER", "D", "class D:\n" + INIT + ACCUMULATE),
                String.format(missing, "merge method", "AE_D1")},
            {python("ae_d2", "(x NUMBER)", "NUMBER", "D", "class D:\n" + INIT + ACCUMULATE + MERGE),
                String.format(missing, "finish method", "AE_D2")},
            {python("ae_d3", "(x NUMBER)", "NUMBER", "D", "class D:\n" + INIT + ACCUMULATE + MERGE + FINISH),
                String.format(missing, "aggregate_state method.", "AE_D3")},
            {"SELECT ae_d3(a) FROM ua", "REFUSED SQL compilation error: | Unknown function AE_D3."},
            {python("ae_d4", "(x NUMBER)", "NUMBER", "D", "class D:\n" + INIT + STATE + MERGE + FINISH),
                String.format(missing, "accumulate method", "AE_D4")},
            {python("ae_d6", "(x NUMBER)", "NUMBER", "D", "class D:\n" + INIT + STATE + ACCUMULATE + FINISH),
                String.format(missing, "merge method", "AE_D6")},
            {python("ae_d5", "(x NUMBER)", "NUMBER", "D", "class D:\n    _s = 0\n" + STATE + ACCUMULATE + MERGE
                + FINISH), "Function AE_D5 successfully created."},
            {"SELECT ae_d5(a) FROM ua", "35"},
        });
    }

    @Test
    public void thePythonCatalogEntryTellsTheAggregate() {
        answer("DESCRIBE FUNCTION ag_sum(NUMBER)");
        assertAnswer("signature|(X NUMBER) ;; returns|NUMBER(38,0) ;; language|PYTHON ;; null handling|CALLED ON NULL"
            + " INPUT ;; volatility|VOLATILE ;; imports|[] ;; handler|S ;; runtime_version|3.11 ;; packages|[]"
            + " ;; is_aggregate|true", "SELECT \"property\", \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"
            + " WHERE \"property\" NOT IN ('body', 'installed_packages')");
        assertAnswer("CREATE OR REPLACE AGGREGATE FUNCTION \"AG_SUM\"(\"X\" NUMBER(38,0)) | RETURNS NUMBER(38,0)"
            + " | LANGUAGE PYTHON | RUNTIME_VERSION = '3.11' | HANDLER = 'S' | AS ' | " + SUM.replace("\n", " | ")
            + " | ';", "SELECT GET_DDL('FUNCTION', 'ag_sum(NUMBER)')");
        answer("SHOW USER FUNCTIONS LIKE 'AG_SUM'");
        assertAnswer("AG_SUM|AG_SUM(NUMBER) RETURN NUMBER|Y|N|PYTHON", "SELECT \"name\", \"arguments\","
            + " \"is_aggregate\", \"is_table_function\", \"language\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }
}
