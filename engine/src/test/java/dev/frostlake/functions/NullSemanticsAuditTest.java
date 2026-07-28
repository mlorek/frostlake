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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NULL-semantics convention audit: Snowflake scalar functions return NULL when any argument is NULL,
 * except the functions whose PURPOSE is null handling (EQUAL_NULL, ZEROIFNULL, …), value constructors
 * (ARRAY_CONSTRUCT, OBJECT_CONSTRUCT, …) and generators (HASH, RANDOM, …). Every registered scalar
 * function is invoked with all-NULL arguments and must return NULL — unless whitelisted below, or
 * whitelisted as legitimately throwing (a required identifier argument). This caught a family that
 * returned 0/FALSE instead of NULL (CHARINDEX, POSITION, INSTR, CONTAINS, STARTSWITH, ENDSWITH,
 * REGEXP_COUNT, REGEXP_INSTR, ARRAY_CONTAINS, the IS_* type predicates), TYPEOF returning the text
 * "NULL", and NPEs in the *_FROM_PARTS trio.
 */
public class NullSemanticsAuditTest extends BaseDatabaseTest {

    /** Functions that legitimately return a non-NULL value for all-NULL arguments (Snowflake-verified). */
    private static final Set<String> NON_NULL_WHITELIST = new HashSet<>(Arrays.asList(
        "ARRAY_CONSTRUCT",              // builds [null]
        "ARRAY_CONSTRUCT_COMPACT",      // builds [] (drops NULLs by definition)
        "OBJECT_CONSTRUCT",             // {} — a NULL key omits the pair
        "OBJECT_CONSTRUCT_KEEP_NULL",   // {} — a NULL key still omits the pair (only NULL values kept)
        "EQUAL_NULL",                   // NULL-safe equality: EQUAL_NULL(NULL, NULL) is TRUE
        "ZEROIFNULL",                   // 0 by definition
        "DIV0NULL",                     // 0 when the divisor is NULL by definition
        "HASH",                         // hashes NULL to a number; never returns NULL
        "GROUPING", "GROUPING_ID",      // engine-special super-group markers
        "LAST_QUERY_ID", "RANDOM", "NORMAL", "UUID_STRING"   // context / generators
    ));

    /** Functions allowed to THROW on a NULL argument (a required identifier/name argument). */
    private static final Set<String> THROW_WHITELIST = new HashSet<>(Arrays.asList(
        "GET_DDL", "NEXTVAL", "CURRVAL"
    ));

    @Test
    public void everyScalarFunctionPropagatesNullOrIsWhitelisted() {
        final Set<String> offenders = new TreeSet<>();
        for (final BuiltInFunction fn : engine.getFunctionRegistry().getAllFunctions()) {
            if (fn.getMinArgCount() == 0 && fn.getMaxArgCount() == 0) {
                continue;   // niladic (CURRENT_*, …): nothing to pass NULL into
            }
            final int argCount = Math.max(fn.getMinArgCount(), 1);
            if (argCount > 6) {
                continue;
            }
            final List<Object> nulls = new ArrayList<>();
            for (int i = 0; i < argCount; i++) {
                nulls.add(null);
            }
            try {
                final Object result = fn.evaluate(nulls);
                if (result != null && !NON_NULL_WHITELIST.contains(fn.getName())) {
                    offenders.add(fn.getName() + " -> " + result);
                }
            } catch (final Exception e) {
                if (!THROW_WHITELIST.contains(fn.getName())) {
                    offenders.add(fn.getName() + " !! " + e.getClass().getSimpleName());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
            "Functions violating NULL-in-NULL-out (add to a whitelist ONLY with Snowflake doc evidence): " + offenders);
    }
}
