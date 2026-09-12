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

/**
 * Casting a NON-FINITE to the exact numeric family — refused, in the words of the SOURCE's family.
 *
 * <p>★ THE TARGET DOES NOT CHOOSE THE SENTENCE; THE SOURCE DOES. {@code ::NUMBER}, {@code ::INT} and
 * {@code ::DECIMAL(10,2)} all produce the same refusal, so the declared target is not echoed at all.
 * What changes it is where the value CAME FROM: a VARIANT source names the bare type {@code FIXED},
 * a FLOAT source names the full internal form {@code FIXED[SB16](38,0){not null}} — the shape shared
 * with every other out-of-range numeric refusal.
 *
 * <p>★ AND THE TWO SPELL NaN DIFFERENTLY. From a VARIANT it is {@code NaN}; from a FLOAT it is
 * {@code nan}, lower case. The infinities are {@code inf} and {@code -inf} on both sides — never the
 * word as written, never the word as it renders. There is no rule here to derive one cell from
 * another, which is why each is its own assertion.
 *
 * <p>★ THE SIGN RULE SPLITS. A JSON DOCUMENT reads {@code +NaN} and refuses {@code -NaN}; a CAST
 * accepts both. Applying the document's asymmetry to the cast — which is what Frostlake did — refused
 * {@code '-nan'::FLOAT}, a value live answers.
 */
public class NonFiniteCastTest extends BaseDatabaseTest {

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            rs.next();
            return String.valueOf(rs.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** ★ A VARIANT source: the bare type, and NaN in camel case. */
    @Test
    public void avariantSourceNamesTheBareType() {
        assertEquals("Number out of representable range: type FIXED, value inf",
            answer("SELECT PARSE_JSON('Infinity')::NUMBER"),
            "the value is abbreviated to inf, not echoed as written or as it renders");
        assertEquals("Number out of representable range: type FIXED, value NaN",
            answer("SELECT PARSE_JSON('NaN')::NUMBER"),
            "★ camel case here — the FLOAT source spells the same value differently");
        assertEquals("Number out of representable range: type FIXED, value -inf",
            answer("SELECT PARSE_JSON('-Infinity')::NUMBER"));
    }

    /** ★ The target is not part of the sentence — three targets, one refusal. */
    @Test
    public void thetargetIsNotEchoed() {
        assertEquals("Number out of representable range: type FIXED, value inf",
            answer("SELECT PARSE_JSON('Infinity')::INT"),
            "an INT target says FIXED, exactly as NUMBER does");
        assertEquals("Number out of representable range: type FIXED, value inf",
            answer("SELECT PARSE_JSON('Infinity')::DECIMAL(10,2)"),
            "★ and so does a target carrying its own width — none of it reaches the message");
    }

    /** ★ A FLOAT source: the full internal type, and NaN in lower case. */
    @Test
    public void afloatSourceNamesTheFullType() {
        assertEquals("Number out of representable range: type FIXED[SB16](38,0){not null}, value inf",
            answer("SELECT 'inf'::FLOAT::NUMBER"),
            "the same shape the ordinary out-of-range refusal carries");
        assertEquals("Number out of representable range: type FIXED[SB16](38,0){not null}, value nan",
            answer("SELECT 'NaN'::FLOAT::NUMBER"),
            "★ lower case, where the VARIANT source said NaN — one value, two spellings");
    }

    /** ★ The CAST reads a sign a DOCUMENT would refuse. */
    @Test
    public void thecastAcceptsANegativeNaN() {
        assertEquals("NaN", answer("SELECT '-nan'::FLOAT"),
            "★ the document rule refuses -NaN; the cast does not, so the two readers differ");
        assertEquals("NaN", answer("SELECT '-NaN'::FLOAT"),
            "and the case of the word changes nothing either way");
        assertEquals("NaN", answer("SELECT '+nan'::FLOAT"));
    }

    /** The approximate target holds a non-finite perfectly well — only the EXACT one refuses. */
    @Test
    public void theapproximateTargetKeepsThem() {
        assertEquals("Infinity", answer("SELECT 'inf'::FLOAT"));
        assertEquals("-Infinity", answer("SELECT '-inf'::FLOAT"));
        assertEquals("DOUBLE", answer("SELECT TYPEOF(PARSE_JSON('Infinity'))"),
            "and inside a VARIANT it is a DOUBLE, which is why the exact cast is the only refusal");
        engine.execute("CREATE OR REPLACE TABLE nf AS SELECT 'inf'::FLOAT AS f");
        assertEquals("FLOAT[DOUBLE]", answer("SELECT SYSTEM$TYPEOF(f) FROM nf"),
            "a column built from one is an ordinary FLOAT column");
        assertEquals("Infinity", answer("SELECT f FROM nf"));
    }
}
