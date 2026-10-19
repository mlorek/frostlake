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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A table procedure called in a FROM clause, {@code TABLE(p(1, 'a'))}, does not convert its arguments as its
 * CALL does: they are matched to the parameters' declared types as a table function's are, by family and
 * exactly, while the statement compiles. A text is no argument of a NUMBER parameter, nor a NUMBER of another
 * scale, a FLOAT, an untyped NULL or a timestamp of other digits; every misfit, too few and too many arguments
 * among them, is {@code Invalid argument types for function 'P': (VARCHAR(1))} at the procedure's name, the name
 * as the call writes it, and with named arguments {@code named arguments [X] do not match any signature for
 * function P}. A number written as a literal is matched at the scale its value needs. Every answer is
 * live-verified.
 */
public class TableProcedureArgumentTypesTest extends MeasuredCellsTestSupport {

    @Test
    public void aFromClauseCallMatchesItsArgumentsExactly() {
        answer(
            "CREATE OR REPLACE PROCEDURE tpa(x INT) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET; "
                + "BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE tpv(x VARCHAR) RETURNS TABLE (a VARCHAR) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE tpd(x DATE) RETURNS TABLE (a DATE) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE tpb(x BOOLEAN) RETURNS TABLE (a BOOLEAN) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE tpf(x FLOAT) RETURNS TABLE (a FLOAT) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE tpt(x TIMESTAMP_NTZ) RETURNS TABLE (a TIMESTAMP_NTZ) LANGUAGE SQL AS $$ "
                + "DECLARE r RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE tpr(x VARIANT) RETURNS TABLE (a VARIANT) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE tpy(x ARRAY) RETURNS TABLE (a ARRAY) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE tpn(x NUMBER(5,2)) RETURNS TABLE (a NUMBER(5,2)) LANGUAGE SQL AS $$ "
                + "DECLARE r RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE tpm(x TIME) RETURNS TABLE (a TIME) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE tpo(x OBJECT) RETURNS TABLE (a OBJECT) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE tpx(x BINARY) RETURNS TABLE (a BINARY) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT :x AS a); RETURN TABLE(r); END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPA': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpa('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPA': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpa('1'))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(tpa(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPA': (NUMBER(2,1))",
            "SELECT * FROM TABLE(tpa(1.5))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPA': (BOOLEAN)",
            "SELECT * FROM TABLE(tpa(TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPA': (NULL)",
            "SELECT * FROM TABLE(tpa(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPA': (VARCHAR(10))",
            "SELECT * FROM TABLE(tpa('2020-01-02'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPA': (DATE)",
            "SELECT * FROM TABLE(tpa('2020-01-02'::DATE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPA': (FLOAT)",
            "SELECT * FROM TABLE(tpa(1::FLOAT))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPA': (VARIANT)",
            "SELECT * FROM TABLE(tpa(PARSE_JSON('1')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPA': (ARRAY)",
            "SELECT * FROM TABLE(tpa(ARRAY_CONSTRUCT(1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPA': (TIMESTAMP_NTZ(9))",
            "SELECT * FROM TABLE(tpa('2020-01-02'::TIMESTAMP_NTZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPA': (TIME(9))",
            "SELECT * FROM TABLE(tpa('10:11:12'::TIME))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPA': (OBJECT)",
            "SELECT * FROM TABLE(tpa(OBJECT_CONSTRUCT('k', 1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPA': (BINARY(67108864))",
            "SELECT * FROM TABLE(tpa(TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPA': (TIMESTAMP_TZ(9))",
            "SELECT * FROM TABLE(tpa('2020-01-02 10:11:12 +02:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPA': (VARCHAR(134217728))",
            "SELECT * FROM TABLE(tpa(NULL::VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPA': (VARCHAR(6))",
            "SELECT * FROM TABLE(tpa('abcdef'))");
        assertAnswer(
            "123456789",
            "SELECT * FROM TABLE(tpa(123456789))");
        assertAnswer(
            "x",
            "SELECT * FROM TABLE(tpv('x'))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(tpv('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPV': (NUMBER(1,0))",
            "SELECT * FROM TABLE(tpv(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPV': (NUMBER(2,1))",
            "SELECT * FROM TABLE(tpv(1.5))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPV': (BOOLEAN)",
            "SELECT * FROM TABLE(tpv(TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPV': (NULL)",
            "SELECT * FROM TABLE(tpv(NULL))");
        assertAnswer(
            "2020-01-02",
            "SELECT * FROM TABLE(tpv('2020-01-02'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPV': (DATE)",
            "SELECT * FROM TABLE(tpv('2020-01-02'::DATE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPV': (FLOAT)",
            "SELECT * FROM TABLE(tpv(1::FLOAT))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPV': (VARIANT)",
            "SELECT * FROM TABLE(tpv(PARSE_JSON('1')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPV': (ARRAY)",
            "SELECT * FROM TABLE(tpv(ARRAY_CONSTRUCT(1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPV': (TIMESTAMP_NTZ(9))",
            "SELECT * FROM TABLE(tpv('2020-01-02'::TIMESTAMP_NTZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPV': (TIME(9))",
            "SELECT * FROM TABLE(tpv('10:11:12'::TIME))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPV': (OBJECT)",
            "SELECT * FROM TABLE(tpv(OBJECT_CONSTRUCT('k', 1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPV': (BINARY(67108864))",
            "SELECT * FROM TABLE(tpv(TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPV': (TIMESTAMP_TZ(9))",
            "SELECT * FROM TABLE(tpv('2020-01-02 10:11:12 +02:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "null",
            "SELECT * FROM TABLE(tpv(NULL::VARCHAR))");
        assertAnswer(
            "abcdef",
            "SELECT * FROM TABLE(tpv('abcdef'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPV': (NUMBER(9,0))",
            "SELECT * FROM TABLE(tpv(123456789))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpd('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpd('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (NUMBER(1,0))",
            "SELECT * FROM TABLE(tpd(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (NUMBER(2,1))",
            "SELECT * FROM TABLE(tpd(1.5))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (BOOLEAN)",
            "SELECT * FROM TABLE(tpd(TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (NULL)",
            "SELECT * FROM TABLE(tpd(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (VARCHAR(10))",
            "SELECT * FROM TABLE(tpd('2020-01-02'))");
        assertAnswer(
            "2020-01-02",
            "SELECT * FROM TABLE(tpd('2020-01-02'::DATE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (FLOAT)",
            "SELECT * FROM TABLE(tpd(1::FLOAT))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (VARIANT)",
            "SELECT * FROM TABLE(tpd(PARSE_JSON('1')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (ARRAY)",
            "SELECT * FROM TABLE(tpd(ARRAY_CONSTRUCT(1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (TIMESTAMP_NTZ(9))",
            "SELECT * FROM TABLE(tpd('2020-01-02'::TIMESTAMP_NTZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (TIME(9))",
            "SELECT * FROM TABLE(tpd('10:11:12'::TIME))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (OBJECT)",
            "SELECT * FROM TABLE(tpd(OBJECT_CONSTRUCT('k', 1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (BINARY(67108864))",
            "SELECT * FROM TABLE(tpd(TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (TIMESTAMP_TZ(9))",
            "SELECT * FROM TABLE(tpd('2020-01-02 10:11:12 +02:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (VARCHAR(134217728))",
            "SELECT * FROM TABLE(tpd(NULL::VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (VARCHAR(6))",
            "SELECT * FROM TABLE(tpd('abcdef'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPD': (NUMBER(9,0))",
            "SELECT * FROM TABLE(tpd(123456789))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpb('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpb('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (NUMBER(1,0))",
            "SELECT * FROM TABLE(tpb(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (NUMBER(2,1))",
            "SELECT * FROM TABLE(tpb(1.5))");
        assertAnswer(
            "true",
            "SELECT * FROM TABLE(tpb(TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (NULL)",
            "SELECT * FROM TABLE(tpb(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (VARCHAR(10))",
            "SELECT * FROM TABLE(tpb('2020-01-02'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (DATE)",
            "SELECT * FROM TABLE(tpb('2020-01-02'::DATE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (FLOAT)",
            "SELECT * FROM TABLE(tpb(1::FLOAT))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (VARIANT)",
            "SELECT * FROM TABLE(tpb(PARSE_JSON('1')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (ARRAY)",
            "SELECT * FROM TABLE(tpb(ARRAY_CONSTRUCT(1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (TIMESTAMP_NTZ(9))",
            "SELECT * FROM TABLE(tpb('2020-01-02'::TIMESTAMP_NTZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (TIME(9))",
            "SELECT * FROM TABLE(tpb('10:11:12'::TIME))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (OBJECT)",
            "SELECT * FROM TABLE(tpb(OBJECT_CONSTRUCT('k', 1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (BINARY(67108864))",
            "SELECT * FROM TABLE(tpb(TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (TIMESTAMP_TZ(9))",
            "SELECT * FROM TABLE(tpb('2020-01-02 10:11:12 +02:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (VARCHAR(134217728))",
            "SELECT * FROM TABLE(tpb(NULL::VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (VARCHAR(6))",
            "SELECT * FROM TABLE(tpb('abcdef'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPB': (NUMBER(9,0))",
            "SELECT * FROM TABLE(tpb(123456789))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpf('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpf('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (NUMBER(1,0))",
            "SELECT * FROM TABLE(tpf(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (NUMBER(2,1))",
            "SELECT * FROM TABLE(tpf(1.5))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (BOOLEAN)",
            "SELECT * FROM TABLE(tpf(TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (NULL)",
            "SELECT * FROM TABLE(tpf(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (VARCHAR(10))",
            "SELECT * FROM TABLE(tpf('2020-01-02'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (DATE)",
            "SELECT * FROM TABLE(tpf('2020-01-02'::DATE))");
        assertAnswer(
            "1.0",
            "SELECT * FROM TABLE(tpf(1::FLOAT))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (VARIANT)",
            "SELECT * FROM TABLE(tpf(PARSE_JSON('1')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (ARRAY)",
            "SELECT * FROM TABLE(tpf(ARRAY_CONSTRUCT(1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (TIMESTAMP_NTZ(9))",
            "SELECT * FROM TABLE(tpf('2020-01-02'::TIMESTAMP_NTZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (TIME(9))",
            "SELECT * FROM TABLE(tpf('10:11:12'::TIME))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (OBJECT)",
            "SELECT * FROM TABLE(tpf(OBJECT_CONSTRUCT('k', 1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (BINARY(67108864))",
            "SELECT * FROM TABLE(tpf(TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (TIMESTAMP_TZ(9))",
            "SELECT * FROM TABLE(tpf('2020-01-02 10:11:12 +02:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (VARCHAR(134217728))",
            "SELECT * FROM TABLE(tpf(NULL::VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (VARCHAR(6))",
            "SELECT * FROM TABLE(tpf('abcdef'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPF': (NUMBER(9,0))",
            "SELECT * FROM TABLE(tpf(123456789))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpt('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpt('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (NUMBER(1,0))",
            "SELECT * FROM TABLE(tpt(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (NUMBER(2,1))",
            "SELECT * FROM TABLE(tpt(1.5))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (BOOLEAN)",
            "SELECT * FROM TABLE(tpt(TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (NULL)",
            "SELECT * FROM TABLE(tpt(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (VARCHAR(10))",
            "SELECT * FROM TABLE(tpt('2020-01-02'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (DATE)",
            "SELECT * FROM TABLE(tpt('2020-01-02'::DATE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (FLOAT)",
            "SELECT * FROM TABLE(tpt(1::FLOAT))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (VARIANT)",
            "SELECT * FROM TABLE(tpt(PARSE_JSON('1')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (ARRAY)",
            "SELECT * FROM TABLE(tpt(ARRAY_CONSTRUCT(1)))");
        assertAnswer(
            "2020-01-02T00:00",
            "SELECT * FROM TABLE(tpt('2020-01-02'::TIMESTAMP_NTZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (TIME(9))",
            "SELECT * FROM TABLE(tpt('10:11:12'::TIME))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (OBJECT)",
            "SELECT * FROM TABLE(tpt(OBJECT_CONSTRUCT('k', 1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (BINARY(67108864))",
            "SELECT * FROM TABLE(tpt(TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (TIMESTAMP_TZ(9))",
            "SELECT * FROM TABLE(tpt('2020-01-02 10:11:12 +02:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (VARCHAR(134217728))",
            "SELECT * FROM TABLE(tpt(NULL::VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (VARCHAR(6))",
            "SELECT * FROM TABLE(tpt('abcdef'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPT': (NUMBER(9,0))",
            "SELECT * FROM TABLE(tpt(123456789))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpr('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpr('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (NUMBER(1,0))",
            "SELECT * FROM TABLE(tpr(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (NUMBER(2,1))",
            "SELECT * FROM TABLE(tpr(1.5))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (BOOLEAN)",
            "SELECT * FROM TABLE(tpr(TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (NULL)",
            "SELECT * FROM TABLE(tpr(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (VARCHAR(10))",
            "SELECT * FROM TABLE(tpr('2020-01-02'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (DATE)",
            "SELECT * FROM TABLE(tpr('2020-01-02'::DATE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (FLOAT)",
            "SELECT * FROM TABLE(tpr(1::FLOAT))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(tpr(PARSE_JSON('1')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (ARRAY)",
            "SELECT * FROM TABLE(tpr(ARRAY_CONSTRUCT(1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (TIMESTAMP_NTZ(9))",
            "SELECT * FROM TABLE(tpr('2020-01-02'::TIMESTAMP_NTZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (TIME(9))",
            "SELECT * FROM TABLE(tpr('10:11:12'::TIME))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (OBJECT)",
            "SELECT * FROM TABLE(tpr(OBJECT_CONSTRUCT('k', 1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (BINARY(67108864))",
            "SELECT * FROM TABLE(tpr(TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (TIMESTAMP_TZ(9))",
            "SELECT * FROM TABLE(tpr('2020-01-02 10:11:12 +02:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (VARCHAR(134217728))",
            "SELECT * FROM TABLE(tpr(NULL::VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (VARCHAR(6))",
            "SELECT * FROM TABLE(tpr('abcdef'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPR': (NUMBER(9,0))",
            "SELECT * FROM TABLE(tpr(123456789))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpy('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpy('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (NUMBER(1,0))",
            "SELECT * FROM TABLE(tpy(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (NUMBER(2,1))",
            "SELECT * FROM TABLE(tpy(1.5))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (BOOLEAN)",
            "SELECT * FROM TABLE(tpy(TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (NULL)",
            "SELECT * FROM TABLE(tpy(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (VARCHAR(10))",
            "SELECT * FROM TABLE(tpy('2020-01-02'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (DATE)",
            "SELECT * FROM TABLE(tpy('2020-01-02'::DATE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (FLOAT)",
            "SELECT * FROM TABLE(tpy(1::FLOAT))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (VARIANT)",
            "SELECT * FROM TABLE(tpy(PARSE_JSON('1')))");
        answer(
            "SELECT * FROM TABLE(tpy(ARRAY_CONSTRUCT(1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (TIMESTAMP_NTZ(9))",
            "SELECT * FROM TABLE(tpy('2020-01-02'::TIMESTAMP_NTZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (TIME(9))",
            "SELECT * FROM TABLE(tpy('10:11:12'::TIME))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (OBJECT)",
            "SELECT * FROM TABLE(tpy(OBJECT_CONSTRUCT('k', 1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (BINARY(67108864))",
            "SELECT * FROM TABLE(tpy(TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (TIMESTAMP_TZ(9))",
            "SELECT * FROM TABLE(tpy('2020-01-02 10:11:12 +02:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (VARCHAR(134217728))",
            "SELECT * FROM TABLE(tpy(NULL::VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (VARCHAR(6))",
            "SELECT * FROM TABLE(tpy('abcdef'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPY': (NUMBER(9,0))",
            "SELECT * FROM TABLE(tpy(123456789))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpn('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpn('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (NUMBER(1,0))",
            "SELECT * FROM TABLE(tpn(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (NUMBER(2,1))",
            "SELECT * FROM TABLE(tpn(1.5))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (BOOLEAN)",
            "SELECT * FROM TABLE(tpn(TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (NULL)",
            "SELECT * FROM TABLE(tpn(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (VARCHAR(10))",
            "SELECT * FROM TABLE(tpn('2020-01-02'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (DATE)",
            "SELECT * FROM TABLE(tpn('2020-01-02'::DATE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (FLOAT)",
            "SELECT * FROM TABLE(tpn(1::FLOAT))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (VARIANT)",
            "SELECT * FROM TABLE(tpn(PARSE_JSON('1')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (ARRAY)",
            "SELECT * FROM TABLE(tpn(ARRAY_CONSTRUCT(1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (TIMESTAMP_NTZ(9))",
            "SELECT * FROM TABLE(tpn('2020-01-02'::TIMESTAMP_NTZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (TIME(9))",
            "SELECT * FROM TABLE(tpn('10:11:12'::TIME))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (OBJECT)",
            "SELECT * FROM TABLE(tpn(OBJECT_CONSTRUCT('k', 1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (BINARY(67108864))",
            "SELECT * FROM TABLE(tpn(TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (TIMESTAMP_TZ(9))",
            "SELECT * FROM TABLE(tpn('2020-01-02 10:11:12 +02:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (VARCHAR(134217728))",
            "SELECT * FROM TABLE(tpn(NULL::VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (VARCHAR(6))",
            "SELECT * FROM TABLE(tpn('abcdef'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPN': (NUMBER(9,0))",
            "SELECT * FROM TABLE(tpn(123456789))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpm('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpm('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (NUMBER(1,0))",
            "SELECT * FROM TABLE(tpm(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (NUMBER(2,1))",
            "SELECT * FROM TABLE(tpm(1.5))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (BOOLEAN)",
            "SELECT * FROM TABLE(tpm(TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (NULL)",
            "SELECT * FROM TABLE(tpm(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (VARCHAR(10))",
            "SELECT * FROM TABLE(tpm('2020-01-02'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (DATE)",
            "SELECT * FROM TABLE(tpm('2020-01-02'::DATE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (FLOAT)",
            "SELECT * FROM TABLE(tpm(1::FLOAT))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (VARIANT)",
            "SELECT * FROM TABLE(tpm(PARSE_JSON('1')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (ARRAY)",
            "SELECT * FROM TABLE(tpm(ARRAY_CONSTRUCT(1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (TIMESTAMP_NTZ(9))",
            "SELECT * FROM TABLE(tpm('2020-01-02'::TIMESTAMP_NTZ))");
        assertAnswer(
            "10:11:12",
            "SELECT * FROM TABLE(tpm('10:11:12'::TIME))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (OBJECT)",
            "SELECT * FROM TABLE(tpm(OBJECT_CONSTRUCT('k', 1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (BINARY(67108864))",
            "SELECT * FROM TABLE(tpm(TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (TIMESTAMP_TZ(9))",
            "SELECT * FROM TABLE(tpm('2020-01-02 10:11:12 +02:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (VARCHAR(134217728))",
            "SELECT * FROM TABLE(tpm(NULL::VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (VARCHAR(6))",
            "SELECT * FROM TABLE(tpm('abcdef'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPM': (NUMBER(9,0))",
            "SELECT * FROM TABLE(tpm(123456789))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpo('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpo('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (NUMBER(1,0))",
            "SELECT * FROM TABLE(tpo(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (NUMBER(2,1))",
            "SELECT * FROM TABLE(tpo(1.5))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (BOOLEAN)",
            "SELECT * FROM TABLE(tpo(TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (NULL)",
            "SELECT * FROM TABLE(tpo(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (VARCHAR(10))",
            "SELECT * FROM TABLE(tpo('2020-01-02'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (DATE)",
            "SELECT * FROM TABLE(tpo('2020-01-02'::DATE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (FLOAT)",
            "SELECT * FROM TABLE(tpo(1::FLOAT))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (VARIANT)",
            "SELECT * FROM TABLE(tpo(PARSE_JSON('1')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (ARRAY)",
            "SELECT * FROM TABLE(tpo(ARRAY_CONSTRUCT(1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (TIMESTAMP_NTZ(9))",
            "SELECT * FROM TABLE(tpo('2020-01-02'::TIMESTAMP_NTZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (TIME(9))",
            "SELECT * FROM TABLE(tpo('10:11:12'::TIME))");
        answer(
            "SELECT * FROM TABLE(tpo(OBJECT_CONSTRUCT('k', 1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (BINARY(67108864))",
            "SELECT * FROM TABLE(tpo(TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (TIMESTAMP_TZ(9))",
            "SELECT * FROM TABLE(tpo('2020-01-02 10:11:12 +02:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (VARCHAR(134217728))",
            "SELECT * FROM TABLE(tpo(NULL::VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (VARCHAR(6))",
            "SELECT * FROM TABLE(tpo('abcdef'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPO': (NUMBER(9,0))",
            "SELECT * FROM TABLE(tpo(123456789))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpx('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (VARCHAR(1))",
            "SELECT * FROM TABLE(tpx('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (NUMBER(1,0))",
            "SELECT * FROM TABLE(tpx(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (NUMBER(2,1))",
            "SELECT * FROM TABLE(tpx(1.5))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (BOOLEAN)",
            "SELECT * FROM TABLE(tpx(TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (NULL)",
            "SELECT * FROM TABLE(tpx(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (VARCHAR(10))",
            "SELECT * FROM TABLE(tpx('2020-01-02'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (DATE)",
            "SELECT * FROM TABLE(tpx('2020-01-02'::DATE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (FLOAT)",
            "SELECT * FROM TABLE(tpx(1::FLOAT))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (VARIANT)",
            "SELECT * FROM TABLE(tpx(PARSE_JSON('1')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (ARRAY)",
            "SELECT * FROM TABLE(tpx(ARRAY_CONSTRUCT(1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (TIMESTAMP_NTZ(9))",
            "SELECT * FROM TABLE(tpx('2020-01-02'::TIMESTAMP_NTZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (TIME(9))",
            "SELECT * FROM TABLE(tpx('10:11:12'::TIME))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (OBJECT)",
            "SELECT * FROM TABLE(tpx(OBJECT_CONSTRUCT('k', 1)))");
        assertAnswer(
            "AB",
            "SELECT * FROM TABLE(tpx(TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (TIMESTAMP_TZ(9))",
            "SELECT * FROM TABLE(tpx('2020-01-02 10:11:12 +02:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (VARCHAR(134217728))",
            "SELECT * FROM TABLE(tpx(NULL::VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (VARCHAR(6))",
            "SELECT * FROM TABLE(tpx('abcdef'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TPX': (NUMBER(9,0))",
            "SELECT * FROM TABLE(tpx(123456789))");
    }

