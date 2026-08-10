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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A VARIANT key contributes its VALUE to the object, not its JSON rendering. Frostlake read the
 * variant's own text, so {@code OBJECT_AGG(k::VARIANT, v)} spelled the key {@code "\"WIDGET_ONE\""} —
 * quotes inside the name — and every later lookup or comparison against that object failed.
 *
 * <p>The cell that mattered most was the CONDITIONAL key, and it was invisible for a while: a
 * conditional whose declared type was UNDETERMINED took a different path and came out right by
 * accident. Give the conditional a declared type — which is what the branch fold does — and the key
 * joined the broken ones. So a metadata-only change surfaced a VALUE bug that had been there all
 * along, in a shape no test covered.
 */
public class ObjectAggVariantKeyTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE kv (k VARCHAR(20), v VARCHAR(20))");
        engine.execute("INSERT INTO kv SELECT 'WIDGET_ONE', 's-1'");
    }

    private String objectOf(final String keyExpression) {
        final ResultSet rs = engine.executeQuery(
            "SELECT OBJECT_AGG(" + keyExpression + ", v::VARIANT) AS c FROM kv");
        rs.next();
        return String.valueOf(rs.getValue("c"));
    }

    /** A column, a literal and an expression cast to VARIANT all key by their TEXT. */
    @Test
    public void aVariantKeyContributesItsText() {
        assertEquals("{\"WIDGET_ONE\":\"s-1\"}", objectOf("k::VARIANT"));
        assertEquals("{\"lit\":\"s-1\"}", objectOf("'lit'::VARIANT"));
        assertEquals("{\"widget_one_x\":\"s-1\"}", objectOf("(LOWER(k) || '_x')::VARIANT"));
    }

    /** Including a CONDITIONAL key — the shape that only breaks once the branches fold to a type. */
    @Test
    public void aConditionalVariantKeyDoesToo() {
        assertEquals("{\"widget_one_agent_id\":\"s-1\"}", objectOf(
            "CASE WHEN k = 'X' THEN 'other' ELSE LOWER(k) || '_agent_id' END::VARIANT"));
        assertEquals("{\"widget_one_agent_id\":\"s-1\"}", objectOf(
            "IFF(k = 'X', 'other', LOWER(k) || '_agent_id')::VARIANT"));
    }

    /** An uncast key is unchanged — the fix must not disturb the ordinary spelling. */
    @Test
    public void aPlainKeyIsUntouched() {
        assertEquals("{\"WIDGET_ONE\":\"s-1\"}", objectOf("k"));
        assertEquals("{\"widget_one_agent_id\":\"s-1\"}", objectOf(
            "CASE WHEN k = 'X' THEN 'other' ELSE LOWER(k) || '_agent_id' END"));
    }
}
