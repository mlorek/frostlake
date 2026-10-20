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

package dev.frostlake.types;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * DECFLOAT through routines and writes (live-verified): a DECFLOAT parameter takes a DECFLOAT argument only, a
 * NUMBER, a FLOAT, a text and an untyped NULL refused; DECFLOAT is a return-type family of its own; Java, Python
 * and Scala routines refuse it in their signatures; scripting keeps it exact; and a FLOAT written into a DECFLOAT
 * column keeps 18 significant digits, where a cast keeps the exact binary value.
 */
public class DecfloatRoutineTest extends MeasuredCellsTestSupport {

    @Test
    public void aRoutineTakesAndReturnsDecfloatsAsTheAccountDoes() {
        engine.execute("CREATE OR REPLACE FUNCTION df_f(x DECFLOAT) RETURNS DECFLOAT AS 'x * 1'");
        engine.execute("CREATE OR REPLACE PROCEDURE df_p(x DECFLOAT) RETURNS DECFLOAT LANGUAGE SQL AS 'BEGIN RETURN "
            + "x; END'");
        engine.execute("CREATE OR REPLACE PROCEDURE df_q(x DECFLOAT) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
            + "x::VARCHAR || '' '' || SYSTEM$TYPEOF(x); END'");
        engine.execute("CREATE OR REPLACE FUNCTION df_t() RETURNS TABLE (d DECFLOAT) AS 'SELECT 1.5 UNION ALL SELECT "
            + "1::DECFLOAT / 3'");
        engine.execute("CREATE OR REPLACE FUNCTION df_sql(x DECFLOAT) RETURNS DECFLOAT LANGUAGE SQL AS 'x + 1'");
        assertCells(new String[][] {
            {"SELECT df_f(1.5)::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DF_F': (NUMBER(2,1))"},
            {"SELECT df_f(1::DECFLOAT / 3)::VARCHAR, SYSTEM$TYPEOF(df_f(1::DECFLOAT))",
                "3.3333333333333333333333333333333333333e-1|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT df_sql(1::DECFLOAT / 3)::VARCHAR, SYSTEM$TYPEOF(df_sql(1::DECFLOAT))",
                "1.3333333333333333333333333333333333333|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT df_sql(1.5)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DF_SQL': (NUMBER(2,1))"},
            {"SELECT df_sql('1.5')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DF_SQL': (VARCHAR(3))"},
            {"SELECT df_sql(1.5::FLOAT)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DF_SQL': (FLOAT)"},
            {"SELECT df_sql(NULL)::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DF_SQL': (NULL)"},
            {"CALL df_p(1.5)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'DF_P': (NUMBER(2,1))"},
            {"CALL df_p(1::DECFLOAT / 3)", "0.33333333333333333333333333333333333333"},
            {"CALL df_q(1.5)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'DF_Q': (NUMBER(2,1))"},
            {"CALL df_q(TO_DECFLOAT('2.25'))", "2.25 DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT d::VARCHAR, SYSTEM$TYPEOF(d) FROM TABLE(df_t()) ORDER BY 1",
                "1.5|DECFLOAT(38)[DECFLOAT18] ;; 3.3333333333333333333333333333333333333e-1|DECFLOAT(38)[DECFLOAT18]"},
            {"CREATE OR REPLACE FUNCTION df_g() RETURNS DECFLOAT AS '1.5'",
                "REFUSED Declared return type 'DECFLOAT(38)' is incompatible with actual return type 'NUMBER(2,1)'"},
            {"CREATE OR REPLACE FUNCTION df_h(x DECFLOAT) RETURNS NUMBER(10,2) AS 'x'",
                "REFUSED Declared return type 'NUMBER(10,2)' is incompatible with actual return type 'DECFLOAT(38)'"},
            {"CREATE OR REPLACE FUNCTION df_k(x NUMBER(10,2)) RETURNS DECFLOAT AS 'x / 3'",
                "REFUSED Declared return type 'DECFLOAT(38)' is incompatible with actual return type 'NUMBER(16,8)'"},
            {"CREATE OR REPLACE FUNCTION df_6() RETURNS DECFLOAT LANGUAGE SQL AS '0.1::FLOAT'",
                "REFUSED Declared return type 'DECFLOAT(38)' is incompatible with actual return type 'FLOAT'"},
            {"CREATE OR REPLACE FUNCTION df_7() RETURNS FLOAT LANGUAGE SQL AS '1::DECFLOAT'",
                "REFUSED Declared return type 'FLOAT' is incompatible with actual return type 'DECFLOAT(38)'"},
            {"CREATE OR REPLACE FUNCTION df_8() RETURNS VARCHAR LANGUAGE SQL AS '1::DECFLOAT'",
                "REFUSED Declared return type 'VARCHAR(134217728)' is incompatible with actual return type "
                + "'DECFLOAT(38)'"},
            {"CREATE OR REPLACE FUNCTION df_java(x DECFLOAT) RETURNS DOUBLE LANGUAGE JAVA HANDLER = 'DfA.f' AS 'class "
                + "DfA { public static double f(java.math.BigDecimal x) { return x.doubleValue(); } }'",
                "REFUSED Snowflake type DECFLOAT[DECFLOAT18](38,0){nullable} is not supported in function DF_JAVA "
                + "with handler DfA.f"},
            {"CREATE OR REPLACE FUNCTION df_java2(x DOUBLE) RETURNS DECFLOAT LANGUAGE JAVA HANDLER = 'DfB.f' AS "
                + "'class DfB { public static java.math.BigDecimal f(double x) { return "
                + "java.math.BigDecimal.valueOf(x); } }'",
                "REFUSED Snowflake type DECFLOAT[DECFLOAT18](38,0){nullable} is not supported in function DF_JAVA2 "
                + "with handler DfB.f"},
            {"CREATE OR REPLACE FUNCTION df_js(x DECFLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'return X;'",
                "REFUSED Language JAVASCRIPT does not support type 'DECFLOAT(38)' for argument or return type."},
            {"CREATE OR REPLACE FUNCTION df_py(x DECFLOAT) RETURNS FLOAT LANGUAGE PYTHON RUNTIME_VERSION = '3.10' "
                + "HANDLER = 'f' AS 'def f(x):\n    return float(x)'",
                "REFUSED Snowflake type DECFLOAT[DECFLOAT18](38,0){nullable} is not supported in function DF_PY with "
                + "handler f"},
            {"CREATE OR REPLACE FUNCTION df_sc(x DECFLOAT) RETURNS DOUBLE LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'DfS.f' AS 'object DfS { def f(x: java.math.BigDecimal): Double = x.doubleValue }'",
                "REFUSED Snowflake type DECFLOAT[DECFLOAT18](38,0){nullable} is not supported in function DF_SC with "
                + "handler DfS.f"},
            {"CREATE OR REPLACE PROCEDURE df_jp(x DECFLOAT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'DfP.run' AS 'class DfP { public static "
                + "String run(com.snowflake.snowpark_java.Session s, java.math.BigDecimal x) { return x.toString(); } "
                + "}'",
                "REFUSED Snowflake type DECFLOAT[DECFLOAT18](38,0){nullable} is not supported in function DF_JP with "
                + "handler DfP.run"},
            {"CREATE OR REPLACE PROCEDURE df_pp(x DECFLOAT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.10' "
                + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS 'def run(session, x):\n    return "
                + "str(x)'",
                "REFUSED Language PYTHON does not support type 'DECFLOAT(38)' for argument or return type."},
            {"CREATE OR REPLACE FUNCTION df_jt() RETURNS TABLE (d DECFLOAT) LANGUAGE JAVA HANDLER = 'DfT' AS 'class "
                + "DfT { }'",
                "REFUSED Snowflake type DECFLOAT[DECFLOAT18](38,0){nullable} is not supported in function DF_JT with "
                + "handler DfT"},
            {"CREATE OR REPLACE FUNCTION df_tbl() RETURNS TABLE (d DECFLOAT) LANGUAGE SQL AS 'SELECT 1.5::DECFLOAT'",
                "Function DF_TBL successfully created."},
        });
    }

