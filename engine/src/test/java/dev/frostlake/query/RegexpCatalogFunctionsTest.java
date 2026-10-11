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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * REGEXP_COUNT, REGEXP_INSTR, REGEXP_REPLACE and REGEXP_SUBSTR as the account answers them: positions and
 * occurrences count from 1, the parameter letters are judged before anything is matched, and the empty pattern
 * matches nothing at all - a count of 0, a position of 0, a NULL substring, the subject unchanged - where a
 * pattern that can match the empty text matches it at every place. Every answer is live-verified.
 */
public class RegexpCatalogFunctionsTest extends MeasuredCellsTestSupport {

    @Test
    public void positionsOccurrencesAndGroups() {
        assertAnswer(
            "3",
            "SELECT REGEXP_COUNT('aXbXcX', 'X')");
        assertAnswer(
            "NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(REGEXP_COUNT('aXbXcX', 'X'))");
        assertAnswer(
            "2",
            "SELECT REGEXP_COUNT('aXbXcX', 'X', 3)");
        assertAnswer(
            "NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(REGEXP_COUNT('aXbXcX', 'X', 3))");
        assertAnswer(
            "3",
            "SELECT REGEXP_COUNT('aXbxcX', 'x', 1, 'i')");
        assertAnswer(
            "NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(REGEXP_COUNT('aXbxcX', 'x', 1, 'i'))");
        assertAnswer(
            "0",
            "SELECT REGEXP_COUNT('abc', '')");
        assertAnswer(
            "NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(REGEXP_COUNT('abc', ''))");
        assertAnswer(
            "2",
            "SELECT REGEXP_INSTR('aXbXcX', 'X')");
        assertAnswer(
            "NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(REGEXP_INSTR('aXbXcX', 'X'))");
        assertAnswer(
            "4",
            "SELECT REGEXP_INSTR('aXbXcX', 'X', 1, 2)");
        assertAnswer(
            "NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(REGEXP_INSTR('aXbXcX', 'X', 1, 2))");
        assertAnswer(
            "5",
            "SELECT REGEXP_INSTR('aXbXcX', 'X', 1, 2, 1)");
        assertAnswer(
            "NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(REGEXP_INSTR('aXbXcX', 'X', 1, 2, 1))");
        assertAnswer(
            "0",
            "SELECT REGEXP_INSTR('aXbXcX', 'Z')");
        assertAnswer(
            "NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(REGEXP_INSTR('aXbXcX', 'Z'))");
        assertAnswer(
            "3",
            "SELECT REGEXP_INSTR('abcabc', '(b)(c)', 1, 1, 0, 'e', 2)");
        assertAnswer(
            "NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(REGEXP_INSTR('abcabc', '(b)(c)', 1, 1, 0, 'e', 2))");
        assertAnswer(
            "abc",
            "SELECT REGEXP_REPLACE('aXbXcX', 'X')");
        assertAnswer(
            "VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(REGEXP_REPLACE('aXbXcX', 'X'))");
        assertAnswer(
            "a-b-c-",
            "SELECT REGEXP_REPLACE('aXbXcX', 'X', '-')");
        assertAnswer(
            "VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(REGEXP_REPLACE('aXbXcX', 'X', '-'))");
        assertAnswer(
            "aXb-c-",
            "SELECT REGEXP_REPLACE('aXbXcX', 'X', '-', 3)");
        assertAnswer(
            "VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(REGEXP_REPLACE('aXbXcX', 'X', '-', 3))");
        assertAnswer(
            "aXb-cX",
            "SELECT REGEXP_REPLACE('aXbXcX', 'X', '-', 1, 2)");
        assertAnswer(
            "VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(REGEXP_REPLACE('aXbXcX', 'X', '-', 1, 2))");
        assertAnswer(
            "bac",
            "SELECT REGEXP_REPLACE('abc', '(a)(b)', '\\\\2\\\\1')");
        assertAnswer(
            "VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(REGEXP_REPLACE('abc', '(a)(b)', '\\\\2\\\\1'))");
        assertAnswer(
            "a-b-c-",
            "SELECT REGEXP_REPLACE('aXbxcX', 'x', '-', 1, 0, 'i')");
        assertAnswer(
            "VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(REGEXP_REPLACE('aXbxcX', 'x', '-', 1, 0, 'i'))");
        assertAnswer(
            "aX",
            "SELECT REGEXP_SUBSTR('aXbXcX', '[a-c]X')");
        assertAnswer(
            "VARCHAR(6)[LOB]",
            "SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR('aXbXcX', '[a-c]X'))");
        assertAnswer(
            "bX",
            "SELECT REGEXP_SUBSTR('aXbXcX', '[a-c]X', 1, 2)");
        assertAnswer(
            "VARCHAR(6)[LOB]",
            "SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR('aXbXcX', '[a-c]X', 1, 2))");
        assertAnswer(
            "c",
            "SELECT REGEXP_SUBSTR('aXbXcX', '([a-c])X', 1, 3, 'e')");
        assertAnswer(
            "VARCHAR(8)[LOB]",
            "SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR('aXbXcX', '([a-c])X', 1, 3, 'e'))");
        assertAnswer(
            "X",
            "SELECT REGEXP_SUBSTR('aXbXcX', '([a-c])(X)', 1, 1, 'e', 2)");
        assertAnswer(
            "VARCHAR(10)[LOB]",
            "SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR('aXbXcX', '([a-c])(X)', 1, 1, 'e', 2))");
        assertAnswer(
            "null",
            "SELECT REGEXP_SUBSTR('abc', 'z')");
        assertAnswer(
            "VARCHAR(3)[LOB]",
            "SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR('abc', 'z'))");
        assertAnswer(
            "null",
            "SELECT REGEXP_SUBSTR(NULL, 'a')");
        assertAnswer(
            "VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(REGEXP_SUBSTR(NULL, 'a'))");
        assertAnswer(
            "REFUSED Invalid regular expression: '(', missing ): (",
            "SELECT REGEXP_COUNT('abc', '(')");
        assertAnswer(
            "REFUSED Invalid parameter value: 0. Reason: Position must be positive",
            "SELECT REGEXP_INSTR('abc', 'b', 0)");
        assertAnswer(
            "REFUSED Invalid parameter value: -1. Reason: Occurrence must not be negative",
            "SELECT REGEXP_REPLACE('abc', 'b', 'x', 1, -1)");
        assertAnswer(
            "REFUSED Invalid parameter value: 0. Reason: Occurrence must be positive",
            "SELECT REGEXP_SUBSTR('abc', 'b', 1, 0)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[REGEXP_COUNT('abc')], expected 2, got 1",
            "SELECT REGEXP_COUNT('abc')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[REGEXP_REPLACE('abc')], expected 2, got 1",
            "SELECT REGEXP_REPLACE('abc')");
    }

