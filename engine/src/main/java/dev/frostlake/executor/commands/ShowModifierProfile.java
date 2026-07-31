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

import dev.frostlake.parser.FrostlakeParser;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What the TERSE / STARTS WITH / LIMIT modifiers actually do to one SHOW listing.
 *
 * <p>They do not do the same thing to all of them, and the differences are not guessable — this class
 * is a transcription of what a real account answered on listing by listing, and the
 * per-listing branches below each cite the reply that pins them.
 *
 * <p>Three facts drive the shape of this class:
 *
 * <ul>
 *   <li><b>STARTS WITH and LIMIT are accepted far more widely than they are honoured.</b> Snowflake
 *       parses both on nearly every listing and then silently ignores them on seven or eight of them —
 *       {@code SHOW STAGES STARTS WITH 'ZZZ'} returns both stages, {@code SHOW SEQUENCES LIMIT 1}
 *       returns both sequences, {@code SHOW FUNCTIONS LIMIT 2} returns all 1136 rows. Frostlake has to
 *       ignore them in exactly the same places, so the two booleans here are per-listing.</li>
 *   <li><b>The honour sets for the two modifiers are not the same set.</b> TAGS honours LIMIT
 *       ({@code SHOW TAGS LIMIT 1} → 1 of 2) but ignores STARTS WITH
 *       ({@code SHOW TAGS STARTS WITH 'ZZZ'} → both). Nothing about the object type predicts it.</li>
 *   <li><b>TERSE is not one projection.</b> It trims to five columns on TABLES/VIEWS/SCHEMAS/DATABASES/
 *       OBJECTS, to those five plus a sixth on STREAMS ({@code tableOn}) and TASKS ({@code schedule}),
 *       to an unrelated subset on ROLES and USERS, and to nothing at all — the full listing, unchanged —
 *       on the dozen others that still accept the keyword.</li>
 * </ul>
 *
 * <p>The TERSE column list is a target shape, not a filter: {@link #terseColumns()} names the columns
 * the row must end up with, in order, and the projection fills each from the source listing when it has
 * a column of that name and with null when it does not. That keeps the column contract right even where
 * Frostlake's underlying listing models less than a real account's does.
 */
public final class ShowModifierProfile {

    /**
     * The TERSE shape shared by the object listings: five columns, in this order, regardless of what the
     * untrimmed listing looks like. Live-verified: {@code SHOW TABLES} answers 27 columns and
     * {@code SHOW TERSE TABLES} these 5; {@code SHOW VIEWS} answers 12 columns and none of them is
     * {@code kind}, yet {@code SHOW TERSE VIEWS} has it (VIEW / MATERIALIZED_VIEW); {@code SHOW SCHEMAS}
     * has neither {@code kind} nor {@code schema_name} and TERSE reports both as null;
     * {@code SHOW DATABASES} has no {@code database_name}/{@code schema_name} and TERSE nulls them while
     * filling {@code kind} with STANDARD.
     */
    private static final List<String> STANDARD_TERSE =
        Arrays.asList("created_on", "name", "kind", "database_name", "schema_name");

    /** TERSE is accepted but inert — the listing comes back whole. */
    private static final List<String> NO_TERSE = Collections.emptyList();

    private final boolean honorsStartsWith;
    private final boolean honorsLimit;
    private final List<String> terseColumns;
    private final String terseKind;
    private final boolean terseKindFromMaterializedFlag;

    private final boolean sortsByName;

    private ShowModifierProfile(final boolean honorsStartsWith, final boolean honorsLimit,
                                final List<String> terseColumns, final String terseKind,
                                final boolean terseKindFromMaterializedFlag, final boolean sortsByName) {
        this.honorsStartsWith = honorsStartsWith;
        this.honorsLimit = honorsLimit;
        this.terseColumns = terseColumns;
        this.terseKind = terseKind;
        this.terseKindFromMaterializedFlag = terseKindFromMaterializedFlag;
        this.sortsByName = sortsByName;
    }

    /**
     * An object listing: one that a real account returns ordered by name, byte-wise.
     *
     * <p>{@code SHOW TABLES} over DT_A, T_A…T_D and a quoted "t_lower" answers in exactly that order,
     * with the lowercase name last, and every other object listing probed behaved the same — including
     * the four that go on to ignore the pagination suffix entirely. The ordering is what gives
     * {@code LIMIT n} and {@code LIMIT n FROM 'x'} a meaning at all: "the first n by name" and "those
     * sorting strictly after x". Frostlake did not sort, so its LIMIT returned whichever n rows the
     * catalog happened to hold first.
     */
    private static ShowModifierProfile sorted(final boolean honorsStartsWith, final boolean honorsLimit,
                                              final List<String> terseColumns, final String terseKind,
                                              final boolean terseKindFromMaterializedFlag) {
        return new ShowModifierProfile(honorsStartsWith, honorsLimit, terseColumns, terseKind,
            terseKindFromMaterializedFlag, true);
    }

    /**
     * A listing left in whatever order it is produced, because its live ordering was not established.
     *
     * <p>COLUMNS groups by table rather than sorting on {@code column_name}, KEYS likewise, and
     * SHOW FUNCTIONS interleaves 1134 built-ins with the user functions in an order this engine has no
     * reason to reproduce. None of them honours LIMIT, so nothing depends on the order being defined.
     */
    private static ShowModifierProfile unsorted() {
        return new ShowModifierProfile(false, false, NO_TERSE, null, false, false);
    }

    /** Whether the listing is returned ordered by name — see {@link #sorted}. */
    public boolean sortsByName() {
        return sortsByName;
    }

    /** Whether {@code STARTS WITH 'prefix'} filters this listing or is parsed and dropped. */
    public boolean honorsStartsWith() {
        return honorsStartsWith;
    }

    /** Whether {@code LIMIT n [FROM 'name']} paginates this listing or is parsed and dropped. */
    public boolean honorsLimit() {
        return honorsLimit;
    }

    /** The columns TERSE projects onto, in order; empty when TERSE leaves the listing alone. */
    public List<String> terseColumns() {
        return terseColumns;
    }

    /** The literal {@code kind} TERSE reports when the untrimmed listing has no such column. */
    public String terseKind() {
        return terseKind;
    }

    /** Whether {@code kind} is MATERIALIZED_VIEW / VIEW per row, read off {@code is_materialized}. */
    public boolean terseKindFromMaterializedFlag() {
        return terseKindFromMaterializedFlag;
    }

    /**
     * The profile for one parsed SHOW statement.
     *
     * <p>The order of the tests mirrors {@code ShowCommandHandler}: the compound listings (MATERIALIZED
     * VIEWS, DYNAMIC TABLES, FILE FORMATS, the two POLICIES) have to be recognised before the bare
     * keyword they contain, or {@code SHOW MATERIALIZED VIEWS} would be answered as VIEWS.
     */
    public static ShowModifierProfile forStatement(final FrostlakeParser.ShowStatementContext ctx) {
        if (ctx.MATERIALIZED() != null && ctx.VIEWS() != null) {
            // LIMIT 1 → 1 of 2 materialized views; STARTS WITH 'ZZZ' → none. Both honoured, TERSE inert
            // (22 columns with and without).
            return sorted(true, true, NO_TERSE, null, false);
        }
        if (ctx.DYNAMIC() != null && ctx.TABLES() != null) {
            // STARTS WITH 'ZZZ' → none. TERSE inert (31 columns either way).
            return sorted(true, true, NO_TERSE, null, false);
        }
        if (ctx.FILE() != null && ctx.FORMATS() != null) {
            // STARTS WITH 'ZZZ' and LIMIT 1 both returned both file formats — parsed, ignored.
            return sorted(false, false, NO_TERSE, null, false);
        }
        if (ctx.MASKING() != null && ctx.POLICIES() != null) {
            // LIMIT 1 → 1 of 2 masking policies; STARTS WITH 'ZZZ' → none.
            return sorted(true, true, NO_TERSE, null, false);
        }
        if (ctx.ROW() != null && ctx.POLICIES() != null) {
            return sorted(true, true, NO_TERSE, null, false);
        }
        if (ctx.TABLES() != null) {
            // 6 tables, LIMIT 2 → the first 2 by name; STARTS WITH 'T' → the 4 uppercase ones, excluding
            // the quoted "t_lower" that STARTS WITH 't' finds instead. 27 columns → 5.
            return sorted(true, true, STANDARD_TERSE, "TABLE", false);
        }
        if (ctx.VIEWS() != null) {
            // STARTS WITH 'V' → the 2 views, not the materialized one. TERSE reports kind per row.
            return sorted(true, true, STANDARD_TERSE, "VIEW", true);
        }
        if (ctx.SCHEMAS() != null) {
            // STARTS WITH 'S' → S1/S2, excluding PUBLIC and INFORMATION_SCHEMA. TERSE kind is null.
            return sorted(true, true, STANDARD_TERSE, null, false);
        }
        if (ctx.DATABASES() != null) {
            // STARTS WITH 'ZZZ' → none; LIMIT 2 → 2 of 7. TERSE kind is the literal STANDARD.
            return sorted(true, true, STANDARD_TERSE, "STANDARD", false);
        }
        if (ctx.OBJECTS() != null) {
            // STARTS WITH 'T' → 4 of 9. 16 columns → 5.
            return sorted(true, true, STANDARD_TERSE, null, false);
        }
        if (ctx.STREAMS() != null) {
            // STARTS WITH 'ZZZ' → none, LIMIT 1 → 1 of 2. TERSE adds a sixth column, tableOn, which
            // carries what the untrimmed listing calls table_name; kind reads DELTA.
            return sorted(true, true, terseWithExtra("tableOn"), "DELTA", false);
        }
        if (ctx.TASKS() != null) {
            // Same six-column shape, the extra one being schedule; kind is null.
            return sorted(true, true, terseWithExtra("schedule"), null, false);
        }
        if (ctx.ROLES() != null) {
            // LIMIT 1 → 1 of 7, STARTS WITH 'A' → ACCOUNTADMIN alone. TERSE keeps an unrelated subset:
            // no created_on, no kind, just the five identity flags.
            return sorted(true, true, Arrays.asList(
                "name", "is_default", "is_current", "is_inherited",
                "is_from_organization_user_group"), null, false);
        }
        if (ctx.USERS() != null) {
            // STARTS WITH 'ZZZ' → none. TERSE trims 31 columns to these 14 — again its own subset,
            // leading with name rather than created_on.
            return sorted(true, true, Arrays.asList(
                "name", "created_on", "display_name", "first_name", "last_name", "email", "comment",
                "has_password", "has_rsa_public_key", "type", "has_mfa", "has_pat",
                "has_workload_identity", "is_from_organization_user"), null, false);
        }
        if (ctx.TAGS() != null) {
            // The asymmetric one: LIMIT 1 returned 1 of 2 tags, STARTS WITH 'ZZZ' returned both.
            return sorted(false, true, NO_TERSE, null, false);
        }
        if (ctx.PIPES() != null) {
            // Both dropped: with two pipes over internal stages, LIMIT 1 returned both and
            // STARTS WITH 'ZZZ' returned both. Worth stating that this one was measured rather than
            // inferred — the first pass here assumed PIPES filtered, on the reasoning that it is a
            // schema-level object listing like STREAMS and TASKS, and the account said otherwise.
            return sorted(false, false, NO_TERSE, null, false);
        }
        if (ctx.STAGES() != null) {
            // STARTS WITH 'ZZZ' and LIMIT 1 both returned both stages — parsed, ignored.
            return sorted(false, false, NO_TERSE, null, false);
        }
        if (ctx.SEQUENCES() != null) {
            // Likewise: both sequences came back from STARTS WITH 'ZZZ' and from LIMIT 1.
            return sorted(false, false, NO_TERSE, null, false);
        }
        if (ctx.WAREHOUSES() != null) {
            // STARTS WITH 'ZZZ' returned all four warehouses and LIMIT 1 all four, though LIKE 'ZZZ%'
            // correctly returned none — so it is these two modifiers that are dropped, not filtering.
            return sorted(false, false, NO_TERSE, null, false);
        }
        if (ctx.COLUMNS() != null) {
            // STARTS WITH 'I' returned all 10 columns although only 9 are named ID; LIMIT 2 returned 10.
            // TERSE is inert too — 13 columns with and without, so the old 3-column projection was wrong.
            return unsorted();
        }
        if (ctx.FUNCTIONS() != null || ctx.PROCEDURES() != null) {
            // SHOW FUNCTIONS STARTS WITH 'FN' → all 1136 rows, LIMIT 2 → all 1136; SHOW PROCEDURES
            // STARTS WITH 'PR' → all 34, and SHOW BUILTIN PROCEDURES LIMIT 3 → all 32. TERSE is inert
            // here as well (20 and 16 columns respectively, with or without it).
            return unsorted();
        }
        // Everything else — KEYS above all, which rejects the suffix outright and takes TERSE as a no-op.
        return unsorted();
    }

    /** The five standard TERSE columns plus one listing-specific extra, appended last as Snowflake does. */
    private static List<String> terseWithExtra(final String extra) {
        return Arrays.asList("created_on", "name", "kind", "database_name", "schema_name", extra);
    }
}
