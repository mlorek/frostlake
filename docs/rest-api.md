# Snowflake REST APIs (`/api/v2`)

`DatabaseHttpServer` serves Snowflake's
[REST APIs for resource management](https://docs.snowflake.com/en/developer-guide/snowflake-rest-api/snowflake-rest-api)
under `/api/v2`, beside the engine's own wire protocol ([http-api.md](http-api.md)). The endpoints, their
parameters and their bodies follow the published OpenAPI specifications
([snowflakedb/snowflake-rest-api-specs](https://github.com/snowflakedb/snowflake-rest-api-specs)), and the
Snowflake Python API (`snowflake.core`) can drive them.

Every resource endpoint is a translation into the SQL the engine already runs — list is a `SHOW`, create a `CREATE`,
delete a `DROP`, an action the matching `ALTER`, `UNDROP`, `EXECUTE` or `CALL` — so an endpoint behaves as its
statement does, refusals included.

## Conventions

### Paths

Account-level collections live at `/api/v2/<resources>` (`/api/v2/warehouses`), schema-level ones at
`/api/v2/databases/{database}/schemas/{schema}/<resources>`. An item is `…/<resources>/{name}`; an action is a
`:verb` suffix on it (`/api/v2/warehouses/{name}:resume`).

A name in a path, or a `name` in a body, is an identifier: unquoted names fold to upper case as SQL folds them,
double-quoted ones are kept exactly (`"my db"`, with `""` for a quote inside). Percent-encode what a URL cannot
carry: `/api/v2/warehouses/%22my%20wh%22`. A quoted name may hold a colon; the action is split off at the last
colon outside quotes. The grants endpoints' `{granteeName}`, `{securableName}` and `{scopeName}` are dotted names
(`db.schema.t`, `db.role`), each part an identifier. A response spells an object's name (and the names in
`database_name`, `schema_name`, `securable` and tag references) bare when an unquoted identifier resolves to it and
quoted otherwise, so a name read from a listing addresses the same object when sent back; owner and grantee names
and the grant listings' names are answered as SHOW lists them.

### Requests

- **Media type.** A body is JSON, except where a Spark Connect endpoint takes `application/octet-stream`. A request
  whose `Content-Type` names another media type than the endpoint's is refused `415` with
  no body and no request id, and nothing runs; a request with no `Content-Type` is read as JSON. A property the
  schema does not define is ignored.
- **Authentication.** `Authorization` and `X-Snowflake-Authorization-Token-Type` are accepted and **not
  checked** — the server is unauthenticated by design ([scope.md](scope.md)), so no request is ever answered
  `401`.
- **Sessions.** The account keeps one session per token, so a call carrying an `Authorization` header runs in the
  session kept for that header's value: what one call changes in it — the warehouse `:use` selects, the one a
  create makes current — carries over to the caller's next call. `X-Sfc-Session: <session id>` runs a call in a
  session the server holds (one started through `/api/sessions` or `/api/execute`) instead. A call with neither
  runs in a session of its own that ends with the call.
- **`createMode`** on create endpoints: `errorIfExists` (default, plain `CREATE`), `orReplace`
  (`CREATE OR REPLACE`), `ifNotExists` (`CREATE … IF NOT EXISTS`); any other value is `400`
  `Not a valid createMode: <v>. Allowed values are: errorIfExists,orReplace,ifNotExists`.
- **`ifExists=true`** on delete and action endpoints: `DROP … IF EXISTS` / `ALTER … IF EXISTS`, so a missing
  object is `200` rather than `404`. A boolean parameter given a value is true only when it says `true` (in any case), and
  any other value reads as false; an absent or empty one takes its default.
- **Listing parameters** translate to the SHOW modifiers: `like` → `LIKE`, `startsWith` → `STARTS WITH`,
  `showLimit` and `fromName` → `LIMIT … FROM`. A listing that parses a modifier but ignores it in SQL ignores it
  here too. Results are not paged: a listing answers every row in one body, with no `Link` header.
- **`PUT` is create-or-alter.** The body is the whole property set: an absent object is created, and an existing
  one takes every property the body names while the ones it leaves out go back to their defaults (a resource's
  section names what its `PUT` leaves alone). A body whose `name` names another object than the path is `409` code
  `001520`, `Retryable race condition in create or alter`, for a warehouse, table, compute pool, service, API
  integration or artifact repository; for a tag, user, database, schema or task it is `400`
  (`The body names <kind> '<a>' but the path names '<b>'.`).

### Responses

| status | when |
|---|---|
| `200` | success: a listing (array) or an object, or `{"status": "…"}` for a create, delete or action, carrying the sentence the statement answered |
| `202` | the operation is still running — see *Asynchronous execution* |
| `400` | a statement the engine refused for a reason not below; a missing required `name` (code `390400`); an invalid identifier (`Invalid SQL Identifier found in the request: <text>`). A request that cannot be read at all — malformed JSON, a body or property of the wrong type (other than a grant's `securable` or `containing_scope`, which answer the missing-property `400`), a query parameter that is not the integer it should be — is a `400` with an **empty** body |
| `403` | the statement was refused for insufficient privileges |
| `404` | the object does not exist; or the path matches no endpoint, or uses a method the API never uses (`PATCH`) — then bare: no body, no request id, as the account's edge answers |
| `405` | the path is an endpoint under another of the API's methods (a `PUT` on a view, a `POST` on a warehouse) — bare |
| `409` | the object already exists |
| `415` | the `Content-Type` is not the endpoint's media type — bare |
| `501` | the endpoint exists in the API but Frostlake does not provide it |

An error carries the `ErrorResponse` body, `{"code", "error_code", "message", "request_id"}`, with `code` and
`error_code` (its deprecated twin) present on every error — empty strings when the failure has no Snowflake error
number. A refused statement's message is relayed as the account relays it: the statement's message without its
`SQL compilation error:` prefix (the line break after it stays) and in **lower case** —
`"\nobject 'w1' already exists."`, or for a fetch of a missing warehouse
`"\nwarehouse 'w9' does not exist or not authorized."` (a refused statement's message keeps the privilege hint that
follows that sentence). The code is the
statement's error number where the refusal's family has one: `002003` does not exist, `002002` already exists,
`001003` syntax error, `003001` insufficient privileges, and a few others the resources name. Every routed answer
carries `X-Snowflake-Request-ID`, and an error's `request_id` repeats it.

Response bodies carry every property of the specification's schema, `null` where the engine has no value, as the
account's do (a managed account's `admin_name` and `admin_password` are not answered).

### Asynchronous execution

An operation that has not finished after `http.rest.syncWaitMs` (default 30000 ms), or any request made with
`asyncExec=true`, is answered `202 Accepted` with `Location: /api/v2/results/<handle>` and the body
`{"code": "392604", "message": "Request execution in progress. Use provided Location header or result handler id
to perform query monitoring and management.", "result_handler": "<handle>", "job_id": "<job id>"}` — the handle is
the job id followed by its result-type digit, and the account spells the key `result_handler` where the
specification says `resultHandler`. `GET` on that location answers `202` again while the operation runs and then
its own response. A handle is forgotten `http.rest.resultRetentionMs` (default one hour) after it was issued; a
well-formed handle that names nothing is `404` code `390404`, and a malformed one `400`
`Invalid resultHandler: <handle>`.

## Resources

Each section lists the endpoints by the specification's `operationId`, what each runs, and what is not provided.

### Warehouses (`warehouse.yaml`)

| endpoint | runs |
|---|---|
| `listWarehouses` `GET /warehouses` | `SHOW WAREHOUSES [LIKE]` + `SHOW PARAMETERS IN WAREHOUSE` per warehouse |
| `createWarehouse` `POST /warehouses` | `CREATE WAREHOUSE` with the body's properties |
| `fetchWarehouse` `GET /warehouses/{name}` | `SHOW WAREHOUSES LIKE` + `SHOW PARAMETERS IN WAREHOUSE` |
| `createOrAlterWarehouse` `PUT /warehouses/{name}` | `CREATE WAREHOUSE`, or `ALTER WAREHOUSE … SET` / `UNSET` |
| `deleteWarehouse` `DELETE /warehouses/{name}` | `DROP WAREHOUSE [IF EXISTS]` |
| `resumeWarehouse`, `suspendWarehouse`, `abortAllQueriesOnWarehouse` | `ALTER WAREHOUSE … RESUME IF SUSPENDED` / `SUSPEND` / `ABORT ALL QUERIES` |
| `renameWarehouse` `POST /warehouses/{name}:rename` | `ALTER WAREHOUSE … RENAME TO` the body's `name` |
| `enableWarehouse`, `disableWarehouse` | `ALTER WAREHOUSE … ENABLE` / `DISABLE` |
| `useWarehouse` `POST /warehouses/{name}:use` | `USE WAREHOUSE` in the call's session (see `X-Sfc-Session`) |
| `setTags`, `unsetTags`, `getTags` | see *Tags* |

- **`resource_constraint`** takes the memory constraints (`MEMORY_1X` … `MEMORY_64X_x86`) of a Snowpark-optimized
  warehouse. A standard warehouse's constraint is its generation: `STANDARD_GEN_1 | STANDARD_GEN_2` is refused
  (`400`, code `000682`, `cannot set resource constraint to '…'. use the generation property …`), and so is unsetting
  it. A `PUT` therefore never sets or unsets `RESOURCE_CONSTRAINT` on a standard warehouse — its constraint follows
  `generation`, which a body naming neither property unsets back to 2 — and skips a constraint the warehouse already
  has, so a body read back from a fetch
  can be sent again. A fetch reports the constraint in force: the memory constraint that was set, else `MEMORY_16X`
  for a Snowpark-optimized warehouse, else `STANDARD_GEN_<generation>`.
- **`wait_for_completion`** is sent only in the `ALTER WAREHOUSE … SET` of a `PUT` on an existing warehouse whose
  body also names `warehouse_size`: `CREATE WAREHOUSE` does not take it, and `ALTER` takes it only beside a size.
  It is not stored: a resize here completes at once.
- **`:enable` / `:disable`** apply to an adaptive warehouse (`warehouse_type` `ADAPTIVE`) only. On any other
  warehouse they are refused (`400`).

### Tags (`tag.yaml`, and `:set-tags` / `:unset-tags` / `:get-tags` on taggable resources)

Path prefix `/api/v2/databases/{database}/schemas/{schema}/tags`.

| endpoint | runs |
|---|---|
| `listTags` `GET …/tags` | `SHOW TAGS [LIKE] IN SCHEMA` |
| `createTag` `POST …/tags` | `CREATE [OR REPLACE] TAG [IF NOT EXISTS] … [ALLOWED_VALUES …] [PROPAGATE = … [ON_CONFLICT = …]] [COMMENT = …]` |
| `fetchTag` `GET …/tags/{name}` | `SHOW TAGS LIKE … IN SCHEMA`, the exact name; `404` when absent |
| `createOrAlterTag` `PUT …/tags/{name}` | `CREATE OR ALTER TAG … [ALLOWED_VALUES …] [COMMENT = …]`, then `ALTER TAG … SET PROPAGATE = …` for a body that names a propagation, else `ALTER TAG … UNSET PROPAGATE` |
| `deleteTag` `DELETE …/tags/{name}` | `DROP TAG [IF EXISTS]` |
| `undropTag` `POST …/tags/{name}:undrop` | `UNDROP TAG` |
| `renameTag` `POST …/tags/{name}:rename` | `ALTER TAG [IF EXISTS] … RENAME TO <targetDatabase>.<targetSchema>.<targetName>` (database and schema default to the tag's; `targetName` is required) |

A fetched tag carries `allowed_values` (the array SHOW TAGS lists), `propagate` (`NONE` for a tag that does not
propagate), `on_conflict` when it is set, `comment` (null when the tag has none; an explicit empty comment reads back as
null too, where the account answers `""`), `created_on`, `database_name`, `schema_name`, `owner`, `owner_role_type` and
`multi_value`. A conflict rule the tag would not take is refused `400` with code `391892` (`invalid on_conflict
strategy: …`). `propagate` must be a keyword (`ON_DEPENDENCY_AND_DATA_MOVEMENT`, `ON_DEPENDENCY`, `ON_DATA_MOVEMENT`);
`on_conflict` is written as the keyword `ALLOWED_VALUES_SEQUENCE` or as a string, and needs `propagate`. Frostlake
records the propagation properties but propagates no tag.

The three tag endpoints of every taggable resource are served by one helper:

| endpoint | runs |
|---|---|
| `setTags` `POST …/{name}:set-tags` | `ALTER <kind> [IF EXISTS] … SET TAG t1 = 'v1', …` from the `TagAssignment` array |
| `unsetTags` `POST …/{name}:unset-tags` | `ALTER <kind> [IF EXISTS] … UNSET TAG t1, …` from the `TagReference` array |
| `getTags` `GET …/{name}:get-tags` | `<database>.INFORMATION_SCHEMA.TAG_REFERENCES(<qualified name>, <domain>)` |

`getTags` answers the assignments made on the object itself; `withLineage=true` adds the ones it inherits from its
table, schema and database (`TAG_REFERENCES` rows with `APPLY_METHOD` `INHERITED`), each with the `level` the tag is
set on. A tag named without a database and schema resolves in the call's session, whose current database and schema are
the server's defaults (`database.default` / `schema.default`, SNOWFLAKE.PUBLIC unless configured): name them.

### Accounts and managed accounts (`account.yaml`, `managed-account.yaml`)

A Frostlake engine is one account. The other accounts of the organization and the reader accounts this account
manages are records: `CREATE ACCOUNT` keeps the name, edition, region, region group, comment and administrator name
and email (never the password or key), and assigns a locator; `SHOW ACCOUNTS` lists this account first and then the
accounts `CREATE ACCOUNT` recorded (the reader accounts are listed by `SHOW MANAGED ACCOUNTS`). `SHOW ACCOUNTS
HISTORY` lists every account, dropped or not, with `dropped_on`, `scheduled_deletion_time` and `restored_on`;
`SHOW ACCOUNTS` leaves a dropped account out until `UNDROP ACCOUNT` returns it.

| endpoint | runs |
|---|---|
| `listAccounts` `GET /accounts` | `SHOW ACCOUNTS [HISTORY] [LIKE]`; `showLimit` cuts the answer. Every property of the schema is answered: what the listing does not carry as the account answers it for an account read back — the administrator's properties, `email`, `first_name`, `last_name` and `retention_time` null, `must_change_password` and `polaris` false, `region_group` `PUBLIC` — and an empty old URL as `""` |
| `createAccount` `POST /accounts` | `CREATE ACCOUNT` with the body's properties (`admin_name`, `admin_password` or `admin_rsa_public_key`, `admin_user_type`, `first_name`, `last_name`, `email`, `must_change_password`, `edition`, `region_group`, `region`, `comment`, `polaris`) |
| `deleteAccount` `DELETE /accounts/{name}` | `DROP ACCOUNT [IF EXISTS] … GRACE_PERIOD_IN_DAYS = n`; `gracePeriodInDays` is required (3 to 90) |
| `UndropAccount` `POST /accounts/{name}:undrop` | `UNDROP ACCOUNT` |
| `listManagedAccounts` `GET /managed-accounts` | `SHOW MANAGED ACCOUNTS [LIKE]`; `account_type` is always `READER` |
| `createManagedAccount` `POST /managed-accounts` | `CREATE MANAGED ACCOUNT … ADMIN_NAME = …, ADMIN_PASSWORD = …, TYPE = READER [, COMMENT = …]`; the status is the account's JSON (`accountName`, `accountLocator`, `url`, `accountLocatorUrl`) |
| `deleteManagedAccount` `DELETE /managed-accounts/{name}` | `DROP MANAGED ACCOUNT` |

### Users, roles, database roles and grants (`user.yaml`, `role.yaml`, `database-role.yaml`, `grant.yaml`)

A `Grant` body names a securable (`securable_type` and `securable`, whose `database`, `schema` and `name` give its
qualified name), or all objects of a kind in a scope (`containing_scope` and no `securable`); `privileges` are SQL
privilege words and `grant_option: true` adds `WITH GRANT OPTION` to a grant and makes a revoke
`REVOKE GRANT OPTION FOR`. A `securable_type` of `role` or `database role` grants or revokes that role; on
`/roles/{name}/grants` such a grant names privileges too, or it is refused as the account refuses it (`400`, code
`001003`, `\nsyntax error line 1 at position 12 unexpected 'on'.`). Listed grants carry one privilege each, with
`grant_option` and `granted_by`, and every property of the schema: `containing_scope` null, and a `securable` of
`database`, `name`, `schema` and `service`, null where the name has no such part. A database role's own USAGE on its
database is listed first, granted by no one (`granted_by` `""`), and beside a USAGE granted to it explicitly rather than
in its place. A listed future grant answers its scope in the
`securable` — `{"database": "D", "schema": "S", "name": "\"<TABLE>\""}`, the kind's placeholder quoted — with
`containing_scope` and `granted_by` null. A statement that reaches objects by the count answers
`Statement executed successfully. N objects affected.`: a grant over all objects of a kind counts the objects in its
scope, a revoke the objects it took a privilege or a grant option back from.

| endpoint | runs |
|---|---|
| `listUsers` `GET /users` | `SHOW USERS [LIKE] [STARTS WITH] [LIMIT … FROM]` |
| `createUser` `POST /users` | `CREATE USER` with the body's properties |
| `fetchUser` `GET /users/{name}` | `SHOW USERS LIKE` + `DESCRIBE USER` |
| `createOrAlterUser` `PUT /users/{name}` | `CREATE USER`, or `ALTER USER … SET` / `UNSET` (the SQL has no `CREATE OR ALTER USER`; an omitted password or `default_secondary_roles` is kept) |
| `deleteUser` `DELETE /users/{name}` | `DROP USER [IF EXISTS]` |
| `listGrants` `GET /users/{name}/grants` | `SHOW GRANTS TO USER` |
| `grant`, `revokeGrants` `POST /users/{name}/grants[:revoke]` | `GRANT ROLE` / `GRANT DATABASE ROLE … TO USER`, `REVOKE ROLE` / `REVOKE DATABASE ROLE … FROM USER` |
| `listRoles` `GET /roles` | `SHOW ROLES [LIKE] [STARTS WITH] [LIMIT … FROM]` |
| `createRole`, `deleteRole` | `CREATE ROLE [COMMENT]`, `DROP ROLE [IF EXISTS]` |
| `listGrants` `GET /roles/{name}/grants` | `SHOW GRANTS TO ROLE` |
| `grantPrivileges`, `revokeGrants` `POST /roles/{name}/grants[:revoke]` | `GRANT … ON <kind> <name> \| ON ACCOUNT \| ON ALL <kinds> IN <scope> TO ROLE [WITH GRANT OPTION]`, `REVOKE [GRANT OPTION FOR] … FROM ROLE [RESTRICT \| CASCADE]` (`mode`) |
| `listGrantsOf` `GET /roles/{name}/grants-of` | `SHOW GRANTS OF ROLE` |
| `listGrantsOn` `GET /roles/{name}/grants-on` | `SHOW GRANTS ON ROLE` |
| `listFutureGrants` `GET /roles/{name}/future-grants` | `SHOW FUTURE GRANTS TO ROLE` |
| `grantFuturePrivileges`, `revokeFutureGrants` | `GRANT … ON FUTURE <kinds> IN <scope> TO ROLE`, `REVOKE … ON FUTURE … FROM ROLE` |
| `listDatabaseRoles` `GET /databases/{database}/database-roles` | `SHOW DATABASE ROLES IN DATABASE [LIMIT … FROM]`; the page starts after the `fromName` |
| `createDatabaseRole`, `deleteDatabaseRole` | `CREATE DATABASE ROLE [COMMENT]`, `DROP DATABASE ROLE [IF EXISTS]` |
| `cloneDatabaseRole` `POST …/database-roles/{name}:clone` | the SQL has no clone of a database role: `CREATE DATABASE ROLE` (per `createMode`, in `targetDatabase` when given) with the source's comment, then a `GRANT` of each privilege, database role and future grant the source holds |
| `listGrants`, `grantPrivileges`, `revokeGrants`, `listFutureGrants`, `grantFuturePrivileges`, `revokeFutureGrants` on `/databases/{database}/database-roles/{name}` | as for roles, with `DATABASE ROLE <database>.<name>` as the grantee |
| `grantPrivilege` `POST /grants/{granteeType}/{granteeName}/{securableType}/{securableName}/privileges` | `GRANT … ON <kind> <name> \| ON ACCOUNT TO <grantee> [WITH GRANT OPTION]`; a role or database role securable with no privileges grants that role |
| `grantGroupPrivilege` `POST /grants/…/{bulkGrantType}/{securableTypePlural}/{scopeType}/{scopeName}/privileges` | `GRANT … ON ALL \| FUTURE <kinds> IN DATABASE \| SCHEMA <scope> TO <grantee>` |
| `revokePrivilege`, `revokeGroupPrivilege` `DELETE …/privileges/{privilege}` | `REVOKE … FROM <grantee> [RESTRICT \| CASCADE]` (`deleteMode`; any other value is no mode, as it is for `mode`) |
| `revokePrivilegeGrantOption`, `revokeGroupPrivilegeGrantOption` `DELETE …/privileges/{privilege}/grant-option` | `REVOKE GRANT OPTION FOR …` |
| `listGrantsTo` `GET /grants/{granteeType}/{granteeName}` | `SHOW GRANTS TO ROLE \| USER \| DATABASE ROLE`; `grantee_type` and `securable_type` are the listing's own words (`ROLE`, `DATABASE_ROLE`, `USER`), `securable_name` its name, `granted_by_role_type` `""` |
| `setTags`, `unsetTags`, `getTags` on users, roles and database roles | see *Tags* (`ALTER USER \| ROLE \| DATABASE ROLE … SET TAG` / `UNSET TAG`; `TAG_REFERENCES` domains `USER`, `ROLE`, `DATABASE ROLE`) |

A fetched user answers `password` as `********` when it has one, and `enable_unredacted_query_syntax_error` as
`false`; `password_last_set` is not recorded and answers null.

Not provided: grantees other than roles, users and database roles (`share`, `application`, `application-role`
answer `501`); `network_policy` is neither applied nor read back (null even when `ALTER USER … SET NETWORK_POLICY`
attached one), and `enable_unredacted_query_syntax_error`, which no USER statement takes, is not applied. `showLimit` on the grants listings cuts the answer.
`days_to_expiry`, `mins_to_unlock` and `mins_to_bypass_mfa` are applied as `DAYS_TO_EXPIRY`, `MINS_TO_UNLOCK` and
`MINS_TO_BYPASS_MFA` and read back as the whole days and minutes left; `rsa_public_key` and `rsa_public_key_2` are
applied as the keys and read back as `DESCRIBE USER` prints them, with their `_fp` fingerprints.

### Databases and schemas (`database.yaml`, `schema.yaml`)

| endpoint | runs |
|---|---|
| `listDatabases` `GET /databases` | `SHOW DATABASES [HISTORY] [LIKE] [STARTS WITH] [LIMIT … FROM]` |
| `createDatabase` `POST /databases` | `CREATE [OR REPLACE] [TRANSIENT] DATABASE [IF NOT EXISTS]` with the body's properties |
| `cloneDatabase` `POST /databases/{name}:clone` | `CREATE … DATABASE <body name> CLONE {name} [AT \| BEFORE (…)]` with the body's properties |
| `fetchDatabase` `GET /databases/{name}` | `SHOW DATABASES LIKE` + `SHOW PARAMETERS IN DATABASE` |
| `createOrAlterDatabase` `PUT /databases/{name}` | `CREATE DATABASE`, or one `ALTER DATABASE … SET` / `UNSET` per property |
| `deleteDatabase` `DELETE /databases/{name}` | `DROP DATABASE [IF EXISTS] … [RESTRICT \| CASCADE]` |
| `undropDatabase` `POST /databases/{name}:undrop` | `UNDROP DATABASE` |
| `listSchemas` `GET /databases/{database}/schemas` | `SHOW SCHEMAS [HISTORY] [LIKE] IN DATABASE … [STARTS WITH] [LIMIT … FROM]` |
| `createSchema` `POST /databases/{database}/schemas` | `CREATE [OR REPLACE] [TRANSIENT] SCHEMA [IF NOT EXISTS] … [WITH MANAGED ACCESS]` with the body's properties |
| `cloneSchema` `POST …/schemas/{name}:clone` | `CREATE … SCHEMA <targetDatabase or database>.<body name> CLONE … [AT \| BEFORE (…)]` |
| `fetchSchema` `GET …/schemas/{name}` | `SHOW SCHEMAS LIKE … IN DATABASE` + `SHOW PARAMETERS IN SCHEMA` |
| `createOrAlterSchema` `PUT …/schemas/{name}` | `CREATE SCHEMA`, or one `ALTER SCHEMA … SET` / `UNSET` per property and `ENABLE \| DISABLE MANAGED ACCESS` |
| `deleteSchema` `DELETE …/schemas/{name}` | `DROP SCHEMA [IF EXISTS] … [RESTRICT \| CASCADE]` |
| `undropSchema` `POST …/schemas/{name}:undrop` | `UNDROP SCHEMA` |
| `setTags`, `unsetTags`, `getTags` | see *Tags* |

- **`kind`**: the query parameter `kind=transient` (the lower-case word only, as the account reads it; any other
  spelling is ignored) or the body's `kind` `TRANSIENT` creates a transient database or schema. A response reports `kind` from the listing's `options` cell.
  A `PUT` that names another kind than the existing object's is `400`, because the kind cannot be altered.
- **Properties**: `comment` and the container parameters are written into the `CREATE` statement, except
  `log_level`, `trace_level` and a schema's `pipe_execution_paused`, which a body cannot set (the account ignores
  them in a request; a fetch still reports them). The container
  parameters are `data_retention_time_in_days`, `max_data_extension_time_in_days`, `default_ddl_collation`,
  `log_level`, `trace_level`, `suspend_task_after_num_failures`, `user_task_managed_initial_warehouse_size`,
  `user_task_timeout_ms`, `serverless_task_min_statement_size`, `serverless_task_max_statement_size`,
  `external_volume`, `catalog`, `iceberg_version_default`, `iceberg_merge_on_read_behavior`,
  `storage_serialization_policy`, `catalog_sync`, `enable_data_compaction` and `replace_invalid_characters`.
  A schema also takes `managed_access`, `pipe_execution_paused` and `classification_profile`, and a database
  `catalog_sync_namespace_mode` and `catalog_sync_namespace_flatten_delimiter` on create only (a `PUT` on an existing
  database cannot set them); these three are not read back (always `null`). A fetch reads the
  parameters back from `SHOW PARAMETERS IN DATABASE | SCHEMA`, which reports each parameter with the value in
  force: the object's own value, the value a schema inherits from its database, or the account default. An empty
  parameter is `""`, and the three warehouse sizes read back upper-cased (`X2LARGE`, `MEDIUM`). A listing carries the
  same properties; a dropped database or schema in a `history` listing reports the defaults (a database also its
  retention). `object_visibility` is not supported and is always `null`.
- **`:clone`**: `point_of_time` `{"point_of_time_type": "timestamp" | "offset" | "statement", "reference":
  "at" | "before", …}` becomes `AT | BEFORE (TIMESTAMP => '…'::TIMESTAMP_LTZ | OFFSET => n | STATEMENT => '…')`.
  An offset must be a number. A schema clone goes to `targetDatabase` when the call names one.
- **`history=true`** lists the dropped databases or schemas that `UNDROP` can still restore. Their `dropped_on`
  is set; a live object's `dropped_on` is `null`. `:undrop` answers `<Kind> <NAME> successfully restored.`, and an
  object that was never dropped is `400`, code `090025` (`… did not exist or was purged.`).
- **`restrict`**: `true` appends `RESTRICT` and `false` appends `CASCADE`. When `restrict` is absent, the `DROP`
  statement's default applies.
- **`PUT`** creates an absent database or schema. For an existing one, it sets each property the body names and
  unsets each parameter (other than `log_level`, `trace_level` and `pipe_execution_paused`, which a `PUT` leaves
  alone) that the object sets itself but the body leaves out. The comment is handled the same way.
  For a schema, `managed_access` switches managed access on or off.
- **Not provided (`501`)**: `createDatabaseFromShare` (`:from-share`) and `createDatabaseFromShareDeprecated`
  (`{name}:from_share`), because Frostlake has no shares. `enableDatabaseReplication`,
  `disableDatabaseReplication`, `refreshDatabaseReplication`, `enableDatabaseFailover`,
  `disableDatabaseFailover` and `primaryDatabaseFailover` also answer `501`, because Frostlake has no
  replication model.

### Tables, views, dynamic tables, event tables, Iceberg tables and sequences

Paths below are relative to `/api/v2/databases/{database}/schemas/{schema}`. A body's `name`, a `targetName`
and the `targetDatabase` / `targetSchema` query parameters are identifiers; `targetTableName`
and a table's foreign-key `referenced_table_name` may be qualified (`schema.name`, `database.schema.name`), the path
supplying the parts they leave out; `newTableName` and an Iceberg table's `referenced_table_name` are single
identifiers. `copyGrants=true` adds `COPY GRANTS` on tables, views and a dynamic table's clone; the event-table and
Iceberg-table endpoints ignore it.

**Tables (`table.yaml`).** A table is read from `SHOW TABLES` (`kind` TABLE reads as `PERMANENT`, `cluster_by`
lists the keys of the `cluster_by` cell, `table_type` comes from the `is_*` flags), its `columns` from
`DESCRIBE TABLE` (a `COLLATE` in the type becomes `collate`, an `IDENTITY START s INCREMENT i` default becomes
the `autoincrement` properties) and its `constraints` from `SHOW PRIMARY | UNIQUE | IMPORTED KEYS`, one entry per
constraint name. A create renders each column as `name type [NOT NULL] [COLLATE] [DEFAULT | AUTOINCREMENT …]
[COMMENT]`; a column's own `constraints` and the table's are written out of line.

| endpoint | runs |
|---|---|
| `listTables` `GET /tables` | `SHOW TABLES [HISTORY] [LIKE] IN SCHEMA … [STARTS WITH] [LIMIT … FROM]`; `deep=true` adds each table's `columns` and `constraints` |
| `createTable` `POST /tables` | `CREATE [OR REPLACE] [TRANSIENT \| TEMPORARY] TABLE [IF NOT EXISTS] … (columns, constraints)` with `CLUSTER BY`, `ENABLE_SCHEMA_EVOLUTION`, `DATA_RETENTION_TIME_IN_DAYS`, `MAX_DATA_EXTENSION_TIME_IN_DAYS`, `CHANGE_TRACKING`, `DEFAULT_DDL_COLLATION`, `ERROR_LOGGING`, `ROW_TIMESTAMP`, `COPY GRANTS`, `COMMENT` |
| `createTableAsSelect` `POST /tables:as-select`, `createTableAsSelectDeprecated` `POST /tables/{name}:as_select` | the same with `AS <query>` (the `query` parameter) |
| `createTableUsingTemplate` `POST /tables:using-template`, `createTableUsingTemplateDeprecated` `POST /tables/{name}:using_template` | `CREATE TABLE … USING TEMPLATE <query>` |
| `fetchTable` `GET /tables/{name}` | `SHOW TABLES LIKE` + `DESCRIBE TABLE` + `SHOW PRIMARY / UNIQUE / IMPORTED KEYS IN TABLE` |
| `createOrAlterTable` `PUT /tables/{name}` | `CREATE OR ALTER TABLE` with the body's columns, constraints and properties |
| `deleteTable` `DELETE /tables/{name}` | `DROP TABLE [IF EXISTS]` |
| `cloneTable` `POST /tables/{name}:clone` | `CREATE TABLE <target> CLONE … [AT \| BEFORE (TIMESTAMP \| OFFSET \| STATEMENT => …)]` from `point_of_time` |
| `createTableLike` `POST /tables/{name}:create-like`, `createTableLikeDeprecated` `…:create_like?newTableName=` | `CREATE TABLE <new> LIKE …` |
| `undropTable` `POST /tables/{name}:undrop` | `UNDROP TABLE` |
| `suspendReclusterTable`, `resumeReclusterTable` (and the `…_recluster` spellings) | `ALTER TABLE [IF EXISTS] … SUSPEND RECLUSTER` / `RESUME RECLUSTER` |
| `swapWithTable` `…:swap-with?targetName=`, `swapWithTableDeprecated` `…:swapwith?targetTableName=` | `ALTER TABLE [IF EXISTS] … SWAP WITH` |
| `setTags`, `unsetTags`, `getTags` | `ALTER TABLE … SET TAG / UNSET TAG`; `TAG_REFERENCES(…, 'TABLE')` |

A `:using-template` query must read a table function and answer the `ARRAY_AGG(OBJECT_CONSTRUCT(*))` column
descriptions, as the `INFER_SCHEMA` form over staged files does; the account also requires that table function to
be `INFER_SCHEMA` (`Unsupported feature 'INFER_SCHEMA function must be used in the TEMPLATE sub-query'.`), where
Frostlake still takes the descriptions from any table function.
`copyGrants` needs an object to copy from (`orReplace`, a clone or a like), else the account's `Invalid operation COPY GRANTS without specifying source object.`;
`data_metric_schedule` is not applied on create; `rows`, `bytes` and `dropped_on` report what `SHOW TABLES` reports.

**Views (`view.yaml`).** A view is read from `SHOW VIEWS` (materialized views left out): its `query` is the
CREATE text the listing reports and `select_query` that text's defining query; its `columns` come from
`DESCRIBE VIEW` (in a listing only with `deep=true`). The specification has no `PUT` for views.

| endpoint | runs |
|---|---|
| `listViews` `GET /views` | `SHOW VIEWS [LIKE] IN SCHEMA … [STARTS WITH] [LIMIT … FROM]` |
| `createView` `POST /views` | `CREATE [OR REPLACE] [SECURE] [TEMPORARY] VIEW [IF NOT EXISTS] … [(col [COMMENT], …)] [COPY GRANTS] [COMMENT] AS <query>` |
| `fetchView` `GET /views/{name}` | `SHOW VIEWS LIKE` + `DESCRIBE VIEW` |
| `deleteView` `DELETE /views/{name}` | `DROP VIEW [IF EXISTS]` |
| `setTags`, `unsetTags`, `getTags` | `ALTER VIEW … SET TAG / UNSET TAG`; `TAG_REFERENCES(…, 'TABLE')` |

Not provided: `recursive: true` answers `501`, although the engine takes `CREATE RECURSIVE VIEW`; a fetched view
always reads `recursive: false`.

**Dynamic tables (`dynamic-table.yaml`).** A dynamic table is read from `SHOW DYNAMIC TABLES`: `target_lag` is
`DOWNSTREAM` or the listed lag in seconds, `scheduling_state` ACTIVE reads as `RUNNING`, `kind` is `TRANSIENT`
when the CREATE text says so, and `query` is the defining query of that text; its `columns` come from
`DESCRIBE DYNAMIC TABLE`.

| endpoint | runs |
|---|---|
| `listDynamicTables` `GET /dynamic-tables` | `SHOW DYNAMIC TABLES [LIKE] IN SCHEMA … [STARTS WITH] [LIMIT … FROM]`; `deep=true` adds `columns` |
| `createDynamicTable` `POST /dynamic-tables` | `CREATE [OR REPLACE] [TRANSIENT] DYNAMIC TABLE [IF NOT EXISTS] … [(names)] TARGET_LAG = '<n> seconds' \| DOWNSTREAM WAREHOUSE = … [REFRESH_MODE] [INITIALIZE] [CLUSTER BY] [DATA_RETENTION_TIME_IN_DAYS] [COMMENT] AS <query>`, then `ALTER … SET MAX_DATA_EXTENSION_TIME_IN_DAYS` when the body sets it |
| `fetchDynamicTable` `GET /dynamic-tables/{name}` | `SHOW DYNAMIC TABLES LIKE` + `DESCRIBE DYNAMIC TABLE` |
| `deleteDynamicTable` `DELETE /dynamic-tables/{name}` | `DROP DYNAMIC TABLE [IF EXISTS]` |
| `cloneDynamicTable` `POST /dynamic-tables/{name}:clone` | `CREATE DYNAMIC TABLE <target> CLONE … [AT \| BEFORE (…)] [COPY GRANTS] [TARGET_LAG] [WAREHOUSE]` |
| `undropDynamicTable` `POST /dynamic-tables/{name}:undrop` | `UNDROP DYNAMIC TABLE` |
| `suspendDynamicTable`, `resumeDynamicTable`, `refreshDynamicTable` | `ALTER DYNAMIC TABLE [IF EXISTS] … SUSPEND` / `RESUME` / `REFRESH` |
| `suspendReclusterDynamicTable`, `resumeReclusterDynamicTable` | `ALTER DYNAMIC TABLE [IF EXISTS] … SUSPEND RECLUSTER` / `RESUME RECLUSTER` |
| `swapWithDynamicTable` `…:swap-with?targetName=` | `ALTER DYNAMIC TABLE [IF EXISTS] … SWAP WITH` |
| `setTags`, `unsetTags`, `getTags` | `ALTER DYNAMIC TABLE … SET TAG / UNSET TAG`; `TAG_REFERENCES(…, 'TABLE')` |

Not provided (`501`): `initialization_warehouse`, `frozen_where`, `backfill_from`, `default_ddl_collation`,
`log_level` and `row_timestamp` on create, and a data type or comment in the column list (names only).

**Sequences (`sequence.yaml`).** A sequence is read from `SHOW SEQUENCES`: `start` is the value it hands out
next and `increment` the listing's `interval`.

| endpoint | runs |
|---|---|
| `listSequences` `GET /sequences` | `SHOW SEQUENCES [LIKE] IN SCHEMA …` |
| `createSequence` `POST /sequences` | `CREATE [OR REPLACE] SEQUENCE [IF NOT EXISTS] … [START] [INCREMENT] [ORDER \| NOORDER] [COMMENT]` |
| `fetchSequence` `GET /sequences/{name}` | `SHOW SEQUENCES LIKE` |
| `deleteSequence` `DELETE /sequences/{name}` | `DROP SEQUENCE [IF EXISTS]` |
| `cloneSequence` `POST /sequences/{name}:clone` | `CREATE SEQUENCE <target> CLONE …` |
| `renameSequence` `POST /sequences/{name}:rename` | `ALTER SEQUENCE [IF EXISTS] … RENAME TO <targetDatabase.targetSchema.targetName>` |

#### Event tables (`event-table.yaml`)

An event table is a table with the fixed OpenTelemetry column set (`TIMESTAMP`, `START_TIMESTAMP`,
`OBSERVED_TIMESTAMP`, `TRACE`, `RESOURCE`, `RESOURCE_ATTRIBUTES`, `SCOPE`, `SCOPE_ATTRIBUTES`, `RECORD_TYPE`,
`RECORD`, `RECORD_ATTRIBUTES`, `VALUE`, `EXEMPLARS`, each with the account's column comment). `SHOW TABLES` reports
it with `is_event = Y`; `SHOW EVENT TABLES` lists only event tables.

| endpoint | runs |
|---|---|
| `listEventTables` `GET …/event-tables` | `SHOW EVENT TABLES [LIKE] IN SCHEMA … [STARTS WITH] [LIMIT … FROM]`, each row completed as a fetch |
| `createEventTable` `POST …/event-tables` | `CREATE [OR REPLACE] EVENT TABLE [IF NOT EXISTS]` with `CLUSTER BY`, `DATA_RETENTION_TIME_IN_DAYS`, `MAX_DATA_EXTENSION_TIME_IN_DAYS`, `CHANGE_TRACKING`, `DEFAULT_DDL_COLLATION`, `COMMENT` |
| `fetchEventTable` `GET …/event-tables/{name}` | `SHOW EVENT TABLES LIKE` + the `SHOW TABLES` row + `DESCRIBE TABLE` for `columns` |
| `deleteEventTable` | `DROP TABLE [IF EXISTS]` |
| `renameEventTable` `POST …/{name}:rename?targetName=` | `ALTER TABLE [IF EXISTS] … RENAME TO` |
| `setTags`, `unsetTags`, `getTags` | `ALTER TABLE … SET TAG` / `UNSET TAG`; `TAG_REFERENCES(…, 'TABLE')` |

`max_data_extension_time_in_days` and `default_ddl_collation` are the account defaults (14, empty): Frostlake keeps
neither per table.

#### Iceberg tables (`iceberg-table.yaml`)

Only Snowflake-managed Iceberg tables exist: `CREATE [OR REPLACE] [TRANSIENT] ICEBERG TABLE` in the four shapes of a
table (columns, `AS SELECT`, `CLONE`, `LIKE`) stores the rows as an ordinary table's and keeps the Iceberg metadata
(`EXTERNAL_VOLUME`, `CATALOG = 'SNOWFLAKE'`, `BASE_LOCATION`, `CATALOG_SYNC`, `STORAGE_SERIALIZATION_POLICY`). A
table written without an external volume is kept in `SNOWFLAKE_MANAGED` storage (where a `BASE_LOCATION` is
refused; a schema's, database's or account's `EXTERNAL_VOLUME` parameter is not consulted, where a real account uses
it) and one without a base location gets `<DB>/<SCHEMA>/<NAME>.<8 random characters>/` — a clone keeps its
source's — as on a real account. Change tracking is always on (`CHANGE_TRACKING = FALSE` is refused). `SHOW TABLES` reports it with
`is_iceberg = Y`, `SHOW ICEBERG TABLES` lists it, and `DROP ICEBERG TABLE` refuses a table that is not one.

| endpoint | runs |
|---|---|
| `listIcebergTables` | `SHOW ICEBERG TABLES [LIKE] IN SCHEMA … [STARTS WITH] [LIMIT … FROM]`; `columns` only with `deep=true` |
| `createSnowflakeManagedIcebergTable` | `CREATE [OR REPLACE] ICEBERG TABLE [IF NOT EXISTS] (columns, constraints) …` with the body's options |
| `createSnowflakeManagedIcebergTableAsSelect` | the same `… AS <query>` |
| `fetchIcebergTable` | `SHOW ICEBERG TABLES LIKE` + the `SHOW TABLES` row + `DESCRIBE TABLE` |
| `dropIcebergTable` | `DROP ICEBERG TABLE [IF EXISTS] … [CASCADE \| RESTRICT]` |
| `undropIcebergTable` | `UNDROP ICEBERG TABLE` |
| `cloneSnowflakeManagedIcebergTable`, `createSnowflakeManagedIcebergTableLike` | `CREATE ICEBERG TABLE <target> CLONE <table> [AT \| BEFORE (…)]` / `LIKE <table>` |
| `resumeReclusterIcebergTable`, `suspendReclusterIcebergTable` | `ALTER ICEBERG TABLE … RESUME \| SUSPEND RECLUSTER` |
| `refreshIcebergTable`, `convertToManagedIcebergTable` | `ALTER ICEBERG TABLE … REFRESH ['<path>']` / `CONVERT TO MANAGED …`: both need an externally managed table, so they are refused (`400`) |
| `createUnmanagedIcebergTableFromAWSGlueCatalog`, `…FromDelta`, `…FromIcebergFiles`, `…FromIcebergRest` | `501`: an externally managed table's data and metadata live outside, where Frostlake does not read |
| `setTags`, `unsetTags`, `getTags` | `ALTER ICEBERG TABLE … SET TAG` / `UNSET TAG`; `TAG_REFERENCES(…, 'TABLE')` |

`storage_serialization_policy`, `max_data_extension_time_in_days` and `auto_refresh` come back as the account's
defaults (`OPTIMIZED`, 14, false); Snowflake-managed storage is reported as `SNOWFLAKE_DEFAULT_VOLUME`, as the API
does; a generated base location is reported as null. A real account also checks that the external volume's storage
is reachable.

### Stages, external volumes and pipes (`stage.yaml`, `external-volume.yaml`, `pipe.yaml`)

Paths below are relative to `/api/v2/databases/{database}/schemas/{schema}`, except external volumes, which are
account-level: `/api/v2/external-volumes`.

**Stages** (`stage.yaml`):

| endpoint | runs |
|---|---|
| `listStages` `GET /stages` | `SHOW STAGES [LIKE] IN SCHEMA` |
| `createStage` `POST /stages` | `CREATE [OR REPLACE] [TEMPORARY] STAGE [IF NOT EXISTS]` with `URL`, `STORAGE_INTEGRATION`, `ENDPOINT`, `CREDENTIALS = (…)`, `ENCRYPTION = (…)`, `DIRECTORY = (…)`, `FILE_FORMAT = (…)`, `COPY_OPTIONS = (…)` and `COMMENT` from the body |
| `fetchStage` `GET /stages/{name}` | `SHOW STAGES LIKE … IN SCHEMA` + `DESCRIBE STAGE` (file format, copy options, directory table) |
| `deleteStage` `DELETE /stages/{name}` | `DROP STAGE [IF EXISTS]` |
| `listFiles` `GET /stages/{name}/files` | `LIST @stage [PATTERN = …]`; `size` is sent as a string, as the schema types it |
| `getPresignedUrl` `POST /stages/{name}/files/{filePath}:presigned-url` | `GET_PRESIGNED_URL('@stage', filePath [, expiration_time])` |

The nested body objects become the parenthesised option lists: each key must be a lower-case word (`[a-z][a-z0-9_]*`) and is
written upper-cased, each value as the literal its JSON type calls for (text quoted, whole numbers and booleans bare,
an array of strings as a list; anything else is `400`). `credentials` is write-only and never read back. A fetched stage answers every property its DESCRIBE lists —
an empty one as null (`size_limit`, `file_extension`) — with text decoded from the listing's escapes (`\\` is a
backslash, `\n` a line break), every `directory_table` property (`aws_sns_topic` and `notification_integration` null,
`refresh_on_create` true when not listed), an internal stage's empty `url` as `""` and an empty comment as null. Frostlake's
DESCRIBE STAGE lists the CSV options whatever the format's type (a real account lists the type's own), so a JSON
format's own options (`strip_outer_array`, …) are not read back. A `file_format` of type `FORMAT_REF` is written
`FILE_FORMAT = (FORMAT_NAME = '…')`; a fetch reports an inline format with the options the specification lists for its
type. `kind` is read from the listing's `type` column; the engine does not report a stage as temporary there, so a
fetched stage is always `PERMANENT`.

A presigned URL points at Frostlake's own HTTP server: `http://<http.host>:<http.port>/presigned/<token>/<file name>`.
The token is opaque — the staged file's location and the expiry, signed with a key the process draws at start-up — so
the URL needs no session and stops working when it expires or the server restarts. Fetching it streams the file;
an expired URL answers `403` (`AccessDenied`), a token the server did not sign `403` (`SignatureDoesNotMatch`) and a
file that is not staged `404` (`NoSuchKey`), each with an XML error body as an object store answers.

**Pipes** (`pipe.yaml`):

| endpoint | runs |
|---|---|
| `listPipes` `GET /pipes` | `SHOW PIPES [LIKE] IN SCHEMA` |
| `createPipe` `POST /pipes` | `CREATE [OR REPLACE] PIPE [IF NOT EXISTS] … [AUTO_INGEST] [ERROR_INTEGRATION] [AWS_SNS_TOPIC] [INTEGRATION] [COMMENT] AS <copy_statement>` |
| `fetchPipe` `GET /pipes/{name}` | `SHOW PIPES LIKE … IN SCHEMA`; `copy_statement` is the listing's `definition` |
| `deletePipe` `DELETE /pipes/{name}` | `DROP PIPE [IF EXISTS]` |
| `refreshPipe` `POST /pipes/{name}:refresh` | `ALTER PIPE [IF EXISTS] … REFRESH [PREFIX = …] [MODIFIED_AFTER = …]` |
| `setTags`, `unsetTags`, `getTags` | `ALTER PIPE … SET TAG` / `UNSET TAG`, `TAG_REFERENCES(…, 'PIPE')` |

`copy_statement` is SQL by the schema's definition and is sent as written. The COPY's names resolve in the pipe's
own schema, when the pipe is created and when it loads. `auto_ingest` and `aws_sns_topic` are accepted but answered
null (the account answers both null): the listing has no `auto_ingest`, and its `notification_channel` is not read
back. An `aws_sns_topic` without `auto_ingest` is refused,
as the statement is: `Pipe Notifications bind failure "Cannot set AWS SNS topic for pipe without auto_ingest"`.

#### External volumes (`external-volume.yaml`)

| endpoint | runs |
|---|---|
| `listExternalVolumes` `GET /external-volumes` | `SHOW EXTERNAL VOLUMES [LIKE]` + `DESCRIBE EXTERNAL VOLUME` per row |
| `createExternalVolume` `POST /external-volumes` | `CREATE [OR REPLACE] EXTERNAL VOLUME [IF NOT EXISTS] … STORAGE_LOCATIONS = ((NAME = … STORAGE_PROVIDER = … STORAGE_BASE_URL = … [ENCRYPTION = (…)]), …) [ALLOW_WRITES] [COMMENT]` |
| `fetchExternalVolume` `GET /external-volumes/{name}` | `SHOW EXTERNAL VOLUMES LIKE` + `DESCRIBE EXTERNAL VOLUME` (each `STORAGE_LOCATION_n` row is a JSON object) |
| `deleteExternalVolume` | `DROP EXTERNAL VOLUME [IF EXISTS]` |
| `undropExternalVolume` `POST /external-volumes/{name}:undrop` | `UNDROP EXTERNAL VOLUME` |

SQL: `CREATE`, `ALTER EXTERNAL VOLUME … ADD STORAGE_LOCATION = (…) | REMOVE STORAGE_LOCATION '<name>' | UPDATE
STORAGE_LOCATION = '<name>' CREDENTIALS = (…) | SET ALLOW_WRITES | SET COMMENT`, `DROP`, `UNDROP`, `SHOW EXTERNAL
VOLUMES [LIKE]` (`name, allow_writes, comment`) and `DESC[RIBE] EXTERNAL VOLUME` (`parent_property, property,
property_type, property_value, property_default`). Frostlake never reaches the storage: the `ACTIVE` location is
empty, and the IAM user and external id a real account generates are absent (so is `created_on` / `owner` in the
API's body, which the listing does not carry).

### Streams and tasks (`stream.yaml`, `task.yaml`)

Paths below are relative to `/api/v2/databases/{database}/schemas/{schema}`.

**Streams** (`stream.yaml`):

| endpoint | runs |
|---|---|
| `listStreams` `GET /streams` | `SHOW STREAMS [LIKE] IN SCHEMA [STARTS WITH] [LIMIT … FROM]` |
| `createStream` `POST /streams` | `CREATE [OR REPLACE] STREAM [IF NOT EXISTS] … [COPY GRANTS] ON TABLE \| VIEW <source> [APPEND_ONLY] [SHOW_INITIAL_ROWS] [COMMENT]` |
| `fetchStream` `GET /streams/{name}` | `SHOW STREAMS LIKE … IN SCHEMA` |
| `deleteStream` `DELETE /streams/{name}` | `DROP STREAM [IF EXISTS]` |
| `cloneStream` `POST /streams/{name}:clone` | `CREATE [OR REPLACE] STREAM [IF NOT EXISTS] <targetDatabase>.<targetSchema>.<name> CLONE … [COPY GRANTS]`, then `ALTER STREAM … SET COMMENT` for a body comment |
| `setTags`, `unsetTags`, `getTags` | `ALTER STREAM … SET TAG` / `UNSET TAG`, `TAG_REFERENCES(…, 'STREAM')` |

A source's database and schema default to the path's; a clone's target database and schema default to the source
stream's. The clone takes the source's definition, comment and current offset, so it holds the same pending changes.
`stream_source` is rebuilt from the listing (`source_type`, `table_name`, `mode`, `base_tables`), with `point_of_time`
null; the stream's own `table_name` is null, as the account answers it. `show_initial_rows` is not read back. A
source of type `stage` writes `ON STAGE` (the stage's directory table, which needs `DIRECTORY = (ENABLE = TRUE)`);
`external_table` writes `ON EXTERNAL TABLE … [INSERT_ONLY = …]`, which the engine refuses as a real account refuses
a missing external table, since it keeps none. A `point_of_time` writes the `AT | BEFORE` clause: a `timestamp` or
an `offset` is an expression and is sent as written, a `statement` id and a `stream` name are quoted, and a stream
named without its schema is one of the path's schema.

**Tasks** (`task.yaml`):

| endpoint | runs |
|---|---|
| `listTasks` `GET /tasks` | `SHOW TASKS [LIKE] IN SCHEMA [STARTS WITH] [ROOT ONLY] [LIMIT … FROM]` (`rootOnly` → `ROOT ONLY`) |
| `createTask` `POST /tasks` | `CREATE [OR REPLACE] TASK [IF NOT EXISTS] … [WAREHOUSE] [SCHEDULE] [options] [FINALIZE = …] [AFTER …] [EXECUTE AS USER …] [WHEN <condition>] AS <definition>` |
| `fetchTask` `GET /tasks/{name}` | `SHOW TASKS LIKE … IN SCHEMA` |
| `createOrAlterTask` `PUT /tasks/{name}` | `CREATE TASK`, or `ALTER TASK … SET` / `UNSET` / `ADD AFTER` / `REMOVE AFTER` / `REMOVE WHEN` / `SET FINALIZE` / `UNSET FINALIZE` / `SET` / `UNSET EXECUTE AS USER` / `MODIFY AS` / `MODIFY WHEN` |
| `deleteTask` `DELETE /tasks/{name}` | `DROP TASK [IF EXISTS]` |
| `executeTask` `POST /tasks/{name}:execute` | `EXECUTE TASK … [RETRY LAST]` (`retryLast`) |
| `resumeTask`, `suspendTask` | `ALTER TASK … RESUME` / `SUSPEND` |
| `fetchTaskDependents` `GET /tasks/{name}/dependents` | `TASK_DEPENDENTS(TASK_NAME => …, RECURSIVE => …)`, each row shaped from `SHOW TASKS` |
| `getCurrentGraphs` `GET /tasks/{name}/current-graphs` (and `getCurrentGraphsDeprecated`, `/current_graphs`) | `CURRENT_TASK_GRAPHS(ROOT_TASK_NAME => …, RESULT_LIMIT => …)` |
| `getCompleteGraphs` `GET /tasks/{name}/complete-graphs` (and `getCompleteGraphsDeprecated`, `/complete_graphs`) | `COMPLETE_TASK_GRAPHS(ROOT_TASK_NAME => …, RESULT_LIMIT => …, ERROR_ONLY => …)` |
| `setTags`, `unsetTags`, `getTags` | `ALTER TASK … SET TAG` / `UNSET TAG`, `TAG_REFERENCES(…, 'TASK')` |

A `schedule` is written as the account writes it, its whole length in seconds — `{"minutes": 2, "seconds": 30}` is
`SCHEDULE = '150 SECOND'` — or `'USING CRON <cron_expr> <timezone>'`, and read back from the listing's text: an
interval in seconds, minutes or hours reads back as whole `minutes` and the `seconds` left over, the seconds always
present; `target_completion_interval` likewise. A fetched task answers `config` as an empty
object when it has none, and the account takes an empty `config` sent back as no config: a create writes none and a
create-or-alter unsets the task's. `session_parameters` answers an empty object (the task's session parameters are not
read back), `owner_role_type` null, and a predecessor in the task's own schema by its bare name. The graph runs answer `first_error_code` 0 for a run without one and `run_id` as the RUN_ID's
low 32 bits, as the account does. A RETRY LAST with nothing to retry is `400` with the account's codes: `091457` when
the graph never ran, `091456` when its last run had no failures. `predecessors` become the `AFTER` list, a bare name naming a task of the path's schema; `condition` and
`definition` are SQL and are sent as written. The graph endpoints keep the runs of the path's task in the path's schema.
`config` is written as `CONFIG` (the object as JSON text) and read back from the listing; `session_parameters` become
one `<name> = <value>` each; `overlap_policy`, `success_integration` and `serverless_task_min_statement_size` are
options like the others; `finalize` is written `FINALIZE = <root>` and read back from the listing's `task_relations`;
`execute_as_user` is written `EXECUTE AS USER`. `PUT` creates the task when it is absent; otherwise it unsets a
finalizer link the body drops, removes the predecessors it drops (`REMOVE AFTER`) and a condition it drops
(`REMOVE WHEN`), UNSETs the settable properties the body leaves out, SETs what it names, adds the predecessors it adds
(`ADD AFTER`), sets its finalizer link and the user it runs as (`UNSET EXECUTE AS USER` when the body drops it), and MODIFYs the
definition and the condition when they
changed. The integer parameters (`user_task_timeout_ms`, …), `user_task_managed_initial_warehouse_size`,
`serverless_task_min_statement_size`, `serverless_task_max_statement_size` and the session parameters are written
but not read back:
`SHOW TASKS` does not carry them.

### Functions, procedures and artifact repositories

Paths below are relative to `/api/v2/databases/{database}/schemas/{schema}`. A function or procedure is addressed by `{nameWithArgs}`: its name followed by its argument types
(`add(NUMBER,NUMBER)`). The list goes into the statement as written, so arguments written with their names
(`add(a number, b number)`) meet the statement's syntax refusal (`400`) on fetch, delete and the tag endpoints, as
on the account; `:rename` (`ALTER FUNCTION … RENAME TO`) takes that form, as the account does. The endpoints that call
a routine (`:execute`, `:call`) also take the bare name, and the arguments pick the overload; calling a routine
that does not exist is `400` code `002141` (`unknown user-defined function …`).

User-defined functions (`user-defined-function.yaml`), under `/databases/{database}/schemas/{schema}`:

| endpoint | runs |
|---|---|
| `listUserDefinedFunctions` `GET /user-defined-functions` | `SHOW USER FUNCTIONS [LIKE] IN SCHEMA` + `DESCRIBE FUNCTION` per overload |
| `createUserDefinedFunction` `POST /user-defined-functions` | `CREATE [OR REPLACE] [TEMPORARY] [SECURE] FUNCTION [IF NOT EXISTS]` from the body |
| `fetchUserDefinedFunction` `GET /user-defined-functions/{nameWithArgs}` | `SHOW USER FUNCTIONS LIKE` + `DESCRIBE FUNCTION` |
| `deleteUserDefinedFunction` `DELETE /user-defined-functions/{nameWithArgs}` | `DROP FUNCTION [IF EXISTS] name(types)` |
| `executeUserDefinedFunction` `POST /user-defined-functions/{name}:execute` | `SELECT name(…) AS "<NAME>"` (named arguments when each has a `name`, cast when it has a `datatype`); answers `{"<NAME>": "<value as text>"}`, keyed by the function's name (the account keys it by the call's text) |
| `renameUserDefinedFunction` `POST …/{nameWithArgs}:rename` | `ALTER FUNCTION … RENAME TO targetDatabase.targetSchema.targetName` |
| `setTags`, `unsetTags`, `getTags` | `ALTER FUNCTION name(types) SET TAG` / `UNSET TAG`; see *Tags* |

Procedures (`procedure.yaml`):

| endpoint | runs |
|---|---|
| `listProcedures` `GET /procedures` | `SHOW PROCEDURES [LIKE] IN SCHEMA` + `DESCRIBE PROCEDURE` per overload |
| `createProcedure` `POST /procedures` | `CREATE [OR REPLACE] PROCEDURE [IF NOT EXISTS]` from the body |
| `fetchProcedure` `GET /procedures/{nameWithArgs}` | `SHOW PROCEDURES LIKE` + `DESCRIBE PROCEDURE` |
| `deleteProcedure` `DELETE /procedures/{nameWithArgs}` | `DROP PROCEDURE [IF EXISTS] name(types)` |
| `callProcedure` `POST /procedures/{nameWithArgs}:call` | `CALL name(…)` with the bare name, named arguments when each has a `name` and cast when it has a `datatype` (a path carrying argument types meets CALL's syntax refusal, as on the account); answers the result's rows as objects keyed by the lower-cased column name, values as text |
| `setTags`, `unsetTags`, `getTags` | `ALTER PROCEDURE name(types) SET TAG` / `UNSET TAG`; see *Tags* |

The body's `arguments` (with `default_value`), `return_type` (`DATATYPE`, with `nullable: false` for `NOT NULL`,
or `TABLE` with its `column_list`), `language_config` (`language`, `runtime_version`,
`packages`, `imports`, `handler`, and for a function `called_on_null_input` and `is_volatile`; a procedure's
`called_on_null_input` is left out), `comment`, `body` and, for a procedure, `execute_as` become
the statement's clauses. Properties no clause of the engine's statement carries are left out of it:
`external_access_integrations`, `secrets`, `target_path`, `artifact_repository`, `artifact_repository_packages`,
and the `copyGrants` parameter. `is_aggregate: true` answers `501` (no aggregate UDFs), as do a secure procedure
and `execute_as: RESTRICTED CALLER`. A fetch reports what SHOW and DESCRIBE carry, with the account's defaults for
what they do not (`called_on_null_input` true, `nullable: true`, `log_level` and `trace_level` `OFF`; for a
function `is_temporary: false` and `is_volatile` true; and `is_builtin: true` for a procedure, as the account reports
it);
`default_value`, `owner` and `owner_role_type` are `null`. A listing leaves the body out (`null` for a function,
`""` for a procedure), as the account's listing does.

Service functions (`function.yaml`) are functions whose calls go to a Snowpark Container Services service:

| endpoint | runs |
|---|---|
| `listFunctions` `GET /functions` | `SHOW USER FUNCTIONS [LIKE] IN SCHEMA`: every user function, as the account lists them — a service function in the `ServiceFunction` shape, any other in the user-defined function one |
| `createFunction` `POST /functions` | `CREATE [OR REPLACE] FUNCTION [IF NOT EXISTS] name(args) RETURNS type SERVICE = … ENDPOINT = … [MAX_BATCH_ROWS = n] AS '<path>'` |
| `fetchFunction` `GET /functions/{nameWithArgs}` | `SHOW USER FUNCTIONS LIKE` + `DESCRIBE FUNCTION`, shaped as in the listing |
| `deleteFunction` `DELETE /functions/{nameWithArgs}` | `DROP FUNCTION [IF EXISTS] name(types)` |
| `executeFunction` `POST /functions/{name}:execute` | `501`: no service runs to answer a call |
| `setTags`, `unsetTags`, `getTags` | as for user-defined functions |

The service must exist when the function is created (an unqualified `service` is taken in the path's schema).
DESCRIBE FUNCTION on a service function adds `service`, `endpoint` and `max_batch_rows` rows; a call of one is
refused (`Service function <NAME> cannot be called: …`).

Artifact repositories (`artifact-repository.yaml`):

| endpoint | runs |
|---|---|
| `listArtifactRepositories` `GET /artifact-repositories` | `SHOW ARTIFACT REPOSITORIES [LIKE] IN SCHEMA`; `startsWith`, `showLimit` and `fromName` are applied to its rows |
| `createArtifactRepository` `POST /artifact-repositories` | `CREATE [OR REPLACE] ARTIFACT REPOSITORY [IF NOT EXISTS] … TYPE = … [API_INTEGRATION = '…'] [COMMENT = '…']` |
| `fetchArtifactRepository` `GET /artifact-repositories/{name}` | `SHOW ARTIFACT REPOSITORIES LIKE` |
| `deleteArtifactRepository` `DELETE /artifact-repositories/{name}` | `DROP ARTIFACT REPOSITORY [IF EXISTS]` |
| `createOrAlterArtifactRepository` `PUT /artifact-repositories/{name}` | `CREATE ARTIFACT REPOSITORY`, or `ALTER ARTIFACT REPOSITORY … SET COMMENT` / `UNSET COMMENT` |
| `renameArtifactRepository` `POST …/{name}:rename` | `501`: no statement renames an artifact repository |

The API's type `PIP` is the statement's `PYPI`. CREATE ARTIFACT REPOSITORY requires `API_INTEGRATION`
(`Property 'API_INTEGRATION' must be specified`); the integration is stored by name and its existence is not yet
checked.

### Compute pools, image repositories and services

Compute pool paths are relative to `/api/v2`; image repository and service paths to
`/api/v2/databases/{database}/schemas/{schema}`.

Compute pools (`compute-pool.yaml`):

| endpoint | runs |
|---|---|
| `listComputePools` `GET /compute-pools` | `SHOW COMPUTE POOLS [LIKE] [STARTS WITH] [LIMIT … FROM]` |
| `createComputePool` `POST /compute-pools` | `CREATE COMPUTE POOL [IF NOT EXISTS]` with the body's properties and `initiallySuspended`; `orReplace` drops the pool first (the statement has no OR REPLACE) |
| `fetchComputePool` `GET /compute-pools/{name}` | `DESCRIBE COMPUTE POOL` |
| `createOrAlterComputePool` `PUT /compute-pools/{name}` | `CREATE COMPUTE POOL`, or `ALTER COMPUTE POOL … SET` / `UNSET` (no `CREATE OR ALTER COMPUTE POOL` exists) |
| `deleteComputePool` `DELETE /compute-pools/{name}` | `DROP COMPUTE POOL [IF EXISTS]` |
| `resumeComputePool`, `suspendComputePool` | `ALTER COMPUTE POOL … RESUME` / `SUSPEND` |
| `stopAllServicesInComputePool`, `stopAllServicesInComputePoolDeprecated` | `ALTER COMPUTE POOL … STOP ALL` |
| `listComputePoolInstanceFamilies` `GET /compute-pools/instance-families` | `SHOW COMPUTE POOL INSTANCE FAMILIES` |
| `setTags`, `unsetTags`, `getTags` | `ALTER COMPUTE POOL … SET TAG` / `UNSET TAG`; see *Tags* |

Image repositories and services are catalog metadata: nothing is pushed to a repository and no container runs.

| endpoint | runs |
|---|---|
| `listImageRepositories` `GET /image-repositories` | `SHOW IMAGE REPOSITORIES [LIKE] IN SCHEMA` |
| `createImageRepository` `POST /image-repositories` | `CREATE [OR REPLACE] IMAGE REPOSITORY [IF NOT EXISTS]` |
| `fetchImageRepository` `GET /image-repositories/{name}` | `SHOW IMAGE REPOSITORIES LIKE` |
| `deleteImageRepository` `DELETE /image-repositories/{name}` | `DROP IMAGE REPOSITORY [IF EXISTS]` |
| `listImagesInRepository` `GET /image-repositories/{name}/images` | `SHOW IMAGES IN IMAGE REPOSITORY` (always empty) |
| `setTags`, `unsetTags`, `getTags` | `ALTER IMAGE REPOSITORY … SET TAG` / `UNSET TAG`; see *Tags* |
| `listServices` `GET /services` | `SHOW SERVICES [LIKE] IN SCHEMA [STARTS WITH] [LIMIT … FROM]` |
| `createService` `POST /services` | `CREATE SERVICE [IF NOT EXISTS] … IN COMPUTE POOL … FROM SPECIFICATION '…'` or `FROM @stage SPECIFICATION_FILE = '…'`, with the body's properties; `orReplace` is `400` |
| `executeJobService` `POST /services:execute-job` | `EXECUTE JOB SERVICE IN COMPUTE POOL … FROM … NAME = …`, recorded as a job service in state `DONE` |
| `fetchService` `GET /services/{name}` | `SHOW SERVICES LIKE` + `DESCRIBE SERVICE` |
| `createOrAlterService` `PUT /services/{name}` | `CREATE SERVICE`, or `ALTER SERVICE … FROM …`, `SET` and `UNSET`; a different `compute_pool` is `400` |
| `deleteService` `DELETE /services/{name}` | `DROP SERVICE [IF EXISTS]` |
| `resumeService`, `suspendService` | `ALTER SERVICE [IF EXISTS] … RESUME` / `SUSPEND` |
| `fetchServiceStatus` `GET /services/{name}/status` | `{"system$get_service_status": "[]"}`: no container reports a status |
| `fetchServiceLogs` `GET /services/{name}/logs` | `{"system$get_service_logs": ""}` |
| `listServiceContainers`, `listServiceInstances` | `SHOW SERVICE CONTAINERS` / `INSTANCES IN SERVICE` (always empty) |
| `showServiceEndpoints` `GET /services/{name}/endpoints` | `SHOW ENDPOINTS IN SERVICE`: the specification's `endpoints` |
| `listServiceRoles` `GET /services/{name}/roles` | `SHOW ROLES IN SERVICE`: `ALL_ENDPOINTS_USAGE` and the specification's `serviceRoles` |
| `listServiceRoleGrantsOf`, `listServiceRoleGrantsTo` | an empty list for a role the service defines (service roles are never granted), `404` otherwise |
| `setTags`, `unsetTags`, `getTags` | `ALTER SERVICE … SET TAG` / `UNSET TAG`; see *Tags* |

A service keeps its declared state: `RUNNING` once created or resumed, `SUSPENDED` once suspended; it reports no
instances, containers or logs. Its endpoints and service roles are read from the specification's YAML text (block
style); a specification from a staged file is recorded by stage and path, not read.

### Network policies, network rules, password policies and secrets

`network-policy.yaml`, `network-rule.yaml`, `password-policy.yaml`, `secret.yaml`. The four objects are recorded
and never enforced: a network policy blocks no one and a password policy checks no password (`docs/scope.md`,
"HTTP AUTHENTICATION"). Paths below are relative to `/api/v2/databases/{database}/schemas/{schema}` for the three
schema-level kinds.

| endpoint | runs |
|---|---|
| `listNetworkPolicies` `GET /network-policies` | `SHOW NETWORK POLICIES` + `DESCRIBE NETWORK POLICY` per policy |
| `createNetworkPolicy` `POST /network-policies` | `CREATE [OR REPLACE] NETWORK POLICY [IF NOT EXISTS]` with the four lists and `COMMENT` |
| `fetchNetworkPolicy` `GET /network-policies/{name}` | `SHOW NETWORK POLICIES` + `DESCRIBE NETWORK POLICY` |
| `deleteNetworkPolicy` `DELETE /network-policies/{name}` | `DROP NETWORK POLICY [IF EXISTS]` |
| `listNetworkRules` `GET …/network-rules` | `SHOW NETWORK RULES [LIKE] IN SCHEMA [STARTS WITH] [LIMIT … FROM]` + `DESCRIBE NETWORK RULE` per rule |
| `createNetworkRule` `POST …/network-rules` | `CREATE [OR REPLACE] NETWORK RULE [IF NOT EXISTS]` with `TYPE`, `MODE`, `VALUE_LIST`, `COMMENT` |
| `fetchNetworkRule` `GET …/network-rules/{name}` | `SHOW NETWORK RULES LIKE` + `DESCRIBE NETWORK RULE` (for `value_list`) |
| `deleteNetworkRule` `DELETE …/network-rules/{name}` | `DROP NETWORK RULE [IF EXISTS]` |
| `listPasswordPolicies` `GET …/password-policies` | `SHOW PASSWORD POLICIES [LIKE] IN SCHEMA [STARTS WITH] [LIMIT]` + `DESCRIBE PASSWORD POLICY` per policy |
| `createPasswordPolicy` `POST …/password-policies` | `CREATE [OR REPLACE] PASSWORD POLICY [IF NOT EXISTS]` with the eleven `PASSWORD_*` settings and `COMMENT` |
| `fetchPasswordPolicy` `GET …/password-policies/{name}` | `SHOW PASSWORD POLICIES LIKE` + `DESCRIBE PASSWORD POLICY` (a setting never set answers its default) |
| `deletePasswordPolicy` `DELETE …/password-policies/{name}` | `DROP PASSWORD POLICY [IF EXISTS]` |
| `renamePasswordPolicy` `POST …/password-policies/{name}:rename` | `ALTER PASSWORD POLICY [IF EXISTS] … RENAME TO targetDatabase.targetSchema.targetName` (the path's database and schema when the query leaves them out) |
| `listSecrets` `GET …/secrets` | `SHOW SECRETS [LIKE] IN SCHEMA [STARTS WITH] [LIMIT … FROM]` + `DESCRIBE SECRET` per secret |
| `createSecret` `POST …/secrets` | `CREATE [OR REPLACE] SECRET [IF NOT EXISTS] … TYPE = <type>` with the properties of the body's type (`PASSWORD`: `username`, `password`; `GENERIC_STRING`: `secret_string`; `OAUTH2`: `api_authentication`, `oauth_scopes`, `oauth_refresh_token`, `oauth_refresh_token_expiry_time`; `CLOUD_PROVIDER_TOKEN`: `api_authentication`; `SYMMETRIC_KEY`: `algorithm`) and `comment` |
| `fetchSecret` `GET …/secrets/{name}` | `SHOW SECRETS LIKE` + `DESCRIBE SECRET` |
| `deleteSecret` `DELETE …/secrets/{name}` | `DROP SECRET [IF EXISTS]` |
| `setTags`, `unsetTags`, `getTags` on network and password policies | see *Tags*; `ALTER NETWORK POLICY` / `ALTER PASSWORD POLICY … SET TAG` / `UNSET TAG`, read back through `TAG_REFERENCES(…, 'NETWORK POLICY' \| 'PASSWORD POLICY')` |

As the account answers them: a network rule's `type` is `IPv4` / `IPv6` for the IP types and its `mode` is lower
case; a network policy's rule lists name each rule by its own name, and a list never set is `[]`; a secret answers
only its own type's properties, with its credential masked — `"password": "********"`,
`"secret_string": "********"` — so no listing, description or response body carries a credential. A body that
leaves out a property its type requires (`type`, a `PASSWORD` secret's `username` / `password`, …) is refused with
`400` code `390400`. SHOW NETWORK POLICIES reports no owner, so a network policy's `owner` and `owner_role_type` are read from the
OWNERSHIP grant `SHOW GRANTS ON NETWORK POLICY` lists (list and fetch run it per policy).

The SQL behind them: `CREATE [OR REPLACE | OR ALTER] NETWORK RULE [IF NOT EXISTS]`, `CREATE [OR REPLACE | OR ALTER]
NETWORK POLICY [IF NOT EXISTS]`, `CREATE [OR REPLACE] PASSWORD POLICY [IF NOT EXISTS]`, `CREATE [OR REPLACE] SECRET
[IF NOT EXISTS]` (CREATE OR ALTER sets what it names and keeps the rest; a rule's TYPE and MODE cannot change);
`ALTER … SET` / `UNSET` of the documented properties (`ALTER SECRET … UNSET` is refused as unsupported, as on the
account), `ALTER NETWORK POLICY … RENAME TO` / `ADD` / `REMOVE {ALLOWED | BLOCKED}_NETWORK_RULE_LIST = ('<rule>', …)` /
`SET TAG` / `UNSET TAG`, `ALTER PASSWORD POLICY … RENAME TO` / `SET TAG` / `UNSET TAG`; `DROP … [IF EXISTS]`; `SHOW
NETWORK RULES`, `SHOW NETWORK POLICIES [LIKE]`, `SHOW PASSWORD POLICIES [… ON ACCOUNT | ON USER <name>]`, `SHOW
SECRETS`; `DESCRIBE` of each. `ALTER ACCOUNT | USER <name> SET PASSWORD POLICY <policy> [FORCE]` / `UNSET PASSWORD
POLICY` and `ALTER ACCOUNT | USER <name> SET NETWORK_POLICY = <policy>` / `UNSET NETWORK_POLICY` record the
attachment (`SHOW PASSWORD POLICIES ON` reads it back, falling back to the account's policy and then the built-in
one) and enforce nothing; a policy attached to a user can be neither dropped nor, for a password policy, replaced.

### Integrations (`api-integration.yaml`, `catalog-integration.yaml`, `notification-integration.yaml`)

Integrations are account objects that hold a third-party service's configuration; Frostlake keeps the configuration
and never calls the service (a real account checks some of it on CREATE — see *Differences*). The three kinds share
one namespace, the `ENABLED` flag and the comment, and are read back from `SHOW <KIND> INTEGRATIONS` and
`DESCRIBE <KIND> INTEGRATION`. Secrets are write-only: `DESCRIBE` shows an API key or an OAuth client secret as one
`☺` per character, and the API answers what `DESCRIBE` shows.

| endpoint | runs |
|---|---|
| `listAPIIntegrations` `GET /api-integrations` | `SHOW API INTEGRATIONS [LIKE]` + `DESCRIBE API INTEGRATION` per row |
| `createAPIIntegration` `POST /api-integrations` | `CREATE [OR REPLACE] API INTEGRATION [IF NOT EXISTS]`: `API_PROVIDER` from `api_hook` (`AWS`, `AZURE`, `GC`, or `GIT` = `git_https_api`), the hook's own properties, `API_ALLOWED_PREFIXES`, `API_BLOCKED_PREFIXES`, `ENABLED`, `COMMENT` |
| `fetchAPIIntegration` `GET /api-integrations/{name}` | `SHOW API INTEGRATIONS LIKE` + `DESCRIBE API INTEGRATION` |
| `createOrAlterAPIIntegration` `PUT /api-integrations/{name}` | `CREATE API INTEGRATION` when absent, else `ALTER API INTEGRATION … SET` the properties ALTER takes that the body names (`API_AWS_ROLE_ARN`, `AZURE_AD_APPLICATION_ID`, `API_KEY`, `ALLOWED_AUTHENTICATION_SECRETS`, the prefixes, `ENABLED`, `COMMENT`; a changed `azure_tenant_id`, `google_audience` or `allowed_api_authentication_integrations` is ignored) and `UNSET` whichever of `API_KEY`, `API_BLOCKED_PREFIXES`, `COMMENT` it leaves out; the provider cannot change (`400`); a body naming another integration is `409` |
| `deleteAPIIntegration` `DELETE /api-integrations/{name}` | `DROP API INTEGRATION [IF EXISTS]` |
| `listCatalogIntegrations`, `fetchCatalogIntegration` | `SHOW CATALOG INTEGRATIONS [LIKE]` + `DESCRIBE CATALOG INTEGRATION`; a Polaris catalog's `rest_config` / `rest_authentication` come from the `REST_CONFIG` / `REST_AUTHENTICATION` objects (`warehouse` is `CATALOG_NAME`) |
| `createCatalogIntegration` `POST /catalog-integrations` | `CREATE [OR REPLACE] CATALOG INTEGRATION [IF NOT EXISTS]` with `CATALOG_SOURCE` (`GLUE`, `OBJECT_STORE`, `POLARIS`), `TABLE_FORMAT`, the source's properties, `ENABLED`, `COMMENT` |
| `deleteCatalogIntegration` | `DROP CATALOG INTEGRATION [IF EXISTS]` |
| `listNotificationIntegrations`, `fetchNotificationIntegration` | `SHOW NOTIFICATION INTEGRATIONS [LIKE]` (its `type` names the hook: `EMAIL`, `WEBHOOK`, `QUEUE - <provider>`) + `DESCRIBE NOTIFICATION INTEGRATION` |
| `createNotificationIntegration` `POST /notification-integrations` | `CREATE [OR REPLACE] NOTIFICATION INTEGRATION [IF NOT EXISTS]`: `TYPE = EMAIL` / `WEBHOOK`, or `TYPE = QUEUE` with the hook type's `NOTIFICATION_PROVIDER` (`AWS_SNS`, `AZURE_EVENT_GRID`, `GCP_PUBSUB`, `AZURE_STORAGE_QUEUE` for `QUEUE_AZURE_EVENT_GRID_INBOUND`) and `DIRECTION = OUTBOUND` for the outbound ones |
| `deleteNotificationIntegration` | `DROP NOTIFICATION INTEGRATION [IF EXISTS]` |
| `setTags`, `unsetTags`, `getTags` (all three kinds) | `ALTER <KIND> INTEGRATION … SET TAG` / `UNSET TAG`; `TAG_REFERENCES(name, 'INTEGRATION')` |

SQL: `CREATE [OR REPLACE] {API | CATALOG | NOTIFICATION} INTEGRATION [IF NOT EXISTS]` with each kind's documented
properties (checked per variant; the first missing required one is refused as `Missing option(s): <NAME>`),
`ALTER [<kind>] INTEGRATION [IF EXISTS] … SET | UNSET | SET TAG | UNSET TAG`, `DROP [<kind>] INTEGRATION
[IF EXISTS]`, `SHOW [<kind>] INTEGRATIONS [LIKE]` (`name, type, category, enabled, comment, created_on`, plus
`direction` for notification integrations) and `DESC[RIBE] [<kind>] INTEGRATION`
(`property, property_type, property_value, property_default`, one layout per variant). `<kind>` may also be
`STORAGE`, `SECURITY` or `EXTERNAL ACCESS` in the statements that take one, but only the three kinds above can be
created. A statement naming a kind the integration is not answers `Integration <NAME> is not a <KIND> integration.`

Differences from a real account: nothing is validated against the outside world — a real account refuses an Azure
or Google gateway URL it cannot reach, contacts a Polaris/REST catalog on CREATE, and accepts only GCP Pub/Sub
names of its own cloud; the identities a real account hands out (`API_AWS_IAM_USER_ARN`, `API_AWS_EXTERNAL_ID`,
`SF_AWS_IAM_USER_ARN`, `GLUE_AWS_IAM_USER_ARN`, …) are not invented, so those `DESCRIBE` rows and the
notification hooks' `sf_aws_*` / `gcp_pubsub_service_account` / `azure_consent_url` properties are absent or null.

### Alerts (`alert.yaml`)

Path prefix `/api/v2/databases/{database}/schemas/{schema}/alerts`.

| endpoint | runs |
|---|---|
| `listAlerts` `GET …/alerts` | `SHOW ALERTS [LIKE] IN SCHEMA … [STARTS WITH] [LIMIT … FROM]` |
| `createAlert` `POST …/alerts` | `CREATE [OR REPLACE] ALERT [IF NOT EXISTS] … [SCHEDULE] [WAREHOUSE] [COMMENT] [CONFIG] [RUNBOOK] [SUSPEND_ALERT_AFTER_NUM_FAILURES] IF (EXISTS (<condition>)) THEN <action>` |
| `fetchAlert` `GET …/alerts/{name}` | `SHOW ALERTS LIKE … IN SCHEMA`, the exact name; `404` when absent |
| `deleteAlert` `DELETE …/alerts/{name}` | `DROP ALERT [IF EXISTS]` |
| `cloneAlert` `POST …/alerts/{name}:clone` | `CREATE [OR REPLACE] ALERT [IF NOT EXISTS] <targetDatabase>.<targetSchema>.<body name> CLONE …` |
| `executeAlert` `POST …/alerts/{name}:execute` | `EXECUTE ALERT` |
| `setTags`, `unsetTags`, `getTags` | see *Tags* (domain `ALERT`) |

The body's `schedule` is written as `USING CRON <cron_expr> <timezone>` for `schedule_type` `CRON_TYPE` and as
`<minutes> MINUTE` for `SCHEDULE_TYPE`, and read back into the same shapes from SHOW ALERTS; an interval written in
seconds or hours (`90 SECONDS`, `2 HOURS`) reads back as its whole minutes (`1`, `120`). `condition` and `action`
are SQL text placed into the statement as written; `config` (an object) is stored as its JSON text, and an alert
without one answers `{}` (an empty object sent back sets none). An empty comment is answered null. A `warehouse` must
exist: `Nonexistent warehouse <NAME> was specified.` A fetched alert carries `name`, `comment`, `schedule`,
`warehouse`, `config`, `condition`, `action`, `runbook`, `created_on`, `database_name`, `schema_name`, `owner`,
`owner_role_type` and `state`; `suspend_alert_after_num_failures`, `template`, `measurement` and `evaluation` are
answered null, since SHOW ALERTS does not list them.

Not provided (`501`): a `template` in the create body (alert templates, `CREATE ALERT … FROM TEMPLATE`) and a
`point_of_time` in the clone body (alerts are cloned as they are now).

On the SQL side, a new alert is suspended; `ALTER ALERT … RESUME` arms its schedule on the engine's task scheduler
(starting it when it is not running), after which the condition is evaluated every interval or at each CRON
instant and the action runs when the condition returns rows. `EXECUTE ALERT` evaluates it once, whatever its state.
Every evaluation is recorded for `INFORMATION_SCHEMA.ALERT_HISTORY`.

### Notebooks and Streamlit (`notebook.yaml`, `streamlit.yaml`)

Notebooks and Streamlit apps are catalog objects: the engine keeps their properties and version history
(`CREATE`, `ALTER`, `DROP`, `UNDROP`, `SHOW NOTEBOOKS` / `SHOW STREAMLITS`, `DESCRIBE NOTEBOOK` /
`DESCRIBE STREAMLIT`, `SHOW VERSIONS IN NOTEBOOK | STREAMLIT`) but never runs them. An object created `FROM` a
location (or from the default template) starts with one committed version, `VERSION$1`, and its default version is
`LAST`. `ADD LIVE VERSION [<alias>] FROM LAST` opens a live version (a second one is refused), `COMMIT` turns it into
the next version, keeping its alias, and closes it, and `ABORT` discards it; both answer `Live version is not found.`
without one. `ADD VERSION [IF NOT EXISTS] [<alias>] FROM '@<stage>[/<path>]'` adds a committed version copied from a
stage that must exist. Each version keeps its alias, its comment (`COMMENT = '…'` on `ADD VERSION`, or on the `COMMIT` that made it: the
comment `ADD LIVE VERSION` gives lasts only while the version is live) and the location its files came from. A legacy `ROOT_LOCATION` app has no versions. A `QUERY_WAREHOUSE` must name an
existing warehouse. A fetch reads the SHOW row and the DESCRIBE row; the version columns become
`default_version_details`, `last_version_details` and `live_version_location_uri`.

Frostlake keeps no Git repositories and no external access integrations: the Git actions (`PUSH`, `PULL`, and a
version added from a Git reference) refuse as the account does for an app whose versions come from no repository,
and `SECRETS` — usable only through an integration that allows the secret — is refused once the list itself reads
well (`SECRETS = ()` is accepted by `ALTER`). `EXECUTE NOTEBOOK` needs a query warehouse and a live version, runs no
notebook code, and answers as a run that succeeded.

| endpoint | runs |
|---|---|
| `listNotebooks`, `listStreamlits` | `SHOW NOTEBOOKS` / `SHOW STREAMLITS [LIKE] IN SCHEMA [STARTS WITH] [LIMIT … FROM]` + `DESCRIBE NOTEBOOK` / `DESCRIBE STREAMLIT` per row |
| `createNotebook` | `CREATE NOTEBOOK [FROM '<fromLocation>']` with `MAIN_FILE`, `COMMENT`, `QUERY_WAREHOUSE`, `IDLE_AUTO_SHUTDOWN_TIME_SECONDS`, `RUNTIME_NAME`, `COMPUTE_POOL` |
| `createStreamlit` | `CREATE STREAMLIT [FROM '<source_location>']` with `MAIN_FILE`, `QUERY_WAREHOUSE`, `COMMENT`, `TITLE`, `IMPORTS`, `EXTERNAL_ACCESS_INTEGRATIONS` |
| `fetchNotebook`, `fetchStreamlit` | `SHOW … LIKE` + `DESCRIBE NOTEBOOK` / `DESCRIBE STREAMLIT` |
| `deleteNotebook`, `deleteStreamlit` | `DROP NOTEBOOK` / `DROP STREAMLIT [IF EXISTS]` |
| `undropStreamlit` | `UNDROP STREAMLIT` |
| `renameNotebook`, `renameStreamlit` | `ALTER … [IF EXISTS] RENAME TO <targetDatabase>.<targetSchema>.<targetName>` (the path's database and schema by default) |
| `executeNotebook` | `EXECUTE NOTEBOOK <name>()` |
| `addLiveVersionNotebook`, `commitNotebook` | `ALTER NOTEBOOK … ADD LIVE VERSION FROM LAST` / `COMMIT`, each with `COMMENT = '<comment>'` from the query |
| `addLiveVersionStreamlit`, `commitStreamlit`, `abortStreamlit` | `ALTER STREAMLIT … ADD LIVE VERSION [<version.name>] FROM LAST` / `COMMIT`, each with `COMMENT = '<version.comment>'`, and `ABORT` |
| `addVersionStreamlit` | `ALTER STREAMLIT … ADD VERSION [IF NOT EXISTS] [<version.name>] FROM '<source_location>' [COMMENT = …]` |
| `addVersionFromGitStreamlit` | `ALTER STREAMLIT … ADD VERSION <version.name> FROM '<git_ref>' [COMMENT = …]` |
| `pullStreamlit`, `pushStreamlit` | `ALTER STREAMLIT … PULL` / `PUSH [TO '<to_git_branch_uri>'] [GIT_CREDENTIALS = <git_credentials> \| USERNAME = … PASSWORD = …] [NAME = … EMAIL = …] [COMMENT = '<git_push_comment>']` |

Not provided (`501`): `addLiveVersionNotebook` and `addLiveVersionStreamlit` with `fromLast=false`; `commitNotebook`
naming a `version` (the SQL's `COMMIT` takes no version name); and `setTags` / `unsetTags` of both resources (`getTags` answers an empty list: neither resource takes tags). The objects
persist with the rest of the catalog: a restart keeps each one's `url_id`, `created_on` and versions, and a dropped
one stays restorable by UNDROP.

### Cortex (`cortex-inference.yaml`, `cortex-embed.yaml`, `cortex-search-service.yaml`)

The Cortex functions come from the optional `frostlake-ai` module ([functions.md](functions.md)), which answers
them from a local Ollama server. The REST surface calls them through SQL, so on a server without the module
`inference:complete`, `inference:embed` and a search `:query` answer `501`, naming the missing function.

| endpoint | runs |
|---|---|
| `cortexLLMInferenceComplete` `POST /cortex/inference:complete` | `SNOWFLAKE.CORTEX.COMPLETE(model, prompt)` for one user message without options, else `COMPLETE(model, [{role, content}, …], {temperature, top_p, max_tokens})` |
| `embed` `POST /cortex/inference:embed` | `SNOWFLAKE.CORTEX.EMBED_TEXT_768` or `EMBED_TEXT_1024`, chosen by the model, once per text |
| `listCortexSearchServices` | `SHOW CORTEX SEARCH SERVICES [LIKE] IN SCHEMA [STARTS WITH] [LIMIT … FROM]` (the engine parses both modifiers but applies neither) |
| `createCortexSearchService` | `CREATE CORTEX SEARCH SERVICE … ON <search_column> [ATTRIBUTES …] WAREHOUSE = … TARGET_LAG = '<seconds> seconds' [COMMENT] AS <definition>` |
| `fetchCortexSearchService` | `SHOW CORTEX SEARCH SERVICES LIKE … IN SCHEMA` |
| `deleteCortexSearchService` | `DROP CORTEX SEARCH SERVICE [IF EXISTS]` |
| `suspendCortexSearchService`, `resumeCortexSearchService` | `ALTER CORTEX SEARCH SERVICE [IF EXISTS] … SUSPEND` / `RESUME` `[INDEXING \| SERVING]` from `target` |
| `queryCortexSearchService` | `SNOWFLAKE.CORTEX.SEARCH_PREVIEW('<db>.<schema>.<service>', '<the request body>')`, answered as its JSON |

A completion is not streamed while it is generated: the whole text arrives as one `text/event-stream` data
event, `data: {"choices": [{"delta": {"type": "text", "content": "…"}}]}`, and the stream ends (the
specification defines no end marker). `"stream": false` answers one JSON object,
`{"choices": [{"message": {"content": "…"}}]}`. Only text message content is read; other content types are
`501`. An embedding answer carries an empty `usage`. `target_lag` is the specification's object,
`{"type": "USER_DEFINED", "seconds": n}`; `DOWNSTREAM` is refused. The layer states use the specification's
words, `ACTIVE` and `SUSPENDED`.

Not provided (`501`): `getModels` sent with its `GetModelsRequest` body (no SQL function lists the models the
module's server holds; a `GET` without the body is a bodiless `400`),
`suggestCortexSearchService` and `sendFeedback`.

### Spark Connect (`spark-connect.yaml`)

All ten endpoints (`executePlan`, `analyzePlan`, `config`, `addArtifacts`, `artifactStatus`, `interrupt`,
`reattachExecute`, `releaseExecute`, `pullRequest`, `pushResponse`) are routed with the body media types the
specification gives them (`application/octet-stream`, and JSON for the last two) and answer `501`: Frostlake
does not execute Spark plans.

## Configuration

| property | default | meaning |
|---|---|---|
| `http.rest.syncWaitMs` | `30000` | how long a request waits for its operation before it is answered `202` |
| `http.rest.resultRetentionMs` | `3600000` | how long a result handle stays fetchable |
