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
 * REGEXP_SUBSTR declares the WIDER of its source and its PATTERN. A match can be no longer than the
 * source, so the source alone would be the obvious rule — but the account declares the pattern's width
 * when the pattern is longer, and that is what a CTAS or a view over the call writes down.
 *
 * <p>Arity has nothing to do with it: the same rule holds for two arguments and for six.
 */
public class RegexpSubstrDeclaredWidthTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b VARCHAR(20))");
        engine.execute("INSERT INTO fz VALUES (5, 'x'), (7, 'y')");
    }

    /** The first column of the first row, as text. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** The pattern decides when it is the longer of the two. */
    @Test
    public void theWiderOfSourceAndPatternWins() {
        assertEquals("VARCHAR(5)[LOB]", answer("SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR('abcd', '[a-z]'))"),
            "a four-character source under a five-character pattern");
        assertEquals("VARCHAR(13)[LOB]",
            answer("SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR('ab', '[a-z]{1,3}xyz'))"),
            "the pattern is longer again");
        assertEquals("VARCHAR(10)[LOB]",
            answer("SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR('abcdefghij', '[a-z]'))"),
            "and the source when IT is longer");
        assertEquals("VARCHAR(5)[LOB]", answer("SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR('abcde', '[a-z]'))"),
            "equal widths");
    }

    /** Arity changes nothing — two arguments and six read the same. */
    @Test
    public void arityDoesNotEnterIntoIt() {
        assertEquals("VARCHAR(5)[LOB]", answer("SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR('abcd', '[a-z]', 1))"));
        assertEquals("VARCHAR(5)[LOB]", answer("SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR('abcd', '[a-z]', 1, 2))"));
        assertEquals("VARCHAR(5)[LOB]",
            answer("SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR('abcd', '[a-z]', 1, 2, 'c'))"));
        assertEquals("VARCHAR(7)[LOB]",
            answer("SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR('abcd', '([a-z])', 1, 2, 'ce', 1))"),
            "the six-argument form's pattern is seven characters, and that is its width");
    }

    /** A column source reads the same way, its declared width against the pattern's. */
    @Test
    public void aColumnSourceFollowsTheSameRule() {
        assertEquals("VARCHAR(20)[LOB]",
            answer("SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR(b, '[a-z]', 1, 2)) FROM fz"));
        assertEquals("VARCHAR(28)[LOB]",
            answer("SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR(b, '[a-z]{1,3}xyzxyzxyzxyzxyzxyz')) FROM fz"),
            "a pattern wider than the column");
        assertEquals("VARCHAR(60)[LOB]",
            answer("SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR(UPPER(b), '[a-z]', 1, 2)) FROM fz"),
            "UPPER triples its argument's width, and that is the source here");
    }

    /** A source that is no text is converted first, and nothing bounds the conversion. */
    @Test
    public void aConvertedSourceIsTheWidestText() {
        assertEquals("VARCHAR(134217728)[LOB]",
            answer("SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR(id, '[0-9]', 1, 2)) FROM fz"));
    }
}
