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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.functions.HigherOrderFunctionNames;
import dev.frostlake.functions.OperatorFunctionNames;
import dev.frostlake.functions.SystemFunctionNames;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SHOW FUNCTIONS lists the built-in function library (is_builtin = 'Y') as well as user-defined
 * functions, matching Snowflake. Previously it returned only user functions, so a fresh session with
 * no user functions produced zero rows.
 *
 * <p>It then listed 396 distinct names while the engine could dispatch 533, because it enumerated the
 * registry's function/aggregate/table-function <em>values</em>: aliases reported their canonical name
 * (SUBSTR showed up as a second SUBSTRING row; ARRAYAGG, RLIKE, DAYOFMONTH never appeared), and the
 * window, higher-order, SYSTEM$ and operator families are not in those maps at all. The listing now comes
 * from the one source the dispatchers themselves consult,
 * {@code FunctionRegistry.allDispatchableNames()}. The tests below pin each family down so the two cannot
 * quietly separate again.
 */
public class ShowFunctionsTest extends BaseDatabaseTest {

    // Column ordinals of the SHOW FUNCTIONS result set.
    private static final int NAME = 1;
    private static final int IS_BUILTIN = 3;

    private Set<String> listedNames() {
        final Set<String> names = new HashSet<String>();
        for (final Row row : engine.executeQuery("SHOW FUNCTIONS").getRows()) {
            names.add(String.valueOf(row.getValue(NAME)).toUpperCase());
        }
        return names;
    }

    /** The error text the engine uses when a name resolves to nothing at all. */
    private String resolutionFailure(final String sql) {
        try {
            engine.executeQuery(sql);
            return null;
        } catch (final RuntimeException e) {
            final String message = String.valueOf(e.getMessage());
            return message.contains("Unknown function ")
                || message.contains("Unknown table function:")
                || message.contains("Unsupported window function:")
                || message.contains("Unsupported system function:") ? message : null;
        }
    }

    @Test
    public void listsBuiltinsWhenNoUserFunctionsExist() {
        final ResultSet rs = engine.executeQuery("SHOW FUNCTIONS");
        assertTrue(rs.getRowCount() > 100,
            "SHOW FUNCTIONS should list the built-in library; got " + rs.getRowCount() + " rows");

        final Set<String> names = new HashSet<String>();
        boolean sawBuiltin = false;
        for (final Row row : rs.getRows()) {
            names.add(String.valueOf(row.getValue(NAME)).toUpperCase());
            if ("Y".equals(row.getValue(IS_BUILTIN))) {
                sawBuiltin = true;
            }
        }
        assertTrue(sawBuiltin, "expected rows flagged is_builtin = 'Y'");
        assertTrue(names.contains("UPPER"), "expected built-in scalar UPPER");
        assertTrue(names.contains("ABS"), "expected built-in scalar ABS");
        assertTrue(names.contains("SUM"), "expected built-in aggregate SUM");
    }

    @Test
    public void listsUserFunctionsAlongsideBuiltins() {
        engine.execute("CREATE FUNCTION my_udf(x INTEGER) RETURNS INTEGER AS 'x + 1'");
        final ResultSet rs = engine.executeQuery("SHOW FUNCTIONS");

        boolean sawUdf = false;
        boolean sawBuiltin = false;
        for (final Row row : rs.getRows()) {
            final String name = String.valueOf(row.getValue(NAME)).toUpperCase();
            if (name.equals("MY_UDF")) {
                sawUdf = true;
                assertEquals("N", row.getValue(IS_BUILTIN), "a user function is not a built-in");
            }
            if ("Y".equals(row.getValue(IS_BUILTIN))) {
                sawBuiltin = true;
            }
        }
        assertTrue(sawUdf, "the user-defined function should be listed");
        assertTrue(sawBuiltin, "built-in functions should be listed alongside it");
    }

    /**
     * Window functions live in {@code WindowFunctionEvaluator}, never in the registry maps, so the old
     * value-based listing missed all 14. Snowflake lists every one of them (
     * {@code SHOW FUNCTIONS LIKE 'ROW_NUMBER'} and the same for RANK / DENSE_RANK / LAG / LEAD /
     * FIRST_VALUE / LAST_VALUE / NTILE / PERCENT_RANK / CUME_DIST / RATIO_TO_REPORT /
     * CONDITIONAL_TRUE_EVENT each return one row with {@code is_builtin = 'Y'}).
     */
    @Test
    public void listsWindowFunctions() {
        final Set<String> names = listedNames();
        for (final String fn : new String[]{"ROW_NUMBER", "RANK", "DENSE_RANK", "LAG", "LEAD",
            "FIRST_VALUE", "LAST_VALUE", "NTH_VALUE", "NTILE", "PERCENT_RANK", "CUME_DIST",
            "RATIO_TO_REPORT", "CONDITIONAL_TRUE_EVENT", "CONDITIONAL_CHANGE_EVENT"}) {
            assertTrue(names.contains(fn), "SHOW FUNCTIONS should list the window function " + fn);
        }
    }

