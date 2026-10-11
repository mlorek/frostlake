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

import dev.frostlake.metastore.Catalog;

/**
 * The parameters this engine's account sets at ACCOUNT level, as an administrator sets them on a real account: a
 * parameter listed here is in force wherever nothing below the account sets it, and SHOW PARAMETERS reports it at
 * level ACCOUNT, with the documented default beside it.
 *
 * <p>The one entry is TIMEZONE = UTC. A session that sets no zone runs in UTC here ({@link SessionZone}), where the
 * documented default is America/Los_Angeles; an account that sets UTC is how a real deployment arrives at the same
 * zone, and qa-develop does exactly that (live-verified: {@code SHOW PARAMETERS LIKE 'TIMEZONE'}, IN SESSION and IN
 * ACCOUNT alike, answers value UTC, default America/Los_Angeles, level ACCOUNT). ALTER ACCOUNT SET puts its own
 * values over it, and ALTER ACCOUNT UNSET TIMEZONE brings the entry back.
 */
public final class AccountParameters {

    private static final String TIMEZONE = "TIMEZONE";

    /** The zone the account sets: the zone {@link SessionZone} falls back to. */
    private static final String UTC = "UTC";

    private AccountParameters() {
    }

    /**
     * The value the account sets for a parameter.
     *
     * @param catalog the catalog that holds what ALTER ACCOUNT set, or null for none
     * @param name    the parameter's name, in any case
     * @return the value, or null when the account leaves the parameter at its default
     */
    public static String value(final Catalog catalog, final String name) {
        final String set = catalog == null ? null : catalog.getAccountParameters().get(name);
        if (set != null) {
            return set;
        }
        return TIMEZONE.equalsIgnoreCase(name) ? UTC : null;
    }
}
