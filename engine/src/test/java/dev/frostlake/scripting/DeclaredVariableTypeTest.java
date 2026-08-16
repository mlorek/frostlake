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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A scripting variable's DECLARED type governs its value (live-verified across the matrix): the
 * value is CONVERTED at every assignment exactly as {@code ::TYPE} converts — a TZ initialiser
 * assigned to a bare {@code timestamp} becomes the session mapping's NTZ, to a {@code date} it
 * truncates, a scalar assigned to an OBJECT is TO_OBJECT's own refusal (the echo says CAST) and to
 * an ARRAY it wraps — the declared WIDTH is enforced (a too-long string is refused with the
 * truncation sentence, never kept), and the declared type WITH its width is what every column
 * derived from the variable gets: {@code varchar(2) := 42} makes a VARCHAR(2) column and
 * {@code number(5,2)} keeps its (5,2).
 *
 * <p>★ A COERCION FAULT IS AN EXPRESSION FAULT, wrapped as {@code Uncaught exception of type
 * 'EXPRESSION_ERROR' on line L at position P : <sentence>} anchored on the initialiser or the
 * assignment's right side.
 *
 * <p>★ THE PLAIN CAST ENFORCES THE SAME WIDTH: {@code 'abcdef'::VARCHAR(2)} is the truncation
 * refusal on both engines.
 */
public class DeclaredVariableTypeTest extends BaseDatabaseTest {

    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return e.getMessage();
    }

    private String columnTypeOf(final String decl, final String init) {
        engine.execute("EXECUTE IMMEDIATE $$\nDECLARE r " + decl + " := " + init
            + ";\nBEGIN\n  CREATE OR REPLACE TABLE dvt_t AS SELECT :r AS c;\n  RETURN 'done';\nEND;\n$$");
        final ResultSet rs = engine.executeQuery("DESC TABLE dvt_t");
        for (int r = 0; r < rs.getRows().size(); r++) {
            if ("C".equalsIgnoreCase(String.valueOf(rs.getRows().get(r).getValue(0)))) {
                return String.valueOf(rs.getRows().get(r).getValue(1));
            }
        }
        return "NO-COLUMN";
    }

    @Test
    public void theTemporalFlavourFollowsTheDeclarationNotTheInitialiser() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        assertEquals("TIMESTAMP_NTZ(9)",
            columnTypeOf("timestamp", "'2026-08-18 16:39:19.676 -0700'::timestamp_tz"));
        assertEquals("2026-08-18 16:39:19",
            answer("SELECT TO_VARCHAR(c, 'YYYY-MM-DD HH24:MI:SS') FROM dvt_t"));
        assertEquals("DATE", columnTypeOf("date", "'2026-08-18 16:39:19'::timestamp_ntz"));
        assertEquals("2026-08-18", answer("SELECT TO_VARCHAR(c, 'YYYY-MM-DD') FROM dvt_t"));
        assertEquals("TIME(9)", columnTypeOf("time", "'2026-08-18 16:39:19'::timestamp_ntz"));
    }

    @Test
    public void theDeclaredWidthIsTheDerivedColumnsWidth() {
        assertEquals("NUMBER(5,2)", columnTypeOf("number(5,2)", "1.7777"));
        assertEquals("1.78", answer("SELECT c FROM dvt_t"));
        assertEquals("NUMBER(38,0)", columnTypeOf("int", "1.7"));
        assertEquals("2", answer("SELECT c FROM dvt_t"));
        assertEquals("VARCHAR(2)", columnTypeOf("varchar(2)", "42"));
        assertEquals("42", answer("SELECT c FROM dvt_t"));
    }

    @Test
    public void theContainerDeclarationsConvertTheScalar() {
        assertEquals("VARIANT", columnTypeOf("variant", "5"));
        assertEquals("5", answer("SELECT c FROM dvt_t"));
        assertEquals("ARRAY", columnTypeOf("array", "5"));
        assertEquals("[5]", answer("SELECT TO_VARCHAR(c) FROM dvt_t").replaceAll("\\s", ""));
    }

    @Test
    public void aTooLongInitialiserIsRefusedAtItsOwnPosition() {
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 24 : "
                + "String 'abcdef' is too long and would be truncated",
            refusal("EXECUTE IMMEDIATE $$\nDECLARE r varchar(2) := 'abcdef';\nBEGIN\n"
                + "  CREATE OR REPLACE TABLE dvt_t AS SELECT :r AS c;\n  RETURN 'done';\nEND;\n$$"));
    }

    @Test
    public void aLaterAssignmentIsHeldToTheSameWidth() {
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 4 at position 7 : "
                + "String 'abcdef' is too long and would be truncated",
            refusal("EXECUTE IMMEDIATE $$\nDECLARE v varchar(2);\nBEGIN\n  v := 'abcdef';\n"
                + "  RETURN v;\nEND;\n$$"));
    }

    @Test
    public void aScalarAssignedToAnObjectIsTheCastMachinerysOwnRefusal() {
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 20 : "
                + "SQL compilation error:\ninvalid type [CAST(5 AS OBJECT)] for parameter 'TO_OBJECT'",
            refusal("EXECUTE IMMEDIATE $$\nDECLARE r object := 5;\nBEGIN\n"
                + "  CREATE OR REPLACE TABLE dvt_t AS SELECT :r AS c;\n  RETURN 'done';\nEND;\n$$"));
    }

    @Test
    public void aDefaultlessDeclarationConvertsOnAssignment() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        engine.execute("EXECUTE IMMEDIATE $$\nDECLARE r timestamp;\nBEGIN\n"
            + "  r := '2026-08-18 16:39:19.676 -0700'::timestamp_tz;\n"
            + "  CREATE OR REPLACE TABLE dvt_t AS SELECT :r AS c;\n  RETURN 'done';\nEND;\n$$");
        final ResultSet rs = engine.executeQuery("DESC TABLE dvt_t");
        assertEquals("TIMESTAMP_NTZ(9)", String.valueOf(rs.getRows().get(0).getValue(1)));
    }

    @Test
    public void thePlainCastEnforcesTheSameStringWidth() {
        assertEquals("String 'abcdef' is too long and would be truncated",
            refusal("SELECT 'abcdef'::VARCHAR(2)"));
        assertEquals("ab", answer("SELECT 'ab'::VARCHAR(2)"));
    }
}
