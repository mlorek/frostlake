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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A refused ALTER FILE FORMAT … SET changes nothing: a codec the format's type does not take is refused, and the format
 * keeps the codec it had. The value was stored as the list was read, before the codec was judged, so DESCRIBE and
 * GET_DDL reported the refused one.
 */
public class AlterFileFormatRefusedTest extends MeasuredCellsTestSupport {

    private static final String COMPRESSION = "SELECT \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) "
        + "WHERE \"property\" = 'COMPRESSION'";

    @Test
    public void aRefusedCodecLeavesTheFormatsOwn() {
        engine.execute("CREATE FILE FORMAT aff_ff TYPE = CSV COMPRESSION = GZIP");
        assertAnswer("REFUSED SQL compilation error: | invalid value [FOO] for parameter 'COMPRESSION'",
            "ALTER FILE FORMAT aff_ff SET COMPRESSION = FOO");
        engine.execute("DESC FILE FORMAT aff_ff");
        assertAnswer("GZIP", COMPRESSION);
        engine.execute("CREATE FILE FORMAT aff_fp TYPE = PARQUET COMPRESSION = SNAPPY");
        assertAnswer("REFUSED SQL compilation error: | Invalid compression format BZ2",
            "ALTER FILE FORMAT aff_fp SET COMPRESSION = BZ2");
        engine.execute("DESC FILE FORMAT aff_fp");
        assertAnswer("SNAPPY", COMPRESSION);
    }
}