    @Test
    public void scalesDigitsDefaultsAndNames() {
        answer(
            "CREATE OR REPLACE PROCEDURE qa(x INT) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET; "
                + "BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE qn(x NUMBER(5,2)) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE qw(x NUMBER(10,0)) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE qv(x VARCHAR) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE qs(x VARCHAR(3)) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE qt(x TIMESTAMP_NTZ) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE q3(x TIMESTAMP_NTZ(3)) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE "
                + "r RESULTSET; BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE ql(x TIMESTAMP_LTZ) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE qm(x TIME) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET; "
                + "BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE qf(x FLOAT) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE qr(x VARIANT) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE qy(x ARRAY) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE qo(x OBJECT) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE qx(x BINARY) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE qd(x DATE) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET; "
                + "BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE qb(x BOOLEAN) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE q2(x INT, y VARCHAR) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE qe(x INT DEFAULT 1) RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET; BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE qg(x INT, y VARCHAR DEFAULT 'd') RETURNS TABLE (a INT) LANGUAGE SQL AS "
                + "$$ DECLARE r RESULTSET; BEGIN r := (SELECT 1 AS a); RETURN TABLE(r); END; $$");
        answer(
            "CREATE OR REPLACE TABLE t1 (c INT, s VARCHAR, n NUMBER(5,2), f FLOAT)");
        answer(
            "INSERT INTO t1 SELECT 1, 'x', 1.25, 1.5");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qa(1))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qa(1::NUMBER(38,0)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qa(1::NUMBER(10,0)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qa(1.0))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QA': (NUMBER(38,2))",
            "SELECT * FROM TABLE(qa(1::NUMBER(38,2)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qa(NULL::INT))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qa(1 + 1))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qa(ABS(1)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qa(1::INT))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qa((SELECT 1)))");
        answer(
            "SELECT * FROM TABLE(qa($1))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qa(LENGTH('abc')))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qa(12345678901234567890))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qa(1e0))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qa(-1))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qa(TO_NUMBER('1')))");
        answer(
            "SELECT * FROM TABLE(qa(COUNT(*)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QA': (NUMBER(5,2))",
            "SELECT * FROM TABLE(qa(1::NUMBER(5,2)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qa(CAST(NULL AS NUMBER(38,0))))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qa(NULL::NUMBER(10,0)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qn(1.25))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qn(1.25::NUMBER(5,2)))");
        assertAnswer(
            "REFUSED Stored procedure execution error on line 1 position 20: Number out of representable range: "
                + "type FIXED[SB4](5,2){nullable}, value 12345.25",
            "SELECT * FROM TABLE(qn(12345.25))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qn(1::NUMBER(38,2)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qn(1::NUMBER(3,2)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QN': (NUMBER(2,1))",
            "SELECT * FROM TABLE(qn(1.2))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qn(1.250))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qn(NULL::NUMBER(5,2)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QN': (NUMBER(1,0))",
            "SELECT * FROM TABLE(qn(1))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qn(0.25))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qn(123.45))");
        assertAnswer(
            "REFUSED Stored procedure execution error on line 1 position 20: Number out of representable range: "
                + "type FIXED[SB4](5,2){nullable}, value 1234.56",
            "SELECT * FROM TABLE(qn(1234.56))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qw(1))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qw(1::NUMBER(10,0)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qw(1::NUMBER(38,0)))");
        assertAnswer(
            "REFUSED Stored procedure execution error on line 1 position 20: Number out of representable range: "
                + "type FIXED[SB8](10,0){nullable}, value 12345678901",
            "SELECT * FROM TABLE(qw(12345678901))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qw(1::NUMBER(11,0)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qw(1.0))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qv('x'))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qv('x'::VARCHAR(10)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qv(NULL::VARCHAR))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qv(UPPER('x')))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qv('a' || 'b'))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qv(1::VARCHAR))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qv(TO_CHAR(1)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QV': (BOOLEAN)",
            "SELECT * FROM TABLE(qv(CURRENT_USER() IS NULL))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qs('x'))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qs('abcdef'))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qs('abc'))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qs('x'::VARCHAR(10)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qs('x'::VARCHAR(3)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qs('x'::VARCHAR))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qs(NULL::VARCHAR(3)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qt('2020-01-02'::TIMESTAMP_NTZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QT': (TIMESTAMP_NTZ(3))",
            "SELECT * FROM TABLE(qt('2020-01-02'::TIMESTAMP_NTZ(3)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QT': (TIMESTAMP_LTZ(9))",
            "SELECT * FROM TABLE(qt('2020-01-02'::TIMESTAMP_LTZ))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qt('2020-01-02'::DATETIME))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QT': (TIMESTAMP_LTZ(9))",
            "SELECT * FROM TABLE(qt(CURRENT_TIMESTAMP()))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QT': (TIMESTAMP_LTZ(9))",
            "SELECT * FROM TABLE(qt(LOCALTIMESTAMP()))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qt('2020-01-02'::TIMESTAMP))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qt(SYSDATE()))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'Q3': (TIMESTAMP_NTZ(9))",
            "SELECT * FROM TABLE(q3('2020-01-02'::TIMESTAMP_NTZ))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(q3('2020-01-02'::TIMESTAMP_NTZ(3)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'Q3': (TIMESTAMP_NTZ(0))",
            "SELECT * FROM TABLE(q3('2020-01-02'::TIMESTAMP_NTZ(0)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(ql(CURRENT_TIMESTAMP()))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(ql('2020-01-02'::TIMESTAMP_LTZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QL': (TIMESTAMP_NTZ(9))",
            "SELECT * FROM TABLE(ql('2020-01-02'::TIMESTAMP_NTZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QL': (TIMESTAMP_LTZ(3))",
            "SELECT * FROM TABLE(ql('2020-01-02'::TIMESTAMP_LTZ(3)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qm('10:11:12'::TIME))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QM': (TIME(3))",
            "SELECT * FROM TABLE(qm('10:11:12'::TIME(3)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qm(CURRENT_TIME()))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qf(1::FLOAT))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qf(1::REAL))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qf(1e0::DOUBLE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QF': (NUMBER(2,1))",
            "SELECT * FROM TABLE(qf(1.5))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qf(SQRT(4)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qf(NULL::FLOAT))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qr(1::VARIANT))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qr(TO_VARIANT('x')))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qr(PARSE_JSON('[1]')))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qr(NULL::VARIANT))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qr(ARRAY_CONSTRUCT(1)::VARIANT))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qr(PARSE_JSON('1'):a))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qy(ARRAY_CONSTRUCT(1)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qy([1]))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qy(PARSE_JSON('[1]')::ARRAY))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qy(NULL::ARRAY))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qy(ARRAY_CONSTRUCT()))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qo(OBJECT_CONSTRUCT('k', 1)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qo({'k': 1}))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qo(NULL::OBJECT))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qo(PARSE_JSON('{}')::OBJECT))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qx(TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qx(X'AB'))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qx(NULL::BINARY))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qx('AB'::BINARY))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qx(X'AB'::BINARY(1)))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qd(CURRENT_DATE()))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qd('2020-01-02'::DATE))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qd(DATE '2020-01-02'))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qd(NULL::DATE))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qd(TO_DATE('2020-01-02')))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qd(CURRENT_DATE))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qb(TRUE))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qb(FALSE))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qb(NULL::BOOLEAN))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qb(1 = 1))");
        answer(
            "SELECT * FROM TABLE(qb(NOT TRUE))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(q2(1, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'Q2': (NUMBER(1,0))",
            "SELECT * FROM TABLE(q2(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'Q2': (VARCHAR(1), NUMBER(1,0))",
            "SELECT * FROM TABLE(q2('x', 1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'Q2': (NUMBER(1,0), VARCHAR(1), NUMBER(1,0))",
            "SELECT * FROM TABLE(q2(1, 'x', 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'Q2': (NUMBER(1,0), NUMBER(1,0))",
            "SELECT * FROM TABLE(q2(1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'Q2': (VARCHAR(1), VARCHAR(1))",
            "SELECT * FROM TABLE(q2('1', 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'Q2': (NUMBER(1,0), NULL)",
            "SELECT * FROM TABLE(q2(1, NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'Q2': (NULL, VARCHAR(1))",
            "SELECT * FROM TABLE(q2(NULL, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'Q2': ()",
            "SELECT * FROM TABLE(q2())");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qe())");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qe(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QE': (VARCHAR(1))",
            "SELECT * FROM TABLE(qe('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QE': (NULL)",
            "SELECT * FROM TABLE(qe(NULL))");
        answer(
            "SELECT * FROM TABLE(qe(1, 2))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qg(1))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qg(1, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QG': (VARCHAR(1))",
            "SELECT * FROM TABLE(qg('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QG': (NUMBER(1,0), NUMBER(1,0))",
            "SELECT * FROM TABLE(qg(1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QG': ()",
            "SELECT * FROM TABLE(qg())");
        answer(
            "SELECT * FROM t1, TABLE(qa(t1.c))");
        answer(
            "SELECT * FROM t1, TABLE(qn(t1.n))");
        answer(
            "SELECT * FROM t1, TABLE(qa(t1.n))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(qa(x => 1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | named arguments [X] do not match any "
                + "signature for function QA",
            "SELECT * FROM TABLE(qa(x => '1'))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(q2(x => 1, y => 'x'))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(q2(y => 'x', x => 1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | illegal mixing of named and positional "
                + "arguments for function Q2",
            "SELECT * FROM TABLE(q2(x => 1, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | illegal mixing of named and positional "
                + "arguments for function Q2",
            "SELECT * FROM TABLE(q2(1, y => 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | named arguments [X, Y] do not match any "
                + "signature for function Q2",
            "SELECT * FROM TABLE(q2(y => 1, x => 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | named arguments [NOSUCH] do not match "
                + "any signature for function QA",
            "SELECT * FROM TABLE(qa(nosuch => 1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TEST_SCHEMA.QA': (VARCHAR(1))",
            "SELECT * FROM TABLE(test_schema.qa('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TEST_DB.TEST_SCHEMA.QA': (VARCHAR(1))",
            "SELECT * FROM TABLE(test_db.test_schema.qa('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QA': (VARCHAR(1))",
            "SELECT * FROM TABLE(\"QA\"('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QA': (VARCHAR(1))",
            "SELECT * FROM TABLE(qa('1')) x");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QA': (VARCHAR(1))",
            "SELECT * FROM TABLE(qa('1')), t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 | Invalid argument types for function "
                + "'QA': (VARCHAR(1))",
            "SELECT nosuchcol FROM TABLE(qa('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QA': (VARCHAR(1))",
            "SELECT * FROM TABLE(qa('1')) WHERE nosuchcol = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NOSUCH_TABLE' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_TABLE.",
            "SELECT * FROM nosuch_table, TABLE(qa('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 | Invalid argument types for function "
                + "'QA': (VARCHAR(1))",
            "SELECT ABS(1, 2) FROM TABLE(qa('1'))");
        answer(
            "SELECT * FROM TABLE(qa(ABS(1, 2)))");
        answer(
            "SELECT * FROM TABLE(qa(nosuchfn(1)))");
        answer(
            "SELECT * FROM TABLE(qa(nosuchcol))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function NOSUCHPROC",
            "SELECT * FROM TABLE(nosuchproc('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QA': (VARCHAR(1))",
            "SELECT * FROM TABLE(qa(\n '1'))");
    }

    @Test
    public void aLiteralIsMatchedAtTheScaleItsValueNeeds() {
        answer(
            "CREATE OR REPLACE FUNCTION n1(a INT, b INT) RETURNS INT AS 'a + b'");
        answer(
            "CREATE OR REPLACE FUNCTION tov(a INT, b VARCHAR) RETURNS TABLE (v INT) AS 'SELECT a'");
        answer(
            "CREATE OR REPLACE FUNCTION tn(a NUMBER(5,2)) RETURNS TABLE (v NUMBER(5,2)) AS 'SELECT a'");
        answer(
            "CREATE OR REPLACE TABLE t1 (c INT, s VARCHAR)");
        answer(
            "INSERT INTO t1 SELECT 1, 'x'");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(tov(1.0, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TOV': (NUMBER(2,1), VARCHAR(1))",
            "SELECT * FROM TABLE(tov(1.50, 'x'))");
        assertAnswer(
            "1",
            "SELECT * FROM TABLE(tov(1e0, 'x'))");
        assertAnswer(
            "1.25",
            "SELECT * FROM TABLE(tn(1.25))");
        assertAnswer(
            "1.25",
            "SELECT * FROM TABLE(tn(1.250))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TN': (NUMBER(2,1))",
            "SELECT * FROM TABLE(tn(1.2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TN': (NUMBER(1,0))",
            "SELECT * FROM TABLE(tn(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TN': (NUMBER(1,0))",
            "SELECT * FROM TABLE(tn(1.0))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'TN': (NUMBER(2,1))",
            "SELECT * FROM TABLE(tn(1.20))");
        assertAnswer(
            "-1.25",
            "SELECT * FROM TABLE(tn(-1.250))");
        assertAnswer(
            "-1",
            "SELECT * FROM TABLE(tov(-1.0, 'x'))");
        assertAnswer(
            "2",
            "SELECT * FROM TABLE(tov(1 + 1.0, 'x'))");
    }
}
