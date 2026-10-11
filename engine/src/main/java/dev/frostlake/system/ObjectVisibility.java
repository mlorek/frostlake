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

package dev.frostlake.system;

import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;

/**
 * Says which objects the rights a statement runs with reach, so that the INFORMATION_SCHEMA views list no
 * others: a role reads the views without a grant on them, and each lists only the objects the role owns or
 * holds a privilege on, in the schemas it can enter (live-verified).
 */
public interface ObjectVisibility {

    /**
     * Whether a schema and what it holds are listed at all.
     *
     * @param database the schema's database
     * @param schema   the schema
     * @return whether the rights reach the database and the schema
     */
    boolean entersSchema(final Database database, final Schema schema);

    /**
     * Whether an object of a schema is listed.
     *
     * @param objectType the object's kind as privileges record it
     * @param schema     the object's schema
     * @param name       the object's name
     * @param owner      the role that owns it, or null when none is recorded
     * @return whether the rights own it or hold a privilege on it
     */
    boolean lists(final String objectType, final Schema schema, final String name, final String owner);

    /**
     * Whether a function overload is listed.
     *
     * @param schema   the function's schema
     * @param function the overload
     * @return whether the rights own it or hold a privilege on it
     */
    boolean listsFunction(final Schema schema, final Function function);

    /**
     * Whether a procedure overload is listed.
     *
     * @param schema    the procedure's schema
     * @param procedure the overload
     * @return whether the rights own it or hold a privilege on it
     */
    boolean listsProcedure(final Schema schema, final Procedure procedure);
}
