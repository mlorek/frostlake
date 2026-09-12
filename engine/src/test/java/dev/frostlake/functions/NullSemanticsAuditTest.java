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

import java.util.Arrays;
import java.util.HashSet;
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
 *
 * <p>Deliberately NOT on the live surface: it drives every registered function object directly
 * through the registry — engine internals a live run cannot reach — so it runs its own embedded
 * engine.
 */
public class NullSemanticsAuditTest extends BaseDatabaseTest {

    /** Functions that legitimately return a non-NULL value for all-NULL arguments (Snowflake-verified). */
    private static final Set<String> NON_NULL_WHITELIST = new HashSet<>(Arrays.asList(
        "ARRAY_CONSTRUCT",              // builds [null]
        "ARRAY_CONSTRUCT_COMPACT",      // builds [] (drops NULLs by definition)
        // {} — the MAP constructor, same rule as its OBJECT twin. Live-measured over a
        // two-row table (so no all-NULL column could be folded to a NULL literal): a NULL KEY drops the
        // whole pair, so an all-NULL call is the EMPTY map rather than NULL. The rest of the MAP family
        // does propagate NULL and is deliberately absent from this list.
        "MAP_CONSTRUCT",
        "EQUAL_NULL",                   // NULL-safe equality: EQUAL_NULL(NULL, NULL) is TRUE
        "ZEROIFNULL",                   // 0 by definition
        "DIV0NULL",                     // 0 when the divisor is NULL by definition
        "HASH",                         // hashes NULL to a number; never returns NULL
        "GROUPING", "GROUPING_ID",      // engine-special super-group markers
        "LAST_QUERY_ID", "RANDOM", "NORMAL", "UUID_STRING",  // context / generators
        // Context functions with an OPTIONAL precision argument: the argument never gates the
        // result, so a NULL precision still yields the current value (Snowflake signature
        // CURRENT_TIMESTAMP([fract_sec_precision])).
        "CURRENT_TIME", "CURRENT_TIMESTAMP",
        // The FILE classifiers. Live-measured, NOT inferred: over a NULL file —
        // both a literal NULL and a NULL FILE column — FL_GET_FILE_TYPE returns the STRING 'unknown'
        // and every FL_IS_* returns FALSE. The eight plain FL_GET_* accessors DO propagate NULL and are
        // deliberately absent from this list, which is exactly the split the account shows.
        "FL_GET_FILE_TYPE",
        "FL_IS_AUDIO", "FL_IS_COMPRESSED", "FL_IS_DOCUMENT", "FL_IS_IMAGE", "FL_IS_VIDEO"
    ));

