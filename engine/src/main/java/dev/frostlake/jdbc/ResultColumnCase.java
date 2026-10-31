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
package dev.frostlake.jdbc;

/**
 * How a connection's result sets match a column label: by its exact spelling, as the account's driver does by default,
 * or ignoring case once the connection's session has set {@code CLIENT_RESULT_COLUMN_CASE_INSENSITIVE = TRUE}, which
 * then holds for the rest of the connection - a later FALSE or UNSET leaves it so, as it leaves the account's driver
 * (live-verified).
 */
interface ResultColumnCase {

    /**
     * @return whether a label names the first column it matches ignoring case
     */
    boolean labelsIgnoreCase();
}
