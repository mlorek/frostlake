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

package dev.frostlake.functions.scalar.math;

import java.util.List;

/**
 * CEIL, FLOOR, ROUND and TRUNC over a FLOAT scale. A scale is a bare number at row time, whatever its type, so only
 * the evaluator, which knows the scale's static type, can tell the function to round in FLOAT arithmetic (see
 * {@link RoundedValue#ofFloatScale}).
 */
public interface FloatScaleRounding {

    /**
     * The call over a FLOAT scale.
     *
     * @param args the call's two arguments, the scale second
     * @return the rounded value, a FLOAT, or null when the input or the scale is null
     */
    Object evaluateFloatScale(List<Object> args);
}
