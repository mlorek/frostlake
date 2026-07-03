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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Snowflake Scripting {@code LET} accepts an untyped form (type inferred) and the {@code DEFAULT} keyword as
 * a synonym for {@code :=}, in addition to the typed {@code LET x <type> := <expr>} form.
 */
public class LetUntypedTest extends BaseDatabaseTest {

    private long stored() {
        return ((Number) engine.executeQuery("SELECT v FROM lr").getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void untypedLetWithColonEquals() {
        engine.execute("CREATE TABLE lr (v INTEGER)");
        engine.execute("""
            BEGIN
              LET x := 5;
              INSERT INTO lr VALUES (:x);
            END;
            """);
        assertEquals(5L, stored());
    }

    @Test
    public void untypedLetWithDefaultKeyword() {
        engine.execute("CREATE TABLE lr (v INTEGER)");
        engine.execute("""
            BEGIN
              LET y DEFAULT 7;
              INSERT INTO lr VALUES (:y);
            END;
            """);
        assertEquals(7L, stored());
    }

    @Test
    public void typedLetWithDefaultKeyword() {
        engine.execute("CREATE TABLE lr (v INTEGER)");
        engine.execute("""
            BEGIN
              LET z INTEGER DEFAULT 9;
              INSERT INTO lr VALUES (:z);
            END;
            """);
        assertEquals(9L, stored());
    }
}
