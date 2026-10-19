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
package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * What the UNICODE_ANALYZER joins into one word — a colon or a middle dot between two letters, a comma between
 * two digits, and neither the other way round — and the BASE64 decoders given a NULL alphabet, which answer
 * NULL (live-verified).
 */
public class SearchAnalyzerJoinsAndNullAlphabetTest extends MeasuredCellsTestSupport {

    @Test
    public void aNullAlphabet() {
        assertAnswer(
            "null",
            "SELECT BASE64_DECODE_BINARY('YWI=', NULL)");
        assertAnswer(
            "null",
            "SELECT TRY_BASE64_DECODE_BINARY('YWI=', NULL)");
        assertAnswer(
            "null",
            "SELECT TRY_BASE64_DECODE_STRING('YWI=', NULL)");
        assertAnswer(
            "null",
            "SELECT BASE64_DECODE_STRING('YWI=', NULL)");
    }

    @Test
    public void whatJoinsTwoLettersOrTwoDigits() {
        assertAnswer(
            "false",
            "SELECT SEARCH('a:b', 'a', ANALYZER => 'UNICODE_ANALYZER')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a:b', 'a:b', ANALYZER => 'UNICODE_ANALYZER')");
        assertAnswer(
            "true",
            "SELECT SEARCH('1:2', '1', ANALYZER => 'UNICODE_ANALYZER')");
        assertAnswer(
            "false",
            "SELECT SEARCH('1,2', '1', ANALYZER => 'UNICODE_ANALYZER')");
        assertAnswer(
            "true",
            "SELECT SEARCH('1,2', '1,2', ANALYZER => 'UNICODE_ANALYZER')");
        assertAnswer(
            "true",
            "SELECT SEARCH('a,b', 'a', ANALYZER => 'UNICODE_ANALYZER')");
        assertAnswer(
            "true",
            "SELECT SEARCH('1\u00b72', '1', ANALYZER => 'UNICODE_ANALYZER')");
        assertAnswer(
            "false",
            "SELECT SEARCH('a\u00b7b', 'a', ANALYZER => 'UNICODE_ANALYZER')");
        assertAnswer(
            "true",
            "SELECT SEARCH('1:2', '1:2', ANALYZER => 'UNICODE_ANALYZER')");
        assertAnswer(
            "true",
            "SELECT SEARCH('12:30', '12', ANALYZER => 'UNICODE_ANALYZER')");
    }
}
