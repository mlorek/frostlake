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
 * A CALL's arguments against its procedure's parameter types, judged while the CALL compiles (live-verified): an
 * ARRAY, an OBJECT and a BINARY for a DATE, BOOLEAN, FLOAT, timestamp or TIME parameter are refused by their types,
 * as are a BOOLEAN, a FLOAT, a DATE, a timestamp and a TIME for an OBJECT, ARRAY or BINARY one; a TIME for a
 * timestamp parameter has a sentence of its own unless another argument is refused; a predicate is refused by every
 * parameter but a BOOLEAN one, where a BOOLEAN value binds; NOT and AND over values are arguments too, and $1 alone
 * binds NULL.
 */
public class CallArgumentTypePairTest extends MeasuredCellsTestSupport {

    @Test
    public void everyPairTheAccountRefusesIsRefusedWhileTheCallCompiles() {
        engine.execute("CREATE OR REPLACE PROCEDURE pd(x DATE) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pb(x BOOLEAN) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pf(x FLOAT) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pn(x TIMESTAMP_NTZ) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
            + "RETURN 1; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pl(x TIMESTAMP_LTZ) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
            + "RETURN 1; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pz(x TIMESTAMP_TZ) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
            + "RETURN 1; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm(x TIME) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE po(x OBJECT) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE py(x ARRAY) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE px(x BINARY) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pv(x VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr(x VARIANT) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pk(x NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        assertCells(new String[][] {
            {"CALL pd(ARRAY_CONSTRUCT(1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PD': (ARRAY)"},
            {"CALL pd(OBJECT_CONSTRUCT('k', 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PD': (OBJECT)"},
            {"CALL pd(TO_BINARY('ab', 'HEX'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PD': (BINARY(67108864))"},
            {"CALL pb(ARRAY_CONSTRUCT(1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PB': (ARRAY)"},
            {"CALL pb(OBJECT_CONSTRUCT('k', 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PB': (OBJECT)"},
            {"CALL pb(TO_BINARY('ab', 'HEX'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PB': (BINARY(67108864))"},
            {"CALL pf(ARRAY_CONSTRUCT(1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PF': (ARRAY)"},
            {"CALL pf(OBJECT_CONSTRUCT('k', 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PF': (OBJECT)"},
            {"CALL pf(TO_BINARY('ab', 'HEX'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PF': (BINARY(67108864))"},
            {"CALL pf('10:11:12'::TIME)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PF': (TIME(9))"},
            {"CALL pn(ARRAY_CONSTRUCT(1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PN': (ARRAY)"},
            {"CALL pn(OBJECT_CONSTRUCT('k', 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PN': (OBJECT)"},
            {"CALL pn(TO_BINARY('ab', 'HEX'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PN': (BINARY(67108864))"},
            {"CALL pn('10:11:12'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"CALL pl(ARRAY_CONSTRUCT(1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PL': (ARRAY)"},
            {"CALL pl(OBJECT_CONSTRUCT('k', 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PL': (OBJECT)"},
            {"CALL pl(TO_BINARY('ab', 'HEX'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PL': (BINARY(67108864))"},
            {"CALL pl('10:11:12'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_LTZ(9)]"},
            {"CALL pz(ARRAY_CONSTRUCT(1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PZ': (ARRAY)"},
            {"CALL pz(OBJECT_CONSTRUCT('k', 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PZ': (OBJECT)"},
            {"CALL pz(TO_BINARY('ab', 'HEX'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PZ': (BINARY(67108864))"},
            {"CALL pz('10:11:12'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]"},
            {"CALL pm(ARRAY_CONSTRUCT(1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PM': (ARRAY)"},
            {"CALL pm(OBJECT_CONSTRUCT('k', 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PM': (OBJECT)"},
            {"CALL pm(TO_BINARY('ab', 'HEX'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PM': (BINARY(67108864))"},
            {"CALL pm((1 = 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PM': (BOOLEAN)"},
            {"CALL pm(1.5::FLOAT)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PM': (FLOAT)"},
            {"CALL pm('2024-01-15 10:11:12'::TIMESTAMP_NTZ)", "1"},
            {"CALL pm('2024-01-15 10:11:12'::TIMESTAMP_LTZ)", "1"},
            {"CALL pm('2024-01-15 10:11:12 +01:00'::TIMESTAMP_TZ)", "1"},
            {"CALL po((1 = 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PO': (BOOLEAN)"},
            {"CALL po(1.5::FLOAT)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PO': (FLOAT)"},
            {"CALL po('2024-01-15'::DATE)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PO': (DATE)"},
            {"CALL po('2024-01-15 10:11:12'::TIMESTAMP_NTZ)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PO': (TIMESTAMP_NTZ(9))"},
            {"CALL po('2024-01-15 10:11:12'::TIMESTAMP_LTZ)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PO': (TIMESTAMP_LTZ(9))"},
            {"CALL po('2024-01-15 10:11:12 +01:00'::TIMESTAMP_TZ)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PO': (TIMESTAMP_TZ(9))"},
            {"CALL po('10:11:12'::TIME)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PO': (TIME(9))"},
            {"CALL po(TO_BINARY('ab', 'HEX'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PO': (BINARY(67108864))"},
            {"CALL py((1 = 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY': (BOOLEAN)"},
            {"CALL py(1.5::FLOAT)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY': (FLOAT)"},
            {"CALL py('2024-01-15'::DATE)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY': (DATE)"},
            {"CALL py('2024-01-15 10:11:12'::TIMESTAMP_NTZ)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY': (TIMESTAMP_NTZ(9))"},
            {"CALL py('2024-01-15 10:11:12'::TIMESTAMP_LTZ)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY': (TIMESTAMP_LTZ(9))"},
            {"CALL py('2024-01-15 10:11:12 +01:00'::TIMESTAMP_TZ)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY': (TIMESTAMP_TZ(9))"},
            {"CALL py('10:11:12'::TIME)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY': (TIME(9))"},
            {"CALL px((1 = 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PX': (BOOLEAN)"},
            {"CALL px(1.5::FLOAT)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PX': (FLOAT)"},
            {"CALL px('2024-01-15'::DATE)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PX': (DATE)"},
            {"CALL px('2024-01-15 10:11:12'::TIMESTAMP_NTZ)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PX': (TIMESTAMP_NTZ(9))"},
            {"CALL px('2024-01-15 10:11:12'::TIMESTAMP_LTZ)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PX': (TIMESTAMP_LTZ(9))"},
            {"CALL px('2024-01-15 10:11:12 +01:00'::TIMESTAMP_TZ)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PX': (TIMESTAMP_TZ(9))"},
            {"CALL px('10:11:12'::TIME)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PX': (TIME(9))"},
            {"CALL px(ARRAY_CONSTRUCT(1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PX': (ARRAY)"},
            {"CALL px(OBJECT_CONSTRUCT('k', 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PX': (OBJECT)"},
            {"CALL pv((1 = 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PV': (BOOLEAN)"},
            {"CALL pv('10:11:12'::TIME)", "1"},
            {"CALL pv(1.5::FLOAT)", "1"},
            {"CALL pv('2024-01-15'::DATE)", "1"},
            {"CALL pr((1 = 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PR': (BOOLEAN)"},
            {"CALL pr('10:11:12'::TIME)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PR': (TIME(9))"},
            {"CALL pr(1.5::FLOAT)", "1"},
            {"CALL pr('2024-01-15 10:11:12'::TIMESTAMP_NTZ)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PR': (TIMESTAMP_NTZ(9))"},
            {"CALL pr(TO_BINARY('ab', 'HEX'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PR': (BINARY(67108864))"},
            {"CALL pv(TRUE)", "1"},
            {"CALL pv(FALSE)", "1"},
            {"CALL pv(NOT TRUE)", "1"},
            {"CALL pv(1 = 1)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PV': (BOOLEAN)"},
            {"CALL pv(TRUE AND FALSE)", "1"},
            {"CALL pv(TRUE::BOOLEAN)", "1"},
            {"CALL pv(CURRENT_USER() IS NULL)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PV': (BOOLEAN)"},
            {"CALL pk(TRUE)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PK': (BOOLEAN)"},
            {"CALL pb(NOT FALSE)", "1"},
            {"CALL pb(NOT (1 = 2))", "1"},
            {"CALL pk($1)", "1"},
            {"CALL pk($2)", "REFUSED SQL compilation error: error line 1 at position 8 | invalid identifier '$2'"},
            {"CALL pv($1)", "1"},
        });
    }

    @Test
    public void predicatesTimesAndBooleanArguments() {
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
            {"CALL pr('2024-01-15 10:11:12'::TIMESTAMP_LTZ)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PR': (TIMESTAMP_LTZ(9))"},
            {"CALL pr('2024-01-15 10:11:12 +01:00'::TIMESTAMP_TZ)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PR': (TIMESTAMP_TZ(9))"},
            {"CALL py(TRUE)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY': (BOOLEAN)"},
            {"CALL py(NOT TRUE)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PY': (BOOLEAN)"},
            {"CALL pv(NOT (1 = 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PV': (BOOLEAN)"},
            {"CALL pv((1 = 1) AND TRUE)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PV': (BOOLEAN)"},
            {"CALL pv(1 IN (1, 2))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PV': (BOOLEAN)"},
            {"CALL pv('a' LIKE 'a')",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PV': (BOOLEAN)"},
            {"CALL pr(NOT TRUE)", "1"},
            {"CALL pr(TRUE AND FALSE)", "1"},
            {"CALL pk(NOT TRUE)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PK': (BOOLEAN)"},
            {"CALL pv(x => 1 = 1)",
                "REFUSED SQL compilation error: error line 0 at position -1 | named arguments [X] do not match any "
                + "signature for function PV"},
            {"CALL pdn(ARRAY_CONSTRUCT(1), '10:11:12'::TIME)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PDN': (ARRAY, TIME(9))"},
            {"CALL pnd('10:11:12'::TIME, ARRAY_CONSTRUCT(1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'PND': (TIME(9), ARRAY)"},
        });
    }
}
