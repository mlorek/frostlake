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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A SQL function whose body is a block binds each argument to its parameter's declared type, an exact number
 * rounded to the parameter's scale, where an expression body keeps the caller's number and its type, a NUMBER of
 * scale 37 included (all live-verified).
 */
public class SqlFunctionParameterBindingTest extends MeasuredCellsTestSupport {

    @Test
    public void aBlockConvertsWhereAnExpressionKeeps() {
        assertAnswer("Function SB successfully created.",
            "CREATE OR REPLACE FUNCTION sb(x NUMBER(10,2)) RETURNS VARCHAR AS $$ BEGIN RETURN x::VARCHAR; END $$");
        assertAnswer("1.00", "SELECT sb(1)");
        assertAnswer("1.24", "SELECT sb(1.239)");
        assertAnswer("Function SN successfully created.",
            "CREATE OR REPLACE FUNCTION sn(x NUMBER) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(x)'");
        assertAnswer("NUMBER(38,37)[SB16]", "SELECT sn(1.5::NUMBER(38,37))");
        assertAnswer("NUMBER(38,30)[SB16]", "SELECT sn(1::NUMBER(38,30))");
        assertAnswer("Function SD successfully created.",
            "CREATE OR REPLACE FUNCTION sd(x NUMBER(38,10)) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(x) || x::VARCHAR'");
        assertAnswer("NUMBER(38,37)[SB16]1.5000000000000000000000000000000000000", "SELECT sd(1.5::NUMBER(38,37))");
    }
}
