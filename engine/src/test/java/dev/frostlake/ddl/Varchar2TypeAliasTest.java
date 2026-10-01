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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code VARCHAR2} is a VARCHAR synonym wherever a type may be written — a column definition, a cast,
 * an added column, and the signature and return of a routine. Bare it is the widest text; with a
 * length it is that length. Like its sibling {@code NVARCHAR2} it takes no {@code VARYING}, and it
 * stays usable as an ordinary identifier.
 */
public class Varchar2TypeAliasTest extends BaseDatabaseTest {

    /** The first column of the first row, as text. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** A column declared with it is a TEXT of the width asked for, or the widest when bare. */
    @Test
    public void aColumnDeclaredWithItIsText() {
        engine.execute("CREATE OR REPLACE TABLE tv (c VARCHAR2(10), b VARCHAR2)");
        final ResultSet rs = engine.executeQuery("SELECT column_name, data_type, "
            + "character_maximum_length FROM information_schema.columns "
            + "WHERE table_name = 'TV' ORDER BY column_name");
        final StringBuilder declared = new StringBuilder();
        while (rs.next()) {
            declared.append(rs.getValue(0)).append(" ").append(rs.getValue(1))
                .append("(").append(rs.getValue(2)).append(") ");
        }
        assertEquals("B TEXT(16777216) C TEXT(10) ", declared.toString());
    }

    /** A cast to it is a VARCHAR — of the declared length, or lengthless when bare. */
    @Test
    public void aCastToItIsAVarchar() {
        assertEquals("VARCHAR(5)[LOB]", answer("SELECT SYSTEM$TYPEOF('ab'::VARCHAR2(5))"));
        assertEquals("VARCHAR[LOB]", answer("SELECT SYSTEM$TYPEOF(NULL::VARCHAR2)"),
            "bare, it carries no width at all");
        assertEquals("VARCHAR(7)[LOB]", answer("SELECT SYSTEM$TYPEOF(CAST('ab' AS VARCHAR2(7)))"));
        assertEquals("ab", answer("SELECT 'ab'::VARCHAR2"));
    }

    /** An added column takes it too. */
    @Test
    public void anAddedColumnTakesIt() {
        engine.execute("CREATE OR REPLACE TABLE tva (c INT)");
        engine.execute("ALTER TABLE tva ADD COLUMN d VARCHAR2(3)");
        assertEquals("3", answer("SELECT character_maximum_length FROM information_schema.columns "
            + "WHERE table_name = 'TVA' AND column_name = 'D'"));
    }

    /** A function and a procedure may be written with it, in the signature and in the return. */
    @Test
    public void aRoutineSignatureTakesIt() {
        engine.execute("CREATE OR REPLACE FUNCTION f2(p VARCHAR2(4)) RETURNS VARCHAR2(9) "
            + "AS $$ p || 'x' $$");
        assertEquals("abx", answer("SELECT f2('ab')"));
        engine.execute("CREATE OR REPLACE PROCEDURE p2(p VARCHAR2) RETURNS VARCHAR2 "
            + "LANGUAGE SQL AS $$ BEGIN RETURN p; END; $$");
        assertEquals("zz", answer("CALL p2('zz')"));
    }

    /** VARYING follows only the fixed-length spellings, so it is a syntax error after this one. */
    @Test
    public void itTakesNoVarying() {
        assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE OR REPLACE TABLE tvv (c VARCHAR2 VARYING)");
            }
        });
    }

    /** It is no reserved word: a column may be named it. */
    @Test
    public void itStaysUsableAsAName() {
        engine.execute("CREATE OR REPLACE TABLE tvn (varchar2 INT)");
        engine.execute("INSERT INTO tvn VALUES (7)");
        assertEquals("7", answer("SELECT varchar2 FROM tvn"));
    }
}
