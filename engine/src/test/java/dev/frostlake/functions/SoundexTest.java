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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SOUNDEX and SOUNDEX_P123 keep the first character exactly as it was written, whatever it is, and code only the ASCII
 * letters after it: vowels separate two equal codes, while H, W and every other character are passed over.
 * SOUNDEX_P123 differs only in coding a letter that shares the first letter's code. Every cell is live-verified.
 */
public class SoundexTest extends BaseDatabaseTest {

    /** Every row's first cell, joined by a bar. */
    private String answer(final String sql) {
        final StringBuilder answer = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (answer.length() > 0) {
                answer.append('|');
            }
            answer.append(String.valueOf(row.getValue(0)));
        }
        return answer.toString();
    }

    /** Each pair is a text and its SOUNDEX, then its SOUNDEX_P123 when a third entry is given. */
    private void assertCodes(final String[][] cells) {
        for (final String[] cell : cells) {
            final String literal = "'" + cell[0].replace("'", "''") + "'";
            assertEquals(cell[1], answer("SELECT SOUNDEX(" + literal + ")"), "SOUNDEX(" + literal + ")");
            if (cell.length > 2) {
                assertEquals(cell[2], answer("SELECT SOUNDEX_P123(" + literal + ")"), "SOUNDEX_P123(" + literal + ")");
            }
        }
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(refused.getMessage() != null && refused.getMessage().contains(fragment),
            sql + " should be refused with \"" + fragment + "\" but read: " + refused.getMessage());
    }

    @Test
    public void theFirstCharacterIsKeptAsWritten() {
        assertCodes(new String[][] {
            {"a", "a000", "a000"},
            {"A", "A000", "A000"},
            {"robert", "r163", "r163"},
            {"Robert", "R163", "R163"},
            {"rupert", "r163", "r163"},
            {"pfister", "p236", "p123"},
            {"lloyd", "l300", "l430"},
            {"ashcraft", "a261", "a261"},
            {"1robert", "1616", "1616"},
            {" robert", " 616", " 616"},
            {"!x", "!200", "!200"},
            {"-a", "-000", "-000"},
            {"9", "9000", "9000"},
            {"123", "1000", "1000"},
            {"1bb", "1100", "1100"},
            {"_bb", "_100", "_100"},
            {"éclair", "é246", "é246"},
            {"Éclair", "É246", "É246"},
            {"ñandu", "ñ530", "ñ530"},
            {"Ärger", "Ä626", "Ä626"},
            {"ß", "ß000", "ß000"},
            {"İstanbul", "İ235", "İ235"},
            {"ıi", "ı000", "ı000"},
            {"Ωmega", "Ω520", "Ω520"},
            {"日本robert", "日616", "日616"},
            {"😀robert", "😀616", "😀616"},
            {"𝐀bb", "𝐀100", "𝐀100"},
            {"ǅb", "ǅ100", "ǅ100"},
            {"ﬁb", "ﬁ100", "ﬁ100"},
            {"", "0000", "0000"},
            {"  ", " 000", " 000"},
        });
        assertCells(new String[][] {
            {"SELECT OCTET_LENGTH(SOUNDEX('😀robert'))", "7"},
            {"SELECT IFF(SOUNDEX(CHR(0) || 'bb') = CHR(0) || '100', 'kept', 'lost')", "kept"},
        });
    }

    @Test
    public void onlyAsciiLettersCarryACode() {
        assertCodes(new String[][] {
            {"aßb", "a100", "a100"},
            {"Straße", "S360", "S360"},
            {"Müller", "M460", "M460"},
            {"Mueller", "M460", "M460"},
            {"béb", "b000", "b100"},
            {"bäb", "b000", "b100"},
            {"bßb", "b000", "b100"},
            {"b日b", "b000", "b100"},
            {"b😀b", "b000", "b100"},
            {"bñb", "b000", "b100"},
            {"bœb", "b000", "b100"},
            {"bİb", "b000", "b100"},
            {"bıb", "b000", "b100"},
            {"b́b", "b000", "b100"},
            {"b1b", "b000", "b100"},
            {"b-b", "b000", "b100"},
            {"b b", "b000", "b100"},
            {"b0b", "b000", "b100"},
            {"b.b", "b000", "b100"},
            {"çc", "ç200", "ç200"},
            {"ćc", "ć200", "ć200"},
            {"ßs", "ß200", "ß200"},
            {"ñn", "ñ500", "ñ500"},
            {"éb", "é100", "é100"},
            {"éé", "é000", "é000"},
            {"açb", "a100", "a100"},
            {"ağ", "a000", "a000"},
        });
    }

    @Test
    public void vowelsSeparateEqualCodesWhileHAndWArePassedOver() {
        assertCodes(new String[][] {
            {"Ashcraft", "A261", "A261"},
            {"Burroughs", "B620", "B620"},
            {"bab", "b100", "b100"},
            {"byb", "b100", "b100"},
            {"bhb", "b000", "b100"},
            {"bwb", "b000", "b100"},
            {"Bhp", "B000", "B100"},
            {"Bwp", "B000", "B100"},
            {"Abhb", "A100", "A100"},
            {"Abwb", "A100", "A100"},
            {"Dht", "D000", "D300"},
            {"Dat", "D300", "D300"},
            {"hb", "h100", "h100"},
            {"wb", "w100", "w100"},
            {"hhb", "h100", "h100"},
            {"Hh", "H000", "H000"},
            {"Hwa", "H000", "H000"},
            {"Honeyman", "H555", "H555"},
            {"Tymczak", "T522", "T522"},
            {"Lukasiewicz", "L222", "L222"},
            {"Aaron", "A650", "A650"},
        });
    }

    @Test
    public void theReferenceCodes() {
        assertCodes(new String[][] {
            {"Rubin", "R150", "R150"},
            {"Pfister", "P236", "P123"},
            {"Pfizer", "P260", "P126"},
            {"Lloyd", "L300", "L430"},
            {"LLoyd", "L300", "L430"},
            {"Jackson", "J250", "J250"},
            {"Washington", "W252", "W252"},
            {"Lee", "L000", "L000"},
            {"Gutierrez", "G362", "G362"},
            {"Wu", "W000", "W000"},
            {"VanDeusen", "V532", "V532"},
            {"Ellery", "E460", "E460"},
            {"Gauss", "G200", "G200"},
            {"Ghosh", "G200", "G200"},
            {"Heilbronn", "H416", "H416"},
            {"Kant", "K530", "K530"},
            {"Knuth", "K530", "K530"},
            {"Ladd", "L300", "L300"},
            {"O'Hara", "O600", "O600"},
            {"Schmidt", "S530", "S253"},
            {"Czech", "C200", "C220"},
            {"Scott", "S300", "S230"},
            {"Szcz", "S000", "S200"},
            {"Ssss", "S000", "S200"},
            {"Cks", "C000", "C200"},
            {"Dt", "D000", "D300"},
            {"Bbbbbbbbbbb", "B000", "B100"},
            {"Fvpb", "F000", "F100"},
            {"Mn", "M000", "M500"},
            {"Rr", "R000", "R600"},
            {"Lr", "L600", "L600"},
            {"Gjkqsxz", "G000", "G200"},
            {"Marks", "M620", "M620"},
            {"Marx", "M620", "M620"},
            {"Marsha", "M620", "M620"},
            {"Marcia", "M620", "M620"},
            {"Ackerman", "A265", "A265"},
            {"Xavier", "X160", "X160"},
            {"Tymczak ", "T522", "T522"},
            {"I love rock and roll music.", "I416", "I416"},
            {"abcdefghijklmnopqrstuvwxyz", "a123", "a123"},
            {"Bcdfgjklmnqrstvxz", "B231", "B231"},
        });
    }

    @Test
    public void anArgumentIsReadAsItsText() {
        assertCells(new String[][] {
            {"SELECT SOUNDEX(NULL)", "null"},
            {"SELECT SOUNDEX_P123(NULL)", "null"},
            {"SELECT SOUNDEX(123)", "1000"},
            {"SELECT SOUNDEX_P123(123)", "1000"},
            {"SELECT SOUNDEX_P123(TRUE)", "t600"},
            {"SELECT SOUNDEX(TO_BOOLEAN('yes'))", "t600"},
            {"SELECT SOUNDEX_P123(1.5)", "1000"},
            {"SELECT SOUNDEX(1.50)", "1000"},
            {"SELECT SOUNDEX_P123(-7)", "-000"},
            {"SELECT SOUNDEX_P123(0.5::FLOAT)", "0000"},
            {"SELECT SOUNDEX_P123(1e3)", "1000"},
            {"SELECT SOUNDEX(1e100)", "1000"},
            {"SELECT SOUNDEX_P123(TO_DATE('2020-01-01'))", "2000"},
            {"SELECT SOUNDEX_P123(TO_TIME('10:00:00'))", "1000"},
            {"SELECT SOUNDEX_P123(PARSE_JSON('{}'))", "{000"},
            {"SELECT SOUNDEX_P123(PARSE_JSON('\"abc\"'))", "a120"},
            {"SELECT SOUNDEX_P123(PARSE_JSON('[1]'))", "[000"},
            {"SELECT SOUNDEX(PARSE_JSON('[1]'))", "[000"},
            {"SELECT SOUNDEX_P123(PARSE_JSON('12'))", "1000"},
            {"SELECT SOUNDEX_P123(PARSE_JSON('1.5e3'))", "1000"},
            {"SELECT SOUNDEX_P123(PARSE_JSON('true'))", "t600"},
            {"SELECT SOUNDEX_P123(PARSE_JSON('null'))", "null"},
            {"SELECT SOUNDEX_P123(TO_VARIANT('robert'))", "r163"},
            {"SELECT SOUNDEX(TO_VARIANT('robert'))", "r163"},
            {"SELECT SOUNDEX_P123(TO_VARIANT(TO_BINARY('6162', 'HEX')))", "6000"},
            {"SELECT SOUNDEX_P123(1::VARIANT::VARCHAR)", "1000"},
            {"SELECT SOUNDEX_P123(COLLATE('abc', 'en-ci'))", "a120"},
            {"SELECT COLLATION(SOUNDEX_P123(COLLATE('abc', 'en-ci')))", "null"},
            {"SELECT soundex_p123('ab')", "a100"},
            {"SELECT \"SOUNDEX_P123\"('ab')", "a100"},
            {"SELECT SOUNDEX_P123(x) FROM (SELECT 'Pfister' x UNION ALL SELECT NULL UNION ALL SELECT 'Lloyd') ORDER BY 1",
                "L430|P123|null"},
        });
    }

    @Test
    public void bothAreSevenWideAndRefuseWhatIsNoText() {
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(SOUNDEX_P123('abc'))", "VARCHAR(7)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(SOUNDEX_P123(NULL))", "VARCHAR(7)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(SOUNDEX_P123(TRUE))", "VARCHAR(7)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(SOUNDEX(1))", "VARCHAR(7)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(SOUNDEX_P123(v)) FROM (SELECT 'abc'::VARCHAR(2) v)", "VARCHAR(7)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(SOUNDEX(v)) FROM (SELECT 'abc'::VARCHAR v)", "VARCHAR(7)[LOB]"},
        });
        assertRefused("SELECT SOUNDEX_P123(TO_BINARY('6162', 'HEX'))",
            "Invalid argument types for function 'SOUNDEX_P123': (BINARY(67108864))");
        assertRefused("SELECT SOUNDEX_P123(ARRAY_CONSTRUCT('a'))",
            "Invalid argument types for function 'SOUNDEX_P123': (ARRAY)");
        assertRefused("SELECT SOUNDEX_P123(ARRAY_CONSTRUCT())",
            "Invalid argument types for function 'SOUNDEX_P123': (ARRAY)");
        assertRefused("SELECT SOUNDEX_P123(OBJECT_CONSTRUCT('a', 1))",
            "Invalid argument types for function 'SOUNDEX_P123': (OBJECT)");
        assertRefused("SELECT SOUNDEX_P123([1,2,3]::VECTOR(FLOAT,3))",
            "Invalid argument types for function 'SOUNDEX_P123': (VECTOR(FLOAT, 3))");
        assertRefused("SELECT SOUNDEX_P123()",
            "not enough arguments for function [SOUNDEX_P123()], expected 1, got 0");
        assertRefused("SELECT SOUNDEX_P123('a','b')",
            "too many arguments for function [SOUNDEX_P123('a', 'b')] expected 1, got 2");
        assertRefused("SELECT SOUNDEX_P123('robert', NULL)",
            "too many arguments for function [SOUNDEX_P123('robert', null)] expected 1, got 2");
    }
}
