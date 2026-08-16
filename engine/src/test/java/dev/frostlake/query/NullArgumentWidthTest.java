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
 * The declared WIDTH of a string function whose argument is an untyped NULL, read through a COLUMN.
 *
 * <p>★ THE SURFACE IS THE WHOLE STORY, and measuring the wrong one made this look like a defect it is
 * not. A column DECLARED from such an expression is VARCHAR(16777216) on BOTH engines — through a CTAS
 * and through a view alike, across twenty string functions. The 134217728 live reports for the same
 * expression comes from {@code SYSTEM$TYPEOF}, which names the EXPRESSION's type rather than a
 * column's, and a 128MB expression clamps to the 16MB storage default the moment it becomes a column.
 * Two constants, two surfaces, and only one of them was ever in disagreement.
 *
 * <p>These cells pin the agreeing surface so it cannot drift while the other is fixed: the width of a
 * function over a NULL, the width of the same function over a real VARCHAR(5) — which is computed from
 * the argument and is the control — and the operators beside the calls.
 *
 * <p>Left for its own task: {@code SYSTEM$TYPEOF} itself, which reads the RUNTIME VALUE rather than the
 * declared type, so it answers NULL for every one of these and drops the parameters even where the type
 * is known.
 */
public class NullArgumentWidthTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE nw (i INT, s VARCHAR(5))");
        engine.execute("INSERT INTO nw VALUES (1, 'abc')");
    }

    /** The declared type of a projected expression, read through a VIEW column. */
    private String width(final String expr) {
        engine.execute("CREATE OR REPLACE VIEW nw_v AS SELECT " + expr + " AS c FROM nw");
        final ResultSet rs = engine.executeQuery("DESCRIBE VIEW nw_v");
        return rs.next() ? String.valueOf(rs.getValue(1)) : "<no row>";
    }

    /** ★ Every string function over a NULL declares the 16MB default as a COLUMN. */
    @Test
    public void astringFunctionOverAnullDeclaresTheStorageDefault() {
        assertEquals("VARCHAR(16777216)", width("UPPER(NULL)"));
        assertEquals("VARCHAR(16777216)", width("LOWER(NULL)"));
        assertEquals("VARCHAR(16777216)", width("INITCAP(NULL)"));
        assertEquals("VARCHAR(16777216)", width("TRIM(NULL)"));
        assertEquals("VARCHAR(16777216)", width("REVERSE(NULL)"));
    }

    /** The same for the ones whose width is otherwise COMPUTED from the argument. */
    @Test
    public void thecomputedWidthFunctionsAgreeToo() {
        assertEquals("VARCHAR(16777216)", width("SUBSTR(NULL, 1, 2)"));
        assertEquals("VARCHAR(16777216)", width("LEFT(NULL, 2)"));
        assertEquals("VARCHAR(16777216)", width("RIGHT(NULL, 2)"));
        assertEquals("VARCHAR(16777216)", width("LPAD(NULL, 5, 'x')"));
        assertEquals("VARCHAR(16777216)", width("REPLACE(NULL, 'a', 'b')"));
    }

    /** Concatenation, as an operator and as both function spellings. */
    @Test
    public void concatenationAgreesInAllThreeSpellings() {
        assertEquals("VARCHAR(16777216)", width("NULL || 'x'"));
        assertEquals("VARCHAR(16777216)", width("CONCAT(NULL, 'x')"));
        assertEquals("VARCHAR(16777216)", width("CONCAT_WS('-', NULL, 'x')"));
    }

    /** ★ THE CONTROL: over a real VARCHAR(5) the width IS computed, and the two engines agree there. */
    @Test
    public void overArealColumnTheWidthIsComputed() {
        assertEquals("VARCHAR(15)", width("UPPER(s)"), "the case family widens threefold");
        assertEquals("VARCHAR(5)", width("SUBSTR(s, 1, 2)"));
        assertEquals("VARCHAR(6)", width("s || 'x'"));
        assertEquals("VARCHAR(6)", width("CONCAT(s, 'x')"));
    }

    /** A CAST and a COALESCE over the same NULL, for completeness. */
    @Test
    public void thecastAndConditionalSpellingsAgree() {
        assertEquals("VARCHAR(16777216)", width("NULL::VARCHAR"));
        assertEquals("VARCHAR(16777216)", width("COALESCE(NULL, 'x')"));
    }
}
