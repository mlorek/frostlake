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

/**
 * A VARIANT that a comparison could not convert to the type beside it — "Failed to cast variant value 1 to DATE"
 * for {@code va = d} (see {@link VariantComparisonOperands}). The failure is the statement's: it holds only
 * where the operands' static types are known, so a filter that retries a row without its relations' types must
 * not read the pair as merely unequal.
 */
public class VariantComparisonCastException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Carry the cast's own failure, in its words.
     *
     * @param failure the cast's failure
     */
    public VariantComparisonCastException(final RuntimeException failure) {
        super(failure.getMessage(), failure);
    }
}
