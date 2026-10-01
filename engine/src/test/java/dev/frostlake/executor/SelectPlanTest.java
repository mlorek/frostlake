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

package dev.frostlake.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every SELECT operand is planned whole and then run as one operator pipeline, end to end: the plan names
 * the source relation and each stage in order, a LATERAL item and a PIVOT among them, their shapes settled
 * while planning. The source is a stage too — a table's scan, a derived table's, a view's or a stream's own
 * read — whose rows flow only when the pipeline runs. Only a PIVOT over {@code IN (ANY)}, a FROM-less
 * projection and a source with no deferred read yet read while planning, and read with a trailing {@code *}.
 */
public class SelectPlanTest extends BaseDatabaseTest {

    private static final String EMBEDDED_ONLY = "the plan is the embedded engine's own";

    @BeforeEach
    public void embeddedOnly() {
        assumeFalse(isLiveSnowflake(), EMBEDDED_ONLY);
    }

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT, b VARCHAR, j VARIANT)");
        engine.execute("INSERT INTO t SELECT 1, 'x', PARSE_JSON('[1,2]')");
        engine.execute("INSERT INTO t SELECT 2, 'x', PARSE_JSON('[3]')");
        engine.execute("INSERT INTO t SELECT 3, 'y', PARSE_JSON('[]')");
        engine.execute("CREATE TABLE u (a INT, c INT)");
        engine.execute("INSERT INTO u VALUES (1, 10), (2, -1)");
    }

    /** The plan's shape: its source and stage names, without each stage's own detail. */
    /** The plan without the nested plans its stages hold in braces, which nest where a nested stage holds one. */
    private static String withoutNestedPlans(final String plan) {
        final StringBuilder outer = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < plan.length(); i++) {
            final char c = plan.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            } else if (depth == 0) {
                outer.append(c);
            }
        }
        return outer.toString();
    }

    private String shape(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final String plan = engine.getExecutor().describeLastSelectPlan();
        // A nested plan reads inside braces after the stage that runs it; the shape is the outer stages'.
        final String inner = withoutNestedPlans(plan.substring("Plan[".length(), plan.length() - 1));
        final StringBuilder names = new StringBuilder();
        for (final String stage : inner.split(" -> ")) {
            final int detail = stage.indexOf('[');
            names.append(names.length() > 0 ? " -> " : "")
                .append(detail < 0 ? stage : stage.substring(0, detail))
                .append(stage.endsWith("*") ? "*" : "");
        }
        return names + " = " + result.getRowCount() + " rows";
    }

    @Test
    public void theStagesRunInOrderAsOnePipeline() {
        assertEquals("SCAN -> WHERE -> PROJECT -> ORDER BY -> LIMIT = 2 rows",
            shape("SELECT a FROM t WHERE a > 0 ORDER BY a LIMIT 2"));
        assertEquals("SCAN -> ORDER BY -> PROJECT = 3 rows", shape("SELECT b FROM t ORDER BY a"));
        assertEquals("SCAN -> GROUP BY -> HAVING -> ORDER BY = 1 rows",
            shape("SELECT b, COUNT(*) FROM t GROUP BY b HAVING COUNT(*) > 1 ORDER BY b"));
        assertEquals("SCAN -> GROUP BY = 1 rows", shape("SELECT SUM(a) FROM t"));
        assertEquals("SCAN -> INNER JOIN -> WHERE -> PROJECT = 1 rows",
            shape("SELECT t.a FROM t JOIN u ON t.a = u.a WHERE u.c > 0"));
        assertEquals("SCAN -> WINDOW = 1 rows",
            shape("SELECT a, ROW_NUMBER() OVER (ORDER BY a) AS rn FROM t QUALIFY rn = 1"));
        assertEquals("SCAN -> PROJECT -> DISTINCT = 2 rows", shape("SELECT DISTINCT b FROM t"));
        // A set operation plans each arm, then combines their results in stages of its own.
        assertEquals("SOURCE -> UNION ALL = 6 rows", shape("SELECT a FROM t UNION ALL SELECT a FROM t"));
    }

    @Test
    public void aStreamableSelectFiltersAndLimitsInOneStage() {
        assertEquals("SCAN -> STREAM -> PROJECT = 1 rows", shape("SELECT a FROM t WHERE a > 1 LIMIT 1"));
    }

    @Test
    public void aLateralItemAndAPivotArePlannedStagesToo() {
        assertEquals("SCAN -> LATERAL -> PROJECT = 3 rows",
            shape("SELECT f.value FROM t, LATERAL FLATTEN(input => j) f"));
        assertEquals("SCAN -> INNER JOIN -> LATERAL -> PROJECT = 3 rows",
            shape("SELECT f.value FROM t JOIN u ON t.a = u.a, LATERAL FLATTEN(input => t.j) f"));
        // An empty left input still answers the right side's shape: nothing runs to discover it.
        assertEquals("SCAN -> LATERAL -> WHERE -> PROJECT = 0 rows",
            shape("SELECT f.value FROM t, LATERAL FLATTEN(input => j) f WHERE a > 100"));
        assertEquals("SUBQUERY -> PIVOT = 1 rows",
            shape("SELECT * FROM (SELECT a, b FROM t) PIVOT (SUM(a) FOR b IN ('x', 'y'))"));
        assertEquals("SUBQUERY -> PIVOT -> PROJECT = 1 rows",
            shape("SELECT x_total FROM (SELECT a, b FROM t) PIVOT (SUM(a) FOR b IN ('x', 'y'))"
                + " AS p (x_total, y_total)"));
        assertEquals("SCAN -> UNPIVOT -> WHERE -> PROJECT = 3 rows",
            shape("SELECT col, val FROM u UNPIVOT (val FOR col IN (a, c)) WHERE val > 0"));
    }

    @Test
    public void onlyADynamicPivotReadsValuesWhilePlanning() {
        assertEquals("SUBQUERY -> PIVOT* = 1 rows",
            shape("SELECT * FROM (SELECT a, b FROM t) PIVOT (SUM(a) FOR b IN (ANY))"));
    }

    @Test
    public void aSetOperationCombinesItsArmsInStages() {
        // INTERSECT binds tighter: the second UNION arm is the term `arm 2 INTERSECT arm 3`.
        assertEquals("SOURCE -> UNION -> ORDER BY -> LIMIT = 2 rows",
            shape("SELECT a FROM t UNION SELECT a FROM u INTERSECT SELECT a FROM t ORDER BY a LIMIT 2"));
        assertEquals("SOURCE -> EXCEPT = 1 rows", shape("SELECT a FROM t EXCEPT SELECT a FROM u"));
        assertEquals("SOURCE -> INTERSECT -> UNION ALL = 5 rows",
            shape("SELECT a FROM t INTERSECT SELECT a FROM u UNION ALL SELECT a FROM t"));
    }

    @Test
    public void aFromlessSelectRunsOverDual() {
        assertEquals("DUAL -> PROJECT* -> WHERE -> LIMIT = 1 rows",
            shape("SELECT 1 AS x, x + 1 AS y WHERE y = 2 LIMIT 1"));
        assertEquals("DUAL -> PROJECT* -> DISTINCT = 1 rows", shape("SELECT DISTINCT 'a', 'b'"));
        assertEquals("DUAL -> PROJECT* -> WHERE = 0 rows", shape("SELECT 1 WHERE 1 = 0"));
        assertEquals("DUAL -> PROJECT* -> LIMIT = 0 rows", shape("SELECT 1 LIMIT 0"));
    }

    @Test
    public void aJoinGroupIsAPlanTheOuterJoinReads() {
        assertEquals("SCAN -> INNER JOIN -> PROJECT = 2 rows",
            shape("SELECT t.a FROM t JOIN (u JOIN t t2 ON u.a = t2.a) ON t.a = u.a"));
        // The group's own pipeline reads inside the join stage that runs it.
        final String plan = engine.getExecutor().describeLastSelectPlan();
        assertTrue(plan.contains("{SCAN[U] -> INNER JOIN[U x T ON u.a = t2.a]{SCAN[T]}}"), plan);
    }

    @Test
    public void aTableScanIsTheFirstStageAndIsNotReadWhilePlanning() {
        assertEquals("SCAN -> PROJECT = 3 rows", shape("SELECT a FROM t"));
        final String plan = engine.getExecutor().describeLastSelectPlan();
        assertEquals("Plan[SCAN[T] -> PROJECT[a]]", plan);
        // A join's right side is read inside the join stage that runs it.
        assertEquals("SCAN -> INNER JOIN -> PROJECT = 2 rows", shape("SELECT t.a FROM t JOIN u ON t.a = u.a"));
        assertTrue(engine.getExecutor().describeLastSelectPlan().contains("INNER JOIN[T x U ON t.a = u.a]{SCAN[U]}"),
            engine.getExecutor().describeLastSelectPlan());
    }

    @Test
    public void aDerivedTableAViewAndACteAreSourceStages() {
        // A derived table's body is a plan of its own, run by the source stage.
        assertEquals("SUBQUERY -> WHERE -> PROJECT = 1 rows",
            shape("SELECT a FROM (SELECT a, b FROM t WHERE b = 'x') d WHERE a > 1"));
        assertTrue(engine.getExecutor().describeLastSelectPlan()
            .startsWith("Plan[SUBQUERY[D]{SCAN[T] -> WHERE[b = 'x', mode=SIMPLE] -> PROJECT[a, b]} -> "),
            engine.getExecutor().describeLastSelectPlan());
        // A view's body is planned where the view lives and run when the source stage runs.
        engine.execute("CREATE VIEW v AS SELECT a, b FROM t");
        assertEquals("VIEW -> WHERE -> PROJECT = 2 rows", shape("SELECT b FROM v WHERE a > 1"));
        assertTrue(engine.getExecutor().describeLastSelectPlan().startsWith("Plan[VIEW[V]{SCAN[T] -> "),
            engine.getExecutor().describeLastSelectPlan());
        // A CTE's rows are computed by the WITH clause and are in hand.
        assertEquals("CTE -> PROJECT = 3 rows", shape("WITH c AS (SELECT a FROM t) SELECT a FROM c"));
        assertEquals("VALUES -> PROJECT = 2 rows", shape("SELECT column1 FROM (VALUES (1), (2)) v"));
        assertEquals("DUAL -> PROJECT = 1 rows", shape("SELECT 1 FROM DUAL"));
    }

    @Test
    public void aStreamIsReadWhenItsSourceStageRuns() {
        engine.execute("CREATE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT 4, 'z', PARSE_JSON('[4]')");
        assertEquals("STREAM SCAN -> PROJECT = 1 rows", shape("SELECT a FROM s"));
        assertEquals("Plan[STREAM SCAN[S] -> PROJECT[a]]", engine.getExecutor().describeLastSelectPlan());
    }

    @Test
    public void aTableFunctionSourceIsTheFirstStage() {
        assertEquals("TABLE -> PROJECT = 3 rows", shape("SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => 3))"));
        assertEquals("TABLE -> WHERE -> PROJECT = 1 rows",
            shape("SELECT value FROM TABLE(FLATTEN(input => PARSE_JSON('[1, 2]'))) WHERE value = 2"));
        assertEquals("TABLE -> PROJECT = 2 rows", shape("SELECT value FROM TABLE(SPLIT_TO_TABLE('a,b', ','))"));
        final String plan = engine.getExecutor().describeLastSelectPlan();
        assertTrue(plan.startsWith("Plan[TABLE[SPLIT_TO_TABLE('a,b', ',')] -> "), plan);
    }

    /** The first cell of a query's answer, as text. */
    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    /** The plan's shape after a statement that answers a count rather than rows. */
    private String dmlShape(final String sql) {
        engine.execute(sql);
        final String plan = engine.getExecutor().describeLastSelectPlan();
        final String inner = withoutNestedPlans(plan.substring("Plan[".length(), plan.length() - 1));
        final StringBuilder names = new StringBuilder();
        for (final String stage : inner.split(" -> ")) {
            final int detail = stage.indexOf('[');
            names.append(names.length() > 0 ? " -> " : "").append(detail < 0 ? stage : stage.substring(0, detail));
        }
        return names.toString();
    }

    @Test
    public void anUpdateAndADeleteEndInTheirWriteStage() {
        assertEquals("TARGET -> WHERE -> UPDATE", dmlShape("UPDATE t SET b = 'z' WHERE a = 1"));
        assertEquals("z", cell("SELECT b FROM t WHERE a = 1"));
        assertEquals("TARGET -> UPDATE", dmlShape("UPDATE u SET c = c + 1"));
        assertEquals("TARGET -> WHERE -> DELETE", dmlShape("DELETE FROM t WHERE a = 3"));
        assertEquals(2, engine.executeQuery("SELECT a FROM t").getRowCount());
        // The FROM / USING sources are a plan of their own, read by the match stage.
        assertEquals("TARGET -> MATCH -> UPDATE", dmlShape("UPDATE t SET b = 'q' FROM u WHERE t.a = u.a AND u.c > 5"));
        assertTrue(engine.getExecutor().describeLastSelectPlan().contains("{SCAN[U]}"),
            engine.getExecutor().describeLastSelectPlan());
        assertEquals("q", cell("SELECT b FROM t WHERE a = 1"));
        assertEquals("TARGET -> MATCH -> DELETE", dmlShape("DELETE FROM t USING u WHERE t.a = u.a AND u.c < 5"));
        assertEquals(1, engine.executeQuery("SELECT a FROM t").getRowCount());
    }

    @Test
    public void aMergeRunsItsClausesAsStages() {
        assertEquals("TARGET -> MERGE MATCH -> MERGE CHECK -> WHEN MATCHED -> WHEN NOT MATCHED",
            dmlShape("MERGE INTO t USING u ON t.a = u.a"
                + " WHEN MATCHED THEN UPDATE SET b = 'm'"
                + " WHEN NOT MATCHED THEN INSERT (a, b) VALUES (u.a, 'n')"));
        assertEquals("m", cell("SELECT b FROM t WHERE a = 1"));
        assertEquals(3, engine.executeQuery("SELECT a FROM t").getRowCount());
        // The check stage stands whenever a WHEN MATCHED clause does; a DELETE-only merge has nothing to refuse.
        assertEquals("TARGET -> MERGE MATCH -> MERGE CHECK -> WHEN MATCHED -> WHEN NOT MATCHED",
            dmlShape("MERGE INTO t USING u ON t.a = u.a WHEN MATCHED THEN DELETE"));
        assertEquals(1, engine.executeQuery("SELECT a FROM t").getRowCount());
    }

    @Test
    public void aRecursiveCteRunsItsRecursionAsAStage() {
        // The recursion is a stage over the anchor; the outer select's own plan is the one recorded last.
        assertEquals("CTE -> PROJECT -> ORDER BY = 4 rows",
            shape("WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 4)"
                + " SELECT n FROM r ORDER BY n"));
        assertEquals("4", cell("WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 4)"
            + " SELECT MAX(n) FROM r"));
        assertEquals("3", cell("WITH RECURSIVE r (n) AS (SELECT 1 UNION SELECT (n % 3) + 1 FROM r)"
            + " SELECT COUNT(*) FROM r"));
    }
}
