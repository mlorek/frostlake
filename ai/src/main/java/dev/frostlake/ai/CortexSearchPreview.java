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

package dev.frostlake.ai;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.table.QueryRunner;
import dev.frostlake.metastore.model.CortexSearchService;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * SEARCH_PREVIEW(service_name, query_json) — queries a Cortex search service and answers with the JSON
 * TEXT Snowflake answers with: {@code {"results": [...], "request_id": "..."}}, ready for
 * {@code PARSE_JSON(...)['results']}.
 *
 * <p>The query object takes {@code query} (required), {@code columns} (which of the service's columns
 * to return, defaulting to all of them) and {@code limit} (defaulting to ten, as Snowflake's does).
 *
 * <p>Ranking is by embedding similarity between the query and each row's search column, which is what
 * the service is FOR — but nothing is indexed ahead of time, so a preview embeds the service's rows on
 * every call. That is fine for the data volumes an emulator sees and would not be for a warehouse; the
 * alternative, keeping an index warm behind a SQL function, would mean a background indexer the engine
 * deliberately does not have.
 */
public class CortexSearchPreview extends BuiltInFunction {

    /** Snowflake's default when the query object names no limit. */
    private static final int DEFAULT_LIMIT = 10;

    /** Installed by {@link AiFunctionProvider}; the catalog and query runner are read from it lazily. */
    private FunctionRegistry registry;

    public CortexSearchPreview(final String name) {
        super(name, StringType.VARCHAR);
    }

    void setRegistry(final FunctionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String serviceName = CortexText.text(args.get(0));
        final String queryJson = args.size() > 1 ? CortexText.text(args.get(1)) : null;
        if (serviceName == null || queryJson == null) {
            return null;
        }
        final CortexSearchService service = resolve(serviceName);
        final JsonNode request = OllamaClient.json().readTree(queryJson);
        final JsonNode queryText = request.get("query");
        if (queryText == null) {
            throw new RuntimeException("Cortex Search request is missing the required 'query' field.");
        }

        final ResultSet source = run(service.getDefinition());
        final List<String> wanted = requestedColumns(request, service, source);
        final int limit = request.get("limit") != null ? request.get("limit").asInt() : DEFAULT_LIMIT;

        final ArrayNode results = CortexJson.array();
        for (final SearchHit hit : rank(source, service.getSearchColumn(), queryText.asString(), limit)) {
            final ObjectNode entry = CortexJson.entry();
            for (final String column : wanted) {
                final int index = source.getColumnIndex(column);
                final Object value = index >= 0 ? hit.getRow().getValue(index) : null;
                entry.put(column, value == null ? null : String.valueOf(value));
            }
            results.add(entry);
        }
        final ObjectNode answer = CortexJson.entry();
        answer.set("results", results);
        answer.put("request_id", UUID.randomUUID().toString());
        return answer.toString();
    }

    /** The service, or the error a real account gives for a name it cannot resolve. */
    private CortexSearchService resolve(final String serviceName) {
        if (registry == null || registry.getCatalog() == null) {
            throw new RuntimeException("Cortex Search Service " + serviceName
                + " does not exist or access is not authorized for the current role.");
        }
        try {
            return registry.getCatalog().resolveCortexSearchService(serviceName);
        } catch (final RuntimeException unresolved) {
            throw new RuntimeException("Cortex Search Service " + serviceName
                + " does not exist or access is not authorized for the current role.");
        }
    }

    private ResultSet run(final String definition) {
        final QueryRunner runner = registry == null ? null : registry.getQueryRunner();
        if (runner == null) {
            throw new RuntimeException("Cortex Search cannot run the service's defining query:"
                + " no query runner is installed.");
        }
        return runner.runQuery(definition);
    }

    /** The columns the request asks for, or every column the service indexes when it asks for none. */
    private List<String> requestedColumns(final JsonNode request, final CortexSearchService service,
                                          final ResultSet source) {
        final List<String> wanted = new ArrayList<>();
        final JsonNode columns = request.get("columns");
        if (columns != null && columns.isArray()) {
            for (int i = 0; i < columns.size(); i++) {
                wanted.add(columns.get(i).asString());
            }
            return wanted;
        }
        if (!service.getColumns().isEmpty()) {
            return service.getColumns();
        }
        for (final ResultSetColumn column : source.getColumns()) {
            wanted.add(column.getName());
        }
        return wanted;
    }

    /** The best-matching rows, most similar first. */
    private List<SearchHit> rank(final ResultSet source, final String searchColumn,
                                 final String queryText, final int limit) {
        final int searchIndex = source.getColumnIndex(searchColumn);
        if (searchIndex < 0) {
            return new ArrayList<>();
        }
        final List<Double> queryEmbedding = OllamaClient.embed(OllamaConfig.embedModel(), queryText);
        final List<SearchHit> hits = new ArrayList<>();
        for (final Row row : source.getRows()) {
            final Object value = row.getValue(searchIndex);
            if (value == null) {
                continue;
            }
            final List<Double> rowEmbedding =
                OllamaClient.embed(OllamaConfig.embedModel(), String.valueOf(value));
            hits.add(new SearchHit(row, CosineSimilarity.of(queryEmbedding, rowEmbedding)));
        }
        Collections.sort(hits, new SearchHitOrder());
        return hits.subList(0, Math.min(limit, hits.size()));
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
