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

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An INSERT with an explicit column list whose value count doesn't match the column count is a clean user
 * error — not a leaked IndexOutOfBounds (too few) or a silent drop (too many).
 */
public class InsertColumnCountTest extends BaseDatabaseTest {

    @Test
    public void tooFewValuesThrowsCleanly() {
        engine.execute("CREATE TABLE t (a INTEGER, b INTEGER)");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO t (a, b) VALUES (1)");
            }
        });
    }

    @Test
    public void tooManyValuesThrowsCleanly() {
        engine.execute("CREATE TABLE t (a INTEGER, b INTEGER)");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO t (a) VALUES (1, 2)");
            }
        });
    }
}
