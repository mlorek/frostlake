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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A DROP's or a DESCRIBE's signature in a block. A faulty one in the body of an IF, a CASE or a loop gives the
 * construct up, and what follows is read from the block around it; in a nested block or a handler it is read as
 * in the block itself. A word run in after a signature is refused, and the block is given up when the
 * statement ends there (live-verified). No DROP here reaches its table.
 */
public class BlockSignatureFaultLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aFaultySignatureInAConstructsBody() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t3 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 3 at position 19 unexpected 'VIEW'. | syntax "
                + "error line 5 at position 0 unexpected 'END'.",
            "BEGIN\n  IF (TRUE) THEN\n    DROP TABLE t3 (VIEW);\n  END IF;\nEND;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 51 unexpected 'END'.",
            "BEGIN IF (TRUE) THEN DROP TABLE t3 (VIEW); END IF; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 50 unexpected 'END'.",
            "BEGIN LOOP DROP TABLE t3 (VIEW); BREAK; END LOOP; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 58 unexpected 'END'.",
            "BEGIN CASE WHEN TRUE THEN DROP TABLE t3 (VIEW); END CASE; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 61 unexpected ')'.",
            "BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN DROP TABLE t3 (VIEW); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 51 unexpected 'FOR'.",
            "BEGIN FOR i IN 1 TO 2 DO DROP TABLE t3 (VIEW); END FOR; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 56 unexpected 'END'.",
            "BEGIN WHILE (FALSE) DO DROP TABLE t3 (VIEW); END WHILE; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 61 unexpected 'END'.",
            "BEGIN IF (TRUE) THEN DROP TABLE t3 (VIEW); RETURN 1; END IF; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 44 unexpected ')'.",
            "BEGIN IF (TRUE) THEN DESCRIBE TABLE t3 (VIEW); END IF; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 31 unexpected ')'.",
            "BEGIN BEGIN DROP TABLE t3 (VIEW); END; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "BEGIN DROP TABLE t3 (VIEW); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '1'.",
            "BEGIN IF (TRUE) THEN DROP TABLE t3 (1); END IF; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'b'.",
            "BEGIN IF (TRUE) THEN DROP TABLE t3 (a b); END IF; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 43 unexpected 'ELSE'. | syntax error line 1 at position 55 unexpected "
                + "'2'.",
            "BEGIN IF (TRUE) THEN DROP TABLE t3 (VIEW); ELSE RETURN 2; END IF; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 66 unexpected 'END'.",
            "BEGIN IF (TRUE) THEN RETURN 1; ELSE DROP TABLE t3 (VIEW); END IF; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 51 unexpected 'RETURN'.",
            "BEGIN IF (TRUE) THEN DROP TABLE t3 (VIEW); END IF; RETURN 1; END;");
    }

    @Test
    public void aWordRunInAfterTheSignature() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'. | syntax error "
                + "line 1 at position 27 unexpected 'RETURN'.",
            "BEGIN DROP TABLE t7 (a) x; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'. | syntax error "
                + "line 1 at position 26 unexpected 'y'.",
            "BEGIN DROP TABLE t7 (a) x y; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'.",
            "BEGIN DROP TABLE t7 (a) 1; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'. | syntax error "
                + "line 1 at position 35 unexpected 'RETURN'.",
            "BEGIN DROP TABLE t7 (a) CASCADE x; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ';'.",
            "BEGIN DESCRIBE TABLE t7 (a) x; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'x'. | syntax error "
                + "line 1 at position 50 unexpected 'RETURN'.",
            "BEGIN IF (TRUE) THEN DROP TABLE t7 (a) x; END IF; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'. | syntax error "
                + "line 1 at position 23 unexpected 'RETURN'.",
            "BEGIN DROP TABLE t7 x; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'. | syntax error "
                + "line 1 at position 27 unexpected 'END'.",
            "BEGIN DROP TABLE t7 (a) x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'. | syntax error "
                + "line 1 at position 27 unexpected 'LET'.",
            "BEGIN DROP TABLE t7 (a) x; LET q := 1; RETURN q; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'x'. | syntax error "
                + "line 1 at position 39 unexpected 'RETURN'.",
            "BEGIN LET q := 1; DROP TABLE t7 (a) x; RETURN q; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 20 unexpected 'x'. | syntax error "
                + "line 3 at position 2 unexpected 'RETURN'.",
            "BEGIN\n  DROP TABLE t7 (a) x;\n  RETURN 1;\nEND;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '1'. | syntax error "
                + "line 1 at position 27 unexpected 'RETURN'.",
            "BEGIN DROP TABLE t7 (1) x; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'. | syntax error "
                + "line 1 at position 27 unexpected 'RETURN'.",
            "BEGIN DROP TABLE t7 (a) x; RETURN 1; EXCEPTION WHEN OTHER THEN RETURN 2; END;");
    }

    @Test
    public void otherKindsAndProperties() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected ''x''.",
            "BEGIN DROP SECRET IF EXISTS nosuch_sig_zq_7f3a91 ('x'); RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 54 unexpected ')'.",
            "BEGIN DROP SECRET IF EXISTS nosuch_sig_zq_7f3a91 (VIEW); RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 80 unexpected 'RETURN'.",
            "BEGIN IF (TRUE) THEN DROP SECRET IF EXISTS nosuch_sig_zq_7f3a91 (VIEW); END IF; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','. | syntax error "
                + "line 1 at position 31 unexpected ';'.",
            "BEGIN DESCRIBE TABLE t7 x = , y; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ';'.",
            "BEGIN DESCRIBE TABLE t7 x =; RETURN 1; END;");
    }
}
