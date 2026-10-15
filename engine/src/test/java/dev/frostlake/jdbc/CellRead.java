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

import java.sql.ResultSet;
import java.sql.SQLException;

/** One read of the current row of a result set, by whichever getter the test names. */
interface CellRead {

    /**
     * The read.
     *
     * @param row the result set, on its row
     * @return what the getter answered
     * @throws SQLException when the getter refuses
     */
    Object of(ResultSet row) throws SQLException;
}
