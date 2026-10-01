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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The syntax errors around the END of a Snowflake Scripting construct (live-verified). A loop's END takes a trailing
 * label, so a loop run into the next statement without its semicolon swallows a word that can be a label and is
 * refused at the token after it; END is no label. An empty body is refused at the END (or UNTIL) that closes it, and
 * the construct's closing words are then read as written.
 */
public class ConstructEndSyntaxTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String line(final int line, final int position, final String token) {
        return "\nsyntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    private static String refused(final String... lines) {
        final StringBuilder message = new StringBuilder("SQL compilation error:");
        for (final String each : lines) {
            message.append(each);
        }
        return message.toString();
    }


    @Test
    public void aLoopRunIntoTheNextStatementSwallowsItsLabel() {
        assertEquals(refused(line(1, 65, "i"), line(1, 68, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 3) DO i := i + 1; END WHILE RETURN i; END; $$"));
        assertEquals(refused(line(1, 70, "i"), line(1, 73, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; REPEAT i := i + 1; UNTIL (i > 2) END REPEAT RETURN i; END; $$"));
        assertEquals(refused(line(1, 65, "i"), line(1, 68, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; FOR j IN 1 TO 3 DO i := i + 1; END FOR RETURN i; END; $$"));
        assertEquals(refused(line(1, 60, ":=")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 3) DO i := i + 1; END WHILE i := 5; RETURN i; END; $$"));
        assertEquals(refused(line(1, 65, "1"), line(1, 72, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 3) DO i := i + 1; END WHILE RETURN 1 + i; END; $$"));
        assertEquals(refused(line(1, 65, "i")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 3) DO i := i + 1; END WHILE RETURN i END; $$"));
        assertEquals(refused(line(6, 7, "i"), line(7, 0, "END")),
            refusal("EXECUTE IMMEDIATE $$BEGIN\nLET i := 0;\nWHILE (i < 3) DO\ni := i + 1;\nEND WHILE\nRETURN i;\nEND;$$"));
        assertEquals(refused(line(1, 59, "i"), line(1, 62, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; LOOP i := i + 1; BREAK; END LOOP RETURN i; END; $$"));
        assertEquals(refused(line(1, 78, "i"), line(1, 81, "END")),
            refusal("EXECUTE IMMEDIATE $$ DECLARE i INT DEFAULT 0; BEGIN WHILE (i < 3) DO i := i + 1; END WHILE RETURN i; END; $$"));
        assertEquals(refused(line(1, 65, "i"), line(1, 68, "EXCEPTION")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 3) DO i := i + 1; END WHILE RETURN i; EXCEPTION WHEN OTHER THEN RETURN 0; END; $$"));
        assertEquals(refused(line(1, 80, "i"), line(1, 91, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; IF (TRUE) THEN WHILE (i < 3) DO i := i + 1; END WHILE RETURN i; END IF; END; $$"));
        assertEquals(refused(line(1, 61, "("), line(1, 66, ")")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE IF (TRUE) THEN RETURN 1; END IF; END; $$"));
        assertEquals(refused(line(1, 64, "("), line(1, 70, ")")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE WHILE (FALSE) DO BREAK; END WHILE; END; $$"));
        assertEquals(refused(line(1, 66, "x"), line(1, 68, "INT")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE DECLARE x INT; BEGIN RETURN 1; END; END; $$"));
        assertEquals(refused(line(1, 63, "p"), line(1, 64, "(")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE CALL p(); END; $$"));
        assertEquals(refused(line(1, 64, "e"), line(1, 67, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE RAISE e; END; $$"));
        assertEquals(refused(line(1, 63, "c"), line(1, 66, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE OPEN c; END; $$"));
        assertEquals(refused(line(1, 64, "c"), line(1, 66, "INTO")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE FETCH c INTO x; END; $$"));
        assertEquals(refused(line(1, 64, "c"), line(1, 67, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE CLOSE c; END; $$"));
        assertEquals(refused(line(1, 64, "INTO"), line(1, 71, "USING")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE MERGE INTO t USING s ON TRUE WHEN MATCHED THEN DELETE; END; $$"));
        assertEquals(refused(line(1, 64, "ALL")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE AWAIT ALL; END; $$"));
    }

    @Test
    public void anEmptyBodyIsRefusedAtTheTokenThatClosesIt() {
        assertEquals(refused(line(1, 24, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN WHILE (FALSE) DO END WHILE; RETURN 1; END; $$"));
        assertEquals(refused(line(1, 26, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN WHILE (FALSE) LOOP END LOOP; RETURN 1; END; $$"));
        assertEquals(refused(line(1, 12, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LOOP END LOOP; RETURN 1; END; $$"));
        assertEquals(refused(line(1, 14, "UNTIL")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN REPEAT UNTIL (TRUE) END REPEAT; RETURN 1; END; $$"));
        assertEquals(refused(line(1, 26, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN FOR j IN 1 TO 3 DO END FOR; RETURN 1; END; $$"));
        assertEquals(refused(line(1, 22, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF (TRUE) THEN END IF; RETURN 1; END; $$"));
        assertEquals(refused(line(1, 27, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN CASE WHEN TRUE THEN END CASE; RETURN 1; END; $$"));
        assertEquals(refused(line(1, 37, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF (TRUE) THEN RETURN 1; ELSE END IF; RETURN 1; END; $$"));
        assertEquals(refused(line(1, 24, "END"), line(1, 27, ";")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN WHILE (FALSE) DO END; RETURN 1; END; $$"));
        assertEquals(refused(line(1, 24, "END"), line(1, 41, "1")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN WHILE (FALSE) DO END WHILE RETURN 1; END; $$"));
        assertEquals(refused(line(1, 12, "END"), line(1, 28, "1")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LOOP END LOOP RETURN 1; END; $$"));
        assertEquals(refused(line(3, 0, "END")),
            refusal("EXECUTE IMMEDIATE $$BEGIN\nWHILE (FALSE) DO\nEND WHILE;\nRETURN 1;\nEND;$$"));
        assertEquals(refused(line(1, 24, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN WHILE (FALSE) DO END WHILE x; RETURN 1; END; $$"));
        assertEquals(refused(line(1, 24, "END"), line(1, 34, "SELECT")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN WHILE (FALSE) DO END WHILE SELECT 1; END; $$"));
        assertEquals(refused(line(1, 12, "END"), line(1, 23, "y"), line(1, 26, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LOOP END LOOP x y; END; $$"));
        assertEquals(refused(line(1, 24, "END"), line(1, 36, "y"), line(1, 38, "z")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN WHILE (FALSE) DO END WHILE x y z; END; $$"));
        assertEquals(refused(line(1, 24, "END"), line(1, 28, "LOOP")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN WHILE (FALSE) DO END LOOP; RETURN 1; END; $$"));
        assertEquals(refused(line(1, 24, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN WHILE (FALSE) DO END WHILE; END; $$"));
        assertEquals(refused(line(1, 22, "END"), line(1, 29, "RETURN"), line(1, 36, "1")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF (TRUE) THEN END IF RETURN 1; END; $$"));
        assertEquals(refused(line(1, 22, "END"), line(1, 25, ";")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF (TRUE) THEN END; RETURN 1; END; $$"));
        assertEquals(refused(line(1, 22, "END"), line(1, 29, "x"), line(1, 32, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF (TRUE) THEN END IF x; END; $$"));
        assertEquals(refused(line(1, 52, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF (TRUE) THEN RETURN 1; ELSEIF (FALSE) THEN END IF; END; $$"));
        assertEquals(refused(line(1, 27, "END"), line(1, 36, "x"), line(1, 39, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN CASE WHEN TRUE THEN END CASE x; END; $$"));
        assertEquals(refused(line(1, 7, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN END; $$"));
        assertEquals(refused(line(1, 43, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN END; $$"));
        assertEquals(refused(line(1, 14, "UNTIL"), line(1, 40, "y"), line(1, 43, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN REPEAT UNTIL (TRUE) END REPEAT x y; END; $$"));
        assertEquals(refused(line(1, 14, "UNTIL"), line(1, 20, "TRUE"), line(1, 25, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN REPEAT UNTIL TRUE END REPEAT; END; $$"));
        assertEquals(refused(line(1, 26, "END"), line(1, 36, "y"), line(1, 39, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN FOR j IN 1 TO 3 DO END FOR x y; END; $$"));
        assertEquals(refused(line(1, 24, "END"), line(1, 34, "END"), line(1, 39, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN WHILE (FALSE) DO END WHILE END; END; $$"));
        assertEquals(refused(line(1, 39, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF (TRUE) THEN WHILE (FALSE) DO END WHILE; END IF; END; $$"));
        assertEquals(refused(line(1, 24, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN WHILE (FALSE) DO END WHILE\n;\nEND; $$"));
    }

    @Test
    public void endIsNoLabel() {
        assertEquals(refused(line(1, 58, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 3) DO i := i + 1; END WHILE END; $$"));
        assertEquals(refused(line(1, 28, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LOOP BREAK; END LOOP END; $$"));
        assertEquals(refused(line(1, 41, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN FOR j IN 1 TO 3 DO BREAK; END FOR END; $$"));
        assertEquals(refused(line(1, 45, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN REPEAT BREAK; UNTIL (TRUE) END REPEAT END; $$"));
        assertEquals(refused(line(1, 28, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LOOP BREAK; END LOOP END $$"));
        assertEquals(refused(line(1, 28, "END"), line(1, 33, "RETURN")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LOOP BREAK; END LOOP END; RETURN 1; END; $$"));
        assertEquals(refused(line(1, 40, "END"), line(1, 45, "RETURN")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN WHILE (TRUE) DO BREAK; END WHILE END; RETURN 2; END; $$"));
        assertEquals(refused(line(1, 28, "end"), line(1, 33, "RETURN")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LOOP BREAK; END LOOP end; RETURN 1; END; $$"));
    }

    @Test
    public void wordsNoLabelCanBeAndConstructsWithoutLabels() {
        assertEquals(refused(line(1, 50, "RETURN"), line(1, 57, "i")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; IF (i = 0) THEN i := 1; END IF RETURN i; END; $$"));
        assertEquals(refused(line(1, 57, "RETURN"), line(1, 64, "i")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; CASE WHEN i = 0 THEN i := 1; END CASE RETURN i; END; $$"));
        assertEquals(refused(line(1, 62, "j"), line(1, 64, ":=")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 3) DO i := i + 1; END WHILE LET j := 1; RETURN i; END; $$"));
        assertEquals(refused(line(1, 58, "SELECT")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 3) DO i := i + 1; END WHILE SELECT 1; RETURN i; END; $$"));
        assertEquals(refused(line(1, 63, "BREAK"), line(1, 70, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE LOOP BREAK; END LOOP; END; $$"));
        assertEquals(refused(line(1, 58, "FOR"), line(1, 64, "IN")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE FOR j IN 1 TO 2 DO BREAK; END FOR; END; $$"));
        assertEquals(refused(line(1, 65, "BREAK"), line(1, 72, "UNTIL")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE REPEAT BREAK; UNTIL (TRUE) END REPEAT; END; $$"));
        assertEquals(refused(line(1, 58, "NULL")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE NULL; END; $$"));
        assertEquals(refused(line(1, 58, "INSERT"), line(1, 72, "VALUES")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE INSERT INTO t VALUES (1); END; $$"));
        assertEquals(refused(line(1, 58, "UPDATE"), line(1, 67, "SET")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE UPDATE t SET a = 1; END; $$"));
        assertEquals(refused(line(1, 58, "DELETE"), line(1, 73, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE DELETE FROM t; END; $$"));
        assertEquals(refused(line(1, 58, "CREATE"), line(1, 73, "(")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE CREATE TABLE t (a INT); END; $$"));
        assertEquals(refused(line(1, 60, "y"), line(1, 63, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE x y; END; $$"));
        assertEquals(refused(line(1, 62, "1")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE \"q\" 1; END; $$"));
        assertEquals(refused(line(1, 64, "1")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE inner 1; END; $$"));
        assertEquals(refused(line(1, 64, "(")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE ASYNC (SELECT 1); END; $$"));
    }
}
