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

package dev.frostlake.http.rest;

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.SystemSchema;
import dev.frostlake.metastore.model.Database;

/**
 * What a request's 404 names when the database or the schema of its path is what is missing. A request for a
 * schema, a table or any other object under a database that is not there is answered with the database's own
 * sentence, and one under a schema that is not there with the schema's, whatever the statement the resource ran
 * for it said (live-verified).
 */
final class MissingParent {

    private MissingParent() {
    }

    /**
     * The refusal to answer with: the one the resource raised, or the missing parent's.
     *
     * @param call the request
     * @param catalog the catalog its path is looked up in
     * @param refusal what the resource raised
     * @return the refusal naming what is missing
     */
    static RestException refine(final RestCall call, final Catalog catalog, final RestException refusal) {
        if (refusal.getStatus() != 404 || call.pathParameter("database") == null) {
            return refusal;
        }
        final String databaseName;
        final String schemaName;
        try {
            databaseName = call.identifier("database").name();
            schemaName = call.pathParameter("schema") == null ? null : call.identifier("schema").name();
        } catch (final RestException unreadable) {
            return refusal;
        }
        if (!catalog.hasDatabaseExact(databaseName)) {
            return RestException.missing("Database", RestIdentifier.display(databaseName));
        }
        final Database database = catalog.databaseExact(databaseName);
        // A schema the system database serves without holding it is there: what is missing is the object.
        if (schemaName != null && !database.hasSchemaExact(schemaName)
                && SystemSchema.of(databaseName, schemaName) == null) {
            return RestException.missing("Schema", RestIdentifier.display(databaseName) + "."
                + RestIdentifier.display(schemaName));
        }
        return refusal;
    }
}
