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
 * A column a routine's RETURNS TABLE declares as a text or a binary of no width is of no width of its own, where
 * a table's column of the same spelling takes the default one. A procedure's result reads it at the widest a
 * value can be - VARCHAR(134217728), BINARY(67108864) - in a CALL, as a relation in FROM, over the CALL's
 * RESULT_SCAN and through a procedure that returns another's table, and SYSTEM$TYPEOF names it at that width; a
 * table function's reads the same through a driver, and SYSTEM$TYPEOF names it bare. A table created over either
 * declares VARCHAR(16777216) and BINARY(8388608), and so does a view's description. A width that is written is
 * kept, the default one included: VARCHAR(16777216) reads 16777216 and BINARY(8388608) 8388608. STRING, TEXT,
 * NVARCHAR and CHARACTER VARYING read as VARCHAR does; CHAR keeps its one character. A procedure declaring no
 * columns answers the table as returned, and is no relation in FROM. The routine's DDL writes each column back
 * as declared; its description, its listing and INFORMATION_SCHEMA name the type alone. Every answer is
 * live-verified.
 */
public class RoutineResultColumnWidthsTest extends MeasuredCellsTestSupport {

    @Test
    public void aBareTextOrBinaryColumnIsOfNoWidthOfItsOwn() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_v() RETURNS TABLE (a VARCHAR) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT 'x'::VARCHAR AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_v3() RETURNS TABLE (a VARCHAR(3)) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET DEFAULT (SELECT 'x'::VARCHAR(3) AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_s() RETURNS TABLE (a STRING) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT 'x'::VARCHAR AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_t() RETURNS TABLE (a TEXT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT 'x' AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_c() RETURNS TABLE (a CHAR) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT 'x'::CHAR AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_b() RETURNS TABLE (a BINARY) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT TO_BINARY('00') AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_b5() RETURNS TABLE (a BINARY(5)) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET DEFAULT (SELECT TO_BINARY('00')::BINARY(5) AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_n() RETURNS TABLE (a NUMBER) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT 1 AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_n52() RETURNS TABLE (a NUMBER(5,2)) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET DEFAULT (SELECT 1.5::NUMBER(5,2) AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_f() RETURNS TABLE (a FLOAT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT 1.5::FLOAT AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_vt() RETURNS TABLE (a VARIANT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT TO_VARIANT(1) AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_ar() RETURNS TABLE (a ARRAY) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT ARRAY_CONSTRUCT(1) AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_ts() RETURNS TABLE (a TIMESTAMP_NTZ) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET DEFAULT (SELECT '2024-01-01'::TIMESTAMP_NTZ AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_ts3() RETURNS TABLE (a TIMESTAMP_NTZ(3)) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET DEFAULT (SELECT '2024-01-01'::TIMESTAMP_NTZ(3) AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_tm() RETURNS TABLE (a TIME) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT '01:02:03'::TIME AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_bo() RETURNS TABLE (a BOOLEAN) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT TRUE AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_d() RETURNS TABLE (a DATE) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT '2024-01-01'::DATE AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_e() RETURNS TABLE () LANGUAGE SQL AS $$ DECLARE r RESULTSET DEFAULT "
                + "(SELECT 'x'::VARCHAR AS a, 'y'::VARCHAR(3) AS b, 'z' AS c); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_two() RETURNS TABLE (a VARCHAR, b VARCHAR(7)) LANGUAGE SQL AS $$ "
                + "DECLARE r RESULTSET DEFAULT (SELECT 'x' AS a, 'y' AS b); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE sp_v() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'x'; END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE sp_v3() RETURNS VARCHAR(3) LANGUAGE SQL AS $$ BEGIN RETURN 'x'; END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE sp_b() RETURNS BINARY LANGUAGE SQL AS $$ BEGIN RETURN TO_BINARY('00'); "
                + "END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE sp_n() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; END $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION fn_v() RETURNS VARCHAR AS $$ 'x' $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION fn_v3() RETURNS VARCHAR(3) AS $$ 'x' $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION fn_b() RETURNS BINARY AS $$ TO_BINARY('00') $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION fn_n() RETURNS NUMBER AS $$ 1 $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION tf_v() RETURNS TABLE (a VARCHAR, b VARCHAR(3), c BINARY) AS $$ SELECT "
                + "'x'::VARCHAR, 'y'::VARCHAR(3), TO_BINARY('00') $$");
        assertShape(
            "A:VARCHAR(134217728)",
            "CALL rt_v()");
        assertShape(
            "A:VARCHAR(3)",
            "CALL rt_v3()");
        assertShape(
            "A:VARCHAR(134217728)",
            "CALL rt_s()");
        assertShape(
            "A:VARCHAR(134217728)",
            "CALL rt_t()");
        assertShape(
            "A:VARCHAR(1)",
            "CALL rt_c()");
        assertShape(
            "A:BINARY",
            "CALL rt_b()");
        assertShape(
            "A:BINARY",
            "CALL rt_b5()");
        assertShape(
            "A:NUMBER(38,0)",
            "CALL rt_n()");
        assertShape(
            "A:NUMBER(5,2)",
            "CALL rt_n52()");
        assertShape(
            "A:VARIANT",
            "CALL rt_vt()");
        assertShape(
            "A:ARRAY",
            "CALL rt_ar()");
        assertShape(
            "A:TIMESTAMP_NTZ",
            "CALL rt_ts()");
        assertShape(
            "A:TIMESTAMP_NTZ",
            "CALL rt_ts3()");
        assertShape(
            "A:TIME",
            "CALL rt_tm()");
        assertShape(
            "A:BOOLEAN",
            "CALL rt_bo()");
        assertShape(
            "A:DATE",
            "CALL rt_d()");
        assertShape(
            "A:VARCHAR(134217728), B:VARCHAR(3), C:VARCHAR(1)",
            "CALL rt_e()");
        assertShape(
            "A:VARCHAR(134217728), B:VARCHAR(7)",
            "CALL rt_two()");
        assertShape(
            "A:VARCHAR(134217728)",
            "SELECT * FROM TABLE(rt_v())");
        assertShape(
            "A:VARCHAR(3)",
            "SELECT * FROM TABLE(rt_v3())");
        assertShape(
            "A:BINARY",
            "SELECT * FROM TABLE(rt_b())");
        assertShape(
            "A:NUMBER(38,0)",
            "SELECT * FROM TABLE(rt_n())");
        assertShape(
            "A:VARCHAR(134217728), B:VARCHAR(7)",
            "SELECT * FROM TABLE(rt_two())");
        assertShape(
            "REFUSED SQL compilation error: Invalid stored procedure 'RT_E' in FROM clause: Return table must "
                + "declare a nonzero number of columns.",
            "SELECT * FROM TABLE(rt_e())");
        assertShape(
            "SP_V:VARCHAR(134217728)",
            "CALL sp_v()");
        assertShape(
            "SP_V3:VARCHAR(134217728)",
            "CALL sp_v3()");
        assertShape(
            "SP_B:BINARY",
            "CALL sp_b()");
        assertShape(
            "SP_N:NUMBER(38,0)",
            "CALL sp_n()");
        assertShape(
            "FN_V():VARCHAR(1), FN_V3():VARCHAR(1), FN_B():BINARY, FN_N():NUMBER(1,0)",
            "SELECT fn_v(), fn_v3(), fn_b(), fn_n()");
        assertShape(
            "A:VARCHAR(134217728), B:VARCHAR(3), C:BINARY",
            "SELECT * FROM TABLE(tf_v())");
        assertShape(
            "A:VARCHAR(134217728), B:VARCHAR(3), C:BINARY",
            "SELECT a, b, c FROM TABLE(tf_v())");
        assertAnswer(
            "x",
            "CALL rt_v()");
        assertShape(
            "A:VARCHAR(134217728)",
            "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "x|y",
            "CALL rt_two()");
        assertAnswer(
            "Table RT_CTAS successfully created.",
            "CREATE OR REPLACE TABLE rt_ctas AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Table RT_CTAS2 successfully created.",
            "CREATE OR REPLACE TABLE rt_ctas2 AS SELECT * FROM TABLE(rt_v())");
        assertAnswer(
            "A|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE rt_ctas2");
        assertAnswer(
            "Table RT_CTAS3 successfully created.",
            "CREATE OR REPLACE TABLE rt_ctas3 AS SELECT fn_v() AS a, fn_b() AS b, sysdate()::VARCHAR AS c");
        assertAnswer(
            "Table RT_CTAS4 successfully created.",
            "CREATE OR REPLACE TABLE rt_ctas4 AS SELECT * FROM TABLE(tf_v())");
        assertShape(
            "A:VARCHAR(134217728)",
            "CALL rt_v() ->> SELECT * FROM $1");
        assertShape(
            "property:VARCHAR(16777216), value:VARCHAR(16777216)",
            "DESCRIBE PROCEDURE rt_v()");
        assertAnswer(
            "TABLE (A BINARY)|null ;; TABLE (A VARCHAR, B VARCHAR)|null ;; TABLE (A VARCHAR)|null ;; "
                + "VARCHAR(134217728)|134217728",
            "SELECT data_type, character_maximum_length FROM information_schema.procedures WHERE procedure_name "
                + "IN ('RT_V', 'RT_B', 'SP_V', 'RT_TWO') ORDER BY procedure_name");
    }

    @Test
    public void aWrittenWidthIsKeptAndExpressionsFollowTheColumn() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_v() RETURNS TABLE (a VARCHAR) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT 'x'::VARCHAR AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_b() RETURNS TABLE (a BINARY) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT TO_BINARY('00') AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_x() RETURNS TABLE (a VARCHAR(16777216), b BINARY(8388608)) LANGUAGE "
                + "SQL AS $$ DECLARE r RESULTSET DEFAULT (SELECT 'x'::VARCHAR AS a, TO_BINARY('00') AS b); BEGIN "
                + "RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_y() RETURNS TABLE (a VARCHAR(134217728), b BINARY(67108864)) LANGUAGE "
                + "SQL AS $$ DECLARE r RESULTSET DEFAULT (SELECT 'x'::VARCHAR AS a, TO_BINARY('00') AS b); BEGIN "
                + "RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_vb() RETURNS TABLE (a VARBINARY, b NVARCHAR, c CHARACTER VARYING, d "
                + "CHAR(4)) LANGUAGE SQL AS $$ DECLARE r RESULTSET DEFAULT (SELECT TO_BINARY('00') AS a, "
                + "'x'::VARCHAR AS b, 'y'::VARCHAR AS c, 'z'::CHAR(4) AS d); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION tf_v() RETURNS TABLE (a VARCHAR, b VARCHAR(3), c BINARY) AS $$ SELECT "
                + "'x'::VARCHAR, 'y'::VARCHAR(3), TO_BINARY('00') $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION tf_x() RETURNS TABLE (a VARCHAR(16777216), b BINARY(8388608)) AS $$ "
                + "SELECT 'x'::VARCHAR, TO_BINARY('00') $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION tf_lit() RETURNS TABLE (a VARCHAR, b NUMBER) AS $$ SELECT 'abc', 1 $$");
        engine.execute(
            "CREATE OR REPLACE VIEW rt_view AS SELECT * FROM TABLE(tf_v())");
        assertShape(
            "A:VARCHAR(16777216), B:BINARY",
            "CALL rt_x()");
        assertShape(
            "A:VARCHAR(134217728), B:BINARY",
            "CALL rt_y()");
        assertShape(
            "A:BINARY, B:VARCHAR(134217728), C:VARCHAR(134217728), D:VARCHAR(4)",
            "CALL rt_vb()");
        assertShape(
            "A:VARCHAR(16777216), B:BINARY",
            "SELECT * FROM TABLE(rt_x())");
        assertShape(
            "A:VARCHAR(134217728), B:BINARY",
            "SELECT * FROM TABLE(rt_y())");
        assertShape(
            "A:VARCHAR(16777216), B:BINARY",
            "SELECT * FROM TABLE(tf_x())");
        assertShape(
            "A:VARCHAR(134217728), B:NUMBER(38,0)",
            "SELECT * FROM TABLE(tf_lit())");
        assertAnswer(
            "x",
            "CALL rt_v()");
        assertAnswer(
            "VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(a) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "00",
            "CALL rt_b()");
        assertAnswer(
            "BINARY(67108864)[LOB]",
            "SELECT SYSTEM$TYPEOF(a) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(a) FROM TABLE(rt_v())");
        assertAnswer(
            "BINARY(67108864)[LOB]",
            "SELECT SYSTEM$TYPEOF(a) FROM TABLE(rt_b())");
        assertAnswer(
            "VARCHAR[LOB]|VARCHAR(3)[LOB]|BINARY[LOB]",
            "SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(b), SYSTEM$TYPEOF(c) FROM TABLE(tf_v())");
        assertAnswer(
            "VARCHAR(16777216)[LOB]|BINARY(8388608)[LOB]",
            "SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(b) FROM TABLE(tf_x())");
        assertAnswer(
            "VARCHAR(16777216)[LOB]|BINARY(8388608)[LOB]",
            "SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(b) FROM TABLE(rt_x())");
        assertShape(
            "A:VARCHAR(134217728), B:VARCHAR(3), C:BINARY",
            "SELECT * FROM rt_view");
        assertShape(
            "C:VARCHAR(134217728), U:VARCHAR(134217728), L:VARCHAR(134217728)",
            "SELECT a || 'x' AS c, UPPER(a) AS u, LEFT(a, 2) AS l FROM TABLE(rt_v())");
        assertShape(
            "C:VARCHAR(134217728), U:VARCHAR(134217728), D:VARCHAR(4)",
            "SELECT a || 'x' AS c, UPPER(a) AS u, b || 'z' AS d FROM TABLE(tf_v())");
        assertShape(
            "A:VARCHAR(134217728)",
            "SELECT a FROM TABLE(rt_v()) UNION ALL SELECT 'abc'");
        assertShape(
            "A:VARCHAR(134217728)",
            "SELECT a FROM TABLE(tf_v()) UNION ALL SELECT 'abc'");
        assertShape(
            "C:VARCHAR(134217728), D:VARCHAR(5)",
            "SELECT COALESCE(a, 'q') AS c, a::VARCHAR(5) AS d FROM TABLE(tf_v())");
        assertAnswer(
            "Table RT_C5 successfully created.",
            "CREATE OR REPLACE TABLE rt_c5 AS SELECT a, a || 'x' AS c FROM TABLE(rt_v())");
        assertAnswer(
            "Table RT_C6 successfully created.",
            "CREATE OR REPLACE TABLE rt_c6 AS SELECT * FROM TABLE(rt_x())");
        assertAnswer(
            "Table RT_C7 successfully created.",
            "CREATE OR REPLACE TABLE rt_c7 AS SELECT * FROM rt_view");
        assertAnswer(
            "TABLE (A BINARY, B VARCHAR, C VARCHAR, D VARCHAR)|null ;; TABLE (A VARCHAR, B BINARY)|null ;; TABLE "
                + "(A VARCHAR, B BINARY)|null",
            "SELECT data_type, character_maximum_length FROM information_schema.procedures WHERE procedure_name "
                + "IN ('RT_X', 'RT_Y', 'RT_VB') ORDER BY procedure_name");
        assertAnswer(
            "TABLE (A VARCHAR, B VARCHAR, C BINARY) ;; TABLE (A VARCHAR, B BINARY)",
            "SELECT data_type FROM information_schema.functions WHERE function_name IN ('TF_V', 'TF_X') ORDER BY "
                + "function_name");
    }

    @Test
    public void theDeclaredColumnsAreWrittenBackAsDeclared() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_v() RETURNS TABLE (a VARCHAR) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT 'x'::VARCHAR AS a); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_x() RETURNS TABLE (a VARCHAR(16777216), b BINARY(8388608)) LANGUAGE "
                + "SQL AS $$ DECLARE r RESULTSET DEFAULT (SELECT 'x'::VARCHAR AS a, TO_BINARY('00') AS b); BEGIN "
                + "RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_vb() RETURNS TABLE (a VARBINARY, b NVARCHAR, c CHARACTER VARYING, d "
                + "CHAR(4), e VARCHAR(7), f BINARY(5)) LANGUAGE SQL AS $$ DECLARE r RESULTSET DEFAULT (SELECT "
                + "TO_BINARY('00') AS a, 'x'::VARCHAR AS b, 'y'::VARCHAR AS c, 'z'::CHAR(4) AS d, 'w'::VARCHAR(7) AS "
                + "e, TO_BINARY('00')::BINARY(5) AS f); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_outer() RETURNS TABLE (a VARCHAR) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET DEFAULT (CALL rt_v()); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_outer_e() RETURNS TABLE () LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (CALL rt_v()); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE rt_outer3() RETURNS TABLE (a VARCHAR(3)) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET DEFAULT (CALL rt_v()); BEGIN RETURN TABLE(r); END $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION tf_v() RETURNS TABLE (a VARCHAR, b VARCHAR(3), c BINARY) AS $$ SELECT "
                + "'x'::VARCHAR, 'y'::VARCHAR(3), TO_BINARY('00') $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION tf_x() RETURNS TABLE (a VARCHAR(16777216), b BINARY(8388608)) AS $$ "
                + "SELECT 'x'::VARCHAR, TO_BINARY('00') $$");
        assertAnswer(
            "CREATE OR REPLACE PROCEDURE \"RT_V\"() | RETURNS TABLE (\"A\" VARCHAR) | LANGUAGE SQL | EXECUTE AS "
                + "OWNER | AS ' DECLARE r RESULTSET DEFAULT (SELECT ''x''::VARCHAR AS a); BEGIN RETURN TABLE(r); END "
                + "';",
            "SELECT GET_DDL('PROCEDURE', 'rt_v()')");
        assertAnswer(
            "CREATE OR REPLACE PROCEDURE \"RT_X\"() | RETURNS TABLE (\"A\" VARCHAR(16777216), \"B\" "
                + "BINARY(8388608)) | LANGUAGE SQL | EXECUTE AS OWNER | AS ' DECLARE r RESULTSET DEFAULT (SELECT "
                + "''x''::VARCHAR AS a, TO_BINARY(''00'') AS b); BEGIN RETURN TABLE(r); END ';",
            "SELECT GET_DDL('PROCEDURE', 'rt_x()')");
        assertAnswer(
            "CREATE OR REPLACE PROCEDURE \"RT_VB\"() | RETURNS TABLE (\"A\" BINARY, \"B\" VARCHAR, \"C\" VARCHAR, "
                + "\"D\" VARCHAR(4), \"E\" VARCHAR(7), \"F\" BINARY(5)) | LANGUAGE SQL | EXECUTE AS OWNER | AS ' "
                + "DECLARE r RESULTSET DEFAULT (SELECT TO_BINARY(''00'') AS a, ''x''::VARCHAR AS b, ''y''::VARCHAR "
                + "AS c, ''z''::CHAR(4) AS d, ''w''::VARCHAR(7) AS e, TO_BINARY(''00'')::BINARY(5) AS f); BEGIN "
                + "RETURN TABLE(r); END ';",
            "SELECT GET_DDL('PROCEDURE', 'rt_vb()')");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"TF_V\"() | RETURNS TABLE (\"A\" VARCHAR, \"B\" VARCHAR(3), \"C\" "
                + "BINARY) | LANGUAGE SQL | AS ' SELECT ''x''::VARCHAR, ''y''::VARCHAR(3), TO_BINARY(''00'') ';",
            "SELECT GET_DDL('FUNCTION', 'tf_v()')");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"TF_X\"() | RETURNS TABLE (\"A\" VARCHAR(16777216), \"B\" "
                + "BINARY(8388608)) | LANGUAGE SQL | AS ' SELECT ''x''::VARCHAR, TO_BINARY(''00'') ';",
            "SELECT GET_DDL('FUNCTION', 'tf_x()')");
        assertAnswer(
            "RT_OUTER|RT_OUTER() RETURN TABLE (A VARCHAR) ;; RT_OUTER3|RT_OUTER3() RETURN TABLE (A VARCHAR) ;; "
                + "RT_OUTER_E|RT_OUTER_E() RETURN TABLE () ;; RT_V|RT_V() RETURN TABLE (A VARCHAR) ;; RT_VB|RT_VB() "
                + "RETURN TABLE (A BINARY, B VARCHAR, C VARCHAR, D VARCHAR, E VARCHAR, F BINARY) ;; RT_X|RT_X() "
                + "RETURN TABLE (A VARCHAR, B BINARY)",
            "SHOW PROCEDURES LIKE 'RT_%' ->> SELECT \"name\", \"arguments\" FROM $1 ORDER BY \"name\"");
        assertAnswer(
            "TF_V|TF_V() RETURN TABLE (A VARCHAR, B VARCHAR, C BINARY) ;; TF_X|TF_X() RETURN TABLE (A VARCHAR, B "
                + "BINARY)",
            "SHOW USER FUNCTIONS LIKE 'TF_%' ->> SELECT \"name\", \"arguments\" FROM $1 ORDER BY \"name\"");
        assertAnswer(
            "TF_V|()|TABLE (A VARCHAR, B VARCHAR, C BINARY)|null ;; TF_X|()|TABLE (A VARCHAR, B BINARY)|null",
            "SELECT function_name, argument_signature, data_type, character_maximum_length FROM "
                + "information_schema.functions WHERE function_name LIKE 'TF!_%' ESCAPE '!' ORDER BY function_name");
        assertAnswer(
            "RT_OUTER|()|TABLE (A VARCHAR) ;; RT_OUTER3|()|TABLE (A VARCHAR) ;; RT_OUTER_E|()|TABLE () ;; "
                + "RT_V|()|TABLE (A VARCHAR) ;; RT_VB|()|TABLE (A BINARY, B VARCHAR, C VARCHAR, D VARCHAR, E VARCHAR,"
                + " F BINARY) ;; RT_X|()|TABLE (A VARCHAR, B BINARY)",
            "SELECT procedure_name, argument_signature, data_type FROM information_schema.procedures WHERE "
                + "procedure_name LIKE 'RT!_%' ESCAPE '!' ORDER BY procedure_name");
        assertShape(
            "A:VARCHAR(134217728)",
            "CALL rt_outer()");
        assertShape(
            "A:VARCHAR(134217728)",
            "CALL rt_outer_e()");
        assertShape(
            "A:VARCHAR(3)",
            "CALL rt_outer3()");
        assertAnswer(
            "x",
            "CALL rt_outer3()");
        assertShape(
            "A:VARCHAR(134217728)",
            "SELECT * FROM TABLE(rt_outer())");
        assertAnswer(
            "x",
            "CALL rt_outer()");
        assertAnswer(
            "VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(a) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertShape(
            "A:VARCHAR(134217728), B:VARCHAR(134217728)",
            "SELECT t.a, u.a AS b FROM TABLE(rt_v()) t JOIN TABLE(tf_v()) u ON t.a = u.a");
        assertShape(
            "M:VARCHAR(134217728), L:VARCHAR(134217728), C:NUMBER(18,0)",
            "SELECT MAX(a) AS m, LISTAGG(a) AS l, COUNT(a) AS c FROM TABLE(rt_v())");
        assertShape(
            "M:VARCHAR(134217728), N:BINARY",
            "SELECT MAX(a) AS m, MAX(c) AS n FROM TABLE(tf_v())");
    }
}
