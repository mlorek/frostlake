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
 * A Python table function that refuses a NULL argument, called laterally over rows that hold none, answers
 * the columns its RETURNS TABLE declares and the rows of the real inputs - written TABLE(f(...)) or LATERAL
 * f(...) - and SELECT * lists them. The handler is not run for the item's shape. A row that does hold a NULL
 * raises the handler's own exception. Every answer is live-verified.
 */
public class LateralPythonTableFunctionTest extends MeasuredCellsTestSupport {

    @Test
    public void aHandlerThatRefusesNullKeepsItsColumns() {
        assertAnswer(
            "Table EV successfully created.",
            "CREATE OR REPLACE TABLE ev (v INT)");
        assertAnswer(
            "2",
            "INSERT INTO ev VALUES (1), (2)");
        assertAnswer(
            "Function PY_STRICT successfully created.",
            "CREATE OR REPLACE FUNCTION py_strict(v INT) RETURNS TABLE (o INT) LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'H' AS $$\nclass H:\n    def process(self, v):\n        if v is None:\n          "
                + "  raise ValueError('none given')\n        yield (v * 10,)\n$$");
        assertAnswer(
            "1|10 ;; 2|20",
            "SELECT ev.v, f.o FROM ev, TABLE(py_strict(ev.v)) f ORDER BY 1");
        assertShape(
            "V:NUMBER(38,0), O:NUMBER(38,0)",
            "SELECT * FROM ev, TABLE(py_strict(ev.v)) f ORDER BY 1");
        assertAnswer(
            "1|10 ;; 2|20",
            "SELECT * FROM ev, TABLE(py_strict(ev.v)) f ORDER BY 1");
        assertAnswer(
            "1|10 ;; 2|20",
            "SELECT ev.v, f.o FROM ev, LATERAL py_strict(ev.v) f ORDER BY 1");
        assertAnswer(
            "1",
            "INSERT INTO ev VALUES (NULL)");
        answer(
            "SELECT ev.v, f.o FROM ev, TABLE(py_strict(ev.v)) f ORDER BY 1");
    }
}
