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
package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How the listings spell a routine (live-verified). SHOW's {@code arguments} column lists the argument TYPES alone
 * and a bare return type; a table function or procedure prints {@code TABLE (<NAME> <TYPE>, …)} with bare type
 * names, a quoted column name as written, and is flagged in {@code is_table_function}. DESCRIBE's {@code returns}
 * row spells a table procedure the same way. INFORMATION_SCHEMA keeps the argument names and spells a scalar
 * return type in full, a text or binary of no declared width at its widest.
 */
public class RoutineListingSpellingTest extends BaseDatabaseTest {

    private static final String RESULT_SET = "DECLARE r RESULTSET DEFAULT (SELECT 1 AS a); BEGIN RETURN TABLE(r); END;";

    @Override
    protected void setupTest() {
        engine.execute("CREATE FUNCTION rls_f3(x INT, y VARCHAR) RETURNS VARCHAR AS $$ y $$");
        engine.execute("CREATE FUNCTION rls_fv10() RETURNS VARCHAR(10) AS $$ 'a' $$");
        engine.execute("CREATE FUNCTION rls_fc3() RETURNS CHAR(3) AS $$ 'a' $$");
        engine.execute("CREATE FUNCTION rls_fn() RETURNS NUMBER(10,2) AS $$ 1 $$");
        engine.execute("CREATE FUNCTION rls_fi() RETURNS INT AS $$ 1 $$");
        engine.execute("CREATE FUNCTION rls_fd() RETURNS DATE AS $$ CURRENT_DATE $$");
        engine.execute("CREATE FUNCTION rls_fvar() RETURNS VARIANT AS $$ 1::VARIANT $$");
        engine.execute("CREATE FUNCTION rls_ftz() RETURNS TIMESTAMP_TZ(3) AS $$ CURRENT_TIMESTAMP::TIMESTAMP_TZ(3) $$");
        engine.execute("CREATE FUNCTION rls_fntz() RETURNS TIMESTAMP_NTZ AS $$ CURRENT_TIMESTAMP::TIMESTAMP_NTZ $$");
        engine.execute("CREATE FUNCTION rls_ft() RETURNS TIME AS $$ CURRENT_TIME $$");
        engine.execute("CREATE FUNCTION rls_fb() RETURNS BOOLEAN AS $$ TRUE $$");
        engine.execute("CREATE FUNCTION rls_fbin() RETURNS BINARY AS $$ TO_BINARY('00') $$");
        engine.execute("CREATE FUNCTION rls_fbin4() RETURNS BINARY(4) AS $$ TO_BINARY('00') $$");
        engine.execute("CREATE FUNCTION rls_farr() RETURNS ARRAY AS $$ ARRAY_CONSTRUCT(1) $$");
        engine.execute("CREATE FUNCTION rls_ftab() RETURNS TABLE (a NUMBER(5,2), \"b c\" VARCHAR(3)) AS"
            + " $$ SELECT 1.5, 'x' $$");
        engine.execute("CREATE PROCEDURE rls_p2(x INT, y VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$ BEGIN RETURN 'p'; END; $$");
        engine.execute("CREATE PROCEDURE rls_pn() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$");
        engine.execute("CREATE PROCEDURE rls_pt1() RETURNS TABLE (a NUMBER(5,2)) LANGUAGE SQL AS $$ " + RESULT_SET
            + " $$");
        engine.execute("CREATE PROCEDURE rls_pt2() RETURNS TABLE (a VARCHAR(3), \"b c\" TIMESTAMP_NTZ(3)) LANGUAGE SQL"
            + " AS $$ " + RESULT_SET + " $$");
        engine.execute("CREATE PROCEDURE rls_pt0() RETURNS TABLE () LANGUAGE SQL AS $$ " + RESULT_SET + " $$");
    }

