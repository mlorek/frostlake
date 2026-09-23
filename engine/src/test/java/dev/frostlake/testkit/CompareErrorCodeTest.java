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

package dev.frostlake.testkit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A corpus spells an error code as the SQL REST API does, six zero-padded digits, while a JDBC driver reports
 * the vendor code as an int: two all-digit codes compare by value, anything else exactly.
 */
public class CompareErrorCodeTest {

    @Test
    public void aZeroPaddedCodeMatchesTheDriversInteger() {
        assertTrue(Compare.sameErrorCode("001003", "1003"));
        assertTrue(Compare.sameErrorCode("1003", "001003"));
        assertTrue(Compare.sameErrorCode("001003", "001003"));
        assertTrue(Compare.sameErrorCode("000", "0"));
    }

    @Test
    public void anotherCodeOrNoCodeDoesNotMatch() {
        assertFalse(Compare.sameErrorCode("001003", "2003"));
        assertFalse(Compare.sameErrorCode("001003", "10030"));
        assertFalse(Compare.sameErrorCode("001003", null));
        assertFalse(Compare.sameErrorCode("001003", ""));
    }

    @Test
    public void aCodeThatIsNotAllDigitsComparesExactly() {
        assertTrue(Compare.sameErrorCode("P0001", "P0001"));
        assertFalse(Compare.sameErrorCode("P0001", "p0001"));
        assertFalse(Compare.sameErrorCode("01003x", "1003x"));
    }
}
