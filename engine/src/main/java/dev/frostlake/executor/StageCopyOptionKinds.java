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
package dev.frostlake.executor;

/** A stage's copy options, each with the kind of value it takes and its default, as DESC STAGE lists them. */
public final class StageCopyOptionKinds {

    /** The copy-option rows of DESC STAGE: property, type, default. */
    static final String[][] ALWAYS = {
        {"ON_ERROR", "String", "ABORT_STATEMENT"},
        {"SIZE_LIMIT", "Long", ""},
        {"PURGE", "Boolean", "false"},
        {"RETURN_FAILED_ONLY", "Boolean", "false"},
        {"ENFORCE_LENGTH", "Boolean", "true"},
        {"TRUNCATECOLUMNS", "Boolean", "false"},
        {"FORCE", "Boolean", "false"},
    };

    /** The copy options DESC STAGE lists only once they are set, after the others (live-verified). */
    static final String[][] WHEN_SET = {
        {"MATCH_BY_COLUMN_NAME", "String", "NONE"},
        {"LOAD_MODE", "String", "FULL_INGEST"},
    };

    private StageCopyOptionKinds() {
    }

    /**
     * The kind of value one of a stage's copy options takes: String, Long or Boolean.
     *
     * @param name the option
     * @return its kind, or null for no copy option DESC STAGE lists
     */
    public static String typeOf(final String name) {
        for (final String[] option : ALWAYS) {
            if (option[0].equalsIgnoreCase(name)) {
                return option[1];
            }
        }
        for (final String[] option : WHEN_SET) {
            if (option[0].equalsIgnoreCase(name)) {
                return option[1];
            }
        }
        return null;
    }
}
