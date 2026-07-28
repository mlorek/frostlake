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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * NULL-argument handling at the UDF boundary. Two rules interact (both Snowflake semantics):
 * casting a variant JSON null to a declared OBJECT/ARRAY parameter yields SQL NULL, and a
 * {@code RETURNS NULL ON NULL INPUT} function short-circuits to NULL without entering the body.
 * A JSON-null path leaf ({@code source_type.CloudGroup} = null) passed to a strict Python UDF
 * previously reached the handler as Python {@code None} and crashed on {@code OBJ.items()} — the
 * vendor {@code has_a_non_null_value(OBJ OBJECT)} shape.
 */
public class UdfNullInputTest extends BaseDatabaseTest {

    @BeforeEach
    public void createStrictUdf() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION strict_has_value(OBJ OBJECT)
            RETURNS BOOLEAN
            LANGUAGE PYTHON
            RETURNS NULL ON NULL INPUT
            RUNTIME_VERSION = '3.11'
            HANDLER = 'h'
            AS $$
            def h(OBJ):
                for _, v in OBJ.items():
                    if v != None:
                        return True
                return False
            $$""");
    }

    private Object q(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void strictUdfSkipsJsonNullLeafForObjectParam() {
        assertNull(q("SELECT strict_has_value(PARSE_JSON('{\"source_type\": {\"CloudGroup\": null}}'):source_type.CloudGroup)"));
    }

    @Test
    public void strictUdfSkipsMissingPath() {
        assertNull(q("SELECT strict_has_value(PARSE_JSON('{\"a\": 1}'):missing.path)"));
    }

    @Test
    public void strictUdfSkipsTraversalThroughJsonNull() {
        assertNull(q("SELECT strict_has_value(PARSE_JSON('{\"core\": null}'):core.Host)"));
    }

    @Test
    public void strictUdfRunsOnRealObject() {
        assertEquals(Boolean.TRUE,
            q("SELECT strict_has_value(PARSE_JSON('{\"o\": {\"a\": null, \"b\": 1}}'):o)"));
        assertEquals(Boolean.FALSE,
            q("SELECT strict_has_value(PARSE_JSON('{\"o\": {\"a\": null}}'):o)"));
    }

    @Test
    public void calledOnNullInputStillReceivesNone() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION lenient_probe(V VARIANT)
            RETURNS VARCHAR
            LANGUAGE PYTHON
            RUNTIME_VERSION = '3.11'
            HANDLER = 'h'
            AS $$
            def h(V):
                return 'none' if V is None else 'value'
            $$""");
        // Default CALLED ON NULL INPUT: the handler runs and sees None for a variant null.
        assertEquals("none", q("SELECT lenient_probe(PARSE_JSON('null'))"));
        assertEquals("value", q("SELECT lenient_probe(PARSE_JSON('{\"a\": 1}'))"));
    }
}
