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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code WITH PRIVILEGES} keeps only the objects the session's role holds one of those privileges on.
 *
 * <p>The modifier was read and then ignored, so a listing answered everything — which is the opposite
 * of what it is for. The account filters by the privilege NAMED and nothing else: owning a schema does
 * not give USAGE on it, so {@code SHOW SCHEMAS WITH PRIVILEGES USAGE} answers nothing over schemas the
 * role merely owns, while {@code SHOW DATABASES WITH PRIVILEGES OWNERSHIP} answers the owned ones.
 *
 * <p>Only the listings whose objects carry grants are filtered — databases, schemas, warehouses and
 * users. The rest refuse the modifier before they ever reach here.
 */
final class ShowPrivilegeFilter {

    private ShowPrivilegeFilter() {
    }

    /**
     * The listing with the rows the current role lacks the named privileges on removed.
     *
     * @param listed  the rows the listing built
     * @param ctx     the statement, for the privilege words it names
     * @param listing which listing this is
     * @param catalog the catalog holding the roles
     * @return the filtered listing, or the same one when the modifier was not written
     */
    static ResultSet apply(final ResultSet listed, final FrostlakeParser.ShowStatementContext ctx,
                           final ShowListing listing, final Catalog catalog) {
        final FrostlakeParser.ShowTailContext tail = ctx.showTail();
        if (tail == null || tail.PRIVILEGES() == null || !listing.filtersByPrivileges()) {
            return listed;
        }
        final String objectType = grantedKindOf(listing);
        if (objectType == null) {
            return listed;
        }
        final Role role = currentRole(catalog);
        if (role == null) {
            return listed;
        }
        final List<Privilege> wanted = new ArrayList<>();
        for (final FrostlakeParser.ShowPrivilegeContext named : tail.showPrivilege()) {
            final Privilege privilege = privilegeOf(writtenName(named));
            if (privilege != null) {
                wanted.add(privilege);
            }
        }
        if (wanted.isEmpty()) {
            return listed;
        }
        final boolean wantsOwnership = wanted.contains(Privilege.OWNERSHIP);
        final List<Row> kept = new ArrayList<>();
        for (final Row row : listed.getRows()) {
            final String name = grantedName(listed, row, listing);
            // OWNERSHIP is recorded on the OBJECT, not as a grant the role holds, so the owner's own
            // name answers for it — a role owning a database is listed by WITH PRIVILEGES OWNERSHIP.
            if (holdsAny(role, objectType, name, wanted)
                    || wantsOwnership && role.getName().equals(cell(listed, row, "owner"))) {
                kept.add(row);
            }
        }
        return new ResultSet(listed.getColumns(), kept);
    }

    /** Whether the role holds any of the named privileges on that object. */
    private static boolean holdsAny(final Role role, final String objectType, final String objectName,
                                    final List<Privilege> wanted) {
        if (objectName == null) {
            return false;
        }
        for (final Privilege privilege : wanted) {
            if (role.getPrivileges(objectType, objectName).contains(privilege)) {
                return true;
            }
        }
        return false;
    }

    /** The kind a grant on this listing's objects is recorded under, or null when they carry none. */
    private static String grantedKindOf(final ShowListing listing) {
        if (listing == ShowListing.DATABASES) {
            return "DATABASE";
        }
        if (listing == ShowListing.SCHEMAS) {
            return "SCHEMA";
        }
        if (listing == ShowListing.WAREHOUSES) {
            return "WAREHOUSE";
        }
        if (listing == ShowListing.USERS) {
            return "USER";
        }
        return null;
    }

    /** The name a grant on one listed object would be keyed by. */
    private static String grantedName(final ResultSet listed, final Row row, final ShowListing listing) {
        final String name = cell(listed, row, "name");
        if (name == null) {
            return null;
        }
        if (listing != ShowListing.SCHEMAS) {
            return name;
        }
        final String database = cell(listed, row, "database_name");
        return database == null ? name : QualifiedName.join(database, name);
    }

    /**
     * A privilege's name as the statement wrote it, its words joined the way a grant records them —
     * {@code CREATE TABLE} is CREATE_TABLE. Reading the whole context's text instead would run the
     * words together.
     */
    private static String writtenName(final FrostlakeParser.ShowPrivilegeContext named) {
        final StringBuilder name = new StringBuilder();
        for (int i = 0; i < named.getChildCount(); i++) {
            if (i > 0) {
                name.append('_');
            }
            name.append(named.getChild(i).getText());
        }
        return name.toString();
    }

    /** One named cell of a listed row, as text. */
    private static String cell(final ResultSet listed, final Row row, final String column) {
        final List<ResultSetColumn> columns = listed.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equalsIgnoreCase(column)) {
                final Object value = row.getValue(i);
                return value == null ? null : String.valueOf(value);
            }
        }
        return null;
    }

    /** The session's role, or null when there is none to ask about. */
    private static Role currentRole(final Catalog catalog) {
        final String name = catalog.currentRoleForOwner();
        return name == null ? null : catalog.getRole(name);
    }

    /** The privilege a written name stands for, or null when this engine does not model it. */
    private static Privilege privilegeOf(final String written) {
        final String name = written.toUpperCase().replace(' ', '_');
        for (final Privilege privilege : Privilege.values()) {
            if (privilege.name().equals(name)) {
                return privilege;
            }
        }
        return null;
    }
}
