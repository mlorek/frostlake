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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How a refusal's echo names a membership test over a subquery: as the comparison the account plans,
 * {@code x = ANY(…)} for IN and {@code x != ALL(…)} for NOT IN, around the SELECT re-printed from its
 * plan — every item named, every column qualified by its relation, a FROM-less SELECT reading
 * {@code FROM (VALUES (null)) DUAL}, the clauses in the plan's order and spelling, and the subject and
 * the item met in one type. Each expected answer is the account's own.
 */
public class SubqueryMembershipEchoTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE rt (g VARCHAR(10), n NUMBER(5,0), v VARIANT, b BOOLEAN)");
        engine.execute("CREATE TABLE re (a NUMBER(10,2), i NUMBER(10,0), f FLOAT)");
        engine.execute("CREATE TABLE one (g VARCHAR(10))");
    }

    /** The answer as one line: the rows a query returns, or its refusal with each line break as |. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED:");
            while (rs.next()) {
                all.append(" ").append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** IN is = ANY and NOT IN is != ALL, whichever arity sentence echoes them. */
    @Test
    public void aMembershipTestIsTheComparisonItIsPlannedAs() {
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT 'abc' AS \"'ABC'\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT 'abc'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' != ALL(SELECT 'abc' AS \"'ABC'\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' NOT IN (SELECT 'abc'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' != ALL(SELECT RT.G AS \"G\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' NOT IN (SELECT g FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER('b' = ANY(SELECT 'abc' AS \"'ABC'\" FROM (VALUES (null)) DUAL), 1)] expected 1, got 2",
            answer("SELECT UPPER('b' IN (SELECT 'abc'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER('b' != ALL(SELECT RT.G AS \"G\" FROM RT AS RT), 1)] expected 1, got 2",
            answer("SELECT UPPER('b' NOT IN (SELECT g FROM rt), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(RT.N = ANY(SELECT RT.N AS \"N\" FROM RT AS RT), 1)] expected 1, got 2",
            answer("SELECT UPPER(n IN (SELECT n FROM rt), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX(RT.G = ANY(SELECT RT.G AS \"G\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX(g IN (SELECT g FROM rt)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX(RT.G = ANY(SELECT 'abc' AS \"'ABC'\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX(g IN (SELECT 'abc')) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT 'abc' AS \"'ABC'\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN ((SELECT 'abc')))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' != ALL(SELECT 'abc' AS \"'ABC'\" FROM (VALUES (null)) DUAL LIMIT 1 OFFSET 0))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' NOT IN (SELECT 'abc' LIMIT 1))"));
    }

    /** Each item carries its name, and each column the relation it is read from. */
    @Test
    public void theItemsAreNamedAndTheirColumnsQualified() {
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (select g from rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"X\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g AS x FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"a b\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g AS \"a b\" FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT 'abc' AS \"X\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT 'abc' AS x))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT 'abc' AS \"q\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT 'abc' AS \"q\"))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT (g) FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT rt.g FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT R.G AS \"G\" FROM RT AS R))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT r.g FROM rt r))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT R.G AS \"G\" FROM RT AS R))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT r.g FROM rt AS r))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT \"r\".G AS \"G\" FROM RT AS r))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt AS \"r\"))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT R.G AS \"G\" FROM RT AS R))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt AS \"R\"))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM \"RT\"))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM TEST_SCHEMA.RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM test_schema.rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT 'abc' AS \"'ABC'\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT 'abc' FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT 'abc' AS \"'ABC'\" FROM RT AS R))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT 'abc' FROM rt r))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT ONE.G AS \"G\" FROM ONE AS ONE))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT * FROM one))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT O.G AS \"G\" FROM ONE AS O))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM one AS o))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT O.G AS \"G\" FROM ONE AS O))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT o.g FROM one o))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT DISTINCT RT.G AS \"G\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT DISTINCT g FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT DISTINCT 'abc' AS \"'ABC'\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT DISTINCT 'abc'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT ALL g FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT UPPER(RT.G) AS \"UPPER(G)\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT UPPER(g) FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT MAX(RT.G) AS \"MAX(G)\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT MAX(g) FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT 'a' || RT.G AS \"'A' || G\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT 'a' || g FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT 'ab' || 'c' AS \"'AB' || 'C'\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT 'ab' || 'c'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT UPPER('abc') AS \"UPPER('ABC')\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT UPPER('abc')))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT 'a''b' AS \"'A''B'\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT 'a''b'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT CAST(RT.N AS VARCHAR(134217728)) AS \"TO_VARCHAR(N)\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT TO_VARCHAR(n) FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT SUBSTR(RT.G, 1, 2) AS \"LEFT(G, 2)\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT LEFT(g, 2) FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT IFNULL(RT.G, 'x') AS \"COALESCE(G, 'X')\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT COALESCE(g, 'x') FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT CAST(RT.G AS VARCHAR(5)) AS \"G::VARCHAR(5)\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g::VARCHAR(5) FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT SYSTEM$NULL_TO_TEXT(null) AS \"NULL\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT NULL))"));
    }

    /** A condition is printed as the plan holds it: a bare boolean is cast, a chain of ANDs flattened. */
    @Test
    public void theWhereClauseIsPrintedAsThePlanHoldsIt() {
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE RT.G = 'x'))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE g = 'x'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE RT.N > 1))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE n > 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE RT.G <> 'x'))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE g <> 'x'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE CAST(RT.B AS BOOLEAN)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE b))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE CAST(NOT(RT.B) AS BOOLEAN)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE NOT b))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE CAST(RT.B AND RT.B AS BOOLEAN)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE b AND b))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE (CAST(RT.B AS BOOLEAN)) AND (RT.N > 1)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE b AND n > 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE (CAST(RT.B AS BOOLEAN)) OR (RT.N > 1)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE b OR n > 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE (RT.B = TRUE) AND (CAST(RT.B AS BOOLEAN))))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE b = TRUE AND b))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE NOT(RT.N > 1)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE NOT n > 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE (RT.G = 'x') OR (RT.N > 1)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE g = 'x' OR n > 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE (RT.N > 1) AND (RT.N < 5) AND (RT.G = 'x')))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE n > 1 AND n < 5 AND g = 'x'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE (RT.G = 'x') AND ((RT.N > 1) OR (RT.N < 0))))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE g = 'x' AND (n > 1 OR n < 0)))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE ((RT.G = 'x') AND (RT.N > 1)) OR (RT.N < 0)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE (g = 'x' AND n > 1) OR n < 0))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE RT.G IS NULL))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE g IS NULL))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE RT.B IS NULL))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE b IS NULL))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE RT.G LIKE 'a%'))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE g LIKE 'a%'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE RT.G IN ('a', 'b')))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE g IN ('a', 'b')))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE RT.N IN (1, 2)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE n IN (1, 2)))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE (RT.N >= 1) AND (RT.N <= 2)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE n BETWEEN 1 AND 2))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE (CAST(RT.N AS NUMBER(6,1))) = 1.5))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE n = 1.5))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE (CAST(RT.N AS NUMBER(6,1))) > 1.5))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE n > 1.5))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE FALSE))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE FALSE))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT 'abc' AS \"'ABC'\" FROM (VALUES (null)) DUAL WHERE TRUE))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT 'abc' WHERE TRUE))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE RT.G = 'it''s'))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE g = 'it''s'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE RT.G = 'a\\\\b'))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE g = 'a\\\\b'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT R.G AS \"G\" FROM RT AS R WHERE R.N > 1))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt r WHERE r.n > 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT 'x' AS \"'X'\" FROM ONE AS ONE WHERE ONE.G = 'b'))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT 'x' FROM one WHERE g = 'b'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE RT.G = ANY(SELECT ONE.G AS \"G\" FROM ONE AS ONE)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE g IN (SELECT g FROM one)))"));
    }

    /** A comma is an inner join, RIGHT the LEFT join of the pair swapped, and ON bracketed. */
    @Test
    public void joinsArePrintedAsTheJoinTheyArePlannedAs() {
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT INNER JOIN RE AS RE))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt, re))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT R.G AS \"G\" FROM RT AS R INNER JOIN RE AS E WHERE R.N = E.I))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT r.g FROM rt r, re e WHERE r.n = e.i))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT INNER JOIN RE AS RE ON (RT.N = RE.I)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt JOIN re ON rt.n = re.i))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT INNER JOIN RE AS RE ON (RT.N = RE.I)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt INNER JOIN re ON rt.n = re.i))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT INNER JOIN RE AS RE))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt CROSS JOIN re))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT LEFT OUTER JOIN RE AS RE ON (RT.N = RE.I)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt LEFT JOIN re ON rt.n = re.i))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT LEFT OUTER JOIN RE AS RE ON ((RT.N = RE.I) AND (RE.A > (CAST(1 AS NUMBER(10,2)))))))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt LEFT OUTER JOIN re ON rt.n = re.i AND re.a > 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RE AS RE LEFT OUTER JOIN RT AS RT ON (RT.N = RE.I)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt RIGHT JOIN re ON rt.n = re.i))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT FULL OUTER JOIN RE AS RE ON (RT.N = RE.I)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt FULL JOIN re ON rt.n = re.i))"));
    }

    /** GROUP BY names the input column, ORDER BY the output one; an ordinal counts to an output column. */
    @Test
    public void groupingAndOrderingKeysArePrintedAsThePlanReadsThem() {
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT GROUP BY RT.G))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt GROUP BY g))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT GROUP BY RT.G, RT.N))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt GROUP BY g, n))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT GROUP BY G))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt GROUP BY 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT GROUP BY RT.G, G))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt GROUP BY g, 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT GROUP BY G))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt GROUP BY ALL))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"X\" FROM RT AS RT GROUP BY X))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g AS x FROM rt GROUP BY x))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT UPPER(RT.G) AS \"UPPER(G)\" FROM RT AS RT GROUP BY UPPER(RT.G)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT UPPER(g) FROM rt GROUP BY UPPER(g)))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT MIN(RT.G) AS \"MIN(G)\" FROM RT AS RT GROUP BY RT.N))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT MIN(g) FROM rt GROUP BY n))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT  ORDER BY G ASC NULLS LAST))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt ORDER BY g))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT  ORDER BY G ASC NULLS LAST))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt ORDER BY g ASC))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT  ORDER BY G DESC NULLS FIRST))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt ORDER BY g DESC))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT  ORDER BY G ASC NULLS FIRST))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt ORDER BY g NULLS FIRST))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT  ORDER BY G ASC NULLS LAST))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt ORDER BY g NULLS LAST))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT  ORDER BY G DESC NULLS LAST))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt ORDER BY g DESC NULLS LAST))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT  ORDER BY RT.N ASC NULLS LAST))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt ORDER BY n))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT  ORDER BY RT.G ASC NULLS LAST))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt ORDER BY rt.g))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT  ORDER BY G ASC NULLS LAST))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt ORDER BY 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT  ORDER BY G DESC NULLS FIRST))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt ORDER BY 1 DESC))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT  ORDER BY G ASC NULLS LAST, RT.N ASC NULLS LAST))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt ORDER BY g, n))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT  ORDER BY RT.N DESC NULLS FIRST, G ASC NULLS LAST))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt ORDER BY n DESC, g))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"X\" FROM RT AS RT  ORDER BY X ASC NULLS LAST))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g AS x FROM rt ORDER BY x))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT UPPER(RT.G) AS \"U\" FROM RT AS RT  ORDER BY U ASC NULLS LAST))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT UPPER(g) AS u FROM rt ORDER BY u))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT  ORDER BY UPPER(G) ASC NULLS LAST))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt ORDER BY UPPER(g)))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT  ORDER BY LOWER(G) DESC NULLS FIRST))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt ORDER BY LOWER(g) DESC))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT DISTINCT RT.G AS \"G\" FROM RT AS RT  ORDER BY G ASC NULLS LAST))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT DISTINCT g FROM rt ORDER BY g))"));
    }

    /** The clauses come in the plan's order, HAVING after ORDER BY, and every limit with its offset. */
    @Test
    public void theClausesComeInThePlansOrder() {
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT LIMIT 1 OFFSET 0))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt LIMIT 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT LIMIT 5 OFFSET 0))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt LIMIT 5))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT LIMIT 1 OFFSET 2))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt LIMIT 1 OFFSET 2))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT LIMIT 2 OFFSET 1))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt LIMIT 2 OFFSET 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT LIMIT 1 OFFSET 0))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt LIMIT 1 OFFSET 0))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT 'abc' AS \"'ABC'\" FROM (VALUES (null)) DUAL LIMIT 1 OFFSET 0))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT 'abc' LIMIT 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT 'abc' AS \"'ABC'\" FROM RT AS RT LIMIT 0 OFFSET 0))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT 'abc' FROM rt LIMIT 0))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT LIMIT 1 OFFSET 0))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT TOP 1 g FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT LIMIT 1 OFFSET 0))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt FETCH FIRST 1 ROWS ONLY))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT LIMIT 2 OFFSET 1))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt OFFSET 1 ROWS FETCH NEXT 2 ROWS ONLY))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT  ORDER BY G ASC NULLS LAST LIMIT 1 OFFSET 0))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt ORDER BY g LIMIT 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE RT.G = 'x'  ORDER BY G ASC NULLS LAST LIMIT 2 OFFSET 0))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE g = 'x' ORDER BY g LIMIT 2))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE RT.N > 1 GROUP BY RT.G))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE n > 1 GROUP BY g))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT GROUP BY RT.G LIMIT 1 OFFSET 0))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt GROUP BY g LIMIT 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT GROUP BY RT.G  HAVING COUNT(*) > 1))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt GROUP BY g HAVING COUNT(*) > 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT MAX(RT.G) AS \"MAX(G)\" FROM RT AS RT  HAVING (MAX(RT.G)) > 'a'))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT MAX(g) FROM rt HAVING MAX(g) > 'a'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT MAX(RT.G) AS \"MAX(G)\" FROM RT AS RT  ORDER BY MAX(G) ASC NULLS LAST  HAVING COUNT(*) > 1))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT MAX(g) FROM rt HAVING COUNT(*) > 1 ORDER BY 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT GROUP BY RT.G  ORDER BY G ASC NULLS LAST  HAVING RT.G = 'x'))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt GROUP BY g HAVING g = 'x' ORDER BY g))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT GROUP BY RT.G  HAVING RT.G = 'x' LIMIT 1 OFFSET 0))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt GROUP BY g HAVING g = 'x' LIMIT 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT GROUP BY RT.G  ORDER BY G ASC NULLS LAST  HAVING RT.G = 'x' LIMIT 1 OFFSET 0))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt GROUP BY g HAVING g = 'x' ORDER BY g LIMIT 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE RT.N > 1 GROUP BY RT.G  HAVING RT.G = 'x'))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE n > 1 GROUP BY g HAVING g = 'x'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT QUALIFY ROW_NUMBER() OVER (ORDER BY RT.G ASC NULLS LAST) = 1))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt QUALIFY ROW_NUMBER() OVER (ORDER BY g) = 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE RT.N = 1 QUALIFY TRUE))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE n = 1 QUALIFY TRUE))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT  ORDER BY G ASC NULLS LAST QUALIFY ROW_NUMBER() OVER (ORDER BY RT.G ASC NULLS LAST) = 1))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt QUALIFY ROW_NUMBER() OVER (ORDER BY g) = 1 ORDER BY g))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT QUALIFY ROW_NUMBER() OVER (ORDER BY RT.G ASC NULLS LAST) = 1 LIMIT 1 OFFSET 0))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt QUALIFY ROW_NUMBER() OVER (ORDER BY g) = 1 LIMIT 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT GROUP BY RT.G  HAVING RT.G = 'x' QUALIFY ROW_NUMBER() OVER (ORDER BY RT.G ASC NULLS LAST) = 1))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt GROUP BY g HAVING g = 'x' QUALIFY ROW_NUMBER() OVER (ORDER BY g) = 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE RT.N > 1  ORDER BY G ASC NULLS LAST QUALIFY ROW_NUMBER() OVER (ORDER BY RT.G ASC NULLS LAST) = 1 LIMIT 1 OFFSET 0))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt WHERE n > 1 QUALIFY ROW_NUMBER() OVER (ORDER BY g) = 1 ORDER BY g LIMIT 1))"));
    }

    /** The subject and the item meet in one type: a typed null, or the lower scale rescaled. */
    @Test
    public void theSubjectAndTheItemMeetInOneType() {
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX(1 = ANY(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX(1 IN (SELECT 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX(1 = ANY(SELECT RT.N AS \"N\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX(1 IN (SELECT n FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX(RT.N = ANY(SELECT RT.N AS \"N\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX(n IN (SELECT n FROM rt)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX(RT.B = ANY(SELECT RT.B AS \"B\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX(b IN (SELECT b FROM rt)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((CAST(1 AS NUMBER(2,1))) = ANY(SELECT 1.5 AS \"1.5\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX(1 IN (SELECT 1.5))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((CAST(RT.N AS NUMBER(6,1))) = ANY(SELECT 1.5 AS \"1.5\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX(n IN (SELECT 1.5)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX(1.5 = ANY(SELECT CAST(2 AS NUMBER(2,1)) AS \"2\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX(1.5 IN (SELECT 2))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX(1.5 = ANY(SELECT CAST(RT.N AS NUMBER(6,1)) AS \"N\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX(1.5 IN (SELECT n FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX(1.5 = ANY(SELECT CAST(RT.N AS NUMBER(6,1)) AS \"N\" FROM RT AS RT WHERE RT.N > 1))], expected 2, got 1",
            answer("SELECT CHARINDEX(1.5 IN (SELECT n FROM rt WHERE n > 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX(1 = ANY(SELECT SYSTEM$NULL_TO_FIXED(null) AS \"NULL\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX(1 IN (SELECT NULL))"));
    }

    /** An invalid-type sentence re-prints the same SELECT in its own vocabulary. */
    @Test
    public void anInvalidTypeSentenceSpeaksItsOwnVocabulary() {
        assertEquals("SQL compilation error:|invalid type [TO_DATE('b' = ANY(SELECT 'abc' AS \"'ABC'\" FROM (VALUES (NULL)) DUAL))] for parameter 'TO_DATE'",
            answer("SELECT TO_DATE('b' IN (SELECT 'abc'))"));
        assertEquals("SQL compilation error:|invalid type [CAST('b' = ANY(SELECT 'abc' AS \"'ABC'\" FROM (VALUES (NULL)) DUAL) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(('b' IN (SELECT 'abc')) AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST('b' != ALL(SELECT RT.G AS \"G\" FROM RT AS RT) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(('b' NOT IN (SELECT g FROM rt)) AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE RT.G = 'x') AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(('b' IN (SELECT g FROM rt WHERE g = 'x')) AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE BOOLEAN_TO_ROWINDEX(RT.B)) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(('b' IN (SELECT g FROM rt WHERE b)) AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT WHERE (FIXED_TO_FIXED(RT.N AS NUMBER(6,1)[UNKNOWN])) > 1.5) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(('b' IN (SELECT g FROM rt WHERE n > 1.5)) AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT  ORDER BY G ASC NULLS LAST LIMIT 1 OFFSET 0) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(('b' IN (SELECT g FROM rt ORDER BY g LIMIT 1)) AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST('b' = ANY(SELECT SUBSTR(RT.G, 1, 2) AS \"LEFT(G, 2)\" FROM RT AS RT) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(('b' IN (SELECT LEFT(g, 2) FROM rt)) AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST('b' = ANY(SELECT SYSTEM$NULL_TO_TEXT(NULL) AS \"NULL\" FROM (VALUES (NULL)) DUAL) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(('b' IN (SELECT NULL)) AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST('b' = ANY(SELECT 'it''s' AS \"'IT''S'\" FROM (VALUES (NULL)) DUAL) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(('b' IN (SELECT 'it''s')) AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(1 AS NUMBER(2,1)[UNKNOWN])) = ANY(SELECT 1.5 AS \"1.5\" FROM (VALUES (NULL)) DUAL) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST((1 IN (SELECT 1.5)) AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST(1.5 = ANY(SELECT FIXED_TO_FIXED(RT.N AS NUMBER(6,1)[UNKNOWN]) AS \"N\" FROM RT AS RT) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST((1.5 IN (SELECT n FROM rt)) AS DATE)"));
    }
}
