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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A table, a view, a materialized view, a dynamic table and a stream share one name space in a schema: a
 * create over a name another of them holds is refused naming the holder's kind, {@code Object 'KT' already
 * exists as TABLE}, and neither OR REPLACE nor IF NOT EXISTS gets past it. A TEMPORARY table is the
 * exception — it may take a view's, a materialized view's or a dynamic table's name, though not a stream's,
 * and a TEMPORARY view may take a table's — and it then shadows the object it names until it is dropped.
 * A sequence keeps its own name space. Every cell is live-verified.
 */
public class UdfOverloadChoiceTest extends BaseDatabaseTest {

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
    public void theArgumentsOwnOrderPicksTheOverload() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P487_DB");
            engine.execute("CREATE OR REPLACE FUNCTION ov(p VARCHAR) RETURNS VARCHAR AS '''v'''");
            engine.execute("CREATE OR REPLACE FUNCTION ov(p NUMBER) RETURNS VARCHAR AS '''n'''");
            engine.execute("CREATE OR REPLACE FUNCTION onf(p NUMBER) RETURNS VARCHAR AS '''n'''");
            engine.execute("CREATE OR REPLACE FUNCTION onf(p FLOAT) RETURNS VARCHAR AS '''f'''");
            engine.execute("CREATE OR REPLACE FUNCTION ovd(p VARCHAR) RETURNS VARCHAR AS '''v'''");
            engine.execute("CREATE OR REPLACE FUNCTION ovd(p DATE) RETURNS VARCHAR AS '''d'''");
            engine.execute("CREATE OR REPLACE FUNCTION ova(p VARIANT) RETURNS VARCHAR AS '''v'''");
            engine.execute("CREATE OR REPLACE FUNCTION ova(p ARRAY) RETURNS VARCHAR AS '''a'''");
            engine.execute("CREATE OR REPLACE FUNCTION ovo(p VARIANT) RETURNS VARCHAR AS '''v'''");
            engine.execute("CREATE OR REPLACE FUNCTION ovo(p OBJECT) RETURNS VARCHAR AS '''o'''");
            engine.execute("CREATE OR REPLACE FUNCTION oao(p ARRAY) RETURNS VARCHAR AS '''a'''");
            engine.execute("CREATE OR REPLACE FUNCTION oao(p OBJECT) RETURNS VARCHAR AS '''o'''");
            assertEquals("v",
                rows("SELECT ov(1.5::FLOAT)"));
            assertEquals("n",
                rows("SELECT ov(1)"));
            assertEquals("v",
                rows("SELECT ov('1')"));
            assertEquals("v",
                rows("SELECT ov(TRUE)"));
            assertEquals("v",
                rows("SELECT ov('2024-01-01'::DATE)"));
            assertEquals("v",
                rows("SELECT ov(PARSE_JSON('1'))"));
            assertEquals("n",
                rows("SELECT onf(1)"));
            assertEquals("n",
                rows("SELECT onf(1.5)"));
            assertEquals("f",
                rows("SELECT onf(1.5::FLOAT)"));
            assertEquals("f",
                rows("SELECT onf('1')"));
            assertEquals("v",
                rows("SELECT ovd('2024-01-01')"));
            assertEquals("d",
                rows("SELECT ovd('2024-01-01'::DATE)"));
            assertEquals("v",
                rows("SELECT ova(PARSE_JSON('[1]'))"));
            assertEquals("v",
                rows("SELECT ova(PARSE_JSON('1'))"));
            assertEquals("v",
                rows("SELECT ovo(PARSE_JSON('{\"a\":1}'))"));
            assertEquals("v",
                rows("SELECT ovo(PARSE_JSON('1'))"));
            assertEquals("a",
                rows("SELECT oao(PARSE_JSON('[1]'))"));
            assertEquals("a",
                rows("SELECT oao(ARRAY_CONSTRUCT(1))"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P487_DB");
        }
    }

