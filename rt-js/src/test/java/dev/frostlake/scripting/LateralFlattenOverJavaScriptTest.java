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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A lateral FLATTEN over a JavaScript function that is not null-safe answers FLATTEN's columns and the rows
 * of the real inputs, and the function is called for the rows alone: a counter it keeps is not advanced an
 * extra time for the item's shape. Every answer is live-verified.
 */
public class LateralFlattenOverJavaScriptTest extends MeasuredCellsTestSupport {

    @Test
    public void aLateralFlattenOverAHandlerThatRefusesNull() {
        assertAnswer(
            "Table W successfully created.",
            "CREATE OR REPLACE TABLE w (id INT, s VARCHAR)");
        assertAnswer(
            "2",
            "INSERT INTO w VALUES (1, 'a,b'), (2, 'c')");
        assertAnswer(
            "Function JSPLIT successfully created.",
            "CREATE OR REPLACE FUNCTION jsplit(S VARCHAR) RETURNS ARRAY LANGUAGE JAVASCRIPT AS 'return S.split(\","
                + "\");'");
        assertAnswer(
            "1|a ;; 1|b ;; 2|c",
            "SELECT w.id, f.value FROM w, LATERAL FLATTEN(INPUT => jsplit(w.s)) f ORDER BY 1, 2");
        assertShape(
            "ID:NUMBER(38,0), S:VARCHAR(16777216), SEQ:NUMBER(38,0), KEY:VARCHAR(134217728), "
                + "PATH:VARCHAR(134217728), INDEX:NUMBER(38,0), VALUE:VARIANT, THIS:VARIANT",
            "SELECT * FROM w, LATERAL FLATTEN(INPUT => jsplit(w.s)) f ORDER BY 1, f.index");
        assertAnswer(
            "Function JCOUNT successfully created.",
            "CREATE OR REPLACE FUNCTION jcount(X FLOAT) RETURNS ARRAY LANGUAGE JAVASCRIPT AS 'globalThis.n = "
                + "(globalThis.n || 0) + 1; return [globalThis.n];'");
        assertAnswer(
            "2",
            "SELECT MAX(f.value::NUMBER) FROM w, LATERAL FLATTEN(INPUT => jcount(w.id::FLOAT)) f");
    }
}
