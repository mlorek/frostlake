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

package dev.frostlake.functions;

import dev.frostlake.functions.table.Generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GENERATOR holds its rows in memory, so a count past what a list holds is refused by name when no time limit
 * bounds it - never narrowed to another count, as a count read in 32 bits was. The function is asked directly:
 * a statement would be a count no test should hand to an account.
 */
public class GeneratorRowCapTest {

    @Test
    public void aCountPastWhatAListHoldsIsRefusedByName() {
        final Generator generator = new Generator();
        final Map<String, Object> bounds = new HashMap<>();
        bounds.put("ROWCOUNT", new BigDecimal("4294967297"));
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                generator.execute(bounds);
            }
        });
        assertTrue(refused.getMessage().contains("cannot produce 4294967297 rows"), refused.getMessage());
    }

    @Test
    public void theFirstCountPastTheCapIsRefusedToo() {
        final Generator generator = new Generator();
        final Map<String, Object> bounds = new HashMap<>();
        bounds.put("ROWCOUNT", new BigDecimal("2147483648"));
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                generator.execute(bounds);
            }
        });
        assertTrue(refused.getMessage().contains("at most 2147483647 without a TIMELIMIT"), refused.getMessage());
    }

    @Test
    public void aCountIsReadWhole() {
        final Generator generator = new Generator();
        final Map<String, Object> bounds = new HashMap<>();
        bounds.put("ROWCOUNT", new BigDecimal("3.9"));
        assertEquals(3, generator.execute(bounds).getRows().size());
    }
}
