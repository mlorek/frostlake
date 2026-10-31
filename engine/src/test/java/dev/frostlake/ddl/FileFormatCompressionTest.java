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
 * A COMPRESSION as the account judges it (live-verified). Ten codecs exist, and any other word is an invalid value
 * wherever a format is written. A file format and COPY's list judge a codec by the effective type, wherever TYPE stands
 * in the list: CSV, JSON, XML and AVRO take {@code AUTO | GZIP | BZ2 | BROTLI | ZSTD | DEFLATE | RAW_DEFLATE | NONE},
 * PARQUET {@code AUTO | LZO | SNAPPY | NONE}, and any other codec is an invalid compression format. A stage's list and
 * a table's take all ten, whatever their type. A codec the engine cannot read or write is the engine's own refusal
 * when the COPY runs.
 */
public class FileFormatCompressionTest extends BaseDatabaseTest {

    private static String invalidFormat(final String codec) {
        return "SQL compilation error:\nInvalid compression format " + codec;
    }

    private static String invalidValue(final String written) {
        return "SQL compilation error:\ninvalid value [" + written + "] for parameter 'COMPRESSION'";
    }

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
        if (isLiveSnowflake()) {
            // The account reads and writes PARQUET; what follows is the engine's own refusal.
            return;
        }
        // The load reaches the engine's own refusal of PARQUET without the formats module, not "invalid value".
        final String load = refusal("COPY INTO tv FROM @s0 FILE_FORMAT = (TYPE = PARQUET COMPRESSION = LZO)");
        assertTrue(load.contains("is not supported") && !load.contains("invalid value"), load);
        final String unload = refusal("COPY INTO @s0/p3 FROM (SELECT 1 a) FILE_FORMAT = (TYPE = PARQUET"
            + " COMPRESSION = SNAPPY)");
        assertTrue(unload.contains("not supported by this engine") && !unload.contains("invalid value"), unload);
    }

    @Test
    public void aCodecIsJudgedByItsOwnTypesList() {
        assertEquals(invalidFormat("SNAPPY"),
            refusal("CREATE OR REPLACE FILE FORMAT fc TYPE = CSV COMPRESSION = SNAPPY"));
        assertEquals(invalidFormat("SNAPPY"),
            refusal("CREATE OR REPLACE FILE FORMAT fc TYPE = CSV COMPRESSION = 'snappy'"),
            "the codec is named in upper case, however it is written");
        assertEquals(invalidFormat("LZO"), refusal("CREATE OR REPLACE FILE FORMAT fj COMPRESSION = LZO TYPE = JSON"));
        assertEquals(invalidFormat("GZIP"),
            refusal("CREATE OR REPLACE FILE FORMAT fp TYPE = PARQUET COMPRESSION = GZIP"));
        assertEquals(invalidFormat("SNAPPY"),
            refusal("CREATE OR REPLACE FILE FORMAT fa TYPE = AVRO COMPRESSION = SNAPPY"));
        engine.execute("CREATE OR REPLACE FILE FORMAT fx TYPE = XML COMPRESSION = BZ2");
        engine.execute("CREATE OR REPLACE FILE FORMAT fa TYPE = AVRO COMPRESSION = BZ2");
        engine.execute("CREATE OR REPLACE FILE FORMAT fa TYPE = AVRO COMPRESSION = ZSTD");
        engine.execute("CREATE OR REPLACE FILE FORMAT fc TYPE = CSV COMPRESSION = RAW_DEFLATE");
        engine.execute("CREATE OR REPLACE FILE FORMAT fp TYPE = PARQUET COMPRESSION = NONE");
        assertEquals(invalidFormat("GZIP"), refusal("ALTER FILE FORMAT fp SET COMPRESSION = GZIP"));
        // A CSV list without TYPE is judged as CSV.
        assertEquals(invalidFormat("SNAPPY"), refusal("CREATE OR REPLACE FILE FORMAT fd COMPRESSION = SNAPPY"));
    }

    @Test
    public void aWordThatIsNoCodecIsAnInvalidValueEverywhere() {
        engine.execute("CREATE OR REPLACE STAGE sa");
        engine.execute("CREATE OR REPLACE TABLE tc (a INT)");
        final String list = "(TYPE = CSV COMPRESSION = NOSUCH)";
        assertEquals(invalidValue("LZ4"), refusal("CREATE OR REPLACE FILE FORMAT fn TYPE = PARQUET COMPRESSION = LZ4"));
        assertEquals(invalidValue("NOSUCH"), refusal("CREATE OR REPLACE STAGE sn FILE_FORMAT = " + list));
        assertEquals(invalidValue("NOSUCH"), refusal("ALTER STAGE sa SET FILE_FORMAT = " + list));
        assertEquals(invalidValue("NOSUCH"), refusal("CREATE OR REPLACE TABLE tn (a INT) STAGE_FILE_FORMAT = " + list));
        assertEquals(invalidValue("NOSUCH"), refusal("ALTER TABLE tc SET STAGE_FILE_FORMAT = " + list));
        assertEquals(invalidValue("NOSUCH"), refusal("COPY INTO tc FROM @sa FILE_FORMAT = " + list));
    }

    @Test
    public void aStagesListAndATablesTakeAnyCodec() {
        engine.execute("CREATE OR REPLACE STAGE sx FILE_FORMAT = (TYPE = XML COMPRESSION = SNAPPY)");
        engine.execute("ALTER STAGE sx SET FILE_FORMAT = (TYPE = PARQUET COMPRESSION = GZIP)");
        engine.execute("CREATE OR REPLACE TABLE tn (a INT) STAGE_FILE_FORMAT = (TYPE = JSON COMPRESSION = LZO)");
        engine.execute("ALTER TABLE tn SET STAGE_FILE_FORMAT = (TYPE = CSV COMPRESSION = SNAPPY)");
    }

    @Test
    public void copyJudgesACodecByItsType() {
        engine.execute("CREATE OR REPLACE STAGE s0");
        engine.execute("CREATE OR REPLACE TABLE tc (a INT)");
        engine.execute("CREATE OR REPLACE FILE FORMAT fnam TYPE = CSV");
        assertEquals(invalidFormat("SNAPPY"),
            refusal("COPY INTO tc FROM @s0 FILE_FORMAT = (TYPE = CSV COMPRESSION = SNAPPY)"));
        assertEquals(invalidFormat("LZO"), refusal("COPY INTO @s0/u FROM (SELECT 1 a) FILE_FORMAT = (TYPE = JSON"
            + " COMPRESSION = LZO)"));
        assertEquals(invalidFormat("SNAPPY"), refusal("COPY INTO tc FROM @s0 FILE_FORMAT = (FORMAT_NAME = fnam"
            + " COMPRESSION = SNAPPY)"), "the named format's type judges the codec written beside it");
    }
}