    /**
     * TRANSFORM / FILTER / REDUCE are taken by {@code ExpressionEvaluatorVisitor} before the registry
     * lookup, so they were invisible to the listing too. Snowflake lists them (
     * {@code SHOW BUILTIN FUNCTIONS LIKE 'TRANSFORM'} returns
     * {@code TRANSFORM(ARRAY, FUNCTION(VARIANT)) RETURN ARRAY}).
     */
    @Test
    public void listsHigherOrderFunctions() {
        final Set<String> names = listedNames();
        for (final String fn : new String[]{"TRANSFORM", "FILTER", "REDUCE"}) {
            assertTrue(names.contains(fn), "SHOW FUNCTIONS should list the higher-order function " + fn);
        }
    }

    /**
     * A dispatch alias is a second registry KEY onto a function instance whose {@code getName()} still
     * answers the canonical name, so enumerating values listed the canonical name twice and the alias
     * never. Snowflake lists each of these as its own name (live-verified).
     */
    @Test
    public void listsDispatchAliases() {
        final Set<String> names = listedNames();
        for (final String alias : new String[]{"SUBSTR", "ARRAYAGG", "RLIKE", "DAYOFMONTH", "BIT_OR_AGG",
            "REGEXP_EXTRACT_ALL", "APPROXIMATE_COUNT_DISTINCT"}) {
            assertTrue(names.contains(alias), "SHOW FUNCTIONS should list the alias " + alias);
        }
        // …and each alias really dispatches under that name, not just under the canonical one.
        assertEquals("BCD", String.valueOf(
            engine.executeQuery("SELECT SUBSTR('ABCDEF', 2, 3) AS S").getRows().get(0).getValue(0)));
    }

    /**
     * Names Snowflake gives to operators rather than to callable functions. It lists all of them
     * (its {@code SHOW BUILTIN FUNCTIONS} returns rows named {@code ||},
     * {@code []}, {@code IS NULL}, {@code COUNT(*)}, {@code LIKE_ANY}, …), and Frostlake implements the
     * matching operator form for every name it declares — {@code OperatorFunctionNamesTest} re-checks
     * that claim entry by entry.
     */
    @Test
    public void listsOperatorImplementedNames() {
        final Set<String> names = listedNames();
        for (final String op : new String[]{"LIKE_ANY", "LIKE_ALL", "ILIKE_ANY", "||", "[]", ":",
            "IS NULL", "IS NOT NULL", "COUNT(*)", "BETWEEN", "CASE", "REGEXP"}) {
            assertTrue(names.contains(op), "SHOW FUNCTIONS should list the operator-implemented " + op);
        }
        // DIV is the counter-example: Snowflake DOES list it, but it cannot invoke it either
        // ("Unsupported feature 'DIV'"), and Frostlake has no DIV in any form — `10 DIV 3` is a syntax
        // error — so a listing that mirrors what THIS engine dispatches must leave it out.
        Assumptions.assumeFalse(isLiveSnowflake(), "a real account lists DIV even though it cannot run it");
        assertTrue(!names.contains("DIV"), "DIV is not dispatchable here, so it must not be listed");
    }

    /**
     * SYSTEM$ built-ins are evaluated by the SYSTEM$ evaluators, not the registry. Snowflake lists them
     * under SHOW FUNCTIONS rather than SHOW PROCEDURES (
     * {@code SHOW BUILTIN FUNCTIONS LIKE 'SYSTEM$WAIT'} returns one row while
     * {@code SHOW PROCEDURES LIKE 'SYSTEM$WAIT'} returns none).
     */
    @Test
    public void listsSystemFunctions() {
        final Set<String> names = listedNames();
        for (final String fn : new String[]{"SYSTEM$WAIT", "SYSTEM$SET_RETURN_VALUE"}) {
            assertTrue(names.contains(fn), "SHOW FUNCTIONS should list " + fn);
        }
        // SYSTEM$TYPEOF and SYSTEM$STREAM_HAS_DATA both work on a real account yet are absent from the
        // 156 SYSTEM$ names its SHOW BUILTIN FUNCTIONS returns, so this half only holds embedded: the
        // listing describes what Frostlake dispatches, and both of these it does.
        Assumptions.assumeFalse(isLiveSnowflake(), "a real account omits these from its own listing");
        assertTrue(names.contains("SYSTEM$TYPEOF"), "SHOW FUNCTIONS should list SYSTEM$TYPEOF");
        assertTrue(names.contains("SYSTEM$STREAM_HAS_DATA"),
            "SHOW FUNCTIONS should list SYSTEM$STREAM_HAS_DATA");
    }

