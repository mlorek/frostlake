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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A refusal that COMPILES is raised whether or not the query has any rows to evaluate. Every table
 * here is EMPTY on purpose: a statement Snowflake refuses is refused over no rows just as it is over
 * one, because the check happens while the statement is compiled and never looks at the data.
 *
 * <p>The line is drawn between name/arity/argument-TYPE errors, which are compile-time, and value
 * COERCION failures — division by zero, an unparseable date, a non-numeric string reaching a numeric
 * function — which are genuinely runtime and are accepted over an empty table on both sides.
 */
public class CompileTimeRefusalTest extends BaseDatabaseTest {

    /** The operand pairs {@code +} and {@code -} accept; every other temporal pair is refused. */
    private static final String[] LEGAL = {
        "d - d", "d + n", "d - n", "n + d", "ts - ts", "n + n", "n - n"};
    private static final String[] OPERANDS = {"d", "ts", "t", "n"};

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ct_empty (k NUMBER, s VARCHAR(10), b BINARY, "
            + "d DATE, ts TIMESTAMP_NTZ, t TIME, n NUMBER)");
    }

    private void refuses(final String sql, final String fragment) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(e.getMessage().contains(fragment),
            "expected [" + fragment + "] from: " + sql + "\nbut got: " + e.getMessage());
    }

    private void accepts(final String sql) {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
    }

    /** A name that resolves to nothing is refused over a table with no rows. */
    @Test
    public void anUnknownFunctionIsRefusedOverAnEmptyTable() {
        refuses("SELECT no_such_fn(k) AS x FROM ct_empty", "Unknown function NO_SUCH_FN.");
    }

    /**
     * And therefore a view or CTAS over that body is refused too. This is the case the body-validation
     * rule could not reach while the refusal came from the evaluator: nothing was evaluated, so nothing
     * was raised, so the object was created.
     */
    @Test
    public void aViewOrCtasOverAnUnknownFunctionIsRefused() {
        for (final String ddl : new String[]{
            "CREATE VIEW ct_v AS SELECT no_such_fn(k) AS x FROM ct_empty",
            "CREATE TABLE ct_c AS SELECT no_such_fn(k) AS x FROM ct_empty"}) {
            final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.execute(ddl);
                }
            });
            assertTrue(e.getMessage().contains("Unknown function NO_SUCH_FN."),
                ddl + " gave: " + e.getMessage());
        }
    }

    /**
     * The whole temporal operand matrix, over no rows. Of the thirty-two combinations only seven are
     * legal, and
     * the asymmetry is the point: {@code NUMBER + DATE} is accepted while {@code NUMBER - DATE} is
     * refused, and a TIMESTAMP takes no numeric operand on either side.
     */
    @Test
    public void everyRefusedTemporalPairIsRefusedOverAnEmptyTable() {
        for (final String left : OPERANDS) {
            for (final String right : OPERANDS) {
                for (final String op : new String[]{"+", "-"}) {
                    final String pair = left + " " + op + " " + right;
                    final String sql = "SELECT " + pair + " AS x FROM ct_empty";
                    if (isLegal(pair)) {
                        accepts(sql);
                    } else {
                        refuses(sql, "Invalid argument types for function '" + op + "'");
                    }
                }
            }
        }
    }

    private boolean isLegal(final String pair) {
        for (final String legal : LEGAL) {
            if (legal.equals(pair)) {
                return true;
            }
        }
        return false;
    }

    /** Mixing BINARY with a non-BINARY in a concatenation is a compile-time refusal too. */
    @Test
    public void binaryConcatenationIsRefusedOverAnEmptyTable() {
        refuses("SELECT b || s AS x FROM ct_empty", "Invalid argument types for function '||'");
    }

    /**
     * The other side of the line: a value that cannot be coerced is a RUNTIME failure, so over an
     * empty table there is nothing to fail and the statement is accepted.
     */
    @Test
    public void coercionFailuresStayRuntimeAndAreAcceptedOverAnEmptyTable() {
        accepts("SELECT k / 0 AS x FROM ct_empty");
        accepts("SELECT CAST(s AS DATE) AS x FROM ct_empty");
        accepts("SELECT ABS(s) AS x FROM ct_empty");
    }

    /**
     * Names the evaluator resolves through its OWN paths rather than the registry must not be caught
     * by the compile-time check: a window function, an aggregate, the higher-order family (which take
     * a lambda), IDENTIFIER (which takes a name) and the SYSTEM$ family.
     */
    @Test
    public void speciallyResolvedNamesAreNotRefused() {
        accepts("SELECT ROW_NUMBER() OVER (ORDER BY k) AS x FROM ct_empty");
        accepts("SELECT COUNT(*) AS x FROM ct_empty");
        accepts("SELECT FILTER(ARRAY_CONSTRUCT(1,2,3), i -> i > 1) AS x");
        accepts("SELECT TRANSFORM(ARRAY_CONSTRUCT(1,2,3), i -> i + 1) AS x");
        accepts("SELECT REDUCE(ARRAY_CONSTRUCT(1,2,3), 0, (acc, i) -> acc + i) AS x");
        accepts("SELECT SYSTEM$TYPEOF(1) AS x");
    }

    /** A catalog UDF resolves by NAME, without the static channel judging its overloads. */
    @Test
    public void aCatalogUdfResolvesByName() {
        engine.execute("CREATE FUNCTION ct_udf(a NUMBER) RETURNS NUMBER AS 'a + 1'");
        accepts("SELECT ct_udf(k) AS x FROM ct_empty");
        refuses("SELECT ct_udf_missing(k) AS x FROM ct_empty", "Unknown function CT_UDF_MISSING.");
    }
    /**
     * The same rule across ALL THREE TIMESTAMP FLAVOURS, which the NTZ matrix above could not reach.
     * Every one of the nine flavour pairs SUBTRACTS legally — two instants are two instants, whatever
     * zone each carries — and every one of the nine ADDS illegally. A flavour behaves exactly as NTZ
     * does against its other neighbours too: no numeric operand on either side, and no DATE.
     *
     * <p>The refusal names the flavour it was GIVEN, with its precision — {@code TIMESTAMP_LTZ(9)},
     * not a shared "TIMESTAMP" — so a message that merely said "timestamp" would be wrong even where
     * the accept/refuse verdict is shared.
     */
    @Test
    public void everyTimestampFlavourFollowsTheSameRule() {
        engine.execute("CREATE TABLE ct_flavours (ntz TIMESTAMP_NTZ, ltz TIMESTAMP_LTZ, tz TIMESTAMP_TZ)");
        final String[] flavours = {"ntz", "ltz", "tz"};
        final String[] spelled = {"TIMESTAMP_NTZ(9)", "TIMESTAMP_LTZ(9)", "TIMESTAMP_TZ(9)"};
        for (int l = 0; l < flavours.length; l++) {
            for (int r = 0; r < flavours.length; r++) {
                accepts("SELECT " + flavours[l] + " - " + flavours[r] + " AS x FROM ct_flavours");
                refuses("SELECT " + flavours[l] + " + " + flavours[r] + " AS x FROM ct_flavours",
                    "Invalid argument types for function '+': (" + spelled[l] + ", " + spelled[r] + ")");
            }
        }
        // And against the neighbours, where a flavour must behave exactly as NTZ does.
        for (int i = 1; i < flavours.length; i++) {
            refuses("SELECT " + flavours[i] + " + 1 AS x FROM ct_flavours",
                "Invalid argument types for function '+': (" + spelled[i] + ", NUMBER(1,0))");
            refuses("SELECT " + flavours[i] + " - DATE '2026-01-01' AS x FROM ct_flavours",
                "Invalid argument types for function '-': (" + spelled[i] + ", DATE)");
        }
    }
}
