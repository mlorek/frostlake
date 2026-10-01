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

package dev.frostlake.metastore.model;

import java.time.Instant;

/**
 * The views every database's INFORMATION_SCHEMA holds, as a real account lists them: 63 names in name
 * order, each with the fixed description the account reports as its comment. None of them was ever
 * created, so each carries the epoch as its creation time, and none has an owner.
 *
 * <p>STREAMS, TASKS and TAGS are deliberately absent: a real account has no such views. This list and the
 * reader in {@code QueryExecutor.executeSystemViewIfApplicable} must name the same set — a name listed here
 * but routed nowhere would resolve to its placeholder definition.
 */
public final class InformationSchemaViews {

    /** Each view's name and the description the account reports as its comment (live-verified). */
    private static final String[][] VIEWS = {
        {"APPLICABLE_ROLES",
            "The roles that can be applied to the current user."},
        {"APPLICATION_CONFIGURATIONS",
            "The configurations currently defined in the current application that are accessible to the current user's role."},
        {"APPLICATION_SERVICES",
            "The application services in this database that are accessible to the current user's role."},
        {"APPLICATION_SPECIFICATIONS",
            "The specification requests currently defined in the current application that are accessible to the current user's role."},
        {"BACKUPS",
            "All backups within an account"},
        {"BACKUP_POLICIES",
            "All backup policies within an account"},
        {"BACKUP_SETS",
            "All backup sets within an account"},
        {"CHECK_CONSTRAINTS",
            "CHECK constraints defined on tables that are accessible by the current user's role"},
        {"CLASSES",
            "The BUNDLE CLASS that the current user has privileges to view."},
        {"CLASS_INSTANCES",
            "The BUNDLE INSTANCE that the current user has privileges to view."},
        {"CLASS_INSTANCE_FUNCTIONS",
            "The functions defined in a bundle that are accessible to the current user's role."},
        {"CLASS_INSTANCE_PROCEDURES",
            "The procedures defined in a bundle that are accessible to the current user's role."},
        {"COLUMNS",
            "The columns of tables defined in this database that are accessible to the current user's role."},
        {"CORTEX_SEARCH_SERVICES",
            "The Cortex Search Services defined in this database that are accessible to the current user's role."},
        {"CORTEX_SEARCH_SERVICE_SCORING_PROFILES",
            "The Cortex Search Service scoring profiles defined in this database that are accessible to the current user's role."},
        {"CURRENT_PACKAGES_POLICY",
            "The packages policy set on the current account"},
        {"DATABASES",
            "The databases that are accessible to the current user's role."},
        {"ELEMENT_TYPES",
            "The element types of structured array types defined in this database that are accessible to the current user's role."},
        {"ENABLED_ROLES",
            "The roles that are enabled to the current user."},
        {"EVENT_TABLES",
            "The event tables defined in this database that are accessible to the current user's role."},
        {"EXTERNAL_TABLES",
            "The external tables defined in this database that are accessible to the current user's role."},
        {"FIELDS",
            "The fields of structured object and map types defined in this database that are accessible to the current user's role."},
        {"FILE_FORMATS",
            "The file formats defined in this database that are accessible to the current user's role."},
        {"FUNCTIONS",
            "The user-defined functions defined in this database that are accessible to the current user's role."},
        {"GIT_REPOSITORIES",
            "Git repositories in this database that are accessible by the current user's role"},
        {"HYBRID_TABLES",
            "The hybrid tables defined in this database that are accessible to the current user's role."},
        {"INDEXES",
            "The indexes defined in this database that are accessible to the current user's role."},
        {"INDEX_COLUMNS",
            "The columns of indexes defined in this database that are accessible to the current user's role."},
        {"INFORMATION_SCHEMA_CATALOG_NAME",
            "Identifies the database (or catalog, in SQL terminology) that contains the information_schema"},
        {"LISTINGS",
            "Listings that are accessible by the current user's role"},
        {"LOAD_HISTORY",
            "The loading information of the copy command"},
        {"MODEL_VERSIONS",
            "The MODEL VERSIONS that the current user has privileges to view "},
        {"NOTEBOOKS",
            "Notebooks in this database that are accessible by the current user's role"},
        {"OBJECT_PRIVILEGES",
            "The privileges on all objects defined in this database that are accessible to the current user's role."},
        {"PACKAGES",
            "Available packages in current account"},
        {"PIPES",
            "The pipes defined in this database that are accessible to the current user's role."},
        {"PROCEDURES",
            "The stored procedures defined in this database that are accessible to the current user's role."},
        {"REFERENTIAL_CONSTRAINTS",
            "Referential Constraints in this database that are accessible to the current user"},
        {"REPLICATION_DATABASES",
            "The databases for replication that are accessible to the current user's role."},
        {"REPLICATION_GROUPS",
            "The replication groups that are accessible to the current user's role."},
        {"SCHEMATA",
            "The schemas defined in this database that are accessible to the current user's role."},
        {"SEMANTIC_DIMENSIONS",
            "The dimensions of Semantic Views defined in this database that are accessible to the current user's role."},
        {"SEMANTIC_FACTS",
            "The facts of Semantic Views defined in this database that are accessible to the current user's role."},
        {"SEMANTIC_METRICS",
            "The metrics of Semantic Views defined in this database that are accessible to the current user's role."},
        {"SEMANTIC_RELATIONSHIPS",
            "The relationships of Semantic Views defined in this database that are accessible to the current user's role."},
        {"SEMANTIC_TABLES",
            "The tables of Semantic Views defined in this database that are accessible to the current user's role."},
        {"SEMANTIC_VARIABLES",
            "The variables of Semantic Views defined in this database that are accessible to the current user's role."},
        {"SEMANTIC_VIEWS",
            "The Semantic Views defined in this database that are accessible to the current user's role."},
        {"SEQUENCES",
            "The sequences defined in this database that are accessible to the current user's role."},
        {"SERVICES",
            "The services in this database that are accessible to the current user's role."},
        {"SHARES",
            "Shares that are accessible by the current user's role"},
        {"SNAPSHOTS",
            "All snapshots within an account"},
        {"SNAPSHOT_POLICIES",
            "All snapshot policies within an account"},
        {"SNAPSHOT_SETS",
            "All snapshot sets within an account"},
        {"STAGES",
            "Stages in this database that are accessible by the current user's role"},
        {"STREAMLITS",
            "Streamlits in this database that are accessible by the current user's role"},
        {"TABLES",
            "The tables defined in this database that are accessible to the current user's role."},
        {"TABLE_CONSTRAINTS",
            "Constraints defined on the tables in this database that are accessible to the current user"},
        {"TABLE_PRIVILEGES",
            "The privileges on tables defined in this database that are accessible to the current user's role."},
        {"TABLE_STORAGE_METRICS",
            "All tables within an account, including expired tables."},
        {"TYPES",
            "The user-defined types defined in this database that are accessible to the current user's role."},
        {"USAGE_PRIVILEGES",
            "The usage privileges on sequences defined in this database that are accessible to the current user's role."},
        {"VIEWS",
            "The views defined in this database that are accessible to the current user's role."}
    };

    /** The placeholder definition a system view carries; its rows come from the system view readers. */
    private static final String DEFINITION = "/* system view */";

    private InformationSchemaViews() {
    }

    /**
     * Registers every view in a database's INFORMATION_SCHEMA: created at the epoch and owned by nobody,
     * like the schema holding them, with the account's description as its comment.
     */
    static void seed(final Schema informationSchema) {
        for (final String[] view : VIEWS) {
            final View systemView = new View(view[0], DEFINITION, Instant.EPOCH);
            systemView.setOwner(null);
            systemView.setComment(view[1]);
            informationSchema.addView(systemView);
        }
    }
}
