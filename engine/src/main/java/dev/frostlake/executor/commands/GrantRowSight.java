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

import dev.frostlake.executor.GrantedObject;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.GrantNames;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What of the account's grants a SHOW GRANTS shows the session: grants on objects it sees, a role among them that it
 * holds or owns (see {@link SecurityManager#sees} and {@link SecurityManager#seesRole}), every one under MANAGE GRANTS
 * (documented: "The command only returns objects for which the current user's current role has been granted at least
 * one access privilege. The MANAGE GRANTS access privilege implicitly allows its holder to see every object in the
 * account"), and so does the SYSTEM user, which holds every privilege.
 */
final class GrantRowSight {

    private GrantRowSight() {
    }

    /**
     * Refuses SHOW GRANTS ON an object the session does not see as one that is not there, as DESCRIBE and GET_DDL
     * refuse it.
     *
     * @param security the session's rights, or null when none are kept
     * @param granted  the object, or null when its kind records no owner here
     */
    static void requireSeen(final SecurityManager security, final GrantedObject granted) {
        if (security == null || granted == null || granted.getOwner() == null
                || security.holdsAccountPrivilege(Privilege.MANAGE_GRANTS)) {
            return;
        }
        if (!security.sees(granted.getKind(), granted.getRecordedName(), granted.getOwner())) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(kindWord(granted.getKind()),
                granted.getRecordedName()));
        }
    }

    /**
     * The rows of a grants listing on what the session sees: each row names its object by a kind and a name.
     *
     * @param listing    the listing
     * @param catalog    the catalog, for each object's owner
     * @param security   the session's rights, or null when none are kept
     * @param kindColumn the column naming each row's kind, or -1 when every row is on a role
     * @param nameColumn the column naming each row's object
     * @return the listing with only those rows
     */
    static ResultSet seen(final ResultSet listing, final Catalog catalog, final SecurityManager security,
                          final int kindColumn, final int nameColumn) {
        if (security == null || security.holdsAccountPrivilege(Privilege.MANAGE_GRANTS)) {
            return listing;
        }
        final List<Row> kept = new ArrayList<>();
        for (final Row row : listing.getRows()) {
            final String kind = kindColumn < 0 ? "ROLE" : String.valueOf(row.getValue(kindColumn));
            final Object name = row.getValue(nameColumn);
            if (name == null || sees(catalog, security, kind, name.toString())) {
                kept.add(row);
            }
        }
        return new ResultSet(listing.getColumns(), kept);
    }

    /** Whether the session sees an object a grants row names, by its kind and its name as the row spells it. */
    private static boolean sees(final Catalog catalog, final SecurityManager security, final String kind,
                                final String spelled) {
        if ("ROLE".equals(kind)) {
            return security.seesRole(spelled);
        }
        if ("ACCOUNT".equals(kind)) {
            return true;
        }
        final String recorded = GrantNames.of(catalog, kind, spelled);
        return security.sees(kind, recorded, catalog.getObjectOwnerRole(kind, recorded));
    }

    /** A kind as a refusal words it: TABLE is Table, DYNAMIC_TABLE Dynamic table. */
    private static String kindWord(final String kind) {
        final String words = kind.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
