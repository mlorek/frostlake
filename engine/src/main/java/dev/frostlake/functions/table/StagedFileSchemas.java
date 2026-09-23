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

import dev.frostlake.executor.copy.InferredColumn;

import java.util.List;
import java.util.Map;

/**
 * The seam {@link InferSchema} reads staged files through: the executor resolves the stage, the file format and
 * the files a call names, and reads their columns.
 */
public interface StagedFileSchemas {

    /**
     * The columns the files an INFER_SCHEMA call names give, in ORDER_ID order.
     *
     * @param arguments the call's arguments by upper-cased name, already validated
     * @return the columns
     */
    List<InferredColumn> inferColumns(final Map<String, Object> arguments);
}
