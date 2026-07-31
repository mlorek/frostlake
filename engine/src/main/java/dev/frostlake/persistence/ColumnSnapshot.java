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

import java.io.Serializable;

/**
 * Serializable snapshot of column metadata
 */
public class ColumnSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String dataType;
    // Type parameters, so NUMBER(38,10) / VARCHAR(16777216) survive a checkpoint round-trip — the
    // bare dataType NAME loses them and a restored engine then computed with the wrong scale.
    // Boxed → old snapshots deserialize them as null and fall back to the name's defaults.
    public Integer precision;
    public Integer scale;
    public Integer maxLength;
    public boolean nullable;
    public boolean primaryKey;
    public String defaultValue;
    // True when defaultValue is a DEFAULT *expression* text (evaluate per row) rather than a literal.
    // Primitive → old snapshots deserialize it as false (a literal), preserving prior behavior.
    public boolean defaultIsExpression;
    public String comment;
    // Qualified name of an attached masking policy, or null. Old snapshots predate this field (null on load).
    public String maskingPolicyName;
    // Constraint/identity metadata. Primitives default to false/0 on old snapshots; objects to null.
    public boolean unique;
    public boolean autoIncrement;
    public long identityStart;
    public long identityIncrement;
    public String collation;
    // Column-level FOREIGN KEY (REFERENCES) target + actions, or null when the column has no reference.
    public String referencedTable;
    public String referencedColumn;
    public String onDelete;
    public String onUpdate;
    public Boolean rely;  // null = unspecified, TRUE = RELY, FALSE = NORELY
}
