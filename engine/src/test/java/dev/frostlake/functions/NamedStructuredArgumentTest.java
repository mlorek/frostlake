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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A call that names its arguments binds a structured one by the rule a positional call does: only a value of the
 * parameter's own shape, so a plain OBJECT or ARRAY, another field or another element type fits no signature, and the
 * call is refused as named arguments that match none (live-verified). A ROW given to a named parameter is refused
 * naming the function as the call writes it, with its schema and its database.
 */
public class NamedStructuredArgumentTest extends MeasuredCellsTestSupport {

    /** Structured OBJECT and ARRAY parameters called by position and by name. */
    @Test
    public void aNamedStructuredArgumentBindsOnlyItsOwnShape() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION nsf(o OBJECT(x VARCHAR)) RETURNS VARCHAR AS $$ o['x']::VARCHAR $$",
                "Function NSF successfully created."},
            {"SELECT nsf({'x': 'a'}::OBJECT(x VARCHAR))",
                "a"},
            {"SELECT nsf(o => {'x': 'a'}::OBJECT(x VARCHAR))",
                "a"},
            {"SELECT nsf({'y': 'a'}::OBJECT(y VARCHAR))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NSF': (OBJECT(y VARCHAR(134217728)))"},
            {"SELECT nsf(o => {'y': 'a'}::OBJECT(y VARCHAR))",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [O] do not match any "
                + "signature for function NSF"},
            {"SELECT nsf({'x': 'a'})",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NSF': (OBJECT)"},
            {"SELECT nsf(o => {'x': 'a'})",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [O] do not match any "
                + "signature for function NSF"},
            {"SELECT nsf(o => {'x': 1}::OBJECT(x INT))",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [O] do not match any "
                + "signature for function NSF"},
            {"SELECT nsf({'x': 1}::OBJECT(x INT))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NSF': (OBJECT(x NUMBER(38,0)))"},
            {"CREATE OR REPLACE FUNCTION nsa(a ARRAY(INT)) RETURNS INT AS $$ ARRAY_SIZE(a) $$",
                "Function NSA successfully created."},
            {"SELECT nsa([1, 2]::ARRAY(INT)), nsa(a => [1, 2]::ARRAY(INT))",
                "2|2"},
            {"SELECT nsa(a => ['x']::ARRAY(VARCHAR))",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [A] do not match any "
                + "signature for function NSA"},
            {"SELECT nsa(['x']::ARRAY(VARCHAR))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NSA': (ARRAY(VARCHAR(134217728)))"},
            {"SELECT nsa(a => [1, 2])",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [A] do not match any "
                + "signature for function NSA"},
            {"SELECT nsa([1, 2])",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NSA': (ARRAY)"},
        });
    }

    /** A ROW given to a named parameter, the function written bare, with its schema and with its database. */
    @Test
    public void aRowArgumentsRefusalNamesTheFunctionAsWritten() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION rowf(a INT, b INT) RETURNS INT AS $$ a + b $$",
                "Function ROWF successfully created."},
            {"SELECT rowf(a => (1, 2), b => 3)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [A, B] do not match any "
                + "signature for function ROWF"},
            {"SELECT test_schema.rowf(a => (1, 2), b => 3)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [A, B] do not match any "
                + "signature for function TEST_SCHEMA.ROWF"},
            {"SELECT test_db.test_schema.rowf(a => (1, 2), b => 3)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [A, B] do not match any "
                + "signature for function TEST_DB.TEST_SCHEMA.ROWF"},
            {"SELECT test_schema.rowf(a => 1, z => 3)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [A, Z] do not match any "
                + "signature for function TEST_SCHEMA.ROWF"},
            {"SELECT test_db.test_schema.rowf(a => 1, z => 3)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [A, Z] do not match any "
                + "signature for function TEST_DB.TEST_SCHEMA.ROWF"},
            {"SELECT TEST_SCHEMA.ROWF(a => (1, 2), b => 3)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [A, B] do not match any "
                + "signature for function TEST_SCHEMA.ROWF"},
        });
    }
}
