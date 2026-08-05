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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Typed procedural variables: a value assigned to a DECLAREd variable — via DEFAULT, {@code :=},
 * SELECT INTO, or FETCH INTO — is coerced to the declared type the way Snowflake casts on
 * assignment (numeric strings parse, fractional values round half away from zero into integral
 * types, FLOAT holds doubles, booleans convert like TO_BOOLEAN). SELECT INTO additionally requires
 * its query to return at most one row (zero rows sets the targets to NULL; more than one is an
 * error) with as many columns as INTO targets.
 */
public class SelectIntoCoercionTest extends BaseDatabaseTest {

    private Object ret(final String block) {
        final ResultSet rs = engine.executeQuery(block);
        assertEquals(1, rs.getRowCount());
        return rs.getRows().get(0).getValue(0);
    }

    private void seedNums() {
        engine.execute("CREATE TABLE nums (n INTEGER)");
        engine.execute("INSERT INTO nums VALUES (1), (2)");
    }

    // ── coercion on the four assignment paths ─────────────────────────────────────────────────────

    // SUM evaluates to 15.0, but an INTEGER variable holds the integral 15 (regression: was "15.0").
    @Test
    public void sumIntoIntegerIsIntegral() {
        engine.execute("CREATE TABLE fives (n INTEGER)");
        engine.execute("INSERT INTO fives VALUES (1),(2),(3),(4),(5)");
        final Object v = ret(
            "DECLARE t INTEGER; BEGIN SELECT SUM(n) INTO t FROM fives; RETURN t; END");
        assertEquals("15", String.valueOf(v));
    }

    // AVG of (1,2) is 1.5 — rounds half away from zero into an INTEGER target.
    @Test
    public void avgIntoIntegerRoundsHalfUp() {
        seedNums();
        final Object v = ret(
            "DECLARE t INTEGER; BEGIN SELECT AVG(n) INTO t FROM nums; RETURN t; END");
        assertEquals("2", String.valueOf(v));
    }

    // A VARCHAR target stringifies the selected value.
    @Test
    public void selectIntoVarcharYieldsString() {
        engine.execute("CREATE TABLE one (n INTEGER)");
        engine.execute("INSERT INTO one VALUES (42)");
        final Object v = ret(
            "DECLARE s VARCHAR; BEGIN SELECT n INTO s FROM one; RETURN s; END");
        assertEquals("42", v);
    }

    // DECLARE ... DEFAULT coerces: a numeric string default becomes a number.
    @Test
    public void declareDefaultStringCoercesToInteger() {
        final Object v = ret(
            "DECLARE x INTEGER DEFAULT '5'; BEGIN RETURN x + 1; END");
        assertEquals(6L, ((Number) v).longValue());
    }

    @Test
    public void declareDefaultStringCoercesToBoolean() {
        assertEquals("yes", ret(
            "DECLARE b BOOLEAN DEFAULT 'true';"
            + " BEGIN IF (b) THEN RETURN 'yes'; ELSE RETURN 'no'; END IF; END"));
    }

    // := assignment coerces to the declared type as well.
    @Test
    public void assignmentCoercesToDeclaredType() {
        final Object v = ret(
            "DECLARE x INTEGER; BEGIN x := '7'; RETURN x + 1; END");
        assertEquals(8L, ((Number) v).longValue());
    }

    // A FLOAT variable holds a double even when assigned an integer.
    @Test
    public void floatVariableHoldsDouble() {
        final Object v = ret(
            "DECLARE f FLOAT; BEGIN f := 1; RETURN f; END");
        assertEquals("1.0", String.valueOf(v));
    }

    // FETCH INTO coerces to the target's declared type (AVG of (1,2) is 1.5 → 2).
    @Test
    public void fetchIntoCoerces() {
        seedNums();
        final Object v = ret(
            "DECLARE t INTEGER; c CURSOR FOR SELECT AVG(n) FROM nums;"
            + " BEGIN OPEN c; FETCH c INTO t; CLOSE c; RETURN t; END");
        assertEquals("2", String.valueOf(v));
    }

    // ── SELECT INTO row-count and column-count enforcement ────────────────────────────────────────

    // Zero rows is NOT an error (verified against live Snowflake): the targets are assigned NULL
    // and the block continues — probe idioms and walk-past-the-end fetch loops rely on it.
    @Test
    public void selectIntoZeroRowsAssignsNull() {
        seedNums();
        final ResultSet rs = engine.executeQuery(
            "DECLARE t INTEGER; BEGIN SELECT n INTO t FROM nums WHERE n > 100; RETURN COALESCE(:t, -1); END");
        assertEquals("-1", String.valueOf(rs.getRows().get(0).getValue(0)));
    }

    @Test
    public void selectIntoMultipleRowsErrors() {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "matches the exact \"wrong number of rows: 2\" wording of the too-many-rows SELECT INTO "
            + "error; a real account words that failure differently — the sibling test proves the error "
            + "itself is raised and catchable on both backends");
        seedNums();
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "DECLARE t INTEGER; BEGIN SELECT n INTO t FROM nums; RETURN t; END");
            }
        });
        assertTrue(ex.getMessage().contains("wrong number of rows: 2"),
            "unexpected message: " + ex.getMessage());
    }

    // The too-many-rows error is an ordinary statement error — catchable by an EXCEPTION handler.
    @Test
    public void selectIntoMultipleRowsCatchableByHandler() {
        seedNums();
        assertEquals("caught", ret(
            "DECLARE t INTEGER; BEGIN SELECT n INTO t FROM nums; RETURN t;"
            + " EXCEPTION WHEN OTHER THEN RETURN 'caught'; END"));
    }

    @Test
    public void selectIntoTargetCountMismatchErrors() {
        engine.execute("CREATE TABLE one2 (n INTEGER)");
        engine.execute("INSERT INTO one2 VALUES (42)");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "DECLARE t INTEGER; BEGIN SELECT n, n INTO t FROM one2; RETURN t; END");
            }
        });
        assertTrue(ex.getMessage().contains("does not match"),
            "unexpected message: " + ex.getMessage());
    }
}
