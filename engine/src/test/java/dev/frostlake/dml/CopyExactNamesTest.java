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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * COPY's stage, its FORMAT_NAME and SYSTEM$STREAM_HAS_DATA name their object exactly, as LIST, DESCRIBE and a
 * staged-file query do: an unquoted LX_ST, LX_FF or LX_S is no stage "lx_st", format "lx_ff" or stream "lx_s". COPY
 * loaded from and unloaded into the quoted stage, read the quoted format, and the function answered for the quoted
 * stream.
 */
public class CopyExactNamesTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void objects() {
        engine.execute("CREATE TABLE cen_t (id INT, s STRING)");
        engine.execute("ALTER TABLE cen_t SET CHANGE_TRACKING = TRUE");
        engine.execute("CREATE STREAM \"cen_s\" ON TABLE cen_t");
        engine.execute("CREATE STAGE \"cen_st\"");
        engine.execute("CREATE FILE FORMAT \"cen_ff\" TYPE = CSV FIELD_DELIMITER = '|'");
        engine.execute("COPY INTO @\"cen_st\"/a.csv FROM (SELECT 7, 'z') FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) "
            + "SINGLE = TRUE");
    }

    @Test
    public void anUnquotedStageNameIsNoQuotedStage() {
        final String missing = "REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.CEN_ST' does not exist or "
            + "not authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
            + "TEST_DB.TEST_SCHEMA.CEN_ST.";
        assertAnswer(missing, "COPY INTO cen_t FROM @CEN_ST FILE_FORMAT = (TYPE = CSV)");
        assertAnswer(missing, "COPY INTO @CEN_ST/b.csv FROM (SELECT 1, 'q') "
            + "FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        // Nothing was unloaded into the quoted stage: it holds the one file it was given.
        assertAnswer("1", "SELECT COUNT(DISTINCT METADATA$FILENAME) FROM @\"cen_st\"");
    }

    @Test
    public void anUnquotedFormatAndStreamNameAreNoQuotedOnes() {
        assertAnswer("REFUSED SQL compilation error: | File format 'CEN_FF' does not exist or not authorized. Your "
            + "primary role <ROLE> must have at least one privilege granted on FILE FORMAT CEN_FF.",
            "COPY INTO cen_t FROM @\"cen_st\"/a.csv FILE_FORMAT = (FORMAT_NAME = CEN_FF) FORCE = TRUE");
        assertAnswer("REFUSED SQL compilation error: | Invalid value ['CEN_S'] for function 'SYSTEM$STREAM_HAS_DATA', "
            + "parameter 1: must be a valid stream name", "SELECT SYSTEM$STREAM_HAS_DATA('CEN_S')");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('\"cen_s\"')");
    }
}
