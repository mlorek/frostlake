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
package dev.frostlake.jdbc;

import dev.frostlake.BaseJdbcTest;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a driver reads of the columns a routine answers (live-verified): a procedure's RETURNS TABLE column of a
 * text or a binary written with no width reads VARCHAR 134217728 and BINARY 67108864 - in a CALL, as a relation
 * in FROM and over the CALL's RESULT_SCAN - and so does a table function's, a view over it and what is computed
 * from it, while a table created over either declares 16777216 and 8388608. A width that is written is read as
 * written, the default one included. A scalar procedure's result is a text or a binary of no width whatever its
 * RETURNS wrote, and a SQL function's is its body's own type.
 */
public class RoutineResultColumnTypesTest extends BaseJdbcTest {

    @Override
    protected void setupTest() throws SQLException {
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_v() RETURNS TABLE (a VARCHAR) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT 'x'::VARCHAR AS a); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_v3() RETURNS TABLE (a VARCHAR(3)) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET DEFAULT (SELECT 'x'::VARCHAR(3) AS a); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_s() RETURNS TABLE (a STRING) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT 'x'::VARCHAR AS a); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_t() RETURNS TABLE (a TEXT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT 'x' AS a); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_c() RETURNS TABLE (a CHAR) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT 'x'::CHAR AS a); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_b() RETURNS TABLE (a BINARY) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT TO_BINARY('00') AS a); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_b5() RETURNS TABLE (a BINARY(5)) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET DEFAULT (SELECT TO_BINARY('00')::BINARY(5) AS a); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_n() RETURNS TABLE (a NUMBER) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT 1 AS a); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_n52() RETURNS TABLE (a NUMBER(5,2)) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET DEFAULT (SELECT 1.5::NUMBER(5,2) AS a); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_f() RETURNS TABLE (a FLOAT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT 1.5::FLOAT AS a); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_vt() RETURNS TABLE (a VARIANT) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT TO_VARIANT(1) AS a); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_ar() RETURNS TABLE (a ARRAY) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT ARRAY_CONSTRUCT(1) AS a); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_ts() RETURNS TABLE (a TIMESTAMP_NTZ) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET DEFAULT (SELECT '2024-01-01'::TIMESTAMP_NTZ AS a); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_ts3() RETURNS TABLE (a TIMESTAMP_NTZ(3)) LANGUAGE SQL AS $$ DECLARE r "
                + "RESULTSET DEFAULT (SELECT '2024-01-01'::TIMESTAMP_NTZ(3) AS a); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_tm() RETURNS TABLE (a TIME) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT '01:02:03'::TIME AS a); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_bo() RETURNS TABLE (a BOOLEAN) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT TRUE AS a); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_d() RETURNS TABLE (a DATE) LANGUAGE SQL AS $$ DECLARE r RESULTSET "
                + "DEFAULT (SELECT '2024-01-01'::DATE AS a); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_e() RETURNS TABLE () LANGUAGE SQL AS $$ DECLARE r RESULTSET DEFAULT "
                + "(SELECT 'x'::VARCHAR AS a, 'y'::VARCHAR(3) AS b, 'z' AS c); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_two() RETURNS TABLE (a VARCHAR, b VARCHAR(7)) LANGUAGE SQL AS $$ "
                + "DECLARE r RESULTSET DEFAULT (SELECT 'x' AS a, 'y' AS b); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE sp_v() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'x'; END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE sp_v3() RETURNS VARCHAR(3) LANGUAGE SQL AS $$ BEGIN RETURN 'x'; END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE sp_b() RETURNS BINARY LANGUAGE SQL AS $$ BEGIN RETURN TO_BINARY('00'); "
                + "END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE sp_n() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1; END $$");
        statement.execute(
            "CREATE OR REPLACE FUNCTION fn_v() RETURNS VARCHAR AS $$ 'x' $$");
        statement.execute(
            "CREATE OR REPLACE FUNCTION fn_v3() RETURNS VARCHAR(3) AS $$ 'x' $$");
        statement.execute(
            "CREATE OR REPLACE FUNCTION fn_b() RETURNS BINARY AS $$ TO_BINARY('00') $$");
        statement.execute(
            "CREATE OR REPLACE FUNCTION fn_n() RETURNS NUMBER AS $$ 1 $$");
        statement.execute(
            "CREATE OR REPLACE FUNCTION tf_v() RETURNS TABLE (a VARCHAR, b VARCHAR(3), c BINARY) AS $$ SELECT "
                + "'x'::VARCHAR, 'y'::VARCHAR(3), TO_BINARY('00') $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_x() RETURNS TABLE (a VARCHAR(16777216), b BINARY(8388608)) LANGUAGE "
                + "SQL AS $$ DECLARE r RESULTSET DEFAULT (SELECT 'x'::VARCHAR AS a, TO_BINARY('00') AS b); BEGIN "
                + "RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_y() RETURNS TABLE (a VARCHAR(134217728), b BINARY(67108864)) LANGUAGE "
                + "SQL AS $$ DECLARE r RESULTSET DEFAULT (SELECT 'x'::VARCHAR AS a, TO_BINARY('00') AS b); BEGIN "
                + "RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE PROCEDURE rt_vb() RETURNS TABLE (a VARBINARY, b NVARCHAR, c CHARACTER VARYING, d "
                + "CHAR(4)) LANGUAGE SQL AS $$ DECLARE r RESULTSET DEFAULT (SELECT TO_BINARY('00') AS a, "
                + "'x'::VARCHAR AS b, 'y'::VARCHAR AS c, 'z'::CHAR(4) AS d); BEGIN RETURN TABLE(r); END $$");
        statement.execute(
            "CREATE OR REPLACE FUNCTION tf_x() RETURNS TABLE (a VARCHAR(16777216), b BINARY(8388608)) AS $$ "
                + "SELECT 'x'::VARCHAR, TO_BINARY('00') $$");
        statement.execute(
            "CREATE OR REPLACE FUNCTION tf_lit() RETURNS TABLE (a VARCHAR, b NUMBER) AS $$ SELECT 'abc', 1 $$");
        statement.execute(
            "CREATE OR REPLACE VIEW rt_view AS SELECT * FROM TABLE(tf_v())");
        statement.execute(
            "CALL rt_two()");
        statement.execute(
            "CREATE OR REPLACE TABLE rt_ctas AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        statement.execute(
            "CREATE OR REPLACE TABLE rt_ctas2 AS SELECT * FROM TABLE(rt_v())");
        statement.execute(
            "CREATE OR REPLACE TABLE rt_ctas3 AS SELECT fn_v() AS a, fn_b() AS b");
        statement.execute(
            "CREATE OR REPLACE TABLE rt_ctas4 AS SELECT * FROM TABLE(tf_v())");
    }

    /**
     * A result's columns as the driver declares them: each a label, a type name, a precision, a scale and a
     * display size.
     */
    private String declared(final String sql) throws SQLException {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            final ResultSetMetaData md = rs.getMetaData();
            final StringBuilder text = new StringBuilder();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                text.append(i == 1 ? "" : ", ").append(md.getColumnLabel(i)).append(' ')
                    .append(md.getColumnTypeName(i)).append(' ').append(md.getPrecision(i)).append(' ')
                    .append(md.getScale(i)).append(" display ").append(md.getColumnDisplaySize(i));
            }
            return text.toString();
        }
    }

    @Test
    public void aTableProceduresCallReadsEachColumnAsDeclared() throws SQLException {
        assertEquals(
            "A VARCHAR 134217728 0 display 134217728",
            declared("CALL rt_v()"));
        assertEquals(
            "A VARCHAR 3 0 display 3",
            declared("CALL rt_v3()"));
        assertEquals(
            "A VARCHAR 134217728 0 display 134217728",
            declared("CALL rt_s()"));
        assertEquals(
            "A VARCHAR 134217728 0 display 134217728",
            declared("CALL rt_t()"));
        assertEquals(
            "A VARCHAR 1 0 display 1",
            declared("CALL rt_c()"));
        assertEquals(
            "A BINARY 67108864 0 display 67108864",
            declared("CALL rt_b()"));
        assertEquals(
            "A BINARY 5 0 display 5",
            declared("CALL rt_b5()"));
        assertEquals(
            "A NUMBER 38 0 display 39",
            declared("CALL rt_n()"));
        assertEquals(
            "A NUMBER 5 2 display 7",
            declared("CALL rt_n52()"));
        assertEquals(
            "A DOUBLE 0 0 display 24",
            declared("CALL rt_f()"));
        assertEquals(
            "A VARIANT 0 0 display 0",
            declared("CALL rt_vt()"));
        assertEquals(
            "A ARRAY 0 0 display 0",
            declared("CALL rt_ar()"));
        assertEquals(
            "A TIMESTAMPNTZ 23 9 display 23",
            declared("CALL rt_ts()"));
        assertEquals(
            "A TIMESTAMPNTZ 23 3 display 23",
            declared("CALL rt_ts3()"));
        assertEquals(
            "A TIME 8 9 display 8",
            declared("CALL rt_tm()"));
        assertEquals(
            "A BOOLEAN 0 0 display 5",
            declared("CALL rt_bo()"));
        assertEquals(
            "A DATE 10 0 display 10",
            declared("CALL rt_d()"));
        assertEquals(
            "A VARCHAR 134217728 0 display 134217728, B VARCHAR 3 0 display 3, C VARCHAR 1 0 display 1",
            declared("CALL rt_e()"));
        assertEquals(
            "A VARCHAR 134217728 0 display 134217728, B VARCHAR 7 0 display 7",
            declared("CALL rt_two()"));
        assertEquals(
            "A VARCHAR 134217728 0 display 134217728",
            declared("CALL rt_v() ->> SELECT * FROM $1"));
        assertEquals(
            "A VARCHAR 16777216 0 display 16777216, B BINARY 8388608 0 display 8388608",
            declared("CALL rt_x()"));
        assertEquals(
            "A VARCHAR 134217728 0 display 134217728, B BINARY 67108864 0 display 67108864",
            declared("CALL rt_y()"));
        assertEquals(
            "A BINARY 67108864 0 display 67108864, B VARCHAR 134217728 0 display 134217728, C VARCHAR 134217728 0 "
                + "display 134217728, D VARCHAR 4 0 display 4",
            declared("CALL rt_vb()"));
    }

    @Test
    public void aTableProcedureInFromAndATableFunctionReadTheSame() throws SQLException {
        assertEquals(
            "A VARCHAR 134217728 0 display 134217728",
            declared("SELECT * FROM TABLE(rt_v())"));
        assertEquals(
            "A VARCHAR 3 0 display 3",
            declared("SELECT * FROM TABLE(rt_v3())"));
        assertEquals(
            "A BINARY 67108864 0 display 67108864",
            declared("SELECT * FROM TABLE(rt_b())"));
        assertEquals(
            "A NUMBER 38 0 display 39",
            declared("SELECT * FROM TABLE(rt_n())"));
        assertEquals(
            "A VARCHAR 134217728 0 display 134217728, B VARCHAR 7 0 display 7",
            declared("SELECT * FROM TABLE(rt_two())"));
        assertEquals(
            "A VARCHAR 134217728 0 display 134217728, B VARCHAR 3 0 display 3, C BINARY 67108864 0 display "
                + "67108864",
            declared("SELECT * FROM TABLE(tf_v())"));
        assertEquals(
            "A VARCHAR 134217728 0 display 134217728, B VARCHAR 3 0 display 3, C BINARY 67108864 0 display "
                + "67108864",
            declared("SELECT a, b, c FROM TABLE(tf_v())"));
        assertEquals(
            "A VARCHAR 16777216 0 display 16777216, B BINARY 8388608 0 display 8388608",
            declared("SELECT * FROM TABLE(rt_x())"));
        assertEquals(
            "A VARCHAR 134217728 0 display 134217728, B BINARY 67108864 0 display 67108864",
            declared("SELECT * FROM TABLE(rt_y())"));
        assertEquals(
            "A VARCHAR 16777216 0 display 16777216, B BINARY 8388608 0 display 8388608",
            declared("SELECT * FROM TABLE(tf_x())"));
        assertEquals(
            "A VARCHAR 134217728 0 display 134217728, B NUMBER 38 0 display 39",
            declared("SELECT * FROM TABLE(tf_lit())"));
        assertEquals(
            "C VARCHAR 134217728 0 display 134217728, U VARCHAR 134217728 0 display 134217728, L VARCHAR "
                + "134217728 0 display 134217728",
            declared("SELECT a || 'x' AS c, UPPER(a) AS u, LEFT(a, 2) AS l FROM TABLE(rt_v())"));
        assertEquals(
            "C VARCHAR 134217728 0 display 134217728, U VARCHAR 134217728 0 display 134217728, D VARCHAR 4 0 "
                + "display 4",
            declared("SELECT a || 'x' AS c, UPPER(a) AS u, b || 'z' AS d FROM TABLE(tf_v())"));
        assertEquals(
            "A VARCHAR 134217728 0 display 134217728",
            declared("SELECT a FROM TABLE(rt_v()) UNION ALL SELECT 'abc'"));
        assertEquals(
            "A VARCHAR 134217728 0 display 134217728",
            declared("SELECT a FROM TABLE(tf_v()) UNION ALL SELECT 'abc'"));
        assertEquals(
            "C VARCHAR 134217728 0 display 134217728, D VARCHAR 5 0 display 5",
            declared("SELECT COALESCE(a, 'q') AS c, a::VARCHAR(5) AS d FROM TABLE(tf_v())"));
    }

    @Test
    public void scalarRoutinesAndWhatIsBuiltOverAResult() throws SQLException {
        assertEquals(
            "SP_V VARCHAR 134217728 0 display 134217728",
            declared("CALL sp_v()"));
        assertEquals(
            "SP_V3 VARCHAR 134217728 0 display 134217728",
            declared("CALL sp_v3()"));
        assertEquals(
            "SP_B BINARY 67108864 0 display 67108864",
            declared("CALL sp_b()"));
        assertEquals(
            "SP_N NUMBER 38 0 display 39",
            declared("CALL sp_n()"));
        assertEquals(
            "FN_V() VARCHAR 1 0 display 1, FN_V3() VARCHAR 1 0 display 1, FN_B() BINARY 67108864 0 display "
                + "67108864, FN_N() NUMBER 1 0 display 2",
            declared("SELECT fn_v(), fn_v3(), fn_b(), fn_n()"));
        assertEquals(
            "A VARCHAR 16777216 0 display 16777216, B VARCHAR 7 0 display 7",
            declared("SELECT * FROM rt_ctas"));
        assertEquals(
            "A VARCHAR 16777216 0 display 16777216",
            declared("SELECT * FROM rt_ctas2"));
        assertEquals(
            "A VARCHAR 1 0 display 1, B BINARY 8388608 0 display 8388608",
            declared("SELECT * FROM rt_ctas3"));
        assertEquals(
            "A VARCHAR 16777216 0 display 16777216, B VARCHAR 3 0 display 3, C BINARY 8388608 0 display 8388608",
            declared("SELECT * FROM rt_ctas4"));
        assertEquals(
            "A VARCHAR 134217728 0 display 134217728, B VARCHAR 3 0 display 3, C BINARY 67108864 0 display "
                + "67108864, D BINARY 5 0 display 5, E VARCHAR 3 0 display 3, F VARCHAR 134217728 0 display 134217728",
            declared("SELECT 'x'::VARCHAR AS a, 'x'::VARCHAR(3) AS b, TO_BINARY('00') AS c, TO_BINARY('00')::BINARY(5) "
                    + "AS d, 'lit' AS e, CURRENT_USER() AS f"));
        assertEquals(
            "A VARCHAR 134217728 0 display 134217728, B VARCHAR 3 0 display 3, C BINARY 67108864 0 display "
                + "67108864",
            declared("SELECT * FROM rt_view"));
    }
}
