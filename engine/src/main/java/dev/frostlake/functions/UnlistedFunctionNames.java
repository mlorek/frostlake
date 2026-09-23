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

import java.util.Arrays;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Built-in functions the account dispatches but does not list: {@code SHOW FUNCTIONS} answers no row for them (the
 * {@code LIKE} pattern naming each one exactly returns nothing, live-verified), so the listing leaves them out while
 * every call resolves. They are the six functions whose first argument names a stage.
 */
public final class UnlistedFunctionNames {

    private static final SortedSet<String> NAMES = Collections.unmodifiableSortedSet(
        new TreeSet<String>(Arrays.asList(
            "BUILD_SCOPED_FILE_URL",
            "BUILD_STAGE_FILE_URL",
            "GET_ABSOLUTE_PATH",
            "GET_PRESIGNED_URL",
            "GET_RELATIVE_PATH",
            "GET_STAGE_LOCATION"
        )));

    private UnlistedFunctionNames() {
    }

    /** Whether SHOW FUNCTIONS leaves this built-in out of its listing. */
    public static boolean contains(final String name) {
        return name != null && NAMES.contains(name.toUpperCase());
    }

    /** Every unlisted built-in's name, upper-cased and sorted. */
    public static SortedSet<String> names() {
        return NAMES;
    }
}