    @Test
    public void aWriteKeepsADecfloatAsTheAccountDoes() {
        engine.execute("CREATE OR REPLACE TABLE dz (k VARCHAR, d DECFLOAT, f FLOAT)");
        engine.execute("INSERT INTO dz (k, d, f) SELECT 'sel', 0.1::FLOAT, 0.1::FLOAT");
        engine.execute("INSERT INTO dz (k, d, f) VALUES ('val', 0.1::FLOAT, 0.1::FLOAT)");
        engine.execute("INSERT INTO dz (k, d) SELECT 'big', 1e38::FLOAT");
        engine.execute("INSERT INTO dz (k, d) SELECT 'third', (1/3)::FLOAT");
        engine.execute("INSERT INTO dz (k, d) SELECT 'cast', 0.1::FLOAT::DECFLOAT");
        engine.execute("INSERT INTO dz (k, d) SELECT 'col', f FROM dz WHERE k = 'sel'");
        engine.execute("INSERT INTO dz (k, d) SELECT 'tiny', 1e-100::FLOAT");
        engine.execute("INSERT INTO dz (k, d) SELECT 'tdf', TO_DECFLOAT(0.1::FLOAT)");
        engine.execute("CREATE OR REPLACE TABLE dm (k VARCHAR, d DECFLOAT)");
        engine.execute("INSERT INTO dm VALUES ('a', 1)");
        engine.execute("UPDATE dm SET d = 0.1::FLOAT");
        engine.execute("MERGE INTO dm USING (SELECT 1 AS x) s ON FALSE WHEN NOT MATCHED THEN INSERT (k, d) VALUES "
            + "('m', 0.1::FLOAT)");
        engine.execute("CREATE OR REPLACE TABLE dv (v VARIANT)");
        engine.execute("CREATE OR REPLACE TABLE dd (d DECFLOAT)");
        engine.execute("INSERT INTO dd SELECT 1.5 UNION ALL SELECT 1::DECFLOAT / 3");
        engine.execute("INSERT INTO dv SELECT TO_VARIANT(1.5::DECFLOAT)");
        engine.execute("INSERT INTO dv SELECT d FROM dd");
        assertCells(new String[][] {
            {"SELECT k, d::VARCHAR FROM dz ORDER BY k",
                "big|99999999999999997700000000000000000000 ;; cast|1.0000000000000000555111512312578270212e-1 ;; "
                + "col|0.100000000000000006 ;; sel|0.100000000000000006 ;; tdf|"
                + "1.0000000000000000555111512312578270212e-1 ;; third|0.33333299999999999 ;; tiny|"
                + "1.00000000000000002e-100 ;; val|0.100000000000000006"},
            {"SELECT k, d::VARCHAR FROM dm ORDER BY k", "a|0.100000000000000006 ;; m|0.100000000000000006"},
            {"SELECT TYPEOF(v), TO_JSON(v), v::VARCHAR FROM dv ORDER BY 3",
                "DECFLOAT|\"1.5\"|1.5 ;; DECFLOAT|\"1.5\"|1.5 ;; DECFLOAT|"
                + "\"3.3333333333333333333333333333333333333e-1\"|3.3333333333333333333333333333333333333e-1"},
            {"SELECT MAX(v)::VARCHAR, MIN(v)::VARCHAR FROM dv", "1.5|3.3333333333333333333333333333333333333e-1"},
            {"SELECT TYPEOF(v:a) FROM (SELECT OBJECT_CONSTRUCT('a', d) AS v FROM dd) ORDER BY 1",
                "DECFLOAT ;; DECFLOAT"},
        });
    }

