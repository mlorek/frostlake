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

package dev.frostlake.metastore.model;

import dev.frostlake.metastore.Taggable;
import dev.frostlake.types.DataType;

import java.util.HashMap;
import java.util.Map;

public class TableColumn implements Taggable {
    private final String name;
    private final DataType dataType;
    private boolean nullable;
    private Object defaultValue;
    private final boolean primaryKey;
    private final boolean unique;
    private final boolean autoIncrement;
    private final long identityStart;
    private final long identityIncrement;
    private String comment;
    private String collation;

    // Foreign key metadata (not enforced)
    private String referencedTable;
    private String referencedColumn;
    private ReferentialAction onDelete;
    private ReferentialAction onUpdate;

    // Constraint metadata (not enforced)
    private Boolean rely;  // null = not specified, true = RELY, false = NORELY

    // Security policy references
    private String maskingPolicyName;  // qualified name of attached masking policy, or null
    private String projectionPolicyName;  // qualified name of attached projection policy, or null

    // Object tags applied via ALTER TABLE ... ALTER COLUMN ... SET TAG (canonical upper-cased name -> value)
    private final Map<String, String> tags = new HashMap<>();

    // Resolvable by name/qualifier but omitted from SELECT * — the right-side duplicate of a
    // JOIN ... USING / NATURAL JOIN column in a merged join view. Never set on catalog columns.
    private boolean hiddenFromStar;

    // A DERIVED relation's column (subquery, CTE, view) whose declared type was inferred from the inner
    // projection rather than guessed. Catalog columns leave this false — their table is trusted by
    // identity instead — so the flag only ever ADDS a column the type rules may read, never removes one.
    private boolean staticallyTyped;

    public TableColumn(final String name, final DataType dataType, final boolean nullable,
                 final Object defaultValue, final boolean primaryKey, final boolean unique,
                 final boolean autoIncrement) {
        this(name, dataType, nullable, defaultValue, primaryKey, unique, autoIncrement, 1, 1);
    }

    public TableColumn(final String name, final DataType dataType, final boolean nullable,
                 final Object defaultValue, final boolean primaryKey, final boolean unique,
                 final boolean autoIncrement, final long identityStart, final long identityIncrement) {
        this.name = name;
        this.dataType = dataType;
        this.nullable = nullable;
        this.defaultValue = defaultValue;
        this.primaryKey = primaryKey;
        this.unique = unique;
        this.autoIncrement = autoIncrement;
        this.identityStart = identityStart;
        this.identityIncrement = identityIncrement;
    }

    public String getName() {
        return name;
    }

    public boolean isHiddenFromStar() {
        return hiddenFromStar;
    }

    /**
     * Whether {@link #getDataType()} is this column's STATICALLY KNOWN type. Set only on a derived
     * relation's columns, where the inner projection's inferred type is carried out to the enclosing
     * query so a type-based rule fires through a subquery, CTE or view exactly as it does over the base
     * table. False on catalog columns, whose table is trusted by catalog identity.
     */
    public boolean isStaticallyTyped() {
        return staticallyTyped;
    }

    public void setStaticallyTyped(final boolean staticallyTyped) {
        this.staticallyTyped = staticallyTyped;
    }

    /** A copy of this column marked hidden from {@code SELECT *} — used for the right-side duplicate
     *  of a USING / NATURAL join column in a merged join view (the shared catalog instance stays
     *  untouched). */
    public TableColumn starHiddenCopy() {
        final TableColumn copy = new TableColumn(name, dataType, nullable, defaultValue, primaryKey,
            unique, autoIncrement, identityStart, identityIncrement);
        copy.comment = comment;
        copy.collation = collation;
        copy.referencedTable = referencedTable;
        copy.referencedColumn = referencedColumn;
        copy.onDelete = onDelete;
        copy.onUpdate = onUpdate;
        copy.rely = rely;
        copy.maskingPolicyName = maskingPolicyName;
        copy.tags.putAll(tags);
        copy.hiddenFromStar = true;
        copy.staticallyTyped = staticallyTyped;
        return copy;
    }

    /** A copy of this column that accepts NULL — the null-extended side of an outer join, where the
     *  shared catalog instance must keep its own NOT NULL. */
    public TableColumn nullableCopy() {
        final TableColumn copy = new TableColumn(name, dataType, true, defaultValue, primaryKey,
            unique, autoIncrement, identityStart, identityIncrement);
        copy.comment = comment;
        copy.collation = collation;
        copy.referencedTable = referencedTable;
        copy.referencedColumn = referencedColumn;
        copy.onDelete = onDelete;
        copy.onUpdate = onUpdate;
        copy.rely = rely;
        copy.maskingPolicyName = maskingPolicyName;
        copy.tags.putAll(tags);
        copy.hiddenFromStar = hiddenFromStar;
        copy.staticallyTyped = staticallyTyped;
        return copy;
    }

    @Override
    public void setTag(final String tagName, final String value) {
        tags.put(tagName.toUpperCase(), value);
    }

    @Override
    public void unsetTag(final String tagName) {
        tags.remove(tagName.toUpperCase());
    }

    @Override
    public String getTagValue(final String tagName) {
        return tags.get(tagName.toUpperCase());
    }

    @Override
    public Map<String, String> getTagValues() {
        return new HashMap<>(tags);
    }

    public DataType getDataType() {
        return dataType;
    }

    public boolean isNullable() {
        return nullable;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public boolean isUnique() {
        return unique;
    }

    public boolean isAutoIncrement() {
        return autoIncrement;
    }

    public long getIdentityStart() {
        return identityStart;
    }

    public long getIdentityIncrement() {
        return identityIncrement;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
    }

    public void setNullable(final boolean nullable) {
        this.nullable = nullable;
    }

    public void setDefaultValue(final Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getCollation() {
        return collation;
    }

    public void setCollation(final String collation) {
        this.collation = collation;
    }

    // Foreign key getters and setters
    public String getReferencedTable() {
        return referencedTable;
    }

    public void setReferencedTable(final String referencedTable) {
        this.referencedTable = referencedTable;
    }

    public String getReferencedColumn() {
        return referencedColumn;
    }

    public void setReferencedColumn(final String referencedColumn) {
        this.referencedColumn = referencedColumn;
    }

    public String getOnDelete() {
        return onDelete != null ? onDelete.getSqlText() : null;
    }

    public void setOnDelete(final String onDelete) {
        this.onDelete = ReferentialAction.fromString(onDelete);
    }

    public String getOnUpdate() {
        return onUpdate != null ? onUpdate.getSqlText() : null;
    }

    public void setOnUpdate(final String onUpdate) {
        this.onUpdate = ReferentialAction.fromString(onUpdate);
    }

    public boolean hasForeignKey() {
        return referencedTable != null;
    }

    public Boolean getRely() {
        return rely;
    }

    public void setRely(final Boolean rely) {
        this.rely = rely;
    }

    /** The projection policy attached to this column, fully qualified, or null. */
    public String getProjectionPolicyName() { return projectionPolicyName; }
    public void setProjectionPolicyName(final String name) { this.projectionPolicyName = name; }
    public boolean hasProjectionPolicy() {
        return projectionPolicyName != null && !projectionPolicyName.isEmpty();
    }

    public String getMaskingPolicyName() { return maskingPolicyName; }
    public void setMaskingPolicyName(final String name) { this.maskingPolicyName = name; }
    public boolean hasMaskingPolicy() { return maskingPolicyName != null && !maskingPolicyName.isEmpty(); }
}
