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

package dev.frostlake.scripting;

import dev.frostlake.DatabaseEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A JavaScript handler runs in the account's locked-down sandbox: no Java interop and none of the
 * JVM-flavoured globals ({@code Java}, {@code Packages}, {@code java}, {@code Graal}, {@code print},
 * {@code load}), a {@code console}, and no dynamic code — {@code eval} and {@code new Function} throw.
 * The expected fingerprint is the account's own, measured through the same UDF.
 */
public class JavaScriptSandboxTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    @Test
    public void aHandlerSeesTheAccountsSandbox() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION js_fp() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$
            var r = [];
            r.push('Java=' + typeof Java);
            r.push('Packages=' + typeof Packages);
            r.push('Graal=' + typeof Graal);
            r.push('print=' + typeof print);
            r.push('load=' + typeof load);
            r.push('java=' + typeof java);
            r.push('console=' + typeof console);
            r.push('eval=' + (function () {
                try { return String(eval('({a:1})?.a')); } catch (e) { return e.name + ': ' + e.message; }
            })());
            r.push('Function=' + (function () {
                try { return String(new Function('return 1')()); } catch (e) { return e.name + ': ' + e.message; }
            })());
            return r.join(';');
            $$""");
        assertEquals("Java=undefined;Packages=undefined;Graal=undefined;print=undefined;load=undefined;"
                + "java=undefined;console=object;eval=EvalError: Dynamic code evaluation disallowed;"
                + "Function=EvalError: Dynamic code evaluation disallowed",
            engine.executeQuery("SELECT js_fp()").getRows().get(0).getValue(0));
    }
}
