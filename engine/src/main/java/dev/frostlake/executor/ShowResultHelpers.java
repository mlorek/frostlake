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
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Small stateless helpers shared by more than one SHOW/DESCRIBE sub-executor. Kept in one place so the
 * describe-result plumbing is not duplicated across the {@code Show*Executor} family.
 */
final class ShowResultHelpers {

    private ShowResultHelpers() {
    }

    /** Build a two-column (property, value) describe result. */
    static ResultSet propertyValueResult(final List<Row> rows) {
        return new ResultSet(Arrays.asList(
            new ResultSetColumn("property", StringType.VARCHAR),
            new ResultSetColumn("value", StringType.VARCHAR)), rows);
    }

    /** Current database.schema for describe lookups. */
    /**
     * The database that owns a {@code SHOW … IN SCHEMA <name>} reference.
     *
     * <p>The name may be qualified. Live accepts {@code SHOW STAGES IN SCHEMA other_db.public} from a
     * session pointed at a different database, and answers about {@code other_db} — so the database
     * cannot simply be the session's current one, which is what every one of these listings used to
     * assume. Doing so looked up the whole dotted string as a schema NAME and reported
     * {@code Schema 'CURRENT.OTHER_DB.PUBLIC' does not exist}.
     */
    static String scopeDatabase(final Catalog catalog, final String schemaReference) {
        if (schemaReference != null) {
            final QualifiedName qn = QualifiedName.parse(schemaReference);
            if (qn.size() == 2) {
                return qn.part(0);
            }
        }
        return catalog.getCurrentDatabase();
    }

    /** The bare schema name of a possibly-qualified reference, or the session's current schema. */
    static String scopeSchemaName(final Catalog catalog, final String schemaReference) {
        if (schemaReference != null) {
            final QualifiedName qn = QualifiedName.parse(schemaReference);
            return qn.size() == 2 ? qn.part(1) : qn.part(0);
        }
        return catalog.getCurrentSchema();
    }

    /**
     * The {@code IN ACCOUNT} form of a listing: every database's rows, in database order, under the
     * columns the per-database listing returned.
     *
     * <p>Live scopes these to the whole account rather than to the current schema — an account holding
     * one stage in the current schema and one elsewhere answers {@code SHOW STAGES IN ACCOUNT} with
     * both. Returns {@code null} for an account with no databases at all, which has no columns to
     * report; callers answer that with their unscoped listing.
     */
    static ResultSet acrossAllDatabases(final Catalog catalog, final DatabaseScopedListing listing) {
        List<ResultSetColumn> columns = null;
        final List<Row> rows = new ArrayList<>();
        for (final Database database : catalog.getAllDatabases()) {
            final ResultSet part = listing.listIn(database.getName());
            columns = part.getColumns();
            rows.addAll(part.getRows());
        }
        return columns != null ? new ResultSet(columns, rows) : null;
    }

    /**
     * The schema a SHOW scope names, for a kind whose miss live reports generically — see
     * {@link SqlCompilationError#objectDoesNotExist()} for which kinds those are. Every other kind
     * resolves the scope directly and lets the catalog name what was missing.
     */
    static Schema scopeSchemaReportedGenerically(final Catalog catalog, final String databaseName,
                                                 final String schemaName) {
        try {
            return catalog.getDatabase(databaseName).getSchema(schemaName);
        } catch (final RuntimeException missing) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
    }

    static Schema resolveDescribeSchema(final Catalog catalog) {
        return catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema());
    }

    /**
     * A SHOW-output text value: an absent one is the EMPTY STRING, never null. Measured on a real
     * account — an object with no comment reports "" from SHOW (INFORMATION_SCHEMA reports NULL for
     * the same thing), and the same holds for cluster_by, url, options and reserved. Columns that
     * are structurally inapplicable rather than merely unset — a stage's region, a tag's
     * allowed_values — do stay NULL there.
     */
    /** Every SHOW listing reports role ownership; the engine has no user-owned objects. */
    public static final String OWNER_ROLE_TYPE = "ROLE";

    public static String text(final String value) {
        return value == null ? "" : value;
    }

    /**
     * The owner_role_type that goes with {@code owner}. An object nobody owns — INFORMATION_SCHEMA
     * is the one the engine has — leaves BOTH columns empty rather than naming a role type for an
     * owner that is not there.
     */
    public static String ownerRoleType(final String owner) {
        return owner == null || owner.isEmpty() ? "" : OWNER_ROLE_TYPE;
    }

    /**
     * A SHOW timestamp column's VALUE. Every such column a real account answers with — {@code created_on}
     * in all of them, plus {@code resumed_on} / {@code updated_on} / {@code last_committed_on} /
     * {@code last_suspended_on} / {@code expires_at_time} / {@code locked_until_time} /
     * {@code started_on} — is declared TIMESTAMP_LTZ, so the cell is a temporal value rather than
     * pre-rendered text and the declared type does not lie about what is in it.
     *
     * <p>Three columns whose NAMES look temporal are measured VARCHAR and must stay text:
     * {@code retention_time} (a number of days), {@code granted_on} in SHOW GRANTS (the granted
     * object's TYPE, not a time), and the {@code mins_to_unlock} / {@code days_to_expiry} family.
     *
     * <p>{@code LocalDateTime} in the session zone, which is what the engine's other TIMESTAMP_LTZ
     * cells carry and what a live account's own TIMESTAMPLTZ arrives as over JDBC.
     */
    public static LocalDateTime createdOn(final Instant createdTime) {
        if (createdTime == null) {
            return null;
        }
        return createdTime.atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /** As {@link #createdOn(Instant)}, for the models that carry a local creation time already. */
    public static LocalDateTime createdOn(final LocalDateTime createdTime) {
        return createdTime;
    }
}
