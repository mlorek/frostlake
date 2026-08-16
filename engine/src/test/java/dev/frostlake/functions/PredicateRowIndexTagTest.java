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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SYSTEM$TYPEOF tags a boolean PREDICATE — a comparison, IS [NOT] NULL, IN, BETWEEN, the LIKE family,
 * EXISTS, a quantified ANY / ALL, a predicate function, and NOT / AND / OR over one — as
 * {@code BOOLEAN[ROWINDEX]}, where a stored or computed boolean is {@code BOOLEAN[SB1]}: a literal, a
 * column, a cast, a conversion, the BOOL* functions, a conditional even over predicate branches, an
 * aggregate, a window function, a scalar subquery and a derived relation's column. Every cell is
 * live-verified, over constants and over a table of two distinct rows.
 */
public class PredicateRowIndexTagTest extends BaseDatabaseTest {

    private static final String PREDICATE = "BOOLEAN[ROWINDEX]";
    private static final String VALUE = "BOOLEAN[SB1]";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE bo (n NUMBER(10,0), m NUMBER(10,0), s VARCHAR(10), b BOOLEAN,"
            + " v VARIANT)");
        engine.execute("INSERT INTO bo SELECT 1, 2, 'a', TRUE, PARSE_JSON('[1]')");
        engine.execute("INSERT INTO bo SELECT 2, 3, 'b', FALSE, PARSE_JSON('{\"a\":1}')");
    }

    private List<String> rows(final String sql) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            final StringBuilder line = new StringBuilder();
            for (int c = 0; c < row.getValues().size(); c++) {
                if (c > 0) {
                    line.append(", ");
                }
                line.append(row.getValue(c));
            }
            out.add(line.toString());
        }
        return out;
    }

    private String typeOf(final String expression) {
        final List<String> answer = rows("SELECT SYSTEM$TYPEOF(" + expression + ")");
        return answer.size() == 1 ? answer.get(0) : String.valueOf(answer);
    }

    private List<String> overTable(final String expression) {
        return rows("SELECT SYSTEM$TYPEOF(" + expression + ") FROM bo");
    }

    @Test
    public void aComparisonIsAPredicate() {
        for (final String predicate : List.of("1 = 2", "1 = 1", "1 < 2", "1 <> 2", "1 >= 2", "'a' = 'b'",
                "1.5 = 2.5", "TRUE = FALSE", "1 = NULL", "NULL = NULL", "(1 = 2) = TRUE", "1 IS NULL", "NULL IS NULL",
                "1 IS NOT NULL", "1 IS DISTINCT FROM 2", "1 IS NOT DISTINCT FROM 2", "'a' LIKE 'b'", "'a' ILIKE 'b'",
                "'a' RLIKE 'b'", "'a' REGEXP 'b'", "'a' NOT LIKE 'b'", "'a' NOT ILIKE 'b'", "'a' NOT RLIKE 'b'",
                "'a' LIKE ANY ('a', 'b')", "1 IN (1, 2)", "1 NOT IN (1, 2)", "(1, 2) IN ((1, 2))", "1 IN (SELECT 1)",
                "1 = ANY (SELECT 1)", "1 > ALL (SELECT 0)", "1 BETWEEN 0 AND 2", "1 NOT BETWEEN 0 AND 2",
                "EXISTS (SELECT 1)", "NOT EXISTS (SELECT 1)", "COLLATE('a', 'en') = 'a'")) {
            assertEquals(PREDICATE, typeOf(predicate), predicate);
        }
    }

    @Test
    public void aPredicateFunctionIsAPredicate() {
        for (final String call : List.of("EQUAL_NULL(1, 2)", "IS_NULL_VALUE(PARSE_JSON('null'))",
                "STARTSWITH('ab', 'a')", "ENDSWITH('ab', 'b')", "CONTAINS('ab', 'a')", "REGEXP_LIKE('a', 'a')",
                "RLIKE('a', 'b')", "LIKE('a', 'a')", "ILIKE('a', 'A')", "ARRAY_CONTAINS(1::VARIANT, [1])",
                "ARRAY_CONTAINS(1, [1])", "ARRAYS_OVERLAP([1], [1])", "IS_ARRAY(PARSE_JSON('[1]'))",
                "IS_INTEGER(1::VARIANT)", "IS_BOOLEAN(TRUE::VARIANT)", "IS_VARCHAR('a'::VARIANT)",
                "IS_CHAR('a'::VARIANT)", "IS_DECIMAL(1.5::VARIANT)", "IS_DOUBLE(1.5::VARIANT)", "IS_REAL(1.5::VARIANT)",
                "IS_DATE(CURRENT_DATE()::VARIANT)", "IS_DATE_VALUE(CURRENT_DATE()::VARIANT)",
                "IS_TIME(CURRENT_TIME()::VARIANT)", "IS_TIMESTAMP_NTZ(1::VARIANT)", "IS_TIMESTAMP_LTZ(1::VARIANT)",
                "IS_TIMESTAMP_TZ(1::VARIANT)", "IS_BINARY(1::VARIANT)")) {
            assertEquals(PREDICATE, typeOf(call), call);
        }
    }

    @Test
    public void notAndOrCarryAPredicateAndNothingElse() {
        for (final String predicate : List.of("NOT 1 = 2", "1 = 1 AND 2 = 2", "1 = 2 OR 2 = 2", "1 = 2 AND TRUE",
                "TRUE AND 1 = 2", "1 = 2 OR TRUE", "NOT (1 IS NULL)", "NOT STARTSWITH('ab', 'a')",
                "STARTSWITH('ab', 'a') AND TRUE", "ARRAY_CONTAINS(1::VARIANT, [1]) AND TRUE")) {
            assertEquals(PREDICATE, typeOf(predicate), predicate);
        }
        for (final String value : List.of("NOT TRUE", "TRUE AND FALSE", "TRUE OR FALSE", "NULL AND TRUE", "TRUE",
                "NULL::BOOLEAN")) {
            assertEquals(VALUE, typeOf(value), value);
        }
    }

    @Test
    public void aComputedBooleanIsAValue() {
        for (final String value : List.of("IFF(1 = 2, TRUE, FALSE)", "IFF(TRUE, 1 = 2, FALSE)",
                "CASE WHEN 1 = 2 THEN TRUE ELSE FALSE END", "COALESCE(1 = 2, TRUE)", "NVL(1 = 2, FALSE)",
                "NULLIF(1 = 2, TRUE)", "IFF(1 = 2, 1 = 2, 1 = 3)", "DECODE(1, 1, 1 = 2, FALSE)", "(1 = 2)::BOOLEAN",
                "TO_BOOLEAN('true')", "'true'::BOOLEAN", "TRY_TO_BOOLEAN('x')", "AS_BOOLEAN(TRUE::VARIANT)",
                "BOOLAND(1, 2)", "BOOLOR(1, 0)", "BOOLXOR(1, 0)", "BOOLNOT(1)", "ZEROIFNULL(1)::BOOLEAN",
                "(SELECT 1 = 2)", "IS_ROLE_IN_SESSION('PUBLIC')", "MAP_CONTAINS_KEY('a', {'a': 1}::MAP(VARCHAR, NUMBER))",
                "GET(PARSE_JSON('{\"a\":true}'), 'a')::BOOLEAN")) {
            assertEquals(VALUE, typeOf(value), value);
        }
        assertEquals("VARIANT[LOB]", typeOf("TO_VARIANT(1 = 2)"));
    }

    @Test
    public void overATableEveryRowIsTaggedAlike() {
        for (final String predicate : List.of("n = 1", "n = m", "n < 5", "n > 5", "b = TRUE", "n IS NULL",
                "s LIKE 'a%'", "n IN (1, 2)", "n BETWEEN 1 AND 2", "n = 1 AND b", "b OR n = 1", "(n = 1) AND (m = 2)",
                "NOT (n = 1)", "NOT NOT (n = 1)", "n = 1 OR n = 2", "1 = 2", "STARTSWITH(s, 'a')", "CONTAINS(s, 'a')",
                "EQUAL_NULL(n, 1)", "IS_ARRAY(v)", "IS_OBJECT(v)", "IS_DECIMAL(v)", "IS_NULL_VALUE(v)",
                "SEARCH(s, 'a')")) {
            assertEquals(Collections.nCopies(2, PREDICATE), overTable(predicate), predicate);
        }
        for (final String value : List.of("b", "NOT b", "b AND TRUE", "IFF(n = 1, n = 1, n = 2)", "NVL(n = 1, FALSE)",
                "BOOLAND(n, 1)", "TO_BOOLEAN(n)", "n::BOOLEAN", "IFF(n = 1, TRUE, NULL)", "LAG(n = 1) OVER (ORDER BY n)",
                "BOOLAND_AGG(b)", "BOOLAND_AGG(n = 1)", "MAX(n = 1)", "ANY_VALUE(n = 1)")) {
            assertEquals(Collections.nCopies(2, VALUE), overTable(value), value);
        }
        assertEquals(List.of(VALUE), overTable("MAX(b)"));
        assertEquals(Collections.nCopies(2, PREDICATE + ", " + VALUE),
            rows("SELECT SYSTEM$TYPEOF(n = 1), SYSTEM$TYPEOF(b) FROM bo"));
    }

    @Test
    public void aDerivedColumnIsAValueHoweverComputed() {
        engine.execute("CREATE OR REPLACE VIEW bov AS SELECT n = 1 AS x, b FROM bo");
        engine.execute("CREATE OR REPLACE TABLE boct AS SELECT n = 1 AS x FROM bo");
        assertEquals(Collections.nCopies(2, VALUE), rows("SELECT SYSTEM$TYPEOF(x) FROM (SELECT n = 1 AS x FROM bo)"));
        assertEquals(List.of(VALUE), rows("SELECT SYSTEM$TYPEOF(x) FROM (SELECT 1 = 2 AS x)"));
        assertEquals(Collections.nCopies(2, VALUE),
            rows("WITH c AS (SELECT n = 1 AS x FROM bo) SELECT SYSTEM$TYPEOF(x) FROM c"));
        assertEquals(Collections.nCopies(4, VALUE),
            rows("SELECT SYSTEM$TYPEOF(x) FROM (SELECT n = 1 AS x FROM bo UNION ALL SELECT b FROM bo)"));
        assertEquals(Collections.nCopies(2, VALUE), rows("SELECT SYSTEM$TYPEOF(x) FROM bov"));
        assertEquals(Collections.nCopies(2, VALUE), rows("SELECT SYSTEM$TYPEOF(x) FROM boct"));
        assertEquals(Collections.nCopies(2, VALUE),
            rows("SELECT SYSTEM$TYPEOF(x AND TRUE) FROM (SELECT n = 1 AS x FROM bo)"));
    }
}
