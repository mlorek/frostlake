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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a timestamp's OFFSET — as a date-part component, and as a format element.
 *
 * <p>★ TZH AND TZM ARE COMPONENTS OF THE VALUE, not of the session. Each flavour answers with its own
 * offset: a TIMESTAMP_LTZ gives the session's, a TIMESTAMP_TZ gives the one it was WRITTEN with, and a
 * TIMESTAMP_NTZ gives zero. Frostlake had neither word in its vocabulary and refused them, while the
 * long spellings TIMEZONE_HOUR / TIMEZONE_MINUTE it did have answered a hard-coded zero for everything.
 *
 * <p>★ A DATE STILL REFUSES THEM, which is the cell that keeps the rule honest: an NTZ answering zero
 * and a DATE refusing outright are different answers to the same word, so the component is decided by
 * the value's KIND and not by whether an offset happens to be there. Both engines already agreed on
 * that refusal and it must not become a zero.
 *
 * <p>★ A BARE TZM IS NOT A FORMAT ELEMENT. Live renders the format string {@code 'TZM'} as the literal
 * text TZM — only the pair (with or without its colon) and TZH alone are read — so the element table
 * carries no TZM entry on purpose. Frostlake rendered a hard-coded {@code 00} there and an escaped
 * {@code 'Z} for the pair, which is what a formatter does when it is quoting rather than refusing.
 *
 * <p>NOT FIXED HERE, tracked on its own: the TZD element (live renders the zone ABBREVIATION, PST).
 */
public class TimezoneComponentTest extends BaseDatabaseTest {

    private static final String LTZ = "'2020-01-01 10:00:00'::TIMESTAMP_LTZ";
    private static final String TZ = "'2020-01-01 10:00:00 +0530'::TIMESTAMP_TZ";
    private static final String NTZ = "'2020-01-01 10:00:00'::TIMESTAMP_NTZ";

    @Override
    protected void setupTest() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
    }

    /** One scalar, as text, or the refusal. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            rs.next();
            return String.valueOf(rs.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** ★ Each flavour reports its OWN offset. */
    @Test
    public void eachFlavourReportsItsOwnOffset() {
        assertEquals("-8", answer("SELECT DATE_PART('TZH', " + LTZ + ")"),
            "an LTZ reports the session's offset");
        assertEquals("0", answer("SELECT DATE_PART('TZM', " + LTZ + ")"));
        assertEquals("5", answer("SELECT DATE_PART('TZH', " + TZ + ")"),
            "★ a TZ reports the offset it was WRITTEN with, not the session's");
        assertEquals("30", answer("SELECT DATE_PART('TZM', " + TZ + ")"));
        assertEquals("0", answer("SELECT DATE_PART('TZH', " + NTZ + ")"),
            "and an NTZ has no offset, which reads as zero rather than refusing");
        assertEquals("0", answer("SELECT DATE_PART('TZM', " + NTZ + ")"));
    }

    /** The LONG spellings are the same components, and were answering a hard-coded zero. */
    @Test
    public void thelongSpellingsAreTheSameComponents() {
        assertEquals("-8", answer("SELECT DATE_PART('TIMEZONE_HOUR', " + LTZ + ")"));
        assertEquals("30", answer("SELECT DATE_PART('TIMEZONE_MINUTE', " + TZ + ")"));
    }

    /** EXTRACT and the BAREWORD slot read them too — one vocabulary, three spellings of the call. */
    @Test
    public void extractAndTheBarewordReadThemToo() {
        assertEquals("-8", answer("SELECT EXTRACT(TZH FROM " + LTZ + ")"));
        assertEquals("-8", answer("SELECT DATE_PART(TZH, " + LTZ + ")"));
    }

    /** ★ A DATE refuses them — the component is decided by KIND, not by having an offset. */
    @Test
    public void adateStillRefusesThem() {
        assertEquals("SQL compilation error:|invalid value [TZH] for parameter"
            + " 'DATE_PART date/time part'",
            answer("SELECT DATE_PART('TZH', '2020-01-01'::DATE)"));
    }

    /** ★ The format elements render the value's own offset, a zero one as Z. */
    @Test
    public void theformatElementsRenderTheOffset() {
        assertEquals("2020-01-01 10:00:00 -08:00",
            answer("SELECT TO_VARCHAR(" + LTZ + ", 'YYYY-MM-DD HH24:MI:SS TZH:TZM')"));
        assertEquals("2020-01-01 10:00:00 +05:30",
            answer("SELECT TO_VARCHAR(" + TZ + ", 'YYYY-MM-DD HH24:MI:SS TZH:TZM')"));
        assertEquals("2020-01-01 10:00:00 Z",
            answer("SELECT TO_VARCHAR(" + NTZ + ", 'YYYY-MM-DD HH24:MI:SS TZH:TZM')"),
            "a naive timestamp shows the zero offset as Z, not as +00:00");
        assertEquals("-0800", answer("SELECT TO_VARCHAR(" + LTZ + ", 'TZHTZM')"),
            "without the colon the pair is four digits");
        assertEquals("-08", answer("SELECT TO_VARCHAR(" + LTZ + ", 'TZH')"));
    }

    /** ★ A bare TZM is LITERAL TEXT, and the elements are case-insensitive. */
    @Test
    public void abareTzmIsLiteralText() {
        assertEquals("TZM", answer("SELECT TO_VARCHAR(" + LTZ + ", 'TZM')"));
        assertEquals("-08:00", answer("SELECT TO_VARCHAR(" + LTZ + ", 'tzh:tzm')"));
    }

    /** Text that is not an element passes through, which is how TZM comes back at all. */
    @Test
    public void unrecognisedTextPassesThrough() {
        assertEquals("2020 QQQ 01", answer("SELECT TO_VARCHAR(" + LTZ + ", 'YYYY QQQ MM')"));
        assertEquals("2020 ZZZ 01", answer("SELECT TO_VARCHAR(" + LTZ + ", 'YYYY ZZZ MM')"));
    }

    /**
     * ★ CURRENT_TIMESTAMP IS A TIMESTAMP_LTZ — and the cell that proves it is not the flavour word but
     * what SURVIVES A ROUND TRIP THROUGH SQL TEXT. A scripting variable is spliced into the statement
     * it feeds, so a carrier with no literal form of its own arrives there as a quoted STRING and
     * every temporal call over it then refuses ON TYPE: TO_VARCHAR's second argument is legal only
     * over a temporal, so the string form reads as "too many arguments", and EXTRACT names the
     * VARCHAR outright. Two refusals that both report a type is how a lost flavour announces itself,
     * and neither is visible to a SELECT that never leaves the expression tree.
     */
    @Test
    public void currentTimestampIsAnLtzThroughSqlText() {
        assertEquals("TIMESTAMP_LTZ(9)[SB16]", answer("SELECT SYSTEM$TYPEOF(CURRENT_TIMESTAMP())"));
        assertEquals("TIMESTAMP_LTZ", answer("SELECT TYPEOF(TO_VARIANT(LOCALTIMESTAMP()))"),
            "LOCALTIMESTAMP is the same value under a second name");
        assertEquals("TIMESTAMP_NTZ", answer("SELECT TYPEOF(TO_VARIANT(SYSDATE()))"),
            "★ SYSDATE is the one that is NOT, and keeps the flavour from being a blanket rule");
        // A RELATION, not a clock reading: the component has to be the session's offset, which is
        // exactly what the same instant written as an LTZ reports. While the flavour was wrong this
        // pair read 0 against the session's own offset, and no literal timestamp could show it.
        assertEquals(answer("SELECT DATE_PART('TZH', CURRENT_TIMESTAMP()::TIMESTAMP_LTZ)"),
            answer("SELECT DATE_PART('TZH', CURRENT_TIMESTAMP())"),
            "casting a value to the flavour it already is must change nothing");
        assertEquals(answer("SELECT EXTRACT(YEAR FROM CURRENT_TIMESTAMP())"),
            answer("""
                BEGIN
                  LET ts TIMESTAMP_LTZ := CURRENT_TIMESTAMP();
                  RETURN EXTRACT(YEAR FROM :ts);
                END"""),
            "★ the same component through a scripting variable, which is the path that goes"
                + " through SQL text and the only one the flavour can be lost on");
        assertEquals(answer("SELECT EXTRACT(YEAR FROM CURRENT_TIMESTAMP())"),
            answer("""
                BEGIN
                  LET ts TIMESTAMP_LTZ := CURRENT_TIMESTAMP();
                  LET y INT := (SELECT EXTRACT(YEAR FROM :ts));
                  RETURN y;
                END"""),
            "and again where the variable feeds a nested query rather than the RETURN");
    }

    /**
     * ★ A DERIVED COLUMN KEEPS THE FLAVOUR OF THE VALUE IT STORES — the second half of the round
     * trip, and the one with no error of its own. A CTAS types each column by looking at what the
     * rows actually hold, and a carrier that no branch of that scan recognises does not announce
     * itself: it falls through to the fallback a derived column gets, the 16MB VARCHAR. The table is
     * then created, populated and queryable, and the fault surfaces only one statement LATER, when
     * something temporal is handed that column and refuses on its type. That is why the check here
     * is the column's own type and not the statement that made it.
     */
    @Test
    public void aderivedColumnKeepsTheFlavourOfItsValue() {
        answer("CREATE OR REPLACE TABLE tz_ctas AS SELECT CURRENT_TIMESTAMP() AS t");
        assertEquals("TIMESTAMP_LTZ(9)[SB16]", answer("SELECT SYSTEM$TYPEOF(t) FROM tz_ctas"),
            "the stored value has an offset, so the column that holds it is the offset flavour");
        assertTrue(answer("SELECT DATE_PART(epoch_millisecond, t) FROM tz_ctas").matches("\\d+"),
            "★ and a temporal function over that column ANSWERS — a text column would refuse here,"
                + " one statement after the CREATE that actually chose the wrong type");
        // The same column built through a SCRIPTING VARIABLE, which is how a value reaches a CTAS
        // without ever being written as a literal. The variable is declared at the flavour it is
        // being given: a variable whose DECLARED type differs from its value is a surface of its
        // own — live converts to the declaration and Frostlake does not — and it is tracked apart
        // from this one, so the cell here holds the declaration and the value in agreement.
        answer("""
            DECLARE
              r timestamp_ltz := current_timestamp();
            BEGIN
              CREATE OR REPLACE TABLE tz_ctas_var AS SELECT :r AS t;
              RETURN 'ok';
            END""");
        assertEquals("TIMESTAMP_LTZ(9)[SB16]", answer("SELECT SYSTEM$TYPEOF(t) FROM tz_ctas_var"),
            "the interpolated value carries its flavour into the new column too");
        assertTrue(answer("SELECT DATE_PART(epoch_millisecond, t) FROM tz_ctas_var").matches("\\d+"));
        // ★ A WRITTEN offset is the other flavour, and the two must not collapse into one.
        answer("CREATE OR REPLACE TABLE tz_ctas_tz AS SELECT " + TZ + " AS t");
        assertEquals("TIMESTAMP_TZ(9)[SB16]", answer("SELECT SYSTEM$TYPEOF(t) FROM tz_ctas_tz"));
        answer("CREATE OR REPLACE TABLE tz_ctas_ntz AS SELECT " + NTZ + " AS t");
        assertEquals("TIMESTAMP_NTZ(9)[SB16]", answer("SELECT SYSTEM$TYPEOF(t) FROM tz_ctas_ntz"),
            "and a naive value still makes a naive column");
    }

    /** ★ SHOW PARAMETERS' own row for a session-set parameter, every cell of it. */
    @Test
    public void showParametersReportsTheSessionLevel() {
        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS LIKE 'TIMEZONE'");
        rs.next();
        final StringBuilder row = new StringBuilder();
        for (int c = 0; c < rs.getColumns().size(); c++) {
            if (c > 0) {
                row.append("/");
            }
            row.append(String.valueOf(rs.getValue(c)));
        }
        assertEquals("TIMEZONE/America/Los_Angeles/America/Los_Angeles/SESSION/time zone/STRING",
            row.toString(),
            "the account default is America/Los_Angeles, a session-set parameter reads SESSION,"
                + " the description is lower-cased and the type word is STRING");
    }
}
