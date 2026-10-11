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

package dev.frostlake.executor;

import dev.frostlake.storage.Row;

import java.util.List;

/**
 * One row a handler table function reads: the row to the call's left, the arguments the call makes of it, and
 * the keys that place the row in a partition and order it there.
 */
final class TableFunctionInput {

    private final Row leftRow;
    private final List<Object> arguments;
    private final List<Object> partitionKey;
    private final List<Object> orderKey;

    /**
     * @param leftRow      the row to the call's left, or null where the call is only being judged
     * @param arguments    the function's arguments for the row
     * @param partitionKey the row's PARTITION BY values
     * @param orderKey     the row's ORDER BY values
     */
    TableFunctionInput(final Row leftRow, final List<Object> arguments, final List<Object> partitionKey,
                       final List<Object> orderKey) {
        this.leftRow = leftRow;
        this.arguments = arguments;
        this.partitionKey = partitionKey;
        this.orderKey = orderKey;
    }

    Row leftRow() {
        return leftRow;
    }

    List<Object> arguments() {
        return arguments;
    }

    List<Object> partitionKey() {
        return partitionKey;
    }

    List<Object> orderKey() {
        return orderKey;
    }
}
