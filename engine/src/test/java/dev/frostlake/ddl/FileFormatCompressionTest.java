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

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A file format's COMPRESSION takes the codecs its TYPE takes, as docs.snowflake.com CREATE FILE FORMAT lists them:
 * CSV, JSON and XML {@code AUTO | GZIP | BZ2 | BROTLI | ZSTD | DEFLATE | RAW_DEFLATE | NONE}, AVRO the same but BZ2,
 * PARQUET {@code AUTO | LZO | SNAPPY | NONE}. The domain is the effective type's wherever TYPE stands in the list, in
 * CREATE FILE FORMAT, in a stage's FILE_FORMAT and in COPY's; a codec the engine cannot read or write is the engine's
 * own refusal when the COPY runs, not the account's "invalid value".
 */
public class FileFormatCompressionTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    /** The COMPRESSION a file format reports. */
    private String compressionOf(final String format) {
        engine.execute("DESC FILE FORMAT " + format);
        final Row row = engine.executeQuery("SELECT \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"
            + " WHERE \"property\" = 'COMPRESSION'").getRows().get(0);
        return String.valueOf(row.getValue(0));
    }

    @Test
    public void parquetTakesSnappyAndLzoEverywhereAFormatIsWritten() {
        engine.execute("CREATE OR REPLACE FILE FORMAT fp TYPE = PARQUET COMPRESSION = SNAPPY");
        assertEquals("SNAPPY", compressionOf("fp"));
        engine.execute("CREATE OR REPLACE FILE FORMAT fl COMPRESSION = 'lzo' TYPE = PARQUET");
        assertEquals("LZO", compressionOf("fl").toUpperCase(Locale.ROOT));
        engine.execute("ALTER FILE FORMAT fp SET COMPRESSION = LZO");
        assertEquals("LZO", compressionOf("fp"));
        engine.execute("CREATE OR REPLACE STAGE s9 FILE_FORMAT = (TYPE = PARQUET COMPRESSION = SNAPPY)");
        engine.execute("CREATE OR REPLACE STAGE s0");
        engine.execute("CREATE OR REPLACE TABLE tv (v VARIANT)");
        // The load reaches the engine's own refusal of PARQUET without the formats module, not "invalid value".
        final String load = refusal("COPY INTO tv FROM @s0 FILE_FORMAT = (TYPE = PARQUET COMPRESSION = LZO)");
        assertTrue(load.contains("is not supported") && !load.contains("invalid value"), load);
        final String unload = refusal("COPY INTO @s0/p3 FROM (SELECT 1 a) FILE_FORMAT = (TYPE = PARQUET"
            + " COMPRESSION = SNAPPY)");
        assertTrue(unload.contains("not supported by this engine") && !unload.contains("invalid value"), unload);
    }

    @Test
    public void aCodecIsJudgedByItsOwnTypesList() {
        assertEquals("SQL compilation error:\ninvalid value [SNAPPY] for parameter 'COMPRESSION'",
            refusal("CREATE OR REPLACE FILE FORMAT fc TYPE = CSV COMPRESSION = SNAPPY"));
        assertEquals("SQL compilation error:\ninvalid value [LZO] for parameter 'COMPRESSION'",
            refusal("CREATE OR REPLACE FILE FORMAT fj COMPRESSION = LZO TYPE = JSON"));
        assertEquals("SQL compilation error:\ninvalid value [GZIP] for parameter 'COMPRESSION'",
            refusal("CREATE OR REPLACE FILE FORMAT fp TYPE = PARQUET COMPRESSION = GZIP"));
        assertEquals("SQL compilation error:\ninvalid value [BZ2] for parameter 'COMPRESSION'",
            refusal("CREATE OR REPLACE FILE FORMAT fa TYPE = AVRO COMPRESSION = BZ2"));
        assertEquals("SQL compilation error:\ninvalid value [SNAPPY] for parameter 'COMPRESSION'",
            refusal("CREATE OR REPLACE STAGE sx FILE_FORMAT = (TYPE = XML COMPRESSION = SNAPPY)"));
        engine.execute("CREATE OR REPLACE FILE FORMAT fx TYPE = XML COMPRESSION = BZ2");
        engine.execute("CREATE OR REPLACE FILE FORMAT fa TYPE = AVRO COMPRESSION = ZSTD");
        engine.execute("CREATE OR REPLACE FILE FORMAT fc TYPE = CSV COMPRESSION = RAW_DEFLATE");
        engine.execute("CREATE OR REPLACE FILE FORMAT fp TYPE = PARQUET COMPRESSION = NONE");
        assertEquals("SQL compilation error:\ninvalid value [GZIP] for parameter 'COMPRESSION'",
            refusal("ALTER FILE FORMAT fp SET COMPRESSION = GZIP"));
        // A CSV list without TYPE is judged as CSV.
        assertEquals("SQL compilation error:\ninvalid value [SNAPPY] for parameter 'COMPRESSION'",
            refusal("CREATE OR REPLACE FILE FORMAT fd COMPRESSION = SNAPPY"));
    }
}
