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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SHOW PROCEDURES and SHOW USER PROCEDURES leave a user procedure's {@code secrets} and
 * {@code external_access_integrations} cells empty, not NULL, as SHOW FUNCTIONS leaves a function's
 * (live-verified).
 */
public class ShowProceduresEmptyCellsTest extends BaseDatabaseTest {

    private static final int NAME = 1;
    private static final int SECRETS = 14;
    private static final int EXTERNAL_ACCESS_INTEGRATIONS = 15;

    /** Each listed procedure's name with its last two cells quoted, NULL spelled bare, a bar between rows. */
    private String lastCells(final String sql) {
        final List<String> rows = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if ("Y".equals(String.valueOf(row.getValue(3)))) {
                continue;
            }
            rows.add(row.getValue(NAME) + ": " + quoted(row.getValue(SECRETS)) + ", "
                + quoted(row.getValue(EXTERNAL_ACCESS_INTEGRATIONS)));
        }
        return String.join(" | ", rows);
    }

    private static String quoted(final Object cell) {
        return cell == null ? "NULL" : "'" + cell + "'";
    }

    @Test
    public void aUserProcedureLeavesSecretsAndIntegrationsEmpty() {
        engine.execute("CREATE OR REPLACE PROCEDURE \"procCase\"() RETURNS VARCHAR LANGUAGE SQL "
            + "AS $$ BEGIN RETURN 'p'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p2(x INT, y VARCHAR) RETURNS VARCHAR LANGUAGE SQL "
            + "AS $$ BEGIN RETURN 'p'; END; $$");
        assertEquals("procCase: '', ''", lastCells("SHOW PROCEDURES LIKE 'procCase'"));
        assertEquals("procCase: '', ''", lastCells("SHOW USER PROCEDURES LIKE 'procCase'"));
        assertEquals("procCase: '', ''", lastCells("SHOW TERSE PROCEDURES LIKE 'procCase'"));
        assertEquals("P2: '', '' | procCase: '', ''", lastCells("SHOW USER PROCEDURES LIKE 'P%'"));
    }
}
