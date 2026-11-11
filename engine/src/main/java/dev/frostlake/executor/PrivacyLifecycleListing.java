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

import dev.frostlake.functions.scalar.context.DefinitionOwnership;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.metastore.model.PrivacyPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.StorageLifecyclePolicy;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SHOW and DESCRIBE of the privacy and the storage lifecycle policies. A listing has the columns the other policy
 * kinds' have, the kind PRIVACY_POLICY or STORAGE_LIFECYCLE_POLICY, and in {@code options} a storage lifecycle
 * policy's archive properties as an object - {@code {"ARCHIVE_FOR_DAYS":90,"ARCHIVE_TIER":"COOL"}}, the days null and
 * the tier "NULL" when unset - where a privacy policy's are empty; TERSE lists the same columns. DESCRIBE answers
 * the name, the signature, the return type and the body, and a storage lifecycle policy's days and tier after them
 * (all live-verified).
 */
final class PrivacyLifecycleListing {

    private static final String PRIVACY = "PRIVACY_POLICY";
    private static final String STORAGE = "STORAGE_LIFECYCLE_POLICY";
    private static final String NONE = "NULL";

    private final Catalog catalog;
    private final DefinitionOwnership ownership;

    PrivacyLifecycleListing(final Catalog catalog, final DefinitionOwnership ownership) {
        this.catalog = catalog;
        this.ownership = ownership;
    }

    /**
     * SHOW PRIVACY POLICIES or SHOW STORAGE LIFECYCLE POLICIES in a schema, the current one when none is named.
     *
     * @param schemaName the schema, or null
     * @param storage    whether the storage lifecycle policies are listed
     * @return the listing
     */
    ResultSet show(final String schemaName, final boolean storage) {
        final String dbName = ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        final List<Row> rows = new ArrayList<>();
        if (dbName != null && scName != null) {
            appendRows(catalog.databaseExact(dbName).schemaExact(scName), dbName, storage, rows);
        }
        return new ResultSet(ShowRoutineExecutor.projectionPolicyColumns(), rows);
    }

    /**
     * The listing over a database's schemas, the current database when none is named.
     *
     * @param databaseName the database, or null
     * @param storage      whether the storage lifecycle policies are listed
     * @return the listing
     */
    ResultSet showInDatabase(final String databaseName, final boolean storage) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        final List<Row> rows = new ArrayList<>();
        if (dbName != null) {
            for (final Schema schema : catalog.databaseExact(dbName).getAllSchemas()) {
                appendRows(schema, dbName, storage, rows);
            }
        }
        return new ResultSet(ShowRoutineExecutor.projectionPolicyColumns(), rows);
    }

    /**
     * The listing over every database of the account.
     *
     * @param storage whether the storage lifecycle policies are listed
     * @return the listing
     */
    ResultSet showInAccount(final boolean storage) {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showInDatabase(databaseName, storage);
            }
        });
        return across != null ? across : show(null, storage);
    }

    private void appendRows(final Schema schema, final String dbName, final boolean storage, final List<Row> rows) {
        final List<? extends SqlObject> policies = storage
            ? ShowRoutineExecutor.byName(schema.getStorageLifecyclePolicies())
            : ShowRoutineExecutor.byName(schema.getPrivacyPolicies());
        final String kind = storage ? STORAGE : PRIVACY;
        for (final SqlObject policy : policies) {
            if (!ownership.sees(kind, schema.qualifiedName(policy.getName()), policy.getOwner())) {
                continue;
            }
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(policy.getCreatedTime()),
                policy.getName(),
                dbName, schema.getName(),
                kind,
                policy.getOwner(),
                ShowResultHelpers.text(policy.getComment()),
                ShowResultHelpers.OWNER_ROLE_TYPE,
                storage ? options((StorageLifecyclePolicy) policy) : "")));
        }
    }

    /** A storage lifecycle policy's archive properties as SHOW writes them. */
    private static String options(final StorageLifecyclePolicy policy) {
        return "{\"ARCHIVE_FOR_DAYS\":" + (policy.getArchiveForDays() == null ? "null" : policy.getArchiveForDays())
            + ",\"ARCHIVE_TIER\":\"" + (policy.getArchiveTier() == null ? NONE : policy.getArchiveTier()) + "\"}";
    }

    /**
     * DESCRIBE PRIVACY POLICY or DESCRIBE STORAGE LIFECYCLE POLICY: one row. A name a policy of the other kind, or of
     * any other, holds is refused as that kind's: {@code Object found is of type 'PRIVACY_POLICY', not specified
     * type 'STORAGE_LIFECYCLE_POLICY'.} (live-verified).
     *
     * @param policyName the policy as written
     * @param storage    whether a storage lifecycle policy is described
     * @return the description
     */
    ResultSet describe(final String policyName, final boolean storage) {
        final String kind = storage ? STORAGE : PRIVACY;
        final SqlObject policy = storage ? catalog.findStorageLifecyclePolicy(policyName)
            : catalog.findPrivacyPolicy(policyName);
        // Named exactly: a quoted name in another case is another policy, which is not there.
        if (policy == null) {
            final Schema home = catalog.requireOwningSchema(QualifiedName.parse(policyName));
            final String holder = home.policyKindHolding(QualifiedName.parse(policyName).last());
            if (holder != null && !holder.equals(kind)) {
                throw new RuntimeException(SqlCompilationError.PREFIX + " Object found is of type '" + holder
                    + "', not specified type '" + kind + "'.");
            }
        }
        if (policy == null || !ownership.sees(kind, catalog.qualifiedExactName(policyName), policy.getOwner())) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(
                storage ? "Storage lifecycle policy" : "Privacy policy", catalog.qualifiedExactName(policyName)));
        }
        final List<ResultSetColumn> columns = new ArrayList<>(Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("signature", StringType.VARCHAR),
            new ResultSetColumn("return_type", StringType.VARCHAR),
            new ResultSetColumn("body", StringType.VARCHAR)));
        final List<Row> rows = new ArrayList<>();
        if (!storage) {
            rows.add(new Row(Arrays.asList(policy.getName(), "()", "PRIVACY_BUDGET",
                ((PrivacyPolicy) policy).getBody())));
            return new ResultSet(columns, rows);
        }
        final StorageLifecyclePolicy lifecycle = (StorageLifecyclePolicy) policy;
        columns.add(new ResultSetColumn("archive_for_days", StringType.VARCHAR));
        columns.add(new ResultSetColumn("archive_tier", StringType.VARCHAR));
        rows.add(new Row(Arrays.asList(lifecycle.getName(),
            ShowRoutineExecutor.describedSignature(lifecycle.getParameters()), "BOOLEAN", lifecycle.getBody(),
            lifecycle.getArchiveForDays() == null ? null : lifecycle.getArchiveForDays().toString(),
            lifecycle.getArchiveTier() == null ? NONE : lifecycle.getArchiveTier())));
        return new ResultSet(columns, rows);
    }
}
