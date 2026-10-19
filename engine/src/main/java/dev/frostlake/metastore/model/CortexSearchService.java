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

import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.Named;
import dev.frostlake.metastore.Taggable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Cortex search service: a named, indexed view over one text column of a query's result, queried
 * back through {@code SNOWFLAKE.CORTEX.SEARCH_PREVIEW}.
 *
 * <p>The engine keeps the whole definition — the searched column, the attribute columns, the defining
 * query, the refresh options — but does no embedding of its own; that belongs to the optional
 * {@code frostlake-ai} pack, the same division the geospatial types keep with {@code frostlake-geo}.
 * A service can therefore be created, listed, described and dropped on a bare engine, and only a
 * SEARCH_PREVIEW against it needs the pack.
 *
 * <p>The embedding model defaults to {@code snowflake-arctic-embed-m-v1.5}, which is the model
 * Snowflake picks when CREATE names none.
 */
public class CortexSearchService implements Named, Taggable {

    /** The model Snowflake indexes with when CREATE names no EMBEDDING_MODEL. */
    public static final String DEFAULT_EMBEDDING_MODEL = "snowflake-arctic-embed-m-v1.5";

    private final String name;
    private String databaseName;
    private String schemaName;
    private final String searchColumn;
    private final List<String> attributeColumns;
    private final List<String> columns;
    private String warehouse;
    private String targetLag;
    private final String embeddingModel;
    private final String definition;
    private String comment;
    private LocalDateTime createdOn;
    private String owner;
    private boolean indexingSuspended;
    private boolean servingSuspended;
    private final List<String> primaryKeyColumns = new ArrayList<String>();
    private Integer fullIndexBuildIntervalDays;
    private boolean requestLogging;
    private Integer autoSuspend;
    private final Map<String, String> tags = new LinkedHashMap<String, String>();

    public CortexSearchService(final String name, final String searchColumn,
                               final List<String> attributeColumns, final List<String> columns,
                               final String warehouse, final String targetLag,
                               final String embeddingModel, final String definition,
                               final String comment) {
        this.name = name;
        this.searchColumn = searchColumn;
        this.attributeColumns = attributeColumns == null
            ? new ArrayList<String>() : new ArrayList<String>(attributeColumns);
        this.columns = columns == null ? new ArrayList<String>() : new ArrayList<String>(columns);
        this.warehouse = warehouse;
        this.targetLag = targetLag;
        this.embeddingModel = embeddingModel == null ? DEFAULT_EMBEDDING_MODEL : embeddingModel;
        this.definition = definition;
        this.comment = comment;
        // The creating statement's instant, which a replayed statement reproduces (see SqlObject).
        this.createdOn = StatementClock.hostLocal();
    }

    @Override
    public String getName() {
        return name;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(final String databaseName) {
        this.databaseName = databaseName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(final String schemaName) {
        this.schemaName = schemaName;
    }

    public String getSearchColumn() {
        return searchColumn;
    }

    public List<String> getAttributeColumns() {
        return attributeColumns;
    }

    /** Every column the defining query projects, in its order — what SEARCH_PREVIEW can return. */
    public List<String> getColumns() {
        return columns;
    }

    public String getWarehouse() {
        return warehouse;
    }

    public String getTargetLag() {
        return targetLag;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    /** The defining query, as written. */
    public String getDefinition() {
        return definition;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreatedOn() {
        return createdOn;
    }

    /**
     * Put back when this object was created, as a snapshot recorded it, so an engine clone or a restore reports the
     * original creation time. A null, from a snapshot that never recorded it, keeps the time it was rebuilt.
     *
     * @param created the recorded creation time, or null
     */
    public void restoreCreatedTime(final LocalDateTime created) {
        if (created != null) {
            this.createdOn = created;
        }
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(final String owner) {
        this.owner = owner;
    }

    /** Whether indexing is suspended ({@code ALTER … SUSPEND INDEXING}). */
    public boolean isIndexingSuspended() {
        return indexingSuspended;
    }

    /** Suspends or resumes indexing. */
    public void setIndexingSuspended(final boolean indexingSuspended) {
        this.indexingSuspended = indexingSuspended;
    }

    /** Whether serving is suspended ({@code ALTER … SUSPEND SERVING}). */
    public boolean isServingSuspended() {
        return servingSuspended;
    }

    /** Suspends or resumes serving. */
    public void setServingSuspended(final boolean servingSuspended) {
        this.servingSuspended = servingSuspended;
    }

    /** ALTER … SET WAREHOUSE: the warehouse that refreshes the service from now on. */
    public void setWarehouse(final String warehouse) {
        this.warehouse = warehouse;
    }

    /** ALTER … SET TARGET_LAG. */
    public void setTargetLag(final String targetLag) {
        this.targetLag = targetLag;
    }

    /** ALTER … SET ATTRIBUTES (…) / UNSET ATTRIBUTES: the attribute columns, replaced whole. */
    public void setAttributeColumns(final List<String> attributes) {
        attributeColumns.clear();
        attributeColumns.addAll(attributes);
    }

    /** The primary key columns ALTER … SET PRIMARY KEY names; empty when none is set. */
    public List<String> getPrimaryKeyColumns() {
        return new ArrayList<String>(primaryKeyColumns);
    }

    /** ALTER … SET PRIMARY KEY = (…) / UNSET PRIMARY KEY: the key's columns, replaced whole. */
    public void setPrimaryKeyColumns(final List<String> columns) {
        primaryKeyColumns.clear();
        primaryKeyColumns.addAll(columns);
    }

    /** The days between full index rebuilds, or null when not set. */
    public Integer getFullIndexBuildIntervalDays() {
        return fullIndexBuildIntervalDays;
    }

    public void setFullIndexBuildIntervalDays(final Integer days) {
        this.fullIndexBuildIntervalDays = days;
    }

    /** Whether the service logs its requests. */
    public boolean isRequestLogging() {
        return requestLogging;
    }

    public void setRequestLogging(final boolean requestLogging) {
        this.requestLogging = requestLogging;
    }

    /** The seconds of inactivity after which serving suspends, or null when it never does. */
    public Integer getAutoSuspend() {
        return autoSuspend;
    }

    public void setAutoSuspend(final Integer seconds) {
        this.autoSuspend = seconds;
    }

    @Override
    public void setTag(final String tagName, final String value) {
        tags.put(tagName, value);
    }

    @Override
    public void unsetTag(final String tagName) {
        tags.remove(tagName);
    }

    @Override
    public String getTagValue(final String tagName) {
        return tags.get(tagName);
    }

    @Override
    public Map<String, String> getTagValues() {
        return new LinkedHashMap<String, String>(tags);
    }
}
