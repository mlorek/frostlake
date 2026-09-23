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
 * A scripting declaration that writes BINARY with no width binds at the type's 64MB maximum, where a
 * COLUMN declared the same way is BINARY(8388608). The width that was WRITTEN is what separates them:
 * an explicit BINARY(8388608) keeps its own.
 */
public class UnsizedBinaryDeclarationTest extends BaseDatabaseTest {

    /** What a block answers. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** What SYSTEM$TYPEOF reads for the variable bound into a statement. */
    private String boundType(final String declaration) {
        return answer("DECLARE s " + declaration + "; t VARCHAR; "
            + "BEGIN t := (SELECT SYSTEM$TYPEOF(:s)); RETURN t; END;");
    }

    /** Unsized, the declaration binds at the maximum — with a value and with NULL alike. */
    @Test
    public void anUnsizedBinaryBindsAtTheMaximum() {
        assertEquals("BINARY(67108864)[LOB]", boundType("BINARY DEFAULT TO_BINARY('ABCD', 'HEX')"));
        assertEquals("BINARY(67108864)[LOB]", boundType("BINARY"));
    }

    /** A width that was written is kept, the 8MB column default included. */
    @Test
    public void aWrittenWidthIsKept() {
        assertEquals("BINARY(8388608)[LOB]", boundType("BINARY(8388608) DEFAULT TO_BINARY('ABCD', 'HEX')"));
        assertEquals("BINARY(10)[LOB]", boundType("BINARY(10) DEFAULT TO_BINARY('ABCD', 'HEX')"));
    }
}
