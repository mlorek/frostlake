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

package dev.frostlake.persistence;

import dev.frostlake.types.DataType;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializable snapshot of a MASKING POLICY definition. The policy-to-column binding is stored separately
 * on the column (see {@link ColumnSnapshot#maskingPolicyName}), so masking is re-enforced after a reload.
 */
public class MaskingPolicySnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String returnType;
    /**
     * The declared RETURNS type itself, whose width the name cannot spell: {@code RETURNS VARCHAR(5)} came back as
     * {@code RETURNS VARCHAR}. Null on snapshots written before it was saved, which fall back to the name.
     */
    public DataType returnDataType;
    /** The RETURNS type as a description spells it; null on snapshots written before it was saved. */
    public String returnSpelling;
    public String body;
    public String comment;
    public String owner;
    /** EXEMPT_OTHER_POLICIES as the CREATE set it; false on snapshots written before it was saved. */
    public boolean exemptOtherPolicies;
    public List<MaskingPolicyParameterSnapshot> parameters = new ArrayList<>();
    // When the object was created. Null on snapshots written before it was saved, which restore it as new.
    public Instant createdAt;
}
