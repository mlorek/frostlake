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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A procedure is created SECURE as a function is: its DDL and its listing say so, a replacement written without
 * the word is no longer secure, and no ALTER changes it.
 */
public class SecureProcedureTest extends BaseDatabaseTest {

    private static final String BODY = " RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'p'; END; $$";
    private static final String DDL_TAIL = "\"SP_ONE\"()\nRETURNS VARCHAR\nLANGUAGE SQL\nEXECUTE AS OWNER\n"
        + "AS ' BEGIN RETURN ''p''; END; ';";

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String secure(final String name) {
        final ResultSet rs = engine.executeQuery("SHOW PROCEDURES LIKE '" + name + "'");
        return cell(rs, soleRowWhere(rs, "name", name), "is_secure");
    }

    @Test
    public void aSecureProcedureSaysSoInItsDdlAndItsListing() {
        engine.execute("CREATE OR REPLACE SECURE PROCEDURE sp_one()" + BODY);
        assertEquals("p", scalar("CALL sp_one()"));
        assertEquals("CREATE OR REPLACE SECURE PROCEDURE " + DDL_TAIL, scalar("SELECT GET_DDL('PROCEDURE', 'sp_one()')"));
        assertEquals("Y", secure("SP_ONE"));
        // A replacement written without the word is an ordinary procedure.
        engine.execute("CREATE OR REPLACE PROCEDURE sp_one()" + BODY);
        assertEquals("CREATE OR REPLACE PROCEDURE " + DDL_TAIL, scalar("SELECT GET_DDL('PROCEDURE', 'sp_one()')"));
        assertEquals("N", secure("SP_ONE"));
    }

    @Test
    public void secureStandsAfterTheTemporaryWordAndBeforeIfNotExists() {
        engine.execute("CREATE SECURE PROCEDURE IF NOT EXISTS sp_two()" + BODY);
        assertEquals("Y", secure("SP_TWO"));
        engine.execute("CREATE OR REPLACE TEMP SECURE PROCEDURE sp_three()" + BODY);
        assertEquals("Y", secure("SP_THREE"));
        assertTrue(refusalOf("CREATE OR REPLACE SECURE TEMPORARY PROCEDURE sp_four()" + BODY).startsWith(
            "SQL compilation error:\nsyntax error line 1 at position 35 unexpected 'PROCEDURE'."));
    }

    @Test
    public void noAlterMakesAProcedureSecureOrPlain() {
        engine.execute("CREATE OR REPLACE SECURE PROCEDURE sp_one()" + BODY);
        final String noProperty = "SQL compilation error:\ninvalid property 'SECURE' for 'PROCEDURE'";
        assertEquals(noProperty, refusalOf("ALTER PROCEDURE sp_one() UNSET SECURE"));
        assertEquals(noProperty, refusalOf("ALTER PROCEDURE sp_one() SET SECURE = TRUE"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 35 unexpected '<EOF>'.",
            refusalOf("ALTER PROCEDURE sp_one() SET SECURE"));
        assertEquals("Y", secure("SP_ONE"));
    }
}
