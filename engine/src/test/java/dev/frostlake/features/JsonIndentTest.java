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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JSON_INDENT sets how wide a semi-structured value is displayed: two spaces on a real account by
 * default, compact at zero. This engine's own default is COMPACT — that is its canonical rendering,
 * and the live-comparison harness pins the server to JSON_INDENT = 0 so both sides emit the same
 * text — but the parameter itself behaves as live's does, and the indented form is byte-identical
 * to a real account's at the same width.
 */
public class JsonIndentTest extends BaseDatabaseTest {

    @AfterEach
    public void resetIndent() {
        engine.execute("ALTER SESSION UNSET JSON_INDENT");
    }

    private String displayed(final String expression) {
        final ResultSet rs = engine.executeQuery("SELECT " + expression);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    /**
     * The live-comparison harness re-serialises every array and object to its canonical compact
     * text so that value comparisons line up, which erases the very whitespace these cases are
     * about. The width still reaches a real account — the harness just cannot show the result.
     */
    private void skipWhereIndentationIsNormalisedAway() {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "the comparison harness re-serialises arrays and objects to compact text");
    }

    @Test
    public void theEngineDisplaysCompactByDefault() {
        assertEquals("[\"a\",\"b\"]", displayed("ARRAY_CONSTRUCT('a','b')"));
        assertEquals("{\"k\":\"v\"}", displayed("OBJECT_CONSTRUCT('k','v')"));
    }

    /** At width two the text matches a real account's byte for byte. */
    @Test
    public void indentTwoMatchesTheLiveRendering() {
        skipWhereIndentationIsNormalisedAway();
        engine.execute("ALTER SESSION SET JSON_INDENT = 2");
        assertEquals("[\n  \"a\",\n  \"b\"\n]", displayed("ARRAY_CONSTRUCT('a','b')"));
        assertEquals("{\n  \"k\": \"v\"\n}", displayed("OBJECT_CONSTRUCT('k','v')"));
        // A nested array indents one level further, and its closing bracket sits at the parent's.
        assertEquals("[\n  [\n    \"x\"\n  ]\n]",
            displayed("ARRAY_CONSTRUCT(ARRAY_CONSTRUCT('x'))"));
    }

    @Test
    public void theWidthIsWhateverTheSessionSets() {
        skipWhereIndentationIsNormalisedAway();
        engine.execute("ALTER SESSION SET JSON_INDENT = 4");
        assertEquals("[\n    \"a\",\n    \"b\"\n]", displayed("ARRAY_CONSTRUCT('a','b')"));
        engine.execute("ALTER SESSION SET JSON_INDENT = 0");
        assertEquals("[\"a\",\"b\"]", displayed("ARRAY_CONSTRUCT('a','b')"));
    }

    /** An empty array or object stays on one line at every width. */
    @Test
    public void anEmptyContainerIsNeverSplitAcrossLines() {
        engine.execute("ALTER SESSION SET JSON_INDENT = 2");
        assertEquals("[]", displayed("ARRAY_CONSTRUCT()"));
        assertEquals("{}", displayed("OBJECT_CONSTRUCT()"));
    }

    /** An explicit conversion is always compact on live, whatever the width is set to. */
    @Test
    public void toVarcharStaysCompactAtEveryWidth() {
        engine.execute("ALTER SESSION SET JSON_INDENT = 2");
        assertEquals("[\"a\"]", displayed("TO_VARCHAR(ARRAY_CONSTRUCT('a'))"));
        engine.execute("ALTER SESSION SET JSON_INDENT = 4");
        assertEquals("[\"a\"]", displayed("TO_VARCHAR(ARRAY_CONSTRUCT('a'))"));
    }

    /** UNSET returns the session to the engine's default. */
    @Test
    public void unsettingRestoresTheDefault() {
        skipWhereIndentationIsNormalisedAway();
        engine.execute("ALTER SESSION SET JSON_INDENT = 2");
        assertEquals("[\n  \"a\"\n]", displayed("ARRAY_CONSTRUCT('a')"));
        engine.execute("ALTER SESSION UNSET JSON_INDENT");
        assertEquals("[\"a\"]", displayed("ARRAY_CONSTRUCT('a')"));
    }

    /** The parameter is reported with live's own default and description. */
    @Test
    public void showParametersReportsJsonIndent() {
        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS LIKE 'JSON_INDENT'");
        assertEquals(1, rs.getRows().size());
        assertEquals("JSON_INDENT", rs.getRows().get(0).getValue(0));
        assertEquals("2", rs.getRows().get(0).getValue(2));
        assertEquals("Width of indentation in JSON output (0 for compact)",
            rs.getRows().get(0).getValue(4));
        assertEquals("NUMBER", rs.getRows().get(0).getValue(5));
    }
}
