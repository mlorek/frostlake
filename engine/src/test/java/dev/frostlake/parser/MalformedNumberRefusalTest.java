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
 * How a malformed number — a digit run that opens an exponent and stops — is refused. The refusal
 * itself was settled by #365; this is its WORDING, and the sentence is stranger than it looks:
 *
 * <pre>
 *   SELECT 1e FROM ex   parse error line 1 at position 9 near '32'.
 * </pre>
 *
 * <p>It does NOT anchor on the offending token. It anchors on the character immediately AFTER it, and
 * names that character by its DECIMAL CHARACTER CODE — 32 is the space. Without the decoding the
 * number reads like an internal token id, which is exactly what it was first taken for. Every code
 * below was measured against a real account:
 *
 * <pre>
 *   32 space   97 'a'   41 ')'   44 ','   58 ':'   59 ';'   9 tab   10 newline
 *   &lt;EOF&gt; past the end of the input — the one case spelled rather than coded
 * </pre>
 *
 * <p>A DANGLING SIGN belongs to the malformed token: {@code 1e+} reports position 10 where {@code 1e}
 * reports 9, so the parse error moves right with it rather than pointing at the sign.
 *
 * <p>★ WHAT IS NOT MATCHED, MEASURED IN FULL AND DELIBERATELY LEFT: live often STACKS a second
 * sentence, an ordinary syntax error at whatever it hits while recovering. Frostlake stops at the parse
 * error. Whether the second sentence appears is NOT derivable from the statement, and the whole measured
 * set is recorded here because the table is the value — four models were tried against it and every one
 * fails on cells another explains.
 *
 * <pre>
 *   SECOND SENTENCE PRESENT — live's message after the shared first line
 *     SELECT 1e FROM ex                position 10 unexpected 'FROM'.
 *     SELECT 1e AS a FROM ex           position 10 unexpected 'AS'.
 *     SELECT i FROM ex WHERE 1e = 1    position 26 unexpected '='.
 *     SELECT i FROM ex WHERE i = 1e    position 29 unexpected '&lt;EOF&gt;'.
 *     SELECT 1e                        position  9 unexpected '&lt;EOF&gt;'.
 *     SELECT 1ea FROM ex               position 11 unexpected 'FROM'.   (past the 'a')
 *     SELECT 1e * 2 FROM ex            position 12 unexpected '2'.      (past the '*')
 *     SELECT 1e;                       position 10 unexpected '&lt;EOF&gt;'.
 *     SELECT 1e || 'x' FROM ex         position 10 unexpected '||'.     (NOT past the '||')
 *     SELECT i FROM ex ORDER BY 1e     position 28 unexpected '&lt;EOF&gt;'.
 *     SELECT i FROM ex GROUP BY 1e     position 28 unexpected '&lt;EOF&gt;'.
 *     SELECT 1e\nFROM ex               line 2 position 0 unexpected 'FROM'.
 *     SELECT 1e, 2e FROM ex            a SECOND parse error at 13, then position 14 unexpected 'FROM'.
 *     SELECT ABS(1e) FROM ex           position 15 unexpected 'FROM', AND a third, BACKWARDS line:
 *                                      position 10 unexpected '('.
 *
 *   SECOND SENTENCE ABSENT
 *     SELECT 1e + 1 FROM ex     SELECT 1e - 1 FROM ex     SELECT 1e, i FROM ex
 *     SELECT 1e::INT FROM ex    INSERT INTO ex VALUES (1e, 'x')
 * </pre>
 *
 * <p>The models, and where each dies:
 * <ul>
 *   <li>DROP the malformed token and re-parse — explains FROM/AS/=/EOF, but predicts an error for
 *       {@code 1e, i} (leaving {@code SELECT , i}) where live is silent.</li>
 *   <li>REPLACE it with a valid number — explains {@code 1e + 1} and {@code 1e, i}, but predicts NO
 *       error for {@code SELECT 1e FROM ex} ({@code SELECT 1 FROM ex} parses) where live has one.</li>
 *   <li>The exponent CONSUMES the next token as its digits — explains FROM/AS/=/&lt;EOF&gt;/'||' and the
 *       'a' of {@code 1ea}, but predicts {@code '*'} for {@code 1e * 2} where live blames the
 *       {@code 2} beyond it.</li>
 *   <li>The exponent takes a SIGN then digits — explains the {@code +} and {@code -} silence exactly,
 *       but then {@code 1e * 2} should either parse or blame the {@code *}, and it does neither.</li>
 * </ul>
 *
 * <p>Neither the {@code +} / {@code -} / {@code ,} / {@code ::} silence nor the {@code *} / {@code ||}
 * noise falls out of any of them. This is live's own parser recovery, and reproducing it would mean
 * steering ANTLR's to match — for a statement shape almost nobody writes. The decision is the same one
 * the LIMIT-slot and unclosed-call stacking reached: record it, do not chase it.
 *
 * <p>Every assertion here is on the FIRST line, except the handful of statements where the two engines
 * already agree in full.
 */
