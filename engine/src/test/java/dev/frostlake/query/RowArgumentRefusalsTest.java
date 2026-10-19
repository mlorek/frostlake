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
 * A ROW written as a parenthesized list where a function takes a value is refused in the words of the function the
 * call is planned as — the deviations, variances and moments as COUNT, COUNT_IF as IFF, APPROX_COUNT_DISTINCT as
 * HLL_ACCUMULATE, the boolean aggregates as TO_BOOLEAN, AS_INTEGER, AS_NUMBER and AS_DECIMAL by their variant
 * parameter — and FIRST_VALUE or LAST_VALUE over one answers a select item of several columns; SYSTEM$TYPEOF names
 * its values' types, GROUPING reads it as no key, and ROLLUP and CUBE take one as a composite element (all
 * live-verified).
 */
public class RowArgumentRefusalsTest extends MeasuredCellsTestSupport {

    @Test
    public void aRowIsReadAsTheAccountReadsIt() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-01'), (2, 'y', '2024-02-01')");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT, b VARCHAR)");
        answer("SELECT EXISTS (SELECT (1, 2))");
        answer("SELECT NOT EXISTS (SELECT (1, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 22 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT EXISTS (SELECT (a, b) FROM t)");
        answer("SELECT a FROM t WHERE a = (SELECT (1, 2))");
        answer("SELECT a FROM t WHERE (SELECT (1, 2))");
        answer("SELECT (SELECT (1, 2))");
        assertAnswer("REFUSED SQL compilation error: | inconsistent data type for result columns for set operator "
                + "input branches, expected ROW(NUMBER(1,0), NUMBER(1,0)), got NUMBER(38,0)",
            "SELECT a FROM t UNION SELECT (1, 2)");
        assertAnswer("REFUSED SQL compilation error: | inconsistent data type for result columns for set operator "
                + "input branches, expected ROW(NUMBER(1,0), NUMBER(1,0)), got NUMBER(38,0)",
            "SELECT a FROM t INTERSECT SELECT (1, 2)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT (1, 2) UNION SELECT (1, 2)");
        answer("INSERT INTO t2 SELECT (1, 2)");
        answer("CREATE OR REPLACE VIEW vr AS SELECT (1, 2) AS r");
        answer("SELECT * FROM vr");
        answer("SELECT r FROM vr");
        answer("DESCRIBE VIEW vr");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 39 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT COUNT(*) FROM (SELECT (1, 2) AS r)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 32 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT * FROM (SELECT (1, 2) AS r)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 47 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "CREATE OR REPLACE TABLE tr AS SELECT (1, 2) AS r");
        answer("SELECT a FROM t WHERE a IN (SELECT (1, 2))");
        assertAnswer("ROW(NUMBER(1,0), NUMBER(1,0))[UNKNOWN]", "SELECT SYSTEM$TYPEOF((1, 2))");
        assertAnswer("1 ;; 1", "SELECT GROUPING((a, b)) FROM t GROUP BY (a, b)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT STDDEV((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT VARIANCE((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT VAR_POP((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT STDDEV_POP((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (ROW(NUMBER(38,0), VARCHAR(16777216)), NUMBER(1,0), NUMBER(1,0))",
            "SELECT COUNT_IF((a, b)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'HLL_ACCUMULATE': (ROW(NUMBER(38,0), VARCHAR(16777216)))",
            "SELECT APPROX_COUNT_DISTINCT((a, b)) FROM t");
        assertAnswer("REFUSED SQL compilation error: | invalid type [TO_BOOLEAN(ROW(T.A, T.B))] for parameter "
                + "'TO_BOOLEAN'",
            "SELECT BOOLAND_AGG((a, b)) FROM t");
        assertAnswer("REFUSED SQL compilation error: | invalid type [TO_BOOLEAN(ROW(T.A, T.B))] for parameter "
                + "'TO_BOOLEAN'",
            "SELECT BOOLOR_AGG((a, b)) FROM t");
        assertAnswer("REFUSED SQL compilation error: | invalid type [TO_BOOLEAN(ROW(T.A, T.B))] for parameter "
                + "'TO_BOOLEAN'",
            "SELECT BOOLXOR_AGG((a, b)) FROM t");
        assertAnswer("REFUSED SQL compilation error: | invalid type [ROW(NUMBER(1,0), NUMBER(1,0))] for parameter "
                + "'AS_INTEGER(variantValue...)'",
            "SELECT AS_INTEGER((1, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_DOUBLE': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT AS_DOUBLE((1, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_VARCHAR': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT AS_VARCHAR((1, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 27 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT FIRST_VALUE((a, b)) OVER (ORDER BY a) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 26 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT LAST_VALUE((a, b)) OVER (ORDER BY a) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LAG': (ROW(NUMBER(38,0), VARCHAR(16777216)))",
            "SELECT LAG((a, b)) OVER (ORDER BY a) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 29 | Invalid argument types for "
                + "function 'LIKE_ANY': (ROW(NUMBER(38,0), VARCHAR(16777216)), NULL, VARCHAR(1))",
            "SELECT a FROM t WHERE (a, b) LIKE ANY ('x')");
        answer("SELECT * FROM TABLE(FLATTEN((1, 2)))");
        answer("SELECT * FROM TABLE(SPLIT_TO_TABLE((1, 2), ','))");
        answer("SELECT * FROM TABLE(GENERATOR(ROWCOUNT => (1, 2)))");
        answer("SELECT a FROM t PIVOT (SUM(a) FOR b IN ((1, 2)))");
        assertAnswer("1|x|1 ;; 2|y|1 ;; null|null|2",
            "SELECT a, b, COUNT(*) FROM t GROUP BY ROLLUP ((a, b)) ORDER BY 1, 2");
        assertAnswer("1|x|1 ;; 2|y|1 ;; null|null|2",
            "SELECT a, b, COUNT(*) FROM t GROUP BY CUBE ((a, b)) ORDER BY 1, 2");
        assertAnswer("1|x|2024-01-01|1 ;; 1|x|null|1 ;; 2|y|2024-02-01|1 ;; 2|y|null|1 ;; null|null|null|2",
            "SELECT a, b, d, COUNT(*) FROM t GROUP BY ROLLUP ((a, b), d) ORDER BY 1, 2, 3");
        assertAnswer("1|x|1 ;; 2|y|1", "SELECT a, b, COUNT(*) FROM t GROUP BY GROUPING SETS ((a, b)) ORDER BY 1, 2");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 6 |  variable 'X' cannot have its type "
                + "inferred from initializer",
            "BEGIN LET x := (1, 2); RETURN 1; END");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 13 |  Unsupported: return multiple "
                + "columns row. Consider returning a table (from a RESULTSET) or an ARRAY.",
            "BEGIN RETURN (1, 2); END");
        answer("SELECT a FROM t ORDER BY (b DESC, a)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'. | syntax "
                + "error line 1 at position 22 unexpected '('.",
            "SELECT a FROM t LIMIT (1, 2)");
        answer("SELECT a FROM t SAMPLE ((1, 2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'FROM'. | syntax "
                + "error line 1 at position 33 unexpected ')'.",
            "SELECT ((EXTRACT('wks' FROM 2, 1))) FROM t");
    }

    @Test
    public void aRowIsReadByEachReaderInItsWords() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-01'), (2, 'y', '2024-02-01')");
        engine.execute("CREATE OR REPLACE TABLE t1c (a INT)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 22 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT EXISTS (SELECT (1, 2) FROM t)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 22 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT EXISTS (SELECT (1, 2) WHERE 1 = 0)");
        answer("SELECT EXISTS (SELECT (1, 2), 3)");
        answer("SELECT EXISTS (SELECT (1, 2) AS r)");
        answer("SELECT a FROM t WHERE EXISTS (SELECT (1, 2)) ORDER BY a");
        answer("SELECT a FROM t WHERE NOT EXISTS (SELECT (1, 2)) ORDER BY a");
        answer("SELECT a FROM t WHERE EXISTS (SELECT (t.a, 2)) ORDER BY a");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 22 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT EXISTS (SELECT (1, 2) UNION ALL SELECT (3, 4))");
        answer("SELECT EXISTS (SELECT 1, (1, 2))");
        answer("SELECT CASE WHEN EXISTS (SELECT (1, 2)) THEN 'y' END");
        answer("SELECT (SELECT (1, 2) AS r)");
        answer("SELECT 1 + (SELECT (1, 2))");
        answer("SELECT a FROM t WHERE a > (SELECT (1, 2))");
        answer("SELECT a FROM t WHERE (a, b) = (SELECT (1, 'x'))");
        answer("SELECT a FROM t WHERE (a, b) IN (SELECT (1, 'x'))");
        answer("SELECT a FROM t WHERE a = ANY (SELECT (1, 2))");
        answer("SELECT a FROM t WHERE (SELECT (a, 2))");
        answer("SELECT a FROM t WHERE (SELECT (1, 2) FROM t LIMIT 1)");
        answer("INSERT INTO t1c SELECT (1, 2)");
        answer("INSERT INTO t1c (a) SELECT (1, 2)");
        answer("INSERT INTO t1c SELECT (a, a) FROM t");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "NUMBER(38,0) but got ROW(ANY) for column A",
            "INSERT INTO t1c VALUES ((1, 2))");
        answer("CREATE OR REPLACE VIEW vr2 AS SELECT (1, 2)");
        assertAnswer("REFUSED SQL compilation error: | View 'VR2' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE VR2.",
            "DESCRIBE VIEW vr2");
        answer("CREATE OR REPLACE VIEW vr3 AS SELECT a, (a, b) AS r FROM t");
        answer("SELECT a FROM vr3 ORDER BY a");
        answer("SELECT COUNT(*) FROM vr3");
        answer("SELECT r FROM vr3");
        answer("DESCRIBE VIEW vr3");
        answer("CREATE OR REPLACE VIEW vr4 AS SELECT a FROM t WHERE EXISTS (SELECT (a, b) FROM t)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SUM': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT AVG((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SUM': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT SUM((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: | Function MIN does not support ROW(NUMBER(38,0), NUMBER(38,0)) "
                + "argument type",
            "SELECT MIN((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT VAR_SAMP((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT STDDEV_SAMP((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT VARIANCE_SAMP((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT VARIANCE_POP((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT KURTOSIS((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT SKEW((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT ANY_VALUE((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_AGG': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT ARRAY_AGG((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LISTAGG': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT LISTAGG((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: | Function MODE does not support ROW(NUMBER(38,0), NUMBER(38,0)) "
                + "argument type",
            "SELECT MODE((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'HASH_AGG': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT HASH_AGG((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITAND_AGG': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT BITAND_AGG((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT COUNT((a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT COUNT(DISTINCT (a, a)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'APPROX_COUNT_DISTINCT': (ROW(NUMBER(38,0), VARCHAR(16777216)), NUMBER(38,0))",
            "SELECT APPROX_COUNT_DISTINCT((a, b), a) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[COUNT_IF(ROW(T.A, T.B), 1)] expected 1, got 2",
            "SELECT COUNT_IF((a, b), 1) FROM t");
        assertAnswer("REFUSED SQL compilation error: | invalid type [ROW(NUMBER(1,0), NUMBER(1,0))] for parameter "
                + "'AS_NUMBER(variantValue...)'",
            "SELECT AS_NUMBER((1, 2))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [ROW(NUMBER(1,0), NUMBER(1,0))] for parameter "
                + "'AS_DECIMAL(variantValue...)'",
            "SELECT AS_DECIMAL((1, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_BOOLEAN': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT AS_BOOLEAN((1, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_DATE': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT AS_DATE((1, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_OBJECT': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT AS_OBJECT((1, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_ARRAY': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT AS_ARRAY((1, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_CHAR': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT AS_CHAR((1, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_BINARY': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT AS_BINARY((1, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_REAL': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT AS_REAL((1, 2))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [ROW(NUMBER(1,0), VARCHAR(1))] for parameter "
                + "'AS_INTEGER(variantValue...)'",
            "SELECT AS_INTEGER((1, 'x'))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [ROW(NUMBER(38,0), VARCHAR(16777216))] for "
                + "parameter 'AS_INTEGER(variantValue...)'",
            "SELECT AS_INTEGER((a, b)) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NTH_VALUE': (ROW(NUMBER(38,0), VARCHAR(16777216)), NUMBER(1,0))",
            "SELECT NTH_VALUE((a, b), 1) OVER (ORDER BY a) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LEAD': (ROW(NUMBER(38,0), VARCHAR(16777216)))",
            "SELECT LEAD((a, b)) OVER (ORDER BY a) FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 63 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT FIRST_VALUE((a, b)) OVER (PARTITION BY a ORDER BY a) AS fv FROM t");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 43 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT a, FIRST_VALUE((a, b)) IGNORE NULLS OVER (ORDER BY a) FROM t");
        assertAnswer("REFUSED SQL compilation error: | invalid type [TO_BOOLEAN(ROW(1, 2))] for parameter 'TO_BOOLEAN'",
            "SELECT BOOLAND_AGG((1, 2))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [TO_BOOLEAN(ROW(T.A, 1))] for parameter "
                + "'TO_BOOLEAN'",
            "SELECT BOOLOR_AGG((a, 1)) FROM t");
        assertAnswer("REFUSED SQL compilation error: | invalid type [ROW(NUMBER(1,0), NUMBER(1,0))] for parameter "
                + "'INPUT'",
            "SELECT * FROM TABLE(FLATTEN(INPUT => (1, 2)))");
        answer("SELECT * FROM TABLE(FLATTEN((a, b))), t");
        answer("SELECT * FROM TABLE(SPLIT_TO_TABLE('a,b', (1, 2)))");
        answer("SELECT * FROM TABLE(GENERATOR(ROWCOUNT => 2, TIMELIMIT => (1, 2)))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 0 | SELECT with no columns",
            "SELECT * FROM TABLE(GENERATOR((1, 2)))");
        assertAnswer("ROW(NUMBER(38,0), VARCHAR(16777216))[UNKNOWN] ;; ROW(NUMBER(38,0), VARCHAR(16777216))[UNKNOWN]",
            "SELECT SYSTEM$TYPEOF((a, b)) FROM t");
        assertAnswer("ROW(NUMBER(1,0), ROW(NUMBER(1,0), VARCHAR(1)))[UNKNOWN]", "SELECT SYSTEM$TYPEOF((1, (2, 'x')))");
        assertAnswer("ROW(NULL, NUMBER(2,1))[UNKNOWN]", "SELECT SYSTEM$TYPEOF((NULL, 1.5))");
        assertAnswer("1 ;; 1", "SELECT GROUPING((a, b)) FROM t GROUP BY a, b");
        assertAnswer("1|1|x ;; 1|2|y ;; 1|null|null",
            "SELECT GROUPING((a, b)), a, b FROM t GROUP BY ROLLUP ((a, b)) ORDER BY 2, 3");
        assertAnswer("2 ;; 2", "SELECT GROUPING((a, b), a) FROM t GROUP BY a, b");
        assertAnswer("1|1 ;; 2|1 ;; null|2", "SELECT a, COUNT(*) FROM t GROUP BY ROLLUP ((a, b)) ORDER BY 1");
        assertAnswer("1|x|1 ;; 1|x|1 ;; 2|y|1 ;; 2|y|1 ;; null|null|2",
            "SELECT a, b, COUNT(*) FROM t GROUP BY ROLLUP ((a, b), (b)) ORDER BY 1, 2");
        assertAnswer("1|x|1|0|0 ;; 2|y|1|0|0 ;; null|null|2|1|1",
            "SELECT a, b, COUNT(*), GROUPING(a), GROUPING(b) FROM t GROUP BY CUBE ((a, b)) ORDER BY 1, 2");
        assertAnswer("1|x|1 ;; 2|y|1 ;; null|null|2",
            "SELECT a, b, COUNT(*) FROM t GROUP BY ROLLUP (((a, b))) ORDER BY 1, 2");
        assertAnswer("1|x|1 ;; 1|x|1 ;; 2|y|1 ;; 2|y|1 ;; null|null|2",
            "SELECT a, b, COUNT(*) FROM t GROUP BY ROLLUP ((a, b), a) ORDER BY 1, 2");
        assertAnswer("1|1 ;; 2|1 ;; null|2", "SELECT a, COUNT(*) FROM t GROUP BY ROLLUP ((a, a)) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 6 |  variable 'X' cannot have its type "
                + "inferred from initializer",
            "BEGIN LET x := (1, 'a'); RETURN 1; END");
        answer("BEGIN LET x INT := (1, 2); RETURN 1; END");
        answer("DECLARE x INT; BEGIN x := (1, 2); RETURN x; END");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 13 |  Unsupported: return multiple "
                + "columns row. Consider returning a table (from a RESULTSET) or an ARRAY.",
            "BEGIN RETURN ((1, 2)); END");
        assertAnswer("true", "BEGIN RETURN (1, 2) = (1, 2); END");
        assertAnswer("REFUSED SQL compilation error: error line 2 at position 9 |  Unsupported: return multiple "
                + "columns row. Consider returning a table (from a RESULTSET) or an ARRAY.",
            "BEGIN\n  RETURN (1, 2);\nEND");
    }
}