    /** One column of a listing per row, a bar between rows. */
    private String column(final String sql, final String name) {
        final ResultSet rs = engine.executeQuery(sql);
        final int at = rs.getColumnIndex(name);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            out.append(out.length() == 0 ? "" : " | ").append(row.getValue(at));
        }
        return out.toString();
    }

    private String returns(final String describe) {
        for (final Row row : engine.executeQuery(describe).getRows()) {
            if ("returns".equals(row.getValue(0))) {
                return String.valueOf(row.getValue(1));
            }
        }
        return null;
    }

    @Test
    public void showListsTheArgumentTypesAlone() {
        assertEquals("RLS_F3(NUMBER, VARCHAR) RETURN VARCHAR", column("SHOW USER FUNCTIONS LIKE 'RLS_F3'", "arguments"));
        assertEquals("RLS_P2(NUMBER, VARCHAR) RETURN VARCHAR", column("SHOW PROCEDURES LIKE 'RLS_P2'", "arguments"));
        assertEquals("RLS_FV10() RETURN VARCHAR", column("SHOW USER FUNCTIONS LIKE 'RLS_FV10'", "arguments"));
        assertEquals("RLS_FN() RETURN NUMBER", column("SHOW USER FUNCTIONS LIKE 'RLS_FN'", "arguments"));
        assertEquals("RLS_FTZ() RETURN TIMESTAMP_TZ", column("SHOW USER FUNCTIONS LIKE 'RLS_FTZ'", "arguments"));
        assertEquals("RLS_PN() RETURN NUMBER", column("SHOW PROCEDURES LIKE 'RLS_PN'", "arguments"));
    }

    @Test
    public void aTableRoutinePrintsItsColumnsAndIsFlagged() {
        assertEquals("RLS_FTAB() RETURN TABLE (A NUMBER, b c VARCHAR)",
            column("SHOW USER FUNCTIONS LIKE 'RLS_FTAB'", "arguments"));
        assertEquals("Y", column("SHOW USER FUNCTIONS LIKE 'RLS_FTAB'", "is_table_function"));
        assertEquals("RLS_PT0() RETURN TABLE () | RLS_PT1() RETURN TABLE (A NUMBER)"
            + " | RLS_PT2() RETURN TABLE (A VARCHAR, b c TIMESTAMP_NTZ)",
            column("SHOW PROCEDURES LIKE 'RLS_PT%'", "arguments"));
        assertEquals("Y | Y | Y", column("SHOW PROCEDURES LIKE 'RLS_PT%'", "is_table_function"));
        assertEquals("N", column("SHOW PROCEDURES LIKE 'RLS_P2'", "is_table_function"));
    }

    @Test
    public void describeSpellsATableProceduresReturns() {
        assertEquals("TABLE (A NUMBER)", returns("DESCRIBE PROCEDURE rls_pt1()"));
        assertEquals("TABLE (A VARCHAR, b c TIMESTAMP_NTZ)", returns("DESCRIBE PROCEDURE rls_pt2()"));
        assertEquals("TABLE ()", returns("DESCRIBE PROCEDURE rls_pt0()"));
        assertEquals("NUMBER(10,2)", returns("DESCRIBE PROCEDURE rls_pn()"));
        assertEquals("VARCHAR", returns("DESCRIBE PROCEDURE rls_p2(INT, VARCHAR)"));
        assertEquals("TABLE (A NUMBER, b c VARCHAR)", returns("DESCRIBE FUNCTION rls_ftab()"));
    }

    @Test
    public void informationSchemaSpellsTheReturnTypeInFull() {
        assertEquals("RLS_F3|VARCHAR(134217728)|(X NUMBER, Y VARCHAR)|134217728|null|null"
            + " ; RLS_FARR|ARRAY|()|null|null|null ; RLS_FB|BOOLEAN|()|null|null|null"
            + " ; RLS_FBIN|BINARY(67108864)|()|null|null|null ; RLS_FBIN4|BINARY(4)|()|null|null|null"
            + " ; RLS_FC3|VARCHAR(3)|()|3|null|null ; RLS_FD|DATE|()|null|null|null"
            + " ; RLS_FI|NUMBER(38,0)|()|null|38|0 ; RLS_FN|NUMBER(10,2)|()|null|10|2"
            + " ; RLS_FNTZ|TIMESTAMP_NTZ(9)|()|null|null|null ; RLS_FT|TIME(9)|()|null|null|null"
            + " ; RLS_FTAB|TABLE (A NUMBER, b c VARCHAR)|()|null|null|null"
            + " ; RLS_FTZ|TIMESTAMP_TZ(3)|()|null|null|null ; RLS_FV10|VARCHAR(10)|()|10|null|null"
            + " ; RLS_FVAR|VARIANT|()|null|null|null",
            rows("SELECT function_name, data_type, argument_signature, character_maximum_length, numeric_precision,"
                + " numeric_scale FROM information_schema.functions WHERE function_name LIKE 'RLS_%' ORDER BY 1"));
        assertEquals("RLS_P2|VARCHAR(134217728)|(X NUMBER, Y VARCHAR)|134217728|null|null"
            + " ; RLS_PN|NUMBER(10,2)|()|null|10|2 ; RLS_PT0|TABLE ()|()|null|null|null"
            + " ; RLS_PT1|TABLE (A NUMBER)|()|null|null|null"
            + " ; RLS_PT2|TABLE (A VARCHAR, b c TIMESTAMP_NTZ)|()|null|null|null",
            rows("SELECT procedure_name, data_type, argument_signature, character_maximum_length,"
                + " numeric_precision, numeric_scale FROM information_schema.procedures"
                + " WHERE procedure_name LIKE 'RLS_%' ORDER BY 1"));
    }

    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            out.append(out.length() == 0 ? "" : " ; ");
            for (int c = 0; c < rs.getColumnCount(); c++) {
                out.append(c == 0 ? "" : "|").append(row.getValue(c));
            }
        }
        return out.toString();
    }
}
