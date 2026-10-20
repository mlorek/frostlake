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
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A hybrid table's DDL and its keys under MERGE (documented): GET_DDL writes {@code create or replace HYBRID TABLE},
 * so the DDL run back makes a hybrid table that holds its keys again; and a MERGE's WHEN NOT MATCHED THEN INSERT is
 * held to the table's FOREIGN KEY, PRIMARY KEY and UNIQUE keys as an INSERT is, in the INSERT's sentences. Engine
 * only: the live account offers no hybrid tables.
 */
public class HybridTableDdlAndMergeKeysTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "the live account offers no hybrid tables");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private List<String> rows(final String sql) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.add(row.getValue(0) + "|" + row.getValue(1));
        }
        return out;
    }

    @Test
    public void getDdlOfAHybridTableMakesAHybridTableAgain() {
        engine.execute("CREATE HYBRID TABLE h (id INT PRIMARY KEY, v INT)");
        final String ddl = String.valueOf(engine.executeQuery("SELECT GET_DDL('TABLE', 'h')").getRows().get(0)
            .getValue(0));
        assertTrue(ddl.startsWith("create or replace HYBRID TABLE H (\n"), ddl);
        assertTrue(ddl.contains("primary key (ID)"), ddl);
        engine.execute("DROP TABLE h");
        engine.execute(ddl);
        assertEquals(1, engine.executeQuery("SHOW HYBRID TABLES LIKE 'H'").getRows().size());
        engine.execute("INSERT INTO h VALUES (1, 10)");
        assertEquals("Primary key already exists", refusal("INSERT INTO h VALUES (1, 11)"));
    }

    @Test
    public void aMergeInsertIsHeldToTheKeysAsAnInsertIs() {
        engine.execute("CREATE HYBRID TABLE h (id INT PRIMARY KEY, u INT UNIQUE)");
        engine.execute("INSERT INTO h VALUES (1, 10)");
        engine.execute("CREATE HYBRID TABLE c (id INT PRIMARY KEY, hid INT REFERENCES h (id))");
        final String orphan = refusal("INSERT INTO c VALUES (6, 66)");
        assertTrue(orphan.contains("Foreign key constraint"), orphan);
        assertEquals(orphan, refusal("MERGE INTO c USING (SELECT 5 AS id, 55 AS hid) s ON c.id = s.id"
            + " WHEN NOT MATCHED THEN INSERT VALUES (s.id, s.hid)"));
        assertEquals("[]", rows("SELECT * FROM c ORDER BY 1").toString());
        assertEquals(refusal("INSERT INTO h VALUES (1, 12)"), refusal("MERGE INTO h USING (SELECT 1 AS id, 11 AS u) s"
            + " ON h.u = s.u WHEN NOT MATCHED THEN INSERT VALUES (s.id, s.u)"));
        assertEquals(refusal("INSERT INTO h VALUES (2, 10)"), refusal("MERGE INTO h USING (SELECT 2 AS id, 10 AS u) s"
            + " ON h.id = s.id WHEN NOT MATCHED THEN INSERT VALUES (s.id, s.u)"));
        assertEquals("[1|10]", rows("SELECT * FROM h ORDER BY 1").toString());
    }
}
