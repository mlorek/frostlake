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

package dev.frostlake.parser;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How many syntax-error LINES a scripting block earns, measured against a real account: live reads
 * a block STATEMENT BY STATEMENT. A fault inside one statement is ONE line, at the first token no
 * continuation can use; the parse resumes at that statement's semicolon; and a second faulty
 * statement adds its own line. The block's own END is never named, a fault inside a nested block or
 * an EXCEPTION handler is one line like any other, and a DECLARE section changes nothing.
 *
 * <pre>
 *   LET r RESULTSET := 1; RETURN 'x';        one line — '1', the value that is not a query
 *   LET x := ;                               one line — ';'
 *   RETURN 1 1;   SELECT 1 1;                one line — the second '1'
 *   two such LETs                            two lines, one each
 *   the LET inside a nested block / an IF / an EXCEPTION handler   one line, at the '1'
 *   RETURN 1 (no semicolon) END;             one line — 'END' itself, the first token out of place
 *   END; junk                                one line — 'junk'
 * </pre>
 *
 * <p>Frostlake's parser, unable to complete a block with a fault inside it, gives the block up and
 * reads its statements at top level, which left the closing {@code END} named as a second line and,
 * for an EXCEPTION section, a first line at WHEN. Those artifacts are now dropped by the listener;
 * the line live names is the one that stays.
 *
 * <p>NOT REPRODUCED, recorded: a statement missing its semicolon before the next one ({@code LET r
 * RESULTSET := 1} then {@code RETURN 'x';}) earns two lines live — the '1' and then the {@code 'x'}
 * — where Frostlake names only the '1'; an IF missing its END IF earns the ';' after the END and
 * then '<EOF>' live, where Frostlake names only the ';'. Both keep live's FIRST line.
 */
public class BlockErrorLinesTest extends BaseDatabaseTest {

    /** The first column of every row, or the refusal with its lines joined by '|'. */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED");
            while (rs.next()) {
                all.append(' ').append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String line(final int line, final int position, final String token) {
        return "syntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    void aFaultInsideOneStatementIsOneLine() {
        assertEquals("SQL compilation error:|" + line(2, 21, "1"),
            outcome("BEGIN\n  LET r RESULTSET := 1;\n  RETURN 'x';\nEND;"));
        assertEquals("SQL compilation error:|" + line(2, 21, "1"),
            outcome("BEGIN\n  LET r RESULTSET := 1;\nEND;"));
        assertEquals("SQL compilation error:|" + line(2, 22, "1"),
            outcome("BEGIN\n  LET r RESULTSET := (1);\n  RETURN 'x';\nEND;"));
        assertEquals("SQL compilation error:|" + line(2, 11, ";"),
            outcome("BEGIN\n  LET x := ;\n  RETURN 'x';\nEND;"));
        assertEquals("SQL compilation error:|" + line(2, 11, ";"),
            outcome("BEGIN\n  LET x := ;\nEND;"));
        assertEquals("SQL compilation error:|" + line(2, 11, "1"),
            outcome("BEGIN\n  RETURN 1 1;\n  RETURN 'x';\nEND;"));
        assertEquals("SQL compilation error:|" + line(2, 11, "1"),
            outcome("BEGIN\n  SELECT 1 1;\n  RETURN 'x';\nEND;"));
        assertEquals("SQL compilation error:|" + line(3, 11, "1"),
            outcome("BEGIN\n  RETURN 1;\n  RETURN 1 1;\nEND;"));
        // Good statements after the fault add nothing, and the block's END is never named.
        assertEquals("SQL compilation error:|" + line(2, 21, "1"),
            outcome("BEGIN\n  LET r RESULTSET := 1;\n  LET y := 2;\n  LET z := 3;\n  RETURN y;\nEND;"));
        assertEquals("SQL compilation error:|" + line(2, 21, "1"),
            outcome("BEGIN\n  LET r RESULTSET := 1;\n  RETURN 'x';\nEND"));
    }

    @Test
    void eachFaultyStatementAddsItsOwnLine() {
        assertEquals("SQL compilation error:|" + line(2, 21, "1") + "|" + line(3, 22, "2"),
            outcome("BEGIN\n  LET r RESULTSET := 1;\n  LET r2 RESULTSET := 2;\n  RETURN 'x';\nEND;"));
        assertEquals("SQL compilation error:|" + line(2, 21, "1") + "|" + line(3, 11, "1"),
            outcome("BEGIN\n  LET r RESULTSET := 1;\n  SELECT 1 1;\n  RETURN 'x';\nEND;"));
        assertEquals("SQL compilation error:|" + line(3, 23, "1") + "|" + line(5, 22, "2"),
            outcome("BEGIN\n  BEGIN\n    LET r RESULTSET := 1;\n  END;\n  LET r2 RESULTSET := 2;\n  RETURN 'x';\nEND;"));
    }

    @Test
    void nestedBlocksHandlersAndDeclarationsChangeNothing() {
        assertEquals("SQL compilation error:|" + line(3, 23, "1"),
            outcome("BEGIN\n  BEGIN\n    LET r RESULTSET := 1;\n  END;\n  RETURN 'x';\nEND;"));
        assertEquals("SQL compilation error:|" + line(3, 23, "1"),
            outcome("BEGIN\n  IF (TRUE) THEN\n    LET r RESULTSET := 1;\n  END IF;\n  RETURN 'x';\nEND;"));
        assertEquals("SQL compilation error:|" + line(5, 23, "1"),
            outcome("BEGIN\n  RETURN 'x';\nEXCEPTION\n  WHEN OTHER THEN\n    LET r RESULTSET := 1;\nEND;"));
        assertEquals("SQL compilation error:|" + line(4, 21, "1"),
            outcome("DECLARE\n  x INT;\nBEGIN\n  LET r RESULTSET := 1;\n  RETURN 'x';\nEND;"));
        assertEquals("SQL compilation error:|" + line(4, 11, ";"),
            outcome("DECLARE\n  x INT;\nBEGIN\n  LET x := ;\n  RETURN 'x';\nEND;"));
        assertEquals("SQL compilation error:|" + line(4, 11, "1"),
            outcome("DECLARE\n  x INT;\nBEGIN\n  RETURN 1 1;\n  RETURN 'x';\nEND;"));
        assertEquals("SQL compilation error:|" + line(2, 22, "1"),
            outcome("DECLARE\n  r RESULTSET DEFAULT 1;\nBEGIN\n  RETURN 'x';\nEND;"));
    }

    @Test
    void theFirstTokenOutOfPlaceIsNamedWhenItIsTheOnlyFault() {
        assertEquals("SQL compilation error:|" + line(3, 0, "END"), outcome("BEGIN\n  RETURN 1\nEND;"));
        assertEquals("SQL compilation error:|" + line(3, 5, "junk"), outcome("BEGIN\n  RETURN 'x';\nEND; junk"));
        // And the shapes that are no fault at all.
        assertEquals("ACCEPTED null", outcome("BEGIN\n  RETURN ;\nEND;"));
        assertEquals("ACCEPTED x", outcome("BEGIN\n  LET r RESULTSET := (SELECT 1);\n  RETURN 'x';\nEND;"));
    }
}
