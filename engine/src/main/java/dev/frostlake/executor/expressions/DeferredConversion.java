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

package dev.frostlake.executor.expressions;

import dev.frostlake.types.DataType;

/**
 * A SQL UDF argument that does not convert to its parameter's declared type. The account converts an argument
 * where an expression or query body READS the parameter, so a body that never reads it - a constant, a branch
 * not taken - never fails: the argument waits as the value the caller passed, and the body reads it through a
 * cast to the declared type, which refuses it there in the conversion's own words (live-verified).
 */
final class DeferredConversion {

    private final Object raw;
    private final DataType type;
    private final RuntimeException failure;

    DeferredConversion(final Object raw, final DataType type, final RuntimeException failure) {
        this.raw = raw;
        this.type = type;
        this.failure = failure;
    }

    /** The value the caller passed. */
    Object getRaw() {
        return raw;
    }

    /** The parameter's declared type. */
    DataType getType() {
        return type;
    }

    /** The conversion's refusal, raised where the body reads the parameter. */
    RuntimeException getFailure() {
        return failure;
    }
}
