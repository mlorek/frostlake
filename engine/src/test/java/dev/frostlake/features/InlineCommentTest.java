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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class InlineCommentTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("CREATE TABLE t (i INTEGER)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("INSERT INTO t VALUES (2)");
    }

    @Test
    public void testInlineCommentInsideInClause() {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("DELETE FROM t WHERE i IN (1 -- comment\n )");
            }
        });
    }

    @Test
    public void testInlineCommentInsideSelect() {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT * FROM t WHERE i IN (\n  1, -- first\n  2  -- second\n)");
            }
        });
    }
}
