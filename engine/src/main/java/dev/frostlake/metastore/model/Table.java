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

import dev.frostlake.executor.ExpressionColumns;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.SnapshotSequence;
import dev.frostlake.types.DataType;
import dev.frostlake.values.RelationStatistics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class Table extends SqlObject {

    /**
     * The DATA_RETENTION_TIME_IN_DAYS this object declares, or null when it declares none and
     * inherits its container's (the account default is 1 at the top of the chain).
     */
    private Integer dataRetentionTimeInDays;

    public Integer getDataRetentionTimeInDays() {
        return dataRetentionTimeInDays;
    }

    public void setDataRetentionTimeInDays(final Integer dataRetentionTimeInDays) {
        this.dataRetentionTimeInDays = dataRetentionTimeInDays;
    }


    private final List<TableColumn> columns;
    // The highest position ever given to a column of this table; a dropped column's number stays spent.
    private int highestOrdinal;
    private final List<String> primaryKeys;
    private final List<ForeignKeyConstraint> foreignKeys;
    // Two indexes over one column list. A column is found by its EXACT name first — "x" and "X" are two
    // columns, and a name that spells one of them exactly must reach that one — and only then by its
    // upper-cased fold, which keeps this internal Java API lenient for callers that pass a name in
    // another case; the fold keeps the FIRST column of each folded spelling.
    private final Map<String, Integer> columnIndex;
    private final Map<String, Integer> exactColumnIndex;
    private final boolean isTemporary;
    private boolean isTransient;
    // Declared with CREATE HYBRID TABLE. Frostlake stores hybrid tables as ordinary tables (row storage,
    // constraints not specially enforced); this flag only preserves the declaration for SHOW HYBRID TABLES
    // and the reported kind.
    private boolean hybrid;
    // Declared with CREATE EVENT TABLE: the fixed OpenTelemetry column set, reported by SHOW EVENT TABLES.
    private boolean eventTable;
    // Declared with CREATE ICEBERG TABLE: the Iceberg metadata SHOW ICEBERG TABLES reports; null for other tables.
    private IcebergTableMetadata icebergMetadata;
    private Instant lastDataChange;
    // When a DML statement last ran against the table, whether or not it changed a row; see markDmlRan.
    private Instant lastDml;
    // Writes that reached the rows since the table was made or restored: below the millisecond in getChangeToken.
    private final AtomicLong dataChanges = new AtomicLong();
    private List<String> clusterKeys;
    // CHECK constraints, in declaration order — the order GET_DDL renders them in.
    private final List<CheckConstraint> checkConstraints = new ArrayList<>();
    private String rowAccessPolicyName;  // qualified name of attached row access policy, or null
    private String aggregationPolicyName;  // qualified name of attached aggregation policy, or null
    private String joinPolicyName;  // qualified name of attached join policy, or null
    // SEARCH OPTIMIZATION: the configured expressions, and the next number to hand out. Numbers are
    // never reused, so dropping one leaves the others as they were (live-verified).
    private final List<SearchOptimizationExpression> searchOptimization = new ArrayList<>();
    private int nextSearchOptimizationId = 1;
    // DATA METRIC FUNCTIONS attached to this table, recorded and never evaluated.
    private final List<DataMetricAttachment> dataMetrics = new ArrayList<>();
    // DATA_METRIC_SCHEDULE as written ('60 MINUTE'); the references view renders it as cron.
    private String dataMetricSchedule;
    // The columns whose DISTINCT values count as one entity for the policy's minimum group size.
    private List<String> aggregationEntityKey = new ArrayList<>();
    // Contacts attached for a purpose — ALTER TABLE … SET CONTACT support = c. Purpose (upper) to the
    // contact's fully qualified name. Ordered so the attachments read back in the order they were made.
    private final Map<String, String> contacts = new LinkedHashMap<>();

    /** The table's own stage file format, as written at CREATE TABLE ({@code STAGE_FILE_FORMAT = (...)}). */
    private final Map<String, String> stageFileFormat = new LinkedHashMap<>();

    /** The table's own stage copy options, as written at CREATE TABLE ({@code STAGE_COPY_OPTIONS = (...)}). */
    private final Map<String, String> stageCopyOptions = new LinkedHashMap<>();
    private List<String> rowAccessPolicyColumns = new ArrayList<>();  // columns passed to policy
    // Names for the constraints this table carries as column flags rather than as constraint objects: the
    // PRIMARY KEY (one constraint spanning every PK column) and the per-column UNIQUE / inline-REFERENCES
    // constraints. Either the name an explicit CONSTRAINT <name> clause gave, or an auto-generated
    // SYS_CONSTRAINT_<uuid> (see ConstraintNames) produced on first request and kept until the constraint
    // is dropped, so every metadata surface reports the same name.
    private String primaryKeyConstraintName;
    private final Map<String, String> uniqueConstraintNames = new HashMap<>();
    private final Map<String, String> columnForeignKeyConstraintNames = new HashMap<>();
    // UNIQUE constraints declared at TABLE level — the only ones whose name or multi-column span the
    // per-column flags cannot express. A bare column-level UNIQUE has no entry here; getUniqueConstraints()
    // synthesizes its single-column constraint from the flag.
    private final List<UniqueConstraint> uniqueConstraints = new ArrayList<>();
    // When a constraint an ALTER TABLE added was created, and when an ALTER last changed one, by upper-cased
    // constraint name. A constraint with no entry was made with the table and never altered since.
    private final Map<String, Instant> constraintCreated = new HashMap<>();
    private final Map<String, Instant> constraintAltered = new HashMap<>();
    // Whether this instance is the CATALOG's own object for its name — see isCatalogResident().
    private boolean catalogResident;
    // Set only on a self-join's second instance of a table: the catalog table it was copied from.
    private Table copiedFrom;
    private String qualifiedName;
    // CHANGE_TRACKING: off on creation; flipped by the table option, ALTER … SET, or the creation
    // of a stream over the table (which enables it implicitly). The CHANGES clause requires it.
    private boolean changeTracking;
    /** Where change tracking was last turned on; see {@link #getChangeTrackingSince()}. */
    private long changeTrackingSince;
    private boolean schemaEvolution;
    private boolean reclusterSuspended;
    // Set only on a USING / NATURAL join's merged relation: the join-key column names (upper-cased,
    // in USING-list / left-table order). SELECT * surfaces these first, and a bare reference to one
    // reads the first NON-NULL of its per-side copies — the merged column of an outer join. Null for
    // every ordinary table.
    private List<String> joinKeyNames;
    // Set only on a DERIVED relation that still carries the statistics of the catalog table beneath
    // it — a subquery, CTE or view projecting that table's rows. Null for every other table.
    private RelationStatistics relationStatistics;
    // Set only on a join's merged relation: the relations it joins and which of them an outer join
    // extends with NULLs. Null for every other table.
    private JoinedRelations joinedRelations;
    // Set only on a staged-file query's relation: which positions a $n reads. Null for every other table.
    private StagePositions stagePositions;
    // The value IDENTITY/AUTOINCREMENT hands out next, or null before the first draw, when it is START. The sequence
    // belongs to the table rather than its rows, so a clone, a restored snapshot and an UNDROP continue it instead
    // of handing out values the table already holds.
    private Long nextIdentityValue;
    // The table's shapes over its history, oldest first (see TableStructure). Seeded as the table's storage comes into
    // being, so a derived relation, which never holds rows of its own, keeps none.
    private final List<TableStructure> structureHistory = new ArrayList<>();

    public Table(final String name, final List<TableColumn> columns, final boolean isTemporary) {
        this(name, columns, isTemporary, false);
    }

    /**
     * Whether this instance is a real table the catalog holds, as opposed to one of the many derived
     * relations the executor builds and hands round in the same shape — a CTE result, a VALUES list, a
     * subquery, a merged join view, a self-join copy, a stage scan, a table function's output.
     *
     * <p>Only a schema registering the table sets this ({@code Schema.addTable}), so it answers by
     * IDENTITY and never by name. Asking the catalog to re-resolve the table's bare name instead — what
     * the type channel used to do — silently answered "not a catalog table" for every table reached
     * through a schema-qualified {@code FROM}, because a bare name resolves in the session's CURRENT
     * schema: {@code FROM base.t} while sitting in PUBLIC resolved nothing, and where PUBLIC happened
     * to hold its own {@code t} it resolved the wrong instance. Every column of such a table was then
     * treated as having no declared type, which is why a view over one reported the VARCHAR
     * placeholder for expressions whose type is perfectly well known.
     */
    public boolean isCatalogResident() {
        return catalogResident;
    }

    /** The database.schema.table this instance was last resolved under, or null before any query
     *  read it — the key its rows are stored by. Re-set on every resolution, so a rename catches up. */
    public String getQualifiedName() {
        return qualifiedName;
    }

    public void setQualifiedName(final String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    /** The next METADATA$ROW_ID; see {@link #nextStreamRowId}. */
    private final AtomicLong streamRowIds = new AtomicLong(1);

    /**
     * The row id the next change to this table hands the streams capturing it. The sequence is the table's own, so a
     * rename, a move, a swap or an UNDROP continues it, where one keyed by the table's name started again and handed
     * out ids its streams already held: the row id is "unique, immutable" (Snowflake's documentation).
     *
     * @return the row id
     */
    public long nextStreamRowId() {
        return streamRowIds.getAndIncrement();
    }

    /** The statistics a derived relation carries of the catalog table beneath it, or null. */
    public RelationStatistics getRelationStatistics() {
        return relationStatistics;
    }

    public void setRelationStatistics(final RelationStatistics relationStatistics) {
        this.relationStatistics = relationStatistics;
    }

    /**
     * The catalog table whose declared types, rows and statistics this instance reads: itself when it is
     * one, the table a self-join's second instance was copied from, or null for every other derived
     * relation. A self-join reads the same table twice, and live types and tags both aliases' columns
     * alike — {@code FROM t a JOIN t b} gives {@code b.n} the NUMBER(10,2)[SB2] {@code a.n} has.
     *
     * @return the catalog table beneath this instance, or null
     */
    public Table residentSource() {
        if (catalogResident) {
            return this;
        }
        return copiedFrom != null && copiedFrom.isCatalogResident() ? copiedFrom : null;
    }

    /**
     * Mark this instance as a copy of {@code source} made for a self-join, so it reads the source's
     * declared types and statistics while keeping its own identity in the join.
     *
     * @param source the table the copy was made from
     */
    public void markCopiedFrom(final Table source) {
        this.copiedFrom = source;
    }

    /** Called by {@code Schema.addTable} as the table enters the catalog. One-way on purpose: a
     *  dropped table is unreachable from the query path, and a rename re-registers the same instance. */
    public void markCatalogResident() {
        this.catalogResident = true;
    }

    /** The join-key column names of a USING / NATURAL join's merged relation (upper-cased, in
     *  USING-list / left-table order), or null for an ordinary table. */
    public List<String> getJoinKeyNames() {
        return joinKeyNames;
    }

    public void setJoinKeyNames(final List<String> joinKeyNames) {
        this.joinKeyNames = joinKeyNames;
    }

    /** The relations a join's merged relation joins and which of them an outer join extends with NULLs,
     *  or null for any other table. */
    public JoinedRelations getJoinedRelations() {
        return joinedRelations;
    }

    public void setJoinedRelations(final JoinedRelations joinedRelations) {
        this.joinedRelations = joinedRelations;
    }

    /** Which positions a {@code $n} reads over a staged-file query's relation, or null for any other table. */
    public StagePositions getStagePositions() {
        return stagePositions;
    }

    public void setStagePositions(final StagePositions stagePositions) {
        this.stagePositions = stagePositions;
    }

    public Table(final String name, final List<TableColumn> columns, final boolean isTemporary, final boolean isTransient) {
        super(name);
        this.columns = new ArrayList<>(columns);
        this.primaryKeys = new ArrayList<>();
        this.foreignKeys = new ArrayList<>();
        this.columnIndex = new HashMap<>();
        this.exactColumnIndex = new HashMap<>();
        this.isTemporary = isTemporary;
        this.isTransient = isTransient;
        this.clusterKeys = new ArrayList<>();

        numberColumns();
        reindexColumns();
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).isPrimaryKey()) {
                primaryKeys.add(columns.get(i).getName());
            }
        }
    }

    /**
     * Give every column that has no position yet the next one, and remember the highest position this
     * table has ever handed out. A column that arrives carrying a position keeps it — that is how a
     * restored snapshot and a clone bring their gaps with them.
     */
    private void numberColumns() {
        for (final TableColumn column : columns) {
            if (column.getOrdinalPosition() > 0) {
                highestOrdinal = Math.max(highestOrdinal, column.getOrdinalPosition());
            }
        }
        for (final TableColumn column : columns) {
            if (column.getOrdinalPosition() <= 0) {
                highestOrdinal++;
                column.setOrdinalPosition(highestOrdinal);
            }
        }
    }

    /**
     * The highest position this table has ever given a column — kept so a dropped column's number is
     * never handed out again, and carried through the catalog snapshot.
     */
    public int getHighestOrdinal() {
        return highestOrdinal;
    }

    /** Restore the high-water mark a snapshot recorded. */
    public void setHighestOrdinal(final int highestOrdinal) {
        this.highestOrdinal = Math.max(this.highestOrdinal, highestOrdinal);
    }

    /**
     * A copy of this table's whole definition under another name, as a clone takes it. It carries every column with
     * all of its attributes and its position (gaps and the high-water mark too), the constraints and their names,
     * the IDENTITY sequence where it stands, the attached policies, search optimization, data metrics, contacts,
     * table-stage options, the table's own settings, its comment and its tags. The rows are the storage's to copy.
     * Neither the owner, which the creating statement sets, nor what only a query's derived relations carry is copied.
     *
     * @param newName        the copy's name
     * @param temporary      whether the copy is TEMPORARY
     * @param transientTable whether the copy is TRANSIENT
     * @return the copy
     */
    public Table copyAs(final String newName, final boolean temporary, final boolean transientTable) {
        final List<TableColumn> copiedColumns = new ArrayList<>(columns.size());
        for (final TableColumn column : columns) {
            copiedColumns.add(column.copy());
        }
        final Table copy = new Table(newName, copiedColumns, temporary, transientTable);
        copy.setHighestOrdinal(highestOrdinal);
        copyConstraintsInto(copy);
        copy.clusterKeys = getClusterKeys();
        copy.dataRetentionTimeInDays = dataRetentionTimeInDays;
        copy.hybrid = hybrid;
        copy.eventTable = eventTable;
        copy.icebergMetadata = icebergMetadata != null ? icebergMetadata.copy() : null;
        copy.rowAccessPolicyName = rowAccessPolicyName;
        copy.rowAccessPolicyColumns = new ArrayList<>(rowAccessPolicyColumns);
        copy.aggregationPolicyName = aggregationPolicyName;
        copy.aggregationEntityKey = new ArrayList<>(aggregationEntityKey);
        copy.joinPolicyName = joinPolicyName;
        for (final SearchOptimizationExpression expression : searchOptimization) {
            copy.restoreSearchOptimization(expression);
        }
        copy.nextSearchOptimizationId = Math.max(copy.nextSearchOptimizationId, nextSearchOptimizationId);
        for (final DataMetricAttachment metric : dataMetrics) {
            final DataMetricAttachment copied = new DataMetricAttachment(metric.getMetricName(), metric.getColumns());
            copied.setSuspended(metric.isSuspended());
            copy.dataMetrics.add(copied);
        }
        copy.dataMetricSchedule = dataMetricSchedule;
        copy.contacts.putAll(contacts);
        copy.stageFileFormat.putAll(stageFileFormat);
        copy.stageCopyOptions.putAll(stageCopyOptions);
        // Change tracking on the copy starts with the copy: it holds no change from before it existed.
        copy.setChangeTracking(changeTracking);
        copy.schemaEvolution = schemaEvolution;
        copy.reclusterSuspended = reclusterSuspended;
        copy.nextIdentityValue = getNextIdentityValue();
        // The copy holds the rows' history up to now, so its streams go on from the row ids already handed out.
        copy.streamRowIds.set(streamRowIds.get());
        copy.setComment(getComment());
        copyTagsInto(copy);
        return copy;
    }

    /**
     * A new table shaped like this one, as CREATE TABLE … LIKE makes it: the columns with their names, types,
     * defaults (IDENTITY included, its sequence starting afresh), nullability, collation and comments, the
     * constraints and their names, the clustering keys and the tags. Policies are not set on the new table
     * (documented for masking and row access policies), the table's settings and comment stay behind, and the
     * columns are numbered afresh.
     *
     * @param newName        the new table's name
     * @param temporary      whether it is TEMPORARY
     * @param transientTable whether it is TRANSIENT
     * @return the new table
     */
    public Table likeCopy(final String newName, final boolean temporary, final boolean transientTable) {
        final List<TableColumn> likeColumns = new ArrayList<>(columns.size());
        for (final TableColumn column : columns) {
            final TableColumn like = column.copy();
            like.setMaskingPolicyName(null);
            like.setMaskingPolicyArguments(null);
            like.setProjectionPolicyName(null);
            like.setOrdinalPosition(0);
            likeColumns.add(like);
        }
        final Table copy = new Table(newName, likeColumns, temporary, transientTable);
        copyConstraintsInto(copy);
        copy.clusterKeys = getClusterKeys();
        copyTagsInto(copy);
        return copy;
    }

    /** Copies the foreign keys, CHECK constraints, UNIQUE constraints and every constraint name into a copy. */
    private void copyConstraintsInto(final Table copy) {
        for (final ForeignKeyConstraint fk : foreignKeys) {
            copy.foreignKeys.add(new ForeignKeyConstraint(fk.getConstraintName(), new ArrayList<>(fk.getColumnNames()),
                fk.getReferencedTable(), new ArrayList<>(fk.getReferencedColumns()), fk.getOnDelete(),
                fk.getOnUpdate(), fk.getRely()));
        }
        copy.checkConstraints.addAll(checkConstraints);
        for (final UniqueConstraint constraint : uniqueConstraints) {
            copy.uniqueConstraints.add(new UniqueConstraint(constraint.getConstraintName(),
                new ArrayList<>(constraint.getColumnNames())));
        }
        copy.primaryKeyConstraintName = primaryKeyConstraintName;
        copy.uniqueConstraintNames.putAll(uniqueConstraintNames);
        copy.columnForeignKeyConstraintNames.putAll(columnForeignKeyConstraintNames);
    }

    /** Copies the table-level tag assignments into a copy; the columns carry their own. */
    private void copyTagsInto(final Table copy) {
        for (final Map.Entry<String, String> tag : getTagValues().entrySet()) {
            copy.setTag(tag.getKey(), tag.getValue());
        }
    }

    /**
     * Gives this table, a clone {@link #copyAs} has just made, the columns its source had at the clone's AT | BEFORE
     * point: the columns that stood then, in their order, each with the name, type, nullability, default, comment, key
     * flags and position it had, a column dropped since included, and the highest position handed out by then. A
     * column that still stands keeps the policies and tags attached to it now, so a clone never answers a column its
     * source protects without that protection.
     *
     * <p>A column keeps its position for life and a dropped column's is never handed out again, so what names columns
     * at the table level follows them by position: a column renamed since is named as it was then, and a constraint
     * or CHECK over a column that did not stand then goes. The columns a row access or aggregation policy is attached
     * on follow the renames, and one that did not stand then is kept as written, so the policy refuses the read rather
     * than let rows through. Clustering keys and search optimization are kept as written.
     *
     * @param shape the source's shape at the point
     */
    public void restructureAsOf(final TableStructure shape) {
        final List<TableColumn> then = shape.getColumns();
        final Map<Integer, TableColumn> standing = new HashMap<>();
        for (final TableColumn column : columns) {
            standing.put(column.getOrdinalPosition(), column);
        }
        // Each standing column's name now, upper-cased as the constraint-name maps key it, to its name then.
        final Map<String, String> namedThen = new HashMap<>();
        final List<TableColumn> restored = new ArrayList<>(then.size());
        for (final TableColumn past : then) {
            final TableColumn column = past.copy();
            final TableColumn now = standing.get(past.getOrdinalPosition());
            if (now != null) {
                column.setMaskingPolicyName(now.getMaskingPolicyName());
                column.setMaskingPolicyArguments(now.getMaskingPolicyArguments());
                column.setProjectionPolicyName(now.getProjectionPolicyName());
                for (final String tag : column.getTagValues().keySet()) {
                    column.unsetTag(tag);
                }
                for (final Map.Entry<String, String> tag : now.getTagValues().entrySet()) {
                    column.setTag(tag.getKey(), tag.getValue());
                }
                namedThen.put(now.getName().toUpperCase(), past.getName());
            }
            restored.add(column);
        }
        // A masking policy taken as it is now reads its USING columns by the names they had then.
        for (final TableColumn column : restored) {
            if (standing.containsKey(column.getOrdinalPosition()) && !column.getMaskingPolicyArguments().isEmpty()) {
                column.setMaskingPolicyArguments(renamedWhereStanding(column.getMaskingPolicyArguments(), namedThen));
            }
        }
        columns.clear();
        columns.addAll(restored);
        highestOrdinal = shape.getHighestOrdinal();
        reindexColumns();
        primaryKeys.clear();
        for (final TableColumn column : columns) {
            if (column.isPrimaryKey()) {
                primaryKeys.add(column.getName());
            }
        }
        if (primaryKeys.isEmpty()) {
            primaryKeyConstraintName = null;
        }
        renameKeys(uniqueConstraintNames, namedThen);
        renameKeys(columnForeignKeyConstraintNames, namedThen);
        for (int i = uniqueConstraints.size() - 1; i >= 0; i--) {
            final UniqueConstraint constraint = uniqueConstraints.get(i);
            final List<String> named = namedAsThen(constraint.getColumnNames(), namedThen);
            if (named == null) {
                uniqueConstraints.remove(i);
            } else {
                uniqueConstraints.set(i, new UniqueConstraint(constraint.getConstraintName(), named));
            }
        }
        for (int i = foreignKeys.size() - 1; i >= 0; i--) {
            final ForeignKeyConstraint fk = foreignKeys.get(i);
            final List<String> named = namedAsThen(fk.getColumnNames(), namedThen);
            if (named == null) {
                foreignKeys.remove(i);
            } else {
                foreignKeys.set(i, new ForeignKeyConstraint(fk.getConstraintName(), named, fk.getReferencedTable(),
                    new ArrayList<>(fk.getReferencedColumns()), fk.getOnDelete(), fk.getOnUpdate(), fk.getRely()));
            }
        }
        for (int i = checkConstraints.size() - 1; i >= 0; i--) {
            for (final String referenced : checkConstraints.get(i).getReferencedColumns()) {
                if (indexOf(referenced) == null) {
                    checkConstraints.remove(i);
                    break;
                }
            }
        }
        rowAccessPolicyColumns = renamedWhereStanding(rowAccessPolicyColumns, namedThen);
        aggregationEntityKey = renamedWhereStanding(aggregationEntityKey, namedThen);
        purgeBrokenUniqueConstraints();
    }

    /** Re-keys a map keyed by upper-cased column name to the names then; a column that did not stand then goes. */
    private static void renameKeys(final Map<String, String> byColumn, final Map<String, String> namedThen) {
        final Map<String, String> renamed = new HashMap<>();
        for (final Map.Entry<String, String> entry : byColumn.entrySet()) {
            final String then = namedThen.get(entry.getKey().toUpperCase());
            if (then != null) {
                renamed.put(then.toUpperCase(), entry.getValue());
            }
        }
        byColumn.clear();
        byColumn.putAll(renamed);
    }

    /** The columns as they were named then, or null when one of them did not stand then. */
    private static List<String> namedAsThen(final List<String> names, final Map<String, String> namedThen) {
        final List<String> then = new ArrayList<>(names.size());
        for (final String name : names) {
            final String named = namedThen.get(name.toUpperCase());
            if (named == null) {
                return null;
            }
            then.add(named);
        }
        return then;
    }

    /** The columns as they were named then; one that did not stand then is kept as written. */
    private static List<String> renamedWhereStanding(final List<String> names, final Map<String, String> namedThen) {
        final List<String> then = new ArrayList<>(names.size());
        for (final String name : names) {
            final String named = namedThen.get(name.toUpperCase());
            then.add(named != null ? named : name);
        }
        return then;
    }

    /** The table's IDENTITY/AUTOINCREMENT column, or null when it has none. */
    private TableColumn identityColumn() {
        for (final TableColumn column : columns) {
            if (column.isAutoIncrement()) {
                return column;
            }
        }
        return null;
    }

    /**
     * Draws the next IDENTITY/AUTOINCREMENT value: START on the first draw, then INCREMENT further on each.
     * A table without such a column counts from 1 by 1.
     *
     * @return the value
     */
    public synchronized long drawIdentityValue() {
        final TableColumn identity = identityColumn();
        final long current = nextIdentityValue != null ? nextIdentityValue
            : identity != null ? identity.getIdentityStart() : 1L;
        nextIdentityValue = current + (identity != null ? identity.getIdentityIncrement() : 1L);
        return current;
    }

    /**
     * The value the next IDENTITY draw answers, START before the first one: what a copy and a snapshot carry.
     *
     * @return the value, or null when the table has no IDENTITY column
     */
    public synchronized Long getNextIdentityValue() {
        final TableColumn identity = identityColumn();
        if (identity == null) {
            return null;
        }
        return nextIdentityValue != null ? nextIdentityValue : identity.getIdentityStart();
    }

    /**
     * Continues the IDENTITY sequence from a recorded value; null starts it again at START.
     *
     * @param next the value the next draw answers, or null
     */
    public synchronized void setNextIdentityValue(final Long next) {
        this.nextIdentityValue = next;
    }

    /**
     * Moves the IDENTITY sequence past every value its column holds in {@code rows}, for a table restored from a
     * snapshot written before the sequence was recorded. Such a table restarted at START and handed out values
     * it already held.
     *
     * @param rows the table's rows
     */
    public synchronized void continueIdentityPast(final List<Row> rows) {
        final TableColumn identity = identityColumn();
        if (identity == null) {
            return;
        }
        final int index = columns.indexOf(identity);
        final long increment = identity.getIdentityIncrement();
        long next = nextIdentityValue != null ? nextIdentityValue : identity.getIdentityStart();
        for (final Row row : rows) {
            final Object value = index < row.size() ? row.getValue(index) : null;
            if (value instanceof Number) {
                final long held = ((Number) value).longValue();
                if (increment >= 0 ? held >= next : held <= next) {
                    next = held + increment;
                }
            }
        }
        nextIdentityValue = next;
    }

    /** Rebuild both column indexes from the column list. */
    private void reindexColumns() {
        columnIndex.clear();
        exactColumnIndex.clear();
        for (int i = 0; i < columns.size(); i++) {
            final String name = columns.get(i).getName();
            exactColumnIndex.put(name, i);
            columnIndex.putIfAbsent(name.toUpperCase(), i);
        }
    }

    /**
     * The position of a column: the one spelled exactly as {@code name} when there is one, else the first
     * whose name folds to the same upper-case spelling, else null.
     */
    private Integer indexOf(final String name) {
        final Integer exact = exactColumnIndex.get(name);
        return exact != null ? exact : columnIndex.get(name.toUpperCase());
    }

    /**
     * Whether a column is spelled EXACTLY {@code name}. SQL resolution matches this way — a reference
     * names the canonical spelling it resolves to, unquoted upper-cased and quoted verbatim — where the
     * other accessors also accept a name in another case.
     *
     * @param name the canonical name
     * @return true when a column carries exactly that name
     */
    public boolean hasColumnExactly(final String name) {
        return exactColumnIndex.containsKey(name);
    }

    public List<TableColumn> getColumns() {
        return new ArrayList<>(columns);
    }

    /** The column count WITHOUT copying the column list ({@link #getColumns()} copies per call). */
    public int columnCount() {
        return columns.size();
    }

    /** The column at a position WITHOUT copying the column list — the per-row write path's accessor. */
    public TableColumn columnAt(final int index) {
        return columns.get(index);
    }

    /**
     * A read-only LIVE view of the columns for hot paths that only iterate — an ALTER stays
     * visible through it, and nothing is copied. {@link #getColumns()} keeps returning a
     * defensive copy for callers that hold or mutate their list.
     */
    public List<TableColumn> columnsView() {
        return Collections.unmodifiableList(columns);
    }

    /** Whether any PRIMARY KEY columns are declared, without copying the name list. */
    public boolean hasPrimaryKeyColumns() {
        return !primaryKeys.isEmpty();
    }

    /**
     * The column, or null when there is none — for callers that phrase the miss their own way. Resolves
     * exactly as {@link #getColumn} does, through the same index, so the two can never disagree.
     */
    public TableColumn findColumn(final String name) {
        final Integer index = indexOf(name);
        return index == null ? null : columns.get(index);
    }

    public TableColumn getColumn(final String name) {
        final Integer index = indexOf(name);
        if (index == null) {
            throw new RuntimeException(SqlCompilationError.invalidIdentifier(name));
        }
        return columns.get(index);
    }

    public int getColumnIndex(final String name) {
        final Integer index = indexOf(name);
        if (index == null) {
            throw new RuntimeException(SqlCompilationError.invalidIdentifier(name));
        }
        return index;
    }

    public boolean hasColumn(final String name) {
        return indexOf(name) != null;
    }

    public List<String> getPrimaryKeys() {
        return new ArrayList<>(primaryKeys);
    }

    public List<ForeignKeyConstraint> getForeignKeys() {
        return new ArrayList<>(foreignKeys);
    }

    /**
     * The name of this table's PRIMARY KEY constraint — one constraint however many columns it spans —
     * or null when the table has none: the name an explicit {@code CONSTRAINT <name> PRIMARY KEY} gave it
     * (see {@link #setPrimaryKeyConstraintName}), else an auto-generated {@code SYS_CONSTRAINT_<uuid>}.
     */
    public String primaryKeyConstraintName() {
        if (!hasPrimaryKeyColumn()) {
            return null;
        }
        if (primaryKeyConstraintName == null) {
            primaryKeyConstraintName = ConstraintNames.generate();
        }
        return primaryKeyConstraintName;
    }

    /**
     * Record the name an explicit {@code CONSTRAINT <name> PRIMARY KEY} declaration gave this table's
     * primary key. A null or blank name changes nothing, leaving the constraint to auto-name itself.
     */
    public void setPrimaryKeyConstraintName(final String name) {
        if (name != null && !name.isEmpty()) {
            primaryKeyConstraintName = name;
        }
    }

    /**
     * The name of the single-column UNIQUE constraint a column-level {@code UNIQUE} declares. A column that
     * belongs to a table-level constraint is named by that constraint instead — see
     * {@link #getUniqueConstraints()}.
     */
    public String uniqueConstraintName(final String columnName) {
        return memoizedConstraintName(uniqueConstraintNames, columnName);
    }

    /** Restore a persisted name for the single-column UNIQUE constraint on one column. */
    public void setUniqueConstraintName(final String columnName, final String name) {
        if (name != null && !name.isEmpty()) {
            uniqueConstraintNames.put(columnName.toUpperCase(), name);
        }
    }

    /**
     * Move a constraint to a new name — {@code ALTER TABLE … RENAME CONSTRAINT old TO new}. A name can
     * live in four places (a declared UNIQUE, the primary key, a table-level FOREIGN KEY, and the
     * per-column name an inline UNIQUE or REFERENCES carries), so all four are searched.
     *
     * @return whether a constraint of that name was found and renamed.
     */
    public boolean renameConstraint(final String oldName, final String newName) {
        for (final UniqueConstraint unique : uniqueConstraints) {
            if (unique.getConstraintName().equalsIgnoreCase(oldName)) {
                unique.setConstraintName(newName);
                return true;
            }
        }
        for (final ForeignKeyConstraint foreignKey : foreignKeys) {
            if (foreignKey.getConstraintName().equalsIgnoreCase(oldName)) {
                foreignKey.setConstraintName(newName);
                return true;
            }
        }
        if (primaryKeyConstraintName != null && primaryKeyConstraintName.equalsIgnoreCase(oldName)) {
            primaryKeyConstraintName = newName;
            return true;
        }
        return renameInNameMap(uniqueConstraintNames, oldName, newName)
            || renameInNameMap(columnForeignKeyConstraintNames, oldName, newName);
    }

    /** Rename a per-column constraint name in one of the name maps, if it holds {@code oldName}. */
    private static boolean renameInNameMap(final Map<String, String> names,
            final String oldName, final String newName) {
        for (final Map.Entry<String, String> entry : names.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equalsIgnoreCase(oldName)) {
                entry.setValue(newName);
                return true;
            }
        }
        return false;
    }

    /** The columns a named constraint covers, or an empty list when no constraint carries that name. */
    public List<String> constraintColumns(final String constraintName) {
        for (final UniqueConstraint unique : uniqueConstraints) {
            if (unique.getConstraintName().equalsIgnoreCase(constraintName)) {
                return unique.getColumnNames();
            }
        }
        for (final ForeignKeyConstraint foreignKey : foreignKeys) {
            if (foreignKey.getConstraintName().equalsIgnoreCase(constraintName)) {
                return foreignKey.getColumnNames();
            }
        }
        final List<String> columns = new ArrayList<>();
        if (primaryKeyConstraintName != null && primaryKeyConstraintName.equalsIgnoreCase(constraintName)) {
            for (final TableColumn column : getColumns()) {
                if (column.isPrimaryKey()) {
                    columns.add(column.getName());
                }
            }
            return columns;
        }
        for (final Map<String, String> names
                : java.util.Arrays.asList(uniqueConstraintNames, columnForeignKeyConstraintNames)) {
            for (final Map.Entry<String, String> entry : names.entrySet()) {
                if (entry.getValue() != null && entry.getValue().equalsIgnoreCase(constraintName)) {
                    columns.add(entry.getKey());
                }
            }
        }
        return columns;
    }

    /** Restore a persisted name for the FOREIGN KEY constraint an inline {@code REFERENCES} declares. */
    public void setColumnForeignKeyConstraintName(final String columnName, final String name) {
        if (name != null && !name.isEmpty()) {
            columnForeignKeyConstraintNames.put(columnName.toUpperCase(), name);
        }
    }

    /**
     * Every UNIQUE constraint on this table, ONE entry per constraint (never one per column): the
     * table-level constraints as declared — a {@code UNIQUE (a, b)} is a single constraint with a single
     * name — followed by a single-column constraint for every unique column none of them spans, which is
     * what a bare column-level {@code UNIQUE} declares.
     */
    public List<UniqueConstraint> getUniqueConstraints() {
        final List<UniqueConstraint> all = new ArrayList<>();
        for (final UniqueConstraint declared : uniqueConstraints) {
            if (spansOnlyUniqueColumns(declared)) {
                all.add(declared);
            }
        }
        for (final TableColumn column : columns) {
            if (column.isUnique() && !coveredByDeclaredConstraint(column.getName())) {
                all.add(new UniqueConstraint(uniqueConstraintName(column.getName()),
                    Collections.singletonList(column.getName())));
            }
        }
        return all;
    }

    /**
     * Only the UNIQUE constraints declared at TABLE level, as stored — for persistence and structural
     * copies, which must not turn a column-level {@code UNIQUE} into a table-level constraint on reload.
     * Callers reporting metadata want {@link #getUniqueConstraints()} instead.
     */
    public List<UniqueConstraint> getDeclaredUniqueConstraints() {
        return new ArrayList<>(uniqueConstraints);
    }

    /** True when every column a declared constraint spans still exists and is still flagged unique. */
    private boolean spansOnlyUniqueColumns(final UniqueConstraint constraint) {
        for (final String columnName : constraint.getColumnNames()) {
            final Integer index = indexOf(columnName);
            if (index == null || !columns.get(index).isUnique()) {
                return false;
            }
        }
        return true;
    }

    private boolean coveredByDeclaredConstraint(final String columnName) {
        for (final UniqueConstraint declared : uniqueConstraints) {
            if (declared.covers(columnName) && spansOnlyUniqueColumns(declared)) {
                return true;
            }
        }
        return false;
    }

    /** The name of the FOREIGN KEY constraint an inline {@code REFERENCES} on one column declares. */
    public String columnForeignKeyConstraintName(final String columnName) {
        return memoizedConstraintName(columnForeignKeyConstraintNames, columnName);
    }

    private String memoizedConstraintName(final Map<String, String> names, final String columnName) {
        final String key = columnName.toUpperCase();
        String name = names.get(key);
        if (name == null) {
            name = ConstraintNames.generate();
            names.put(key, name);
        }
        return name;
    }

    private boolean hasPrimaryKeyColumn() {
        for (final TableColumn column : columns) {
            if (column.isPrimaryKey()) {
                return true;
            }
        }
        return false;
    }

    /** Forget the generated names tied to one column, so a re-added constraint gets a fresh name. */
    private void forgetColumnConstraintNames(final String columnName) {
        final String key = columnName.toUpperCase();
        uniqueConstraintNames.remove(key);
        columnForeignKeyConstraintNames.remove(key);
    }

    public void addForeignKey(final ForeignKeyConstraint foreignKey) {
        foreignKeys.add(foreignKey);
    }

    /**
     * Put {@code replacement} where {@code existing} stands, keeping the constraints' order.
     *
     * @param existing    one of this table's foreign keys
     * @param replacement the constraint to stand in its place
     */
    public void replaceForeignKey(final ForeignKeyConstraint existing, final ForeignKeyConstraint replacement) {
        final int at = foreignKeys.indexOf(existing);
        if (at >= 0) {
            foreignKeys.set(at, replacement);
        }
    }

    public void dropForeignKey(final String constraintName) {
        final Iterator<ForeignKeyConstraint> it = foreignKeys.iterator();
        while (it.hasNext()) {
            if (it.next().getConstraintName().equalsIgnoreCase(constraintName)) {
                it.remove();
            }
        }
    }

    /**
     * Drop the PRIMARY KEY, UNIQUE or FOREIGN KEY constraint named {@code constraintName}
     * ({@code ALTER TABLE … DROP CONSTRAINT name}); false when no such constraint carries that name.
     */
    public boolean dropNamedConstraint(final String constraintName) {
        List<String> uniqueColumns = null;
        for (final UniqueConstraint unique : uniqueConstraints) {
            if (unique.getConstraintName().equalsIgnoreCase(constraintName)) {
                uniqueColumns = unique.getColumnNames();
                break;
            }
        }
        if (uniqueColumns != null) {
            dropUnique(uniqueColumns);
            return true;
        }
        for (final ForeignKeyConstraint foreignKey : foreignKeys) {
            if (foreignKey.getConstraintName().equalsIgnoreCase(constraintName)) {
                dropForeignKey(constraintName);
                return true;
            }
        }
        if (constraintName.equalsIgnoreCase(primaryKeyConstraintName())) {
            dropPrimaryKey();
            return true;
        }
        final List<String> columnUniques = columnsNamed(uniqueConstraintNames, constraintName);
        if (!columnUniques.isEmpty()) {
            dropUnique(columnUniques);
            return true;
        }
        final List<String> columnForeignKeys = columnsNamed(columnForeignKeyConstraintNames, constraintName);
        if (!columnForeignKeys.isEmpty()) {
            dropForeignKeyColumns(columnForeignKeys);
            return true;
        }
        return false;
    }

    private static List<String> columnsNamed(final Map<String, String> names, final String constraintName) {
        final List<String> columns = new ArrayList<>();
        for (final Map.Entry<String, String> entry : names.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equalsIgnoreCase(constraintName)) {
                columns.add(entry.getKey());
            }
        }
        return columns;
    }

    public boolean isTemporary() {
        return isTemporary;
    }

    public boolean isTransient() {
        return isTransient;
    }

    /**
     * Makes the table transient. A table in a transient schema or database is transient by definition (documented),
     * so one a CLONE brings into such a container becomes transient with it, as one created there is; nothing makes a
     * transient table permanent again.
     */
    public void markTransient() {
        this.isTransient = true;
    }

    /** Whether the table is an event table. */
    public boolean isEventTable() {
        return eventTable;
    }

    /** Marks the table as an event table. */
    public void setEventTable(final boolean eventTable) {
        this.eventTable = eventTable;
    }

    /** The table's Iceberg metadata, or null when it is not an Iceberg table. */
    public IcebergTableMetadata getIcebergMetadata() {
        return icebergMetadata;
    }

    /** Makes the table an Iceberg table with this metadata, or an ordinary one with null. */
    public void setIcebergMetadata(final IcebergTableMetadata icebergMetadata) {
        this.icebergMetadata = icebergMetadata;
    }

    public boolean isHybrid() {
        return hybrid;
    }

    public void setHybrid(final boolean hybrid) {
        this.hybrid = hybrid;
    }

    public boolean isChangeTracking() {
        return changeTracking;
    }

    public void setChangeTracking(final boolean changeTracking) {
        if (changeTracking && !this.changeTracking) {
            changeTrackingSince = SnapshotSequence.mark();
        }
        this.changeTracking = changeTracking;
    }

    /**
     * Where change tracking was last turned on, in the order table snapshots are taken in: a change to the
     * table before it went untracked. {@link Long#MAX_VALUE} while tracking is off.
     *
     * @return the sequence number, or {@link Long#MAX_VALUE}
     */
    public long getChangeTrackingSince() {
        return changeTracking ? changeTrackingSince : Long.MAX_VALUE;
    }

    /**
     * Whether automatic reclustering is paused — {@code ALTER TABLE … SUSPEND RECLUSTER}. SHOW TABLES'
     * automatic_clustering cell reads ON only for a CLUSTERED table that is not suspended.
     */
    public boolean isReclusterSuspended() {
        return reclusterSuspended;
    }

    public void setReclusterSuspended(final boolean reclusterSuspended) {
        this.reclusterSuspended = reclusterSuspended;
    }

    /** ENABLE_SCHEMA_EVOLUTION — SHOW TABLES reports it as Y/N (live-verified). */
    public boolean isSchemaEvolution() {
        return schemaEvolution;
    }

    public void setSchemaEvolution(final boolean schemaEvolution) {
        this.schemaEvolution = schemaEvolution;
    }

    /**
     * When a statement last wrote this table's rows — an INSERT, UPDATE, DELETE, MERGE, COPY or TRUNCATE —
     * or null when nothing has written them since the table was made.
     */
    public Instant getLastDataChange() {
        return lastDataChange;
    }

    /** Records that a statement wrote this table's rows at {@code instant}. */
    public void markDataChanged(final Instant instant) {
        this.lastDataChange = instant;
        dataChanges.incrementAndGet();
    }

    /** How many statements have written this table's rows: what a snapshot keeps beside {@link #getLastDataChange}. */
    public long getDataChangeCount() {
        return dataChanges.get();
    }

    /**
     * Put back when a statement last wrote this table's rows and how many writes there had been. Rows a snapshot
     * restores, or an UNDROP puts back, are written again, which would otherwise move LAST_ALTERED and the change
     * token to that moment.
     *
     * @param instant the last write, or null when nothing had written the rows
     * @param changes the count of writes
     */
    public void restoreDataChange(final Instant instant, final long changes) {
        this.lastDataChange = instant;
        dataChanges.set(changes);
    }

    /**
     * SYSTEM$LAST_CHANGE_COMMIT_TIME's token for this table: its last write's instant in nanoseconds since the
     * epoch, or its creation's while nothing has written it. Below the millisecond the token counts the writes, so
     * two writes in one millisecond give two tokens. The documentation calls the value a rough estimate of the last
     * change time, to be compared between calls rather than read as a time.
     *
     * @return the token, the same until the table's rows change
     */
    public long getChangeToken() {
        final Instant at = lastDataChange != null ? lastDataChange : getCreatedTime();
        return at.toEpochMilli() * 1_000_000L + dataChanges.get() % 1_000_000L;
    }

    /**
     * A DML statement ran against this table — an INSERT, UPDATE, DELETE or MERGE naming it as its target — at
     * {@code instant}. LAST_ALTERED moves even when the statement changed no row (documented: the column "is updated
     * even when no rows are affected by the DML statement"); the table's data, its change token and what a
     * materialized view refreshes from, stays as it was.
     *
     * @param instant the statement's instant
     */
    public void markDmlRan(final Instant instant) {
        if (lastDml == null || instant.isAfter(lastDml)) {
            lastDml = instant;
        }
    }

    /**
     * INFORMATION_SCHEMA's LAST_ALTERED: the latest of the table's last DDL, the last write to its rows and the last
     * DML run against it. The documentation has both DDL and DML move it, a DML at the instant its statement began,
     * and a DML that changes no row too.
     *
     * @return the instant
     */
    public Instant getLastAlteredTime() {
        Instant latest = getLastDdlTime();
        if (lastDataChange != null && lastDataChange.isAfter(latest)) {
            latest = lastDataChange;
        }
        if (lastDml != null && lastDml.isAfter(latest)) {
            latest = lastDml;
        }
        return latest;
    }

    /**
     * An ALTER TABLE … ADD created the named constraint: TABLE_CONSTRAINTS and REFERENTIAL_CONSTRAINTS report the
     * statement's instant as its CREATED and LAST_ALTERED, where a constraint the CREATE TABLE declared reports the
     * table's.
     *
     * @param constraintName the constraint's name
     */
    public void markConstraintCreated(final String constraintName) {
        final String key = constraintName.toUpperCase(Locale.ROOT);
        constraintCreated.put(key, StatementClock.instant());
        constraintAltered.remove(key);
    }

    /**
     * An ALTER changed the named constraint — its RELY or ENFORCED property — so its LAST_ALTERED moves to the
     * statement's instant.
     *
     * @param constraintName the constraint's name
     */
    public void markConstraintAltered(final String constraintName) {
        constraintAltered.put(constraintName.toUpperCase(Locale.ROOT), StatementClock.instant());
    }

    /**
     * RENAME CONSTRAINT moved a constraint to a new name: it keeps when it was created, and the rename moves its
     * LAST_ALTERED.
     *
     * @param oldName the name it had
     * @param newName the name it has now
     */
    public void markConstraintRenamed(final String oldName, final String newName) {
        final Instant created = constraintCreated.remove(oldName.toUpperCase(Locale.ROOT));
        constraintAltered.remove(oldName.toUpperCase(Locale.ROOT));
        if (created != null) {
            constraintCreated.put(newName.toUpperCase(Locale.ROOT), created);
        }
        markConstraintAltered(newName);
    }

    /**
     * When the named constraint was created: the ALTER TABLE that added it, else the table's own creation, which is
     * when every constraint the CREATE TABLE declared came to be.
     *
     * @param constraintName the constraint's name
     * @return the instant
     */
    public Instant constraintCreatedTime(final String constraintName) {
        final Instant created = constraintName == null ? null
            : constraintCreated.get(constraintName.toUpperCase(Locale.ROOT));
        return created != null ? created : getCreatedTime();
    }

    /**
     * When the named constraint was last altered: the last ALTER that renamed it or changed its properties, else its
     * creation.
     *
     * @param constraintName the constraint's name
     * @return the instant
     */
    public Instant constraintLastAlteredTime(final String constraintName) {
        final Instant altered = constraintName == null ? null
            : constraintAltered.get(constraintName.toUpperCase(Locale.ROOT));
        return altered != null ? altered : constraintCreatedTime(constraintName);
    }

    /** When a DML statement last ran against the table, whether or not it changed a row, or null when none has. */
    public Instant getLastDml() {
        return lastDml;
    }

    /**
     * Put back when a DML statement last ran against the table, as a snapshot recorded it ({@link #markDmlRan}).
     *
     * @param instant the recorded instant, or null when none had run
     */
    public void restoreLastDml(final Instant instant) {
        this.lastDml = instant;
    }

    /** When each constraint an ALTER TABLE added was created, by upper-cased name: what a snapshot keeps. */
    public Map<String, Instant> getConstraintCreatedTimes() {
        return new HashMap<>(constraintCreated);
    }

    /** When an ALTER last changed each constraint it changed, by upper-cased name: what a snapshot keeps. */
    public Map<String, Instant> getConstraintAlteredTimes() {
        return new HashMap<>(constraintAltered);
    }

    /**
     * Put back the constraint dates a snapshot recorded ({@link #markConstraintCreated}, {@link #markConstraintAltered}).
     * A null, from a snapshot that never recorded them, leaves every constraint dated from the table.
     *
     * @param created when each constraint an ALTER TABLE added was created, or null
     * @param altered when an ALTER last changed each constraint, or null
     */
    public void restoreConstraintTimes(final Map<String, Instant> created, final Map<String, Instant> altered) {
        constraintCreated.clear();
        constraintAltered.clear();
        if (created != null) {
            constraintCreated.putAll(created);
        }
        if (altered != null) {
            constraintAltered.putAll(altered);
        }
    }

    /** A DDL on the table may have changed its shape, which then takes effect with it. */
    @Override
    public void markDdl(final String user) {
        super.markDdl(user);
        recordStructure();
    }

    /**
     * Starts the table's structure history with the shape it has as its storage comes into being, unless it has one
     * already: an undropped table keeps its own.
     *
     * @param millis   the storage's creation instant
     * @param sequence the storage's place in the order snapshots are taken in
     */
    public void seedStructure(final long millis, final long sequence) {
        synchronized (structureHistory) {
            if (structureHistory.isEmpty()) {
                structureHistory.add(new TableStructure(millis, sequence, columns, highestOrdinal));
            }
        }
    }

    /**
     * Records the table's shape when a statement has changed it (an ALTER TABLE, a COMMENT, a CREATE OR ALTER), taking
     * effect at that statement's instant. Nothing is recorded while the shape stands, nor before the table's storage
     * exists.
     */
    public void recordStructure() {
        synchronized (structureHistory) {
            if (!structureHistory.isEmpty()
                    && !structureHistory.get(structureHistory.size() - 1).describes(columns)) {
                structureHistory.add(new TableStructure(StatementClock.instant().toEpochMilli(),
                    SnapshotSequence.mark(), columns, highestOrdinal));
            }
        }
    }

    /**
     * The shape the table has now, recorded first if a change has not been: the layout a snapshot taken now holds its
     * rows in.
     *
     * @return the shape, or null before the table's storage exists
     */
    public TableStructure currentStructure() {
        synchronized (structureHistory) {
            recordStructure();
            return structureHistory.isEmpty() ? null : structureHistory.get(structureHistory.size() - 1);
        }
    }

    /**
     * The shape the table had at an instant: the last to take effect at it, or before it for a BEFORE point.
     *
     * @param millis the instant
     * @param before whether the point is just before the instant
     * @return the shape, or null when the table keeps no history
     */
    public TableStructure structureAt(final long millis, final boolean before) {
        synchronized (structureHistory) {
            TableStructure found = structureHistory.isEmpty() ? null : structureHistory.get(0);
            for (final TableStructure shape : structureHistory) {
                if (before ? shape.getFromMillis() < millis : shape.getFromMillis() <= millis) {
                    found = shape;
                } else {
                    break;
                }
            }
            return found;
        }
    }

    /**
     * The shape the table had at a place in the order snapshots are taken in, which is how a STATEMENT point reads.
     *
     * @param sequence the place
     * @return the shape, or null when the table keeps no history
     */
    public TableStructure structureUpTo(final long sequence) {
        synchronized (structureHistory) {
            TableStructure found = structureHistory.isEmpty() ? null : structureHistory.get(0);
            for (final TableStructure shape : structureHistory) {
                if (shape.getFromSequence() <= sequence) {
                    found = shape;
                } else {
                    break;
                }
            }
            return found;
        }
    }

    /**
     * Forgets the shapes that gave way before a place in the snapshot order, once no time-travel point can reach
     * back past it.
     *
     * @param sequence the oldest place still reachable
     */
    public void forgetStructureBefore(final long sequence) {
        synchronized (structureHistory) {
            while (structureHistory.size() > 1 && structureHistory.get(1).getFromSequence() <= sequence) {
                structureHistory.remove(0);
            }
        }
    }

    public List<String> getClusterKeys() {
        return clusterKeys != null ? new ArrayList<>(clusterKeys) : new ArrayList<>();
    }

    public void setClusterKeys(final List<String> clusterKeys) {
        this.clusterKeys = clusterKeys != null ? new ArrayList<>(clusterKeys) : new ArrayList<>();
    }

    public void addColumn(final TableColumn column) {
        if (exactColumnIndex.containsKey(column.getName())) {
            throw new RuntimeException("Column already exists: " + column.getName());
        }
        columns.add(column);
        numberColumns();
        reindexColumns();
        if (column.isPrimaryKey()) {
            primaryKeys.add(column.getName());
        }
    }

    /**
     * Replaces one column in place, keeping its position — the shape change {@code CREATE OR ALTER TABLE}
     * makes when a column stays but its type, nullability or comment does not.
     *
     * @param name    the column
     * @param replacement the column it becomes
     */
    public void replaceColumn(final String name, final TableColumn replacement) {
        final Integer index = indexOf(name);
        if (index == null) {
            throw new RuntimeException(SqlCompilationError.columnDoesNotExist(name));
        }
        // The column stays where it was, so it keeps the position it was given.
        replacement.setOrdinalPosition(columns.get(index).getOrdinalPosition());
        columns.set(index, replacement);
        reindexColumns();
    }

    public void dropColumn(final String name) {
        final Integer index = indexOf(name);
        if (index == null) {
            throw new RuntimeException(SqlCompilationError.columnDoesNotExist(name));
        }
        if (columns.get(index).isPrimaryKey()) {
            // A PRIMARY KEY that spanned the dropped column goes with it, whole, and the columns it
            // leaves behind keep the NOT NULL it gave them (live-verified).
            dropPrimaryKey();
        }
        final TableColumn col = columns.get(index);
        columns.remove((int) index);
        primaryKeys.remove(col.getName());
        forgetColumnConstraintNames(col.getName());
        if (!hasPrimaryKeyColumn()) {
            primaryKeyConstraintName = null;
        }

        reindexColumns();

        // A UNIQUE constraint that spanned the dropped column goes with it, whole.
        purgeBrokenUniqueConstraints();
    }

    public void addColumn(final String name, final DataType dataType) {
        addColumn(new TableColumn(name, dataType, true, null, false, false, false));
    }

    /**
     * Renames a column, which moves its name and nothing else: the column keeps its position, type, default, IDENTITY,
     * collation, comment, foreign-key reference, masking and projection policies and tags (a rename is no way around
     * a policy), and everything the table holds by the column's name follows it: the primary key in its order, the
     * UNIQUE and FOREIGN KEY constraints and their names, the CHECK constraints, the clustering key, search
     * optimization, the columns a row access or aggregation policy is attached on and the data metric functions. A
     * foreign key in another table naming this column is the caller's to follow ({@code ColumnRenames}).
     *
     * @param oldName the column, as written
     * @param newName its new name
     * @return the column's name before the rename, as the table spelled it
     */
    public String renameColumn(final String oldName, final String newName) {
        final Integer index = indexOf(oldName);
        if (index == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExistWithoutHint("Object", oldName));
        }
        if (exactColumnIndex.containsKey(newName)) {
            // The account treats the rename target as an OBJECT, spelled table.column.
            throw new RuntimeException(SqlCompilationError.of("Object '" + getName().toUpperCase()
                + "." + newName.toUpperCase() + "' already exists."));
        }

        final TableColumn oldColumn = columns.get(index);
        final String was = oldColumn.getName();
        columns.set(index, oldColumn.renamedCopy(newName));
        reindexColumns();

        // The key keeps its column order: a composite key renamed in its first column still starts with it.
        for (int i = 0; i < primaryKeys.size(); i++) {
            if (primaryKeys.get(i).equalsIgnoreCase(was)) {
                primaryKeys.set(i, newName);
            }
        }

        // Renaming a column does not replace its constraints, so their generated names move with it.
        moveConstraintName(uniqueConstraintNames, was, newName);
        moveConstraintName(columnForeignKeyConstraintNames, was, newName);
        for (final UniqueConstraint declared : uniqueConstraints) {
            declared.renameColumn(was, newName);
        }
        for (int i = 0; i < foreignKeys.size(); i++) {
            foreignKeys.set(i, foreignKeys.get(i).withColumnRenamed(was, newName));
        }
        for (int i = 0; i < checkConstraints.size(); i++) {
            final CheckConstraint check = checkConstraints.get(i);
            checkConstraints.set(i, new CheckConstraint(check.getName(),
                ExpressionColumns.renamed(check.getExpression(), was, newName), check.isAutoNamed(),
                renamedIn(check.getReferencedColumns(), was, newName)));
        }
        final List<String> clustered = new ArrayList<>();
        for (final String key : getClusterKeys()) {
            clustered.add(ExpressionColumns.renamed(key, was, newName));
        }
        clusterKeys = clustered;
        for (int i = 0; i < searchOptimization.size(); i++) {
            final SearchOptimizationExpression expression = searchOptimization.get(i);
            searchOptimization.set(i, new SearchOptimizationExpression(expression.getExpressionId(),
                expression.getMethod(), ExpressionColumns.renamed(expression.getTarget(), was, newName),
                expression.getTargetDataType()));
        }
        rowAccessPolicyColumns = renamedIn(rowAccessPolicyColumns, was, newName);
        aggregationEntityKey = renamedIn(aggregationEntityKey, was, newName);
        // A conditional masking policy's USING columns, on any column of the table, the renamed one's own included.
        for (final TableColumn column : columns) {
            if (!column.getMaskingPolicyArguments().isEmpty()) {
                column.setMaskingPolicyArguments(renamedIn(column.getMaskingPolicyArguments(), was, newName));
            }
        }
        for (int i = 0; i < dataMetrics.size(); i++) {
            final DataMetricAttachment metric = dataMetrics.get(i);
            final DataMetricAttachment renamed = new DataMetricAttachment(metric.getMetricName(),
                renamedIn(metric.getColumns(), was, newName));
            renamed.setSuspended(metric.isSuspended());
            dataMetrics.set(i, renamed);
        }
        return was;
    }

    /** The names with {@code oldName} replaced by {@code newName}, matched as a column is (ignoring case). */
    private static List<String> renamedIn(final List<String> names, final String oldName, final String newName) {
        final List<String> renamed = new ArrayList<>(names.size());
        for (final String name : names) {
            renamed.add(name.equalsIgnoreCase(oldName) ? newName : name);
        }
        return renamed;
    }

    private void moveConstraintName(final Map<String, String> names, final String oldName, final String newName) {
        final String existing = names.remove(oldName.toUpperCase());
        if (existing != null) {
            names.put(newName.toUpperCase(), existing);
        }
    }

    public void alterColumnType(final String columnName, final DataType newDataType) {
        final Integer index = indexOf(columnName);
        if (index == null) {
            throw new RuntimeException(SqlCompilationError.invalidIdentifier(columnName));
        }

        final TableColumn oldColumn = columns.get(index);
        columns.set(index, copyColumn(oldColumn, newDataType, oldColumn.isPrimaryKey(), oldColumn.isUnique()));
    }

    public void addPrimaryKeyConstraint(final List<String> columnNames) {
        for (final String colName : columnNames) {
            final Integer index = indexOf(colName);
            if (index == null) {
                throw new RuntimeException(SqlCompilationError.invalidIdentifier(colName));
            }

            final TableColumn oldColumn = columns.get(index);
            if (!oldColumn.isPrimaryKey()) {
                columns.set(index, copyColumnWithConstraintFlags(oldColumn, true, oldColumn.isUnique()));
                primaryKeys.add(colName);
            }
        }
    }

    private TableColumn copyColumnWithConstraintFlags(final TableColumn oldColumn,
                                                      final boolean primaryKey, final boolean unique) {
        return copyColumn(oldColumn, oldColumn.getDataType(), primaryKey, unique);
    }

    // Rebuild a column with a different type or PRIMARY KEY / UNIQUE flags (all final on TableColumn),
    // carrying over the rest of its metadata — policies and tags included — so changing one property never
    // silently discards another, and the column keeps its place in INFORMATION_SCHEMA.COLUMNS.
    private TableColumn copyColumn(final TableColumn oldColumn, final DataType dataType,
                                   final boolean primaryKey, final boolean unique) {
        return oldColumn.rebuiltCopy(dataType, oldColumn.isNullable(), primaryKey, unique);
    }

    public void dropPrimaryKey() {
        for (int i = 0; i < columns.size(); i++) {
            final TableColumn oldColumn = columns.get(i);
            if (oldColumn.isPrimaryKey()) {
                columns.set(i, copyColumnWithConstraintFlags(oldColumn, false, oldColumn.isUnique()));
            }
        }
        primaryKeys.clear();
        primaryKeyConstraintName = null;   // a re-added PRIMARY KEY is a new constraint, with a new name
    }

    public void dropUnique(final List<String> columnNames) {
        for (final String colName : columnNames) {
            final Integer index = indexOf(colName);
            if (index == null) {
                throw new RuntimeException(SqlCompilationError.invalidIdentifier(colName));
            }
            clearUniqueFlag(colName);
        }
        purgeBrokenUniqueConstraints();
    }

    /** Clear one column's UNIQUE flag and forget the generated name of its single-column constraint. */
    private void clearUniqueFlag(final String columnName) {
        final Integer index = indexOf(columnName);
        if (index != null) {
            final TableColumn oldColumn = columns.get(index);
            if (oldColumn.isUnique()) {
                columns.set(index, copyColumnWithConstraintFlags(oldColumn, oldColumn.isPrimaryKey(), false));
            }
        }
        uniqueConstraintNames.remove(columnName.toUpperCase());
    }

    // A UNIQUE constraint is all-or-nothing: once one of its columns stops being unique (dropped, or named
    // in a DROP UNIQUE), the whole constraint is gone and its remaining columns lose the flag with it —
    // otherwise a leftover column would silently become a unique constraint of its own.
    private void purgeBrokenUniqueConstraints() {
        for (int i = uniqueConstraints.size() - 1; i >= 0; i--) {
            final UniqueConstraint declared = uniqueConstraints.get(i);
            if (!spansOnlyUniqueColumns(declared)) {
                uniqueConstraints.remove(i);
                for (final String colName : declared.getColumnNames()) {
                    clearUniqueFlag(colName);
                }
            }
        }
    }

    // Drop the FOREIGN KEY whose column list matches (ALTER TABLE ... DROP FOREIGN KEY (cols)): remove the
    // constraint record and clear the reference metadata on those columns so it no longer surfaces.
    public void dropForeignKeyColumns(final List<String> columnNames) {
        final List<String> target = upperCased(columnNames);
        for (int i = foreignKeys.size() - 1; i >= 0; i--) {
            if (upperCased(foreignKeys.get(i).getColumnNames()).equals(target)) {
                foreignKeys.remove(i);
            }
        }
        for (final String colName : columnNames) {
            final Integer index = indexOf(colName);
            if (index != null) {
                final TableColumn col = columns.get(index);
                col.setReferencedTable(null);
                col.setReferencedColumn(null);
                columnForeignKeyConstraintNames.remove(colName.toUpperCase());
            }
        }
    }

    private List<String> upperCased(final List<String> names) {
        final List<String> out = new ArrayList<>(names.size());
        for (final String n : names) {
            out.add(n.toUpperCase());
        }
        return out;
    }

    /**
     * Add a UNIQUE constraint spanning one or more columns: flag every column unique (that is how the
     * engine enforces uniqueness) and record the constraint, so its columns stay ONE constraint under ONE
     * name. A null or blank {@code constraintName} auto-names it the way Snowflake does.
     */
    public void addUniqueConstraint(final String constraintName, final List<String> columnNames) {
        addUniqueConstraint(new UniqueConstraint(constraintName, columnNames));
    }

    /** Add an already-built UNIQUE constraint — the CREATE TABLE parse path and snapshot restore. */
    public void addUniqueConstraint(final UniqueConstraint constraint) {
        for (final String colName : constraint.getColumnNames()) {
            final Integer index = indexOf(colName);
            if (index == null) {
                throw new RuntimeException(SqlCompilationError.invalidIdentifier(colName));
            }

            final TableColumn oldColumn = columns.get(index);
            if (!oldColumn.isUnique()) {
                columns.set(index, copyColumnWithConstraintFlags(oldColumn, oldColumn.isPrimaryKey(), true));
            }
        }
        uniqueConstraints.add(constraint);
    }

    @Override
    public String getObjectType() {
        return "TABLE";
    }

    public String getRowAccessPolicyName() { return rowAccessPolicyName; }
    public void setRowAccessPolicyName(final String name) { this.rowAccessPolicyName = name; }
    public List<String> getRowAccessPolicyColumns() { return new ArrayList<>(rowAccessPolicyColumns); }
    public void setRowAccessPolicyColumns(final List<String> cols) { this.rowAccessPolicyColumns = new ArrayList<>(cols); }
    public boolean hasRowAccessPolicy() { return rowAccessPolicyName != null && !rowAccessPolicyName.isEmpty(); }

    public String getAggregationPolicyName() { return aggregationPolicyName; }
    public void setAggregationPolicyName(final String name) { this.aggregationPolicyName = name; }
    public boolean hasAggregationPolicy() {
        return aggregationPolicyName != null && !aggregationPolicyName.isEmpty();
    }

    public String getDataMetricSchedule() { return dataMetricSchedule; }
    public void setDataMetricSchedule(final String schedule) { this.dataMetricSchedule = schedule; }

    /** The data metric functions attached to this table, in the order they were added. */
    public List<DataMetricAttachment> getDataMetrics() { return new ArrayList<>(dataMetrics); }

    /** Attach a metric unless the same one over the same columns is already there (live: a repeat
     *  adds nothing). */
    public void addDataMetric(final DataMetricAttachment attachment) {
        if (findDataMetric(attachment.getMetricName(), attachment.getColumns()) == null) {
            dataMetrics.add(attachment);
        }
    }

    /** The attachment for that metric over those columns, or null when there is none. */
    public DataMetricAttachment findDataMetric(final String metricName, final List<String> columns) {
        for (final DataMetricAttachment attachment : dataMetrics) {
            if (attachment.matches(metricName, columns)) {
                return attachment;
            }
        }
        return null;
    }

    /** Detach that metric; answers whether there was one to detach. */
    public boolean dropDataMetric(final String metricName, final List<String> columns) {
        final DataMetricAttachment attachment = findDataMetric(metricName, columns);
        return attachment != null && dataMetrics.remove(attachment);
    }

    /** The configured search-optimization expressions, in the order they were added. */
    public List<SearchOptimizationExpression> getSearchOptimization() {
        return new ArrayList<>(searchOptimization);
    }

    public boolean hasSearchOptimization() { return !searchOptimization.isEmpty(); }

    /**
     * Add one expression unless an identical one is already configured — live takes a repeat without
     * adding a second row.
     *
     * @return the expression, whether it was added now or already there
     */
    public SearchOptimizationExpression addSearchOptimization(final String method, final String target,
                                                              final String targetDataType) {
        for (final SearchOptimizationExpression existing : searchOptimization) {
            if (existing.getMethod().equalsIgnoreCase(method)
                    && existing.getTarget().equalsIgnoreCase(target)) {
                return existing;
            }
        }
        final SearchOptimizationExpression added = new SearchOptimizationExpression(
            nextSearchOptimizationId, method, target, targetDataType);
        nextSearchOptimizationId++;
        searchOptimization.add(added);
        return added;
    }

    /** Restore one expression with the number it already had (snapshot / clone). */
    public void restoreSearchOptimization(final SearchOptimizationExpression expression) {
        searchOptimization.add(expression);
        if (expression.getExpressionId() >= nextSearchOptimizationId) {
            nextSearchOptimizationId = expression.getExpressionId() + 1;
        }
    }

    /** Drop the expression with that number; answers whether there was one. */
    public boolean dropSearchOptimization(final int expressionId) {
        for (int i = 0; i < searchOptimization.size(); i++) {
            if (searchOptimization.get(i).getExpressionId() == expressionId) {
                searchOptimization.remove(i);
                return true;
            }
        }
        return false;
    }

    /** Drop the expression naming that method and target; answers whether there was one. */
    public boolean dropSearchOptimization(final String method, final String target) {
        for (int i = 0; i < searchOptimization.size(); i++) {
            final SearchOptimizationExpression expression = searchOptimization.get(i);
            if (expression.getMethod().equalsIgnoreCase(method)
                    && expression.getTarget().equalsIgnoreCase(target)) {
                searchOptimization.remove(i);
                return true;
            }
        }
        return false;
    }

    /** Drop the lot — the bare DROP SEARCH OPTIMIZATION. */
    public void clearSearchOptimization() {
        searchOptimization.clear();
        nextSearchOptimizationId = 1;
    }

    public String getJoinPolicyName() { return joinPolicyName; }
    public void setJoinPolicyName(final String name) { this.joinPolicyName = name; }
    public boolean hasJoinPolicy() { return joinPolicyName != null && !joinPolicyName.isEmpty(); }

    public List<String> getAggregationEntityKey() { return new ArrayList<>(aggregationEntityKey); }
    public void setAggregationEntityKey(final List<String> columns) {
        this.aggregationEntityKey = new ArrayList<>(columns);
    }

    /** The CHECK constraints, in declaration order. */
    public List<CheckConstraint> getCheckConstraints() { return new ArrayList<>(checkConstraints); }

    public void addCheckConstraint(final CheckConstraint check) { checkConstraints.add(check); }

    /** Remove the check of that name; answers whether there was one. */
    public boolean dropCheckConstraint(final String name) {
        for (int i = 0; i < checkConstraints.size(); i++) {
            if (checkConstraints.get(i).getName().equalsIgnoreCase(name)) {
                checkConstraints.remove(i);
                return true;
            }
        }
        return false;
    }

    /** The contacts attached to this table, keyed by purpose. */
    public Map<String, String> getContacts() { return new LinkedHashMap<>(contacts); }

    /**
     * The stage file format the table was created with, keyed by option name.
     *
     * @return the options, empty when none were written
     */
    public Map<String, String> getStageFileFormat() {
        return stageFileFormat;
    }

    /**
     * The stage copy options the table was created with, keyed by option name.
     *
     * @return the options, empty when none were written
     */
    public Map<String, String> getStageCopyOptions() {
        return stageCopyOptions;
    }

    /** Attach a contact for a purpose, replacing whatever that purpose held. */
    public void setContact(final String purpose, final String contactName) {
        contacts.put(purpose.toUpperCase(Locale.ROOT), contactName);
    }

    /** Detach the contact held for a purpose, if any — live does not mind that there was none. */
    public void unsetContact(final String purpose) {
        contacts.remove(purpose.toUpperCase(Locale.ROOT));
    }
}
