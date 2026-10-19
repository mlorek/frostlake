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
 * The properties after a DESCRIBE's object when no signature stands before them: the account reads them as it
 * reads them after one — a value in parentheses, a CASE or a CAST where a value stands, a comma or nothing where
 * one is due, a path, a cast, a stage reference — in a statement alone, in a script and in a block, where an
 * unknown name is refused while the block compiles (live-verified).
 */
public class DescribePropertyLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void caseAndCastValues() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t3 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'. | syntax "
                + "error line 1 at position 26 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 x = CASE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'WHEN'. | syntax "
                + "error line 1 at position 27 unexpected 'WHEN'.",
            "DESCRIBE TABLE t1 x = CASE WHEN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'. | syntax error "
                + "line 1 at position 27 unexpected '1'.",
            "DESCRIBE TABLE t1 x = CASE 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'. | syntax "
                + "error line 1 at position 26 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 x = CAST");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 26 unexpected '('. | syntax error line 1 at position 29 unexpected 'AS'. | "
                + "syntax error line 1 at position 35 unexpected ')'.",
            "DESCRIBE TABLE t1 x = CAST(1 AS INT)");
    }

    @Test
    public void listsOfValues() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t3 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED Invalid value specified for property 'TYPE'",
            "DESCRIBE TABLE t1 TYPE = (COLUMNS)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'b'. | syntax error "
                + "line 1 at position 25 unexpected 'b'. | syntax error line 1 at position 26 unexpected ')'.",
            "DESCRIBE TABLE t1 x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'b'. | syntax error "
                + "line 1 at position 25 unexpected 'b'. | syntax error line 1 at position 28 unexpected ')'.",
            "DESCRIBE TABLE t1 x = (a b c)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'b'.",
            "DESCRIBE TABLE t1 x = (1 b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '1'. | syntax error "
                + "line 1 at position 25 unexpected '1'. | syntax error line 1 at position 26 unexpected ')'.",
            "DESCRIBE TABLE t1 x = (a 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'c'. | syntax error "
                + "line 1 at position 28 unexpected 'c'. | syntax error line 1 at position 29 unexpected ')'.",
            "DESCRIBE TABLE t1 x = (a, b c)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','. | syntax error "
                + "line 1 at position 24 unexpected '1'.",
            "DESCRIBE TABLE t1 x = (,1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ')'.",
            "DESCRIBE TABLE t1 x = (1,)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 x = (,)");
    }

    @Test
    public void commasAndMissingParts() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t3 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 x = ,");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ','.",
            "DESCRIBE TABLE t1 x = , y = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 22 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 x, y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'y'.",
            "DESCRIBE TABLE t1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 x =");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 x = 1, y");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = 1 y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = 1, y = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = (1, 2) y = 3");
    }

    @Test
    public void pathsCastsAndStages() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t3 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '::'. | syntax error "
                + "line 1 at position 23 unexpected '.'. | syntax error line 1 at position 25 unexpected '::'. | "
                + "syntax error line 1 at position 30 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 x = a.b::INT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '::'. | syntax error "
                + "line 1 at position 23 unexpected '::'. | syntax error line 1 at position 28 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 x = a::INT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '::'.",
            "DESCRIBE TABLE t1 x = 1::INT");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = a.b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1'. | syntax error "
                + "line 1 at position 23 unexpected '.'. | syntax error line 1 at position 25 unexpected '.'. | "
                + "syntax error line 1 at position 28 unexpected '1'.",
            "DESCRIBE TABLE t1 x = a.b.c 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '@s'.",
            "DESCRIBE TABLE t1 x = (@s)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = @s");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = @s/p");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '@s'.",
            "DESCRIBE TABLE t1 x = 1, @s");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '@s'. | syntax error "
                + "line 1 at position 27 unexpected '1'.",
            "DESCRIBE TABLE t1 x = (@s, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = '@s'");
    }

    @Test
    public void typeAndKnownValues() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t3 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 TYPE = COLUMNS");
        assertAnswer(
            "STAGE_FILE_FORMAT|TYPE|String|CSV|CSV ;; STAGE_FILE_FORMAT|RECORD_DELIMITER|String|\\n|\\n ;; "
                + "STAGE_FILE_FORMAT|FIELD_DELIMITER|String|,|, ;; STAGE_FILE_FORMAT|FILE_EXTENSION|String|| ;; "
                + "STAGE_FILE_FORMAT|SKIP_HEADER|Integer|0|0 ;; STAGE_FILE_FORMAT|PARSE_HEADER|Boolean|false|false "
                + ";; STAGE_FILE_FORMAT|DATE_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|TIME_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|TIMESTAMP_FORMAT|String|AUTO|AUTO ;; "
                + "STAGE_FILE_FORMAT|BINARY_FORMAT|String|HEX|HEX ;; STAGE_FILE_FORMAT|ESCAPE|String|NONE|NONE ;; "
                + "STAGE_FILE_FORMAT|ESCAPE_UNENCLOSED_FIELD|String|\\\\|\\\\ ;; "
                + "STAGE_FILE_FORMAT|TRIM_SPACE|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|FIELD_OPTIONALLY_ENCLOSED_BY|String|NONE|NONE ;; "
                + "STAGE_FILE_FORMAT|NULL_IF|List|[\\\\N]|[\\\\N] ;; STAGE_FILE_FORMAT|COMPRESSION|String|AUTO|AUTO "
                + ";; STAGE_FILE_FORMAT|ERROR_ON_COLUMN_COUNT_MISMATCH|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|VALIDATE_UTF8|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|SKIP_BLANK_LINES|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|REPLACE_INVALID_CHARACTERS|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|EMPTY_FIELD_AS_NULL|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|SKIP_BYTE_ORDER_MARK|Boolean|true|true ;; "
                + "STAGE_FILE_FORMAT|ENCODING|String|UTF8|UTF8 ;; STAGE_FILE_FORMAT|MULTI_LINE|Boolean|true|true ;; "
                + "STAGE_COPY_OPTIONS|ON_ERROR|String|ABORT_STATEMENT|ABORT_STATEMENT ;; "
                + "STAGE_COPY_OPTIONS|SIZE_LIMIT|Long|| ;; STAGE_COPY_OPTIONS|PURGE|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|RETURN_FAILED_ONLY|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|ENFORCE_LENGTH|Boolean|true|true ;; "
                + "STAGE_COPY_OPTIONS|TRUNCATECOLUMNS|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|FORCE|Boolean|false|false ;; STAGE_LOCATION|URL|String||",
            "DESCRIBE TABLE t1 TYPE = STAGE");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [x] for parameter 'TYPE'",
            "DESCRIBE TABLE t1 TYPE = x");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE t1 x = 1");
    }

    @Test
    public void afterASignatureAndInAScript() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t3 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '::'. | syntax error "
                + "line 1 at position 27 unexpected '.'. | syntax error line 1 at position 29 unexpected '::'. | "
                + "syntax error line 1 at position 34 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = a.b::INT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '@s'.",
            "DESCRIBE TABLE t1 (a) x = 1, @s");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | syntax error "
                + "line 1 at position 29 unexpected '@s'. | syntax error line 1 at position 33 unexpected '1'.",
            "DESCRIBE TABLE t1 (a b) x = (@s, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'b'. | syntax error "
                + "line 1 at position 21 unexpected 'b'. | syntax error line 1 at position 22 unexpected ')'.",
            "DESC TABLE t1 x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'b'. | syntax error "
                + "line 1 at position 25 unexpected 'b'. | syntax error line 1 at position 26 unexpected ')'.",
            "DESCRIBE TABLE t1 x = (a b); SELECT 1 x y");
    }

    @Test
    public void inABlock() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t3 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "BEGIN\n  DESCRIBE TABLE t1 (a) x = 1;\nEND;");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "BEGIN DESCRIBE TABLE t1 x = 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'b'. | syntax error "
                + "line 1 at position 31 unexpected 'b'. | syntax error line 1 at position 32 unexpected ')'.",
            "BEGIN DESCRIBE TABLE t1 x = (a b); END;");
        assertAnswer(
            "1",
            "BEGIN DESCRIBE TABLE t1 TYPE = COLUMNS; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "BEGIN LET q := 1; DESCRIBE TABLE t1 x = 1; END;");
    }
}
