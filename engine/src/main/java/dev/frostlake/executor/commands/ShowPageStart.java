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

package dev.frostlake.executor.commands;

/**
 * Where a listing's {@code LIMIT n FROM 'name'} starts (live-verified, listing by listing). The listings read the
 * name three ways, which tell apart only when a listing spans schemas, whose rows come schema by schema.
 *
 * <pre>
 *   AFTER_NAME    TABLES, VIEWS, SCHEMAS, DATABASES, ROLES, …   the rows whose name sorts after it
 *   FROM_NAME     TASKS                                         the rows whose name sorts at or after it
 *   AT_NAME       STREAMS, TAGS, the policies, NETWORK RULES    the first row whose name sorts at or after it,
 *                                                               and every row the listing holds after that one
 * </pre>
 */
public enum ShowPageStart {
    /** The rows whose name sorts strictly after the name. */
    AFTER_NAME,
    /** The rows whose name sorts at or after the name. */
    FROM_NAME,
    /** The first row whose name sorts at or after the name, and every row after it. */
    AT_NAME;

    /**
     * Whether a row is kept.
     *
     * @param rowName the row's name, or null
     * @param name    the name the page starts from
     * @param started whether an earlier row already started the page
     * @return true when the row belongs to the page
     */
    public boolean keeps(final String rowName, final String name, final boolean started) {
        if (rowName == null) {
            return this == AT_NAME && started;
        }
        final int order = rowName.compareTo(name);
        if (this == AFTER_NAME) {
            return order > 0;
        }
        return order >= 0 || this == AT_NAME && started;
    }
}
