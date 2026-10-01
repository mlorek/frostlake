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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An empty text literal is one character wide: {@code ''} is VARCHAR(1), in SYSTEM$TYPEOF, in an argument-type
 * refusal and in the width of what is computed from it. Live-verified.
 */
public class EmptyStringLiteralTypeTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    @Test
    public void anEmptyTextIsOneCharacterWide() {
        assertEquals("VARCHAR(1)[LOB]", scalar("SELECT SYSTEM$TYPEOF('') AS r"));
        assertEquals("VARCHAR(1)[LOB]", scalar("SELECT SYSTEM$TYPEOF($$$$) AS r"));
        assertEquals("VARCHAR(2)[LOB]", scalar("SELECT SYSTEM$TYPEOF('' || '') AS r"));
        assertEquals("VARCHAR(2)[LOB]", scalar("SELECT SYSTEM$TYPEOF('a' || '') AS r"));
        assertEquals("VARCHAR(2)[LOB]", scalar("SELECT SYSTEM$TYPEOF(CONCAT('', '')) AS r"));
        assertEquals("VARCHAR(3)[LOB]", scalar("SELECT SYSTEM$TYPEOF(UPPER('')) AS r"));
        assertEquals("SQL compilation error: error line 1 at position 25\nInvalid argument types for function '||': "
            + "(ARRAY, VARCHAR(1))", refusal("SELECT ARRAY_CONSTRUCT() || '' AS r"));
        assertEquals("SQL compilation error: error line 1 at position 19\nInvalid argument types for function '||': "
            + "(BOOLEAN, VARCHAR(1))", refusal("SELECT (1 IS NULL) || '' AS r"));
    }

    @Test
    public void aTableBuiltFromOneDeclaresTheWidth() {
        engine.execute("CREATE OR REPLACE TABLE esl AS SELECT '' AS c, '' || '' AS c2");
        assertEquals("VARCHAR(1)", describeCell("esl", "C", "type"));
        assertEquals("VARCHAR(2)", describeCell("esl", "C2", "type"));
    }
}
