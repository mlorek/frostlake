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

import dev.frostlake.config.EngineConfig;
import dev.frostlake.executor.ConditionalDdlOutcome;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.StatementTokens;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.ArtifactRepository;
import dev.frostlake.metastore.model.ContainerObjects;
import dev.frostlake.metastore.model.ContainerService;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.ImageRepository;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Snowpark Container Services statements over catalog metadata — image repositories, services and job services
 * — and the artifact repository statements. Nothing runs: a service keeps its declared state (RUNNING once created
 * or resumed, SUSPENDED once suspended), reports no instances or containers, and a job service is recorded as DONE.
 * Its endpoints and service roles are read from the specification text.
 */
public final class ContainerServicesHandler {

    private static final String[] SERVICE_PROPERTIES = {"AUTO_SUSPEND_SECS", "EXTERNAL_ACCESS_INTEGRATIONS",
        "AUTO_RESUME", "MIN_INSTANCES", "MIN_READY_INSTANCES", "MAX_INSTANCES", "LOG_LEVEL", "QUERY_WAREHOUSE",
        "COMMENT"};
    private static final String[] JOB_PROPERTIES = {"NAME", "ASYNC", "REPLICAS", "QUERY_WAREHOUSE", "COMMENT",
        "EXTERNAL_ACCESS_INTEGRATIONS"};

    private static final String[] SERVICE_COLUMNS = {"name", "status", "database_name", "schema_name", "owner",
        "compute_pool", "dns_name", "current_instances", "target_instances", "min_ready_instances", "min_instances",
        "max_instances", "auto_resume", "external_access_integrations", "created_on", "updated_on", "resumed_on",
        "suspended_on", "auto_suspend_secs", "comment", "owner_role_type", "query_warehouse", "is_job",
        "is_async_job", "spec_digest", "is_upgrading", "managing_object_domain", "managing_object_name"};

    private final Catalog catalog;
    private final EngineConfig config;

    /**
     * @param catalog the catalog the objects live in
     * @param config the engine configuration, for the account names a repository URL carries
     */
    public ContainerServicesHandler(final Catalog catalog, final EngineConfig config) {
        this.catalog = catalog;
        this.config = config;
    }

    /** Runs one container statement: a result set for a SHOW or DESCRIBE, null for the others. */
    public Object handle(final FrostlakeParser.ContainerServicesStatementContext ctx) {
        final int verb = ctx.getStart().getType();
        if (ctx.IMAGES() != null) {
            imageRepository(ctx.qualifiedName(), true);
            return emptyImages();
        }
        if (ctx.REPOSITORIES() != null) {
            return ctx.IMAGE() != null ? showImageRepositories(ctx) : showArtifactRepositories(ctx);
        }
        if (ctx.IMAGE() != null) {
            if (verb == FrostlakeParser.ALTER) {
                return alterImageRepository(ctx);
            }
            return verb == FrostlakeParser.CREATE ? createImageRepository(ctx) : dropImageRepository(ctx);
        }
        if (ctx.ARTIFACT() != null) {
            return artifactRepository(ctx, verb);
        }
        if (ctx.JOB() != null && verb == FrostlakeParser.EXECUTE) {
            return executeJob(ctx);
        }
        if (ctx.SERVICES() != null) {
            return showServices(ctx);
        }
        if (ctx.CONTAINERS() != null || ctx.INSTANCES() != null) {
            service(ctx.qualifiedName(), true);
            return ctx.CONTAINERS() != null ? emptyContainers() : emptyInstances();
        }
        if (ctx.ENDPOINTS() != null) {
            return endpoints(service(ctx.qualifiedName(), true));
        }
        if (verb == FrostlakeParser.CREATE) {
            return createService(ctx);
        }
        if (verb == FrostlakeParser.ALTER) {
            return alterService(ctx);
        }
        if (verb == FrostlakeParser.DROP) {
            return dropService(ctx);
        }
        return describeService(ctx);
    }

    // ---- names --------------------------------------------------------------------------------------------

    private static String[] parts(final FrostlakeParser.QualifiedNameContext name) {
        return ParseTreeText.qualifiedNameParts(name);
    }

    private Schema schemaOf(final FrostlakeParser.QualifiedNameContext name) {
        return catalog.requireOwningSchema(QualifiedName.of(parts(name)));
    }

    private static String last(final FrostlakeParser.QualifiedNameContext name) {
        final String[] parts = parts(name);
        return parts[parts.length - 1];
    }

    /** The object's full name as a refusal spells it: database, schema and name. */
    private String fullName(final Schema schema, final String name) {
        return QualifiedName.join(databaseOf(schema), schema.getName(), name);
    }

