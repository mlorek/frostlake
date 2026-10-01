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

package dev.frostlake.http.rest.resource;

import dev.frostlake.http.rest.RestCall;
import dev.frostlake.http.rest.RestCreateMode;
import dev.frostlake.http.rest.RestException;
import dev.frostlake.http.rest.RestIdentifier;
import dev.frostlake.http.rest.RestJson;
import dev.frostlake.http.rest.RestResource;
import dev.frostlake.http.rest.RestResponse;
import dev.frostlake.http.rest.RestRouter;
import dev.frostlake.http.rest.RestRow;
import dev.frostlake.http.rest.RestShow;
import dev.frostlake.http.rest.RestSql;
import dev.frostlake.http.rest.RestStatement;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;

/**
 * External volumes ({@code external-volume.yaml}, {@code /api/v2/external-volumes}): list, create, fetch, delete
 * and {@code :undrop}.
 *
 * <p>A volume is read from {@code SHOW EXTERNAL VOLUMES} (name, allow_writes, comment) and its storage locations
 * from the STORAGE_LOCATION_n rows of {@code DESCRIBE EXTERNAL VOLUME}, whose values are JSON objects; the
 * encryption DESCRIBE flattens to ENCRYPTION_TYPE and ENCRYPTION_KMS_KEY_ID becomes the location's
 * {@code encryption} object again.
 */
public final class ExternalVolumeResource implements RestResource {

    private static final String COLLECTION = "/api/v2/external-volumes";
    private static final String ITEM = COLLECTION + "/{name}";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listExternalVolumes", this);
        router.add("POST", COLLECTION, "createExternalVolume", this);
        router.add("GET", ITEM, "fetchExternalVolume", this);
        router.add("DELETE", ITEM, "deleteExternalVolume", this);
        router.add("POST", ITEM + ":undrop", "undropExternalVolume", this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        switch (call.operation()) {
            case "listExternalVolumes":
                final ArrayNode out = RestJson.array();
                for (final RestRow row : call.sql().show("SHOW EXTERNAL VOLUMES" + RestShow.like(call))) {
                    out.add(toJson(call, row));
                }
                return RestResponse.json(200, out);
            case "createExternalVolume":
                return create(call);
            case "fetchExternalVolume":
                final RestIdentifier name = call.identifier("name");
                final List<RestRow> rows = call.sql().showNamed("SHOW EXTERNAL VOLUMES" + RestShow.likeName(name),
                    name);
                if (rows.isEmpty()) {
                    throw RestException.notFound("External volume '" + name + "' does not exist or not authorized.");
                }
                return RestResponse.json(200, toJson(call, rows.get(0)));
            case "deleteExternalVolume":
                return call.sql().action("DROP EXTERNAL VOLUME" + call.ifExists() + " " + call.identifier("name").sql());
            case "undropExternalVolume":
                return call.sql().action("UNDROP EXTERNAL VOLUME " + call.identifier("name").sql());
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static ObjectNode toJson(final RestCall call, final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        final ArrayNode locations = RestJson.array();
        for (final RestRow property : call.sql().show("DESCRIBE EXTERNAL VOLUME "
                + RestIdentifier.quote(row.string("name")))) {
            final String key = property.string("property");
            if (key == null || !key.startsWith("STORAGE_LOCATION_")) {
                continue;
            }
            locations.add(location(RestIntegrations.object(property.string("property_value"))));
        }
        node.set("storage_locations", locations);
        RestJson.bool(node, "allow_writes", row, "allow_writes");
        RestJson.string(node, "comment", row, "comment");
        RestJson.nulls(node, "created_on", "owner", "owner_role_type");
        return node;
    }

    /** One storage location from DESCRIBE's JSON object. */
    private static ObjectNode location(final JsonNode shown) {
        final ObjectNode location = RestJson.object();
        final String provider = RestIntegrations.member(shown, "STORAGE_PROVIDER");
        RestIntegrations.text(location, "name", RestIntegrations.member(shown, "NAME"));
        RestIntegrations.text(location, "storage_provider", provider);
        RestIntegrations.text(location, "storage_base_url", RestIntegrations.member(shown, "STORAGE_BASE_URL"));
        if ("AZURE".equals(provider)) {
            RestIntegrations.text(location, "azure_tenant_id", RestIntegrations.member(shown, "AZURE_TENANT_ID"));
            return location;
        }
        if (!"GCS".equals(provider)) {
            RestIntegrations.text(location, "storage_aws_role_arn",
                RestIntegrations.member(shown, "STORAGE_AWS_ROLE_ARN"));
            RestIntegrations.text(location, "storage_aws_external_id",
                RestIntegrations.member(shown, "STORAGE_AWS_EXTERNAL_ID"));
        }
        final String encryption = RestIntegrations.member(shown, "ENCRYPTION_TYPE");
        if (encryption != null) {
            final ObjectNode encryptionNode = RestJson.object();
            encryptionNode.put("type", encryption);
            RestIntegrations.text(encryptionNode, "kms_key_id", RestIntegrations.member(shown, "ENCRYPTION_KMS_KEY_ID"));
            location.set("encryption", encryptionNode);
        } else {
            location.putNull("encryption");
        }
        return location;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + " EXTERNAL VOLUME"
            + mode.ifNotExists() + " " + name.sql());
        final JsonNode locations = body.get("storage_locations");
        if (locations == null || !locations.isArray() || locations.isEmpty()) {
            throw RestException.badRequest("Missing required property 'storage_locations'.");
        }
        final StringBuilder list = new StringBuilder("(");
        int count = 0;
        for (final JsonNode location : locations.values()) {
            if (!location.isObject()) {
                throw RestException.badRequest("Property 'storage_locations' must be an array of objects.");
            }
            if (count++ > 0) {
                list.append(", ");
            }
            list.append(locationSql(location));
        }
        sql.property("STORAGE_LOCATIONS", list.append(')').toString());
        sql.bool(body, "allow_writes", "ALLOW_WRITES");
        sql.string(body, "comment", "COMMENT");
        return call.sql().action(sql.toString());
    }

    /** {@code (NAME = '…' STORAGE_PROVIDER = '…' …)} for one location of the body. */
    private static String locationSql(final JsonNode location) {
        final RestStatement sql = new RestStatement("(");
        sql.string(location, "name", "NAME");
        final String provider = RestJson.text(location, "storage_provider");
        if (provider != null) {
            sql.property("STORAGE_PROVIDER", RestSql.literal(provider.toUpperCase(Locale.ROOT)));
        }
        sql.string(location, "storage_base_url", "STORAGE_BASE_URL");
        sql.string(location, "storage_aws_role_arn", "STORAGE_AWS_ROLE_ARN");
        sql.string(location, "storage_aws_external_id", "STORAGE_AWS_EXTERNAL_ID");
        sql.string(location, "azure_tenant_id", "AZURE_TENANT_ID");
        final JsonNode encryption = location.get("encryption");
        if (encryption != null && encryption.isObject()) {
            final RestStatement nested = new RestStatement("(");
            nested.string(encryption, "type", "TYPE");
            nested.string(encryption, "kms_key_id", "KMS_KEY_ID");
            sql.property("ENCRYPTION", nested.append(" )").toString());
        }
        return sql.append(" )").toString();
    }
}
