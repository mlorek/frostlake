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

package dev.frostlake.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * An IN that cannot stand where it is written is refused naming the IN, as written — never an operator read in its
 * place (live-verified).
 */
public class InTokenLineTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String at(final int position, final String token) {
        return "SQL compilation error:\nsyntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void anInNoListCanFollowIsRefusedAsTheIn() {
        assertEquals(at(7, "IN"), refusal("SELECT IN"));
        assertEquals(at(15, "IN"), refusal("SHOW VARIABLES IN ACCOUNT"));
        assertEquals(at(15, "IN"), refusal("SHOW TABLES IN IN"));
        assertEquals(at(27, "IN"), refusal("SHOW ORGANIZATION ACCOUNTS IN ACCOUNT"));
        assertEquals(at(7, "in"), refusal("SELECT in"));
    }

    @Test
    public void aListItCanReadStillSpeaksAtItsOwnFault() {
        assertEquals(at(14, "<EOF>"), refusal("SHOW TABLES IN"));
        assertEquals(at(30, "2"), refusal("SELECT a FROM t WHERE a IN (1 2)"));
    }
}
