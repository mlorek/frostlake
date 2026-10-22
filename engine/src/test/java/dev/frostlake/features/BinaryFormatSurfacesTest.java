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

import dev.frostlake.LiveSnowflake;
import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * BINARY_FORMAT in a CREATE STAGE's file format is judged as in a named file format, and a scripting variable
 * declared BINARY reads an assigned text by the session's BINARY_INPUT_FORMAT (live-verified); a COPY's inline file
 * format judges it as well.
 */
public class BinaryFormatSurfacesTest extends MeasuredCellsTestSupport {

    @Test
    public void formatSurfaces() {
        assertAnswer("REFUSED SQL compilation error: | Invalid binary format string 'FOO': Must be 'HEX', 'BASE64', "
                + "or 'UTF-8'",
            "CREATE OR REPLACE STAGE bf_st FILE_FORMAT = (TYPE = CSV BINARY_FORMAT = FOO)");
        assertAnswer("Stage area BF_ST2 successfully created.",
            "CREATE OR REPLACE STAGE bf_st2 FILE_FORMAT = (TYPE = CSV BINARY_FORMAT = UTF8)");
        assertAnswer("Stage area BF_ST3 successfully created.",
            "CREATE OR REPLACE STAGE bf_st3 FILE_FORMAT = (TYPE = CSV BINARY_FORMAT = 'BASE64')");
        assertAnswer("4869", "DECLARE b BINARY; BEGIN b := '4869'; RETURN b; END;");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET BINARY_INPUT_FORMAT = 'BASE64'");
        assertAnswer("4869", "DECLARE b BINARY; BEGIN b := 'SGk='; RETURN b; END;");
        assertAnswer("E3CEBD", "DECLARE b BINARY; BEGIN b := '4869'; RETURN b; END;");
    }

    /**
     * A COPY's inline file format judges its BINARY_FORMAT as CREATE STAGE's does, loading and unloading alike.
     * Engine-only: a COPY INTO never runs on the shared account.
     */
    @Test
    public void copyInlineBinaryFormat() {
        assumeFalse(LiveSnowflake.enabled());
        engine.execute("CREATE OR REPLACE TABLE bt (b BINARY)");
        engine.execute("CREATE OR REPLACE STAGE bst");
        final String refused = "REFUSED SQL compilation error: | Invalid binary format string 'FOO': Must be 'HEX', "
            + "'BASE64', or 'UTF-8'";
        assertAnswer(refused, "COPY INTO bt FROM @bst FILE_FORMAT = (TYPE = CSV BINARY_FORMAT = FOO)");
        assertAnswer(refused, "COPY INTO @bst FROM bt FILE_FORMAT = (TYPE = CSV BINARY_FORMAT = FOO)");
    }
}
