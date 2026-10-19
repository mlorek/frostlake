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

package dev.frostlake.metastore.model;

import dev.frostlake.types.DataType;

import java.util.List;

/**
 * A function or a procedure as its overloads are told apart: by name and signature, and by what it returns when a
 * name and signature are refused as taken (see {@link OverloadStore}).
 */
public interface Routine extends SessionScoped {

    /**
     * The routine's canonical name.
     *
     * @return the name
     */
    String getName();

    /**
     * The routine's parameters, in order.
     *
     * @return the parameters
     */
    List<Parameter> getParameters();

    /**
     * The type the routine returns.
     *
     * @return the type
     */
    DataType getReturnType();

    /**
     * Whether the return type was written with its width.
     *
     * @return whether it was, or null when that is not known
     */
    Boolean getReturnSized();

    /**
     * The columns of the table the routine returns.
     *
     * @return the columns; empty for a routine that returns a value
     */
    List<Parameter> getReturnColumns();

    /**
     * Whether the routine returns a table.
     *
     * @return whether it does
     */
    boolean returnsTable();
}
