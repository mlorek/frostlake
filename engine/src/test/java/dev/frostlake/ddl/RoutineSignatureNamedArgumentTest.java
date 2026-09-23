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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The argument list naming one overload takes types alone outside ALTER … RENAME TO: an argument written with
 * its name is refused at its type, a plain word standing for a type is an unsupported data type, and GET_DDL
 * reads its {@code name(TYPES)} argument whole, matching the types exactly.
 */
public class RoutineSignatureNamedArgumentTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE FUNCTION f1() RETURNS INT AS '1'");
        engine.execute("CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS 'x'");
        engine.execute("CREATE OR REPLACE PROCEDURE p1(x INT) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
    }

    /** Every row's cells, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                out.append(out.length() > 0 ? " | " : "").append(row.getValues());
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String syntax(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    private static String unsupportedType(final String name) {
        return "SQL compilation error:|Unsupported data type '" + name + "'.";
    }

    private static String noObject(final String written) {
        return "SQL compilation error:|Object '" + written + "' does not exist or not authorized.";
    }

    @Test
    public void aNamedArgumentIsRefusedAtItsType() {
        assertEquals(syntax(23, "INT"), answer("DESCRIBE FUNCTION f1(x INT)"));
        assertEquals(syntax(19, "INT"), answer("DROP FUNCTION f1(x INT, y INT)"));
        assertEquals(syntax(19, "INT"), answer("DROP FUNCTION f1(x INT)"));
        assertEquals(syntax(29, "INT"), answer("DROP FUNCTION IF EXISTS f1(x INT)"));
        assertEquals(syntax(24, "INT"), answer("DROP FUNCTION f1(INT, x INT)"));
        assertEquals(syntax(19, "NUMBER"), answer("DROP FUNCTION f1(x NUMBER(10, 2))"));
        assertEquals(syntax(25, "INT"), answer("COMMENT ON FUNCTION f1(x INT) IS 'c'"));
        assertEquals(syntax(29, "INT"), answer("SHOW GRANTS ON FUNCTION f1(x INT)"));
        assertEquals(syntax(29, "INT"), answer("GRANT USAGE ON FUNCTION f1(x INT) TO ROLE PUBLIC"));
        assertEquals(syntax(24, "INT"), answer("DESCRIBE PROCEDURE p1(x INT)"));
        assertEquals(syntax(20, "INT"), answer("DROP PROCEDURE p1(x INT)"));
        assertEquals(syntax(26, "INT"), answer("COMMENT ON PROCEDURE p1(x INT) IS 'c'"));
        assertEquals(syntax(30, "INT"), answer("SHOW GRANTS ON PROCEDURE p1(x INT)"));
        assertEquals(syntax(31, "INT"), answer("REVOKE USAGE ON PROCEDURE p1(x INT) FROM ROLE PUBLIC"));
    }

    @Test
    public void aPlainWordForATypeIsUnsupported() {
        assertEquals(unsupportedType("X"), answer("ALTER FUNCTION f1(INT, x) SET COMMENT = 'q'"));
        assertEquals(unsupportedType("X"), answer("DROP FUNCTION f1(x)"));
        assertEquals(unsupportedType("X"), answer("DROP FUNCTION f1(x, INT)"));
        assertEquals(unsupportedType("x"), answer("DROP FUNCTION f1(\"x\")"));
        assertEquals(unsupportedType("X"), answer("DESCRIBE FUNCTION f1(INT, x)"));
        assertEquals(unsupportedType("X"), answer("COMMENT ON FUNCTION f1(x) IS 'c'"));
        assertEquals(unsupportedType("X"), answer("SHOW GRANTS ON FUNCTION f1(x)"));
    }

    @Test
    public void getDdlMatchesTheWrittenTypesExactly() {
        final String withArgument = "[CREATE OR REPLACE FUNCTION \"F1\"(\"X\" NUMBER(38,0))\n"
            + "RETURNS NUMBER(38,0)\nLANGUAGE SQL\nAS 'x';]";
        assertEquals(withArgument.replace('\n', '|'), answer("SELECT GET_DDL('FUNCTION', 'f1(INT)')").replace('\n', '|'));
        assertEquals(withArgument.replace('\n', '|'), answer("SELECT GET_DDL('FUNCTION', 'f1( INT )')").replace('\n', '|'));
        assertEquals(withArgument.replace('\n', '|'),
            answer("SELECT GET_DDL('FUNCTION', 'f1(NUMBER(38,0))')").replace('\n', '|'));
        assertEquals(withArgument.replace('\n', '|'),
            answer("SELECT GET_DDL('FUNCTION', 'f1(integer)')").replace('\n', '|'));
        assertEquals("[CREATE OR REPLACE FUNCTION \"F1\"()|RETURNS NUMBER(38,0)|LANGUAGE SQL|AS '1';]",
            answer("SELECT GET_DDL('FUNCTION', 'f1()')").replace('\n', '|'));
        assertEquals(noObject("f1(x INT)"), answer("SELECT GET_DDL('FUNCTION', 'f1(x INT)')"));
        assertEquals(noObject("f1(VARCHAR)"), answer("SELECT GET_DDL('FUNCTION', 'f1(VARCHAR)')"));
        assertEquals(noObject("f1(FLOAT)"), answer("SELECT GET_DDL('FUNCTION', 'f1(FLOAT)')"));
        assertEquals(noObject("f1(x)"), answer("SELECT GET_DDL('FUNCTION', 'f1(x)')"));
        assertEquals(noObject("f1"), answer("SELECT GET_DDL('FUNCTION', 'f1')"));
        assertEquals(noObject("f1(INT"), answer("SELECT GET_DDL('FUNCTION', 'f1(INT')"));
        assertEquals(noObject("f1(INT, INT)"), answer("SELECT GET_DDL('FUNCTION', 'f1(INT, INT)')"));
        assertEquals(noObject("p1(x INT)"), answer("SELECT GET_DDL('PROCEDURE', 'p1(x INT)')"));
    }

    @Test
    public void aLaterFaultDoesNotComeFirst() {
        // The named argument is refused while the list is read, so the DEFAULT after it is never reached.
        assertEquals(syntax(19, "INT"), answer("DROP FUNCTION f1(x INT DEFAULT 1)"));
    }
}
