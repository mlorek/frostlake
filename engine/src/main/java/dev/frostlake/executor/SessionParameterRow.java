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

/**
 * One row of {@link SessionParameterCatalog}: the parts of a session parameter that do not change with
 * the session. The value in force and the level are computed where the statement runs.
 */
public final class SessionParameterRow {

    private final String name;
    private final String defaultValue;
    private final String description;
    private final String type;

    SessionParameterRow(final String name, final String defaultValue, final String description,
                        final String type) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.description = description;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    /** The value a session that has not set it sees. */
    public String getDefaultValue() {
        return defaultValue;
    }

    public String getDescription() {
        return description;
    }

    /** BOOLEAN, STRING or NUMBER — the only three words a real account uses. */
    public String getType() {
        return type;
    }
}