    /**
     * The listing IS the dispatch table: it is built from
     * {@code FunctionRegistry.allDispatchableNames()}, the same maps and name sets
     * {@code getFunction} / {@code getAggregateFunction} / {@code getTableFunction} /
     * {@code WindowFunctionEvaluator} / {@code SystemFunctionEvaluator} resolve against.
     */
    @Test
    public void listedBuiltinsAreExactlyTheEngineSDispatchableNames() {
        Assumptions.assumeFalse(isLiveSnowflake(), "compares against this engine's own registry");
        final SortedSet<String> dispatchable = engine.getFunctionRegistry().allDispatchableNames();
        final Set<String> listed = listedNames();
        assertEquals(dispatchable.size(), listed.size(),
            "SHOW FUNCTIONS should list one row per dispatchable name");
        for (final String name : dispatchable) {
            assertTrue(listed.contains(name), "dispatchable but unlisted: " + name);
        }
    }

    /**
     * Every listed name resolves when called — no row advertises a function the engine would answer
     * "Unknown function" for. Argument-count and argument-type errors are fine and expected: they prove
     * the name resolved. Three families are excluded and covered by the tests above instead: operator
     * names are not callable by name (that is what makes them operators, and Snowflake cannot call
     * {@code LIKE_ANY('abc', 'a%')} either), the higher-order three need a lambda argument the
     * zero-argument probe cannot supply, and several SYSTEM$ names have session-wide side effects.
     */
    @Test
    public void everyListedNameResolvesWhenCalled() {
        Assumptions.assumeFalse(isLiveSnowflake(), "runs ~500 statements against engine internals");
        engine.execute("CREATE TABLE probe_rows (a INTEGER)");
        engine.execute("INSERT INTO probe_rows VALUES (1)");

        final StringBuilder unresolved = new StringBuilder();
        for (final String name : listedNames()) {
            if (OperatorFunctionNames.contains(name) || HigherOrderFunctionNames.contains(name)
                    || SystemFunctionNames.contains(name)) {
                continue;
            }
            if (resolutionFailure("SELECT " + name + "()") == null) {
                continue;
            }
            if (resolutionFailure("SELECT " + name + "() OVER (ORDER BY a) FROM probe_rows") == null) {
                continue;
            }
            final String asTableFunction = resolutionFailure("SELECT * FROM TABLE(" + name + "())");
            if (asTableFunction != null) {
                unresolved.append(name).append(" -> ").append(asTableFunction).append('\n');
            }
        }
        assertEquals("", unresolved.toString(), "SHOW FUNCTIONS listed names the engine cannot resolve:\n"
            + unresolved);
    }

    /**
     * The {@code description} of a user routine is its COMMENT, or a fixed placeholder when it has none.
     *
     * <p>Live-verified on a real account: a UDF created without a comment lists
     * {@code user-defined function} and a stored procedure without one {@code user-defined procedure},
     * while creating either {@code COMMENT = '…'} puts that comment in the same cell. The column is never
     * null for a user routine — Frostlake used to pass the raw (null) comment straight through.
     */
    @Test
    public void descriptionDefaultsToThePlaceholderAndOtherwiseCarriesTheComment() {
        engine.execute("CREATE FUNCTION d_plain(x INTEGER) RETURNS INTEGER AS 'x + 1'");
        engine.execute("CREATE FUNCTION d_commented(x INTEGER) RETURNS INTEGER "
            + "COMMENT = 'my fn comment' AS 'x + 2'");
        engine.execute("CREATE PROCEDURE d_proc() RETURNS VARCHAR LANGUAGE SQL AS BEGIN RETURN 'a'; END");

        assertEquals("user-defined function", description("SHOW USER FUNCTIONS LIKE 'D_PLAIN'"));
        assertEquals("my fn comment", description("SHOW USER FUNCTIONS LIKE 'D_COMMENTED'"));
        assertEquals("user-defined procedure", description("SHOW PROCEDURES LIKE 'D_PROC'"));
    }

    private String description(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        return String.valueOf(rs.getRows().get(0).getValue(rs.getColumnIndex("description")));
    }

    /** The three families with no registry entry still dispatch under the names the listing advertises. */
    @Test
    public void listedNamesFromTheNonRegistryFamiliesDispatch() {
        engine.execute("CREATE TABLE dispatch_rows (a INTEGER)");
        engine.execute("INSERT INTO dispatch_rows VALUES (1), (2)");

        assertEquals(2, engine.executeQuery(
            "SELECT ROW_NUMBER() OVER (ORDER BY a) AS R FROM dispatch_rows").getRowCount());
        assertEquals("4", String.valueOf(engine.executeQuery(
            "SELECT TRANSFORM(ARRAY_CONSTRUCT(1, 2), x -> x * 2)[1]::INTEGER AS T")
            .getRows().get(0).getValue(0)));
        assertEquals("true", String.valueOf(engine.executeQuery(
            "SELECT 'abc' LIKE ANY ('a%', 'z%') AS L").getRows().get(0).getValue(0)).toLowerCase());
    }
}
