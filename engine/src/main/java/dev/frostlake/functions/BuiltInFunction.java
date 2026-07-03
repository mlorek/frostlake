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

package dev.frostlake.functions;

import dev.frostlake.types.DataType;
import java.util.List;

public abstract class BuiltInFunction {

    private final String name;
    private final DataType returnType;

    protected BuiltInFunction(final String name, final DataType returnType) {
        this.name = name;
        this.returnType = returnType;
    }

    public String getName() {
        return name;
    }

    public DataType getReturnType() {
        return returnType;
    }

    public abstract Object evaluate(final List<Object> args);

    public abstract int getMinArgCount();

    public abstract int getMaxArgCount();

    public boolean isVariadic() {
        return getMaxArgCount() == Integer.MAX_VALUE;
    }
}
