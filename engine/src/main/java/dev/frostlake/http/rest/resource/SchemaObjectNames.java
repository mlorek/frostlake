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

package dev.frostlake.http.rest.resource;

import dev.frostlake.http.rest.RestCall;
import dev.frostlake.http.rest.RestIdentifier;

/**
 * A schema-level object's name as the string arguments of the metadata functions take it — {@code db.schema.name},
 * each part bare when an unquoted identifier resolves to it and double-quoted otherwise — and the database whose
 * {@code INFORMATION_SCHEMA} answers for it.
 */
final class SchemaObjectNames {

    /** Static helpers only. */
    private SchemaObjectNames() {
    }

    /** The object's name as a function's string argument names it. */
    static String dotted(final RestCall call, final RestIdentifier name) {
        return RestIdentifier.display(call.identifier("database").name()) + "."
            + RestIdentifier.display(call.identifier("schema").name()) + "." + RestIdentifier.display(name.name());
    }

    /** The path's database as the qualifier of an {@code INFORMATION_SCHEMA} function. */
    static String informationSchemaDatabase(final RestCall call) {
        return RestIdentifier.display(call.identifier("database").name());
    }
}
