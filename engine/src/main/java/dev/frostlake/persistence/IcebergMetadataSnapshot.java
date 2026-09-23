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

/** An Iceberg table's metadata, as a snapshot holds it. */
public class IcebergMetadataSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** EXTERNAL_VOLUME. */
    public String externalVolume;

    /** CATALOG. */
    public String catalog;

    /** BASE_LOCATION, or null. */
    public String baseLocation;

    /** CATALOG_SYNC, or null. */
    public String catalogSync;

    /** STORAGE_SERIALIZATION_POLICY, or null. */
    public String storageSerializationPolicy;

    /** CATALOG_TABLE_NAME, or null. */
    public String catalogTableName;

    /** CATALOG_NAMESPACE, or null. */
    public String catalogNamespace;

    /** METADATA_FILE_PATH, or null. */
    public String metadataFilePath;
}
