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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A VARIANT cast to a sized VARCHAR is width-checked on the text it converts to, except for a number or
 * boolean constant wrapped straight into the VARIANT, which converts unchecked, as live folds it: a literal
 * under casts and signs, TRUE AND TRUE, TO_NUMBER('123'), and the branch an IFF over a constant condition
 * takes. A computed wrap (SQRT(2), 1/3), a cast that adds decimals the constant lacks (123::NUMBER(10,2)),
 * COALESCE over a wrap and a JSON-parsed number are checked. Every cell is live-verified.
 */
public class VariantWrapWidthCheckTest extends BaseDatabaseTest {

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

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    @Test
    public void aConstantWrapConvertsUncheckedAndAComputedOneIsChecked() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P460_DB");
            engine.execute("CREATE OR REPLACE VIEW P460_DB.PUBLIC.V1 AS SELECT TO_VARIANT(123) AS v");
            assertRefused("SELECT TO_VARIANT(SQRT(2))::VARCHAR(3)",
                "String '1.414213562' is too long and would be truncated");
            assertRefused("SELECT TO_VARIANT(123::NUMBER(10,2))::VARCHAR(2)",
                "String '123' is too long and would be truncated");
            assertEquals("123",
                rows("SELECT TO_VARIANT(123)::VARCHAR(1)"));
            assertEquals("1.5",
                rows("SELECT TO_VARIANT(1.5::FLOAT)::VARCHAR(1)"));
            assertEquals("123",
                rows("SELECT IFF(TRUE, TO_VARIANT(123), NULL)::VARCHAR(1)"));
            assertRefused("SELECT COALESCE(TO_VARIANT(123), NULL)::VARCHAR(1)",
                "String '123' is too long and would be truncated");
            assertEquals("123",
                rows("SELECT TO_VARIANT(123::INT)::VARCHAR(1)"));
            assertEquals("123",
                rows("SELECT TO_VARIANT(123::NUMBER(10,0))::VARCHAR(1)"));
            assertEquals("1.5",
                rows("SELECT TO_VARIANT(1.5::NUMBER(3,1))::VARCHAR(1)"));
            assertEquals("2.0",
                rows("SELECT TO_VARIANT(2::FLOAT)::VARCHAR(1)"));
            assertRefused("SELECT TO_VARIANT(1/3)::VARCHAR(1)",
                "String '0.333333' is too long and would be truncated");
            assertEquals("5",
                rows("SELECT TO_VARIANT(ABS(-5))::VARCHAR(1)"));
            assertEquals("-5",
                rows("SELECT TO_VARIANT(-5)::VARCHAR(1)"));
            assertEquals("3",
                rows("SELECT TO_VARIANT(1 + 2)::VARCHAR(1)"));
            assertEquals("true",
                rows("SELECT TO_VARIANT(TRUE AND TRUE)::VARCHAR(1)"));
            assertEquals("true",
                rows("SELECT TO_VARIANT(NOT FALSE)::VARCHAR(1)"));
            assertRefused("SELECT PARSE_JSON('12345')::VARCHAR(2)",
                "String '12345' is too long and would be truncated");
            assertEquals("123",
                rows("SELECT TO_VARIANT(TO_VARIANT(123))::VARCHAR(1)"));
            assertEquals("123",
                rows("SELECT IFF(FALSE, NULL, TO_VARIANT(123))::VARCHAR(1)"));
            assertRefused("SELECT (SELECT TO_VARIANT(SQRT(2)))::VARCHAR(3)",
                "String '1.414213562' is too long and would be truncated");
            assertEquals("123.45",
                rows("SELECT TO_VARIANT(123.45)::VARCHAR(2)"));
            assertEquals("100000",
                rows("SELECT TO_VARIANT(1e5)::VARCHAR(2)"));
            assertEquals("12",
                rows("SELECT TO_VARIANT(12::NUMBER(10,2))::VARCHAR(2)"));
            assertEquals("123",
                rows("SELECT TO_VARIANT(TO_NUMBER('123'))::VARCHAR(1)"));
            assertEquals("123",
                rows("SELECT TO_VARIANT(CAST(123 AS NUMBER(5,0)))::VARCHAR(1)"));
            assertRefused("SELECT TO_VARIANT(123)::VARCHAR(2)::VARCHAR(1)",
                "String '123' is too long and would be truncated");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P460_DB");
        }
    }
}
