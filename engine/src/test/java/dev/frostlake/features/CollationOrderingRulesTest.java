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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The order a locale collation puts two strings in, cell by cell. A separator carries a weight of its
 * own below every letter; width and the non-breaking space fold away only once the collation stops
 * being fully sensitive; accent-insensitivity keeps a locale's OWN letters apart while merging its mere
 * accents; and accents are compared from the START of the string in every locale. Live-verified.
 */
public class CollationOrderingRulesTest extends BaseDatabaseTest {

    /** The single BOOLEAN cell a comparison answers, as text. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount());
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString().toUpperCase();
    }

    private void assertTrueCell(final String sql) {
        assertEquals("TRUE", answer(sql), sql);
    }

    private void assertFalseCell(final String sql) {
        assertEquals("FALSE", answer(sql), sql);
    }

    /** A space and a hyphen sort BELOW every letter and digit, each with a weight of its own. */
    @Test
    public void separatorsWeighBelowLetters() {
        assertTrueCell("SELECT 'a b' COLLATE 'en' < 'ab'");
        assertFalseCell("SELECT 'a b' COLLATE 'en' = 'ab'");
        assertTrueCell("SELECT 'a-b' COLLATE 'en' < 'ab'");
        assertTrueCell("SELECT 'a b' COLLATE 'en' < 'a-b'");
        assertTrueCell("SELECT 'a_b' COLLATE 'en' < 'ab'");
        assertTrueCell("SELECT 'a.b' COLLATE 'en' < 'ab'");
        assertTrueCell("SELECT '1' COLLATE 'en' < 'a'");
        assertTrueCell("SELECT '_' COLLATE 'en' < 'a'");
        assertTrueCell("SELECT '-' COLLATE 'en' < '+'");
        assertTrueCell("SELECT '' COLLATE 'en' < ' '");
        assertFalseCell("SELECT ' ' COLLATE 'en' = ''");
        assertFalseCell("SELECT 'a  b' COLLATE 'en' = 'a b'");
    }

    /** Width and the non-breaking space are told apart by a fully sensitive collation and by no other. */
    @Test
    public void widthAndNonBreakingSpaceFoldBelowFullSensitivity() {
        assertFalseCell("SELECT 'a\u00A0b' COLLATE 'en' = 'a b'");
        assertTrueCell("SELECT 'a\u00A0b' COLLATE 'en-ci' = 'a b'");
        assertFalseCell("SELECT 'a\u00A0b' = 'a b'");
        assertTrueCell("SELECT 'a' COLLATE 'en-ci' = '\uFF21'");
        assertFalseCell("SELECT 'A' COLLATE 'en' = '\uFF21'");
        assertFalseCell("SELECT 'a' COLLATE 'en' = '\uFF41'");
        assertTrueCell("SELECT 'a' COLLATE 'en' < '\uFF41'");
        assertTrueCell("SELECT 'a' COLLATE 'en-ai' = '\uFF41'");
        assertFalseCell("SELECT 'a' = '\uFF41'");
    }

    /** Accent-insensitivity merges accents, never a letter the locale tailors as its own. */
    @Test
    public void accentInsensitivityKeepsALocalesOwnLetters() {
        assertFalseCell("SELECT 'a' COLLATE 'pl' = '\u0105'");
        assertFalseCell("SELECT 'a' COLLATE 'pl-ai' = '\u0105'");
        assertFalseCell("SELECT 'a' COLLATE 'pl-cs-ai' = '\u0105'");
        assertFalseCell("SELECT 'a' COLLATE 'pl-ci' = '\u0104'");
        assertTrueCell("SELECT 'a' COLLATE 'pl' < '\u0105'");
        assertTrueCell("SELECT '\u0105' COLLATE 'pl' < 'b'");
        assertFalseCell("SELECT '\u00DF' COLLATE 'de' = 'ss'");
        assertTrueCell("SELECT '\u00DF' COLLATE 'de-ai' = 'ss'");
    }

    /** Accents are compared from the START of the string, in French as everywhere else. */
    @Test
    public void accentsCompareForward() {
        assertTrueCell("SELECT 'cote' COLLATE 'fr' < 'c\u00F4te'");
        assertFalseCell("SELECT 'c\u00F4te' COLLATE 'fr' < 'cot\u00E9'");
        assertTrueCell("SELECT 'cote' COLLATE 'fr' < 'cot\u00E9'");
        assertTrueCell("SELECT 'c\u00F4t\u00E9' COLLATE 'fr' < 'cot\u00E9e'");
    }

    /** A locale's own letter order, and the case tiers the preference options choose. */
    @Test
    public void localeOrderAndCaseTiers() {
        assertTrueCell("SELECT '\u00E4' COLLATE 'sv' > 'z'");
        assertFalseCell("SELECT '\u00E4' COLLATE 'de' > 'z'");
        assertTrueCell("SELECT '\u00F6' COLLATE 'de' < 'p'");
        assertTrueCell("SELECT 'a' COLLATE 'en' < 'A'");
        assertFalseCell("SELECT 'a' COLLATE 'en-fu' < 'A'");
        assertFalseCell("SELECT 'A' COLLATE 'en-fl' < 'a'");
        assertFalseCell("SELECT 'a' COLLATE 'utf8' < 'A'");
        assertTrueCell("SELECT 'a b' COLLATE 'utf8' < 'ab'");
        assertFalseCell("SELECT 'a' COLLATE '' = 'A'");
    }

    /** The trimming, case-conversion and punctuation options. */
    @Test
    public void trimCaseAndPunctuationOptions() {
        assertTrueCell("SELECT 'a ' COLLATE 'en-rtrim' = 'a'");
        assertTrueCell("SELECT ' a' COLLATE 'en-ltrim' = 'a'");
        assertTrueCell("SELECT ' a ' COLLATE 'en-trim' = 'a'");
        assertTrueCell("SELECT 'A' COLLATE 'en-lower' = 'a'");
        assertTrueCell("SELECT 'a' COLLATE 'en-upper' = 'A'");
        assertTrueCell("SELECT 'a-b' COLLATE 'en-pi' = 'ab'");
        assertTrueCell("SELECT 'a b' COLLATE 'en-pi' = 'ab'");
        assertTrueCell("SELECT 'a.b' COLLATE 'en-pi' = 'ab'");
    }
}
