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
 * The conditional branch the CURRENT statement's DDL handler took, reported by the handler itself:
 * the status sentence for {@code CREATE … IF NOT EXISTS} that found its object, and for
 * {@code DROP … IF EXISTS} that did not, depends on what the catalog held a moment BEFORE — a fact
 * only the handler's own skip branch knows. Held in a ThreadLocal and consumed per statement,
 * following {@link StatementClock}'s pattern: one engine serves many sessions, so this must not be
 * a process-wide setting.
 */
public final class ConditionalDdlOutcome {

    private static final ThreadLocal<ConditionalDdlBranch> TAKEN =
        new ThreadLocal<ConditionalDdlBranch>();

    private ConditionalDdlOutcome() {
    }

    /** Record that CREATE … IF NOT EXISTS found the object and created nothing. */
    public static void createSkipped() {
        TAKEN.set(ConditionalDdlBranch.CREATE_SKIPPED);
    }

    /** Record that CREATE OR ALTER TABLE found the table and altered it where it stands. */
    public static void alteredInPlace() {
        TAKEN.set(ConditionalDdlBranch.ALTERED_IN_PLACE);
    }

    /** Record that DROP … IF EXISTS found nothing to drop. */
    public static void dropSkipped() {
        TAKEN.set(ConditionalDdlBranch.DROP_SKIPPED);
    }

    /** Clear whatever an earlier statement may have left, before a statement runs. */
    public static void clear() {
        TAKEN.remove();
    }

    /** The branch the statement just executed took, consumed — null when it took neither. */
    public static ConditionalDdlBranch take() {
        final ConditionalDdlBranch taken = TAKEN.get();
        TAKEN.remove();
        return taken;
    }
}
