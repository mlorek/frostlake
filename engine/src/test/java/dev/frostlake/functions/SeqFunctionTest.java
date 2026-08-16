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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.types.NumericType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SEQ1 / SEQ2 / SEQ4 / SEQ8 — the row's 0-based ordinal, wrapped to the function's width. Every
 * expectation below is a cell measured on a real account.
 */
public class SeqFunctionTest extends BaseDatabaseTest {

    @BeforeEach
    public void createEightRows() {
        engine.execute("CREATE TABLE nums (x INTEGER)");
        engine.execute("INSERT INTO nums VALUES (1),(2),(3),(4),(5),(6),(7),(8)");
    }

    /** The first column of every row, as text, so a whole sequence can be compared in one assertion. */
    private String column(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> values = new ArrayList<>();
        for (int i = 0; i < rs.getRows().size(); i++) {
            values.add(String.valueOf(rs.getRows().get(i).getValue(0)));
        }
        return String.join(",", values);
    }

    private long scalar(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void everyWidthCountsFromZero() {
        assertEquals("0,1,2,3,4,5,6,7", column("SELECT SEQ1() FROM nums"));
        assertEquals("0,1,2,3,4,5,6,7", column("SELECT SEQ2() FROM nums"));
        assertEquals("0,1,2,3,4,5,6,7", column("SELECT SEQ4() FROM nums"));
        assertEquals("0,1,2,3,4,5,6,7", column("SELECT SEQ8() FROM nums"));
    }

    /** With no FROM there is one row, and live numbers it 0. */
    @Test
    public void aFromLessSelectIsRowZero() {
        assertEquals(0L, scalar("SELECT SEQ4()"));
    }

    /**
     * The number belongs to the ROW, not the call: live returns 0,2,4,6 for {@code SEQ4()+SEQ4()}
     * rather than 0+1,2+3,… A counter incremented inside the function would fail this.
     */
    @Test
    public void allCallsInOneRowAgree() {
        assertEquals("0,2,4,6,8,10,12,14", column("SELECT SEQ4()+SEQ4() FROM nums"));
        final ResultSet rs = engine.executeQuery("SELECT SEQ4(), SEQ8() FROM nums");
        for (int i = 0; i < rs.getRows().size(); i++) {
            assertEquals(rs.getRows().get(i).getValue(0), rs.getRows().get(i).getValue(1),
                "the two widths must report the same row");
        }
    }

    /** Live numbers the rows the projection is HANDED, so a WHERE has already removed some. */
    @Test
    public void theCountIsPostFilter() {
        assertEquals("0,1,2,3", column("SELECT SEQ4() FROM nums WHERE x > 4"));
        assertEquals("0,1,2,3", column("SELECT SEQ4() FROM nums WHERE MOD(x,2) = 0"));
    }

    /** And a SEQ in the predicate itself counts the rows the filter reads. */
    @Test
    public void theCountIsAvailableInsideWhere() {
        assertEquals("1,2", column("SELECT x FROM nums WHERE SEQ4() < 2"));
    }

    /** Ungrouped, the group is the whole input in scan order: 0+1+2+3+4. */
    @Test
    public void anAggregateSumsTheOrdinals() {
        assertEquals(10L, scalar("SELECT SUM(SEQ4()) FROM TABLE(GENERATOR(ROWCOUNT=>5))"));
    }

    /**
     * SEQ1 is one byte: unsigned it spans 0..127 and wraps to 0; signed it wraps to -128. Measured
     * live over 260 rows, and here over 1000 so the period is exercised repeatedly.
     */
    @Test
    public void seq1WrapsAtOneByte() {
        assertEquals("0,127,128", widthSummary("SEQ1()", 1000));
        assertEquals("-128,127,256", widthSummary("SEQ1(1)", 1000));
    }

    /** The same rule two bytes wide — the point being that it is a rule, not a single measurement. */
    @Test
    public void seq2WrapsAtTwoBytes() {
        assertEquals("0,32767,32768", widthSummary("SEQ2()", 70000));
        assertEquals("-32768,32767,65536", widthSummary("SEQ2(1)", 70000));
    }

    /** min, max and distinct count of a width over {@code rows} generated rows. */
    private String widthSummary(final String call, final int rows) {
        return column("SELECT MIN(s) || ',' || MAX(s) || ',' || COUNT(DISTINCT s)"
            + " FROM (SELECT " + call + " s FROM TABLE(GENERATOR(ROWCOUNT=>" + rows + ")))");
    }

    /**
     * Each width declares its own digits — live reports NUMBER(3,0), (5,0), (10,0), (19,0). Read off
     * the STATIC type: the reported one is the VARCHAR placeholder every non-semi-structured
     * projection carries, and NumericType.toString drops the parameters, so precision and scale are
     * asserted directly.
     */
    @Test
    public void eachWidthDeclaresItsOwnDigits() {
        Assumptions.assumeFalse(isLiveSnowflake(), NO_STATIC_TYPES);
        assertEquals("3,0", typeOf("SEQ1()"));
        assertEquals("5,0", typeOf("SEQ2()"));
        assertEquals("10,0", typeOf("SEQ4()"));
        assertEquals("19,0", typeOf("SEQ8()"));
    }

    /** And the sign does not widen it: SEQ1(1) reaches -128 inside NUMBER(3,0). */
    @Test
    public void theSignDoesNotChangeTheDeclaredType() {
        Assumptions.assumeFalse(isLiveSnowflake(), NO_STATIC_TYPES);
        assertEquals(typeOf("SEQ1()"), typeOf("SEQ1(1)"));
        assertEquals(typeOf("SEQ8()"), typeOf("SEQ8(1)"));
    }

    /** The declared (p,s) is read off the engine's own column metadata, which the live
     *  harness does not carry — the JDBC result set reports no static type at all. */
    private static final String NO_STATIC_TYPES =
        "the live harness carries no declared static types (precision/scale unreadable)";

    private String typeOf(final String call) {
        final ResultSet rs = engine.executeQuery("SELECT " + call + " FROM nums");
        final NumericType type = (NumericType) rs.getColumns().get(0).getStaticType();
        return type.getPrecision() + "," + type.getScale();
    }

    @Test
    public void theSignMustBeZeroOrOne() {
        assertEquals("Invalid parameter value: 2. Reason: sign must be 0 or 1",
            refusal("SELECT SEQ1(2) FROM nums"));
        assertEquals("Invalid parameter value: -1. Reason: sign must be 0 or 1",
            refusal("SELECT SEQ1(-1) FROM nums"));
        assertEquals("Invalid parameter value: NULL. Reason: sign must not be NULL",
            refusal("SELECT SEQ1(NULL) FROM nums"));
        assertEquals("Numeric value 'x' is not recognized",
            refusal("SELECT SEQ1('x') FROM nums"));
    }

    /** Anything whose numeric value lands on 0 or 1 is taken, including a string. */
    @Test
    public void aNumericValuedSignIsAccepted() {
        assertEquals("0,1,2,3,4,5,6,7", column("SELECT SEQ1(1.0) FROM nums"));
        assertEquals("0,1,2,3,4,5,6,7", column("SELECT SEQ1('1') FROM nums"));
        assertEquals("0,1,2,3,4,5,6,7", column("SELECT SEQ1(0) FROM nums"));
    }

    @Test
    public void atMostOneArgument() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT SEQ4(0,1) FROM nums");
            }
        });
        assertTrue(ex.getMessage().contains("too many arguments"), ex.getMessage());
    }

    private String refusal(final String sql) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return rootMessage(ex);
    }

    private String rootMessage(final Throwable thrown) {
        Throwable current = thrown;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
