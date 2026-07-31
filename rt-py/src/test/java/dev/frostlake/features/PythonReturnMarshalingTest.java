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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Python UDF returning a dict/list yields a VARIANT, not a raw guest-language object — the
 * value must behave as JSON downstream (path access, DISTINCT hashing, array indexing).
 */
public class PythonReturnMarshalingTest extends BaseDatabaseTest {

    private ResultSet q(final String sql) {
        return engine.executeQuery(sql);
    }

    private String scalar(final String sql) {
        final Object v = q(sql).getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }

    @Test
    public void pythonDictReturnBecomesAVariant() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION pyobj(k VARCHAR) RETURNS VARIANT
            LANGUAGE PYTHON RUNTIME_VERSION='3.11' HANDLER='h' AS $$
            def h(k):
                return {'a': k, 'n': 2}
            $$""");
        assertEquals("x", scalar("SELECT pyobj('x'):a::VARCHAR"));
        // PyObject.hashCode() delegates to Python __hash__, so any hash-based stage used to raise
        // "unhashable type: 'dict'".
        assertEquals(1, q("SELECT DISTINCT pyobj('x')").getRowCount());
    }

    @Test
    public void pythonListReturnBecomesAVariantArray() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION pylist(k VARCHAR) RETURNS VARIANT
            LANGUAGE PYTHON RUNTIME_VERSION='3.11' HANDLER='h' AS $$
            def h(k):
                return [k, 1, True]
            $$""");
        assertEquals("[\"x\",1,true]", scalar("SELECT pylist('x')"));
        assertEquals("x", scalar("SELECT pylist('x')[0]::VARCHAR"));
    }
}
