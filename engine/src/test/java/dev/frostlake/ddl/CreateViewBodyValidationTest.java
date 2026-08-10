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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CREATE VIEW whose body will not COMPILE is refused, and the refusal is simply the select's own
 * error — Snowflake has no view-specific sentence for it. A missing table names the object fully, a
 * missing column gives the positioned invalid-identifier error, and an unknown function names itself.
 *
 * <p>All live-measured. The distinction that matters underneath: Frostlake resolves a view's column
 * shape by EXECUTING the definition where Snowflake merely plans it, so only COMPILATION failures
 * refuse the CREATE — a body that compiles and then fails on the data present must still be accepted,
 * because live never evaluates those rows at all.
 */
public class CreateViewBodyValidationTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE bv_t (k NUMBER, w VARCHAR(4))");
    }

    private String refusalOf(final String sql) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
        return ex.getMessage();
    }

    /**
     * A body over a table that does not exist is refused, naming the object.
     *
     * <p>Only the object NAME is asserted, not its qualification: live spells it in full
     * ({@code Object 'TEST_DB.TEST_SCHEMA.NO_SUCH_TABLE' ...}) where Frostlake echoes it as written,
     * which is a divergence in the select's own missing-object sentence rather than in this rule, and
     * is tracked on its own.
     */
    @Test
    public void aMissingTableIsRefused() {
        final String message = refusalOf("CREATE VIEW bv_v AS SELECT * FROM no_such_table");
        assertTrue(message.startsWith("SQL compilation error:"), message);
        assertTrue(message.contains("NO_SUCH_TABLE"), message);
        assertTrue(message.contains("does not exist or not authorized."), message);
    }

    /** And so is one that names a column the source does not have. */
    @Test
    public void aMissingColumnIsRefused() {
        final String message = refusalOf("CREATE VIEW bv_v AS SELECT no_such_col FROM bv_t");
        assertTrue(message.startsWith("SQL compilation error:"), message);
        assertTrue(message.contains("invalid identifier 'NO_SUCH_COL'"), message);
    }

    /**
     * A body whose column list does not match the view's declared one is refused before anything is
     * compiled — the one view-specific sentence in this area.
     */
    @Test
    public void aMismatchedColumnListIsRefused() {
        assertTrue(refusalOf("CREATE VIEW bv_v (a) AS SELECT k, w FROM bv_t")
            .contains("Invalid column definition list"));
    }

    /** A body that compiles is accepted, and reports its columns. */
    @Test
    public void aCompilableBodyIsAccepted() {
        engine.execute("CREATE VIEW bv_ok AS SELECT k, w FROM bv_t");
        assertEquals(2, engine.executeQuery("DESCRIBE VIEW bv_ok").getRows().size());
    }

    /** A refused CREATE leaves NO view behind. */
    @Test
    public void aRefusedCreateLeavesNothingBehind() {
        refusalOf("CREATE VIEW bv_gone AS SELECT * FROM no_such_table");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM bv_gone");
            }
        });
        assertTrue(ex.getMessage().contains("BV_GONE"), ex.getMessage());
    }
}
