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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A failed {@code CREATE OR REPLACE VIEW} leaves the EXISTING view standing. The replacement is
 * validated in full — body compiled, columns named and counted — before anything is dropped, so an
 * invalid replacement costs nothing. Frostlake used to drop first and validate afterwards, which
 * destroyed a working view whenever the new definition turned out to be invalid.
 *
 * <p>And the checks run in live's order: THE BODY COMPILES FIRST. A statement that is both unnamable
 * and uncompilable reports the compilation failure, not the naming one.
 */
public class CreateOrReplaceViewDurabilityTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE cd_t (k NUMBER, s VARCHAR(4))");
        engine.execute("INSERT INTO cd_t VALUES (1, 'a')");
    }

    /** The view's column names, proving which definition is in force. */
    private String columnsOf(final String viewName) {
        final ResultSet rs = engine.executeQuery("SELECT * FROM " + viewName);
        final StringBuilder names = new StringBuilder();
        for (int i = 0; i < rs.getColumnCount(); i++) {
            names.append(i == 0 ? "" : ",").append(rs.getColumns().get(i).getName());
        }
        return names.toString();
    }

    private void refuses(final String sql) {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
    }

    /** Every way a replacement can fail leaves the original in place. */
    @Test
    public void aFailedReplacementLeavesTheOriginalStanding() {
        final String[] invalid = {
            "CREATE OR REPLACE VIEW cd_v AS SELECT k + 1 FROM cd_t",
            "CREATE OR REPLACE VIEW cd_v AS SELECT no_such_fn(k) AS x FROM cd_t",
            "CREATE OR REPLACE VIEW cd_v (a, b) AS SELECT k FROM cd_t",
            "CREATE OR REPLACE VIEW cd_v AS SELECT k AS x FROM no_such_table",
            "CREATE OR REPLACE VIEW cd_v AS SELECT no_such_col AS x FROM cd_t",
        };
        for (final String attempt : invalid) {
            engine.execute("CREATE OR REPLACE VIEW cd_v AS SELECT k AS original FROM cd_t");
            refuses(attempt);
            assertEquals("ORIGINAL", columnsOf("cd_v"),
                "the original view should have survived: " + attempt);
        }
    }

    /** The same for a materialized view, which had the identical drop-first ordering. */
    @Test
    public void aFailedMaterializedReplacementLeavesTheOriginalStanding() {
        final String[] invalid = {
            "CREATE OR REPLACE MATERIALIZED VIEW cd_mv AS SELECT no_such_fn(k) AS x FROM cd_t",
            "CREATE OR REPLACE MATERIALIZED VIEW cd_mv AS SELECT k AS x FROM no_such_table",
            "CREATE OR REPLACE MATERIALIZED VIEW cd_mv AS SELECT no_such_col AS x FROM cd_t",
        };
        for (final String attempt : invalid) {
            engine.execute("CREATE OR REPLACE MATERIALIZED VIEW cd_mv AS SELECT k AS original FROM cd_t");
            refuses(attempt);
            assertEquals("ORIGINAL", columnsOf("cd_mv"),
                "the original materialized view should have survived: " + attempt);
        }
    }

    /** A SUCCESSFUL replacement does of course replace it. */
    @Test
    public void aValidReplacementStillReplaces() {
        engine.execute("CREATE OR REPLACE VIEW cd_v AS SELECT k AS original FROM cd_t");
        engine.execute("CREATE OR REPLACE VIEW cd_v AS SELECT s AS replaced FROM cd_t");
        assertEquals("REPLACED", columnsOf("cd_v"));
    }

    /** The body compiles before either column check, so its failure is the one reported. */
    @Test
    public void theBodyCompilesBeforeEitherColumnCheck() {
        assertEquals("Unknown function NO_SUCH_FN.",
            sentenceOf("CREATE VIEW cd_a AS SELECT no_such_fn(k) FROM cd_t"));
        assertEquals("Unknown function NO_SUCH_FN.",
            sentenceOf("CREATE VIEW cd_b (a, b) AS SELECT no_such_fn(k) FROM cd_t"));
    }

    /** With a body that DOES compile, the column checks speak as before. */
    @Test
    public void theColumnChecksStillSpeakForACompilableBody() {
        assertEquals("Missing column specification",
            sentenceOf("CREATE VIEW cd_c AS SELECT k + 1 FROM cd_t"));
        assertEquals("Invalid column definition list",
            sentenceOf("CREATE VIEW cd_d (a, b) AS SELECT k FROM cd_t"));
    }

    /** A declared column list NAMES an otherwise unnamable item, so the view is legal. */
    @Test
    public void aDeclaredListNamesAnUnnamableItem() {
        engine.execute("CREATE VIEW cd_e (a) AS SELECT k + 1 FROM cd_t");
        assertEquals("A", columnsOf("cd_e"));
    }

    /** The refusal's own sentence, with the compilation prefix stripped. */
    private String sentenceOf(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        final String flat = e.getMessage().replace('\n', ' ');
        final int at = flat.indexOf("error: ");
        return at < 0 ? flat.trim() : flat.substring(at + "error: ".length()).trim();
    }
}
