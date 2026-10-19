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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The signature a statement writes after a routine's name picks the overload by type family, and a type written
 * with a length, a precision or a scale names only a parameter declared with the same (live-verified). A bare
 * {@code CHAR}, {@code CHARACTER} or {@code NCHAR} is a text of length 1, so it names a {@code CHAR},
 * {@code CHAR(1)} or {@code VARCHAR(1)} parameter and no longer one; a bare {@code VARCHAR}, {@code STRING} or
 * {@code TEXT} names a text of any length. DROP, DESCRIBE, COMMENT ON and ALTER read a signature the same way.
 */
public class RoutineSignatureWidthTest extends BaseDatabaseTest {

    private void function(final String name, final String type) {
        engine.execute("CREATE OR REPLACE FUNCTION " + name + "(code " + type + ") RETURNS INT AS $$ 1 $$");
    }

    private void assertMissing(final String kind, final String name, final String sql) {
        final String message = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
        assertTrue(message.startsWith("SQL compilation error:\n" + kind + " 'TEST_DB.TEST_SCHEMA." + name
            + "' does not exist or not authorized."), sql + " ==> " + message);
    }

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aBareCharIsATextOfLengthOne() {
        function("rsw_c2", "CHAR(2)");
        assertMissing("Function", "RSW_C2", "DROP FUNCTION rsw_c2(CHAR)");
        assertMissing("Function", "RSW_C2", "DROP FUNCTION rsw_c2(CHARACTER)");
        assertMissing("Function", "RSW_C2", "DROP FUNCTION rsw_c2(NCHAR)");
        assertEquals("RSW_C2 successfully dropped.", status("DROP FUNCTION rsw_c2(CHAR(2))"));
        function("rsw_c1", "CHAR");
        assertEquals("RSW_C1 successfully dropped.", status("DROP FUNCTION rsw_c1(CHAR)"));
        function("rsw_c1", "CHAR");
        assertEquals("RSW_C1 successfully dropped.", status("DROP FUNCTION rsw_c1(VARCHAR(1))"));
        function("rsw_v1", "VARCHAR(1)");
        assertEquals("RSW_V1 successfully dropped.", status("DROP FUNCTION rsw_v1(CHAR)"));
        function("rsw_v", "VARCHAR");
        assertMissing("Function", "RSW_V", "DROP FUNCTION rsw_v(CHAR)");
    }

    @Test
    public void aWrittenLengthMustBeTheDeclaredOne() {
        function("rsw_c2", "CHAR(2)");
        assertMissing("Function", "RSW_C2", "DROP FUNCTION rsw_c2(VARCHAR(3))");
        assertEquals("RSW_C2 successfully dropped.", status("DROP FUNCTION rsw_c2(VARCHAR(2))"));
        function("rsw_v5", "VARCHAR(5)");
        assertMissing("Function", "RSW_V5", "DROP FUNCTION rsw_v5(VARCHAR(6))");
        assertEquals("RSW_V5 successfully dropped.", status("DROP FUNCTION rsw_v5(STRING)"));
        function("rsw_v", "VARCHAR");
        assertMissing("Function", "RSW_V", "DROP FUNCTION rsw_v(VARCHAR(5))");
        assertEquals("RSW_V successfully dropped.", status("DROP FUNCTION rsw_v(VARCHAR)"));
        function("rsw_c3", "CHARACTER(3)");
        assertEquals("RSW_C3 successfully dropped.", status("DROP FUNCTION rsw_c3(TEXT)"));
    }

    @Test
    public void aPrecisionAScaleAndABinaryLengthAreReadTheSameWay() {
        function("rsw_n", "NUMBER(5,2)");
        assertMissing("Function", "RSW_N", "DROP FUNCTION rsw_n(NUMBER(10,0))");
        assertEquals("RSW_N successfully dropped.", status("DROP FUNCTION rsw_n(INT)"));
        function("rsw_n", "NUMBER(5,2)");
        assertEquals("RSW_N successfully dropped.", status("DROP FUNCTION rsw_n(NUMBER)"));
        function("rsw_b", "BINARY(4)");
        assertMissing("Function", "RSW_B", "DROP FUNCTION rsw_b(BINARY(5))");
        assertEquals("RSW_B successfully dropped.", status("DROP FUNCTION rsw_b(BINARY)"));
        function("rsw_t", "TIMESTAMP_NTZ(3)");
        assertMissing("Function", "RSW_T", "DROP FUNCTION rsw_t(TIMESTAMP_NTZ(6))");
        assertEquals("RSW_T successfully dropped.", status("DROP FUNCTION rsw_t(TIMESTAMP_NTZ)"));
    }

    @Test
    public void describeCommentAndAlterReadTheSignatureAsDropDoes() {
        function("rsw_c2", "CHAR(2)");
        assertMissing("Function", "RSW_C2", "DESCRIBE FUNCTION rsw_c2(CHAR)");
        assertMissing("Function", "RSW_C2", "DESCRIBE FUNCTION rsw_c2(VARCHAR(3))");
        assertEquals("(CODE VARCHAR)", String.valueOf(
            engine.executeQuery("DESCRIBE FUNCTION rsw_c2(CHAR(2))").getRows().get(0).getValue(1)));
        assertEquals("(CODE VARCHAR)", String.valueOf(
            engine.executeQuery("DESCRIBE FUNCTION rsw_c2(STRING)").getRows().get(0).getValue(1)));
        assertMissing("Function", "RSW_C2", "COMMENT ON FUNCTION rsw_c2(CHAR) IS 'c'");
        assertMissing("Function", "RSW_C2", "ALTER FUNCTION rsw_c2(CHAR) SET COMMENT = 'c'");
        assertEquals("Statement executed successfully.", status("ALTER FUNCTION rsw_c2(CHAR(2)) SET COMMENT = 'c'"));
    }

    @Test
    public void aProcedureIsNamedTheSameWay() {
        engine.execute("CREATE OR REPLACE PROCEDURE rsw_p(code CHAR(2)) RETURNS INT LANGUAGE SQL AS"
            + " $$ BEGIN RETURN 1; END; $$");
        assertMissing("Procedure", "RSW_P", "DESCRIBE PROCEDURE rsw_p(CHAR)");
        assertMissing("Procedure", "RSW_P", "DROP PROCEDURE rsw_p(CHAR)");
        assertEquals("RSW_P successfully dropped.", status("DROP PROCEDURE rsw_p(VARCHAR)"));
    }

    @Test
    public void overloadsDifferingOnlyInLengthAreOneSignature() {
        function("rsw_o", "CHAR(2)");
        final String message = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE FUNCTION rsw_o(code CHAR(3)) RETURNS INT AS $$ 1 $$");
            }
        }).getMessage();
        assertEquals("SQL compilation error:\nObject 'RSW_O' already exists.", message);
    }
}
