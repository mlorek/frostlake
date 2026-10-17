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

package dev.frostlake.procedures;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * What a CALL argument becomes in its NUMBER parameter (live-verified): a value too wide is refused at the
 * parameter's scale under the storage class that value needs - value 123456789.00, FIXED[SB8](5,2) - a FLOAT by its
 * own text and not null, a text as the text it is; $1 alone binds NULL and $2 is an invalid identifier.
 */
public class CallArgumentValueTest extends MeasuredCellsTestSupport {

    @Test
    public void positionalBooleanAndRangeArguments() {
        engine.execute("CREATE OR REPLACE PROCEDURE qe(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "NVL(x, 'none'); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE qb2(x BOOLEAN) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "x::VARCHAR; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE qn(x NUMBER(5,2)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
            + "RETURN x::VARCHAR; END; $$");
        assertCells(new String[][] {
            {"CALL qe($1)", "none"},
            {"CALL qe($2)", "REFUSED SQL compilation error: error line 1 at position 8 | invalid identifier '$2'"},
            {"CALL qe(TRUE)", "true"},
            {"CALL qb2(NOT TRUE)", "false"},
            {"CALL qb2(NOT NOT TRUE)", "true"},
            {"SELECT $1", "null"},
            {"CALL qn(123456789)",
                "REFUSED Number out of representable range: type FIXED[SB8](5,2){nullable}, value 123456789.00"},
            {"CALL qn(1234.5)",
                "REFUSED Number out of representable range: type FIXED[SB4](5,2){nullable}, value 1234.50"},
            {"CALL qn(999.999)",
                "REFUSED Number out of representable range: type FIXED[SB4](5,2){nullable}, value 999.999"},
            {"CALL qn(-123456789.5)",
                "REFUSED Number out of representable range: type FIXED[SB8](5,2){nullable}, value -123456789.50"},
            {"CALL qn(12345678901234567890)",
                "REFUSED Number out of representable range: type FIXED[SB16](5,2){nullable}, value "
                + "12345678901234567890.00"},
        });
    }

    @Test
    public void aNumberTooWideIsRefusedAtTheParameterScale() {
        engine.execute("CREATE OR REPLACE PROCEDURE pr(x VARIANT) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE py(x ARRAY) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pv(x VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pk(x NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pb(x BOOLEAN) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pdn(a DATE, b TIMESTAMP_NTZ) RETURNS NUMBER LANGUAGE SQL AS $$ "
            + "BEGIN RETURN 1; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pnd(a TIMESTAMP_NTZ, b DATE) RETURNS NUMBER LANGUAGE SQL AS $$ "
            + "BEGIN RETURN 1; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE qn(x NUMBER(5,2)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
            + "RETURN x::VARCHAR; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE qn10(x NUMBER(10,0)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
            + "RETURN x::VARCHAR; END; $$");
        assertCells(new String[][] {
            {"CALL qn(1000)",
                "REFUSED Number out of representable range: type FIXED[SB4](5,2){nullable}, value 1000.00"},
            {"CALL qn(99999)",
                "REFUSED Number out of representable range: type FIXED[SB4](5,2){nullable}, value 99999.00"},
            {"CALL qn(-1000.555)",
                "REFUSED Number out of representable range: type FIXED[SB4](5,2){nullable}, value -1000.555"},
            {"CALL qn(0.001)", "0.00"},
            {"CALL qn(1000::FLOAT)",
                "REFUSED Number out of representable range: type FIXED[SB4](5,2){not null}, value 1000"},
            {"CALL qn('1000')", "REFUSED Numeric value '1000' is out of range"},
            {"CALL qn10(12345678901)",
                "REFUSED Number out of representable range: type FIXED[SB8](10,0){nullable}, value 12345678901"},
            {"CALL qn10(1.5)", "2"},
        });
    }
}
