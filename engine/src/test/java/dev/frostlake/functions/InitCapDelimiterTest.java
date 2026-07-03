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

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * INITCAP uses Snowflake's default delimiter set (whitespace plus a fixed punctuation set), and an
 * explicit delimiters argument overrides it — an empty string meaning the whole input is one word.
 * Previously it split only on whitespace and ignored the second argument.
 */
public class InitCapDelimiterTest extends BaseDatabaseTest {

    private String initcap(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void whitespaceDelimitersByDefault() {
        assertEquals("The Sky Is Blue", initcap("SELECT INITCAP('the sky is blue')"));
    }

    @Test
    public void punctuationIsADefaultDelimiter() {
        // '+' and '_' are in the default delimiter set, so they start new words.
        assertEquals("Sky+Blue", initcap("SELECT INITCAP('sky+blue')"));
        assertEquals("Hell0_Hi+There", initcap("SELECT INITCAP('HelL0_hi+therE')"));
    }

    @Test
    public void emptyDelimiterTreatsWholeInputAsOneWord() {
        assertEquals("This is the new frame+work",
                initcap("SELECT INITCAP('this is the new Frame+work', '')"));
    }

    @Test
    public void explicitDelimiterOverridesDefault() {
        // Only ' ' is a delimiter here, so '+' no longer starts a new word.
        assertEquals("Sky+blue Sea", initcap("SELECT INITCAP('sky+blue sea', ' ')"));
    }
}
