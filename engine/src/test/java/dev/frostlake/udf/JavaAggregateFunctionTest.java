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

/**
 * CREATE AGGREGATE FUNCTION with a Java handler (live-verified): the handler class's initialize, accumulate, merge
 * and finish carry a group's state, every row of a group reaches accumulate - a NULL argument too - and the call is
 * an aggregate wherever a built-in one is, except that it takes no window, no DISTINCT, no WITHIN GROUP and no
 * PIVOT. The catalog tells it as an aggregate.
 */
public class JavaAggregateFunctionTest extends MeasuredCellsTestSupport {

    /** A sum whose state is a Long: a NULL leaves the state as it is. */
    private static final String SUM = "public class H { public Long initialize() { return 0L; }"
        + " public Long accumulate(Long s, Long x) { return x == null ? s : s + x; }"
        + " public Long merge(Long a, Long b) { return a + b; } public Long finish(Long s) { return s; } }";

    /** The sum of the products of two arguments. */
    private static final String DOT = "CREATE OR REPLACE AGGREGATE FUNCTION aj2a(x NUMBER, y NUMBER) RETURNS NUMBER"
        + " LANGUAGE JAVA RUNTIME_VERSION = '17' HANDLER = 'H' AS $$\n"
        + "public class H { public Long initialize() { return 0L; }"
        + " public Long accumulate(Long s, Long x, Long y) { return x == null || y == null ? s : s + x * y; }"
        + " public Long merge(Long a, Long b) { return a + b; } public Long finish(Long s) { return s; } }\n$$";

    private static final String SHOWN = "SELECT \"name\", \"is_aggregate\", \"is_secure\", \"arguments\""
        + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1";

    private static String java(final String name, final String body) {
        return "CREATE OR REPLACE AGGREGATE FUNCTION " + name + "(x NUMBER) RETURNS NUMBER LANGUAGE JAVA"
            + " RUNTIME_VERSION = '17' HANDLER = 'H' AS $$\n" + body + "\n$$";
    }

