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

/**
 * Serializable snapshot of a single MASKING POLICY parameter (name + data-type name + optional default).
 */
public class MaskingPolicyParameterSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String dataType;
    /**
     * The type itself, so every declared parameter survives a checkpoint or an engine clone. The type NAME
     * alone restored NUMBER(8,4) as NUMBER(38,0), VARCHAR(10) as VARCHAR, TIMESTAMP_NTZ(3) as
     * TIMESTAMP_NTZ(9) and a structured ARRAY or OBJECT as the plain one. Null on snapshots written before
     * it was persisted, which restore from the name as they always did.
     */
    public DataType type;
    public String defaultValue;
}
