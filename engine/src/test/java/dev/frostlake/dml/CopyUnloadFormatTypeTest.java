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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COPY INTO &lt;location&gt; writes only the types the account unloads (docs.snowflake.com, COPY INTO &lt;location&gt;:
 * {@code TYPE = CSV | JSON | PARQUET}). AVRO, ORC and XML are refused once the source and the stage are found, by the
 * type as it was written, whether that is the statement's or the target stage's own; each used to write a gzipped CSV
 * file under the type's name and report success. PARQUET, which this engine cannot write, is refused as its own limit.
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

    private static String notImplemented(final String type) {
        return "SQL compilation error: Unload not yet implemented for file format '" + type + "'";
    }

    @Test
    public void aParquetUnloadIsRefusedRatherThanWrittenAsCsv() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the account unloads PARQUET; this engine ships no writer for it");
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
            assertEquals(notImplemented(type),
                refusal("COPY INTO @s0/" + type + " FROM (SELECT 1 a) FILE_FORMAT = (TYPE = " + type + ")"));
        }
        assertEquals(0, listed("s0"));
    }

    /** The statement's type is named as it was written, over what the format or the stage holds. */
    @Test
    public void theRefusalNamesTheTypeAsItWasWritten() {
        assertEquals(notImplemented("avro"),
            refusal("COPY INTO @s0/a1 FROM (SELECT 1 a) FILE_FORMAT = (TYPE = avro)"));
        assertEquals(notImplemented("xml"),
            refusal("COPY INTO @s0/a2 FROM (SELECT 1 a) FILE_FORMAT = (TYPE = 'xml')"));
        engine.execute("CREATE OR REPLACE FILE FORMAT av TYPE = AVRO");
        assertEquals(notImplemented("AVRO"),
            refusal("COPY INTO @s0/a3 FROM (SELECT 1 a) FILE_FORMAT = (FORMAT_NAME = av)"));
        engine.execute("CREATE OR REPLACE STAGE sa FILE_FORMAT = (TYPE = AVRO)");
        assertEquals(notImplemented("AVRO"), refusal("COPY INTO @sa/a4 FROM (SELECT 1 a)"));
        assertEquals(notImplemented("Orc"),
            refusal("COPY INTO @sa/a6 FROM (SELECT 1 a) FILE_FORMAT = (TYPE = Orc)"));
        engine.execute("CREATE OR REPLACE STAGE sn FILE_FORMAT = av");
        assertEquals(notImplemented("AVRO"), refusal("COPY INTO @sn/a5 FROM (SELECT 1 a)"));
    }

    /** An option, the source and the stage are all judged before the type is. */
    @Test
    public void theTypeIsJudgedLast() {
        engine.execute("CREATE OR REPLACE TABLE ut (a INT)");
        assertEquals("SQL compilation error:\ninvalid parameter 'NOSUCH'",
            refusal("COPY INTO @s0/o1 FROM ut FILE_FORMAT = (TYPE = AVRO) NOSUCH = 1"));
        final String table = refusal("COPY INTO @s0/o2 FROM nosuch_t FILE_FORMAT = (TYPE = AVRO)");
        assertTrue(table.startsWith("SQL compilation error:\nObject 'NOSUCH_T' does not exist"), table);
        final String stage = refusal("COPY INTO @nosuch_s/o3 FROM ut FILE_FORMAT = (TYPE = AVRO)");
        assertTrue(stage.contains("Stage 'TEST_DB.TEST_SCHEMA.NOSUCH_S' does not exist"), stage);
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
