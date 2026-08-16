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
 * Automatic reclustering is paused and resumed on a clustered table, and SHOW TABLES'
 * {@code automatic_clustering} cell follows it — ON, then OFF, then ON again (live-verified, which is
 * what makes this more than an accepted-and-ignored clause).
 *
 * <p>The refusal is measured too, and it is unusual: a table with no clustering key answers
 * {@code Table 'X' is not clustered} with a trailing newline and NO compilation-error prefix.
 */
public class AlterTableReclusterTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE clustered_t (n INT) CLUSTER BY (n)");
        engine.execute("CREATE TABLE plain_t (n INT)");
    }

    private String automaticClustering(final String table) {
        final ResultSet tables = engine.executeQuery("SHOW TABLES LIKE '" + table + "'");
        return cell(tables, soleRowWhere(tables, "name", table.toUpperCase()), "automatic_clustering");
    }

    @Test
    public void suspendAndResumeMoveTheAutomaticClusteringCell() {
        assertEquals("ON", automaticClustering("clustered_t"));
        engine.execute("ALTER TABLE clustered_t SUSPEND RECLUSTER");
        assertEquals("OFF", automaticClustering("clustered_t"));
        engine.execute("ALTER TABLE clustered_t RESUME RECLUSTER");
        assertEquals("ON", automaticClustering("clustered_t"));
    }

    @Test
    public void theClusteringKeyIsUntouchedBySuspending() {
        engine.execute("ALTER TABLE clustered_t SUSPEND RECLUSTER");
        final ResultSet tables = engine.executeQuery("SHOW TABLES LIKE 'clustered_t'");
        assertEquals("LINEAR(n)",
            cell(tables, soleRowWhere(tables, "name", "CLUSTERED_T"), "cluster_by"));
    }

    @Test
    public void suspendingAnUnclusteredTableIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE plain_t SUSPEND RECLUSTER");
            }
        });
        assertEquals("Table 'PLAIN_T' is not clustered\n", ex.getMessage());
    }

    @Test
    public void resumingAnUnclusteredTableIsRefusedTheSameWay() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE plain_t RESUME RECLUSTER");
            }
        });
        assertEquals("Table 'PLAIN_T' is not clustered\n", ex.getMessage());
    }

    /** The new keyword must not cost anyone a column or table called recluster. */
    @Test
    public void reclusterRemainsUsableAsAName() {
        engine.execute("CREATE TABLE recluster (recluster INT)");
        engine.execute("INSERT INTO recluster VALUES (5)");
        assertEquals("5", engine.executeQuery("SELECT recluster FROM recluster")
            .getRows().get(0).getValue(0).toString());
    }
}