    @BeforeEach
    public void createTheSum() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE ua (g INT, a INT)", "Table UA successfully created."},
            {"INSERT INTO ua VALUES (1, 10), (1, 20), (2, 5), (2, NULL), (3, NULL)", "5"},
            {java("ag_sum", SUM), "Function AG_SUM successfully created."},
        });
    }

    @Test
    public void aGroupIsFoldedThroughTheHandlersState() {
        assertCells(new String[][] {
            {"SELECT g, ag_sum(a) FROM ua GROUP BY g ORDER BY g", "1|30 ;; 2|5 ;; 3|0"},
            {"SELECT ag_sum(a) FROM ua", "35"},
            {"SELECT ag_sum(a) FROM ua WHERE 1 = 0", "null"},
            {"SELECT ag_sum(1)", "1"},
            {"SELECT g FROM ua GROUP BY g HAVING ag_sum(a) > 10 ORDER BY 1", "1"},
            {"SELECT ag_sum(a) + 1, TYPEOF(ag_sum(a)::VARIANT) FROM ua", "36|INTEGER"},
            {"SELECT g FROM ua GROUP BY g ORDER BY ag_sum(a) DESC", "1 ;; 2 ;; 3"},
            {"SELECT (SELECT ag_sum(a) FROM ua)", "35"},
            {"SELECT g, ag_sum(a) FROM ua GROUP BY ROLLUP (g) ORDER BY g NULLS LAST", "1|30 ;; 2|5 ;; 3|0 ;; null|35"},
            {"SELECT g, ag_sum(a) FROM ua GROUP BY ALL ORDER BY g", "1|30 ;; 2|5 ;; 3|0"},
            {"SELECT test_schema.ag_sum(a) FROM ua", "35"},
            {"SELECT test_db.test_schema.ag_sum(a) FROM ua", "35"},
        });
        assertShape("S:NUMBER(38,0)", "SELECT ag_sum(a) AS s FROM ua");
    }

    @Test
    public void everyArgumentReachesTheHandler() {
        assertCells(new String[][] {
            {DOT, "Function AJ2A successfully created."},
            {"SELECT g, aj2a(a, g) FROM ua GROUP BY g ORDER BY g", "1|30 ;; 2|10 ;; 3|0"},
            {"SELECT aj2a(a) FROM ua", "REFUSED SQL compilation error: error line 1 at position 7"
                + " | Invalid argument types for function 'AJ2A': (NUMBER(38,0))"},
            {"SELECT aj2a(a, g, g) FROM ua", "REFUSED SQL compilation error: error line 1 at position 7"
                + " | too many arguments for function [AJ2A(UA.A, UA.G, UA.G)] expected 2, got 3"},
            {"SELECT * FROM ua WHERE aj2a(a, g) > 0", "REFUSED SQL compilation error:"
                + " | Invalid aggregate function in where clause [EXTENSION_FUNC_A(UA.A, UA.G)]"},
        });
    }

    @Test
    public void aUserAggregateTakesNoWindowQuantifierOrPivot() {
        final String kind = "REFUSED SQL compilation error: | Invalid function type [AG_SUM] for window function.";
        final String unknown = "REFUSED SQL compilation error: | Unknown function AG_SUM.";
        assertCells(new String[][] {
            {"SELECT ag_sum(a) OVER () FROM ua", kind},
            {"SELECT ag_sum(a) OVER (PARTITION BY g) FROM ua", kind},
            {"SELECT ag_sum(DISTINCT a) FROM ua", unknown},
            {"SELECT ag_sum(a) WITHIN GROUP (ORDER BY a) FROM ua", unknown},
            {"SELECT * FROM ua PIVOT (ag_sum(a) FOR g IN (1, 2))", "REFUSED SQL compilation error:"
                + " | invalid function 'ag_sum'"},
            {"SELECT ag_sum(a, g) FROM ua", "REFUSED SQL compilation error: error line 1 at position 7"
                + " | too many arguments for function [AG_SUM(UA.A, UA.G)] expected 1, got 2"},
            {"SELECT ag_sum() FROM ua", "REFUSED SQL compilation error: error line 1 at position 7"
                + " | Invalid argument types for function 'AG_SUM': ()"},
            {"SELECT * FROM ua WHERE ag_sum(a) > 1", "REFUSED SQL compilation error:"
                + " | Invalid aggregate function in where clause [EXTENSION_FUNC_A(UA.A)]"},
            {"SELECT g, ag_sum(a) FROM ua",
                "REFUSED SQL compilation error: | [UA.G] is not a valid group by expression"},
            {"SELECT SUM(ag_sum(a)) FROM ua GROUP BY g", "REFUSED SQL compilation error:  | Aggregate functions"
                + " cannot be nested: [EXTENSION_FUNC_A(UA.A)] nested in [SUM(EXTENSION_FUNC_A(UA.A))]"},
            {"SELECT ag_sum(SUM(a)) FROM ua GROUP BY g", "REFUSED SQL compilation error:  | Aggregate functions"
                + " cannot be nested: [SUM(UA.A)] nested in [EXTENSION_FUNC_A(SUM(UA.A))]"},
            {"SELECT g, ag_sum(a) FROM ua GROUP BY g QUALIFY ag_sum(a) > 5 ORDER BY g", "REFUSED SQL compilation"
                + " error: error line 1 at position 39 | found QUALIFY clause but no window function."},
        });
    }

    @Test
    public void aJavaHandlerNeedsEachOfItsFourMethods() {
        final String required = "REFUSED Failed to find a public method named \"%s\" with %d arguments"
            + " | The %s() method is required for Java UDAFs in function %s with handler H";
        assertCells(new String[][] {
            {java("aj1", "public class H { }"), String.format(required, "initialize", 0, "initialize", "AJ1")},
            {java("aj2", "public class H { public void initialize() { } }"),
                String.format(required, "accumulate", 2, "accumulate", "AJ2")},
            {java("aj3", "public class H { private long s; public void initialize() { s = 0; }"
                + " public void accumulate(Long x) { if (x != null) s += x; } }"),
                String.format(required, "accumulate", 2, "accumulate", "AJ3")},
            {java("ak1", "public class H { public Long initialize() { return 0L; }"
                + " public Long accumulate(Long s, Long x) { return x == null ? s : s + x; } }"),
                String.format(required, "merge", 2, "merge", "AK1")},
            {java("ak2", "public class H { public Long initialize() { return 0L; }"
                + " public Long accumulate(Long s, Long x) { return x == null ? s : s + x; }"
                + " public Long merge(Long a, Long b) { return a + b; } }"),
                String.format(required, "finish", 1, "finish", "AK2")},
            {"SELECT ak2(a) FROM ua", "REFUSED SQL compilation error: | Unknown function AK2."},
            {java("ak4", "public class H { public long[] initialize() { return new long[1]; }"
                + " public long[] accumulate(long[] s, Long x) { if (x != null) s[0] += x; return s; }"
                + " public long[] merge(long[] a, long[] b) { a[0] += b[0]; return a; }"
                + " public Long finish(long[] s) { return s[0]; } }"), "Function AK4 successfully created."},
            {"SELECT g, ak4(a) FROM ua GROUP BY g ORDER BY g", "1|30 ;; 2|5 ;; 3|0"},
        });
    }

    @Test
    public void onlyAPythonOrJavaFunctionOfArgumentsAggregates() {
        final String counted = "CREATE OR REPLACE AGGREGATE FUNCTION ag_none() RETURNS NUMBER LANGUAGE JAVA"
            + " RUNTIME_VERSION = '17' HANDLER = 'H' AS 'public class H { }'";
        assertCells(new String[][] {
            {"CREATE OR REPLACE AGGREGATE FUNCTION ag_sql(x NUMBER) RETURNS NUMBER LANGUAGE SQL AS 'x'",
                "REFUSED Aggregate functions not supported for language 'SQL'."},
            {"CREATE OR REPLACE AGGREGATE FUNCTION ag_js(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'return X;'",
                "REFUSED Aggregate functions not supported for language 'JAVASCRIPT'."},
            {"CREATE OR REPLACE AGGREGATE FUNCTION ag_sc(x NUMBER) RETURNS NUMBER LANGUAGE SCALA"
                + " RUNTIME_VERSION = '2.12' HANDLER = 'H' AS 'class H {}'",
                "REFUSED Aggregate functions not supported for language 'SCALA'."},
            {"CREATE OR REPLACE AGGREGATE FUNCTION ag_tab(x NUMBER) RETURNS TABLE (y NUMBER) LANGUAGE PYTHON"
                + " RUNTIME_VERSION = '3.11' HANDLER = 'S' AS 'class S: pass'",
                "REFUSED SQL compilation error: Aggregate functions cannot return a table"},
            {"CREATE OR REPLACE AGGREGATE SECURE FUNCTION ag_sec(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON"
                + " RUNTIME_VERSION = '3.11' HANDLER = 'S' AS 'class S: pass'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SECURE'."},
            {counted, "REFUSED SQL compilation error: Cannot create user-defined aggregate function with 0"
                + " arguments: " + counted + "."},
        });
    }

    @Test
    public void theCatalogTellsAnAggregateFunction() {
        answer("SHOW USER FUNCTIONS LIKE 'AG_SUM'");
        assertAnswer("AG_SUM|AG_SUM(NUMBER) RETURN NUMBER|Y|N|JAVA", "SELECT \"name\", \"arguments\", \"is_aggregate\","
            + " \"is_table_function\", \"language\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        answer("DESCRIBE FUNCTION ag_sum(NUMBER)");
        assertAnswer("signature|(X NUMBER) ;; returns|NUMBER(38,0) ;; language|JAVA ;; null handling|CALLED ON NULL"
            + " INPUT ;; volatility|VOLATILE ;; imports|[] ;; handler|H ;; target_path|null ;; runtime_version|17"
            + " ;; packages|[]", "SELECT \"property\", \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"
            + " WHERE \"property\" NOT IN ('body', 'installed_packages')");
        assertAnswer("CREATE OR REPLACE AGGREGATE FUNCTION \"AG_SUM\"(\"X\" NUMBER(38,0)) | RETURNS NUMBER(38,0)"
            + " | LANGUAGE JAVA | RUNTIME_VERSION = '17' | HANDLER = 'H' | AS ' | " + SUM + " | ';",
            "SELECT GET_DDL('FUNCTION', 'ag_sum(NUMBER)')");
        assertAnswer("AG_SUM|YES|NUMBER(38,0)", "SELECT FUNCTION_NAME, IS_AGGREGATE, DATA_TYPE"
            + " FROM INFORMATION_SCHEMA.FUNCTIONS WHERE FUNCTION_NAME = 'AG_SUM'");
    }

    @Test
    public void aRenamedOrSecureAggregateStaysOne() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE SECURE AGGREGATE FUNCTION ag_sec(x NUMBER) RETURNS NUMBER LANGUAGE JAVA"
                + " RUNTIME_VERSION = '17' HANDLER = 'H' AS $$\n" + SUM + "\n$$",
                "Function AG_SEC successfully created."},
            {"ALTER FUNCTION ag_sum(NUMBER) RENAME TO ag_ren", "Statement executed successfully."},
            {"SELECT ag_ren(a) FROM ua", "35"},
        });
        answer("SHOW USER FUNCTIONS LIKE 'AG_%'");
        assertAnswer("AG_REN|Y|N|AG_REN(NUMBER) RETURN NUMBER ;; AG_SEC|Y|Y|AG_SEC(NUMBER) RETURN NUMBER", SHOWN);
        assertCells(new String[][] {
            {"DROP FUNCTION ag_ren(NUMBER)", "AG_REN successfully dropped."},
            {"SELECT ag_ren(a) FROM ua", "REFUSED SQL compilation error: | Unknown function AG_REN."},
        });
    }
}
