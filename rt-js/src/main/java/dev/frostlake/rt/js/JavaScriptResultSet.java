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

package dev.frostlake.rt.js;

import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

public class JavaScriptResultSet {
        private final ResultSet resultSet;
        private int currentRow = -1;

        public JavaScriptResultSet(final ResultSet resultSet) {
            this.resultSet = resultSet;
        }

        public boolean next() {
            currentRow++;
            return currentRow < resultSet.getRowCount();
        }

        public Object getColumnValue(final int columnIndex) {
            if (currentRow < 0 || currentRow >= resultSet.getRowCount()) {
                throw new RuntimeException("No current row. Call next() first.");
            }

            final Row row = resultSet.getRows().get(currentRow);
            final int index = columnIndex - 1;

            if (index < 0 || index >= row.size()) {
                throw new RuntimeException("Column index " + columnIndex + " is out of range");
            }

            return row.getValue(index);
        }

        public Object getColumnValue(final String columnName) {
            if (currentRow < 0 || currentRow >= resultSet.getRowCount()) {
                throw new RuntimeException("No current row. Call next() first.");
            }

            final Row row = resultSet.getRows().get(currentRow);
            for (int i = 0; i < resultSet.getColumns().size(); i++) {
                if (resultSet.getColumns().get(i).getName().equalsIgnoreCase(columnName)) {
                    return row.getValue(i);
                }
            }

            throw new RuntimeException("Column " + columnName + " not found");
        }

        public int getRowCount() {
            return resultSet.getRowCount();
        }

        public int getColumnCount() {
            return resultSet.getColumnCount();
        }
    }
