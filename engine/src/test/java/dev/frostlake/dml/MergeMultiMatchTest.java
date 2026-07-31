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

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a target row joins to more than one source row, a merge-UPDATE is non-deterministic and Snowflake
 * refuses it (ERROR_ON_NONDETERMINISTIC_MERGE, default TRUE): "Duplicate row detected during DML action".
 * The surplus source rows must never fall through to WHEN NOT MATCHED and get inserted as duplicates
 * either — with only a DELETE clause, or with an AND that narrows the duplicates to one, the merge runs.
 */
public class MergeMultiMatchTest extends BaseDatabaseTest {

    private long count(final String table) {
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM " + table).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void multiMatchedSourceDoesNotInsertADuplicate() {
        engine.execute("CREATE TABLE tgt (id INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO tgt VALUES (1, 'x')");
        engine.execute("CREATE TABLE src (id INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO src VALUES (1, 'a'), (1, 'b')");

        // Snowflake refuses the merge outright: the target row joins TWO source rows, so the UPDATE
        // would be non-deterministic. Live-verified on a real account — the statement
        // fails "Duplicate row detected during DML action Row Values: [1, \"x\"]" (the TARGET row's
        // values) and the target is left untouched; SHOW PARAMETERS reports
        // ERROR_ON_NONDETERMINISTIC_MERGE = true by default.
        final RuntimeException duplicate = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    MERGE INTO tgt USING src ON tgt.id = src.id
                      WHEN MATCHED THEN UPDATE SET v = src.v
                      WHEN NOT MATCHED THEN INSERT (id, v) VALUES (src.id, src.v)
                    """);
            }
        });
        assertTrue(duplicate.getMessage().contains("Duplicate row detected during DML action"),
            duplicate.getMessage());
        assertEquals(1L, count("tgt"));

        // The rule is UPDATE-specific and clause-sensitive (both live-verified): the same duplicate
        // join with only WHEN MATCHED THEN DELETE runs, and a WHEN MATCHED AND <cond> that narrows the
        // duplicates back to a single source row runs too.
        engine.execute("""
            MERGE INTO tgt USING src ON tgt.id = src.id
              WHEN MATCHED AND src.v = 'a' THEN UPDATE SET v = src.v
            """);
        assertEquals("a", engine.executeQuery("SELECT v FROM tgt").getRows().get(0).getValue(0));
        engine.execute("MERGE INTO tgt USING src ON tgt.id = src.id WHEN MATCHED THEN DELETE");
        assertEquals(0L, count("tgt"));
    }
}
