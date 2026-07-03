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

package com.snowflake.snowpark_java;

import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stub implementation of Snowflake Snowpark DataFrame for inline Java procedures.
 */
public class DataFrame {

    private final ResultSet resultSet;

    public DataFrame(final ResultSet resultSet) {
        this.resultSet = resultSet;
    }

    public Row[] collect() {
        return resultSet.getRows().toArray(new Row[0]);
    }

    public long count() {
        return resultSet.getRowCount();
    }

    public List<Map<String, Object>> toMapList() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (final Row row : resultSet.getRows()) {
            Map<String, Object> map = new HashMap<>();
            for (int i = 0; i < resultSet.getColumns().size(); i++) {
                map.put(resultSet.getColumns().get(i).getName(), row.getValue(i));
            }
            result.add(map);
        }
        return result;
    }

    public ResultSet getResultSet() {
        return resultSet;
    }
}