    @Test
    public void scriptingKeepsADecfloatExact() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT DEFAULT 0.1; BEGIN RETURN y::VARCHAR; END$$", "0.1"},
            {"EXECUTE IMMEDIATE $$BEGIN LET y DECFLOAT := 0.1; RETURN y::VARCHAR; END$$", "0.1"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 0.1; RETURN y::VARCHAR; END$$", "0.1"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 0.1 + 0; RETURN y::VARCHAR; END$$", "0.1"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 0.333333333333333333333333333; RETURN y::VARCHAR; "
                + "END$$",
                "0.333333333333333333333333333"},
            {"EXECUTE IMMEDIATE $$BEGIN LET n NUMBER(38,20) := 0.1; RETURN n::VARCHAR; END$$",
                "0.10000000000000000000"},
            {"EXECUTE IMMEDIATE $$BEGIN LET f := 0.1; RETURN SYSTEM$TYPEOF(f) || ' ' || f::VARCHAR; END$$",
                "FLOAT[DOUBLE] 0.1"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := (SELECT 0.1); RETURN y::VARCHAR; END$$", "0.1"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 0.1::FLOAT; RETURN y::VARCHAR; END$$",
                "1.0000000000000000555111512312578270212e-1"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 1::DECFLOAT / 3; RETURN (y * 3)::VARCHAR; END$$",
                "9.9999999999999999999999999999999999999e-1"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 1::DECFLOAT / 3; RETURN y || '|'; END$$",
                "3.3333333333333333333333333333333333333e-1|"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 1::DECFLOAT / 3; RETURN y; END$$",
                "0.33333333333333333333333333333333333333"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 2; RETURN y / 3; END$$",
                "0.66666666666666666666666666666666666667"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 2; IF (y / 3 > 0.6) THEN RETURN 'gt'; END IF; RETURN "
                + "'le'; END$$",
                "gt"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 2; RETURN -y / 3; END$$",
                "-0.66666666666666666666666666666666666667"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; BEGIN y := 2; RETURN ABS(y / 3); END$$",
                "0.66666666666666666666666666666666666667"},
            {"EXECUTE IMMEDIATE $$DECLARE y DECFLOAT; z DECFLOAT; BEGIN y := 2; z := y / 3; RETURN z::VARCHAR || ' ' |"
                + "| SYSTEM$TYPEOF(z); END$$",
                "6.6666666666666666666666666666666666667e-1 DECFLOAT(38)[DECFLOAT18]"},
        });
    }

    /**
     * A SQL table function's DECFLOAT parameter is the DECFLOAT its argument is, every digit kept, as a scalar
     * function's is: its plain digits made the body read a NUMBER, a FLOAT past 38 of them, and refused 1.5e300 as an
     * integer literal out of range.
     */
    @Test
    public void aTableFunctionReadsADecfloatArgumentAsTheDecfloatItIs() {
        engine.execute("CREATE OR REPLACE FUNCTION df_st2(x DECFLOAT) RETURNS TABLE (y VARCHAR, t VARCHAR) AS "
            + "'SELECT x::VARCHAR, SYSTEM$TYPEOF(x)'");
        engine.execute("CREATE OR REPLACE FUNCTION df_st3(x DECFLOAT) RETURNS TABLE (y DECFLOAT) AS 'SELECT x / 3'");
        engine.execute("CREATE OR REPLACE FUNCTION df_sc2(x DECFLOAT) RETURNS VARCHAR AS "
            + "'x::VARCHAR || SYSTEM$TYPEOF(x)'");
        engine.execute("CREATE OR REPLACE TABLE df_args (d DECFLOAT)");
        engine.execute("INSERT INTO df_args SELECT DECFLOAT '1.2345678901234567890123456789012345678e-20'");
        assertCells(new String[][] {
            {"SELECT * FROM TABLE(df_st2(DECFLOAT '1.5'))", "1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT * FROM TABLE(df_st2(DECFLOAT '-1.5'))", "-1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT * FROM TABLE(df_st2(DECFLOAT '1' / 3))",
                "3.3333333333333333333333333333333333333e-1|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT * FROM TABLE(df_st2(DECFLOAT '1.5e300'))", "1.5e300|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT * FROM TABLE(df_st2(DECFLOAT '1e-50'))", "1e-50|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT y::VARCHAR FROM TABLE(df_st3(DECFLOAT '1'))", "3.3333333333333333333333333333333333333e-1"},
            {"SELECT t.* FROM df_args, TABLE(df_st2(df_args.d)) t",
                "1.2345678901234567890123456789012345678e-20|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT df_sc2(DECFLOAT '1.5')", "1.5DECFLOAT(38)[DECFLOAT18]"},
        });
    }

    /**
     * A DECFLOAT argument to a SQL function's VARIANT parameter is a DECFLOAT member, every digit kept, as
     * {@code TO_VARIANT} of it is: it was the VARIANT of its plain digits, a DOUBLE past 38 of them, a DECIMAL
     * otherwise, and 1e300 refused. A table function's VARIANT parameter takes a VARIANT only, a DECFLOAT refused as a
     * NUMBER is (see TableFunctionSignatures), and keeps the DECFLOAT inside the VARIANT it is given.
     */
    @Test
    public void aVariantParameterHoldsADecfloatArgumentAsADecfloat() {
        engine.execute("CREATE OR REPLACE FUNCTION df_sv(v VARIANT) RETURNS VARCHAR AS 'TYPEOF(v) || '' '' || "
            + "TO_JSON(v)'");
        engine.execute("CREATE OR REPLACE FUNCTION df_sw(v VARIANT) RETURNS VARIANT AS 'v'");
        engine.execute("CREATE OR REPLACE FUNCTION df_tv(v VARIANT) RETURNS TABLE (t VARCHAR, j VARCHAR) AS "
            + "'SELECT TYPEOF(v), TO_JSON(v)'");
        engine.execute("CREATE OR REPLACE TABLE df_vargs (d DECFLOAT)");
        engine.execute("INSERT INTO df_vargs SELECT DECFLOAT '-1.2345678901234567890123456789012345678e-20'");
        assertCells(new String[][] {
            {"SELECT df_sv(DECFLOAT '1' / 3), df_sv(DECFLOAT '1.5'), "
                + "df_sv(DECFLOAT '1.2345678901234567890123456789012345678e-20')",
                "DECFLOAT \"3.3333333333333333333333333333333333333e-1\"|DECFLOAT \"1.5\"|"
                + "DECFLOAT \"1.2345678901234567890123456789012345678e-20\""},
            {"SELECT df_sv(DECFLOAT '1e300')", "DECFLOAT \"1e300\""},
            {"SELECT df_sv(d) FROM df_vargs", "DECFLOAT \"-1.2345678901234567890123456789012345678e-20\""},
            {"SELECT df_sv(1.5), df_sv(NULL::DECFLOAT)", "DECIMAL 1.5|null"},
            {"SELECT df_sw(DECFLOAT '1.2345678901234567890123456789012345678e-20')::VARCHAR",
                "1.2345678901234567890123456789012345678e-20"},
            {"SELECT * FROM TABLE(df_tv(DECFLOAT '1e-50'))",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'DF_TV': (DECFLOAT(38))"},
            {"SELECT * FROM TABLE(df_tv(1))",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'DF_TV': (NUMBER(1,0))"},
            {"SELECT * FROM TABLE(df_tv(TO_VARIANT(DECFLOAT '1e-50')))", "DECFLOAT|\"1e-50\""},
        });
    }
}
