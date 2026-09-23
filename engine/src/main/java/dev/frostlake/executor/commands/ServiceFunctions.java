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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Schema;

/**
 * Service functions: a CREATE FUNCTION naming a SERVICE and an ENDPOINT, whose body is the HTTP path a call is sent
 * to. Frostlake records one as metadata; since no service runs, calling it is refused.
 */
public final class ServiceFunctions {

    /** Static helpers only. */
    private ServiceFunctions() {
    }

    /**
     * The service a SERVICE option names, which must exist.
     *
     * @return its qualified name
     */
    public static String requireService(final Catalog catalog, final String[] parts) {
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String name = parts[parts.length - 1];
        if (schema.getContainerObjects().getService(name) == null) {
            throw new RuntimeException(SqlCompilationError.of("SERVICE '" + QualifiedName.join(parts)
                + "' does not exist or not authorized."));
        }
        return QualifiedName.join(parts);
    }

    /** A service function names both its service and its endpoint, and the path its calls go to. */
    public static void requireComplete(final String service, final String endpoint, final String body) {
        if (service == null && endpoint == null) {
            return;
        }
        if (service == null) {
            throw new RuntimeException(SqlCompilationError.of("Missing option(s): [SERVICE]"));
        }
        if (endpoint == null) {
            throw new RuntimeException(SqlCompilationError.of("Missing option(s): [ENDPOINT]"));
        }
        if (body == null) {
            throw new RuntimeException(SqlCompilationError.of("Missing option(s): [AS]"));
        }
    }

    /** The refusal a call of a service function meets. */
    public static String cannotCall(final Function function) {
        return "Service function " + function.getName() + " cannot be called: service " + function.getServiceName()
            + " has no running instance to answer it.";
    }
}
