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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A query over staged CSV declares its positional columns at a width that follows the KIND of stage: a
 * named or user stage declares the 128MB text, a table stage the 16MB one. METADATA$FILENAME is the
 * 16MB text whichever stage it came from.
 *
 * <p>The files are staged in a local directory here, which the account has no equivalent for, so these
 * run embedded; the live shape is the one the widths are pinned to.
 */
public class StageQueryColumnWidthTest extends BaseDatabaseTest {

    /** The engine stages files on disk, which a real account's cloud storage cannot stand in for. */
    private static final String LOCAL_STAGES = "the files are staged in a local directory, which the"
        + " account's cloud stages have no equivalent for";

    @Override
    protected void setupTest() {
        Assumptions.assumeFalse(isLiveSnowflake(), LOCAL_STAGES);
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("CREATE OR REPLACE STAGE st");
        stageLocalFile("st", "rows.csv", "1,a\n2,b\n");
        stageLocalFile("%t1", "rows.csv", "1,a\n2,b\n");
        stageLocalFile("~", "rows.csv", "1,a\n2,b\n");
    }

    /** The declared type of a query's first column. */
    private String firstColumnType(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getColumns().get(0).getDataType().getName()
            + "(" + ((dev.frostlake.types.StringType) rs.getColumns().get(0).getDataType()).getMaxLength() + ")";
    }

    /** A named stage and the user stage declare the 128MB text. */
    @Test
    public void aNamedOrUserStageDeclaresTheWidestText() {
        assertEquals("VARCHAR(134217728)", firstColumnType("SELECT $1 FROM @st"));
        assertEquals("VARCHAR(134217728)", firstColumnType("SELECT $1 FROM @~"));
    }

    /** A table stage declares the 16MB one. */
    @Test
    public void aTableStageDeclaresTheOrdinaryText() {
        assertEquals("VARCHAR(16777216)", firstColumnType("SELECT $1 FROM @%t1"));
    }

    /** METADATA$FILENAME is the 16MB text whichever stage it came from. */
    @Test
    public void theFilenameColumnIsUnchanged() {
        final ResultSet rs = engine.executeQuery("SELECT METADATA$FILENAME FROM @st");
        assertEquals(16777216,
            ((dev.frostlake.types.StringType) rs.getColumns().get(0).getDataType()).getMaxLength());
    }
}
