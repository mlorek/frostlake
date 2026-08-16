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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An OBJECT_CONSTRUCT key must be TEXT, and live names the offending DECLARED type:
 *
 * <pre>
 *   OBJECT_CONSTRUCT(v::VARIANT, x)   Function OBJECT_CONSTRUCT does not support VARIANT argument
 *                                     type for keys
 *   OBJECT_CONSTRUCT(n, x)            … NUMBER(38,0) …
 *   OBJECT_CONSTRUCT(b, x)            … BOOLEAN …           OBJECT_CONSTRUCT(d, x)   … DATE …
 * </pre>
 *
 * <p>Frostlake accepted every one of them, building keys out of whatever the value rendered to. Only
 * the KEY positions are asked — a VARIANT VALUE is ordinary — and this is NOT the rule OBJECT_AGG
 * follows: that one takes a VARIANT key and uses its text. The two functions genuinely diverge, and
 * both halves are measured (see ObjectAggVariantKeyTest for the other).
 */
public class ObjectConstructKeyTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE kv (k VARCHAR(20), v VARCHAR(20), n NUMBER, d DATE,"
            + " b BOOLEAN, va VARIANT)");
        engine.execute("INSERT INTO kv SELECT 'WIDGET', 's-1', 7, '2026-01-01', TRUE,"
            + " TO_VARIANT('x')");
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    private String keyRefusal(final String expression) {
        return refusal("SELECT " + expression + " AS c FROM kv");
    }

    /** Every non-text key is refused, named by its declared type. */
    @Test
    public void aNonTextKeyIsRefusedByItsType() {
        assertEquals("SQL compilation error: Function OBJECT_CONSTRUCT does not support VARIANT"
            + " argument type for keys", keyRefusal("OBJECT_CONSTRUCT(va, v)"));
        assertEquals("SQL compilation error: Function OBJECT_CONSTRUCT does not support VARIANT"
            + " argument type for keys", keyRefusal("OBJECT_CONSTRUCT(k::VARIANT, v)"));
        assertEquals("SQL compilation error: Function OBJECT_CONSTRUCT does not support NUMBER(38,0)"
            + " argument type for keys", keyRefusal("OBJECT_CONSTRUCT(n, v)"));
        assertEquals("SQL compilation error: Function OBJECT_CONSTRUCT does not support BOOLEAN"
            + " argument type for keys", keyRefusal("OBJECT_CONSTRUCT(b, v)"));
        assertEquals("SQL compilation error: Function OBJECT_CONSTRUCT does not support DATE"
            + " argument type for keys", keyRefusal("OBJECT_CONSTRUCT(d, v)"));
    }

    /** Any pair triggers it, and the KEEP_NULL spelling reports under its own name. */
    @Test
    public void anyPairAndBothSpellings() {
        assertEquals("SQL compilation error: Function OBJECT_CONSTRUCT does not support VARIANT"
            + " argument type for keys", keyRefusal("OBJECT_CONSTRUCT('a', 1, k::VARIANT, 2)"));
        assertEquals("SQL compilation error: Function OBJECT_CONSTRUCT_KEEP_NULL does not support"
            + " VARIANT argument type for keys",
            keyRefusal("OBJECT_CONSTRUCT_KEEP_NULL(k::VARIANT, v)"));
    }

    /** A TEXT key is fine, and so is a VARIANT VALUE — only the key positions are asked. */
    @Test
    public void textKeysAndVariantValuesAreFine() {
        assertEquals("accepted", keyRefusal("OBJECT_CONSTRUCT(k, v)"));
        assertEquals("accepted", keyRefusal("OBJECT_CONSTRUCT('a', v)"));
        assertEquals("accepted", keyRefusal("OBJECT_CONSTRUCT('a', va)"));
        assertEquals("accepted", keyRefusal("OBJECT_CONSTRUCT('a', v::VARIANT)"));
        assertEquals("accepted", keyRefusal("OBJECT_CONSTRUCT(*)"));
    }

    /** And a VIEW over the same body is refused too, rather than created. */
    @Test
    public void aViewOverItIsRefused() {
        assertEquals("SQL compilation error: Function OBJECT_CONSTRUCT does not support VARIANT"
            + " argument type for keys",
            refusal("CREATE OR REPLACE VIEW kv_v AS SELECT OBJECT_CONSTRUCT(k::VARIANT, v) AS c"
                + " FROM kv"));
    }
}
