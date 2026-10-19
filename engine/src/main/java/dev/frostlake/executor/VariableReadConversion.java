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

/**
 * Converts a block variable's value where one of the block's expressions reads it: a CALL's answer held unconverted
 * converts to the variable's type there (see {@code ProceduralExecutor.readHeld}).
 */
public interface VariableReadConversion {

    /**
     * The value an expression reads from a variable.
     *
     * @param name  the variable's name, as the expression writes it
     * @param value the value the variable holds
     * @return the value the expression reads
     */
    Object read(String name, Object value);
}
