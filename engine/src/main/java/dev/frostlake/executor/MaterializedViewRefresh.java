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
import dev.frostlake.metastore.model.MaterializedView;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * How far a materialized view has caught up with the table it reads, as SHOW MATERIALIZED VIEWS reports it.
 *
 * <p>A real account maintains a materialized view in the background, and its listing describes the data the
 * view last materialized rather than the view's own history. {@code refreshed_on} and {@code compacted_on}
 * both name the source table's last write that the view has caught up with: the write before the view was
 * created, or the epoch when the table had never been written. {@code behind_by} is how much later the
 * table's newest write came, floored to whole seconds and spelled {@code 12s}, {@code 1m47s} or
 * {@code 497126h52m14s}, and {@code 0s} when nothing newer was written. Every INSERT, UPDATE, DELETE and
 * TRUNCATE counts as a write (live-verified). Maintenance kept a view there for minutes on end, and this
 * engine never refreshes one, so a view stays at the write it was created over. Its query results are
 * current regardless, as a real account's are.
 */
public final class MaterializedViewRefresh {

    private MaterializedViewRefresh() {
    }

    /**
     * When the named table was last written, or null when it never was, or when no such table exists any
     * more.
     */
    public static Instant lastWrite(final Catalog catalog, final String database, final String schema,
                                    final String table) {
        if (database == null || schema == null || table == null) {
            return null;
        }
        try {
            return catalog.resolveTable(QualifiedName.of(database, schema, table)).getLastDataChange();
        } catch (final RuntimeException gone) {
            return null;
        }
    }

    /** The listing's {@code refreshed_on} and {@code compacted_on}: the write the view caught up with. */
    static OffsetDateTime refreshedOn(final MaterializedView view) {
        return ShowResultHelpers.createdOn(view.getMaterializedAsOf() == null ? Instant.EPOCH : view.getMaterializedAsOf());
    }

    /** The listing's {@code behind_by}: how much later the source table's newest write is. */
    static String behindBy(final Catalog catalog, final MaterializedView view) {
        final Instant newest = lastWrite(catalog, view.getSourceDatabase(), view.getSourceSchema(), view.getSourceTable());
        final Instant caughtUp = view.getMaterializedAsOf() == null ? Instant.EPOCH : view.getMaterializedAsOf();
        if (newest == null || !newest.isAfter(caughtUp)) {
            return "0s";
        }
        final long seconds = Duration.between(caughtUp, newest).getSeconds();
        final long hours = seconds / 3600;
        final long minutes = seconds % 3600 / 60;
        final long rest = seconds % 60;
        if (hours > 0) {
            return hours + "h" + minutes + "m" + rest + "s";
        }
        if (minutes > 0) {
            return minutes + "m" + rest + "s";
        }
        return rest + "s";
    }
}
