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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CollateAndKeywordFunctionTest extends BaseDatabaseTest {

    @Test
    public void collateFunctionParsesAndPassesThrough() {
        final ResultSet rs = engine.executeQuery("SELECT COLLATE('AbC', 'en-ci')");
        assertEquals("AbC", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void collateFunctionInPredicate() {
        engine.execute("CREATE TABLE t (d VARCHAR)");
        engine.execute("INSERT INTO t VALUES ('N:1'), ('N:2')");
        final ResultSet rs = engine.executeQuery(
            "SELECT d FROM t WHERE COLLATE(d, 'en-ci') NOT IN ('N:2') ORDER BY d");
        assertEquals(1, rs.getRowCount());
        assertEquals("N:1", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void closeUsableAsQualifiedNameSegment() {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    CREATE OR REPLACE PROCEDURE p_close_name() RETURNS OBJECT LANGUAGE SQL AS
                    DECLARE
                        s OBJECT;
                    BEGIN
                        s := (a.b.close(:s, NULL));
                        RETURN :s;
                    END;
                    """);
            }
        });
    }
}
