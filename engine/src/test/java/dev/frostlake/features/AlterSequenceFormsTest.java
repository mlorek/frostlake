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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The forms ALTER SEQUENCE takes (live-verified): a comment set and unset, the step with or without SET and with
 * BY, an equals sign or neither, NOORDER, and several properties in one statement with or without commas. A
 * sequence that serves its values out of order cannot be put back in order; a property written twice, START, a
 * step of zero and UNSET of anything but the comment are refused, and a refused statement changes nothing.
 */
public class AlterSequenceFormsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE SEQUENCE asf_s START = 10");
    }

    private String shown(final String name, final String column) {
        final ResultSet rs = engine.executeQuery("SHOW SEQUENCES LIKE '" + name + "'");
        final Object value = rs.getRows().get(0).getValue(rs.getColumnIndex(column));
        return value == null ? "" : String.valueOf(value);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void theCommentIsSetAndUnset() {
        engine.execute("ALTER SEQUENCE asf_s SET COMMENT = 'c'");
        assertEquals("c", shown("ASF_S", "comment"));
        engine.execute("ALTER SEQUENCE asf_s UNSET COMMENT");
        assertEquals("", shown("ASF_S", "comment"));
        engine.execute("ALTER SEQUENCE asf_s COMMENT = 'd'");
        assertEquals("d", shown("ASF_S", "comment"));
        engine.execute("ALTER SEQUENCE asf_s SET COMMENT = NULL");
        assertEquals("", shown("ASF_S", "comment"));
    }

    @Test
    public void theStepIsSetWithOrWithoutSet() {
        engine.execute("ALTER SEQUENCE asf_s INCREMENT BY 5");
        assertEquals("5", shown("ASF_S", "interval"));
        engine.execute("ALTER SEQUENCE asf_s INCREMENT = 4");
        assertEquals("4", shown("ASF_S", "interval"));
        engine.execute("ALTER SEQUENCE asf_s INCREMENT 6");
        assertEquals("6", shown("ASF_S", "interval"));
        engine.execute("ALTER SEQUENCE asf_s SET INCREMENT BY 7");
        assertEquals("7", shown("ASF_S", "interval"));
        engine.execute("ALTER SEQUENCE asf_s SET INCREMENT 8");
        assertEquals("8", shown("ASF_S", "interval"));
        engine.execute("ALTER SEQUENCE asf_s SET INCREMENT = 3 COMMENT = 'e'");
        assertEquals("3", shown("ASF_S", "interval"));
        assertEquals("e", shown("ASF_S", "comment"));
        engine.execute("ALTER SEQUENCE asf_s SET INCREMENT = 2, COMMENT = 'f'");
        assertEquals("2", shown("ASF_S", "interval"));
        assertEquals("f", shown("ASF_S", "comment"));
        assertEquals("10", shown("ASF_S", "next_value"));
    }

    @Test
    public void anOrderedSequenceMayOnlyLoseItsOrder() {
        engine.execute("CREATE SEQUENCE asf_o START = 1 INCREMENT = 1 ORDER");
        assertEquals("Y", shown("ASF_O", "ordered"));
        engine.execute("ALTER SEQUENCE asf_o SET NOORDER");
        assertEquals("N", shown("ASF_O", "ordered"));
        assertEquals("SQL compilation error:\nInvalid operation ALTER NOORDER SEQUENCE TO ORDER",
            refusal("ALTER SEQUENCE asf_o SET ORDER"));
        assertEquals("SQL compilation error:\nInvalid operation ALTER NOORDER SEQUENCE TO ORDER",
            refusal("ALTER SEQUENCE asf_s ORDER"));
        engine.execute("ALTER SEQUENCE asf_s NOORDER");
        engine.execute("ALTER SEQUENCE asf_s SET NOORDER COMMENT = 'g'");
        assertEquals("g", shown("ASF_S", "comment"));
    }

    @Test
    public void whatIsNoPropertyIsRefusedAndChangesNothing() {
        assertEquals("SQL compilation error:\nduplicate property 'SEQUENCE_INCREMENT';",
            refusal("ALTER SEQUENCE asf_s SET INCREMENT = 3 INCREMENT = 4"));
        assertEquals("SQL compilation error:\nduplicate property 'COMMENT';",
            refusal("ALTER SEQUENCE asf_s SET COMMENT = 'a' COMMENT = 'b'"));
        assertEquals("SQL compilation error:\nduplicate property 'COMMENT';",
            refusal("ALTER SEQUENCE asf_s UNSET COMMENT, COMMENT"));
        assertEquals("SQL compilation error:\ninvalid property 'SEQUENCE_START' for 'SEQUENCE'",
            refusal("ALTER SEQUENCE asf_s SET START = 5"));
        assertEquals("SQL compilation error:\ninvalid value '0' for property 'SEQUENCE_INCREMENT'",
            refusal("ALTER SEQUENCE asf_s SET INCREMENT = 0"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 27 unexpected 'INCREMENT'.",
            refusal("ALTER SEQUENCE asf_s UNSET INCREMENT"));
        assertEquals("1", shown("ASF_S", "interval"));
        assertEquals("", shown("ASF_S", "comment"));
    }

    @Test
    public void aMissingSequenceIsForgivenOnlyWhenAsked() {
        engine.execute("ALTER SEQUENCE IF EXISTS asf_nosuch SET COMMENT = 'x'");
        final String missing = refusal("ALTER SEQUENCE asf_nosuch SET COMMENT = 'x'");
        assertTrue(missing.startsWith("SQL compilation error:\nSequence 'TEST_DB.TEST_SCHEMA.ASF_NOSUCH' does not exist"
            + " or not authorized."), missing);
    }
}
