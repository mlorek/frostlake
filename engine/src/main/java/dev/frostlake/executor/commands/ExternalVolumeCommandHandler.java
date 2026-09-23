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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.ExternalVolumeRegistry;
import dev.frostlake.metastore.model.ExternalVolume;
import dev.frostlake.metastore.model.PropertyValue;
import dev.frostlake.metastore.model.PropertyValueKind;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CREATE, ALTER, DROP, UNDROP, SHOW and DESCRIBE of external volumes: STORAGE_LOCATIONS (each a NAME, a
 * STORAGE_PROVIDER, a STORAGE_BASE_URL and the provider's own properties), ALLOW_WRITES and a comment. Frostlake
 * keeps the configuration and never reaches the storage; the first location is the active one.
 */
public final class ExternalVolumeCommandHandler {

    private static final List<String> PROVIDERS = Arrays.asList("S3", "S3GOV", "GCS", "AZURE", "S3COMPAT");
    private static final List<String> LOCATION_TAKES = Arrays.asList("NAME", "STORAGE_PROVIDER", "STORAGE_BASE_URL",
        "STORAGE_AWS_ROLE_ARN", "STORAGE_AWS_ACCESS_POINT_ARN", "STORAGE_AWS_EXTERNAL_ID", "ENCRYPTION",
        "USE_PRIVATELINK_ENDPOINT", "AZURE_TENANT_ID", "CREDENTIALS", "STORAGE_ENDPOINT");

    private final Catalog catalog;

    /** @param catalog the catalog holding the account's external volumes */
    public ExternalVolumeCommandHandler(final Catalog catalog) {
        this.catalog = catalog;
    }

    private ExternalVolumeRegistry registry() {
        return catalog.getExternalVolumes();
    }

    /** Runs a CREATE, ALTER, DROP or UNDROP of an external volume and answers its status. */
    public ResultSet handle(final FrostlakeParser.ExternalVolumeStatementContext ctx) {
        final String name = SqlIdentifiers.canonical(ctx.identifier());
        if (ctx.CREATE() != null) {
            return create(ctx, name);
        }
        if (ctx.UNDROP() != null) {
            if (registry().find(name) != null) {
                throw new RuntimeException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(name)
                    + "' already exists."));
            }
            if (registry().undrop(name) == null) {
                throw new RuntimeException("External_volume " + name + " did not exist or was purged.");
            }
            return StatusResults.of("External_volume " + name + " successfully restored.");
        }
        final ExternalVolume volume = registry().find(name);
        if (ctx.DROP() != null) {
            if (volume == null) {
                if (ctx.if_exists() != null) {
                    return StatusResults.alreadyDropped(name);
                }
                throw missing(name);
            }
            registry().drop(name);
            return StatusResults.dropped(name);
        }
        if (volume == null) {
            if (ctx.if_exists() != null) {
                return StatusResults.of(StatusResults.EXECUTED);
            }
            throw missing(name);
        }
        alter(volume, ctx.externalVolumeAction());
        return StatusResults.of(StatusResults.EXECUTED);
    }

    private ResultSet create(final FrostlakeParser.ExternalVolumeStatementContext ctx, final String name) {
        if (ctx.if_not_exists() != null && ctx.or_replace() != null) {
            throw new RuntimeException("options IF NOT EXISTS and OR REPLACE are incompatible.");
        }
        final Map<String, PropertyValue> properties = ObjectProperties.read(ctx.objectProperty());
        final ExternalVolume volume = new ExternalVolume(name);
        volume.setOwner(catalog.currentRoleForOwner());
        for (final Map.Entry<String, PropertyValue> entry : properties.entrySet()) {
            final String key = entry.getKey();
            if ("STORAGE_LOCATIONS".equals(key)) {
                for (final PropertyValue location : entry.getValue().getItems()) {
                    addLocation(volume, location);
                }
            } else if ("ALLOW_WRITES".equals(key)) {
                volume.setAllowWrites(ObjectProperties.flag(key, entry.getValue()));
            } else if ("COMMENT".equals(key)) {
                volume.setComment(entry.getValue().getText());
            } else {
                throw invalidProperty(key);
            }
        }
        if (volume.getStorageLocations().isEmpty()) {
            throw new RuntimeException("External volume " + name + " must have STORAGE_LOCATIONS defined.");
        }
        final ExternalVolume standing = registry().find(name);
        if (standing != null && ctx.or_replace() == null) {
            if (ctx.if_not_exists() != null) {
                return StatusResults.alreadyExists(name);
            }
            throw new RuntimeException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(name)
                + "' already exists."));
        }
        registry().discard(name);
        registry().put(volume);
        return StatusResults.of(name + " successfully created.");
    }

    private void alter(final ExternalVolume volume, final FrostlakeParser.ExternalVolumeActionContext action) {
        if (action.ADD() != null) {
            final FrostlakeParser.ObjectPropertyContext property = action.objectProperty(0);
            if (!"STORAGE_LOCATION".equals(ObjectProperties.key(property.optionKey()))) {
                throw invalidProperty(ObjectProperties.key(property.optionKey()));
            }
            addLocation(volume, ObjectProperties.value(property.objectPropertyValue()));
        } else if (action.REMOVE() != null) {
            if (!"STORAGE_LOCATION".equals(ObjectProperties.key(action.optionKey()))) {
                throw invalidProperty(ObjectProperties.key(action.optionKey()));
            }
            final String locationName = SqlStringLiterals.decode(action.STRING_LITERAL().getText());
            final Map<String, PropertyValue> location = location(volume, locationName);
            if (volume.getStorageLocations().size() == 1) {
                throw new RuntimeException("Storage location '" + locationName + "' is the only storage location on"
                    + " this external volume and hence cannot be removed. Please add other locations for this"
                    + " location to be removed.");
            }
            volume.getStorageLocations().remove(location);
        } else if (action.UPDATE() != null) {
            final Map<String, PropertyValue> properties = ObjectProperties.read(action.objectProperty());
            final PropertyValue target = properties.get("STORAGE_LOCATION");
            if (target == null || target.getText() == null) {
                throw new RuntimeException(SqlCompilationError.of("Missing option(s): [STORAGE_LOCATION]"));
            }
            final Map<String, PropertyValue> location = location(volume, target.getText());
            for (final Map.Entry<String, PropertyValue> entry : properties.entrySet()) {
                if ("CREDENTIALS".equals(entry.getKey())) {
                    location.put("CREDENTIALS", entry.getValue());
                } else if (!"STORAGE_LOCATION".equals(entry.getKey())) {
                    throw invalidProperty(entry.getKey());
                }
            }
        } else {
            final Map<String, PropertyValue> properties = ObjectProperties.read(action.objectProperty());
            for (final Map.Entry<String, PropertyValue> entry : properties.entrySet()) {
                if ("ALLOW_WRITES".equals(entry.getKey())) {
                    volume.setAllowWrites(ObjectProperties.flag(entry.getKey(), entry.getValue()));
                } else if ("COMMENT".equals(entry.getKey())) {
                    volume.setComment(entry.getValue().getText());
                } else {
                    throw invalidProperty(entry.getKey());
                }
            }
        }
    }

    /** Checks one storage location's properties and adds it; its name must be new to the volume. */
    private static void addLocation(final ExternalVolume volume, final PropertyValue location) {
        if (location.getKind() != PropertyValueKind.PROPERTIES) {
            throw invalidProperty("STORAGE_LOCATIONS");
        }
        final Map<String, PropertyValue> properties = new LinkedHashMap<>(location.getEntries());
        for (final String key : properties.keySet()) {
            if (!LOCATION_TAKES.contains(key)) {
                throw invalidProperty(key);
            }
        }
        for (final String required : Arrays.asList("NAME", "STORAGE_PROVIDER", "STORAGE_BASE_URL")) {
            if (!properties.containsKey(required)) {
                throw new RuntimeException("Property '" + required
                    + "' is required for all storage locations on an external volume.");
            }
        }
        final PropertyValue provider = properties.get("STORAGE_PROVIDER");
        final String providerText = provider.getText() == null ? "" : provider.getText().toUpperCase(Locale.ROOT);
        if (!PROVIDERS.contains(providerText)) {
            throw new RuntimeException(SqlCompilationError.of("invalid value '" + provider.getWritten()
                + "' for property 'STORAGE_PROVIDER'"));
        }
        properties.put("STORAGE_PROVIDER", PropertyValue.text(providerText));
        final String name = properties.get("NAME").getText();
        for (final Map<String, PropertyValue> existing : volume.getStorageLocations()) {
            final PropertyValue existingName = existing.get("NAME");
            if (existingName != null && existingName.getText() != null && existingName.getText().equals(name)) {
                throw new RuntimeException("Storage location '" + name + "' already exists. Please supply a"
                    + " different name or remove the existing storage location first.");
            }
        }
        volume.getStorageLocations().add(properties);
    }

    private static Map<String, PropertyValue> location(final ExternalVolume volume, final String locationName) {
        for (final Map<String, PropertyValue> location : volume.getStorageLocations()) {
            final PropertyValue name = location.get("NAME");
            if (name != null && locationName.equals(name.getText())) {
                return location;
            }
        }
        throw new RuntimeException("Storage location '" + locationName
            + "' does not exist. Please supply the name of an existing storage location.");
    }

    private static RuntimeException invalidProperty(final String key) {
        return new RuntimeException(SqlCompilationError.of("invalid property '" + key + "' for 'EXTERNAL_VOLUME'"));
    }

    private static RuntimeException missing(final String name) {
        return new RuntimeException(SqlCompilationError.doesNotExist("External volume", name));
    }

    /** SHOW EXTERNAL VOLUMES: name, allow_writes and comment, by name. */
    public ResultSet show() {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("allow_writes", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR));
        final List<Row> rows = new ArrayList<>();
        for (final ExternalVolume volume : registry().all()) {
            rows.add(new Row(Arrays.asList((Object) volume.getName(), volume.isAllowWrites() ? "true" : "false",
                volume.getComment())));
        }
        return new ResultSet(columns, rows);
    }

    /**
     * DESCRIBE EXTERNAL VOLUME: ALLOW_WRITES, one STORAGE_LOCATION_n row per location holding its properties as
     * a JSON object (the encryption flattened to ENCRYPTION_TYPE and ENCRYPTION_KMS_KEY_ID, credentials left
     * out), the ACTIVE location, and the comment when there is one.
     */
    public ResultSet describe(final FrostlakeParser.IdentifierContext nameContext) {
        final String name = SqlIdentifiers.canonical(nameContext);
        final ExternalVolume volume = registry().find(name);
        if (volume == null) {
            throw missing(name);
        }
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("parent_property", StringType.VARCHAR),
            new ResultSetColumn("property", StringType.VARCHAR),
            new ResultSetColumn("property_type", StringType.VARCHAR),
            new ResultSetColumn("property_value", StringType.VARCHAR),
            new ResultSetColumn("property_default", StringType.VARCHAR));
        final List<Row> rows = new ArrayList<>();
        if (volume.getComment() != null) {
            rows.add(new Row(Arrays.asList((Object) "", "COMMENT", "String", volume.getComment(), "")));
        }
        rows.add(new Row(Arrays.asList((Object) "", "ALLOW_WRITES", "Boolean",
            volume.isAllowWrites() ? "true" : "false", "true")));
        int index = 0;
        for (final Map<String, PropertyValue> location : volume.getStorageLocations()) {
            index++;
            rows.add(new Row(Arrays.asList((Object) "STORAGE_LOCATIONS", "STORAGE_LOCATION_" + index, "String",
                locationJson(location), "")));
        }
        // The active location is chosen when the volume is first used, which never happens here.
        rows.add(new Row(Arrays.asList((Object) "STORAGE_LOCATIONS", "ACTIVE", "String", "", "")));
        return new ResultSet(columns, rows);
    }

    /**
     * A location as DESCRIBE shows it: NAME, STORAGE_PROVIDER, STORAGE_BASE_URL, the STORAGE_ALLOWED_LOCATIONS the
     * base URL opens, the provider's own properties, and — but for Azure — the encryption flattened to
     * ENCRYPTION_TYPE and ENCRYPTION_KMS_KEY_ID. Credentials never show.
     */
    private static String locationJson(final Map<String, PropertyValue> location) {
        final Map<String, PropertyValue> shown = new LinkedHashMap<>();
        final String baseUrl = location.get("STORAGE_BASE_URL").getText();
        shown.put("NAME", location.get("NAME"));
        shown.put("STORAGE_PROVIDER", location.get("STORAGE_PROVIDER"));
        shown.put("STORAGE_BASE_URL", location.get("STORAGE_BASE_URL"));
        final List<PropertyValue> allowed = new ArrayList<>();
        allowed.add(PropertyValue.text(baseUrl + "*"));
        shown.put("STORAGE_ALLOWED_LOCATIONS", PropertyValue.list(allowed));
        for (final Map.Entry<String, PropertyValue> entry : location.entrySet()) {
            if (!shown.containsKey(entry.getKey()) && !"CREDENTIALS".equals(entry.getKey())
                    && !"ENCRYPTION".equals(entry.getKey())) {
                shown.put(entry.getKey(), entry.getValue());
            }
        }
        if (!"AZURE".equals(location.get("STORAGE_PROVIDER").getText())) {
            final PropertyValue encryption = location.get("ENCRYPTION");
            final PropertyValue type = encryption == null ? null : encryption.entry("TYPE");
            final PropertyValue key = encryption == null ? null : encryption.entry("KMS_KEY_ID");
            shown.put("ENCRYPTION_TYPE", PropertyValue.text(type == null || type.getText() == null ? "NONE"
                : type.getText().toUpperCase(Locale.ROOT)));
            shown.put("ENCRYPTION_KMS_KEY_ID", PropertyValue.text(key == null || key.getText() == null ? ""
                : key.getText()));
        }
        return PropertyValue.properties(shown).json();
    }
}
