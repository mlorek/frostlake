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
import dev.frostlake.values.ValueRange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    // Whether the identity was declared ORDER: its numbers are handed out in order. NOORDER is the default.
    private boolean identityOrdered;
    private String comment;
    private String collation;

    // The position the table gave this column, counted from 1 and never reused: dropping a column
    // leaves its number behind, and the next column added takes one past the highest ever given.
    private int ordinalPosition;

    // Foreign key metadata (not enforced)
    private String referencedTable;
    private String referencedColumn;
    private ReferentialAction onDelete;
    private ReferentialAction onUpdate;

    // Constraint metadata (not enforced)
    private Boolean rely;  // null = not specified, true = RELY, false = NORELY

    // Security policy references
    private String maskingPolicyName;  // qualified name of attached masking policy, or null
    // A conditional masking policy's USING columns, the masked column first, by canonical name; empty for a policy
    // attached without USING.
    private List<String> maskingPolicyArguments = new ArrayList<>();
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
    private boolean heldToDeclaredWidth;

    /** Whether the column belongs to a relation the plan has no statistics of: a VALUES clause's. */
    private boolean withoutStatistics;

    // A DERIVED relation's column's interval of values, propagated from the inner projection the way
    // the account propagates statistics; null when none is known. A catalog column never carries one —
    // its interval is read from its rows when asked.
    private ValueRange valueRange;

    // A DERIVED relation's column projecting a bare string literal: the NUMBER the literal spells, which
    // a conditional folds the column by (see getSpelledNumber); null for every other column.
    private DataType spelledNumber;

    // A DERIVED relation's column projecting a number or boolean constant wrapped into a VARIANT, which a
    // cast to a sized VARCHAR converts unchecked (see isUncheckedConstant); false for every other column.
    private boolean uncheckedConstant;

    // A DERIVED relation's column projecting a double live's compiler folds, which TO_VARIANT wraps keeping
    // its FLOAT origin (see isFoldedDouble); false for every other column.
    private boolean foldedDouble;
    // Whether the column holds nothing but an untyped NULL (see isUntypedNull); false for every other column.
    private boolean untypedNull;
    // Whether a DERIVED relation's column projects the word NULL cast to a text type (see isNullText); false for
    // every other column.
    private boolean nullText;
    // How a DERIVED relation's column reads a stored table's column (see getStoredRead); NONE for every other column.
    private StoredRead storedRead = StoredRead.NONE;
    // Whether a DERIVED relation's column's values can be NULL, as its projection judged its item (see
    // getValuesNullable); null for a column no projection judged.
    private Boolean valuesNullable;

    // Whether a DERIVED relation's column holds its collation because a COLLATE named it (see
    // isCollationExplicit); false for every other column, a catalog column's among them.
    private boolean collationExplicit;

    // The type in the Iceberg format's own terms of an Iceberg table's column a column definition typed (see
    // getFormatType); null for every other column.
    private DataType formatType;

    // The expression a virtual column's values are computed from, as written (see getVirtualExpression), and as the
    // account tells it back (see getVirtualText); null for every other column.
    private String virtualExpression;
    private String virtualText;

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
     * The position this column holds in its table, counted from 1 — what INFORMATION_SCHEMA.COLUMNS
     * reports. A column keeps the number it was given, so a dropped column leaves a gap and no later
     * column takes its number. 0 on a column that never joined a table (a derived relation's).
     */
    public int getOrdinalPosition() {
        return ordinalPosition;
    }

    /** Give this column its position; the table does this as the column joins it. */
    public void setOrdinalPosition(final int ordinalPosition) {
        this.ordinalPosition = ordinalPosition;
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

    public ValueRange getValueRange() {
        return valueRange;
    }

    /**
     * Whether this is a view's column, whose values a reader holds to the column's declared width: read through
     * the view, a NUMBER(18,2) column is told at most eighteen digits wide whatever its body could add up to
     * (live-verified). An aggregate over the column reads the body's own interval.
     *
     * @return true for a view's column
     */
    public boolean isHeldToDeclaredWidth() {
        return heldToDeclaredWidth;
    }

    public void setHeldToDeclaredWidth(final boolean heldToDeclaredWidth) {
        this.heldToDeclaredWidth = heldToDeclaredWidth;
    }

    /**
     * Whether the plan has no statistics of the column's relation — a VALUES clause's. Such a column carries no
     * value interval, so an exact number read from it is tagged by its declared precision alone, and the plan
     * takes it to vary whatever its rows hold (live-verified).
     *
     * @return true for a VALUES clause's column
     */
    public boolean isWithoutStatistics() {
        return withoutStatistics;
    }

    public void setWithoutStatistics(final boolean withoutStatistics) {
        this.withoutStatistics = withoutStatistics;
    }

    public void setValueRange(final ValueRange valueRange) {
        this.valueRange = valueRange;
    }

    /**
     * The type in the Iceberg format's own terms of an Iceberg table's column a column definition typed — in the
     * list of CREATE ICEBERG TABLE or by ADD COLUMN: an int, a long, a float or a double where {@link #getDataType()}
     * holds the NUMBER or the FLOAT it reports. GET_DDL spells it in the format's names, a change of the column's
     * type is judged by the format's promotions, and a value written is held to it (see {@code IcebergTypes}). A
     * column a query or LIKE gave the table has none.
     *
     * @return the format's type, or null
     */
    public DataType getFormatType() {
        return formatType;
    }

    /**
     * Gives the column its type in the Iceberg format's own terms.
     *
     * @param formatType the format's type, or null for none
     */
    public void setFormatType(final DataType formatType) {
        this.formatType = formatType;
    }

    /**
     * Whether the column has a type in the Iceberg format's own terms.
     *
     * @return true when {@link #getFormatType()} is not null
     */
    public boolean isFormatTyped() {
        return formatType != null;
    }

    /**
     * The expression a virtual column's values are computed from, as its definition wrote it: {@code a + 1} for
     * {@code b INT AS (a + 1)}. No statement writes such a column; its value follows the row's other columns.
     *
     * @return the expression, or null for a column that is not virtual
     */
    public String getVirtualExpression() {
        return virtualExpression;
    }

    /**
     * The expression as DESCRIBE TABLE, SHOW COLUMNS and GET_DDL tell it: {@code A + 1} for {@code a + 1}.
     *
     * @return the text, or null for a column that is not virtual
     */
    public String getVirtualText() {
        return virtualText;
    }

    /**
     * Makes the column virtual.
     *
     * @param expression the expression as written
     * @param text       the expression as the account tells it back
     */
    public void setVirtualExpression(final String expression, final String text) {
        this.virtualExpression = expression;
        this.virtualText = text;
    }

    /**
     * Whether the column is virtual.
     *
     * @return true when {@link #getVirtualExpression()} is not null
     */
    public boolean isVirtual() {
        return virtualExpression != null;
    }

    /**
     * The NUMBER this column's values spell when a derived relation projects a bare string literal as
     * it — {@code (SELECT '12.5' AS t, …)} — or projects another such column; null for every other
     * column. A conditional folds the column by it as it folds the literal itself: live types
     * {@code COALESCE(t, n)} over that derived table exactly as {@code COALESCE('12.5', n)}.
     *
     * @return the literal's measured NUMBER, or null
     */
    public DataType getSpelledNumber() {
        return spelledNumber;
    }

    public void setSpelledNumber(final DataType spelledNumber) {
        this.spelledNumber = spelledNumber;
    }

    /**
     * Whether this DERIVED relation's column projects a number or boolean constant wrapped into a VARIANT,
     * or projects another such column. Live folds the constant through the relation, so a cast to a sized
     * VARCHAR converts it unchecked as it converts the wrap written in place: {@code WITH c AS (SELECT
     * TO_VARIANT(123) AS v) SELECT v::VARCHAR(1) FROM c} is {@code 123}. A catalog column never is one.
     *
     * @return true for such a column
     */
    public boolean isUncheckedConstant() {
        return uncheckedConstant;
    }

    public void setUncheckedConstant(final boolean uncheckedConstant) {
        this.uncheckedConstant = uncheckedConstant;
    }

    /**
     * Whether this DERIVED relation's column projects a double live's compiler folds, or projects another
     * such column: {@code WITH c AS (SELECT 2::FLOAT AS f) SELECT TO_VARIANT(f)::VARCHAR FROM c} is
     * {@code 2.0}, as the wrap of {@code 2::FLOAT} written in place is, where a computed double is {@code 2}.
     * A catalog column never is one.
     *
     * @return true for such a column
     */
    /**
     * Whether the column holds nothing but an untyped NULL: DUAL's COLUMN1, a VALUES column written as NULL in
     * every row, a derived column over the bare word. It has no type of its own, so every type rule reads a
     * reference to it as the bare word NULL, while the column itself describes as a zero-width string
     * (live-verified).
     *
     * @return true for such a column
     */
    public boolean isUntypedNull() {
        return untypedNull;
    }

    public void setUntypedNull(final boolean untypedNull) {
        this.untypedNull = untypedNull;
    }

    /**
     * Whether this DERIVED relation's column projects the word NULL cast to a text type, {@code NULL::VARCHAR(10)}:
     * the column is the text type it names, and an exact number beside a reference to it reads it as the bare word
     * NULL, as it reads the cast itself (live-verified).
     *
     * @return true for such a column
     */
    public boolean isNullText() {
        return nullText;
    }

    public void setNullText(final boolean nullText) {
        this.nullText = nullText;
    }

    /**
     * How this DERIVED relation's column reads a stored table's column: the column whole or an element or a path of
     * it, carried through every relation that projects it as it is (see StoredRead). A catalog column answers NONE;
     * its own table is the stored one.
     *
     * @return how it reads one
     */
    public StoredRead getStoredRead() {
        return storedRead;
    }

    public void setStoredRead(final StoredRead storedRead) {
        this.storedRead = storedRead;
    }

    /**
     * Whether a derived relation's column's values can be NULL, as the projection that made it judged its item: a
     * literal, a cast of one and an operation over such items cannot, which a refusal spells {@code {not null}}.
     *
     * @return the judgement, or null where no projection made one
     */
    public Boolean getValuesNullable() {
        return valuesNullable;
    }

    public void setValuesNullable(final Boolean valuesNullable) {
        this.valuesNullable = valuesNullable;
    }

    public boolean isFoldedDouble() {
        return foldedDouble;
    }

    public void setFoldedDouble(final boolean foldedDouble) {
        this.foldedDouble = foldedDouble;
    }

    /**
     * Whether this DERIVED relation's column holds its collation because a COLLATE named it, in the query
     * that built the relation or in one below it: such a collation outranks a column's declared one wherever
     * the two meet, so {@code SELECT … FROM (SELECT c COLLATE 'en-cs' AS x, c FROM t) WHERE x = c} compares
     * under {@code en-cs}. A catalog column never does — a table created from such a query declares the
     * collation as its own.
     *
     * @return true for a named collation
     */
    public boolean isCollationExplicit() {
        return collationExplicit;
    }

    public void setCollationExplicit(final boolean collationExplicit) {
        this.collationExplicit = collationExplicit;
    }

    /**
     * A copy of this column with another type, nullability and key flags — the fields its constructor fixes —
     * carrying everything else the column holds: comment, collation, position, foreign-key reference, RELY, its
     * masking and projection policies, its tags and its typing flags. Every rebuild of a declared column goes
     * through here, so re-flagging one for a PRIMARY KEY or UNIQUE clause, or retyping it, never drops a policy
     * or a tag: the constraint clauses of CREATE TABLE used to strip both.
     *
     * @param newType       the type the copy takes
     * @param newNullable   whether it accepts NULL
     * @param newPrimaryKey whether it is part of the primary key
     * @param newUnique     whether it is unique
     * @return the copy
     */
    public TableColumn rebuiltCopy(final DataType newType, final boolean newNullable, final boolean newPrimaryKey,
                                   final boolean newUnique) {
        return carriedInto(new TableColumn(name, newType, newNullable, defaultValue, newPrimaryKey,
            newUnique, autoIncrement, identityStart, identityIncrement));
    }

    /**
     * A copy of this column under another name, carrying everything else it holds: type, nullability, default, key
     * flags, IDENTITY, comment, collation, position, foreign-key reference, its masking and projection policies and
     * its tags. A rename moves the name and nothing else, so it is no way around a policy.
     *
     * @param newName the name the copy takes
     * @return the copy
     */
    public TableColumn renamedCopy(final String newName) {
        return carriedInto(new TableColumn(newName, dataType, nullable, defaultValue, primaryKey,
            unique, autoIncrement, identityStart, identityIncrement));
    }

    /** {@code copy} given every attribute of this column its constructor does not take; answers the copy. */
    private TableColumn carriedInto(final TableColumn copy) {
        copy.comment = comment;
        copy.identityOrdered = identityOrdered;
        copy.collation = collation;
        copy.collationExplicit = collationExplicit;
        copy.ordinalPosition = ordinalPosition;
        copy.referencedTable = referencedTable;
        copy.referencedColumn = referencedColumn;
        copy.onDelete = onDelete;
        copy.onUpdate = onUpdate;
        copy.rely = rely;
        copy.maskingPolicyName = maskingPolicyName;
        copy.maskingPolicyArguments = new ArrayList<>(maskingPolicyArguments);
        copy.projectionPolicyName = projectionPolicyName;
        copy.tags.putAll(tags);
        copy.withoutStatistics = withoutStatistics;
        copy.hiddenFromStar = hiddenFromStar;
        copy.staticallyTyped = staticallyTyped;
        copy.valueRange = valueRange;
        copy.spelledNumber = spelledNumber;
        copy.uncheckedConstant = uncheckedConstant;
        copy.foldedDouble = foldedDouble;
        copy.untypedNull = untypedNull;
        copy.nullText = nullText;
        copy.valuesNullable = valuesNullable;
        copy.formatType = formatType;
        copy.virtualExpression = virtualExpression;
        copy.virtualText = virtualText;
        return copy;
    }

    /** A copy of this column with every attribute it has, as a clone of its table carries it. */
    public TableColumn copy() {
        return rebuiltCopy(dataType, nullable, primaryKey, unique);
    }

    /** A copy of this column marked hidden from {@code SELECT *} — used for the right-side duplicate
     *  of a USING / NATURAL join column in a merged join view (the shared catalog instance stays
     *  untouched). */
    public TableColumn starHiddenCopy() {
        final TableColumn copy = new TableColumn(name, dataType, nullable, defaultValue, primaryKey,
            unique, autoIncrement, identityStart, identityIncrement);
        copy.comment = comment;
        copy.collation = collation;
        copy.collationExplicit = collationExplicit;
        copy.referencedTable = referencedTable;
        copy.referencedColumn = referencedColumn;
        copy.onDelete = onDelete;
        copy.onUpdate = onUpdate;
        copy.rely = rely;
        copy.maskingPolicyName = maskingPolicyName;
        copy.projectionPolicyName = projectionPolicyName;
        copy.tags.putAll(tags);
        copy.hiddenFromStar = true;
        copy.staticallyTyped = staticallyTyped;
        copy.valueRange = valueRange;
        copy.spelledNumber = spelledNumber;
        copy.uncheckedConstant = uncheckedConstant;
        copy.foldedDouble = foldedDouble;
        copy.untypedNull = untypedNull;
        copy.nullText = nullText;
        copy.valuesNullable = valuesNullable;
        return copy;
    }

    /**
     * A copy of this column carrying another type and nullability, for a shape change that keeps the
     * column itself — {@code CREATE OR ALTER TABLE} widening a VARCHAR or setting NOT NULL.
     *
     * @param newType     the type the column takes
     * @param newNullable whether it accepts NULL
     * @return the copy
     */
    public TableColumn retypedCopy(final DataType newType, final boolean newNullable) {
        return rebuiltCopy(newType, newNullable, primaryKey, unique);
    }

    /** A copy of this column that accepts NULL — the null-extended side of an outer join, where the
     *  shared catalog instance must keep its own NOT NULL. */
    public TableColumn nullableCopy() {
        final TableColumn copy = new TableColumn(name, dataType, true, defaultValue, primaryKey,
            unique, autoIncrement, identityStart, identityIncrement);
        copy.comment = comment;
        copy.collation = collation;
        copy.collationExplicit = collationExplicit;
        copy.referencedTable = referencedTable;
        copy.referencedColumn = referencedColumn;
        copy.onDelete = onDelete;
        copy.onUpdate = onUpdate;
        copy.rely = rely;
        copy.maskingPolicyName = maskingPolicyName;
        copy.projectionPolicyName = projectionPolicyName;
        copy.tags.putAll(tags);
        copy.hiddenFromStar = hiddenFromStar;
        copy.staticallyTyped = staticallyTyped;
        copy.valueRange = valueRange;
        copy.spelledNumber = spelledNumber;
        // The null-extended side no longer folds its constant: a cast of it is checked (live-verified).
        return copy;
    }

    @Override
    public void setTag(final String tagName, final String value) {
        tags.put(tagName.toUpperCase(Locale.ROOT), value);
    }

    @Override
    public void unsetTag(final String tagName) {
        tags.remove(tagName.toUpperCase(Locale.ROOT));
    }

    @Override
    public String getTagValue(final String tagName) {
        return tags.get(tagName.toUpperCase(Locale.ROOT));
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

    /** Whether the column's identity was declared ORDER; false for NOORDER, for none and for no identity. */
    public boolean isIdentityOrdered() {
        return identityOrdered;
    }

    public void setIdentityOrdered(final boolean identityOrdered) {
        this.identityOrdered = identityOrdered;
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

    /**
     * The columns a conditional masking policy reads, as its USING clause names them: the masked column first, then
     * one per further argument of the policy's signature. Empty when the policy was attached without USING.
     *
     * @return the canonical column names, in order
     */
    public List<String> getMaskingPolicyArguments() {
        return new ArrayList<>(maskingPolicyArguments);
    }

    public void setMaskingPolicyArguments(final List<String> columns) {
        this.maskingPolicyArguments = columns != null ? new ArrayList<>(columns) : new ArrayList<String>();
    }
    public boolean hasMaskingPolicy() { return maskingPolicyName != null && !maskingPolicyName.isEmpty(); }
}