    @Test
    public void theEmptyPattern() {
        assertAnswer(
            "0",
            "SELECT REGEXP_COUNT('abc', '')");
        assertAnswer(
            "0",
            "SELECT REGEXP_COUNT('', '')");
        assertAnswer(
            "4",
            "SELECT REGEXP_COUNT('abc', 'x*')");
        assertAnswer(
            "4",
            "SELECT REGEXP_COUNT('abc', 'b*')");
        assertAnswer(
            "1",
            "SELECT REGEXP_COUNT('abc', '^')");
        assertAnswer(
            "0",
            "SELECT REGEXP_INSTR('abc', '')");
        assertAnswer(
            "1",
            "SELECT REGEXP_INSTR('abc', 'x*')");
        assertAnswer(
            "1",
            "SELECT REGEXP_INSTR('abc', 'b*')");
        assertAnswer(
            "2",
            "SELECT REGEXP_INSTR('abc', 'b*', 1, 2)");
        assertAnswer(
            "abc",
            "SELECT REGEXP_REPLACE('abc', '', '-')");
        assertAnswer(
            "-a-b-c-",
            "SELECT REGEXP_REPLACE('abc', 'x*', '-')");
        assertAnswer(
            "-a--c-",
            "SELECT REGEXP_REPLACE('abc', 'b*', '-')");
        assertAnswer(
            "",
            "SELECT REGEXP_REPLACE('', '', '-')");
        assertAnswer(
            "true|null",
            "SELECT REGEXP_SUBSTR('abc', '') IS NULL, REGEXP_SUBSTR('abc', '')");
        assertAnswer(
            "false|",
            "SELECT REGEXP_SUBSTR('abc', 'x*') IS NULL, REGEXP_SUBSTR('abc', 'x*')");
        assertAnswer(
            "false|",
            "SELECT REGEXP_SUBSTR('abc', 'b*') IS NULL, REGEXP_SUBSTR('abc', 'b*')");
        assertAnswer(
            "b",
            "SELECT REGEXP_SUBSTR('abc', 'b*', 1, 2)");
        assertAnswer(
            "false",
            "SELECT REGEXP_LIKE('abc', '')");
        assertAnswer(
            "true",
            "SELECT REGEXP_LIKE('', '')");
        assertAnswer(
            "null",
            "SELECT REGEXP_SUBSTR_ALL('abc', '')");
        assertAnswer(
            "[\"\",\"b\",\"\",\"\"]",
            "SELECT REGEXP_SUBSTR_ALL('abc', 'b*')");
        assertAnswer(
            "null",
            "SELECT REGEXP_COUNT('abc', NULL)");
        assertAnswer(
            "null",
            "SELECT REGEXP_COUNT(NULL, 'a')");
        assertAnswer(
            "0",
            "SELECT REGEXP_COUNT('aaa', 'a', 4)");
        assertAnswer(
            "REFUSED Invalid parameter value: 0. Reason: Position must be positive",
            "SELECT REGEXP_COUNT('aaa', 'a', 0)");
        assertAnswer(
            "REFUSED Invalid parameter value: x. Reason: Unknown parameter: 'x'",
            "SELECT REGEXP_COUNT('aaa', 'a', 1, 'x')");
        assertAnswer(
            "null",
            "SELECT REGEXP_COUNT('aaa', 'a', NULL)");
    }
}
