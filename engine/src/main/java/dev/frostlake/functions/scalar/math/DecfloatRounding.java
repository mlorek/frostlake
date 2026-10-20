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
 * CEIL, FLOOR, ROUND and TRUNC over a DECFLOAT. A DECFLOAT is a bare BigDecimal at row time, as a NUMBER is, so only
 * the evaluator, which knows the argument's static type, can tell the function which family it rounds (see
 * {@link RoundedValue#ofDecfloat}).
 */
public interface DecfloatRounding {

    /**
     * The call over a DECFLOAT input.
     *
     * @param args the call's arguments, the DECFLOAT first
     * @return the rounded value, or null when the input, the scale or the mode is null
     */
    Object evaluateDecfloat(List<Object> args);
}
