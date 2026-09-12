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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * INFORMATION_SCHEMA.COLUMNS leaves both lengths of a BINARY column NULL — a declared width or not, a
 * table, a view or a CTAS column alike — while a string reports its length and four octets a character.
 * Every cell is live-verified.
 */
public class BinaryColumnLengthTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    /** A table's BINARY columns report no length; its strings report theirs. */
    @Test
    public void aTableColumnReportsNoBinaryLength() {
        engine.execute("CREATE OR REPLACE TABLE tb (b BINARY(8), b5 BINARY(5), bb BINARY, vb VARBINARY, v5 VARCHAR(5), vv VARCHAR, bo BOOLEAN)");
        assertEquals("B, BINARY, null, null, null | B5, BINARY, null, null, null | BB, BINARY, null, null, null | VB, BINARY, null, null, null | V5, TEXT, 5, 20, null | VV, TEXT, 16777216, 67108864, null | BO, BOOLEAN, null, null, null",
            rows("SELECT column_name, data_type, character_maximum_length, character_octet_length, numeric_precision FROM information_schema.columns WHERE table_name = 'TB' ORDER BY ordinal_position"));
    }

    /** A view's and a CTAS's BINARY columns report none either. */
    @Test
    public void aDerivedColumnReportsNoBinaryLength() {
        engine.execute("CREATE OR REPLACE TABLE tb (b BINARY(8), b5 BINARY(5), bb BINARY)");
        engine.execute("CREATE OR REPLACE VIEW vw AS SELECT b, NULLIF(b5, b5) AS nb, X'0102' AS lit, TO_BINARY('00') AS tb2 FROM tb");
        assertEquals("B, BINARY, null, null | NB, BINARY, null, null | LIT, BINARY, null, null | TB2, BINARY, null, null",
            rows("SELECT column_name, data_type, character_maximum_length, character_octet_length FROM information_schema.columns WHERE table_name = 'VW' ORDER BY ordinal_position"));
        engine.execute("CREATE OR REPLACE VIEW va AS SELECT MIN(b) AS mb FROM tb");
        assertEquals("MB, BINARY, null, null",
            rows("SELECT column_name, data_type, character_maximum_length, character_octet_length FROM information_schema.columns WHERE table_name = 'VA' ORDER BY ordinal_position"));
        engine.execute("CREATE OR REPLACE TABLE ct AS SELECT b, X'0102' AS lit FROM tb");
        assertEquals("B, BINARY, null, null | LIT, BINARY, null, null",
            rows("SELECT column_name, data_type, character_maximum_length, character_octet_length FROM information_schema.columns WHERE table_name = 'CT' ORDER BY ordinal_position"));
    }
}