    private String databaseOf(final Schema schema) {
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema candidate : database.getAllSchemas()) {
                if (candidate == schema) {
                    return database.getName();
                }
            }
        }
        return catalog.getCurrentDatabase();
    }

    // ---- properties ---------------------------------------------------------------------------------------

    private static String propertyName(final FrostlakeParser.ContainerPropertyContext property) {
        return SqlIdentifiers.canonical(property.identifier()).toUpperCase(Locale.ROOT);
    }

    private static RuntimeException invalidProperty(final String name, final String kind) {
        return new RuntimeException(SqlCompilationError.of("invalid property '" + name + "' for '" + kind + "'"));
    }

    private static void requireKnown(final String name, final String[] known, final String kind) {
        for (final String candidate : known) {
            if (candidate.equals(name)) {
                return;
            }
        }
        throw invalidProperty(name, kind);
    }

    /** A value as text: a string literal decoded, a name canonical, a number or boolean as written. */
    private static String text(final FrostlakeParser.ContainerValueContext value) {
        if (value.STRING_LITERAL() != null) {
            return SqlStringLiterals.decode(value.STRING_LITERAL().getText());
        }
        if (value.qualifiedName() != null) {
            return String.join(".", parts(value.qualifiedName()));
        }
        return value.getText().toUpperCase(Locale.ROOT);
    }

    private static Long number(final FrostlakeParser.ContainerValueContext value, final String name) {
        if (value.INTEGER_LITERAL() == null) {
            throw new RuntimeException(SqlCompilationError.of("invalid value [" + value.getText()
                + "] for parameter '" + name + "'"));
        }
        return Long.valueOf(value.getText());
    }

    private static Boolean bool(final FrostlakeParser.ContainerValueContext value, final String name) {
        final String text = text(value).toUpperCase(Locale.ROOT);
        if ("TRUE".equals(text) || "FALSE".equals(text)) {
            return Boolean.valueOf("TRUE".equals(text));
        }
        throw new RuntimeException(SqlCompilationError.of("invalid value [" + value.getText()
            + "] for parameter '" + name + "'"));
    }

    private static List<String> names(final FrostlakeParser.ContainerValueContext value) {
        final List<String> out = new ArrayList<>();
        for (final FrostlakeParser.ContainerValueContext item : value.containerValue()) {
            out.add(text(item));
        }
        if (value.containerValue().isEmpty() && value.LPAREN() == null) {
            out.add(text(value));
        }
        return out;
    }

    // ---- image repositories -------------------------------------------------------------------------------

    private ImageRepository imageRepository(final FrostlakeParser.QualifiedNameContext name, final boolean require) {
        final Schema schema = schemaOf(name);
        final ImageRepository repository = schema.getContainerObjects().getImageRepository(last(name));
        if (repository == null && require) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Image repository",
                fullName(schema, last(name))));
        }
        return repository;
    }

    private Object createImageRepository(final FrostlakeParser.ContainerServicesStatementContext ctx) {
        final Schema schema = schemaOf(ctx.qualifiedName());
        final String name = last(ctx.qualifiedName());
        final ContainerObjects objects = schema.getContainerObjects();
        if (objects.getImageRepository(name) != null && ctx.or_replace() == null) {
            if (ctx.if_not_exists() != null) {
                ConditionalDdlOutcome.createSkipped();
                return null;
            }
            throw new RuntimeException(SqlCompilationError.of("Object '" + name + "' already exists."));
        }
        final ImageRepository repository = new ImageRepository(name);
        for (final FrostlakeParser.ContainerPropertyContext property : ctx.containerProperty()) {
            final String key = propertyName(property);
            if ("COMMENT".equals(key)) {
                repository.setComment(text(property.containerValue()));
            } else if ("ENCRYPTION".equals(key)) {
                for (final FrostlakeParser.ContainerPropertyContext inner : property.containerValue()
                        .containerProperty()) {
                    if (!"TYPE".equals(propertyName(inner))) {
                        throw invalidProperty(propertyName(inner), "ENCRYPTION");
                    }
                    final String type = text(inner.containerValue()).toUpperCase(Locale.ROOT);
                    if (!"SNOWFLAKE_FULL".equals(type) && !"SNOWFLAKE_SSE".equals(type)) {
                        throw new RuntimeException(SqlCompilationError.of("invalid value [" + type
                            + "] for parameter 'TYPE'"));
                    }
                    repository.setEncryption(type);
                }
            } else {
                throw invalidProperty(key, "STAGE");
            }
        }
        repository.setOwner(catalog.currentRoleForOwner());
        objects.putImageRepository(repository);
        return null;
    }

    private Object alterImageRepository(final FrostlakeParser.ContainerServicesStatementContext ctx) {
        final ImageRepository repository = imageRepository(ctx.qualifiedName(), ctx.if_exists() == null);
        if (repository == null) {
            return null;
        }
        final FrostlakeParser.ArtifactRepositoryAlterActionContext action = ctx.artifactRepositoryAlterAction();
        if (action.SET() != null && action.tagSet() == null) {
            for (final FrostlakeParser.ContainerPropertyContext property : action.containerProperty()) {
                if (!"COMMENT".equals(propertyName(property))) {
                    throw invalidProperty(propertyName(property), "STAGE");
                }
                repository.setComment(text(property.containerValue()));
            }
        } else if (action.UNSET() != null && action.tagUnset() == null) {
            for (final FrostlakeParser.IdentifierContext id : action.identifier()) {
                if (!"COMMENT".equals(SqlIdentifiers.canonical(id).toUpperCase(Locale.ROOT))) {
                    throw invalidProperty(SqlIdentifiers.canonical(id), "STAGE");
                }
                repository.setComment(null);
            }
        }
        return null;
    }

    private Object dropImageRepository(final FrostlakeParser.ContainerServicesStatementContext ctx) {
        final Schema schema = schemaOf(ctx.qualifiedName());
        final String name = last(ctx.qualifiedName());
        if (!schema.getContainerObjects().removeImageRepository(name)) {
            if (ctx.if_exists() != null) {
                ConditionalDdlOutcome.dropSkipped();
                return null;
            }
            throw new RuntimeException(SqlCompilationError.doesNotExist("Image repository", fullName(schema, name)));
        }
        return null;
    }

    private ResultSet showImageRepositories(final FrostlakeParser.ContainerServicesStatementContext ctx) {
        final List<ResultSetColumn> columns = columns("created_on", "name", "database_name", "schema_name",
            "repository_url", "owner", "owner_role_type", "comment", "encryption");
        final List<Row> rows = new ArrayList<>();
        final String like = like(ctx);
        for (final Schema schema : scope(ctx.containerShowScope())) {
            final String database = databaseOf(schema);
            for (final ImageRepository repository : schema.getContainerObjects().getImageRepositories()) {
                if (like != null && !ShowResultHelpers.matchesLike(repository.getName(), like)) {
                    continue;
                }
                rows.add(new Row(Arrays.<Object>asList(ShowResultHelpers.createdOn(repository.getCreatedOn()),
                    repository.getName(), database, schema.getName(),
                    repositoryUrl(database, schema.getName(), repository.getName()), repository.getOwner(),
                    ShowResultHelpers.ownerRoleType(repository.getOwner()), text(repository.getComment()),
                    repository.getEncryption())));
            }
        }
        return new ResultSet(columns, rows);
    }

    /** The registry URL a repository is pushed to and pulled from: {@code <org>-<account>.registry…/…}. */
    private String repositoryUrl(final String database, final String schema, final String repository) {
        final String organization = config == null ? null : config.getOrganizationName();
        final String account = config == null ? null : config.getAccountName();
        final String host = (organization == null ? "" : organization + "-")
            + (account == null ? "frostlake" : account);
        return (host.replace('_', '-') + ".registry.snowflakecomputing.com/" + database + "/" + schema + "/"
            + repository).toLowerCase(Locale.ROOT);
    }

    private static ResultSet emptyImages() {
        return new ResultSet(columns("created_on", "image_name", "tags", "digest", "image_path"), new ArrayList<>());
    }

    // ---- services -----------------------------------------------------------------------------------------

    private ContainerService service(final FrostlakeParser.QualifiedNameContext name, final boolean require) {
        final Schema schema = schemaOf(name);
        final ContainerService service = schema.getContainerObjects().getService(last(name));
        if (service == null && require) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Service", fullName(schema, last(name))));
        }
        return service;
    }

    /**
     * The service a {@code SHOW ROLES IN SERVICE <name>} names, resolved in the current schema, as its role listing;
     * null when no such service exists, leaving the refusal to the listing's own scope rules.
     */
    public ResultSet serviceRolesOrNull(final String name) {
        final String database = catalog.getCurrentDatabase();
        final String schemaName = catalog.getCurrentSchema();
        if (database == null || schemaName == null) {
            return null;
        }
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(database, schemaName, name));
        final ContainerService service = schema.getContainerObjects().getService(name);
        if (service == null) {
            return null;
        }
        final List<Row> rows = new ArrayList<>();
        final Object created = ShowResultHelpers.createdOn(service.getCreatedOn());
        rows.add(new Row(Arrays.<Object>asList(created, "ALL_ENDPOINTS_USAGE", null)));
        for (final Map<String, String> role : ServiceSpecReader.entries(service.getSpecification(), "serviceRoles")) {
            if (role.get("name") != null) {
                rows.add(new Row(Arrays.<Object>asList(created, role.get("name").toUpperCase(Locale.ROOT), null)));
            }
        }
        return new ResultSet(columns("created_on", "name", "comment"), rows);
    }

    private void applySource(final ContainerService service, final FrostlakeParser.ServiceSourceContext source) {
        final boolean template = source.SPECIFICATION_TEMPLATE() != null
            || source.SPECIFICATION_TEMPLATE_FILE() != null;
        if (source.STRING_LITERAL() != null && (source.SPECIFICATION_FILE() != null
                || source.SPECIFICATION_TEMPLATE_FILE() != null)) {
            service.setSpecification(null, source.stageRef() == null ? null : source.stageRef().getText(),
                SqlStringLiterals.decode(source.STRING_LITERAL().getText()), template);
            return;
        }
        final String text = source.DOLLAR_QUOTED_STRING() != null
            ? dollarQuoted(source.DOLLAR_QUOTED_STRING().getText())
            : SqlStringLiterals.decode(source.STRING_LITERAL().getText());
        service.setSpecification(text, null, null, template);
    }

    private static String dollarQuoted(final String token) {
        return token.substring(2, token.length() - 2);
    }

    private String requireComputePool(final FrostlakeParser.IdentifierContext pool) {
        final String name = SqlIdentifiers.canonical(pool);
        if (!catalog.hasComputePool(name)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Compute pool", name));
        }
        return name;
    }

    private void setServiceProperty(final ContainerService service, final FrostlakeParser.ContainerPropertyContext
            property, final String kind) {
        final String key = propertyName(property);
        final FrostlakeParser.ContainerValueContext value = property.containerValue();
        switch (key) {
            case "AUTO_SUSPEND_SECS":
                service.setAutoSuspendSecs(number(value, key));
                break;
            case "MIN_INSTANCES":
                service.setMinInstances(number(value, key));
                break;
            case "MAX_INSTANCES":
                service.setMaxInstances(number(value, key));
                break;
            case "MIN_READY_INSTANCES":
                service.setMinReadyInstances(number(value, key));
                break;
            case "AUTO_RESUME":
                service.setAutoResume(bool(value, key));
                break;
            case "LOG_LEVEL":
                service.setLogLevel(text(value).toUpperCase(Locale.ROOT));
                break;
            case "QUERY_WAREHOUSE":
                service.setQueryWarehouse(text(value));
                break;
            case "COMMENT":
                service.setComment(text(value));
                break;
            case "EXTERNAL_ACCESS_INTEGRATIONS":
                service.setExternalAccessIntegrations(names(value));
                break;
            default:
                throw invalidProperty(key, kind);
        }
    }

    private Object createService(final FrostlakeParser.ContainerServicesStatementContext ctx) {
        final Schema schema = schemaOf(ctx.qualifiedName());
        final String name = last(ctx.qualifiedName());
        final ContainerObjects objects = schema.getContainerObjects();
        if (objects.getService(name) != null) {
            if (ctx.if_not_exists() != null) {
                ConditionalDdlOutcome.createSkipped();
                return null;
            }
            throw new RuntimeException(SqlCompilationError.of("Object '" + name + "' already exists."));
        }
        final ContainerService service = new ContainerService(name, false);
        service.setComputePool(requireComputePool(ctx.identifier()));
        applySource(service, ctx.serviceSource());
        for (final FrostlakeParser.ContainerPropertyContext property : ctx.containerProperty()) {
            requireKnown(propertyName(property), SERVICE_PROPERTIES, "SERVICE");
            setServiceProperty(service, property, "SERVICE");
        }
        service.setOwner(catalog.currentRoleForOwner());
        service.setStatus("RUNNING");
        objects.putService(service);
        return null;
    }

    private Object executeJob(final FrostlakeParser.ContainerServicesStatementContext ctx) {
        String name = null;
        Schema schema = null;
        final ContainerService declared = new ContainerService("JOB", true);
        boolean async = false;
        for (final FrostlakeParser.ContainerPropertyContext property : ctx.containerProperty()) {
            final String key = propertyName(property);
            requireKnown(key, JOB_PROPERTIES, "JOB SERVICE");
            if ("NAME".equals(key)) {
                final FrostlakeParser.QualifiedNameContext written = property.containerValue().qualifiedName();
                if (written == null) {
                    throw invalidProperty(key, "JOB SERVICE");
                }
                schema = schemaOf(written);
                name = last(written);
            } else if ("ASYNC".equals(key)) {
                async = bool(property.containerValue(), key).booleanValue();
            } else if (!"REPLICAS".equals(key)) {
                setServiceProperty(declared, property, "JOB SERVICE");
            } else {
                number(property.containerValue(), key);
            }
        }
        if (schema == null) {
            schema = catalog.requireOwningSchema(QualifiedName.of("JOB"));
            name = "JOB_" + StatementTokens.draw("0123456789ABCDEF", 16);
        }
        if (schema.getContainerObjects().getService(name) != null) {
            throw new RuntimeException(SqlCompilationError.of("Object '" + name + "' already exists."));
        }
        final ContainerService job = new ContainerService(name, true);
        job.setComputePool(requireComputePool(ctx.identifier()));
        applySource(job, ctx.serviceSource());
        job.setComment(declared.getComment());
        job.setQueryWarehouse(declared.getQueryWarehouse());
        job.setExternalAccessIntegrations(declared.getExternalAccessIntegrations());
        job.setAsyncJob(async);
        job.setOwner(catalog.currentRoleForOwner());
        job.setStatus("DONE");
        schema.getContainerObjects().putService(job);
        return statusResult("Job " + name + " completed successfully with status: DONE.");
    }

    private static ResultSet statusResult(final String status) {
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.<Object>asList(status)));
        return new ResultSet(columns("status"), rows);
    }

    private Object alterService(final FrostlakeParser.ContainerServicesStatementContext ctx) {
        final ContainerService service = service(ctx.qualifiedName(), ctx.if_exists() == null);
        if (service == null) {
            return null;
        }
        final FrostlakeParser.ServiceAlterActionContext action = ctx.serviceAlterAction();
        if (action.SUSPEND() != null) {
            service.setStatus("SUSPENDED");
        } else if (action.RESUME() != null) {
            service.setStatus(service.isJob() ? service.getStatus() : "RUNNING");
        } else if (action.serviceSource() != null) {
            applySource(service, action.serviceSource());
        } else if (action.SET() != null && action.tagSet() == null) {
            for (final FrostlakeParser.ContainerPropertyContext property : action.containerProperty()) {
                requireKnown(propertyName(property), SERVICE_PROPERTIES, "SERVICE");
            }
            for (final FrostlakeParser.ContainerPropertyContext property : action.containerProperty()) {
                setServiceProperty(service, property, "SERVICE");
            }
        } else if (action.UNSET() != null && action.tagUnset() == null) {
            for (final FrostlakeParser.IdentifierContext id : action.identifier()) {
                final String key = SqlIdentifiers.canonical(id).toUpperCase(Locale.ROOT);
                requireKnown(key, SERVICE_PROPERTIES, "SERVICE");
                unsetServiceProperty(service, key);
            }
        }
        service.touch();
        return null;
    }

    private static void unsetServiceProperty(final ContainerService service, final String key) {
        switch (key) {
            case "AUTO_SUSPEND_SECS":
                service.setAutoSuspendSecs(null);
                break;
            case "MIN_INSTANCES":
                service.setMinInstances(null);
                break;
            case "MAX_INSTANCES":
                service.setMaxInstances(null);
                break;
            case "MIN_READY_INSTANCES":
                service.setMinReadyInstances(null);
                break;
            case "AUTO_RESUME":
                service.setAutoResume(null);
                break;
            case "LOG_LEVEL":
                service.setLogLevel(null);
                break;
            case "QUERY_WAREHOUSE":
                service.setQueryWarehouse(null);
                break;
            case "COMMENT":
                service.setComment(null);
                break;
            default:
                service.setExternalAccessIntegrations(new ArrayList<String>());
                break;
        }
    }

    private Object dropService(final FrostlakeParser.ContainerServicesStatementContext ctx) {
        final Schema schema = schemaOf(ctx.qualifiedName());
        final String name = last(ctx.qualifiedName());
        if (!schema.getContainerObjects().removeService(name)) {
            if (ctx.if_exists() != null) {
                ConditionalDdlOutcome.dropSkipped();
                return null;
            }
            throw new RuntimeException(SqlCompilationError.doesNotExist("Service", fullName(schema, name)));
        }
        return null;
    }

    private ResultSet showServices(final FrostlakeParser.ContainerServicesStatementContext ctx) {
        final List<ResultSetColumn> columns = serviceColumns(false);
        final List<Row> rows = new ArrayList<>();
        final String like = like(ctx);
        final FrostlakeParser.ContainerShowScopeContext scope = ctx.containerShowScope();
        final String pool = scope != null && scope.POOL() != null ? SqlIdentifiers.canonical(scope.identifier())
            : null;
        final String startsWith = ctx.showTail() != null && ctx.showTail().STARTS() != null
            && !ctx.showTail().STRING_LITERAL().isEmpty()
            ? SqlStringLiterals.decode(ctx.showTail().STRING_LITERAL(0).getText()) : null;
        for (final Schema schema : pool != null ? scope(null, true) : scope(scope)) {
            for (final ContainerService service : schema.getContainerObjects().getServices()) {
                if (ctx.JOB() != null && !service.isJob() || ctx.JOBS() != null && service.isJob()
                        || pool != null && !pool.equals(service.getComputePool())
                        || like != null && !ShowResultHelpers.matchesLike(service.getName(), like)
                        || startsWith != null && !service.getName().startsWith(startsWith)) {
                    continue;
                }
                rows.add(serviceRow(schema, service, false));
            }
        }
        return limit(new ResultSet(columns, rows), ctx.showTail());
    }

    private static ResultSet limit(final ResultSet set, final FrostlakeParser.ShowTailContext tail) {
        if (tail == null || tail.LIMIT() == null || tail.INTEGER_LITERAL() == null) {
            return set;
        }
        final int max = Integer.parseInt(tail.INTEGER_LITERAL().getText());
        String from = null;
        if (tail.FROM() != null) {
            final int index = tail.STARTS() != null ? 1 : 0;
            from = SqlStringLiterals.decode(tail.STRING_LITERAL(index).getText());
        }
        final List<Row> kept = new ArrayList<>();
        for (final Row row : set.getRows()) {
            if (from != null && String.valueOf(row.getValue(0)).compareTo(from) < 0) {
                continue;
            }
            if (kept.size() < max) {
                kept.add(row);
            }
        }
        return new ResultSet(set.getColumns(), kept);
    }

    private static List<ResultSetColumn> serviceColumns(final boolean describe) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        for (final String name : SERVICE_COLUMNS) {
            columns.add(new ResultSetColumn(name, serviceColumnType(name)));
            if (describe && "compute_pool".equals(name)) {
                columns.add(new ResultSetColumn("spec", StringType.VARCHAR));
            }
        }
        return columns;
    }

    private static DataType serviceColumnType(final String name) {
        if (name.endsWith("_on")) {
            return ShowResultHelpers.CREATED_ON;
        }
        if (name.endsWith("_instances") || "auto_suspend_secs".equals(name)) {
            return NumericType.NUMBER;
        }
        return StringType.VARCHAR;
    }

    private Row serviceRow(final Schema schema, final ContainerService service, final boolean describe) {
        final String database = databaseOf(schema);
        final List<Object> values = new ArrayList<>();
        values.add(service.getName());
        values.add(service.getStatus());
        values.add(database);
        values.add(schema.getName());
        values.add(service.getOwner());
        values.add(service.getComputePool());
        if (describe) {
            values.add(specText(service));
        }
        values.add((service.getName() + "." + schema.getName() + "." + database + ".svc.spcs.internal")
            .toLowerCase(Locale.ROOT).replace('_', '-'));
        values.add(Long.valueOf(0));
        values.add(Long.valueOf(service.isJob() || "SUSPENDED".equals(service.getStatus()) ? 0
            : orDefault(service.getMinInstances(), 1)));
        values.add(Long.valueOf(orDefault(service.getMinReadyInstances(), orDefault(service.getMinInstances(), 1))));
        values.add(Long.valueOf(orDefault(service.getMinInstances(), 1)));
        values.add(Long.valueOf(orDefault(service.getMaxInstances(), orDefault(service.getMinInstances(), 1))));
        values.add(service.getAutoResume() == null || service.getAutoResume().booleanValue() ? "true" : "false");
        values.add(service.getExternalAccessIntegrations().isEmpty() ? null
            : "[" + String.join(",", service.getExternalAccessIntegrations()) + "]");
        values.add(ShowResultHelpers.createdOn(service.getCreatedOn()));
        values.add(ShowResultHelpers.createdOn(service.getUpdatedOn()));
        values.add(stamp(service.getResumedOn()));
        values.add(stamp(service.getSuspendedOn()));
        values.add(Long.valueOf(orDefault(service.getAutoSuspendSecs(), 0)));
        values.add(service.getComment());
        values.add(ShowResultHelpers.ownerRoleType(service.getOwner()));
        values.add(service.getQueryWarehouse());
        values.add(service.isJob() ? "true" : "false");
        values.add(service.isAsyncJob() ? "true" : "false");
        values.add(Integer.toHexString(specText(service).hashCode()));
        values.add("false");
        values.add(null);
        values.add(null);
        return new Row(values);
    }

    private static long orDefault(final Long value, final long fallback) {
        return value == null ? fallback : value.longValue();
    }

    private static Object stamp(final Instant instant) {
        return instant == null ? null : ShowResultHelpers.createdOn(instant);
    }

    private static String specText(final ContainerService service) {
        if (service.getSpecification() != null) {
            return service.getSpecification();
        }
        return (service.getSpecificationStage() == null ? "" : service.getSpecificationStage() + "/")
            + (service.getSpecificationFile() == null ? "" : service.getSpecificationFile());
    }

    private ResultSet describeService(final FrostlakeParser.ContainerServicesStatementContext ctx) {
        final Schema schema = schemaOf(ctx.qualifiedName());
        final ContainerService service = service(ctx.qualifiedName(), true);
        final List<Row> rows = new ArrayList<>();
        rows.add(serviceRow(schema, service, true));
        return new ResultSet(serviceColumns(true), rows);
    }

    private static ResultSet emptyContainers() {
        return new ResultSet(columns("database_name", "schema_name", "service_name", "service_status", "instance_id",
            "instance_status", "container_name", "status", "message", "image_name", "image_digest", "restart_count",
            "start_time"), new ArrayList<>());
    }

    private static ResultSet emptyInstances() {
        return new ResultSet(columns("database_name", "schema_name", "service_name", "service_status", "instance_id",
            "status", "spec_digest", "creation_time", "start_time", "ip_address"), new ArrayList<>());
    }

    private static ResultSet endpoints(final ContainerService service) {
        final List<Row> rows = new ArrayList<>();
        for (final Map<String, String> endpoint : ServiceSpecReader.entries(service.getSpecification(), "endpoints")) {
            final String port = endpoint.get("port");
            final String protocol = endpoint.get("protocol");
            final boolean isPublic = "true".equalsIgnoreCase(endpoint.get("public"));
            rows.add(new Row(Arrays.<Object>asList(endpoint.get("name"), port, endpoint.get("portRange"),
                protocol == null ? "HTTP" : protocol.toUpperCase(Locale.ROOT), isPublic ? "true" : "false",
                isPublic ? "Endpoints provisioning in progress... check back in a few minutes" : "")));
        }
        return new ResultSet(columns("name", "port", "port_range", "protocol", "is_public", "ingress_url"), rows);
    }

    // ---- artifact repositories ----------------------------------------------------------------------------

    private Object artifactRepository(final FrostlakeParser.ContainerServicesStatementContext ctx, final int verb) {
        final Schema schema = schemaOf(ctx.qualifiedName());
        final String name = last(ctx.qualifiedName());
        final ContainerObjects objects = schema.getContainerObjects();
        final ArtifactRepository existing = objects.getArtifactRepository(name);
        if (verb == FrostlakeParser.CREATE) {
            if (existing != null && ctx.or_replace() == null) {
                if (ctx.if_not_exists() != null) {
                    ConditionalDdlOutcome.createSkipped();
                    return null;
                }
                throw new RuntimeException(SqlCompilationError.of("Object '" + name + "' already exists."));
            }
            String type = null;
            String integration = null;
            String comment = null;
            for (final FrostlakeParser.ContainerPropertyContext property : ctx.containerProperty()) {
                final String key = propertyName(property);
                if ("TYPE".equals(key)) {
                    type = text(property.containerValue()).toUpperCase(Locale.ROOT);
                } else if ("API_INTEGRATION".equals(key)) {
                    integration = text(property.containerValue());
                } else if ("COMMENT".equals(key)) {
                    comment = text(property.containerValue());
                } else {
                    throw invalidProperty(key, "ARTIFACT REPOSITORY");
                }
            }
            if (type == null) {
                throw new RuntimeException(SqlCompilationError.of("Missing option(s): [TYPE]"));
            }
            if (integration == null) {
                throw new RuntimeException("Property 'API_INTEGRATION' must be specified");
            }
            if (!"APPLICATION".equals(type) && !"PYPI".equals(type)) {
                throw new RuntimeException(SqlCompilationError.of("invalid value [" + type + "] for parameter 'TYPE'"));
            }
            final ArtifactRepository repository = new ArtifactRepository(name, type);
            repository.setApiIntegration(integration);
            repository.setComment(comment);
            repository.setOwner(catalog.currentRoleForOwner());
            objects.putArtifactRepository(repository);
            return null;
        }
        if (existing == null) {
            if (ctx.if_exists() != null) {
                if (verb == FrostlakeParser.DROP) {
                    ConditionalDdlOutcome.dropSkipped();
                }
                return null;
            }
            throw new RuntimeException(SqlCompilationError.doesNotExist("Artifact Repository",
                fullName(schema, name)));
        }
        if (verb == FrostlakeParser.DROP) {
            objects.removeArtifactRepository(name);
            return null;
        }
        if (verb == FrostlakeParser.ALTER) {
            final FrostlakeParser.ArtifactRepositoryAlterActionContext action = ctx.artifactRepositoryAlterAction();
            if (action.SET() != null && action.tagSet() == null) {
                for (final FrostlakeParser.ContainerPropertyContext property : action.containerProperty()) {
                    if (!"COMMENT".equals(propertyName(property))) {
                        throw invalidProperty(propertyName(property), "ARTIFACT REPOSITORY");
                    }
                    existing.setComment(text(property.containerValue()));
                }
            } else if (action.UNSET() != null && action.tagUnset() == null) {
                for (final FrostlakeParser.IdentifierContext id : action.identifier()) {
                    if (!"COMMENT".equals(SqlIdentifiers.canonical(id).toUpperCase(Locale.ROOT))) {
                        throw invalidProperty(SqlIdentifiers.canonical(id), "ARTIFACT REPOSITORY");
                    }
                    existing.setComment(null);
                }
            }
            return null;
        }
        final List<Row> rows = new ArrayList<>();
        rows.add(artifactRow(schema, existing));
        return new ResultSet(artifactColumns(), rows);
    }

    private static List<ResultSetColumn> artifactColumns() {
        return columns("created_on", "name", "database_name", "schema_name", "type", "api_integration", "owner",
            "owner_role_type", "comment");
    }

    private Row artifactRow(final Schema schema, final ArtifactRepository repository) {
        return new Row(Arrays.<Object>asList(ShowResultHelpers.createdOn(repository.getCreatedOn()),
            repository.getName(), databaseOf(schema), schema.getName(), repository.getType(),
            repository.getApiIntegration(), repository.getOwner(),
            ShowResultHelpers.ownerRoleType(repository.getOwner()), text(repository.getComment())));
    }

    private ResultSet showArtifactRepositories(final FrostlakeParser.ContainerServicesStatementContext ctx) {
        final List<Row> rows = new ArrayList<>();
        final String like = like(ctx);
        final int max = ctx.LIMIT() != null ? Integer.parseInt(ctx.INTEGER_LITERAL().getText()) : Integer.MAX_VALUE;
        for (final Schema schema : scope(ctx.containerShowScope())) {
            for (final ArtifactRepository repository : schema.getContainerObjects().getArtifactRepositories()) {
                if (rows.size() < max && (like == null || ShowResultHelpers.matchesLike(repository.getName(), like))) {
                    rows.add(artifactRow(schema, repository));
                }
            }
        }
        return new ResultSet(artifactColumns(), rows);
    }

    // ---- listings -----------------------------------------------------------------------------------------

    private static String text(final String value) {
        return value == null ? "" : value;
    }

    private static String like(final FrostlakeParser.ContainerServicesStatementContext ctx) {
        return ctx.LIKE() == null ? null : SqlStringLiterals.decode(ctx.STRING_LITERAL().getText());
    }

    private List<Schema> scope(final FrostlakeParser.ContainerShowScopeContext scope) {
        return scope(scope, false);
    }

    /** The schemas a listing's IN clause covers; the current database's, or the account's without a clause. */
    private List<Schema> scope(final FrostlakeParser.ContainerShowScopeContext scope, final boolean account) {
        final List<Schema> schemas = new ArrayList<>();
        if (!account && scope != null && (scope.SCHEMA() != null || scope.qualifiedName() != null
                && scope.DATABASE() == null)) {
            if (scope.qualifiedName() == null) {
                schemas.add(catalog.requireOwningSchema(QualifiedName.of("X")));
                return schemas;
            }
            final String[] written = parts(scope.qualifiedName());
            final String database = written.length >= 2 ? written[written.length - 2] : catalog.getCurrentDatabase();
            schemas.add(catalog.databaseExact(database).schemaExact(written[written.length - 1]));
            return schemas;
        }
        String database = null;
        if (!account && scope != null && scope.DATABASE() != null) {
            database = scope.identifier() != null ? SqlIdentifiers.canonical(scope.identifier())
                : catalog.getCurrentDatabase();
        } else if (!account && scope == null) {
            database = catalog.getCurrentDatabase();
        }
        for (final Database candidate : catalog.getAllDatabases()) {
            if (database == null || database.equals(candidate.getName())) {
                schemas.addAll(candidate.getAllSchemas());
            }
        }
        return schemas;
    }

    private static List<ResultSetColumn> columns(final String... names) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        for (final String name : names) {
            columns.add(new ResultSetColumn(name, "created_on".equals(name) ? ShowResultHelpers.CREATED_ON
                : StringType.VARCHAR));
        }
        return columns;
    }
}
