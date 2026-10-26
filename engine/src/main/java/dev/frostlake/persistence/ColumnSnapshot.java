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
import java.util.List;
import java.util.Map;

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

    /**
     * Whether a BINARY column is the FIXED spelling. Both spellings report the type BINARY, so the
     * name cannot carry it — and SHOW COLUMNS' {@code fixed} cell is the one place it shows.
     * Null on snapshots written before it was persisted, where the NAME still held the spelling.
     */
    public Boolean binaryFixed;
    /**
     * The column's type itself, which the name and the parameters above do not fully carry: they miss a
     * TIMESTAMP's or a TIME's fractional precision, and they restored a VECTOR or a GEOGRAPHY column as
     * text. Null on snapshots written before it was persisted, which restore from those fields.
     */
    public DataType type;
    public boolean nullable;
    public boolean primaryKey;
    public String defaultValue;
    // True when defaultValue is a DEFAULT *expression* text (evaluate per row) rather than a literal.
    // Primitive → old snapshots deserialize it as false (a literal), preserving prior behavior.
    public boolean defaultIsExpression;
    /**
     * A literal default's value itself, which {@link #defaultValue} holds only as text; null for an expression, and
     * on a snapshot written before it was kept, which restores the text.
     */
    public Serializable defaultLiteral;
    /**
     * An expression default as it is told back, where that is not the default as written. Null on snapshots written
     * before it was persisted, which tell the default as written.
     */
    public String defaultTold;
    public String comment;
    // Qualified name of an attached masking policy, or null. Old snapshots predate this field (null on load).
    public String maskingPolicyName;
    // A conditional masking policy's USING columns, the masked column first; null on snapshots that predate it.
    public List<String> maskingPolicyArguments;
    public String projectionPolicyName;
    // Constraint/identity metadata. Primitives default to false/0 on old snapshots; objects to null.
    public boolean unique;
    public boolean autoIncrement;
    public long identityStart;
    public long identityIncrement;
    public boolean identityOrdered;
    public String collation;
    /**
     * An Iceberg table's column's type in the format's own terms (see TableColumn#getFormatType); null for every other
     * column, and on a snapshot written before it was kept.
     */
    public DataType formatType;
    /**
     * A virtual column's expression as written and as it is told back (see TableColumn#getVirtualExpression); null for
     * every other column, and on a snapshot written before it was kept.
     */
    public String virtualExpression;
    public String virtualText;

    // The position the table gave this column: kept so a restored table reports the same gaps a
    // dropped column left behind. 0 in a snapshot written before positions were recorded.
    public int ordinalPosition;
    // Column-level FOREIGN KEY (REFERENCES) target + actions, or null when the column has no reference.
    public String referencedTable;
    public String referencedColumn;
    public String onDelete;
    public String onUpdate;
    public Boolean rely;  // null = unspecified, TRUE = RELY, FALSE = NORELY
    // Names of the constraints this column carries as flags — its single-column UNIQUE constraint and the
    // FOREIGN KEY an inline REFERENCES declares. Null on old snapshots (and whenever the column has no such
    // constraint), in which case the name regenerates on first use as it always did.
    public String uniqueConstraintName;
    public String foreignKeyConstraintName;

    // The object's tag associations, tag name -> value. Null in a snapshot written before tags were
    // recorded, which reads back as an object carrying none.
    public Map<String, String> tags;
}
