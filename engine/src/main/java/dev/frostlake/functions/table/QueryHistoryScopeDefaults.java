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

package dev.frostlake.functions.table;

/**
 * The caller's session, user and warehouse, read when a scoped query-history function runs — each is the
 * default of the scope it names, used when the call leaves the argument out.
 */
public interface QueryHistoryScopeDefaults {

    /** The calling session's number, as CURRENT_SESSION() answers it; null when there is none. */
    Long currentSession();

    /** The calling user, as CURRENT_USER() answers it; null when there is none. */
    String currentUser();

    /** The session's current warehouse; null when it has none. */
    String currentWarehouse();
}
