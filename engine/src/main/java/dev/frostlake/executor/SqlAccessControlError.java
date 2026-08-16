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
 * How Snowflake lays out an access-control refusal: {@code "SQL access control error:"}, a
 * newline, then the detail — the same two-line layout {@link SqlCompilationError} documents for
 * compilation errors. Live-verified on a real account: dropping an INFORMATION_SCHEMA view (under
 * DROP VIEW or DROP TABLE alike — the object is found before any kind check) answers
 * {@code SQL access control error:\nInsufficient privileges to operate on view 'TABLES'.}.
 */
public final class SqlAccessControlError {

    /** The prefix, without the separator that follows it. */
    public static final String PREFIX = "SQL access control error:";

    private SqlAccessControlError() {
    }

    /** The refusal for an operation the session may not perform on an object it can see. */
    public static String insufficientPrivileges(final String objectKind, final String objectName) {
        return PREFIX + "\nInsufficient privileges to operate on " + objectKind
            + " '" + objectName + "'.";
    }
}
