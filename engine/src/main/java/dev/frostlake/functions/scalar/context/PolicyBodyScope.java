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

package dev.frostlake.functions.scalar.context;

/**
 * Marks the thread as evaluating a POLICY BODY, for the functions that live only there.
 * {@code AGGREGATION_CONSTRAINT} is one: live answers
 * {@code The AGGREGATION_CONSTRAINT function can only be called from a body of an aggregation
 * constraint policy} to a bare call, while {@code NO_AGGREGATION_CONSTRAINT} and
 * {@code PROJECTION_CONSTRAINT} are callable anywhere.
 */
public final class PolicyBodyScope {

    private static final ThreadLocal<Boolean> INSIDE = new ThreadLocal<>();

    private PolicyBodyScope() {
    }

    public static boolean isInside() {
        return Boolean.TRUE.equals(INSIDE.get());
    }

    /** Enter the scope; the caller MUST leave it in a finally block. */
    public static void enter() {
        INSIDE.set(Boolean.TRUE);
    }

    public static void leave() {
        INSIDE.remove();
    }
}
