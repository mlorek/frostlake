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
 * How a SHOW listing answers a scope naming an APPLICATION, a CLASS or a SERVICE instance, none of which
 * Frostlake models — live-verified per listing, and not predictable from the kind of object listed.
 */
enum ShowScopeAnswer {

    /**
     * {@code Unsupported statement type 'Cannot show objects of type <KIND> in <SCOPE>'.} — decided by the
     * statement's shape, before a LIMIT 0 or a WITH PRIVILEGES is judged.
     */
    CANNOT_SHOW,

    /** {@code Object does not exist, or operation cannot be performed.} — a lookup, judged after them. */
    OBJECT_DOES_NOT_EXIST,

    /** {@code Application '<NAME>' does not exist or not authorized.} — a lookup. */
    MISSING_APPLICATION,

    /** {@code Object type or Class '<NAME>' does not exist or not authorized.} — a lookup. */
    MISSING_CLASS,

    /** {@code Service '<DB>.<SCHEMA>.<NAME>' does not exist or not authorized.} — a lookup. */
    MISSING_SERVICE
}
