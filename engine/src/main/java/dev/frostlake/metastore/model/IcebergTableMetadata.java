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

/**
 * What makes a table an Iceberg table: the external volume and base location its files would live in, the
 * catalog that manages it ({@code SNOWFLAKE} for a Snowflake-managed table) and its storage settings. The rows
 * themselves are stored as an ordinary table's; this is the metadata SHOW ICEBERG TABLES reports.
 */
public final class IcebergTableMetadata {

    /** The catalog of a Snowflake-managed Iceberg table. */
    public static final String SNOWFLAKE_CATALOG = "SNOWFLAKE";

    private String externalVolume;
    private String catalog = SNOWFLAKE_CATALOG;
    private String baseLocation;
    private String catalogSync;
    private String storageSerializationPolicy = "OPTIMIZED";
    private String catalogTableName;
    private String catalogNamespace;
    private String metadataFilePath;

    /** A copy, for a table cloned or created like this one. */
    public IcebergTableMetadata copy() {
        final IcebergTableMetadata copy = new IcebergTableMetadata();
        copy.externalVolume = externalVolume;
        copy.catalog = catalog;
        copy.baseLocation = baseLocation;
        copy.catalogSync = catalogSync;
        copy.storageSerializationPolicy = storageSerializationPolicy;
        copy.catalogTableName = catalogTableName;
        copy.catalogNamespace = catalogNamespace;
        copy.metadataFilePath = metadataFilePath;
        return copy;
    }

    /** Whether Snowflake manages the table (its catalog is {@code SNOWFLAKE}). */
    public boolean isManaged() {
        return SNOWFLAKE_CATALOG.equals(catalog);
    }

    /** The external volume's name. */
    public String getExternalVolume() {
        return externalVolume;
    }

    /** Sets the external volume's name. */
    public void setExternalVolume(final String externalVolume) {
        this.externalVolume = externalVolume;
    }

    /** The catalog's name. */
    public String getCatalog() {
        return catalog;
    }

    /** Sets the catalog's name. */
    public void setCatalog(final String catalog) {
        this.catalog = catalog;
    }

    /** The path of the table's files relative to the external volume, or null. */
    public String getBaseLocation() {
        return baseLocation;
    }

    /** Sets the base location. */
    public void setBaseLocation(final String baseLocation) {
        this.baseLocation = baseLocation;
    }

    /** The catalog integration the table syncs to, or null. */
    public String getCatalogSync() {
        return catalogSync;
    }

    /** Sets CATALOG_SYNC. */
    public void setCatalogSync(final String catalogSync) {
        this.catalogSync = catalogSync;
    }

    /** COMPATIBLE or OPTIMIZED. */
    public String getStorageSerializationPolicy() {
        return storageSerializationPolicy;
    }

    /** Sets STORAGE_SERIALIZATION_POLICY. */
    public void setStorageSerializationPolicy(final String storageSerializationPolicy) {
        this.storageSerializationPolicy = storageSerializationPolicy;
    }

    /** The table's name in an external catalog, or null. */
    public String getCatalogTableName() {
        return catalogTableName;
    }

    /** Sets CATALOG_TABLE_NAME. */
    public void setCatalogTableName(final String catalogTableName) {
        this.catalogTableName = catalogTableName;
    }

    /** The table's namespace in an external catalog, or null. */
    public String getCatalogNamespace() {
        return catalogNamespace;
    }

    /** Sets CATALOG_NAMESPACE. */
    public void setCatalogNamespace(final String catalogNamespace) {
        this.catalogNamespace = catalogNamespace;
    }

    /** The metadata file an externally managed table was last refreshed from, or null. */
    public String getMetadataFilePath() {
        return metadataFilePath;
    }

    /** Sets the metadata file path. */
    public void setMetadataFilePath(final String metadataFilePath) {
        this.metadataFilePath = metadataFilePath;
    }
}
