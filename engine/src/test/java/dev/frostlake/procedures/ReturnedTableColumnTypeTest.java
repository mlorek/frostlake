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
 * The columns a RETURNS TABLE procedure answers are checked against the types its query's columns have
 * (live-verified): a VARIANT variable bound in the query is a VARIANT column whatever it holds, so a VARIANT column
 * takes a number, a BOOLEAN or a FLOAT given for it and a NUMBER column refuses one; a column of its own type fills
 * an ARRAY or an OBJECT column, a VARIANT does not fill an ARRAY, nor a NUMBER a VARIANT.
 */
public class ReturnedTableColumnTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void aBoundVariableIsItsDeclaredTypeInTheReturnedTable() {
        engine.execute("CREATE OR REPLACE PROCEDURE tq1() RETURNS TABLE (a ARRAY) LANGUAGE SQL AS $$ DECLARE r "
            + "RESULTSET; BEGIN r := (SELECT ARRAY_CONSTRUCT(1) AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tq2() RETURNS TABLE (a OBJECT) LANGUAGE SQL AS $$ DECLARE r "
            + "RESULTSET; BEGIN r := (SELECT OBJECT_CONSTRUCT('k', 1) AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tq3() RETURNS TABLE (a VARIANT) LANGUAGE SQL AS $$ DECLARE r "
            + "RESULTSET; BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tq4() RETURNS TABLE (a VARIANT) LANGUAGE SQL AS $$ DECLARE r "
            + "RESULTSET; BEGIN r := (SELECT ARRAY_CONSTRUCT(1) AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tq5() RETURNS TABLE (a ARRAY) LANGUAGE SQL AS $$ DECLARE r "
            + "RESULTSET; BEGIN r := (SELECT PARSE_JSON('[1]') AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tq6(x ARRAY) RETURNS TABLE (a ARRAY) LANGUAGE SQL AS $$ DECLARE "
            + "r RESULTSET; BEGIN r := (SELECT :x::ARRAY AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tq7(x NUMBER) RETURNS TABLE (a VARIANT) LANGUAGE SQL AS $$ "
            + "DECLARE r RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tq8(x VARIANT) RETURNS TABLE (a NUMBER) LANGUAGE SQL AS $$ "
            + "DECLARE r RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE TABLE tqt (a ARRAY, o OBJECT, v VARIANT)");
        engine.execute("INSERT INTO tqt SELECT ARRAY_CONSTRUCT(1), OBJECT_CONSTRUCT('k', 1), PARSE_JSON('1')");
        engine.execute("CREATE OR REPLACE PROCEDURE tq9() RETURNS TABLE (a ARRAY) LANGUAGE SQL AS $$ DECLARE r "
            + "RESULTSET; BEGIN r := (SELECT a FROM tqt); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tq10() RETURNS TABLE (o OBJECT) LANGUAGE SQL AS $$ DECLARE r "
            + "RESULTSET; BEGIN r := (SELECT o FROM tqt); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tq11(x ARRAY) RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE s "
            + "VARCHAR; BEGIN s := (SELECT SYSTEM$TYPEOF(:x)); RETURN s; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tq12(x OBJECT, y VARIANT, z NUMBER) RETURNS VARCHAR LANGUAGE "
            + "SQL AS $$ DECLARE s VARCHAR; BEGIN s := (SELECT SYSTEM$TYPEOF(:x) || ' ' || "
            + "SYSTEM$TYPEOF(:y) || ' ' || SYSTEM$TYPEOF(:z)); RETURN s; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tq13(x ARRAY) RETURNS TABLE (a ARRAY) LANGUAGE SQL AS $$ "
            + "DECLARE r RESULTSET; BEGIN r := (SELECT x FROM (SELECT :x AS x)); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tq14(x VARIANT) RETURNS TABLE (a VARIANT) LANGUAGE SQL AS $$ "
            + "DECLARE r RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        assertCells(new String[][] {
            {"CALL tq1()", "[1]"},
            {"CALL tq2()", "{\"k\":1}"},
            {"CALL tq3()",
                "REFUSED Stored procedure execution error: data type of returned table does not match expected "
                + "returned table type"},
            {"CALL tq4()",
                "REFUSED Stored procedure execution error: data type of returned table does not match expected "
                + "returned table type"},
            {"CALL tq5()",
                "REFUSED Stored procedure execution error: data type of returned table does not match expected "
                + "returned table type"},
            {"CALL tq6(ARRAY_CONSTRUCT(1))", "[1]"},
            {"CALL tq7(1)",
                "REFUSED Stored procedure execution error: data type of returned table does not match expected "
                + "returned table type"},
            {"CALL tq8(1)",
                "REFUSED Stored procedure execution error: data type of returned table does not match expected "
                + "returned table type"},
            {"CALL tq9()", "[1]"},
            {"CALL tq10()", "{\"k\":1}"},
            {"CALL tq11(ARRAY_CONSTRUCT(1))", "ARRAY[LOB]"},
            {"CALL tq12(OBJECT_CONSTRUCT('k', 1), PARSE_JSON('1'), 5)", "OBJECT[LOB] VARIANT[LOB] NUMBER(38,0)[SB1]"},
            {"CALL tq13(ARRAY_CONSTRUCT(1))", "[1]"},
            {"CALL tq14('2024-01-15'::DATE)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'TQ14': (DATE)"},
            {"CALL tq14(TO_BINARY('ab', 'HEX'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'TQ14': (BINARY(67108864))"},
            {"CALL tq14(NULL)", "null"},
        });
    }

    @Test
    public void aVariantColumnTakesAnyValueBoundToItsParameter() {
        engine.execute("CREATE OR REPLACE PROCEDURE tpr(x VARIANT) RETURNS TABLE (a VARIANT) LANGUAGE SQL AS $$ "
            + "DECLARE r RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tpy(x ARRAY) RETURNS TABLE (a ARRAY) LANGUAGE SQL AS $$ DECLARE "
            + "r RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tpo(x OBJECT) RETURNS TABLE (a OBJECT) LANGUAGE SQL AS $$ "
            + "DECLARE r RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tpx(x BINARY) RETURNS TABLE (a BINARY) LANGUAGE SQL AS $$ "
            + "DECLARE r RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tpm(x TIME) RETURNS TABLE (a TIME) LANGUAGE SQL AS $$ DECLARE r "
            + "RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tpd(x DATE) RETURNS TABLE (a DATE) LANGUAGE SQL AS $$ DECLARE r "
            + "RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tpb(x BOOLEAN) RETURNS TABLE (a BOOLEAN) LANGUAGE SQL AS $$ "
            + "DECLARE r RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tpf(x FLOAT) RETURNS TABLE (a FLOAT) LANGUAGE SQL AS $$ DECLARE "
            + "r RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tpt(x TIMESTAMP_NTZ) RETURNS TABLE (a TIMESTAMP_NTZ) LANGUAGE "
            + "SQL AS $$ DECLARE r RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tpn(x NUMBER(5,2)) RETURNS TABLE (a NUMBER(5,2)) LANGUAGE SQL "
            + "AS $$ DECLARE r RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE qa(x VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE qv(x VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE qb(x BOOLEAN) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        assertCells(new String[][] {
            {"CALL tpr(1)", "1"},
            {"CALL tpr(TRUE)", "true"},
            {"CALL tpr(1.5::FLOAT)", "1.5"},
            {"CALL tpr(ARRAY_CONSTRUCT(1))", "[1]"},
            {"CALL tpr(OBJECT_CONSTRUCT('k', 1))", "{\"k\":1}"},
            {"CALL tpr('x')",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'TPR': (VARCHAR(1))"},
            {"CALL tpr(PARSE_JSON('[1]'))", "[1]"},
            {"CALL tpd(ARRAY_CONSTRUCT(1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'TPD': (ARRAY)"},
            {"CALL tpb(ARRAY_CONSTRUCT(1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'TPB': (ARRAY)"},
            {"CALL tpf('10:11:12'::TIME)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'TPF': (TIME(9))"},
            {"CALL tpt('10:11:12'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"CALL tpn(123456789)",
                "REFUSED Number out of representable range: type FIXED[SB8](5,2){nullable}, value 123456789.00"},
            {"CALL tpm(TRUE)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'TPM': (BOOLEAN)"},
            {"CALL tpo(TRUE)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'TPO': (BOOLEAN)"},
            {"CALL tpx(TRUE)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'TPX': (BOOLEAN)"},
            {"CALL tpd(OBJECT_CONSTRUCT('k', 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'TPD': (OBJECT)"},
            {"CALL tpf(TO_BINARY('ab', 'HEX'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'TPF': (BINARY(67108864))"},
            {"CALL tpm(1.5::FLOAT)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'TPM': (FLOAT)"},
            {"CALL tpy('2024-01-15'::DATE)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'TPY': (DATE)"},
            {"CALL tpx('2024-01-15 10:11:12'::TIMESTAMP_NTZ)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'TPX': (TIMESTAMP_NTZ(9))"},
            {"CALL qa($1)", "1"},
            {"CALL qv(CURRENT_USER() IS NULL)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'QV': (BOOLEAN)"},
            {"CALL qv(TRUE)", "1"},
            {"CALL qb(NOT TRUE)", "1"},
            {"CALL qb(1 = 1)", "1"},
        });
    }
}
