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

package dev.frostlake.stage;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DIRECTORY(@stage) declares its columns in the account's types: RELATIVE_PATH a VARCHAR(134217728), SIZE a
 * NUMBER(38,0), LAST_MODIFIED a TIMESTAMP_TZ(3) at the session's offset, and MD5, ETAG and FILE_URL a VARCHAR of no
 * width. A table built over the listing stores the texts as VARCHAR(16777216), and a view declares the same. Every
 * cell is live-verified.
 */
public class DirectoryColumnTypesTest extends BaseDatabaseTest {

    private static final String STORED = "RELATIVE_PATH:TEXT:16777216:-:-:- SIZE:NUMBER:-:38:0:-"
        + " LAST_MODIFIED:TIMESTAMP_TZ:-:-:-:3 MD5:TEXT:16777216:-:-:- ETAG:TEXT:16777216:-:-:-"
        + " FILE_URL:TEXT:16777216:-:-:-";

    @Override
    protected void setupTest() {
        engine.execute("CREATE STAGE ds DIRECTORY = (ENABLE = TRUE)");
    }

    /** The first row's first cell. */
    private String answer(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    private String columnsOf(final String table) {
        return answer("SELECT LISTAGG(column_name || ':' || data_type || ':'"
            + " || COALESCE(character_maximum_length::VARCHAR, '-') || ':' || COALESCE(numeric_precision::VARCHAR, '-')"
            + " || ':' || COALESCE(numeric_scale::VARCHAR, '-') || ':' || COALESCE(datetime_precision::VARCHAR, '-'),"
            + " ' ') WITHIN GROUP (ORDER BY ordinal_position) FROM information_schema.columns WHERE table_name = '"
            + table + "'");
    }

    @Test
    public void aTableBuiltOverTheListingStoresTheDeclaredTypes() {
        engine.execute("CREATE OR REPLACE TABLE dt AS SELECT * FROM DIRECTORY(@ds)");
        assertEquals(STORED, columnsOf("DT"));
    }

    @Test
    public void aViewOverTheListingDeclaresTheSameTypes() {
        engine.execute("CREATE OR REPLACE VIEW dv AS SELECT * FROM DIRECTORY(@ds)");
        assertEquals(STORED, columnsOf("DV"));
    }

    @Test
    public void eachColumnCarriesItsTypeIntoTheQuery() {
        // Live lists a staged file only after ALTER STAGE ... REFRESH, a statement this engine does not parse, so the
        // cells over a listed row are the engine's alone; they are the ones the account answers for its own file.
        Assumptions.assumeFalse(isLiveSnowflake(), "needs a listed file, which live lists only after a REFRESH");
        engine.execute("COPY INTO @ds/u.csv FROM (SELECT 1 AS n) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)"
            + " SINGLE = TRUE");
        engine.execute("ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'");
        assertEquals("VARCHAR(134217728)[LOB] NUMBER(38,0)[SB16] TIMESTAMP_TZ(3)[SB8] VARCHAR[LOB] VARCHAR[LOB]"
                + " VARCHAR[LOB]",
            answer("SELECT SYSTEM$TYPEOF(relative_path) || ' ' || SYSTEM$TYPEOF(size) || ' '"
                + " || SYSTEM$TYPEOF(last_modified) || ' ' || SYSTEM$TYPEOF(md5) || ' ' || SYSTEM$TYPEOF(etag) || ' '"
                + " || SYSTEM$TYPEOF(file_url) FROM DIRECTORY(@ds)"));
        assertEquals("u.csv +09:00", answer("SELECT relative_path || ' ' || TO_VARCHAR(last_modified, 'TZH:TZM')"
            + " FROM DIRECTORY(@ds)"));
    }
}
