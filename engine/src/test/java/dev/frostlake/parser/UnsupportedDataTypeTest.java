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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An unknown word where a data type belongs is named as an unsupported TYPE, not refused as syntax
 * (live-verified across the family). The sentence is unpositioned, keeps the LAST dotted part of
 * the written name unquoted and upper-cased, and echoes the written parameters joined by ", ".
 *
 * <p>★ QUOTING DOES NOT PROTECT A REAL TYPE NAME: {@code (c "VARCHAR")} is unsupported 'VARCHAR',
 * because the slot resolves by NAME after the parse, not by keyword.
 *
 * <p>★ THE SENTENCE FIRES ONLY WHERE A TYPE BELONGS: a KEYWORD in the slot stays a syntax error,
 * and so does a word AFTER a complete type — {@code (c INT badword)} names 'badword'.
 */
public class UnsupportedDataTypeTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return e.getMessage();
    }

    @Test
    public void createTableNamesTheUnknownTypeWithItsParameters() {
        assertEquals("SQL compilation error:\nUnsupported data type 'NOSUCHTYPE(0)'.",
            refusal("CREATE TABLE ut1 (c NOSUCHTYPE(0))"));
        assertEquals("SQL compilation error:\nUnsupported data type 'NOSUCHTYPE(5, 2)'.",
            refusal("CREATE TABLE ut2 (c NOSUCHTYPE(5,2))"));
        assertEquals("SQL compilation error:\nUnsupported data type 'NOSUCHTYPE'.",
            refusal("CREATE TABLE ut3 (c NOSUCHTYPE)"));
        assertEquals("SQL compilation error:\nUnsupported data type 'NOSUCHTYPE(3)'.",
            refusal("CREATE TABLE ut8 (a INT, c NOSUCHTYPE(3))"));
    }

    @Test
    public void theWrittenNameIsUpperCasedAndKeepsItsLastDottedPart() {
        assertEquals("SQL compilation error:\nUnsupported data type 'NOSUCHTYPE'.",
            refusal("CREATE TABLE ut4 (c nosuchtype)"));
        assertEquals("SQL compilation error:\nUnsupported data type 'MYTYPE'.",
            refusal("CREATE TABLE ut5 (c mydb.mysch.mytype)"));
    }

    @Test
    public void aQuotedRealTypeNameIsUnknown() {
        assertEquals("SQL compilation error:\nUnsupported data type 'VARCHAR'.",
            refusal("CREATE TABLE ut6 (c \"VARCHAR\")"));
    }

    @Test
    public void bothCastSpellingsShareTheSentence() {
        assertEquals("SQL compilation error:\nUnsupported data type 'NOSUCHTYPE'.",
            refusal("SELECT 1::NOSUCHTYPE"));
        assertEquals("SQL compilation error:\nUnsupported data type 'NOSUCHTYPE(5)'.",
            refusal("SELECT CAST(1 AS NOSUCHTYPE(5))"));
    }

    @Test
    public void alterTableAddColumnSharesTheSentence() {
        engine.execute("CREATE TABLE ut10 (i INT)");
        assertEquals("SQL compilation error:\nUnsupported data type 'NOSUCHTYPE'.",
            refusal("ALTER TABLE ut10 ADD COLUMN c NOSUCHTYPE"));
    }

    @Test
    public void aKeywordInTheTypeSlotStaysASyntaxError() {
        final String message = refusal("CREATE TABLE ut7 (c SELECT)");
        assertTrue(message.contains("syntax error line 1 at position 20 unexpected 'SELECT'."),
            message);
        assertFalse(message.contains("Unsupported data type"), message);
    }

    @Test
    public void aWordAfterACompleteTypeStaysASyntaxError() {
        final String message = refusal("CREATE TABLE ut9 (c INT badword)");
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 24 unexpected 'badword'.",
            message);
    }
}
