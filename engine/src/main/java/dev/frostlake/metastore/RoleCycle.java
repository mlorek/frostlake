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

package dev.frostlake.metastore;

/**
 * The refusal of a role grant that would close a loop in the role hierarchy, an account role's and a database role's
 * alike: {@code SQL execution error: Circular role grants not allowed. Granting role TEST_DB.DR_C3 to TEST_DB.DR_C1
 * would create a cycle.}, the granted role first, each as a sentence spells it (live-verified for database roles).
 */
public final class RoleCycle {

    private RoleCycle() {
    }

    /**
     * The refusal's text.
     *
     * @param granted the role the statement grants, as a sentence spells it
     * @param holder  the role that would hold it, as a sentence spells it
     * @return the text
     */
    public static String refusal(final String granted, final String holder) {
        return "SQL execution error: Circular role grants not allowed. Granting role " + granted + " to " + holder
            + " would create a cycle.";
    }
}
