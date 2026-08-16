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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.SqlTypeNames;

/**
 * The type rule a masking policy attachment must pass. It is NOT the row access policy's rule
 * reworded: live names no column here, and spells the policy's type in the INTERNAL vocabulary —
 * TEXT rather than VARCHAR(n), FIXED rather than NUMBER(p,s), REAL rather than FLOAT — while the
 * column keeps its canonical SQL spelling. Matching itself is by family, parameters ignored.
 */
public final class MaskingPolicyAttachment {

    private MaskingPolicyAttachment() {
    }

    /**
     * @param policy the policy being attached, or null when it could not be resolved (checked earlier)
     * @param column the column it is being attached to
     */
    public static void requireTypeMatch(final MaskingPolicy policy, final TableColumn column) {
        if (policy == null || policy.getParameters().isEmpty()) {
            return;
        }
        final DataType policyType = policy.getParameters().get(0).getDataType();
        if (!SqlTypeNames.sameFamily(column.getDataType(), policyType)) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " COLUMN data type "
                + SqlTypeNames.canonical(column.getDataType()) + " does not match with masking policy"
                + " data type " + SqlTypeNames.internalName(policyType) + ".");
        }
    }
}
