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
 * The modifiers a SHOW listing's own grammar takes after its scope, live-verified per listing: which of
 * {@code STARTS WITH}, {@code LIMIT} and {@code WITH PRIVILEGES} it reads. A modifier the grammar lacks is a
 * syntax error at its first word — except a WITH, which live reads on and refuses at the word after it.
 */
enum ShowTailGrammar {

    /** STARTS WITH, LIMIT and WITH PRIVILEGES — nearly every listing. */
    FULL(true, true, true),

    /** STARTS WITH and LIMIT, but no WITH PRIVILEGES: TASKS and ROLES. */
    NO_PRIVILEGES(true, true, false),

    /** LIMIT alone: ORGANIZATION ACCOUNTS. */
    LIMIT_ONLY(false, true, false),

    /** None of the three: LOCKS, TRANSACTIONS, VARIABLES and the KEYS listings. */
    NONE(false, false, false);

    private final boolean startsWith;
    private final boolean limit;
    private final boolean privileges;

    ShowTailGrammar(final boolean startsWith, final boolean limit, final boolean privileges) {
        this.startsWith = startsWith;
        this.limit = limit;
        this.privileges = privileges;
    }

    /** Whether the listing reads {@code STARTS WITH 'prefix'}. */
    boolean readsStartsWith() {
        return startsWith;
    }

    /** Whether the listing reads {@code LIMIT n [FROM 'name']}. */
    boolean readsLimit() {
        return limit;
    }

    /** Whether the listing reads {@code WITH PRIVILEGES p [, p]}. */
    boolean readsPrivileges() {
        return privileges;
    }
}
