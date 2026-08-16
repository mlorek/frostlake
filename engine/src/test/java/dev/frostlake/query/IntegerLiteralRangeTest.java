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
 * A WRITTEN integer literal wider than the largest exact numeric is refused by the literal READER,
 * before any clause sees the number.
 *
 * <p>★ THE REFUSAL IS A DIFFERENT LAYER FROM THE VALUE ONE, and a statement can only ever reach one of
 * them. This is the literal reader: it fires while compiling, carries a POSITION, and echoes the digits
 * as written. Its neighbour — a COMPUTED value past the same width — fires at row time with no position
 * and names the internal type ({@code Number out of representable range: type FIXED[SB16](38,0)…}); see
 * {@code functions/RoundedValueRangeTest}.
 *
 * <p>★ THE PREFIX IS SPELLED WITH A CAPITAL E — {@code SQL compilation error: Error line 1 at position
 * 7} — where the identifier and syntax families use a lower-case "error". One of the few places live
 * capitalises it, and measured rather than assumed.
 *
 * <p>★ ONLY A POINT-FREE, EXPONENT-FREE LITERAL BELONGS TO THIS FAMILY, which is why the sentence says
 * INTEGER literal. The same magnitude written with a decimal point or an exponent is a DOUBLE and has
 * no such limit: {@code 999…9.0} (39 nines) and {@code 1e39} both answer.
 *
 * <p>★ THE LIMIT COUNTS SIGNIFICANT DIGITS BUT THE ECHO IS AS WRITTEN. Leading zeros are free — 39
 * zeros is a legal way to write 0 — yet a literal that refuses is echoed with its leading zero intact.
 *
 * <p>★ A LEADING MINUS IS NOT PART OF THE LITERAL. It is a unary operator, so the position is the
 * digits' own offset (one past the sign) and the echoed value carries no sign.
 */
public class IntegerLiteralRangeTest extends BaseDatabaseTest {

    /** 38 significant digits — the widest an exact numeric holds. */
    private static final String D38 = "99999999999999999999999999999999999999";
    /** 39 significant digits — one past the ceiling. */
    private static final String D39 = "999999999999999999999999999999999999999";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rr (n38 NUMBER(38,0))");
        engine.execute("INSERT INTO rr VALUES (1)");
    }

    private String refusal(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("accepted:");
            while (rs.next()) {
                all.append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String statementRefusal(final String sql) {
        try {
            engine.execute(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The refusal at a given 0-based offset, echoing the digits as written. */
    private static String at(final int position, final String digits) {
        return "SQL compilation error: Error line 1 at position " + position
            + "|Integer literal is out of representable range: " + digits;
    }

    /** 38 digits is the ceiling; 39 is the first refusal, whatever the digits are. */
    @Test
    public void theBoundaryIsThirtyNineSignificantDigits() {
        assertEquals("accepted:" + D38, refusal("SELECT " + D38 + " FROM rr"));
        assertEquals(at(7, D39), refusal("SELECT " + D39 + " FROM rr"));
        final String powerOfTen = "1" + "0".repeat(38);
        assertEquals(at(7, powerOfTen), refusal("SELECT " + powerOfTen + " FROM rr"));
    }

    /** The sign is a unary operator: the position is the digits', and the echo carries no sign. */
    @Test
    public void aLeadingMinusIsNotPartOfTheLiteral() {
        assertEquals(at(8, D39), refusal("SELECT -" + D39 + " FROM rr"));
        assertEquals("accepted:-" + D38, refusal("SELECT -" + D38 + " FROM rr"));
    }

    /** Leading zeros do not count toward the limit, but they DO appear in the echo. */
    @Test
    public void theLimitCountsSignificantDigitsAndTheEchoIsAsWritten() {
        assertEquals(at(7, "0" + D39), refusal("SELECT 0" + D39 + " FROM rr"));
        assertEquals("accepted:" + D38, refusal("SELECT 0" + D38 + " FROM rr"));
        assertEquals("accepted:0", refusal("SELECT " + "0".repeat(39) + " FROM rr"));
        assertEquals("accepted:1", refusal("SELECT " + "0".repeat(40) + "1 FROM rr"));
    }

    /**
     * A decimal point or an exponent takes the literal out of the family entirely — it is a DOUBLE,
     * which has no width limit. Only acceptance is asserted: how the value then RENDERS is a separate
     * question, and the two engines do not yet agree on it.
     */
    @Test
    public void aPointOrExponentLeavesTheFamily() {
        assertEquals("accepted", statementRefusal("SELECT " + D39 + ".0 FROM rr"));
        assertEquals("accepted", statementRefusal("SELECT " + D39 + ".5 FROM rr"));
        assertEquals("accepted", statementRefusal("SELECT 1e39 FROM rr"));
        assertEquals("accepted", statementRefusal("SELECT 1e38 FROM rr"));
    }

    /** The position is the literal's OWN 0-based offset, wherever in the expression it sits. */
    @Test
    public void thePositionIsTheLiteralsOwnOffset() {
        assertEquals(at(13, D39), refusal("SELECT n38 * " + D39 + " FROM rr"));
        assertEquals(at(12, D39), refusal("SELECT n38, " + D39 + " FROM rr"));
        assertEquals(at(11, D39), refusal("SELECT ABS(" + D39 + ") FROM rr"));
        assertEquals(at(7, D39), refusal("SELECT " + D39 + "::NUMBER(38,0) FROM rr"));
    }

    /** It fires wherever a number can be written, because the READER is what refuses it. */
    @Test
    public void everyClauseThatCanHoldANumberRefusesIt() {
        assertEquals(at(31, D39), refusal("SELECT n38 FROM rr WHERE n38 < " + D39));
        assertEquals(at(28, D39), refusal("SELECT n38 FROM rr ORDER BY " + D39));
        assertEquals(at(28, D39), refusal("SELECT n38 FROM rr GROUP BY " + D39));
        assertEquals(at(30, D39),
            refusal("SELECT CASE WHEN n38 > 0 THEN " + D39 + " ELSE 0 END FROM rr"));
        assertEquals(at(36, D39), refusal("SELECT n38 FROM rr WHERE n38 IN (1, " + D39 + ")"));
        assertEquals(at(25, D39), refusal("SELECT n38 FROM rr LIMIT " + D39));
    }

    /** And in the statements that write one outside a query. */
    @Test
    public void theWritingStatementsRefuseItToo() {
        assertEquals(at(23, D39), statementRefusal("INSERT INTO rr VALUES (" + D39 + ")"));
        assertEquals(at(55, D39), statementRefusal(
            "CREATE OR REPLACE TABLE rr_def (v NUMBER(38,0) DEFAULT " + D39 + ")"));
        assertEquals(at(20, D39), statementRefusal("UPDATE rr SET n38 = " + D39 + " WHERE n38 = 1"));
        assertEquals(at(38, D39), statementRefusal(
            "CREATE OR REPLACE VIEW rr_v AS SELECT " + D39 + " AS v FROM rr"));
    }
}
