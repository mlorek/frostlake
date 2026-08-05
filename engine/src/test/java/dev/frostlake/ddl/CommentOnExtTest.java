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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests COMMENT ON for object types beyond the original set: SEQUENCE, FILE FORMAT, MASKING POLICY,
 * ROW ACCESS POLICY, PIPE.
 */
public class CommentOnExtTest extends BaseDatabaseTest {

    private Map<String, String> describe(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final Map<String, String> props = new HashMap<>();
        for (final Row row : rs.getRows()) {
            props.put(String.valueOf(row.getValue(0)), String.valueOf(row.getValue(1)));
        }
        return props;
    }

    @Test
    public void commentOnSequence() {
        engine.execute("CREATE SEQUENCE s1");
        engine.execute("COMMENT ON SEQUENCE s1 IS 'my seq'");
        // DESC SEQUENCE returns one columnar row (live shape); the comment sits in its own column.
        final ResultSet rs = engine.executeQuery("DESCRIBE SEQUENCE s1");
        assertEquals(1, rs.getRowCount());
        assertEquals("my seq", rs.getRows().get(0).getValue(rs.getColumnIndex("comment")));
    }

    @Test
    public void commentOnFileFormat() {
        engine.execute("CREATE FILE FORMAT ff TYPE = CSV");
        engine.execute("COMMENT ON FILE FORMAT ff IS 'fmt note'");
        // A file format's comment surfaces in SHOW FILE FORMATS, not in DESCRIBE: live-verified on a
        // real account, DESCRIBE FILE FORMAT lists only the FORMAT properties (TYPE,
        // RECORD_DELIMITER, FIELD_DELIMITER, …) with no COMMENT row, while SHOW carries a comment column.
        final ResultSet shown = engine.executeQuery("SHOW FILE FORMATS LIKE 'FF'");
        assertEquals(1, shown.getRowCount());
        assertEquals("fmt note", shown.getRows().get(0).getValue(shown.getColumnIndex("comment")));
        assertNull(describe("DESCRIBE FILE FORMAT ff").get("COMMENT"));
    }

    @Test
    public void commentOnMaskingAndRowAccessPolicy() {
        engine.execute("CREATE MASKING POLICY mp AS (v STRING) RETURNS STRING -> '***'");
        engine.execute("CREATE ROW ACCESS POLICY rap AS (v STRING) RETURNS BOOLEAN -> TRUE");
        // Applies without error (policies inherit setComment from SqlObject).
        engine.execute("COMMENT ON MASKING POLICY mp IS 'mask note'");
        engine.execute("COMMENT ON ROW ACCESS POLICY rap IS 'rap note'");
    }
}
