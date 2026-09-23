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

/**
 * The object listings SHOW takes a scope and modifiers for, each with the facts live answers them by — all
 * live-verified, listing by listing, since none of them follows from the kind of object listed:
 *
 * <ul>
 *   <li>the object kind a {@code Cannot show objects of type <KIND> in <SCOPE>} sentence names;</li>
 *   <li>the name an {@code Unsupported feature 'SHOW <NAME> ... WITH PRIVILEGES <>'.} sentence gives the listing,
 *       live's own internal spelling ({@code MASKING_POLICYS}, {@code COMPUTE_POOL_INSTANCE_FAMILIESS});</li>
 *   <li>which modifiers its grammar reads ({@link ShowTailGrammar}), and whether WITH PRIVILEGES filters it or is
 *       refused as unsupported;</li>
 *   <li>how it answers a scope naming an APPLICATION, a CLASS or a SERVICE instance ({@link ShowScopeAnswer});</li>
 *   <li>whether it lists account-level things — databases, warehouses, users, roles, compute pools, locks and
 *       transactions — which only an {@code IN ACCOUNT} scope lists.</li>
 * </ul>
 */
enum ShowListing {

    DATABASES("DATABASE", "DATABASES", ShowTailGrammar.FULL, true, ShowScopeAnswer.CANNOT_SHOW,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, true, false),
    SCHEMAS("SCHEMA", "SCHEMAS", ShowTailGrammar.FULL, true, ShowScopeAnswer.OBJECT_DOES_NOT_EXIST,
        ShowScopeAnswer.OBJECT_DOES_NOT_EXIST, ShowScopeAnswer.OBJECT_DOES_NOT_EXIST, false, false),
    TABLES("TABLE", "TABLES", ShowTailGrammar.FULL, false, ShowScopeAnswer.OBJECT_DOES_NOT_EXIST,
        ShowScopeAnswer.OBJECT_DOES_NOT_EXIST, ShowScopeAnswer.CANNOT_SHOW, false, false),
    ICEBERG_TABLES("ICEBERG TABLE", "ICEBERG_TABLES", ShowTailGrammar.FULL, false,
        ShowScopeAnswer.OBJECT_DOES_NOT_EXIST, ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    EVENT_TABLES("EVENT TABLE", "EVENT_TABLES", ShowTailGrammar.FULL, false,
        ShowScopeAnswer.OBJECT_DOES_NOT_EXIST, ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    EXTERNAL_TABLES("EXTERNAL TABLE", "EXTERNAL_TABLES", ShowTailGrammar.FULL, false,
        ShowScopeAnswer.OBJECT_DOES_NOT_EXIST, ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    INTEGRATIONS("INTEGRATION", "INTEGRATIONS", ShowTailGrammar.NONE, false, ShowScopeAnswer.CANNOT_SHOW,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, true, false),
    EXTERNAL_VOLUMES("EXTERNAL VOLUME", "EXTERNAL_VOLUMES", ShowTailGrammar.NONE, false, ShowScopeAnswer.CANNOT_SHOW,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, true, false),
    VIEWS("VIEW", "VIEWS", ShowTailGrammar.FULL, false, ShowScopeAnswer.OBJECT_DOES_NOT_EXIST,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    MATERIALIZED_VIEWS("MATERIALIZED VIEW", "MATERIALIZED_VIEWS", ShowTailGrammar.FULL, false,
        ShowScopeAnswer.MISSING_APPLICATION, ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    DYNAMIC_TABLES("DYNAMIC TABLE", "DYNAMIC_TABLES", ShowTailGrammar.FULL, false,
        ShowScopeAnswer.OBJECT_DOES_NOT_EXIST, ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    HYBRID_TABLES("KEY VALUE TABLE", "KEY_VALUE_TABLES", ShowTailGrammar.FULL, false,
        ShowScopeAnswer.OBJECT_DOES_NOT_EXIST, ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    COLUMNS("COLUMN", "COLUMNS", ShowTailGrammar.FULL, false, ShowScopeAnswer.MISSING_APPLICATION,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    STREAMS("STREAM", "STREAMS", ShowTailGrammar.FULL, false, ShowScopeAnswer.MISSING_APPLICATION,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    TASKS("TASK", null, ShowTailGrammar.NO_PRIVILEGES, false, ShowScopeAnswer.MISSING_APPLICATION,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    ALERTS("ALERT", null, ShowTailGrammar.NO_PRIVILEGES, false, ShowScopeAnswer.MISSING_APPLICATION,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    PIPES("PIPE", "PIPES", ShowTailGrammar.FULL, false, ShowScopeAnswer.MISSING_APPLICATION,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    SEQUENCES("SEQUENCE", "SEQUENCES", ShowTailGrammar.FULL, false, ShowScopeAnswer.MISSING_APPLICATION,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    WAREHOUSES("WAREHOUSE", "WAREHOUSES", ShowTailGrammar.FULL, true, ShowScopeAnswer.CANNOT_SHOW,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, true, false),
    CORTEX_SEARCH_SERVICES("CORTEX SEARCH SERVICE", "CORTEX_SEARCH_SERVICES", ShowTailGrammar.FULL, false,
        ShowScopeAnswer.MISSING_APPLICATION, ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    NOTEBOOKS("NOTEBOOK", "NOTEBOOKS", ShowTailGrammar.FULL, false, ShowScopeAnswer.MISSING_APPLICATION,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    STREAMLITS("STREAMLIT", "STREAMLITS", ShowTailGrammar.FULL, false, ShowScopeAnswer.MISSING_APPLICATION,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    COMPUTE_POOLS("COMPUTE POOL", "COMPUTE_POOLS", ShowTailGrammar.FULL, false, ShowScopeAnswer.CANNOT_SHOW,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, true, false),
    COMPUTE_POOL_INSTANCE_FAMILIES(null, "COMPUTE_POOL_INSTANCE_FAMILIESS", ShowTailGrammar.FULL, false,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    STAGES("STAGE", "STAGES", ShowTailGrammar.FULL, false, ShowScopeAnswer.OBJECT_DOES_NOT_EXIST,
        ShowScopeAnswer.OBJECT_DOES_NOT_EXIST, ShowScopeAnswer.CANNOT_SHOW, false, false),
    FILE_FORMATS("FILE FORMAT", "FILE_FORMATS", ShowTailGrammar.FULL, false, ShowScopeAnswer.OBJECT_DOES_NOT_EXIST,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    TAGS("TAG", "TAGS", ShowTailGrammar.FULL, false, ShowScopeAnswer.MISSING_APPLICATION,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    PROCEDURES("PROCEDURE", "PROCEDURES", ShowTailGrammar.FULL, false, ShowScopeAnswer.MISSING_APPLICATION,
        ShowScopeAnswer.MISSING_CLASS, ShowScopeAnswer.CANNOT_SHOW, false, false),
    FUNCTIONS("FUNCTION", "FUNCTIONS", ShowTailGrammar.FULL, false, ShowScopeAnswer.MISSING_APPLICATION,
        ShowScopeAnswer.MISSING_CLASS, ShowScopeAnswer.MISSING_SERVICE, false, false),
    USERS("USER", "USERS", ShowTailGrammar.FULL, true, ShowScopeAnswer.CANNOT_SHOW,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, true, false),
    ROLES("ROLE", null, ShowTailGrammar.NO_PRIVILEGES, false, ShowScopeAnswer.CANNOT_SHOW,
        ShowScopeAnswer.MISSING_CLASS, ShowScopeAnswer.MISSING_SERVICE, true, false),
    MASKING_POLICIES("MASKING POLICY", "MASKING_POLICYS", ShowTailGrammar.FULL, false,
        ShowScopeAnswer.MISSING_APPLICATION, ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    CONTACTS("CONTACT", "CONTACTS", ShowTailGrammar.FULL, false, ShowScopeAnswer.MISSING_APPLICATION,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    PROJECTION_POLICIES("PROJECTION POLICY", "PROJECTION_POLICYS", ShowTailGrammar.FULL, false,
        ShowScopeAnswer.MISSING_APPLICATION, ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    AGGREGATION_POLICIES("AGGREGATION POLICY", "AGGREGATION_POLICYS", ShowTailGrammar.FULL, false,
        ShowScopeAnswer.MISSING_APPLICATION, ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    JOIN_POLICIES("JOIN POLICY", "JOIN_POLICYS", ShowTailGrammar.FULL, false, ShowScopeAnswer.MISSING_APPLICATION,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    ROW_ACCESS_POLICIES("ROW ACCESS POLICY", "ROW_ACCESS_POLICYS", ShowTailGrammar.FULL, false,
        ShowScopeAnswer.MISSING_APPLICATION, ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    OBJECTS("OBJECT", "OBJECTS", ShowTailGrammar.FULL, false, ShowScopeAnswer.OBJECT_DOES_NOT_EXIST,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    KEYS("CONSTRAINT", null, ShowTailGrammar.NONE, false, ShowScopeAnswer.MISSING_APPLICATION,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    ACCOUNTS(null, "ACCOUNTS", ShowTailGrammar.FULL, false, ShowScopeAnswer.CANNOT_SHOW,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    ORGANIZATION_ACCOUNTS(null, null, ShowTailGrammar.LIMIT_ONLY, false, ShowScopeAnswer.CANNOT_SHOW,
        ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW, false, false),
    LOCKS(null, null, ShowTailGrammar.NONE, false, ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW,
        ShowScopeAnswer.CANNOT_SHOW, true, true),
    TRANSACTIONS(null, null, ShowTailGrammar.NONE, false, ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW,
        ShowScopeAnswer.CANNOT_SHOW, true, true),
    VARIABLES(null, null, ShowTailGrammar.NONE, false, ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW,
        ShowScopeAnswer.CANNOT_SHOW, false, true),
    /** GRANTS and PARAMETERS, which take none of these scopes and modifiers here. */
    OTHER(null, null, ShowTailGrammar.NONE, false, ShowScopeAnswer.CANNOT_SHOW, ShowScopeAnswer.CANNOT_SHOW,
        ShowScopeAnswer.CANNOT_SHOW, false, false);

    private final String objectType;
    private final String featureName;
    private final ShowTailGrammar tail;
    private final boolean filtersByPrivileges;
    private final ShowScopeAnswer applicationScope;
    private final ShowScopeAnswer classScope;
    private final ShowScopeAnswer serviceScope;
    private final boolean accountLevel;
    private final boolean startsWithStacksItsPrefix;

    ShowListing(final String objectType, final String featureName, final ShowTailGrammar tail,
                final boolean filtersByPrivileges, final ShowScopeAnswer applicationScope,
                final ShowScopeAnswer classScope, final ShowScopeAnswer serviceScope, final boolean accountLevel,
                final boolean startsWithStacksItsPrefix) {
        this.objectType = objectType;
        this.featureName = featureName;
        this.tail = tail;
        this.filtersByPrivileges = filtersByPrivileges;
        this.applicationScope = applicationScope;
        this.classScope = classScope;
        this.serviceScope = serviceScope;
        this.accountLevel = accountLevel;
        this.startsWithStacksItsPrefix = startsWithStacksItsPrefix;
    }

    /** The object kind a {@code Cannot show objects of type <KIND> in …} sentence names. */
    String objectType() {
        return objectType;
    }

    /** The listing's name in the {@code Unsupported feature 'SHOW <NAME> ... WITH PRIVILEGES <>'.} sentence. */
    String featureName() {
        return featureName;
    }

    /** The modifiers the listing's grammar reads. */
    ShowTailGrammar tail() {
        return tail;
    }

    /** Whether WITH PRIVILEGES filters the listing, rather than being refused as an unsupported feature. */
    boolean filtersByPrivileges() {
        return filtersByPrivileges;
    }

    /** How the listing answers {@code IN APPLICATION <name>}. */
    ShowScopeAnswer applicationScope() {
        return applicationScope;
    }

    /** How the listing answers {@code IN CLASS <name>}. */
    ShowScopeAnswer classScope() {
        return classScope;
    }

    /** How the listing answers {@code IN SERVICE <name>}. */
    ShowScopeAnswer serviceScope() {
        return serviceScope;
    }

    /** Whether the listing names account-level objects, which only {@code IN ACCOUNT} lists. */
    boolean isAccountLevel() {
        return accountLevel;
    }

    /**
     * Whether the listing, written with no IN clause, looks in the current SCHEMA alone. Live splits the
     * schema-level listings kind by kind, with nothing in the object kind to predict it: the ones named here read
     * the current schema, the ones {@link #listsSearchPathUnscoped} names read the search path. Either way a
     * session with no current schema lists its whole database, and one with no current database the account.
     *
     * @return whether the unscoped listing is the current schema's
     */
    boolean listsCurrentSchemaUnscoped() {
        switch (this) {
            case STAGES:
            case PIPES:
            case MASKING_POLICIES:
            case ROW_ACCESS_POLICIES:
            case PROJECTION_POLICIES:
            case AGGREGATION_POLICIES:
            case JOIN_POLICIES:
            case EVENT_TABLES:
            case DYNAMIC_TABLES:
            // The hybrid, Iceberg and Cortex Search listings are unmeasured and keep the schema they have always
            // read: none of the three objects could be created to tell.
            case HYBRID_TABLES:
            case ICEBERG_TABLES:
            // An external table cannot be created here either, so which of the two scopes its unscoped
            // listing reads cannot be told apart: the listing is empty whichever it is.
            case EXTERNAL_TABLES:
            case CORTEX_SEARCH_SERVICES:
            case NOTEBOOKS:
            case STREAMLITS:
            case CONTACTS:
            case KEYS:
                return true;
            default:
                return false;
        }
    }

    /**
     * Whether the listing, written with no IN clause in a session with a current schema, lists the schemas of the
     * session's SEARCH_PATH — {@code $current, $public} unless the session sets another — rather than one schema:
     * each schema in path order, and of the objects sharing a name only the one the path reaches first, as an
     * unqualified reference would resolve it. The routine listings reach an overload by its argument types.
     *
     * @return whether the unscoped listing follows the search path
     */
    boolean listsSearchPathUnscoped() {
        switch (this) {
            case TABLES:
            case VIEWS:
            case OBJECTS:
            case COLUMNS:
            case FUNCTIONS:
            case PROCEDURES:
            case SEQUENCES:
            case STREAMS:
            case TASKS:
            case FILE_FORMATS:
            case TAGS:
            case ALERTS:
            case MATERIALIZED_VIEWS:
                return true;
            default:
                return false;
        }
    }

    /**
     * Whether a STARTS WITH the listing does not read stacks a second line at its prefix: live reads the WITH as the
     * start of a statement, so the string after it is a fault of its own.
     */
    boolean startsWithStacksItsPrefix() {
        return startsWithStacksItsPrefix;
    }

    /**
     * The listing a SHOW statement names. The compound listings are recognised before the bare word they contain,
     * as {@code ShowModifierProfile} does, or {@code SHOW MATERIALIZED VIEWS} would read as VIEWS.
     *
     * @param ctx the statement
     * @return its listing
     */
    static ShowListing of(final FrostlakeParser.ShowStatementContext ctx) {
        if (ctx.COMPUTE() != null) {
            return ctx.FAMILIES() != null ? COMPUTE_POOL_INSTANCE_FAMILIES : COMPUTE_POOLS;
        }
        if (ctx.ICEBERG() != null) {
            return ICEBERG_TABLES;
        }
        if (ctx.EVENT() != null) {
            return EVENT_TABLES;
        }
        if (ctx.INTEGRATIONS() != null) {
            return INTEGRATIONS;
        }
        if (ctx.VOLUMES() != null) {
            return EXTERNAL_VOLUMES;
        }
        if (ctx.EXTERNAL() != null) {
            return EXTERNAL_TABLES;
        }
        if (ctx.DYNAMIC() != null) {
            return DYNAMIC_TABLES;
        }
        if (ctx.HYBRID() != null) {
            return HYBRID_TABLES;
        }
        if (ctx.MATERIALIZED() != null) {
            return MATERIALIZED_VIEWS;
        }
        if (ctx.CORTEX() != null) {
            return CORTEX_SEARCH_SERVICES;
        }
        if (ctx.NOTEBOOKS() != null) {
            return NOTEBOOKS;
        }
        if (ctx.STREAMLITS() != null) {
            return STREAMLITS;
        }
        if (ctx.FILE() != null) {
            return FILE_FORMATS;
        }
        if (ctx.POLICIES() != null) {
            return policies(ctx);
        }
        if (ctx.KEYS() != null) {
            return KEYS;
        }
        if (ctx.ORGANIZATION() != null) {
            return ORGANIZATION_ACCOUNTS;
        }
        return plain(ctx);
    }

    private static ShowListing policies(final FrostlakeParser.ShowStatementContext ctx) {
        if (ctx.MASKING() != null) {
            return MASKING_POLICIES;
        }
        if (ctx.PROJECTION() != null) {
            return PROJECTION_POLICIES;
        }
        if (ctx.AGGREGATION() != null) {
            return AGGREGATION_POLICIES;
        }
        return ctx.JOIN() != null ? JOIN_POLICIES : ROW_ACCESS_POLICIES;
    }

    private static ShowListing plain(final FrostlakeParser.ShowStatementContext ctx) {
        if (ctx.DATABASES() != null) {
            return DATABASES;
        }
        if (ctx.SCHEMAS() != null) {
            return SCHEMAS;
        }
        if (ctx.TABLES() != null) {
            return TABLES;
        }
        if (ctx.VIEWS() != null) {
            return VIEWS;
        }
        if (ctx.COLUMNS() != null) {
            return COLUMNS;
        }
        if (ctx.STREAMS() != null) {
            return STREAMS;
        }
        if (ctx.TASKS() != null) {
            return TASKS;
        }
        if (ctx.ALERTS() != null) {
            return ALERTS;
        }
        if (ctx.PIPES() != null) {
            return PIPES;
        }
        if (ctx.SEQUENCES() != null) {
            return SEQUENCES;
        }
        if (ctx.WAREHOUSES() != null) {
            return WAREHOUSES;
        }
        if (ctx.STAGES() != null) {
            return STAGES;
        }
        if (ctx.TAGS() != null) {
            return TAGS;
        }
        if (ctx.PROCEDURES() != null) {
            return PROCEDURES;
        }
        if (ctx.FUNCTIONS() != null) {
            return FUNCTIONS;
        }
        if (ctx.USERS() != null) {
            return USERS;
        }
        if (ctx.ROLES() != null) {
            return ROLES;
        }
        if (ctx.CONTACTS() != null) {
            return CONTACTS;
        }
        if (ctx.OBJECTS() != null) {
            return OBJECTS;
        }
        return service(ctx);
    }

    private static ShowListing service(final FrostlakeParser.ShowStatementContext ctx) {
        if (ctx.ACCOUNTS() != null) {
            return ACCOUNTS;
        }
        if (ctx.LOCKS() != null) {
            return LOCKS;
        }
        if (ctx.TRANSACTIONS() != null) {
            return TRANSACTIONS;
        }
        return ctx.VARIABLES() != null ? VARIABLES : OTHER;
    }
}
