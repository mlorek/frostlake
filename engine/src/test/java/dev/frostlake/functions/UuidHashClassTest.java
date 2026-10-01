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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HASH keys a UUID apart from the same text: a bare UUID, the UUID wrapped in a VARIANT, and a container
 * holding it each hash differently from their string counterparts and from one another, while a text cast to
 * UUID hashes as the UUID does. Only which arguments hash alike is compared — the values themselves are
 * Snowflake's own algorithm. Every cell is live-verified.
 */
public class UuidHashClassTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE uhc_t (u UUID, s VARCHAR)");
        engine.execute("INSERT INTO uhc_t SELECT '8e6b1a90-3c0e-4a52-9c4e-0f5b2f7d1a11', '8e6b1a90-3c0e-4a52-9c4e-0f5b2f7d1a11'");
    }

    /** The one row's cells, joined by a comma. */
    private String cells(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < row.getValues().size(); i++) {
            out.append(i > 0 ? ", " : "").append(row.getValue(i));
        }
        return out.toString();
    }

    @Test
    public void aUuidHashesApartFromItsText() {
        assertEquals("false, false, false", cells("SELECT HASH(u) = HASH(s), HASH(u) = HASH(u::VARCHAR),"
            + " HASH(u, s) = HASH(s, u) FROM uhc_t").toLowerCase());
        assertEquals("true", cells("SELECT HASH(u) = HASH(s::UUID) FROM uhc_t").toLowerCase());
    }

    @Test
    public void aUuidMemberHashesApartFromAStringMember() {
        assertEquals("false, false, false, false", cells("SELECT HASH(TO_VARIANT(u)) = HASH(TO_VARIANT(s)),"
            + " HASH(u) = HASH(TO_VARIANT(u)), HASH(ARRAY_CONSTRUCT(u)) = HASH(ARRAY_CONSTRUCT(s)),"
            + " HASH(TO_VARIANT(u)) = HASH(PARSE_JSON('\"8e6b1a90-3c0e-4a52-9c4e-0f5b2f7d1a11\"')) FROM uhc_t")
            .toLowerCase());
    }
}
