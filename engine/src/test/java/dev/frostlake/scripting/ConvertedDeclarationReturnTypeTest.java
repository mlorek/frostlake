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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A bare RETURN of a typed name reports the type its value was CONVERTED with, not the type the
 * declaration wrote, and hands the value back as that conversion produced it.
 *
 * <p>★ A BOOLEAN into an exact number converts as {@code TRUE::NUMBER} does, which is NUMBER(2,0)
 * whatever width the declaration spells — so {@code x NUMBER(10,2) DEFAULT TRUE} returns {@code 1},
 * never {@code 1.00}, and the declared scale never enters.
 *
 * <p>★ A NUMBER into a TIMESTAMP flavour declares the number's own scale, so {@code LET x TIMESTAMP
 * := 5} is TIMESTAMP_NTZ(0) where a text initialiser keeps the default nine.
 *
 * <p>★ THE VARIABLE ITSELF KEEPS ITS DECLARED TYPE. Only the bare RETURN reports the conversion:
 * bound as {@code :x} the name is its declaration, and inside an expression the declaration wins too.
 *
 * <p>Every cell is live-verified.
 */
public class ConvertedDeclarationReturnTypeTest extends BaseDatabaseTest {

    /** The block's result column type, read off RESULT_SCAN the way a caller sees it. */
    private String returnedType(final String block) {
        engine.executeQuery("EXECUTE IMMEDIATE $$ " + block + " $$");
        engine.execute("CREATE OR REPLACE TABLE cdrt_t AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        final ResultSet described = engine.executeQuery("DESC TABLE cdrt_t");
        return String.valueOf(described.getRows().get(0).getValue(1));
    }

    /** The block's returned value as text. */
    private String returnedValue(final String block) {
        final ResultSet rs = engine.executeQuery("EXECUTE IMMEDIATE $$ " + block + " $$");
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void aBooleanIntoAnExactNumberReportsTheConversionsOwnType() {
        assertEquals("NUMBER(2,0)",
            returnedType("DECLARE x NUMBER DEFAULT TRUE; BEGIN RETURN x; END;"));
        assertEquals("NUMBER(2,0)",
            returnedType("DECLARE x NUMBER(10,2) DEFAULT TRUE; BEGIN RETURN x; END;"));
        assertEquals("NUMBER(2,0)",
            returnedType("DECLARE x NUMBER DEFAULT FALSE; BEGIN RETURN x; END;"));
    }

    @Test
    public void theValueComesBackAsTheConversionProducedIt() {
        assertEquals("1", returnedValue("DECLARE x NUMBER DEFAULT TRUE; BEGIN RETURN x; END;"));
        // The declared scale never enters: 1, not 1.00.
        assertEquals("1", returnedValue("DECLARE x NUMBER(10,2) DEFAULT TRUE; BEGIN RETURN x; END;"));
        assertEquals("0", returnedValue("DECLARE x NUMBER DEFAULT FALSE; BEGIN RETURN x; END;"));
    }

    @Test
    public void anAssignmentConvertsExactlyAsADeclarationDoes() {
        assertEquals("NUMBER(2,0)",
            returnedType("DECLARE x NUMBER; BEGIN x := TRUE; RETURN x; END;"));
        assertEquals("NUMBER(2,0)",
            returnedType("BEGIN LET x := 1; x := TRUE; RETURN x; END;"));
        assertEquals("1", returnedValue("DECLARE x NUMBER; BEGIN x := TRUE; RETURN x; END;"));
    }

    @Test
    public void aNumberIntoATimestampDeclaresItsOwnScale() {
        assertEquals("TIMESTAMP_NTZ(0)",
            returnedType("BEGIN LET x TIMESTAMP := 5; RETURN x; END;"));
    }

    /** A conversion that changes nothing leaves the declaration alone. */
    @Test
    public void aSourceTheTargetTakesKeepsTheDeclaredType() {
        assertEquals("NUMBER(38,0)",
            returnedType("DECLARE x NUMBER DEFAULT '12'; BEGIN RETURN x; END;"));
        assertEquals("NUMBER(38,0)",
            returnedType("DECLARE x NUMBER DEFAULT 5; BEGIN RETURN x; END;"));
        assertEquals("NUMBER(10,2)",
            returnedType("DECLARE x NUMBER(10,2) DEFAULT 5; BEGIN RETURN x; END;"));
        assertEquals("5.00", returnedValue("DECLARE x NUMBER(10,2) DEFAULT 5; BEGIN RETURN x; END;"));
    }

    /** Inside an expression the declaration wins, so the timestamp keeps its nine digits. */
    @Test
    public void insideAnExpressionTheDeclaredTypeStillWins() {
        assertEquals("TIMESTAMP_NTZ(9)",
            returnedType("BEGIN LET x TIMESTAMP := 5; RETURN x + INTERVAL '1 second'; END;"));
    }
}