    @Test
    public void theSameOrderOverTheOtherPairs() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P487B_DB");
            engine.execute("CREATE OR REPLACE FUNCTION pvf(p VARCHAR) RETURNS VARCHAR AS '''V'''");
            engine.execute("CREATE OR REPLACE FUNCTION pvf(p FLOAT) RETURNS VARCHAR AS '''F'''");
            engine.execute("CREATE OR REPLACE FUNCTION pvb(p VARCHAR) RETURNS VARCHAR AS '''V'''");
            engine.execute("CREATE OR REPLACE FUNCTION pvb(p BOOLEAN) RETURNS VARCHAR AS '''B'''");
            engine.execute("CREATE OR REPLACE FUNCTION pvt(p VARCHAR) RETURNS VARCHAR AS '''V'''");
            engine.execute("CREATE OR REPLACE FUNCTION pvt(p TIMESTAMP_NTZ) RETURNS VARCHAR AS '''T'''");
            engine.execute("CREATE OR REPLACE FUNCTION pxv(p VARIANT) RETURNS VARCHAR AS '''X'''");
            engine.execute("CREATE OR REPLACE FUNCTION pxv(p VARCHAR) RETURNS VARCHAR AS '''V'''");
            engine.execute("CREATE OR REPLACE FUNCTION pxn(p VARIANT) RETURNS VARCHAR AS '''X'''");
            engine.execute("CREATE OR REPLACE FUNCTION pxn(p NUMBER) RETURNS VARCHAR AS '''N'''");
            assertEquals("F",
                rows("SELECT pvf(1)"));
            assertEquals("F",
                rows("SELECT pvf(1.5::FLOAT)"));
            assertEquals("V",
                rows("SELECT pvf('s')"));
            assertEquals("V",
                rows("SELECT pvf(TRUE)"));
            assertEquals("V",
                rows("SELECT pvf('2024-01-01'::DATE)"));
            assertEquals("V",
                rows("SELECT pvf(PARSE_JSON('1'))"));
            assertEquals("B",
                rows("SELECT pvb(1)"));
            assertEquals("B",
                rows("SELECT pvb(1.5::FLOAT)"));
            assertEquals("V",
                rows("SELECT pvb('s')"));
            assertEquals("B",
                rows("SELECT pvb(TRUE)"));
            assertEquals("V",
                rows("SELECT pvb('2024-01-01'::DATE)"));
            assertEquals("V",
                rows("SELECT pvt(1)"));
            assertEquals("V",
                rows("SELECT pvt(1.5::FLOAT)"));
            assertEquals("V",
                rows("SELECT pvt('s')"));
            assertEquals("V",
                rows("SELECT pvt(TRUE)"));
            assertEquals("T",
                rows("SELECT pvt('2024-01-01'::DATE)"));
            assertEquals("V",
                rows("SELECT pvt(PARSE_JSON('1'))"));
            assertEquals("X",
                rows("SELECT pxv(1)"));
            assertEquals("X",
                rows("SELECT pxv(1.5::FLOAT)"));
            assertEquals("V",
                rows("SELECT pxv('s')"));
            assertEquals("V",
                rows("SELECT pxv(TRUE)"));
            assertEquals("V",
                rows("SELECT pxv('2024-01-01'::DATE)"));
            assertEquals("X",
                rows("SELECT pxv(PARSE_JSON('1'))"));
            assertEquals("N",
                rows("SELECT pxn(1)"));
            assertEquals("X",
                rows("SELECT pxn(1.5::FLOAT)"));
            assertEquals("X",
                rows("SELECT pxn(TRUE)"));
            assertRefused("SELECT pxn('2024-01-01'::DATE)",
                "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'PXN': (DATE)");
            assertEquals("X",
                rows("SELECT pxn(PARSE_JSON('1'))"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P487B_DB");
        }
    }
}
