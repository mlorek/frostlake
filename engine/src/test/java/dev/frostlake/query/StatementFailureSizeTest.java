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
 * A failed statement's message of more than 16384 bytes of UTF-8 is cut to its first 16384 characters and ended by
 * {@code ... (Original error message truncated since it exceeded ERROR_MSG_MAX_SIZE).}, twice over: a message of
 * fewer characters but more bytes keeps them all and ends with the sentence twice (all live-verified).
 */
public class StatementFailureSizeTest extends MeasuredCellsTestSupport {

    private static final String CUT = "... (Original error message truncated since it exceeded ERROR_MSG_MAX_SIZE).";

    /** The refusal of a text that reads as no number. */
    private static String notANumber(final String text) {
        return "Numeric value '" + text + "' is not recognized";
    }

    @Test
    public void aMessageOfMoreThan16384BytesIsCut() {
        assertAnswer("REFUSED " + notANumber("a".repeat(16350)), "SELECT REPEAT('a', 16350)::INT");
        assertAnswer("REFUSED " + notANumber("a".repeat(16351)).substring(0, 16384) + CUT,
            "SELECT REPEAT('a', 16351)::INT");
        assertAnswer("REFUSED " + notANumber("a".repeat(20000)).substring(0, 16384) + CUT,
            "SELECT REPEAT('a', 20000)::INT");
    }

    @Test
    public void aMessageOfFewerCharactersButMoreBytesEndsTwice() {
        assertAnswer("REFUSED " + notANumber("é".repeat(9000)) + CUT + CUT, "SELECT REPEAT('é', 9000)::INT");
    }
}
