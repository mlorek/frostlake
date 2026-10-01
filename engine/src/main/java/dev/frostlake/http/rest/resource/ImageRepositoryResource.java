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
import dev.frostlake.http.rest.RestTags;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * The image repository endpoints ({@code image-repository.yaml}): SHOW IMAGE REPOSITORIES, CREATE / DROP IMAGE
 * REPOSITORY, SHOW IMAGES IN IMAGE REPOSITORY, which lists nothing since no image is ever pushed, and the tag
 * endpoints through ALTER IMAGE REPOSITORY.
 */
public final class ImageRepositoryResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/image-repositories";
    private static final String ITEM = COLLECTION + "/{name}";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listImageRepositories", this);
        router.add("POST", COLLECTION, "createImageRepository", this);
        router.add("GET", ITEM, "fetchImageRepository", this);
        router.add("DELETE", ITEM, "deleteImageRepository", this);
        router.add("GET", ITEM + "/images", "listImagesInRepository", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER IMAGE REPOSITORY", call.qualifiedSql(name), "IMAGE REPOSITORY",
                call.qualifiedSql(name), call.identifier("database").sql());
        }
        switch (call.operation()) {
            case "listImageRepositories":
                return list(call);
            case "createImageRepository":
                return create(call);
            case "fetchImageRepository":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "deleteImageRepository":
                return call.sql().action("DROP IMAGE REPOSITORY" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")));
            case "listImagesInRepository":
                return images(call);
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static String show(final RestCall call, final String like) {
        return "SHOW IMAGE REPOSITORIES" + like + " IN SCHEMA " + call.schemaSql();
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show(show(call, RestShow.like(call)))) {
            out.add(toJson(row));
        }
        return RestResponse.json(200, out);
    }

    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed(show(call, RestShow.likeName(name)), name);
        if (rows.isEmpty()) {
            throw RestException.notFound("Image repository '"
                + UserDefinedFunctionResource.qualified(call, name) + "' does not exist or not authorized.");
        }
        return toJson(rows.get(0));
    }

    private static ObjectNode toJson(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.string(node, "repository_url", row, "repository_url");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        return node;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        return call.sql().action("CREATE" + mode.orReplace() + " IMAGE REPOSITORY" + mode.ifNotExists() + " "
            + call.qualifiedSql(name));
    }

    private static RestResponse images(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW IMAGES IN IMAGE REPOSITORY "
                + call.qualifiedSql(call.identifier("name")))) {
            final ObjectNode image = RestJson.object();
            RestJson.string(image, "created_on", row, "created_on");
            RestJson.string(image, "image_name", row, "image_name");
            RestJson.string(image, "tags", row, "tags");
            RestJson.string(image, "digest", row, "digest");
            RestJson.string(image, "image_path", row, "image_path");
            RestJson.nulls(image, "created_on", "image_name", "tags", "digest", "image_path");
            out.add(image);
        }
        return RestResponse.json(200, out);
    }
}
