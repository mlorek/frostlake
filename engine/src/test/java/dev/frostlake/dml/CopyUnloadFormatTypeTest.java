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
package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * COPY INTO &lt;location&gt; writes only the types the account unloads (docs.snowflake.com, COPY INTO &lt;location&gt;:
 * {@code TYPE = CSV | JSON | PARQUET}). AVRO, ORC and XML are refused, and so is PARQUET, which this engine cannot
 * write: each used to write a gzipped CSV file under the type's name and report success. The refusal comes before
 * anything is written, whether the type is the statement's or the target stage's own.
 */
public class CopyUnloadFormatTypeTest extends BaseDatabaseTest {

    @BeforeEach
    public void createFixtures() {
        engine.execute("CREATE OR REPLACE STAGE s0");
        engine.execute("CREATE OR REPLACE STAGE sp FILE_FORMAT = (TYPE = PARQUET)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private int listed(final String stage) {
        return engine.executeQuery("LIST @" + stage).getRows().size();
    }

    @Test
    public void aParquetUnloadIsRefusedRatherThanWrittenAsCsv() {
        final String refused = "COPY INTO location: file format type 'PARQUET' is not supported by this engine for"
            + " unloading (supported: CSV, JSON)";
        assertEquals(refused, refusal("COPY INTO @s0/p1 FROM (SELECT 1 a, 'x' b) FILE_FORMAT = (TYPE = PARQUET)"));
        assertEquals(refused, refusal("COPY INTO @s0/p2 FROM (SELECT 1 a) FILE_FORMAT = (TYPE = 'parquet'"
            + " COMPRESSION = SNAPPY)"));
        assertEquals(refused, refusal("COPY INTO @sp/p3 FROM (SELECT 1 a)"));
        engine.execute("CREATE OR REPLACE FILE FORMAT pq TYPE = PARQUET");
        assertEquals(refused, refusal("COPY INTO @s0/p4 FROM (SELECT 1 a) FILE_FORMAT = (FORMAT_NAME = pq)"));
        assertEquals(0, listed("s0"));
        assertEquals(0, listed("sp"));
    }

    @Test
    public void avroOrcAndXmlCannotBeUnloaded() {
        for (final String type : new String[] {"AVRO", "ORC", "XML"}) {
            assertEquals("COPY INTO location: file format type '" + type + "' cannot be unloaded (supported for"
                + " unloading: CSV, JSON, PARQUET)",
                refusal("COPY INTO @s0/" + type + " FROM (SELECT 1 a) FILE_FORMAT = (TYPE = " + type + ")"));
        }
        assertEquals(0, listed("s0"));
    }

    @Test
    public void csvAndJsonStillUnload() {
        final ResultSet csv = engine.executeQuery("COPY INTO @s0/c1 FROM (SELECT 1 a) FILE_FORMAT = (TYPE = CSV)");
        assertEquals("1", String.valueOf(csv.getRows().get(0).getValue(0)));
        final ResultSet json = engine.executeQuery("COPY INTO @s0/j1 FROM (SELECT OBJECT_CONSTRUCT('a', 1))"
            + " FILE_FORMAT = (TYPE = JSON)");
        assertEquals("1", String.valueOf(json.getRows().get(0).getValue(0)));
        assertEquals(2, listed("s0"));
    }
}
