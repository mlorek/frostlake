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

import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * INFORMATION_SCHEMA.SERVERLESS_TASK_HISTORY: the credits serverless task runs used, by task and hour. The engine
 * bills no credits, so it lists no row, in the account's four columns and types (live-verified): START_TIME and
 * END_TIME TIMESTAMP_LTZ, TASK_NAME text and CREDITS_USED NUMBER(38,9). Its arguments — DATE_RANGE_START,
 * DATE_RANGE_END and TASK_NAME — are taken and read no further.
 */
public class ServerlessTaskHistoryFunction extends TableFunction {

    public ServerlessTaskHistoryFunction() {
        super("SERVERLESS_TASK_HISTORY");
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Every argument is optional.
    }

    @Override
    public List<ResultSetColumn> outputColumns(final Map<String, Object> namedArgs) {
        return columns();
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        return new ResultSet(columns(), new ArrayList<Row>());
    }

    private static List<ResultSetColumn> columns() {
        return Arrays.asList(
            new ResultSetColumn("START_TIME", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("END_TIME", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("TASK_NAME", TaskGraphRuns.TEXT),
            new ResultSetColumn("CREDITS_USED", new NumericType("NUMBER", 38, 9)));
    }
}
