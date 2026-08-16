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

package dev.frostlake.procedural;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An untyped LET or DECLARE is typed from ANY initialiser, widened the way live declares the name - a
 * text VARCHAR(134217728), a whole number NUMBER(38,0), a fraction FLOAT, a date or a timestamp its own
 * - so a RETURN of it is judged and converted under the declared RETURNS type. An initialiser that gives
 * no type at all (a bare unknown name, a bare NULL, a cursor record's field) is refused while the block
 * compiles, at the LET or at the DECLARE item's name. Every cell is live-verified.
 */
public class UntypedDeclarationTypeTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
    }

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    /** The declared RETURNS judges an untyped LET by the type its initialiser gives it. */
    @Test
    public void anUntypedDeclarationIsTypedFromAnyInitialiser() {
        engine.execute("CREATE OR REPLACE PROCEDURE p_d1() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN LET y := CURRENT_DATE(); RETURN y; END; $$");
        assertRefused("CALL p_d1()",
            "SQL compilation error: error line 1 at position 32\n Declared return type 'NUMBER(38,0)' is incompatible with actual return type 'DATE'");
        engine.execute("CREATE OR REPLACE PROCEDURE p_d5() RETURNS OBJECT LANGUAGE SQL AS $$ BEGIN LET y := 'abc'; RETURN y; END; $$");
        assertRefused("CALL p_d5()",
            "SQL compilation error: error line 1 at position 23\n Declared return type 'OBJECT' is incompatible with actual return type 'VARCHAR(134217728)'");
        engine.execute("CREATE OR REPLACE PROCEDURE p_d6() RETURNS OBJECT LANGUAGE SQL AS $$ BEGIN LET y := 1 + 1; RETURN y; END; $$");
        assertRefused("CALL p_d6()",
            "SQL compilation error: error line 1 at position 23\n Declared return type 'OBJECT' is incompatible with actual return type 'NUMBER(38,0)'");
        engine.execute("CREATE OR REPLACE PROCEDURE p_d7() RETURNS OBJECT LANGUAGE SQL AS $$ BEGIN LET y := 1.5 * 2; RETURN y; END; $$");
        assertRefused("CALL p_d7()",
            "SQL compilation error: error line 1 at position 25\n Declared return type 'OBJECT' is incompatible with actual return type 'FLOAT'");
        engine.execute("CREATE OR REPLACE PROCEDURE p_d9() RETURNS OBJECT LANGUAGE SQL AS $$ BEGIN LET y := UPPER('ab'); RETURN y; END; $$");
        assertRefused("CALL p_d9()",
            "SQL compilation error: error line 1 at position 29\n Declared return type 'OBJECT' is incompatible with actual return type 'VARCHAR(134217728)'");
        engine.execute("CREATE OR REPLACE PROCEDURE p_d10() RETURNS OBJECT LANGUAGE SQL AS $$ BEGIN LET y := CURRENT_TIMESTAMP(); RETURN y; END; $$");
        assertRefused("CALL p_d10()",
            "SQL compilation error: error line 1 at position 37\n Declared return type 'OBJECT' is incompatible with actual return type 'TIMESTAMP_LTZ(9)'");
        engine.execute("CREATE OR REPLACE PROCEDURE p_d11() RETURNS OBJECT LANGUAGE SQL AS $$ BEGIN LET y := TRUE; RETURN y; END; $$");
        assertRefused("CALL p_d11()",
            "SQL compilation error: error line 1 at position 22\n Declared return type 'OBJECT' is incompatible with actual return type 'BOOLEAN'");
        engine.execute("CREATE OR REPLACE PROCEDURE p_d12() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN LET y := CURRENT_DATE(); RETURN 1; END; $$");
        assertEquals("1",
            rows("CALL p_d12()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_d13() RETURNS OBJECT LANGUAGE SQL AS $$ BEGIN LET y := 'x' || 'yz'; RETURN y; END; $$");
        assertRefused("CALL p_d13()",
            "SQL compilation error: error line 1 at position 29\n Declared return type 'OBJECT' is incompatible with actual return type 'VARCHAR(134217728)'");
        engine.execute("CREATE OR REPLACE PROCEDURE p_d15() RETURNS OBJECT LANGUAGE SQL AS $$ BEGIN LET y := (SELECT 1); RETURN y; END; $$");
        assertRefused("CALL p_d15()",
            "SQL compilation error: error line 1 at position 28\n Declared return type 'OBJECT' is incompatible with actual return type 'NUMBER(38,0)'");
        engine.execute("CREATE OR REPLACE PROCEDURE p_d16() RETURNS OBJECT LANGUAGE SQL AS $$ BEGIN LET y := 7; LET z := y * 2; RETURN z; END; $$");
        assertRefused("CALL p_d16()",
            "SQL compilation error: error line 1 at position 35\n Declared return type 'OBJECT' is incompatible with actual return type 'NUMBER(38,0)'");
        engine.execute("CREATE OR REPLACE PROCEDURE p_d17() RETURNS OBJECT LANGUAGE SQL AS $$ DECLARE y DEFAULT 'abcd'; BEGIN RETURN y; END; $$");
        assertRefused("CALL p_d17()",
            "SQL compilation error: error line 1 at position 33\n Declared return type 'OBJECT' is incompatible with actual return type 'VARCHAR(134217728)'");
        engine.execute("CREATE OR REPLACE PROCEDURE p_d18() RETURNS OBJECT LANGUAGE SQL AS $$ BEGIN LET y := ABS(-5); RETURN y; END; $$");
        assertRefused("CALL p_d18()",
            "SQL compilation error: error line 1 at position 25\n Declared return type 'OBJECT' is incompatible with actual return type 'NUMBER(38,0)'");
        engine.execute("CREATE OR REPLACE PROCEDURE p_d19() RETURNS OBJECT LANGUAGE SQL AS $$ BEGIN LET y := TO_DATE('2020-01-01'); RETURN y; END; $$");
        assertRefused("CALL p_d19()",
            "SQL compilation error: error line 1 at position 39\n Declared return type 'OBJECT' is incompatible with actual return type 'DATE'");
    }

    /** A text LET is typed, so its RETURN converts to the declared type and fails as live does. */
    @Test
    public void aTextDeclarationIsConvertedByItsReturn() {
        engine.execute("CREATE OR REPLACE PROCEDURE p_d4() RETURNS DATE LANGUAGE SQL AS $$ BEGIN LET y := 'x'; RETURN y; END; $$");
        assertRefused("CALL p_d4()",
            "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 28 : Date 'x' is not recognized");
    }

    /** An initialiser that gives no type is refused at the declaration, before the rest of the body. */
    @Test
    public void anInitialiserWithNoTypeIsRefusedAtTheDeclaration() {
        engine.execute("CREATE OR REPLACE PROCEDURE p_d2() RETURNS DATE LANGUAGE SQL AS $$ BEGIN LET a := missing; RETURN 5; END; $$");
        assertRefused("CALL p_d2()",
            "SQL compilation error: error line 1 at position 7\n variable 'A' cannot have its type inferred from initializer");
        engine.execute("CREATE OR REPLACE PROCEDURE p_d8() RETURNS OBJECT LANGUAGE SQL AS $$ DECLARE a := missing; BEGIN RETURN 5; END; $$");
        assertRefused("CALL p_d8()",
            "SQL compilation error: error line 1 at position 9\n variable 'A' cannot have its type inferred from initializer");
        engine.execute("CREATE OR REPLACE PROCEDURE p_d14() RETURNS OBJECT LANGUAGE SQL AS $$ BEGIN LET y := NULL; RETURN y; END; $$");
        assertRefused("CALL p_d14()",
            "SQL compilation error: error line 1 at position 7\n variable 'Y' cannot have its type inferred from initializer");
        engine.execute("CREATE OR REPLACE PROCEDURE p_n1() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN LET x := 1; LET a := missing; RETURN 5; END; $$");
        assertRefused("CALL p_n1()",
            "SQL compilation error: error line 1 at position 19\n variable 'A' cannot have its type inferred from initializer");
        engine.execute("CREATE OR REPLACE PROCEDURE p_n2() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) THEN LET a := missing; END IF; RETURN 5; END; $$");
        assertRefused("CALL p_n2()",
            "SQL compilation error: error line 1 at position 22\n variable 'A' cannot have its type inferred from initializer");
        engine.execute("CREATE OR REPLACE PROCEDURE p_n3() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN FOR i IN 1 TO 2 DO LET a := missing; END FOR; RETURN 5; END; $$");
        assertRefused("CALL p_n3()",
            "SQL compilation error: error line 1 at position 26\n variable 'A' cannot have its type inferred from initializer");
        engine.execute("CREATE OR REPLACE PROCEDURE p_n4() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN LET a := missing; END; RETURN 5; END; $$");
        assertRefused("CALL p_n4()",
            "SQL compilation error: error line 1 at position 13\n variable 'A' cannot have its type inferred from initializer");
        engine.execute("CREATE OR REPLACE PROCEDURE p_n5() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c CURSOR FOR SELECT 1 AS a; BEGIN FOR r IN c DO LET z := r.a; END FOR; RETURN 'ok'; END; $$");
        assertRefused("CALL p_n5()",
            "SQL compilation error: error line 1 at position 57\n variable 'Z' cannot have its type inferred from initializer");
        engine.execute("CREATE OR REPLACE PROCEDURE p_n6() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c CURSOR FOR SELECT 1 AS a; BEGIN LET q := 1; FOR r IN c DO LET z := r.a; END FOR; RETURN 'ok'; END; $$");
        assertRefused("CALL p_n6()",
            "SQL compilation error: error line 1 at position 69\n variable 'Z' cannot have its type inferred from initializer");
        engine.execute("CREATE OR REPLACE PROCEDURE p_n7() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE x NUMBER; a := missing; BEGIN RETURN 5; END; $$");
        assertRefused("CALL p_n7()",
            "SQL compilation error: error line 1 at position 19\n variable 'A' cannot have its type inferred from initializer");
        engine.execute("CREATE OR REPLACE PROCEDURE p_n8() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE a DEFAULT missing; BEGIN RETURN 5; END; $$");
        assertRefused("CALL p_n8()",
            "SQL compilation error: error line 1 at position 9\n variable 'A' cannot have its type inferred from initializer");
        engine.execute("CREATE OR REPLACE PROCEDURE p_n9() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN LET a := NULL; RETURN 'x'; END; $$");
        assertRefused("CALL p_n9()",
            "SQL compilation error: error line 1 at position 7\n variable 'A' cannot have its type inferred from initializer");
        engine.execute("CREATE OR REPLACE PROCEDURE p_n16() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN LET a := missing; RETURN b; END; $$");
        assertRefused("CALL p_n16()",
            "SQL compilation error: error line 1 at position 7\n variable 'A' cannot have its type inferred from initializer");
        engine.execute("CREATE OR REPLACE PROCEDURE p_n17() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c CURSOR FOR SELECT 1 AS a; BEGIN FOR r IN c DO LET z := 1; LET w := r.a; END FOR; RETURN 'ok'; END; $$");
        assertRefused("CALL p_n17()",
            "SQL compilation error: error line 1 at position 69\n variable 'W' cannot have its type inferred from initializer");
        assertRefused("EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1 AS a; BEGIN FOR r IN c DO LET z := r.a; END FOR; RETURN 'ok'; END; $$",
            "SQL compilation error: error line 1 at position 57\n variable 'Z' cannot have its type inferred from initializer");
        assertRefused("EXECUTE IMMEDIATE $$ BEGIN LET a := missing; RETURN 5; END; $$",
            "SQL compilation error: error line 1 at position 7\n variable 'A' cannot have its type inferred from initializer");
    }

    /** An unknown name inside a larger expression stays an invalid identifier; typed shapes run. */
    @Test
    public void otherInitialisersKeepTheirOwnAnswers() {
        engine.execute("CREATE OR REPLACE PROCEDURE p_n10() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN LET a := missing + 1; RETURN 5; END; $$");
        assertRefused("CALL p_n10()",
            "SQL compilation error: error line 1 at position 16\ninvalid identifier 'MISSING'");
        engine.execute("CREATE OR REPLACE PROCEDURE p_n12() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN LET a := :missing; RETURN 5; END; $$");
        assertRefused("CALL p_n12()",
            "SQL compilation error: error line 1 at position 16\ninvalid identifier 'missing'");
        engine.execute("CREATE OR REPLACE PROCEDURE p_n13() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN LET a := NULL::NUMBER; RETURN a; END; $$");
        assertEquals("null",
            rows("CALL p_n13()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_n14() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN LET a := IFF(TRUE, 1, 'x'); RETURN 5; END; $$");
        assertEquals("5",
            rows("CALL p_n14()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_n18() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c CURSOR FOR SELECT 1 AS a; BEGIN FOR r IN c DO LET z NUMBER := r.a; END FOR; RETURN 'ok'; END; $$");
        assertEquals("ok",
            rows("CALL p_n18()"));
    }
}
