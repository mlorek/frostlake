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

package dev.frostlake.executor.copy;

/**
 * One row of INFER_SCHEMA's answer: a column of the staged files, as COLUMN_NAME, TYPE, NULLABLE, EXPRESSION,
 * FILENAMES and ORDER_ID report it.
 */
public final class InferredColumn {

    private final String name;
    private final String type;
    private final boolean nullable;
    private final String expression;
    private final String fileNames;
    private final int orderId;

    InferredColumn(final String name, final String type, final boolean nullable, final String expression,
                   final String fileNames, final int orderId) {
        this.name = name;
        this.type = type;
        this.nullable = nullable;
        this.expression = expression;
        this.fileNames = fileNames;
        this.orderId = orderId;
    }

    /** COLUMN_NAME. */
    public String name() {
        return name;
    }

    /** TYPE. */
    public String type() {
        return type;
    }

    /** NULLABLE. */
    public boolean nullable() {
        return nullable;
    }

    /** EXPRESSION. */
    public String expression() {
        return expression;
    }

    /** FILENAMES. */
    public String fileNames() {
        return fileNames;
    }

    /** ORDER_ID. */
    public int orderId() {
        return orderId;
    }
}