public class MalformedNumberRefusalTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ex (i INT, s VARCHAR(9))");
    }

    /** The whole refusal, newlines rendered so nothing can normalise them away. */
    private String messageOf(final String sql) {
        try {
            engine.execute(sql);
            return "<accepted>";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\r", "\\r").replace("\n", "\\n");
        }
    }

    /** Just the sentence under the prefix — the part whose rule is determined. */
    private String firstSentenceOf(final String sql) {
        final String[] lines = messageOf(sql).split("\\\\n");
        return lines.length > 1 ? lines[1] : lines[0];
    }

    /** The position is the character AFTER the token, and the code is that character's. */
    @Test
    public void thePositionIsTheCharacterAfterTheToken() {
        assertEquals("parse error line 1 at position 9 near '32'.",
            firstSentenceOf("SELECT 1e FROM ex"));
        assertEquals("parse error line 1 at position 9 near '32'.",
            firstSentenceOf("SELECT 1E FROM ex"), "the E's case changes nothing");
        assertEquals("parse error line 1 at position 9 near '32'.",
            firstSentenceOf("SELECT 0e FROM ex"));
        assertEquals("parse error line 1 at position 10 near '32'.",
            firstSentenceOf("SELECT 12e FROM ex"), "a longer digit run moves it right");
        assertEquals("parse error line 1 at position 11 near '32'.",
            firstSentenceOf("SELECT 1.5e FROM ex"));
    }

    /** A dangling SIGN is part of the malformed token, so the position moves past it too. */
    @Test
    public void aDanglingSignBelongsToTheToken() {
        assertEquals("parse error line 1 at position 10 near '32'.",
            firstSentenceOf("SELECT 1e+ FROM ex"));
        assertEquals("parse error line 1 at position 10 near '32'.",
            firstSentenceOf("SELECT 1e- FROM ex"));
        assertEquals("parse error line 1 at position 12 near '32'.",
            firstSentenceOf("SELECT 1.5e+ FROM ex"));
    }

    /** Every character that can follow one, by its code. */
    @Test
    public void theCharacterIsNamedByItsDecimalCode() {
        assertEquals("parse error line 1 at position 9 near '97'.",
            firstSentenceOf("SELECT 1ea FROM ex"), "97 is 'a' — so 1ea is 1e followed by a letter");
        assertEquals("parse error line 1 at position 10 near '41'.",
            firstSentenceOf("SELECT (1e) FROM ex"), "41 is ')'");
        assertEquals("parse error line 1 at position 9 near '44'.",
            firstSentenceOf("SELECT 1e, i FROM ex"), "44 is ','");
        assertEquals("parse error line 1 at position 9 near '58'.",
            firstSentenceOf("SELECT 1e::INT FROM ex"), "58 is ':' — the FIRST of the two");
        assertEquals("parse error line 1 at position 9 near '59'.",
            firstSentenceOf("SELECT 1e;"), "59 is ';'");
        assertEquals("parse error line 1 at position 9 near '9'.",
            firstSentenceOf("SELECT 1e\tFROM ex"), "9 is a TAB, coded like any other character");
        assertEquals("parse error line 1 at position 9 near '10'.",
            firstSentenceOf("SELECT 1e\nFROM ex"), "10 is a NEWLINE, and the LINE stays 1");
    }

    /** Past the end of the input it is SPELLED rather than coded. */
    @Test
    public void endOfInputIsSpelledNotCoded() {
        assertEquals("parse error line 1 at position 9 near '<EOF>'.", firstSentenceOf("SELECT 1e"));
        assertEquals("parse error line 1 at position 29 near '<EOF>'.",
            firstSentenceOf("SELECT i FROM ex WHERE i = 1e"));
        assertEquals("parse error line 1 at position 28 near '<EOF>'.",
            firstSentenceOf("SELECT i FROM ex ORDER BY 1e"));
        assertEquals("parse error line 1 at position 28 near '<EOF>'.",
            firstSentenceOf("SELECT i FROM ex GROUP BY 1e"));
    }

    /** Wherever in a statement it is written, the rule is the same. */
    @Test
    public void itIsTheSameRuleInEveryClause() {
        assertEquals("parse error line 1 at position 9 near '32'.",
            firstSentenceOf("SELECT 1e AS a FROM ex"));
        assertEquals("parse error line 1 at position 25 near '32'.",
            firstSentenceOf("SELECT i FROM ex WHERE 1e = 1"));
        assertEquals("parse error line 1 at position 13 near '41'.",
            firstSentenceOf("SELECT ABS(1e) FROM ex"));
        assertEquals("parse error line 1 at position 25 near '44'.",
            firstSentenceOf("INSERT INTO ex VALUES (1e, 'x')"));
        assertEquals("parse error line 1 at position 9 near '32'.",
            firstSentenceOf("SELECT 1e || 'x' FROM ex"));
    }

    /** The statements where the WHOLE message already agrees, second sentence included. */
    @Test
    public void theWholeMessageAgreesWhereLiveStacksNothingExtra() {
        assertEquals("SQL compilation error:\\nparse error line 1 at position 9 near '32'.",
            messageOf("SELECT 1e + 1 FROM ex"));
        assertEquals("SQL compilation error:\\nparse error line 1 at position 9 near '32'.",
            messageOf("SELECT 1e - 1 FROM ex"));
        assertEquals("SQL compilation error:\\nparse error line 1 at position 9 near '44'.",
            messageOf("SELECT 1e, i FROM ex"));
        assertEquals("SQL compilation error:\\nparse error line 1 at position 9 near '58'.",
            messageOf("SELECT 1e::INT FROM ex"));
        assertEquals("SQL compilation error:\\nparse error line 1 at position 25 near '44'.",
            messageOf("INSERT INTO ex VALUES (1e, 'x')"));
        assertEquals("SQL compilation error:\\nparse error line 1 at position 10 near '41'."
            + "\\nsyntax error line 1 at position 12 unexpected 'FROM'.",
            messageOf("SELECT (1e) FROM ex"), "and one where the second sentence agrees too");
    }

    /** The VALID exponents beside them, which must keep reading. */
    @Test
    public void theValidExponentsStillRead() {
        assertEquals("<accepted>", messageOf("SELECT 1e1 FROM ex"));
        assertEquals("<accepted>", messageOf("SELECT 1e5 FROM ex"));
        assertEquals("<accepted>", messageOf("SELECT 1e+5 FROM ex"));
        assertEquals("<accepted>", messageOf("SELECT 1e-5 FROM ex"));
        assertEquals("<accepted>", messageOf("SELECT 1.5e2 FROM ex"));
    }
}
