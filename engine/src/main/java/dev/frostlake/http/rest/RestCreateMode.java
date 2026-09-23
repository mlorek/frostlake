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

package dev.frostlake.http.rest;

/**
 * The {@code createMode} query parameter of the create endpoints, and the CREATE spelling each value selects:
 * {@code errorIfExists} (the default) a plain CREATE, {@code orReplace} CREATE OR REPLACE, {@code ifNotExists}
 * CREATE … IF NOT EXISTS.
 */
public enum RestCreateMode {

    /** A plain CREATE: an existing object of that name is a conflict. */
    ERROR_IF_EXISTS("errorIfExists"),
    /** CREATE OR REPLACE. */
    OR_REPLACE("orReplace"),
    /** CREATE … IF NOT EXISTS. */
    IF_NOT_EXISTS("ifNotExists");

    private final String parameterValue;

    RestCreateMode(final String parameterValue) {
        this.parameterValue = parameterValue;
    }

    /**
     * The mode a {@code createMode} value names; null selects the default.
     *
     * @throws RestException {@code 400} for a value the API does not define
     */
    public static RestCreateMode of(final String value) {
        if (value == null || value.isEmpty()) {
            return ERROR_IF_EXISTS;
        }
        for (final RestCreateMode mode : values()) {
            if (mode.parameterValue.equals(value)) {
                return mode;
            }
        }
        throw RestException.badRequest("Not a valid createMode: " + value
            + ". Allowed values are: errorIfExists,orReplace,ifNotExists");
    }

    /** {@code " OR REPLACE"} for {@link #OR_REPLACE}, else nothing — the text that follows {@code CREATE}. */
    public String orReplace() {
        return this == OR_REPLACE ? " OR REPLACE" : "";
    }

    /** {@code " IF NOT EXISTS"} for {@link #IF_NOT_EXISTS}, else nothing — the text that precedes the name. */
    public String ifNotExists() {
        return this == IF_NOT_EXISTS ? " IF NOT EXISTS" : "";
    }
}
