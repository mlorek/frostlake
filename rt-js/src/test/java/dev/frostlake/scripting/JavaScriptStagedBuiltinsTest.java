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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The builtins a JavaScript handler finds are the account's engine's: the ones a newer language edition adds are
 * there, the ones no browser engine has are not, and neither are the host's own. Every cell is live-verified.
 */
public class JavaScriptStagedBuiltinsTest extends BaseDatabaseTest {

    /** An expression, and its text. */
    private static final String[][] VALUES = {
            {"typeof Error.isError",
                "function"},
            {"typeof Iterator",
                "function"},
            {"typeof Iterator.prototype.map",
                "function"},
            {"typeof Promise.withResolvers",
                "function"},
            {"typeof Promise.try",
                "function"},
            {"typeof Object.groupBy",
                "function"},
            {"typeof Array.fromAsync",
                "function"},
            {"typeof Set.prototype.union",
                "function"},
            {"typeof RegExp.escape",
                "function"},
            {"typeof Math.sumPrecise",
                "function"},
            {"typeof Float16Array",
                "function"},
            {"typeof Uint8Array.fromBase64",
                "function"},
            {"typeof Temporal",
                "undefined"},
            {"typeof Atomics.pause",
                "function"},
            {"typeof JSON.rawJSON",
                "function"},
            {"typeof String.prototype.isWellFormed",
                "function"},
            {"typeof Array.prototype.toSorted",
                "function"},
            {"typeof structuredClone",
                "undefined"},
            {"typeof queueMicrotask",
                "undefined"},
            {"typeof setTimeout",
                "undefined"},
            {"typeof SharedArrayBuffer",
                "function"},
            {"typeof WeakRef",
                "function"},
            {"typeof FinalizationRegistry",
                "function"},
            {"typeof Intl.Segmenter",
                "function"},
            {"typeof Intl.DurationFormat",
                "function"},
            {"typeof ShadowRealm",
                "undefined"},
            {"typeof Intl.Locale",
                "function"},
            {"typeof Iterator.concat",
                "function"},
            {"typeof Math.f16round",
                "function"},
            {"typeof Error.captureStackTrace",
                "function"},
            {"Error.stackTraceLimit",
                "10"},
            {"typeof Array.prototype.group",
                "undefined"},
            {"typeof Map.groupBy",
                "function"},
            {"typeof String.prototype.at",
                "function"},
            {"typeof Object.hasOwn",
                "function"},
            {"typeof AggregateError",
                "function"},
            {"typeof escape",
                "function"},
            {"typeof globalThis.global",
                "undefined"},
            {"typeof require",
                "undefined"},
            {"typeof process",
                "undefined"},
            {"typeof Buffer",
                "undefined"},
            {"typeof print",
                "undefined"},
            {"typeof load",
                "undefined"},
            {"typeof console",
                "object"},
            {"typeof unescape",
                "function"},
            {"typeof escape",
                "function"},
            {"Object.getOwnPropertyNames(class C {}).join()",
                "length,name,prototype"},
            {"Object.getOwnPropertyNames(() => 1).join()",
                "length,name"},
            {"(function(){ 'use strict'; return Object.getOwnPropertyNames(function h(){}).join(); })()",
                "length,name,prototype"},
            {"Array.from(5).length",
                "0"},
            {"typeof String.dedent",
                "undefined"},
    };

    private static final String TRY = "function t(f){try{return String(f())}catch(e){return 'THREW '+(e&&e.name)+': '"
        + "+(e&&e.message)+' / '+(typeof e)}}\n";

    /** The only cell of a one-row answer. */
    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    /** Each expression, evaluated inside a handler, has the given text, or throws what the text tells. */
    private void assertValues(final String[][] cases) {
        for (int from = 0; from < cases.length; from += 12) {
            final int to = Math.min(cases.length, from + 12);
            final StringBuilder body = new StringBuilder(TRY).append("return [");
            for (int i = from; i < to; i++) {
                body.append(i > from ? ",\n" : "").append("t(function(){ return ").append(cases[i][0]).append("; })");
            }
            engine.execute("CREATE OR REPLACE FUNCTION ev() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$"
                + body.append("].join(' ;; ');") + "$$");
            final String[] cells = cell("SELECT ev()").split(" ;; ", -1);
            assertEquals(to - from, cells.length, String.join(" ;; ", cells));
            for (int i = from; i < to; i++) {
                assertEquals(cases[i][1], cells[i - from], cases[i][0]);
            }
        }
    }

    @Test
    public void aHandlerFindsTheAccountsBuiltins() {
        assertValues(VALUES);
    }
}
