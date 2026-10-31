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

import dev.frostlake.metastore.SqlObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class View extends SqlObject implements SessionScoped, ContactHolder {

    private final String definition;
    private final List<String> columnNames;
    /** The CREATE statement exactly as typed (Snowflake surfaces it verbatim), or null pre-restore. */
    private String originalDdl;
    /** The CREATE statement as VIEW_DEFINITION holds it, or null — see {@link #getDefinitionText}. */
    private String definitionText;
    /** The CREATE statement as SHOW VIEWS spells it, or null — see {@link #getListedText}. */
    private String listedText;
    /** The view's column list as resolved when it was created — see {@link #getResolvedColumns()}. */
    private List<TableColumn> resolvedColumns;
    private boolean secure = false;
    /**
     * Whether the view tracks its changes, so a CHANGES clause may read it: a stream created on it and CHANGE_TRACKING
     * = TRUE turn it on, and it stays on when the stream is dropped (live-verified).
     */
    private boolean changeTracking = false;
    private boolean temporary = false;
    /** Whether the view was created RECURSIVE, its definition then the recursive CTE it runs as. */
    private boolean recursive = false;
    /** A RECURSIVE view's body as written, which its DDL shows in place of the CTE it runs as; or null. */
    private String writtenBody;
    /** Attached row access policy (ALTER VIEW ... ADD ROW ACCESS POLICY p ON (cols)), or null. */
    private String rowAccessPolicyName;
    private List<String> rowAccessPolicyColumns = new ArrayList<>();
    /** The aggregation policy the view is read under, by its qualified name, with its ENTITY KEY; or null. */
    private String aggregationPolicyName;
    private List<String> aggregationEntityKey = new ArrayList<>();
    /** The join policy the view is read under, by its qualified name; or null. */
    private String joinPolicyName;
    /** The DATA METRIC FUNCTIONS attached to the view and their DATA_METRIC_SCHEDULE, as a table's are. */
    private final DataMetricAttachments dataMetrics = new DataMetricAttachments();
    /** The contacts attached to the view, by purpose, each by its full name. */
    private final Map<String, String> contacts = new TreeMap<>();

    public View(final String name, final String definition) {
        super(name);
        this.definition = definition;
        this.columnNames = null;
    }

    public View(final String name, final List<String> columnNames, final String definition) {
        super(name);
        this.definition = definition;
        this.columnNames = columnNames;
    }

    /** A view that carries a fixed creation time, as INFORMATION_SCHEMA's never-created views do. */
    public View(final String name, final String definition, final Instant createdTime) {
        super(name, createdTime);
        this.definition = definition;
        this.columnNames = null;
    }

    public String getDefinition() {
        return definition;
    }

    public String getOriginalDdl() {
        return originalDdl;
    }

    public void setOriginalDdl(final String originalDdl) {
        this.originalDdl = originalDdl;
    }

    /**
     * The CREATE statement as INFORMATION_SCHEMA.VIEWS' VIEW_DEFINITION holds it: as written, without the
     * view's own COMMENT clause. Null when the statement carried none, or the view was not created from a
     * statement here.
     */
    public String getDefinitionText() {
        return definitionText;
    }

    public void setDefinitionText(final String definitionText) {
        this.definitionText = definitionText;
    }

    /**
     * The CREATE statement as SHOW VIEWS spells it: as written, with the view's own COMMENT clause
     * re-printed as {@code comment = '…'}. Null when the statement carried none, or the view was not
     * created from a statement here.
     */
    public String getListedText() {
        return listedText;
    }

    public void setListedText(final String listedText) {
        this.listedText = listedText;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public boolean hasExplicitColumnNames() {
        return columnNames != null && !columnNames.isEmpty();
    }

    /**
     * The view's columns — name and declared type — as its defining query resolved to at CREATE time,
     * or null when they could not be resolved (a body that would not plan, or a snapshot predating
     * capture). This is what {@code INFORMATION_SCHEMA.COLUMNS} reports for the view, and through it
     * what {@code DatabaseMetaData.getColumns} answers.
     *
     * <p>Resolved ONCE, at creation, and never recomputed — which is Snowflake's own behavior, verified
     * live on a real account. There, a view's reported column metadata is a snapshot taken
     * when the view is created and is entirely independent of the tables underneath it afterwards:
     *
     * <ul>
     *   <li>{@code ALTER TABLE t ALTER COLUMN c SET DATA TYPE VARCHAR(50)} leaves every view over
     *       {@code c} still reporting {@code VARCHAR(10)};</li>
     *   <li>{@code ALTER TABLE t ADD COLUMN} does not widen a {@code SELECT *} view — the star was
     *       expanded once, at creation;</li>
     *   <li>{@code ALTER TABLE t ALTER COLUMN c DROP NOT NULL} leaves the view still reporting
     *       {@code IS_NULLABLE = NO};</li>
     *   <li>dropping the column a view projects — or the whole base table — leaves the view reporting
     *       its columns unchanged, even though selecting from it then fails to compile.</li>
     * </ul>
     *
     * <p>Freezing at creation is also what keeps a metadata read from ever planning a query:
     * {@code INFORMATION_SCHEMA} is itself served by these views, so resolving a view body while
     * answering a metadata call could re-enter the metadata layer. Nothing here can.
     */
    public List<TableColumn> getResolvedColumns() {
        return resolvedColumns;
    }

    public void setResolvedColumns(final List<TableColumn> resolvedColumns) {
        this.resolvedColumns = resolvedColumns != null ? new ArrayList<>(resolvedColumns) : null;
    }

    public boolean hasResolvedColumns() {
        return resolvedColumns != null && !resolvedColumns.isEmpty();
    }

    public boolean isSecure() { return secure; }
    public void setSecure(final boolean secure) { this.secure = secure; }
    public boolean isChangeTracking() { return changeTracking; }
    public void setChangeTracking(final boolean changeTracking) { this.changeTracking = changeTracking; }

    /**
     * A TEMPORARY (TEMP / VOLATILE) view lives only as long as the session that created it, the same
     * lifetime Frostlake gives a temporary table. Live also makes it invisible to other sessions and
     * lets it shadow a permanent object of the same name; Frostlake has one namespace per catalog, so
     * it models the lifetime and not the isolation.
     */
    @Override
    public boolean isTemporary() { return temporary; }

    /** Whether the view was created RECURSIVE. */
    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(final boolean recursive) {
        this.recursive = recursive;
    }

    /** A RECURSIVE view's body as written, or null for any other view. */
    public String getWrittenBody() {
        return writtenBody;
    }

    public void setWrittenBody(final String writtenBody) {
        this.writtenBody = writtenBody;
    }

    /** The body the view's DDL shows: as written for a RECURSIVE view, else its definition. */
    public String getShownBody() {
        return recursive && writtenBody != null ? writtenBody : definition;
    }

    public void setTemporary(final boolean temporary) { this.temporary = temporary; }

    public String getRowAccessPolicyName() { return rowAccessPolicyName; }
    public void setRowAccessPolicyName(final String name) { this.rowAccessPolicyName = name; }
    public List<String> getRowAccessPolicyColumns() { return rowAccessPolicyColumns; }
    public void setRowAccessPolicyColumns(final List<String> cols) {
        this.rowAccessPolicyColumns = new ArrayList<>(cols);
    }
    public boolean hasRowAccessPolicy() { return rowAccessPolicyName != null && !rowAccessPolicyName.isEmpty(); }

    public String getAggregationPolicyName() { return aggregationPolicyName; }
    public void setAggregationPolicyName(final String name) { this.aggregationPolicyName = name; }
    public List<String> getAggregationEntityKey() { return aggregationEntityKey; }
    public void setAggregationEntityKey(final List<String> key) {
        this.aggregationEntityKey = key == null ? new ArrayList<String>() : new ArrayList<>(key);
    }
    public boolean hasAggregationPolicy() {
        return aggregationPolicyName != null && !aggregationPolicyName.isEmpty();
    }

    public String getJoinPolicyName() { return joinPolicyName; }
    public void setJoinPolicyName(final String name) { this.joinPolicyName = name; }
    public boolean hasJoinPolicy() { return joinPolicyName != null && !joinPolicyName.isEmpty(); }

    /** The data metric functions attached to this view and their schedule. */
    public DataMetricAttachments dataMetricAttachments() { return dataMetrics; }

    @Override
    public Map<String, String> getContacts() {
        return new TreeMap<>(contacts);
    }

    @Override
    public void setContact(final String purpose, final String contactName) {
        contacts.put(purpose.toUpperCase(Locale.ROOT), contactName);
    }

    @Override
    public void unsetContact(final String purpose) {
        contacts.remove(purpose.toUpperCase(Locale.ROOT));
    }

    /**
     * A full-fidelity duplicate for CLONE DATABASE/SCHEMA: keeps the explicit column list, the SECURE
     * flag, the comment, the change tracking a stream on the clone reads by, any attached row access, aggregation
     * and join policy, the attached data metric functions and their schedule, the contacts, and the columns with their
     * masking and projection policies and their tags (the name-and-definition constructor alone silently dropped all of
     * these from clones).
     */
    public View copy() {
        final View clone = columnNames != null
            ? new View(name, new ArrayList<>(columnNames), definition)
            : new View(name, definition);
        clone.setComment(comment);
        clone.originalDdl = originalDdl;
        clone.definitionText = definitionText;
        clone.listedText = listedText;
        if (resolvedColumns != null) {
            // Each column its own copy, so a policy or a tag set on a column of one view is not set on the other.
            final List<TableColumn> columns = new ArrayList<>();
            for (final TableColumn column : resolvedColumns) {
                columns.add(column.copy());
            }
            clone.resolvedColumns = columns;
        }
        clone.secure = secure;
        clone.changeTracking = changeTracking;
        clone.recursive = recursive;
        clone.writtenBody = writtenBody;
        clone.rowAccessPolicyName = rowAccessPolicyName;
        clone.rowAccessPolicyColumns = new ArrayList<>(rowAccessPolicyColumns);
        clone.aggregationPolicyName = aggregationPolicyName;
        clone.aggregationEntityKey = new ArrayList<>(aggregationEntityKey);
        clone.joinPolicyName = joinPolicyName;
        dataMetrics.copyInto(clone.dataMetrics);
        clone.contacts.putAll(contacts);
        return clone;
    }

    /**
     * The view's full {@code CREATE OR REPLACE} statement rendered against {@code displayName}
     * (bare for GET_DDL's default output, schema-qualified for the metadata views). Snowflake
     * surfaces a view's executable DDL — not just its query — in
     * {@code INFORMATION_SCHEMA.VIEWS.VIEW_DEFINITION} and in SHOW VIEWS' {@code text} column;
     * deployment tooling relies on {@code EXECUTE IMMEDIATE} of that text to recreate views.
     */
    public String ddl(final String displayName) {
        // Live-verified: Snowflake's SHOW VIEWS text and VIEW_DEFINITION carry the ORIGINAL
        // statement exactly as typed. The reconstruction below is the fallback for views restored
        // from snapshots that predate original-text capture.
        if (originalDdl != null) {
            return SqlObject.withoutTrailingSemicolon(originalDdl);
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("create or replace ");
        if (secure) {
            sb.append("secure ");
        }
        if (recursive) {
            sb.append("recursive ");
        }
        sb.append("view ").append(displayName);
        if (hasExplicitColumnNames()) {
            sb.append(" (").append(String.join(", ", columnNames)).append(")");
        }
        return sb.append(" as ").append(SqlObject.withoutTrailingSemicolon(getShownBody())).append(";").toString();
    }

    @Override
    public String getObjectType() {
        return "VIEW";
    }
}
