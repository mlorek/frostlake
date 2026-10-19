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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A quoted stage name, or a quoted schema name, may hold a dot, and the stage it names is one stage wherever it is
 * referenced: LIST, a staged-file query and INFORMATION_SCHEMA.STAGES, which lists it by its name as written
 * (live-verified).
 */
public class DottedStageNameTest extends BaseDatabaseTest {

    private int rows(final String sql) {
        return engine.executeQuery(sql).getRows().size();
    }

    private String first(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        return String.valueOf(row.getValue(0));
    }

    @Test
    public void aStageWhoseNameHoldsADotIsOneStage() {
        assertEquals("Stage area s.dotted successfully created.", first("CREATE STAGE \"s.dotted\""));
        assertEquals(0, rows("LIST @\"s.dotted\""));
        assertEquals(0, rows("SELECT $1 FROM @\"s.dotted\""));
        assertEquals(0, rows("SELECT $1 FROM @\"s.dotted\"/sub/"));
        assertEquals("s.dotted", first("SELECT STAGE_NAME FROM INFORMATION_SCHEMA.STAGES"
            + " WHERE STAGE_NAME = 's.dotted'"));
    }

    @Test
    public void aStageInASchemaWhoseNameHoldsADot() {
        final String database = first("SELECT CURRENT_DATABASE()");
        engine.execute("CREATE SCHEMA \"S.A\"");
        assertEquals("Stage area C successfully created.", first("CREATE STAGE \"S.A\".c"));
        assertEquals(0, rows("LIST @\"S.A\".c"));
        assertEquals(0, rows("SELECT $1 FROM @\"S.A\".c"));
        assertEquals("S.A", first("SELECT STAGE_SCHEMA FROM " + database + ".INFORMATION_SCHEMA.STAGES"
            + " WHERE STAGE_NAME = 'C'"));
    }
}