    /** Functions allowed to THROW on a NULL argument (a required identifier/name argument). */
    private static final Set<String> THROW_WHITELIST = new HashSet<>(Arrays.asList(
        "GET_DDL", "NEXTVAL", "CURRVAL",
        // Catalog-only entry: the call shape TRY_CAST(x, ...) is a live syntax error (only
        // TRY_CAST(expr AS type) exists, and it parses as a cast expression), so the registered
        // function exists solely for SHOW FUNCTIONS and refuses direct evaluation.
        "TRY_CAST",
        // COLLATE's specification must be WRITTEN as a string literal, and a NULL is none: live refuses
        // COLLATE(NULL, NULL) with "Argument number 2 for function 'COLLATE' needs to be a string
        // literal." (measured). A NULL operand beside a written specification is NULL.
        "COLLATE",
        // The SEQ family's optional argument is a SIGN, and live refuses a NULL one outright rather
        // than folding the row's ordinal away: "Invalid parameter value: NULL. Reason: sign must not
        // be NULL". Measured, not inferred — and it is the same refusal for all four widths.
        "SEQ1", "SEQ2", "SEQ4", "SEQ8",
        // Catalog-only entry like TRY_CAST: the call shape CAST(x, ...) is not SQL — only
        // CAST(expr AS type) exists, parsed as the cast construct.
        "CAST",
        // One NULL is an ODD argument count, refused for its arity before any key is judged (live:
        // "invalid number of arguments for [OBJECT_CONSTRUCT], expected 2, got 1"); an even count of
        // NULLs is {}, since a NULL key omits its pair.
        "OBJECT_CONSTRUCT", "OBJECT_CONSTRUCT_KEEP_NULL",
        // The strict-argument families refuse a bare NULL literal by MEASURED design, and their
        // NULL cells are pinned in their own live-verified classes (map/typed-value/vector
        // strictness) — this audit only confirms they refuse rather than mis-propagate.
        "MAP_CAT", "MAP_CONSTRUCT", "MAP_CONTAINS_KEY", "MAP_DELETE", "MAP_ENTRIES",
        "MAP_INSERT", "MAP_KEYS", "MAP_PICK", "MAP_SIZE",
        "TRY_TO_BINARY", "TRY_TO_BOOLEAN", "TRY_TO_DATE", "TRY_TO_DOUBLE", "TRY_TO_NUMBER",
        "TRY_TO_TIME", "TRY_TO_TIMESTAMP", "TRY_TO_TIMESTAMP_NTZ",
        // The LTZ and TZ twins refuse an untyped NULL exactly as the NTZ spelling does — live,
        // TRY_TO_TIMESTAMP_LTZ(NULL) is "Function TRY_CAST cannot be used with arguments of types
        // NULL and TIMESTAMP_LTZ(9)" (measured beside the rest of the TRY_TO_* matrix).
        "TRY_TO_TIMESTAMP_LTZ", "TRY_TO_TIMESTAMP_TZ",
        // TRY_TO_UUID is the same TRY_CAST: live refuses TRY_TO_UUID(NULL) as "Function TRY_CAST cannot
        // be used with arguments of types NULL and UUID".
        "TRY_TO_UUID",
        "VECTOR_COSINE_SIMILARITY", "VECTOR_INNER_PRODUCT", "VECTOR_L1_DISTANCE",
        "VECTOR_L2_DISTANCE", "VECTOR_NORMALIZE", "VECTOR_TRUNC",
        // A projection policy's verdict takes a NAMED argument and nothing else: live refuses the
        // positional call this sweep makes, and refuses ALLOW => NULL as well, so there is no NULL
        // answer to propagate. Both measured.
        "PROJECTION_CONSTRAINT",
        // An aggregation policy's verdict may not be called outside a policy body at all — live
        // answers that refusal to any bare call, NULL argument or not (measured).
        "AGGREGATION_CONSTRAINT",
        // A join policy's verdict refuses a NULL argument outright rather than folding the call
        // away, in both the named and the positional spelling (measured).
        "JOIN_CONSTRAINT"
    ));

    /**
     * The sweep runs as SQL — {@code SELECT FN(NULL, …)} per registered scalar — so the live switch
     * audits the same propagation claims against the account: an offender is a non-NULL answer
     * outside the whitelist. A THROW is judged embedded (the whitelist is exact there); on a live
     * run a refusal only skips the cell — strictness and existence differences are their own test
     * domains, and an engine-specific FL_* helper simply does not exist on the account.
     */
    @Test
    public void everyScalarFunctionPropagatesNullOrIsWhitelisted() {
        final Set<String> offenders = new TreeSet<>();
        int skipped = 0;
        for (final BuiltInFunction fn : engine.getFunctionRegistry().getAllFunctions()) {
            if (fn.getMinArgCount() == 0 && fn.getMaxArgCount() == 0) {
                continue;   // niladic (CURRENT_*, …): nothing to pass NULL into
            }
            final int argCount = Math.max(fn.getMinArgCount(), 1);
            if (argCount > 6 || !fn.getName().matches("[A-Z_][A-Z0-9_$]*")) {
                continue;   // operator-shaped registry names are not call-shaped SQL
            }
            final StringBuilder call = new StringBuilder(fn.getName()).append("(");
            for (int i = 0; i < argCount; i++) {
                if (i > 0) {
                    call.append(", ");
                }
                call.append("NULL");
            }
            call.append(")");
            try {
                final Object result = engine.executeQuery("SELECT " + call)
                    .getRows().get(0).getValue(0);
                if (result != null && !NON_NULL_WHITELIST.contains(fn.getName())) {
                    offenders.add(fn.getName() + " -> " + result);
                }
            } catch (final RuntimeException e) {
                if (isLiveSnowflake()) {
                    skipped++;   // unknown-to-live name or live strictness — not this audit's cell
                } else if (!THROW_WHITELIST.contains(fn.getName())) {
                    offenders.add(fn.getName() + " !! " + e.getClass().getSimpleName());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
            "Functions violating NULL-in-NULL-out (add to a whitelist ONLY with Snowflake doc evidence; "
                + skipped + " cells skipped): " + offenders);
    }
}
