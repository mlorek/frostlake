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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * JAROWINKLER_SIMILARITY(s1, s2) — Jaro-Winkler similarity as an integer 0–100: case-insensitive, compared by
 * character, a Winkler bonus only from a Jaro similarity of 0.7, and the score truncated. Every cell is
 * live-verified.
 */
public class JarowinklerSimilarityTest extends BaseDatabaseTest {

    private long num(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    /** Each row is two strings and their score. */
    private void assertScores(final String[][] cells) {
        for (final String[] cell : cells) {
            final String sql = "SELECT JAROWINKLER_SIMILARITY('" + cell[0].replace("'", "''") + "', '"
                + cell[1].replace("'", "''") + "')";
            assertEquals(Long.parseLong(cell[2]), num(sql), sql);
        }
    }

    @Test
    public void snowflakeVsOracle() {
        // Documented Snowflake example: 61.
        assertEquals(61L, num("SELECT JAROWINKLER_SIMILARITY('Snowflake', 'Oracle')"));
    }

    @Test
    public void marthaVsMarhta() {
        // Classic Jaro-Winkler example (Jaro 0.944 + 3-char prefix bonus) → 96.
        assertEquals(96L, num("SELECT JAROWINKLER_SIMILARITY('MARTHA', 'MARHTA')"));
    }

    @Test
    public void identicalStringsAreOneHundred() {
        assertEquals(100L, num("SELECT JAROWINKLER_SIMILARITY('Frostlake', 'Frostlake')"));
    }

    @Test
    public void comparisonIsCaseInsensitive() {
        assertEquals(100L, num("SELECT JAROWINKLER_SIMILARITY('SNOW', 'snow')"));
    }

    @Test
    public void nullArgumentYieldsNull() {
        assertNull(scalar("SELECT JAROWINKLER_SIMILARITY(NULL, 'x')"));
        assertNull(scalar("SELECT JAROWINKLER_SIMILARITY('x', NULL)"));
    }

    @Test
    public void singleCharactersScoreTheirMatch() {
        assertScores(new String[][] {
            {"a", "a", "100"},
            {"a", "A", "100"},
            {"b", "b", "100"},
            {"a", "b", "0"},
            {"A", "b", "0"},
            {"ä", "Ä", "100"},
            {"É", "é", "100"},
            {"Ω", "ω", "100"},
            {"ǅ", "ǆ", "100"},
            {"😀", "😀", "100"},
        });
        assertEquals(100L, num("SELECT JAROWINKLER_SIMILARITY(COLLATE('a', 'en-ci'), 'A')"));
        assertEquals(100L, num("SELECT JAROWINKLER_SIMILARITY('a' COLLATE 'en-cs', 'A')"));
    }

    @Test
    public void anEmptyStringScoresZero() {
        assertScores(new String[][] {
            {"", "a", "0"},
            {"a", "", "0"},
            {"", "", "0"},
            {"", "adddccb", "0"},
        });
    }

    @Test
    public void onlyASevenTenthsJaroEarnsThePrefixBonus() {
        assertScores(new String[][] {
            {"ab", "ac", "66"},
            {"abcxyz", "abuvwq", "55"},
            {"prefixxxxxx", "prefixyyyyy", "69"},
            {"a b", "ab", "61"},
            {"aXbXc", "abc", "68"},
            {"😀a", "😀b", "66"},
            {"abcdefg", "abcdefh", "94"},
            {"aaaab", "aaaac", "92"},
            {"Dwayne", "Duane", "84"},
            {"DwAyNE", "DuANE", "84"},
            {"abc", "abd", "82"},
        });
    }

    @Test
    public void theScoreIsTruncated() {
        assertScores(new String[][] {
            {"bcc", "bbcacbccac", "78"},
            {"bcabb", "caacccca", "54"},
            {"cabbcabcc", "cbbccc", "89"},
            {"acaa", "abcaab", "89"},
            {"caaaaa", "ccaaaaaca", "89"},
            {"Jellyfish", "Smellyfish", "89"},
            {"Straße", "STRASSE", "90"},
            {"hello", "help", "84"},
            {"abcd", "bcd", "91"},
            {"aaaa", "aa", "86"},
        });
    }

    @Test
    public void halfTheOutOfOrderPairsRoundedDownAreTranspositions() {
        assertScores(new String[][] {
            {"abc", "cba", "55"},
            {"xyz", "zyx", "55"},
            {"abcabc", "cbacba", "88"},
            {"dcb", "abcb", "55"},
            {"cdbdcca", "cbacb", "67"},
            {"ab", "ba", "0"},
            {"CRATE", "TRACE", "73"},
        });
    }

    @Test
    public void caseFoldingIsTheFullLowerCaseMapping() {
        assertScores(new String[][] {
            {"İ", "i", "85"},
            {"ß", "SS", "0"},
            {"ı", "I", "0"},
            {"ﬁ", "FI", "0"},
        });
    }

    @Test
    public void measuredPairs() {
        assertScores(new String[][] {
            {"bbdac", "acc", "0"},
            {"cab", "bb", "0"},
            {"abaca", "b", "73"},
            {"cacaad", "dbddac", "38"},
            {"ad", "ad", "100"},
            {"acb", "a", "80"},
            {"d", "ca", "0"},
            {"db", "dabcaac", "78"},
            {"bd", "c", "0"},
            {"aaadd", "acad", "80"},
            {"abcacab", "ccadcda", "63"},
            {"c", "cacddac", "74"},
            {"dadc", "bb", "0"},
            {"adcaaa", "abadd", "58"},
            {"cbcdda", "dbdcab", "69"},
            {"b", "dacdd", "0"},
            {"abcdd", "dcdddcd", "67"},
            {"adcdd", "acbdd", "88"},
            {"bbabcdd", "dacabcb", "50"},
            {"cccabbc", "aba", "49"},
            {"abbcbb", "dadbd", "57"},
            {"ad", "cdb", "61"},
            {"ddcdc", "bd", "56"},
            {"bdaca", "db", "63"},
            {"d", "bbadc", "0"},
            {"adb", "cccbd", "51"},
            {"dbccdadgdcch", "gddchgcegheafggd", "55"},
            {"gedhfcahbbcdbb", "fdeggcae", "61"},
            {"ebabgfbc", "aahecbehaefggce", "51"},
            {"bhdbfeace", "gfhbhgebgchfgdd", "63"},
            {"eaacdghacdadaca", "ghcbdhfdcdfee", "52"},
            {"bfcahaadgahh", "cachdcdedfghefe", "63"},
            {"ghghfeffddfegeb", "hbhbggcbadb", "59"},
        });
    }

    @Test
    public void argumentsAreReadAsTextAndTheResultIsNumberFour() {
        assertEquals(82L, num("SELECT JAROWINKLER_SIMILARITY(123, 124)"));
        assertEquals(100L, num("SELECT JAROWINKLER_SIMILARITY(TRUE, 'TRUE')"));
        assertEquals("NUMBER(4,0)[SB2]", String.valueOf(scalar("SELECT SYSTEM$TYPEOF(JAROWINKLER_SIMILARITY('a', 'b'))")));
    }
}
