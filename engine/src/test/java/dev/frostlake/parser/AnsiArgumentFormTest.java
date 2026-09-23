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

package dev.frostlake.parser;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ANSI {@code <fn>(<x> FROM <a> FOR <b>)} argument form, which Snowflake refuses for EVERY
 * function name there is — SUBSTRING included, and EXTRACT the single exception. So there is no
 * syntax to add here and adding some would be the fidelity bug; what was wrong was the refusal.
 *
 * <p>Frostlake explained itself in a sentence of its own invention — "'SUBSTRING' does not accept a
 * FROM argument form (only EXTRACT does)" — which appears on no real account and carried no line or
 * position at all. That is the message-level twin of an FL-only syntax extension. Live says only
 * where the FROM is:
 *
 * <pre>
 *   SELECT SUBSTRING(v FROM 2) FROM rs   syntax error line 1 at position 19 unexpected 'FROM'.
 * </pre>
 *
 * <p>The parse is carried as far as the FROM ON PURPOSE, by an alternative that accepts nothing: kill
 * it earlier — with a predicate on the function name — and the refusal lands on the argument BEFORE
 * the FROM instead, which is measured and is why the grammar reads the way it does. The same reasoning
 * already governs the ANSI POSITION form.
 *
 * <p>ONE LINE is asserted per case: the lines live's recovery stacks after the FROM are pinned in
 * {@code AnsiFromClauseReadingTest}. Live's second line for {@code SUBSTRING(v FROM 2)} is position 24 ('2'),
 * and its third for the FOR form is position 30.
 */
public class AnsiArgumentFormTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rs (v VARCHAR, n INT)");
        engine.execute("INSERT INTO rs VALUES ('hello', 2)");
    }

    /** The FIRST syntax-error line a statement raises, or "accepted". */
    private String firstLine(final String sql) {
        try {
            engine.execute(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            for (final String line : String.valueOf(refused.getMessage()).split("\n")) {
                if (line.startsWith("syntax error")) {
                    return line;
                }
            }
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** The form the task is named for: the refusal sits on the FROM, not on the FOR after it. */
    @Test
    public void theFromForFormIsRefusedAtTheFrom() {
        assertEquals("syntax error line 1 at position 19 unexpected 'FROM'.",
            firstLine("SELECT SUBSTRING(v FROM 1 FOR 2) FROM rs"));
        assertEquals("syntax error line 1 at position 19 unexpected 'FROM'.",
            firstLine("SELECT SUBSTRING(v FROM 1 FOR 2 FOR 3) FROM rs"),
            "a second FOR changes nothing — live reports the same three lines");
        assertEquals("syntax error line 1 at position 23 unexpected 'FROM'.",
            firstLine("SELECT SUBSTRING('abc' FROM 1 FOR 2)"));
    }

    /** And the FROM form without a FOR, which used to get the invented sentence. */
    @Test
    public void theFromFormAloneIsRefusedThereToo() {
        assertEquals("syntax error line 1 at position 19 unexpected 'FROM'.",
            firstLine("SELECT SUBSTRING(v FROM 2) FROM rs"));
        assertEquals("syntax error line 1 at position 19 unexpected 'FROM'.",
            firstLine("SELECT SUBSTRING(v FROM n) FROM rs"));
        assertEquals("syntax error line 1 at position 25 unexpected 'FROM'.",
            firstLine("SELECT SUBSTRING('hello' FROM 2)"));
        assertEquals("syntax error line 1 at position 16 unexpected 'FROM'.",
            firstLine("SELECT SUBSTR(v FROM 2) FROM rs"));
    }

    /** The position follows the FROM through the statement, not the function or the select list. */
    @Test
    public void thePositionFollowsTheFromItself() {
        assertEquals("syntax error line 1 at position 22 unexpected 'FROM'.",
            firstLine("SELECT 1, SUBSTRING(v FROM 2) FROM rs"));
        assertEquals("syntax error line 1 at position 15 unexpected 'FROM'.",
            firstLine("SELECT UPPER(v FROM 2) FROM rs"));
        assertEquals("syntax error line 1 at position 18 unexpected 'FROM'.",
            firstLine("SELECT NOSUCHFN(v FROM 2) FROM rs"),
            "an unknown name is refused as SYNTAX, before anything looks the name up");
    }

    /** TRIM's ANSI spellings are refused the same way, at their own FROM. */
    @Test
    public void theAnsiTrimFormsAreRefusedAtTheirFrom() {
        assertEquals("syntax error line 1 at position 16 unexpected 'FROM'.",
            firstLine("SELECT TRIM(' ' FROM v) FROM rs"));
        assertEquals("syntax error line 1 at position 20 unexpected 'FROM'.",
            firstLine("SELECT TRIM(LEADING FROM v) FROM rs"));
    }

    /** EXTRACT is the exception — but only with an IDENTIFIER part. */
    @Test
    public void extractKeepsTheFromFormWithAnIdentifierPart() {
        assertEquals("accepted", firstLine("SELECT EXTRACT(YEAR FROM CURRENT_DATE)"));
        assertEquals("accepted", firstLine("SELECT EXTRACT(month FROM '2023-05-08'::DATE)"));
        assertEquals("syntax error line 1 at position 22 unexpected 'FROM'.",
            firstLine("SELECT EXTRACT('YEAR' FROM CURRENT_DATE)"),
            "a STRING part is refused, which Frostlake used to accept");
        assertEquals("accepted", firstLine("SELECT EXTRACT('month', '2023-05-08'::DATE)"),
            "the COMMA form takes a string part and is a different alternative entirely");
    }

    /** A FOR with no FROM is refused at the FOR, and the ordinary spellings still run. */
    @Test
    public void theOrdinarySpellingsAreUntouched() {
        assertEquals("syntax error line 1 at position 19 unexpected 'FOR'.",
            firstLine("SELECT SUBSTRING(v FOR 2) FROM rs"));
        assertEquals("accepted", firstLine("SELECT SUBSTRING(v, 1, 2) FROM rs"));
        assertEquals("accepted", firstLine("SELECT TRIM(v, ' ') FROM rs"));
        assertEquals("accepted", firstLine("SELECT POSITION('e' IN v) FROM rs"),
            "the ANSI POSITION spelling live DOES accept");
        assertEquals("accepted", firstLine("SELECT CAST(v AS VARCHAR) FROM rs"));
    }

    /** OVERLAY's ANSI spelling is refused at its own keyword, which is not a FROM at all. */
    @Test
    public void overlayIsRefusedAtItsPlacingKeyword() {
        assertEquals("syntax error line 1 at position 17 unexpected 'PLACING'.",
            firstLine("SELECT OVERLAY(v PLACING 'x' FROM 1) FROM rs"));
    }
}
