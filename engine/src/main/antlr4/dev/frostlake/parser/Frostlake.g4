grammar Frostlake;

// Parser Rules - Simplified for demonstration
sqlScript
    : (statement SEMI?)+ EOF
    ;

statement
    : ddlStatement
    | dmlStatement
    | queryStatement
    | explainStatement
    | transactionStatement
    | listStatement
    | getStatement
    | putStatement
    | removeStatement
    | sessionSetStatement
    | sessionUnsetStatement
    | proceduralStatement
    | showStatement
    | describeStatement
    | securityStatement
    | taskStatement
    ;

ddlStatement
    : createStatement
    | dropStatement
    | undropStatement
    | alterStatement
    | useStatement
    | commentStatement
    | truncateStatement
    ;

undropStatement
    : UNDROP TABLE qualifiedName SEMI?
    | UNDROP SCHEMA qualifiedName SEMI?
    | UNDROP DATABASE identifier SEMI?
    ;

createStatement
    : CREATE or_replace? DATABASE if_not_exists? identifier (CLONE identifier)? (DATA_RETENTION_TIME_IN_DAYS EQ INTEGER_LITERAL)? commentClause? SEMI?
    | CREATE or_replace? SCHEMA if_not_exists? qualifiedName (CLONE qualifiedName)? commentClause? SEMI?
    | CREATE or_replace? (TRANSIENT | TEMPORARY | TEMP)? TABLE if_not_exists? objectName commentClause? clusterByClause? (LPAREN columnList RPAREN | CLONE qualifiedName | columnListOptional? AS selectStatement) clusterByClause? commentClause? SEMI?
    | CREATE or_replace? SECURE? VIEW if_not_exists? qualifiedName (LPAREN viewColumnList RPAREN)? commentClause? AS selectStatement commentClause? SEMI?
    | CREATE or_replace? SECURE? MATERIALIZED VIEW if_not_exists? qualifiedName (LPAREN viewColumnList RPAREN)? commentClause? AS selectStatement commentClause? SEMI?
    | CREATE or_replace? DYNAMIC TABLE if_not_exists? qualifiedName dynamicTableOptions AS selectStatement commentClause? SEMI?
    | CREATE or_replace? STREAM if_not_exists? qualifiedName ON (TABLE | VIEW) qualifiedName streamOptions? commentClause? SEMI?
    | CREATE or_replace? TASK if_not_exists? qualifiedName warehouseClause? taskOptions? afterClause? commentClause? (WHEN booleanExpr)? AS taskBody commentClause? SEMI?
    | CREATE or_replace? PIPE if_not_exists? qualifiedName pipeOptions? AS copyStatement commentClause? SEMI?
    | CREATE or_replace? SEQUENCE if_not_exists? qualifiedName sequenceOptions? commentClause? SEMI?
    | CREATE or_replace? WAREHOUSE if_not_exists? identifier warehouseProperties? commentClause? SEMI?
    | CREATE or_replace? STAGE if_not_exists? qualifiedName stageProperties? commentClause? SEMI?
    | CREATE or_replace? FILE FORMAT if_not_exists? qualifiedName copyFormatOption* commentClause? SEMI?
    | CREATE or_replace? TAG if_not_exists? qualifiedName tagProperties? commentClause? SEMI?
    | CREATE or_replace? SECURE? FUNCTION if_not_exists? qualifiedName LPAREN parameterList? RPAREN RETURNS returnType functionOption* (AS bodyDefinition)? SEMI?
    | CREATE or_replace? PROCEDURE if_not_exists? qualifiedName LPAREN parameterList? RPAREN RETURNS returnType languageClause? runtimeVersionClause? packagesClause? importsClause? handlerClause? commentClause? executeAsClause? (AS bodyDefinition)?  SEMI?
    | CREATE or_replace? USER if_not_exists? identifier userProperties? commentClause? SEMI?
    | CREATE or_replace? ROLE if_not_exists? identifier commentClause? SEMI?
    | CREATE or_replace? MASKING POLICY if_not_exists? qualifiedName AS LPAREN parameterList RPAREN RETURNS dataTypeName typeParameters? THIN_ARROW (bodyDefinition | booleanExpr) commentClause? SEMI?
    | CREATE or_replace? ROW ACCESS POLICY if_not_exists? qualifiedName AS LPAREN parameterList RPAREN RETURNS BOOLEAN THIN_ARROW (bodyDefinition | booleanExpr) commentClause? SEMI?
    ;

commentClause
    : COMMENT EQ? STRING_LITERAL
    ;

executeAsClause
    : EXECUTE AS (OWNER | CALLER)
    ;

nullHandlingClause
    : CALLED ON NULL INPUT
    | RETURNS NULL ON NULL INPUT
    | STRICT
    ;

volatilityClause
    : IMMUTABLE
    | VOLATILE
    ;

functionOption
    : languageClause
    | runtimeVersionClause
    | packagesClause
    | importsClause
    | handlerClause
    | nullHandlingClause
    | volatilityClause
    | commentClause
    ;

importsClause
    : IMPORTS EQ LPAREN stringLiteralList RPAREN
    ;

collateClause
    : COLLATE STRING_LITERAL
    ;

clusterByClause
    : CLUSTER BY LPAREN expressionList RPAREN
    ;

languageClause
    : LANGUAGE (JAVASCRIPT | JAVA | SQL | PYTHON | SCALA)
    ;

runtimeVersionClause
    : RUNTIME_VERSION EQ (STRING_LITERAL | FLOAT_LITERAL | INTEGER_LITERAL)
    ;

handlerClause
    : HANDLER EQ STRING_LITERAL
    ;

packagesClause
    : PACKAGES EQ LPAREN stringLiteralList RPAREN
    ;

dropStatement
    : DROP DATABASE if_exists? identifier SEMI?
    | DROP SCHEMA if_exists? qualifiedName SEMI?
    | DROP TABLE if_exists? objectName SEMI?
    | DROP VIEW if_exists? qualifiedName SEMI?
    | DROP MATERIALIZED VIEW if_exists? qualifiedName SEMI?
    | DROP DYNAMIC TABLE if_exists? qualifiedName SEMI?
    | DROP STREAM if_exists? qualifiedName SEMI?
    | DROP TASK if_exists? qualifiedName SEMI?
    | DROP PIPE if_exists? qualifiedName SEMI?
    | DROP SEQUENCE if_exists? qualifiedName SEMI?
    | DROP WAREHOUSE if_exists? identifier SEMI?
    | DROP STAGE if_exists? qualifiedName SEMI?
    | DROP FILE FORMAT if_exists? qualifiedName SEMI?
    | DROP TAG if_exists? qualifiedName SEMI?
    | DROP FUNCTION if_exists? qualifiedName (LPAREN dataTypeList? RPAREN)? SEMI?
    | DROP PROCEDURE if_exists? qualifiedName (LPAREN dataTypeList? RPAREN)? SEMI?
    | DROP USER if_exists? identifier SEMI?
    | DROP ROLE if_exists? identifier SEMI?
    | DROP MASKING POLICY if_exists? qualifiedName SEMI?
    | DROP ROW ACCESS POLICY if_exists? qualifiedName SEMI?
    ;

alterStatement
    : ALTER DATABASE if_exists? identifier databaseAction SEMI?
    | ALTER SCHEMA if_exists? qualifiedName schemaAction SEMI?
    | ALTER TABLE if_exists? qualifiedName tableAction SEMI?
    | ALTER VIEW if_exists? qualifiedName viewAction SEMI?
    | ALTER MATERIALIZED VIEW if_exists? qualifiedName materializedViewAction SEMI?
    | ALTER DYNAMIC TABLE if_exists? qualifiedName dynamicTableAction SEMI?
    | ALTER STREAM if_exists? qualifiedName streamAction SEMI?
    | ALTER TASK if_exists? qualifiedName taskAction SEMI?
    | ALTER PIPE if_exists? qualifiedName pipeAction SEMI?
    | ALTER SEQUENCE if_exists? qualifiedName sequenceAction SEMI?
    | ALTER WAREHOUSE if_exists? identifier warehouseAction SEMI?
    | ALTER STAGE if_exists? qualifiedName stageAction SEMI?
    | ALTER FILE FORMAT if_exists? qualifiedName fileFormatAction SEMI?
    | ALTER TAG if_exists? qualifiedName tagAction SEMI?
    | ALTER MASKING POLICY if_exists? qualifiedName maskingPolicyAction SEMI?
    | ALTER ROW ACCESS POLICY if_exists? qualifiedName rowAccessPolicyAction SEMI?
    | ALTER USER if_exists? identifier userAction SEMI?
    | ALTER ROLE if_exists? identifier roleAction SEMI?
    | ALTER FUNCTION if_exists? qualifiedName (LPAREN dataTypeList? RPAREN)? routineAlterAction SEMI?
    | ALTER PROCEDURE if_exists? qualifiedName (LPAREN dataTypeList? RPAREN)? routineAlterAction SEMI?
    | ALTER SESSION sessionAction SEMI?
    ;

useStatement
    : USE DATABASE objectName SEMI?
    | USE SCHEMA objectName SEMI?
    | USE WAREHOUSE objectName SEMI?
    | USE ROLE objectName SEMI?
    | USE SECONDARY ROLES (ALL | identifier) SEMI?
    ;

// An object name that may be given literally or resolved dynamically via IDENTIFIER('<name>') / IDENTIFIER($var).
objectName
    : KW_IDENTIFIER LPAREN expression RPAREN
    | qualifiedName
    ;

commentStatement
    : COMMENT if_exists? ON DATABASE identifier IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON SCHEMA qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON TABLE qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON COLUMN qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON VIEW qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON MATERIALIZED VIEW qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON FUNCTION qualifiedName LPAREN dataTypeList? RPAREN IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON PROCEDURE qualifiedName LPAREN dataTypeList? RPAREN IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON STREAM qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON TASK qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON WAREHOUSE identifier IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON STAGE qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON TAG qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON USER identifier IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON ROLE identifier IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON SEQUENCE qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON PIPE qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON MASKING POLICY qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON ROW ACCESS POLICY qualifiedName IS STRING_LITERAL SEMI?
    | COMMENT if_exists? ON FILE FORMAT qualifiedName IS STRING_LITERAL SEMI?
    ;

truncateStatement
    : TRUNCATE TABLE? if_exists? qualifiedName SEMI?
    ;

securityStatement
    : grantStatement
    | revokeStatement
    ;

taskStatement
    : EXECUTE TASK qualifiedName SEMI?
    ;

grantStatement
    : GRANT ROLE identifier TO (USER | ROLE) identifier SEMI?  // GRANT ROLE role_name TO USER/ROLE target_name
    | GRANT globalPrivilegeList TO ROLE identifier SEMI?  // GRANT global_privs TO ROLE role_name
    | GRANT privilegeList ON objectType qualifiedName (LPAREN identifierList RPAREN)? TO (USER | ROLE) identifier SEMI?  // GRANT privs ON type name [(col1, col2)] TO USER/ROLE target_name
    | GRANT OWNERSHIP ON objectType qualifiedName TO (USER | ROLE) identifier SEMI?  // GRANT OWNERSHIP ON type name TO USER/ROLE target_name
    | GRANT privilegeList ON ACCOUNT TO (USER | ROLE) identifier SEMI?  // GRANT privs ON ACCOUNT TO USER/ROLE target_name
    | GRANT privilegeList ON ALL bulkObjectType IN bulkScope TO (USER | ROLE) identifier SEMI?  // GRANT privs ON ALL <types> IN <scope>
    | GRANT privilegeList ON FUTURE bulkObjectType IN bulkScope TO (USER | ROLE) identifier SEMI?  // GRANT privs ON FUTURE <types> IN <scope>
    ;

bulkObjectType
    : SCHEMAS
    | TABLES
    | VIEWS
    | FUNCTIONS
    | PROCEDURES
    | STAGES
    | SEQUENCES
    | TASKS
    | STREAMS
    | PIPES
    ;

bulkScope
    : DATABASE qualifiedName
    | SCHEMA qualifiedName
    | ACCOUNT
    ;

revokeStatement
    : REVOKE ROLE identifier FROM (USER | ROLE) identifier SEMI?  // REVOKE ROLE role_name FROM USER/ROLE target_name
    | REVOKE globalPrivilegeList FROM ROLE identifier SEMI?  // REVOKE global_privs FROM ROLE role_name
    | REVOKE privilegeList ON objectType qualifiedName (LPAREN identifierList RPAREN)? FROM (USER | ROLE) identifier SEMI?  // REVOKE privs ON type name [(col1, col2)] FROM USER/ROLE target_name
    | REVOKE OWNERSHIP ON objectType qualifiedName FROM (USER | ROLE) identifier SEMI?  // REVOKE OWNERSHIP ON type name FROM USER/ROLE target_name
    | REVOKE privilegeList ON ACCOUNT FROM (USER | ROLE) identifier SEMI?  // REVOKE privs ON ACCOUNT FROM USER/ROLE target_name
    ;

privilegeList
    : privilege (COMMA privilege)*
    | ALL PRIVILEGES?
    ;

globalPrivilegeList
    : globalPrivilege (COMMA globalPrivilege)*
    ;

globalPrivilege
    : CREATE ACCOUNT
    | CREATE DATABASE
    | CREATE INTEGRATION
    | CREATE NETWORK POLICY
    | CREATE ROLE
    | CREATE USER
    | CREATE WAREHOUSE
    | APPLY MASKING POLICY
    | APPLY ROW ACCESS POLICY
    | APPLY SESSION POLICY
    | APPLY TAG
    | EXECUTE TASK
    | IMPORT SHARE
    | MANAGE GRANTS
    | MONITOR EXECUTION
    | MONITOR USAGE
    ;

privilege
    : SELECT
    | INSERT
    | UPDATE
    | DELETE
    | TRUNCATE
    | CREATE
    | DROP
    | ALTER
    | MODIFY
    | USAGE
    | OPERATE
    | MONITOR
    | READ
    | WRITE
    | EXECUTE
    | REFERENCES
    | OWNERSHIP
    | APPLY
    | CREATE SCHEMA
    | CREATE TABLE
    | CREATE VIEW
    | CREATE STAGE
    | CREATE FILE_FORMAT
    | CREATE SEQUENCE
    | CREATE FUNCTION
    | CREATE PROCEDURE
    | CREATE PIPE
    | CREATE STREAM
    | CREATE TASK
    | CREATE MASKING POLICY
    | CREATE ROW ACCESS POLICY
    | CREATE TAG
    | IMPORTED PRIVILEGES
    | USE_ANY_ROLE
    ;

objectType
    : DATABASE
    | SCHEMA
    | TABLE
    | VIEW
    | WAREHOUSE
    | STAGE
    | STREAM
    | TASK
    | SEQUENCE
    | PIPE
    | PROCEDURE
    | FUNCTION
    | FILE_FORMAT
    | INTEGRATION
    | RESOURCE_MONITOR
    | MASKING_POLICY
    | ROW_ACCESS_POLICY
    | SESSION_POLICY
    | TAG
    ;

userProperties
    : userProperty+
    ;

userProperty
    : PASSWORD EQ STRING_LITERAL
    | DEFAULT_ROLE EQ (identifier | STRING_LITERAL)
    ;

streamOptions
    : streamOption (streamOption)*
    ;

streamOption
    : APPEND_ONLY EQ booleanValue
    | SHOW_INITIAL_ROWS EQ booleanValue
    ;

parameterList
    : parameterDef (COMMA parameterDef)*
    ;

parameterDef
    : identifier dataTypeName typeParameters? ((DEFAULT | COLON_EQ) expression)?
    ;

dataTypeList
    : dataTypeName typeParameters? (COMMA dataTypeName typeParameters?)*
    ;

returnType
    : TABLE LPAREN columnList RPAREN    // Table function
    | dataTypeName typeParameters? (NOT NULL)?   // Scalar function; NOT NULL is informational
    ;

bodyDefinition
    : STRING_LITERAL              // Single-quoted string
    | DOLLAR_QUOTED_STRING        // Dollar-quoted string ($$...$$)
    | beginEndBlock               // Unquoted scripting body: CREATE PROCEDURE ... AS BEGIN...END / AS DECLARE...END
    ;

taskBody
    : sqlStatement                // Raw SQL statement
    | executeImmediateStatement   // EXECUTE IMMEDIATE expression
    | bodyDefinition              // String literal (single or dollar-quoted)
    ;

warehouseClause
    : WAREHOUSE EQ (STRING_LITERAL | identifier | SESSION_VAR_REF)
    ;

scheduleClause
    : SCHEDULE EQ STRING_LITERAL
    ;

afterClause
    : AFTER qualifiedName (COMMA qualifiedName)*
    ;

taskOptions
    : taskOption+
    ;

taskOption
    : ALLOW_OVERLAPPING_EXECUTION EQ booleanValue
    | USER_TASK_TIMEOUT_MS EQ INTEGER_LITERAL
    | SUSPEND_TASK_AFTER_NUM_FAILURES EQ INTEGER_LITERAL
    | TASK_AUTO_RETRY_ATTEMPTS EQ INTEGER_LITERAL
    | USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE EQ STRING_LITERAL
    | SERVERLESS_TASK_MAX_STATEMENT_SIZE EQ identifier
    | TARGET_COMPLETION_INTERVAL EQ STRING_LITERAL
    | USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS EQ INTEGER_LITERAL
    | ERROR_INTEGRATION EQ identifier
    | COMMENT EQ? STRING_LITERAL
    | scheduleClause
    ;

pipeOptions
    : pipeOption+
    ;

pipeOption
    : AUTO_INGEST EQ booleanValue
    | AWS_SNS_TOPIC EQ STRING_LITERAL
    | ERROR_INTEGRATION EQ STRING_LITERAL
    | INTEGRATION EQ STRING_LITERAL
    ;

copyIntoStatement
    : COPY INTO qualifiedName copyIntoTableClause* SEMI?
    | COPY INTO stageRef copyIntoStageClause* SEMI?
    | COPY INTO STRING_LITERAL copyIntoStageClause* SEMI?
    ;

copyIntoTableClause
    : LPAREN identifierList RPAREN
    | FROM copySource
    | FILE_FORMAT EQ LPAREN copyFormatOptions RPAREN
    | FILES EQ LPAREN stringLiteralList RPAREN
    | PATTERN EQ STRING_LITERAL
    | VALIDATION_MODE EQ copyOptionValue
    | ON_ERROR EQ copyOptionValue
    | SIZE_LIMIT EQ INTEGER_LITERAL
    | PURGE EQ booleanValue
    | FORCE EQ booleanValue
    | MATCH_BY_COLUMN_NAME EQ copyOptionValue
    | identifier EQ copyOptionValue
    ;

copyIntoStageClause
    : FROM copyTableSource
    | FILE_FORMAT EQ LPAREN copyFormatOptions RPAREN
    | PARTITION BY expression
    | HEADER (EQ booleanValue)?
    | OVERWRITE EQ booleanValue
    | SINGLE EQ booleanValue
    | MAX_FILE_SIZE EQ INTEGER_LITERAL
    | VALIDATION_MODE EQ copyOptionValue
    | identifier EQ copyOptionValue
    ;

copySource
    : stageRef
    | STRING_LITERAL
    | LPAREN copyTransformation RPAREN
    ;

// COPY load transformation: a projection over the staged file's positional columns ($1, $2, …) FROM @stage.
// Only the projection + stage source are modelled (no WHERE/JOIN/aggregates), matching Snowflake's COPY transform.
copyTransformation
    : SELECT copyTransformItem (COMMA copyTransformItem)* FROM stageRef
    ;

copyTransformItem
    : expression (AS identifier)?
    ;

// A stage reference: a named stage (@stage), the current user's stage (@~), or a table stage (@%table),
// each with an optional path beneath it.
stageRef
    : AT identifier stagePath?
    | AT TILDE stagePath?
    | AT PERCENT identifier stagePath?
    ;

stagePath
    : ((SLASH | DOT) (identifier | INTEGER_LITERAL))+
    ;

copyTableSource
    : qualifiedName
    | LPAREN selectStatement RPAREN
    ;

stringLiteralList
    : STRING_LITERAL (COMMA STRING_LITERAL)*
    ;

copyFormatOptions
    : copyFormatOption (COMMA? copyFormatOption)*
    ;

copyFormatOption
    : TYPE EQ copyOptionValue
    | FIELD_DELIMITER EQ copyOptionValue
    | SKIP_HEADER EQ INTEGER_LITERAL
    | DATE_FORMAT EQ copyOptionValue
    | COMPRESSION EQ copyOptionValue
    | RECORD_DELIMITER EQ copyOptionValue
    | ESCAPE EQ copyOptionValue
    // A parenthesized string list, e.g. NULL_IF = ('\\N', 'NULL', '') — must precede the scalar catch-all.
    | identifier EQ LPAREN stringLiteralList? RPAREN
    | identifier EQ copyOptionValue
    ;

// A copy option value: a quoted string, an unquoted enum identifier (TYPE = CSV, COMPRESSION = GZIP),
// the keyword-valued enums CONTINUE/AUTO, an integer, or a boolean — matching Snowflake's unquoted forms.
copyOptionValue
    : STRING_LITERAL
    | INTEGER_LITERAL
    | booleanValue
    | CONTINUE
    | AUTO
    | identifier
    ;

copyStatement
    : COPY INTO qualifiedName copyRestOfStatement
    ;

copyRestOfStatement
    : ~SEMI*
    ;

warehouseProperties
    : WITH? warehouseProperty (warehouseProperty)*
    ;

warehouseProperty
    : WAREHOUSE_TYPE EQ (STANDARD | STRING_LITERAL)
    | WAREHOUSE_SIZE EQ (STRING_LITERAL | identifier | SESSION_VAR_REF)
    | AUTO_SUSPEND EQ INTEGER_LITERAL
    | AUTO_RESUME EQ booleanValue
    | MIN_CLUSTER_COUNT EQ INTEGER_LITERAL
    | MAX_CLUSTER_COUNT EQ INTEGER_LITERAL
    | SCALING_POLICY EQ (STANDARD | ECONOMY)
    | INITIALLY_SUSPENDED EQ booleanValue
    | RESOURCE_MONITOR EQ identifier
    | MAX_CONCURRENCY_LEVEL EQ INTEGER_LITERAL
    | STATEMENT_QUEUED_TIMEOUT_IN_SECONDS EQ INTEGER_LITERAL
    | STATEMENT_TIMEOUT_IN_SECONDS EQ INTEGER_LITERAL
    | ENABLE_QUERY_ACCELERATION EQ booleanValue
    | QUERY_ACCELERATION_MAX_SCALE_FACTOR EQ INTEGER_LITERAL
    | COMMENT EQ STRING_LITERAL
    ;

stageProperties
    : URL EQ STRING_LITERAL stageOption*
    ;

stageOption
    : FILE_FORMAT EQ STRING_LITERAL
    | ENCRYPTION EQ booleanValue
    | COMMENT EQ STRING_LITERAL
    ;

taskAction
    : RESUME
    | SUSPEND
    | SET (taskOption | warehouseClause)+
    | UNSET taskParamName (COMMA taskParamName)*
    | MODIFY AS taskBody
    | MODIFY WHEN booleanExpr
    ;

// Parameter names that ALTER TASK … UNSET accepts (keyword tokens plus a generic identifier fallback).
taskParamName
    : WAREHOUSE | SCHEDULE | COMMENT | USER_TASK_TIMEOUT_MS | SUSPEND_TASK_AFTER_NUM_FAILURES
    | TASK_AUTO_RETRY_ATTEMPTS | USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE | SERVERLESS_TASK_MAX_STATEMENT_SIZE
    | TARGET_COMPLETION_INTERVAL | USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS | ERROR_INTEGRATION
    | ALLOW_OVERLAPPING_EXECUTION | identifier
    ;

pipeAction
    : RESUME
    | PAUSE
    | REFRESH pipeRefreshOption*
    | SET pipeSetOption+
    ;

// Option names are identifiers (e.g. PIPE_EXECUTION_PAUSED, PREFIX, MODIFIED_AFTER) so no new keyword
// tokens are needed; the handler validates the name.
pipeSetOption
    : identifier EQ (booleanValue | STRING_LITERAL)
    ;

pipeRefreshOption
    : identifier EQ STRING_LITERAL
    ;

sequenceOptions
    : sequenceOption+
    ;

sequenceOption
    : START (WITH | EQ)? MINUS? INTEGER_LITERAL
    | INCREMENT (BY | EQ)? MINUS? INTEGER_LITERAL
    | ORDER
    | NOORDER
    ;

sequenceAction
    : SET INCREMENT (BY | EQ) MINUS? INTEGER_LITERAL
    | RESTART (WITH MINUS? INTEGER_LITERAL)?
    ;

tagAssign
    : qualifiedName EQ STRING_LITERAL
    ;

tagSet
    : SET TAG tagAssign (COMMA tagAssign)*
    ;

tagUnset
    : UNSET TAG qualifiedName (COMMA qualifiedName)*
    ;

columnTagAction
    : ALTER COLUMN identifier (tagSet | tagUnset)
    ;

warehouseAction
    : RESUME
    | SUSPEND
    | SET warehouseProperty+
    | RENAME TO? identifier
    | tagSet
    | tagUnset
    ;

databaseAction
    : RENAME TO? identifier
    | SET COMMENT EQ STRING_LITERAL
    | SET READ_ONLY EQ booleanValue
    | UNSET READ_ONLY
    | tagSet
    | tagUnset
    ;

schemaAction
    : RENAME TO? identifier
    | SET COMMENT EQ STRING_LITERAL
    | tagSet
    | tagUnset
    ;

tableAction
    : RENAME TO? identifier
    | SWAP WITH qualifiedName
    | ADD COLUMN if_not_exists? columnDef
    | DROP COLUMN if_exists? identifier
    | RENAME COLUMN identifier TO? identifier
    | ALTER COLUMN identifier ((SET DATA)? TYPE)? dataTypeName typeParameters?
    | ALTER COLUMN identifier SET NOT NULL
    | ALTER COLUMN identifier DROP NOT NULL
    | ALTER COLUMN identifier SET DEFAULT defaultExpression
    | ALTER COLUMN identifier DROP DEFAULT
    | SET COMMENT EQ STRING_LITERAL
    | CLUSTER BY LPAREN expressionList RPAREN
    | ADD tableConstraint
    | DROP CONSTRAINT identifier
    | ALTER COLUMN identifier SET MASKING POLICY qualifiedName (USING LPAREN identifierList RPAREN)?
    | ALTER COLUMN identifier UNSET MASKING POLICY
    | ADD ROW ACCESS POLICY qualifiedName ON LPAREN identifierList RPAREN
    | DROP ROW ACCESS POLICY qualifiedName
    | tagSet
    | tagUnset
    | columnTagAction
    ;

viewAction
    : RENAME TO? identifier
    | SET COMMENT EQ STRING_LITERAL
    | tagSet
    | tagUnset
    ;

materializedViewAction
    : SUSPEND
    | RESUME
    | REFRESH
    | RENAME TO? identifier
    | SET COMMENT EQ STRING_LITERAL
    ;

dynamicTableOptions
    : dynamicTableOption+
    ;

dynamicTableOption
    : TARGET_LAG EQ (STRING_LITERAL | DOWNSTREAM)
    | WAREHOUSE EQ identifier
    | REFRESH_MODE EQ (AUTO | FULL | INCREMENTAL)
    | INITIALIZE EQ (ON_CREATE | ON_SCHEDULE)
    | DATA_RETENTION_TIME_IN_DAYS EQ INTEGER_LITERAL
    | MAX_FILE_SIZE EQ INTEGER_LITERAL
    | COMMENT EQ STRING_LITERAL
    ;

dynamicTableAction
    : SUSPEND
    | RESUME
    | REFRESH
    | SET TARGET_LAG EQ (STRING_LITERAL | DOWNSTREAM)
    | SET WAREHOUSE EQ identifier
    | SET REFRESH_MODE EQ (AUTO | FULL | INCREMENTAL)
    | SET DATA_RETENTION_TIME_IN_DAYS EQ INTEGER_LITERAL
    | SET COMMENT EQ STRING_LITERAL
    ;

streamAction
    : SET COMMENT EQ STRING_LITERAL
    | UNSET COMMENT
    ;

stageAction
    : SET URL EQ STRING_LITERAL
    | SET FILE_FORMAT EQ STRING_LITERAL
    | SET COMMENT EQ STRING_LITERAL
    ;

tagProperties
    : ALLOWED_VALUES stringLiteralList
    | MASKING EQ booleanValue
    ;

tagAction
    : ADD ALLOWED_VALUES stringLiteralList
    | DROP ALLOWED_VALUES stringLiteralList
    | UNSET ALLOWED_VALUES
    | SET MASKING EQ booleanValue
    | SET COMMENT EQ STRING_LITERAL
    | RENAME TO? identifier
    ;

maskingPolicyAction
    : RENAME TO? identifier
    ;

rowAccessPolicyAction
    : RENAME TO? identifier
    ;

fileFormatAction
    : RENAME TO? qualifiedName
    | SET? copyFormatOption+
    ;

routineAlterAction
    : RENAME TO qualifiedName
    | SET COMMENT EQ STRING_LITERAL
    | UNSET COMMENT
    ;

userAction
    : RENAME TO? identifier
    | SET PASSWORD EQ STRING_LITERAL
    | SET DEFAULT_ROLE EQ identifier
    | SET COMMENT EQ STRING_LITERAL
    ;

roleAction
    : RENAME TO? identifier
    | SET COMMENT EQ STRING_LITERAL
    ;

sessionAction
    : SET sessionParameter EQ literal
    ;

sessionParameter
    : MULTI_STATEMENT_COUNT
    | identifier
    ;

booleanValue
    : TRUE
    | FALSE
    ;

sqlStatement
    : dmlStatement
    | selectStatement
    ;

columnList
    : columnOrConstraint (COMMA columnOrConstraint)*
    ;

columnOrConstraint
    : columnDef
    | tableConstraint
    ;

columnDef
    : identifier dataTypeName typeParameters?  columnConstraint* commentClause?
    ;

typeParameters
    : LPAREN INTEGER_LITERAL (COMMA INTEGER_LITERAL)? RPAREN
    ;

tableConstraint
    : constraintName? PRIMARY KEY LPAREN identifierList RPAREN relyOption?
    | constraintName? UNIQUE LPAREN identifierList RPAREN relyOption?
    | constraintName? FOREIGN KEY LPAREN identifierList RPAREN REFERENCES qualifiedName LPAREN identifierList RPAREN referentialActions? relyOption?
    ;

constraintName
    : CONSTRAINT identifier
    ;

referentialActions
    : referentialAction+
    ;

referentialAction
    : ON DELETE referentialOption
    | ON UPDATE referentialOption
    ;

referentialOption
    : CASCADE
    | SET NULL
    | SET DEFAULT
    | RESTRICT
    | NO ACTION
    ;

// Date/time type keywords usable as a typed-literal prefix, e.g. DATE '2020-01-15' or TIMESTAMP '…'.
dateTimeLiteralType
    : DATE | DATETIME | TIMESTAMP | TIMESTAMP_NTZ | TIMESTAMPNTZ | TIMESTAMP_LTZ | TIMESTAMP_TZ
    ;

dataTypeName
    : INTEGER | INT | BIGINT | SMALLINT | TINYINT | BYTEINT | NUMBER | DECIMAL | FLOAT | FLOAT4 | FLOAT8 | DOUBLE PRECISION? | REAL
    | VARCHAR | STRING | TEXT | CHAR | BOOLEAN | DATE | DATETIME | TIMESTAMP | TIMESTAMP_NTZ | TIMESTAMPNTZ | TIMESTAMP_LTZ | TIMESTAMP_TZ | VARIANT
    | ARRAY | OBJECT | BINARY | VARBINARY
    | UUID
    | VECTOR LPAREN (FLOAT | INT) COMMA INTEGER_LITERAL RPAREN
    ;

columnConstraint
    : PRIMARY KEY relyOption?
    | NOT? NULL relyOption?
    | UNIQUE relyOption?
    | AUTOINCREMENT identityProperties?
    | IDENTITY identityProperties?
    | DEFAULT defaultExpression
    | REFERENCES qualifiedName LPAREN identifier RPAREN referentialActions? relyOption?
    | FOREIGN KEY REFERENCES qualifiedName LPAREN identifier RPAREN referentialActions? relyOption?
    | collateClause
    ;

// AUTOINCREMENT / IDENTITY seed + step: (start, step) or START <n> INCREMENT <n>, with optional ORDER/NOORDER.
identityProperties
    : LPAREN INTEGER_LITERAL COMMA INTEGER_LITERAL RPAREN (ORDER | NOORDER)?
    | START INTEGER_LITERAL INCREMENT INTEGER_LITERAL (ORDER | NOORDER)?
    | (ORDER | NOORDER)
    ;

defaultExpression
    : expression
    ;

relyOption
    : RELY
    | NORELY
    ;

dmlStatement
    : insertStatement
    | multiTableInsertStatement
    | updateStatement
    | deleteStatement
    | mergeStatement
    | copyIntoStatement
    ;

insertStatement
    : withClause? INSERT OVERWRITE? INTO objectName columnListOptional? VALUES valueTupleList SEMI?
    | withClause? INSERT OVERWRITE? INTO objectName columnListOptional? selectStatement SEMI?
    ;

// Snowflake multi-table INSERT: unconditional (INSERT [OVERWRITE] ALL INTO ...) and
// conditional (INSERT [OVERWRITE] {FIRST | ALL} WHEN cond THEN INTO ... [ELSE INTO ...]).
multiTableInsertStatement
    : INSERT OVERWRITE? ALL multiInsertInto+ selectStatement SEMI?
    | INSERT OVERWRITE? (FIRST | ALL) multiInsertWhen+ multiInsertElse? selectStatement SEMI?
    ;

multiInsertInto
    : INTO qualifiedName columnListOptional? (VALUES LPAREN expressionList RPAREN)?
    ;

multiInsertWhen
    : WHEN booleanExpr THEN multiInsertInto+
    ;

multiInsertElse
    : ELSE multiInsertInto+
    ;

columnListOptional
    : LPAREN identifierList RPAREN
    ;

identifierList
    : identifier (COMMA identifier)*
    ;

viewColumnList
    : viewColumnDef (COMMA viewColumnDef)*
    ;

viewColumnDef
    : identifier commentClause?
    ;

valueTupleList
    : valueTuple (COMMA valueTuple)*
    ;

valueTuple
    : LPAREN valueList RPAREN
    ;

valueList
    : expression (COMMA expression)*
    ;

updateStatement
    : withClause? UPDATE objectName SET assignmentList (FROM tableReference (COMMA tableReference)*)? whereClause? SEMI?
    ;

deleteStatement
    : withClause? DELETE FROM objectName (USING tableReference (COMMA tableReference)*)? whereClause? SEMI?
    ;

mergeStatement
    : withClause? MERGE INTO qualifiedName (AS? identifier)?
      USING mergeSource (AS? identifier (LPAREN identifierList RPAREN)?)?
      ON booleanExpr
      mergeClause+
      SEMI?
    ;

mergeSource
    : qualifiedName                              # MergeSourceTable
    | LPAREN VALUES valueTupleList RPAREN       # MergeSourceValues
    | LPAREN selectStatement RPAREN             # MergeSourceSubquery
    ;

mergeClause
    : WHEN MATCHED (AND booleanExpr)? THEN UPDATE SET assignmentList
    | WHEN MATCHED (AND booleanExpr)? THEN DELETE
    | WHEN NOT MATCHED (AND booleanExpr)? THEN INSERT columnListOptional? VALUES valueTuple
    ;

assignmentList
    : assignment (COMMA assignment)*
    ;

assignment
    : (identifier '.')? identifier EQ expression
    ;

queryStatement
    : selectStatement SEMI?
    ;

// EXPLAIN [USING { TABULAR | JSON | TEXT }] <query|dml> — returns the structural execution plan as a result set.
explainStatement
    : EXPLAIN (USING identifier)? (selectStatement | dmlStatement) SEMI?
    ;

selectStatement
    : withClause? selectOperand (setOperator selectOperand)* orderByClause? (limitClause | fetchClause)?
    ;

selectOperand
    : selectClause
    | LPAREN selectStatement RPAREN
    ;

withClause
    : WITH RECURSIVE? cteDefinition (COMMA cteDefinition)*
    ;

cteDefinition
    : identifier columnListOptional? AS LPAREN selectStatement RPAREN
    ;

selectClause
    : SELECT DISTINCT? topClause? selectList (FROM tableExpression whereClause? groupByClause? havingClause? qualifyClause?)? whereClause?
    ;

setOperator
    : UNION ALL?
    | INTERSECT ALL?
    | EXCEPT ALL?
    | MINUS_KW ALL?   // MINUS is a Snowflake synonym for EXCEPT
    ;

tableExpression
    : tableReference (COMMA tableReference | joinClause)*
    ;

tableReference
    : LATERAL? tableSource (AS identifier (LPAREN identifierList RPAREN)? | nonJoinKeywordIdentifier (LPAREN identifierList RPAREN)?)? sampleClause?
    ;

sampleClause
    : (SAMPLE | TABLESAMPLE) sampleMethod? LPAREN (INTEGER_LITERAL | FLOAT_LITERAL) ROWS? RPAREN sampleSeed?
    ;

sampleMethod
    : BERNOULLI | ROW | SYSTEM
    ;

sampleSeed
    : (REPEATABLE | SEED) LPAREN INTEGER_LITERAL RPAREN
    ;

tableSource
    : TABLE LPAREN expression RPAREN    // Table function call
    | FLATTEN LPAREN flattenArgList RPAREN  // LATERAL FLATTEN(expr [, name => val ...])
    | KW_IDENTIFIER LPAREN expression RPAREN  // IDENTIFIER(expr) — dynamic table name
    | qualifiedName timeTravelClause? pivotClause?
    | qualifiedName timeTravelClause? unpivotClause?
    | qualifiedName timeTravelClause?
    | LPAREN selectStatement RPAREN
    | LPAREN VALUES valueTupleList RPAREN  // (VALUES (...), (...)) as subquery
    | VALUES valueTupleList                // Inline values
    ;

// FLATTEN argument list: positional INPUT followed by optional named params, or all named
flattenArgList
    : expression (COMMA namedArgument)*   // positional INPUT [, name => val ...]
    | namedArgumentList                   // all named
    ;

timeTravelClause
    : AT_KEYWORD LPAREN timeTravelPoint RPAREN
    | BEFORE LPAREN timeTravelPoint RPAREN
    | changesClause
    ;

timeTravelPoint
    : TIMESTAMP ARROW expression
    | OFFSET ARROW expression
    | STATEMENT ARROW expression
    ;

changesClause
    : CHANGES LPAREN INFORMATION ARROW identifier RPAREN
      (AT_KEYWORD LPAREN timeTravelPoint RPAREN | BEFORE LPAREN timeTravelPoint RPAREN)?
      (END LPAREN timeTravelPoint RPAREN)?
    ;

joinClause
    : NATURAL? joinType? JOIN LATERAL? tableReference (ON booleanExpr | USING LPAREN identifierList RPAREN)?
    ;

joinType
    : INNER
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    | CROSS
    ;

pivotClause
    : PIVOT LPAREN aggregateFunction FOR identifier IN LPAREN pivotValueList RPAREN RPAREN
    ;

unpivotClause
    : UNPIVOT unpivotNulls? LPAREN identifier FOR identifier IN LPAREN unpivotColumnList RPAREN RPAREN
    ;

unpivotNulls
    : INCLUDE NULLS
    | EXCLUDE NULLS
    ;

aggregateFunction
    : functionName LPAREN DISTINCT? expression RPAREN
    ;

pivotValueList
    : pivotValue (COMMA pivotValue)*
    ;

pivotValue
    : literal (AS? identifier)?
    ;

unpivotColumnList
    : identifier (COMMA identifier)*
    ;

selectList
    : selectItem (COMMA selectItem)* COMMA?
    ;

selectItem
    : STAR starModifier*                                 # StarItem
    | qualifiedName DOT STAR starModifier*                # QualifiedStarItem
    | qualifiedName DOT DOUBLE_STAR                       # SpreadItem
    | DOUBLE_STAR qualifiedName                           # SpreadPrefixItem
    | booleanExpr (AS? identifier)?                       # ExprItem
    ;

// Snowflake column-list modifiers on a SELECT * : EXCLUDE, RENAME, REPLACE, ILIKE.
starModifier
    : ILIKE STRING_LITERAL
    | EXCLUDE (identifier | LPAREN identifier (COMMA identifier)* RPAREN)
    | RENAME (starRenameItem | LPAREN starRenameItem (COMMA starRenameItem)* RPAREN)
    | REPLACE LPAREN starReplaceItem (COMMA starReplaceItem)* RPAREN
    ;

starRenameItem
    : identifier AS? identifier
    ;

starReplaceItem
    : expression AS identifier
    ;

whereClause
    : WHERE booleanExpr
    ;

groupByClause
    : GROUP BY ALL
    | GROUP BY groupByElement (COMMA groupByElement)*
    ;

groupByElement
    : expression                                         // plain column/expression
    | ROLLUP LPAREN groupByColumnList RPAREN             // ROLLUP(a, b, c)
    | CUBE LPAREN groupByColumnList RPAREN               // CUBE(a, b, c)
    | GROUPING SETS LPAREN groupingSetList RPAREN        // GROUPING SETS((a,b),(a),(b),())
    ;

groupByColumnList
    : expression (COMMA expression)*
    ;

groupingSetList
    : groupingSet (COMMA groupingSet)*
    ;

groupingSet
    : LPAREN (expression (COMMA expression)*)? RPAREN   // (a, b) or ()
    | expression                                        // shorthand single column
    ;

havingClause
    : HAVING booleanExpr
    ;

qualifyClause
    : QUALIFY booleanExpr
    ;

overClause
    : OVER LPAREN partitionByClause? orderByClause? windowFrame? RPAREN
    ;

// Ordered-set aggregate qualifier: LISTAGG(...) / PERCENTILE_CONT(p) WITHIN GROUP (ORDER BY ...).
withinGroupClause
    : WITHIN GROUP LPAREN orderByClause RPAREN
    ;

// Null treatment for window value functions: FIRST_VALUE(x) { IGNORE | RESPECT } NULLS OVER (...).
nullHandling
    : (IGNORE | RESPECT) NULLS
    ;

windowFrame
    : (ROWS | RANGE) (BETWEEN frameBound AND frameBound | frameBound)
    ;

frameBound
    : UNBOUNDED PRECEDING
    | UNBOUNDED FOLLOWING
    | CURRENT ROW
    | expression PRECEDING
    | expression FOLLOWING
    ;

partitionByClause
    : PARTITION BY expressionList
    ;

orderByClause
    : ORDER BY orderItem (COMMA orderItem)*
    ;

orderItem
    : expression (ASC | DESC)? (NULLS (FIRST | LAST))?
    ;

topClause
    : TOP INTEGER_LITERAL
    ;

limitClause
    : LIMIT INTEGER_LITERAL (OFFSET INTEGER_LITERAL)?
    ;

fetchClause
    : FETCH (FIRST | NEXT) INTEGER_LITERAL (ROW | ROWS)? ONLY
    ;

transactionStatement
    : BEGIN TRANSACTION? SEMI?
    | COMMIT SEMI?
    | ROLLBACK SEMI?
    ;

listStatement
    : LIST AT qualifiedName (PATTERN EQ STRING_LITERAL)? SEMI?
    ;

// Stage file operations: PUT (local → stage), GET (stage → local), REMOVE/RM (delete staged files).
putStatement
    : PUT STRING_LITERAL stageRef stageFileOption* SEMI?
    ;

getStatement
    : GET stageRef STRING_LITERAL stageFileOption* SEMI?
    ;

removeStatement
    : (REMOVE | RM) stageRef (PATTERN EQ STRING_LITERAL)? SEMI?
    ;

stageFileOption
    : identifier EQ copyOptionValue
    ;

showStatement
    : SHOW DATABASES (LIKE STRING_LITERAL)? SEMI?
    | SHOW SCHEMAS (LIKE STRING_LITERAL)? (IN DATABASE identifier)? SEMI?
    | SHOW TABLES (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA)? qualifiedName)? SEMI?
    | SHOW VIEWS (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA)? qualifiedName)? SEMI?
    | SHOW MATERIALIZED VIEWS (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA)? qualifiedName)? SEMI?
    | SHOW DYNAMIC TABLES (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA)? qualifiedName)? SEMI?
    | SHOW COLUMNS (IN | FROM) TABLE? qualifiedName SEMI?
    | SHOW STREAMS (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA | ACCOUNT)? qualifiedName)? SEMI?
    | SHOW TASKS (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA | ACCOUNT)? qualifiedName)? SEMI?
    | SHOW PIPES (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA | ACCOUNT)? qualifiedName)? SEMI?
    | SHOW SEQUENCES (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA | ACCOUNT)? qualifiedName)? SEMI?
    | SHOW WAREHOUSES (LIKE STRING_LITERAL)? SEMI?
    | SHOW STAGES (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA | ACCOUNT)? qualifiedName)? SEMI?
    | SHOW FILE FORMATS (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA)? qualifiedName)? SEMI?
    | SHOW TAGS (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA | ACCOUNT)? qualifiedName)? SEMI?
    | SHOW USER? PROCEDURES (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA)? qualifiedName)? SEMI?
    | SHOW USER? FUNCTIONS (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA)? qualifiedName)? SEMI?
    | SHOW USERS (LIKE STRING_LITERAL)? SEMI?
    | SHOW ROLES (LIKE STRING_LITERAL)? SEMI?
    | SHOW GRANTS ON objectType identifier SEMI?
    | SHOW GRANTS TO (USER | ROLE) identifier SEMI?  // SHOW GRANTS TO USER/ROLE name
    | SHOW MASKING POLICIES (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA)? qualifiedName)? SEMI?
    | SHOW ROW ACCESS POLICIES (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA)? qualifiedName)? SEMI?
    | SHOW PARAMETERS (LIKE STRING_LITERAL)? (IN (SESSION | ACCOUNT | (DATABASE | SCHEMA | TABLE | WAREHOUSE | USER | ROLE) identifier))? SEMI?
    | SHOW SESSIONS (LIKE STRING_LITERAL)? SEMI?
    | SHOW OBJECTS (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA)? qualifiedName)? SEMI?
    | SHOW ORGANIZATION ACCOUNTS SEMI?
    | SHOW ACCOUNTS SEMI?
    | SHOW LOCKS (IN ACCOUNT)? SEMI?
    | SHOW TRANSACTIONS (LIKE STRING_LITERAL)? SEMI?
    | SHOW VARIABLES SEMI?
    | SHOW PRIMARY KEYS (IN TABLE qualifiedName)? SEMI?
    | SHOW UNIQUE KEYS (IN TABLE qualifiedName)? SEMI?
    ;

describeStatement
    : (DESCRIBE | DESC) TABLE qualifiedName SEMI?
    | (DESCRIBE | DESC) VIEW qualifiedName SEMI?
    | (DESCRIBE | DESC) MATERIALIZED VIEW qualifiedName SEMI?
    | (DESCRIBE | DESC) DYNAMIC TABLE qualifiedName SEMI?
    | (DESCRIBE | DESC) STREAM identifier SEMI?
    | (DESCRIBE | DESC) TASK identifier SEMI?
    | (DESCRIBE | DESC) PIPE identifier SEMI?
    | (DESCRIBE | DESC) SEQUENCE identifier SEMI?
    | (DESCRIBE | DESC) WAREHOUSE identifier SEMI?
    | (DESCRIBE | DESC) STAGE identifier SEMI?
    | (DESCRIBE | DESC) TAG identifier SEMI?
    | (DESCRIBE | DESC) FUNCTION qualifiedName (LPAREN dataTypeList? RPAREN)? SEMI?
    | (DESCRIBE | DESC) PROCEDURE qualifiedName (LPAREN dataTypeList? RPAREN)? SEMI?
    | (DESCRIBE | DESC) USER identifier SEMI?
    | (DESCRIBE | DESC) MASKING POLICY qualifiedName SEMI?
    | (DESCRIBE | DESC) ROW ACCESS POLICY qualifiedName SEMI?
    | (DESCRIBE | DESC) FILE FORMAT qualifiedName SEMI?
    | (DESCRIBE | DESC) RESULT (STRING_LITERAL | identifier LPAREN RPAREN) SEMI?
    ;

// Procedural Language Statements
proceduralStatement
    // beginEndBlock is listed BEFORE declareStatement so that a `DECLARE … BEGIN … END` parses as a
    // block whose DECLARE section is scoped to THAT block (Snowflake semantics), not as a standalone
    // DECLARE in the enclosing scope followed by a declare-less block. A leading DECLARE not followed
    // by BEGIN is not viable for beginEndBlock and falls through to declareStatement (Frostlake's
    // DECLARE-as-statement extension), preserving existing behavior.
    : beginEndBlock
    | declareStatement
    | letStatement
    | assignmentStatement
    | setStatement
    | ifStatement
    | caseStatement
    | loopStatement
    | whileStatement
    | repeatStatement
    | forStatement
    | returnStatement
    | breakStatement
    | continueStatement
    | raiseStatement
    | callStatement
    | executeImmediateStatement
    | openStatement
    | fetchStatement
    | closeStatement
    | selectIntoStatement
    | nullStatement
    ;

declareStatement
    : DECLARE declarationItem+
    ;

declarationItem
    : identifier (EXCEPTION LPAREN expression COMMA STRING_LITERAL RPAREN SEMI?
                 | CURSOR FOR cursorSource SEMI?
                 | RESULTSET (DEFAULT LPAREN selectStatement RPAREN)? SEMI?
                 | dataTypeName typeParameters? ((DEFAULT | COLON_EQ) expression)? SEMI?)
    ;

// A cursor's source is either a SELECT query or the name of a RESULTSET variable
// (Snowflake: `c CURSOR FOR res;` where `res RESULTSET DEFAULT (…)`).
cursorSource
    : selectStatement
    | identifier
    ;

exceptionDeclaration
    : identifier EXCEPTION LPAREN INTEGER_LITERAL COMMA STRING_LITERAL RPAREN SEMI?
    ;

cursorDeclaration
    : identifier CURSOR FOR selectStatement SEMI?
    ;

resultSetDeclaration
    : identifier RESULTSET (DEFAULT LPAREN selectStatement RPAREN)? SEMI?
    ;

variableDeclaration
    : identifier dataTypeName typeParameters? ((DEFAULT | COLON_EQ) expression)? SEMI?
    ;

letStatement
    : LET identifier CURSOR FOR selectStatement SEMI?
    | LET identifier dataTypeName typeParameters? (COLON_EQ | DEFAULT) expression SEMI?
    | LET identifier (COLON_EQ | DEFAULT) expression SEMI?
    ;

assignmentStatement
    : identifier COLON_EQ expression SEMI?
    | identifier COLON_EQ LPAREN callStatement RPAREN SEMI?
    ;

setStatement
    : SET identifier (EQ | COLON_EQ) expression SEMI?
    ;

// Top-level session SET/UNSET (outside procedural blocks)
// SET var = expr
// SET (var1, var2, ...) = (expr1, expr2, ...)
sessionSetStatement
    : SET identifier EQ expression SEMI?
    | SET LPAREN identifierList RPAREN EQ LPAREN expressionList RPAREN SEMI?
    ;

// UNSET var  |  UNSET (var1, var2, ...)
sessionUnsetStatement
    : UNSET identifier SEMI?
    | UNSET LPAREN identifierList RPAREN SEMI?
    ;

openStatement
    : OPEN identifier (USING LPAREN expressionList RPAREN)? SEMI?
    ;

fetchStatement
    : FETCH identifier INTO identifierList SEMI?
    ;

closeStatement
    : CLOSE identifier SEMI?
    ;

ifStatement
    : IF booleanExpr THEN statementList
      (ELSEIF booleanExpr THEN statementList)*
      (ELSE statementList)?
      END IF SEMI?
    ;

caseStatement
    : CASE booleanExpr?
      (WHEN booleanExpr THEN statementList)+
      (ELSE statementList)?
      END CASE? SEMI?
    ;

// An optional loop label: `<name>: LOOP … END LOOP <name>;`. The trailing END-label identifier is
// accepted and ignored; only the leading label drives labeled BREAK / CONTINUE targeting.
loopLabel
    : identifier COLON
    ;

loopStatement
    : loopLabel? LOOP statementList END LOOP identifier? SEMI?
    ;

whileStatement
    : loopLabel? WHILE booleanExpr DO statementList END WHILE identifier? SEMI?
    ;

forStatement
    : loopLabel? FOR identifier IN REVERSE? expression TO expression DO statementList END FOR identifier? SEMI?   // integer range
    | loopLabel? FOR identifier IN expression DO statementList END FOR identifier? SEMI?                          // cursor / list
    ;

repeatStatement
    : loopLabel? REPEAT statementList UNTIL booleanExpr END REPEAT identifier? SEMI?
    ;

returnStatement
    : RETURN TABLE LPAREN selectStatement RPAREN SEMI?
    | RETURN TABLE LPAREN expression RPAREN SEMI?
    | RETURN expression? SEMI?
    ;

breakStatement
    : BREAK identifier? SEMI?
    ;

continueStatement
    : CONTINUE identifier? SEMI?
    ;

raiseStatement
    : RAISE (identifier STRING_LITERAL?)? SEMI?
    ;

callStatement
    : CALL qualifiedName LPAREN expressionList? RPAREN SEMI?
    ;

executeImmediateStatement
    : EXECUTE IMMEDIATE expression (USING LPAREN expressionList RPAREN)? SEMI?
    | EXECUTE IMMEDIATE FROM (stageRef | STRING_LITERAL) SEMI?
    ;

beginEndBlock
    : declareSection? BEGIN statementList exceptionSection? END SEMI?
    ;

declareSection
    : declareStatement
    ;

exceptionSection
    : EXCEPTION exceptionHandler+
    ;

exceptionHandler
    : WHEN exceptionCondition CONTINUE? THEN statementList
    ;

exceptionCondition
    : OTHER
    | identifier
    ;

// SELECT expr1, expr2 INTO var1, var2 FROM table [WHERE ...]
// Targets may be plain identifiers or bind variables (:varname)
selectIntoStatement
    : SELECT DISTINCT? selectList INTO intoTargetList (FROM tableExpression whereClause? groupByClause? havingClause? qualifyClause?)? orderByClause? (limitClause | fetchClause)? SEMI?
    ;

intoTargetList
    : intoTarget (COMMA intoTarget)*
    ;

intoTarget
    : COLON identifier   // :varname  (bind variable)
    | identifier         // plain variable name
    ;

nullStatement
    : NULL SEMI?
    ;

statementList
    : statement+
    ;

// Boolean tier wrapping the value/predicate `expression` below. Currently a pass-through; the
// final migration step gives it NOT/AND/OR alternatives and removes those from `expression`
// (so BETWEEN/LIKE operands, which reference `expression`, can no longer absorb a trailing
// AND/OR). Boolean-context grammar rules reference `booleanExpr`; value operands reference
// `expression`.
booleanExpr
    : NOT booleanExpr                # NotExpr
    | booleanExpr AND booleanExpr    # AndExpr
    | booleanExpr OR booleanExpr     # OrExpr
    | expression                     # ValueExpr
    ;

expression
    : literal                                                    # LiteralExpr
    | SESSION_VAR_REF                                            # SessionVarExpr
    | COLON identifier                                           # BindVarExpr
    | QUESTION                                                   # PositionalBindExpr
    | SYSTEM_STREAM_HAS_DATA LPAREN expression RPAREN            # SystemStreamHasDataExpr
    | SYSTEM_USER_TASK_CANCEL LPAREN expression RPAREN           # SystemUserTaskCancelExpr
    | SYSTEM_FUNC LPAREN expressionList? RPAREN                  # SystemFuncExpr
    | CURRENT_TIMESTAMP                                          # CurrentTimestampExpr
    | CURRENT_DATE                                               # CurrentDateExpr
    | CURRENT_TIME                                               # CurrentTimeExpr
    | CURRENT_USER                                               # CurrentUserExpr
    | qualifiedName                                              # QualifiedNameExpr
    | jsonObjectLiteral                                          # JsonObjectExpr
    | jsonArrayLiteral                                           # JsonArrayExpr
    | caseExpression                                             # CaseExpr
    | INTERVAL expression intervalUnit                           # IntervalExpr
    | INTERVAL STRING_LITERAL                                    # IntervalStringExpr
    | dateTimeLiteralType STRING_LITERAL                         # TypedDateTimeLiteralExpr
    | CAST LPAREN expression AS dataTypeName typeParameters? RPAREN              # CastExpr
    | functionName LPAREN identifier FROM expression RPAREN                      # ExtractFromExpr
    | functionName LPAREN DISTINCT? STAR RPAREN                  # FunctionCallStarExpr
    | functionName LPAREN expression (COMMA namedArgument)+ RPAREN overClause?  # FunctionCallMixedArgsExpr
    | functionName LPAREN namedArgumentList RPAREN overClause?   # FunctionCallNamedArgsExpr
    | functionName LPAREN DISTINCT? functionArgList? RPAREN withinGroupClause? nullHandling? overClause?     # FunctionCallExpr
    | EXECUTE IMMEDIATE expression (USING LPAREN expressionList RPAREN)?  # ExecuteImmediateExpr
    | op=(PLUS | MINUS) expression                               # UnaryExpr
    | EXISTS LPAREN selectStatement RPAREN                       # ExistsExpr
    | LPAREN selectStatement RPAREN                              # ScalarSubqueryExpr
    | expression COLON identifier ((DOT | COLON) identifier)*    # ObjectAccessExpr
    | expression LBRACKET expression RBRACKET                    # ArrayAccessExpr
    | expression DOT identifier                                  # FieldAccessExpr
    | expression DOUBLE_COLON dataTypeName typeParameters?       # CastExpr2
    | expression PIPE_PIPE expression                            # ConcatExpr
    | expression op=(STAR | SLASH | PERCENT) expression          # MultiplicativeExpr
    | expression op=(PLUS | MINUS) expression                    # AdditiveExpr
    | expression IS NOT? NULL                                    # IsNullExpr
    | expression NOT? (LIKE | ILIKE) expression (ESCAPE expression)? # LikeExpr
    | expression NOT? BETWEEN expression AND expression          # BetweenExpr
    | expression NOT? IN LPAREN selectStatement RPAREN           # InSubqueryExpr
    | expression NOT? IN LPAREN expressionList RPAREN            # InListExpr
    | LPAREN expressionList RPAREN NOT? IN LPAREN selectStatement RPAREN  # TupleInSubqueryExpr
    | LPAREN expressionList RPAREN NOT? IN LPAREN expressionList RPAREN   # TupleInListExpr
    | expression op=(EQ | NEQ | LT | LTE | GT | GTE) expression  # ComparisonExpr
    | expression op=(EQ | NEQ | LT | LTE | GT | GTE) quantifier LPAREN selectStatement RPAREN # QuantifiedComparisonExpr
    | LPAREN booleanExpr RPAREN                                  # ParenExpr
    ;

caseExpression
    : CASE expression whenClause+ (ELSE expression)? END         # SimpleCaseExpr
    | CASE whenClause+ (ELSE expression)? END                    # SearchedCaseExpr
    ;

whenClause
    : WHEN booleanExpr THEN expression
    ;

quantifier
    : ANY
    | SOME
    | ALL
    ;

intervalUnit
    : YEAR | YEARS
    | MONTH | MONTHS
    | DAY | DAYS
    | HOUR | HOURS
    | MINUTE | MINUTES
    | SECOND | SECONDS
    ;

jsonObjectLiteral
    : LBRACE jsonKeyValuePair (COMMA jsonKeyValuePair)* RBRACE
    | LBRACE RBRACE
    ;

jsonKeyValuePair
    : STRING_LITERAL COLON expression
    ;

jsonArrayLiteral
    : LBRACKET expression (COMMA expression)* RBRACKET
    | LBRACKET RBRACKET
    ;

expressionList
    : expression (COMMA expression)*
    ;

// Function-call argument list. Uses booleanExpr (which is a superset of expression) so a bare boolean —
// COUNT_IF(a OR b), IFF(x AND y, …) — is accepted as an argument without extra parentheses, while the
// value-context `expressionList` still keeps AND/OR out (so BETWEEN/LIKE operands don't absorb them).
functionArgList
    : booleanExpr (COMMA booleanExpr)*
    ;

namedArgumentList
    : namedArgument (COMMA namedArgument)*
    ;

namedArgument
    : identifier ARROW expression
    ;

functionName
    : identifier (DOT identifier)*
    ;

qualifiedName
    : identifier (DOT identifier)*
    ;

identifier
    : IDENTIFIER
    | KW_IDENTIFIER  // the literal word "identifier" as a plain name (it lexes as KW_IDENTIFIER now)
    | ACCOUNTS      // Allow ACCOUNTS as identifier
    | ACTION
    | AFTER         // Allow AFTER as identifier
    | ALLOW_OVERLAPPING_EXECUTION
    | BEFORE        // Allow BEFORE as identifier
    | CALLER        // Allow CALLER as identifier
    | COLUMNS       // Allow COLUMNS as identifier (for INFORMATION_SCHEMA views)
    | COMMENT       // Allow COMMENT as identifier
    | COPY          // Allow COPY as identifier (table name)
    | CURRENT_DATE  // Allow CURRENT_DATE as identifier (function name)
    | CURRENT_TIME  // Allow CURRENT_TIME as identifier (function name)
    | CURRENT_TIMESTAMP // Allow CURRENT_TIMESTAMP as identifier (function name)
    | CURRENT_USER      // Allow CURRENT_USER as identifier (function name)
    | CURRVAL       // Allow CURRVAL as identifier (function name)
    | DATA          // Allow DATA as identifier
    | DATABASES     // Allow DATABASES as identifier (for INFORMATION_SCHEMA views)
    | CHAR          // Allow CHAR as identifier (function name)
    | DATE          // Allow DATE as identifier
    | DATETIME      // Allow DATETIME as identifier
    | DAY           // Allow DAY as identifier (can be column name)
    | DAYS          // Allow DAYS as identifier (can be column name)
    | DELIMITER     // Allow DELIMITER as identifier (SPLIT_TO_TABLE parameter)
    | DOWNSTREAM    // Allow DOWNSTREAM as identifier
    | DYNAMIC       // Allow DYNAMIC as identifier
    | ECONOMY       // Allow ECONOMY as identifier
    | ENABLE_QUERY_ACCELERATION
    | FILE
    | FIRST         // Allow FIRST as identifier (also ORDER BY ... NULLS FIRST)
    | FLATTEN       // Allow FLATTEN as identifier (table function)
    | FUNCTIONS     // Allow FUNCTIONS as identifier (INFORMATION_SCHEMA view)
    | GENERATOR     // Allow GENERATOR as identifier (table function)
    | GRANTS        // Allow GRANTS as identifier
    | GROUPING      // Allow GROUPING as identifier (function name)
    | HOUR          // Allow HOUR as identifier (can be column name)
    | HOURS         // Allow HOURS as identifier (can be column name)
    | IGNORE        // Allow IGNORE as identifier (also FIRST_VALUE(...) IGNORE NULLS)
    | RESPECT       // Allow RESPECT as identifier (also FIRST_VALUE(...) RESPECT NULLS)
    | INCREMENTAL   // Allow INCREMENTAL as identifier
    | INFORMATION   // Allow INFORMATION as identifier
    | INITIALIZE    // Allow INITIALIZE as identifier
    | INITIALLY_SUSPENDED
    | INPUT         // Allow INPUT as identifier (FLATTEN parameter)
    | INTO          // Allow INTO as identifier
    | KEY           // Allow KEY as identifier (FLATTEN output column)
    | LAST          // Allow LAST as identifier (also ORDER BY ... NULLS LAST)
    | LEFT          // Allow LEFT as identifier (Snowflake compatible)
    | LOCKS         // Allow LOCKS as identifier
    | MAX_CONCURRENCY_LEVEL
    | MINUTE        // Allow MINUTE as identifier (can be column name)
    | MINUTES       // Allow MINUTES as identifier (can be column name)
    | MINUS_KW      // Allow MINUS as identifier (reserved word, but permissive)
    | MODE          // Allow MODE as identifier (FLATTEN parameter)
    | MONTH         // Allow MONTH as identifier (can be column name)
    | MONTHS        // Allow MONTHS as identifier (can be column name)
    | NEXTVAL       // Allow NEXTVAL as identifier (function name)
    | NULLS         // Allow NULLS as identifier (also ORDER BY ... NULLS FIRST/LAST)
    | OBJECTS       // Allow OBJECTS as identifier
    | OFFSET
    | ORGANIZATION  // Allow ORGANIZATION as identifier
    | OTHER         // Allow OTHER as identifier (column name)
    | OUTER         // Allow OUTER as identifier (FLATTEN parameter)
    | OWNER         // Allow OWNER as identifier
    | STRICT        // Allow STRICT as identifier
    | IMMUTABLE     // Allow IMMUTABLE as identifier
    | VOLATILE      // Allow VOLATILE as identifier
    | SECURE        // Allow SECURE as identifier
    | CALLED        // Allow CALLED as identifier
    | PARAMETERS    // Allow PARAMETERS as identifier
    | PARTITION
    | PATH          // Allow PATH as identifier (FLATTEN parameter)
    | PATTERN
    | PIPES         // Allow PIPES as identifier (INFORMATION_SCHEMA view)
    | POSITIONAL_PARAMETER  // Allow $1, $2, etc. as identifier
    | PRIVILEGES    // Allow PRIVILEGES as identifier
    | PROCEDURES    // Allow PROCEDURES as identifier (INFORMATION_SCHEMA view)
    | QUERY_ACCELERATION_MAX_SCALE_FACTOR
    | QUOTED_IDENTIFIER
    | READ_ONLY     // Allow READ_ONLY as identifier
    | RECURSIVE     // Allow RECURSIVE as identifier (FLATTEN parameter)
    | CURRENT       // Allow CURRENT as identifier (window-frame keyword)
    | RANGE         // Allow RANGE as identifier (window-frame keyword)
    | UNBOUNDED     // Allow UNBOUNDED as identifier (window-frame keyword)
    | PRECEDING     // Allow PRECEDING as identifier (window-frame keyword)
    | FOLLOWING     // Allow FOLLOWING as identifier (window-frame keyword)
    | SAMPLE        // Allow SAMPLE as identifier (sampling keyword)
    | SYSTEM        // Allow SYSTEM as identifier (sampling method)
    | SEED          // Allow SEED as identifier (sampling keyword)
    | BERNOULLI     // Allow BERNOULLI as identifier (sampling method)
    | NATURAL       // Allow NATURAL as identifier (join keyword)
    | REPEATABLE    // Allow REPEATABLE as identifier (sampling keyword)
    | REVERSE       // Allow REVERSE as identifier (range-FOR keyword)
    | REPEAT        // Allow REPEAT as identifier (also the REPEAT() string function)
    // NOTE: UNTIL is deliberately NOT an identifier — as one, `statement+` would swallow the
    // `UNTIL <cond>` that terminates a REPEAT body, breaking the REPEAT … UNTIL boundary.
    | REPLACE
    | EXCLUDE       // Allow EXCLUDE as identifier (also the SELECT * EXCLUDE modifier)
    | INCLUDE       // Allow INCLUDE as identifier (also the UNPIVOT INCLUDE NULLS modifier)
    | REAL          // Allow REAL as identifier (also the REAL data-type name, a FLOAT synonym)
    | PRECISION     // Allow PRECISION as identifier (also the DOUBLE PRECISION data-type name)
    | RESULT        // Allow RESULT as identifier (common alias / procedural variable)
    | RIGHT         // Allow RIGHT as identifier (Snowflake compatible)
    | ROLE          // Allow ROLE as identifier
    | ROLES         // Allow ROLES as identifier
    | ROWCOUNT      // Allow ROWCOUNT as identifier (GENERATOR parameter)
    | SCALING_POLICY
    | SCHEMAS       // Allow SCHEMAS as identifier (for INFORMATION_SCHEMA views)
    | SECOND        // Allow SECOND as identifier (can be column name)
    | SECONDS       // Allow SECONDS as identifier (can be column name)
    | SEQUENCES     // Allow SEQUENCES as identifier (INFORMATION_SCHEMA view)
    | SERVERLESS_TASK_MAX_STATEMENT_SIZE
    | SESSIONS      // Allow SESSIONS as identifier
    | SETS          // Allow SETS as identifier
    | SPLIT_TO_TABLE // Allow SPLIT_TO_TABLE as identifier (table function)
    | STAGE
    | STANDARD      // Allow STANDARD as identifier
    | STATEMENT     // Allow STATEMENT as identifier
    | STATEMENT_QUEUED_TIMEOUT_IN_SECONDS
    | STATEMENT_TIMEOUT_IN_SECONDS
    | STREAMS       // Allow STREAMS as identifier (INFORMATION_SCHEMA view)
    | STRING        // Allow STRING as identifier (data type and parameter)
    | SUSPEND_TASK_AFTER_NUM_FAILURES
    | TABLES        // Allow TABLES as identifier (for INFORMATION_SCHEMA views)
    | TAG           // Allow TAG as identifier
    | TAGS
    | TARGET_COMPLETION_INTERVAL
    | TASKS         // Allow TASKS as identifier (INFORMATION_SCHEMA view)
    | TASK_AUTO_RETRY_ATTEMPTS
    | TEMP
    | TEMPORARY
    | TEXT          // Allow TEXT as identifier (data type)
    | TIMELIMIT     // Allow TIMELIMIT as identifier (GENERATOR parameter)
    | TIMESTAMP     // Allow TIMESTAMP as identifier
    | TIMESTAMP_NTZ
    | TIMESTAMPNTZ
    | TRANSACTIONS  // Allow TRANSACTIONS as identifier
    | TYPE
    | UNIQUE        // Allow UNIQUE as identifier
    | URL
    | USER          // Allow USER as identifier
    | USERS         // Allow USERS as identifier
    | USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE
    | USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS
    | USER_TASK_TIMEOUT_MS
    | VARIABLES     // Allow VARIABLES as identifier
    | VIEWS         // Allow VIEWS as identifier (for INFORMATION_SCHEMA views)
    | IMPORTS        // Allow IMPORTS as identifier
    | WAREHOUSE_TYPE // Allow WAREHOUSE_TYPE as identifier
    | YEAR          // Allow YEAR as identifier (can be column name)
    | YEARS         // Allow YEARS as identifier (can be column name)
    | SHOW_INITIAL_ROWS
    | APPEND_ONLY
    | VECTOR
    | RESOURCE
    | UUID
    | REFERENCES
    | SQL
    | TRUNCATE      // Allow TRUNCATE as identifier (TRUNCATE() numeric function, alias of TRUNC)
    ;

nonJoinKeywordIdentifier
    : IDENTIFIER
    | KW_IDENTIFIER
    | APPEND_ONLY
    | QUOTED_IDENTIFIER
    | POSITIONAL_PARAMETER
    | DATE
    | DATA
    | TIMESTAMP
    | COMMENT
    | USERS
    | ROLES
    | GRANTS
    | PRIVILEGES
    | USER
    | ROLE
    | COPY
    | INTO
    | DATABASES
    | TABLES
    | COLUMNS
    | VIEWS
    | SCHEMAS
    | GENERATOR
    | ROWCOUNT
    | TIMELIMIT
    | SPLIT_TO_TABLE
    | STRING
    | TEXT
    | DELIMITER
    | UNIQUE
    | FLATTEN
    | INPUT
    | PATH
    | RECURSIVE
    | MODE
    | KEY
    | TAG
    | NEXTVAL
    | CURRVAL
    | CURRENT_TIMESTAMP
    | CURRENT_DATE
    | CURRENT_TIME
    | OTHER
    | TYPE
    | ACTION
    | PATTERN
    | URL
    | FILE
    | TIMESTAMP_NTZ
    | OFFSET
    | PARTITION
    | TAGS
    | TEMP
    | TEMPORARY
    | REPLACE
    | EXCLUDE
    | INCLUDE
    | YEAR
    | YEARS
    | MONTH
    | MONTHS
    | DAY
    | DAYS
    | HOUR
    | HOURS
    | MINUTE
    | MINUTES
    | SECOND
    | SECONDS
    | RESOURCE
    | SHOW_INITIAL_ROWS
    // Note: Intentionally exclude LEFT, RIGHT, INNER, FULL, CROSS, OUTER
    // These cannot be used as table aliases without AS keyword
    ;

// Common DDL clauses
if_exists
    : IF EXISTS
    ;

if_not_exists
    : IF NOT EXISTS
    ;

or_replace
    : OR REPLACE
    ;

literal
    : INTEGER_LITERAL
    | FLOAT_LITERAL
    | STRING_LITERAL
    | DOLLAR_QUOTED_STRING
    | TRUE
    | FALSE
    | NULL
    ;

// Lexer Rules
// Keywords
SELECT: S E L E C T;
DISTINCT: D I S T I N C T;
FROM: F R O M;
WHERE: W H E R E;
AND: A N D;
OR: O R;
NOT: N O T;
IN: I N;
TO: T O;
IS: I S;
NULL: N U L L;
TRUE: T R U E;
FALSE: F A L S E;
AS: A S;
GROUP: G R O U P;
BY: B Y;
BETWEEN: B E T W E E N;
LIKE: L I K E;
ILIKE: I L I K E;
ESCAPE: E S C A P E;
HAVING: H A V I N G;
QUALIFY: Q U A L I F Y;
OVER: O V E R;
PARTITION: P A R T I T I O N;
ORDER: O R D E R;
NOORDER: N O O R D E R;
ASC: A S C;
DESC: D E S C;
LIMIT: L I M I T;
TOP: T O P;
OFFSET: O F F S E T;
FIRST: F I R S T;
LAST: L A S T;
NULLS: N U L L S;
IGNORE: I G N O R E;
RESPECT: R E S P E C T;
NEXT: N E X T;
ROWS: R O W S;
RANGE: R A N G E;
UNBOUNDED: U N B O U N D E D;
PRECEDING: P R E C E D I N G;
FOLLOWING: F O L L O W I N G;
CURRENT: C U R R E N T;
ONLY: O N L Y;
CREATE: C R E A T E;
CLONE: C L O N E;
DROP: D R O P;
UNDROP: U N D R O P;
EXPLAIN: E X P L A I N;
RESULT: R E S U L T;
ALTER: A L T E R;
RENAME: R E N A M E;
ADD: A D D;
COLUMN: C O L U M N;
TABLE: T A B L E;
VIEW: V I E W;
MATERIALIZED: M A T E R I A L I Z E D;
DYNAMIC: D Y N A M I C;
TARGET_LAG: T A R G E T '_' L A G;
DOWNSTREAM: D O W N S T R E A M;
INCREMENTAL: I N C R E M E N T A L;
INITIALIZE: I N I T I A L I Z E;
ORGANIZATION: O R G A N I Z A T I O N;
ACCOUNTS: A C C O U N T S;
LOCKS: L O C K S;
TRANSACTIONS: T R A N S A C T I O N S;
VARIABLES: V A R I A B L E S;
ON_CREATE: O N '_' C R E A T E;
ON_SCHEDULE: O N '_' S C H E D U L E;
REFRESH_MODE: R E F R E S H '_' M O D E;
DATA_RETENTION_TIME_IN_DAYS: D A T A '_' R E T E N T I O N '_' T I M E '_' I N '_' D A Y S;
READ_ONLY: R E A D '_' O N L Y;
AUTO: A U T O;
DATABASE: D A T A B A S E;
SCHEMA: S C H E M A;
INSERT: I N S E R T;
INTO: I N T O;
VALUES: V A L U E S;
COPY: C O P Y;
UPDATE: U P D A T E;
DELETE: D E L E T E;
SET: S E T;
UNSET: U N S E T;
PRIMARY: P R I M A R Y;
KEYS: K E Y S;
KEY: K E Y;
UNIQUE: U N I Q U E;
DEFAULT: D E F A U L T;
AUTOINCREMENT: A U T O I N C R E M E N T;
IDENTITY: I D E N T I T Y;
// The IDENTIFIER() name-resolution keyword — a distinct token so IDENTIFIER('x') is unambiguous against a
// table name followed by a bare (list); declared before the general IDENTIFIER rule so it wins the literal
// word. Kept usable as a plain identifier via the identifier allowlist below.
KW_IDENTIFIER: I D E N T I F I E R;
UNION: U N I O N;
INTERSECT: I N T E R S E C T;
EXCEPT: E X C E P T;
EXCLUDE: E X C L U D E;
INCLUDE: I N C L U D E;
MINUS_KW: M I N U S;   // the word MINUS (set operator); the '-' symbol is the separate MINUS token
USE: U S E;
SHOW: S H O W;
DESCRIBE: D E S C R I B E;
DATABASES: D A T A B A S E S;
SCHEMAS: S C H E M A S;
TABLES: T A B L E S;
VIEWS: V I E W S;
COLUMNS: C O L U M N S;
FUTURE: F U T U R E;
STREAMS: S T R E A M S;
TASKS: T A S K S;
PIPES: P I P E S;
SEQUENCES: S E Q U E N C E S;
WAREHOUSES: W A R E H O U S E S;
STAGES: S T A G E S;
ROLLUP: R O L L U P;
CUBE: C U B E;
GROUPING: G R O U P I N G;
SETS: S E T S;
PARAMETERS: P A R A M E T E R S;
SESSIONS: S E S S I O N S;
OBJECTS: O B J E C T S;
PROCEDURES: P R O C E D U R E S;
FUNCTIONS: F U N C T I O N S;

// Stream, Task, Warehouse, Stage
STREAM: S T R E A M;
TASK: T A S K;
WAREHOUSE: W A R E H O U S E;
STAGE: S T A G E;
MERGE: M E R G E;
USING: U S I N G;
ON: O N;
WHEN: W H E N;
MATCHED: M A T C H E D;
PIVOT: P I V O T;
UNPIVOT: U N P I V O T;

// Join keywords
JOIN: J O I N;
INNER: I N N E R;
LEFT: L E F T;
RIGHT: R I G H T;
FULL: F U L L;
OUTER: O U T E R;
CROSS: C R O S S;
LATERAL: L A T E R A L;
RESUME: R E S U M E;
SUSPEND: S U S P E N D;
PAUSE: P A U S E;
REFRESH: R E F R E S H;
REBUILD: R E B U I L D;
DISABLE: D I S A B L E;
ENABLE: E N A B L E;
SCHEDULE: S C H E D U L E;
AFTER: A F T E R;
ALLOW_OVERLAPPING_EXECUTION: A L L O W '_' O V E R L A P P I N G '_' E X E C U T I O N;
USER_TASK_TIMEOUT_MS: U S E R '_' T A S K '_' T I M E O U T '_' M S;
SUSPEND_TASK_AFTER_NUM_FAILURES: S U S P E N D '_' T A S K '_' A F T E R '_' N U M '_' F A I L U R E S;
TASK_AUTO_RETRY_ATTEMPTS: T A S K '_' A U T O '_' R E T R Y '_' A T T E M P T S;
USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE: U S E R '_' T A S K '_' M A N A G E D '_' I N I T I A L '_' W A R E H O U S E '_' S I Z E;
SERVERLESS_TASK_MAX_STATEMENT_SIZE: S E R V E R L E S S '_' T A S K '_' M A X '_' S T A T E M E N T '_' S I Z E;
TARGET_COMPLETION_INTERVAL: T A R G E T '_' C O M P L E T I O N '_' I N T E R V A L;
USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS: U S E R '_' T A S K '_' M I N I M U M '_' T R I G G E R '_' I N T E R V A L '_' I N '_' S E C O N D S;
START: S T A R T;
INCREMENT: I N C R E M E N T;
RESTART: R E S T A R T;
NEXTVAL: N E X T V A L;
CURRVAL: C U R R V A L;
CURRENT_TIMESTAMP: C U R R E N T UNDERSCORE T I M E S T A M P;
CURRENT_DATE: C U R R E N T UNDERSCORE D A T E;
CURRENT_TIME: C U R R E N T UNDERSCORE T I M E;
CURRENT_USER: C U R R E N T UNDERSCORE U S E R;
APPEND_ONLY: A P P E N D UNDERSCORE O N L Y;
SHOW_INITIAL_ROWS: S H O W UNDERSCORE I N I T I A L UNDERSCORE R O W S;
AUTO_INGEST: A U T O UNDERSCORE I N G E S T;
AWS_SNS_TOPIC: A W S UNDERSCORE S N S UNDERSCORE T O P I C;
ERROR_INTEGRATION: E R R O R UNDERSCORE I N T E G R A T I O N;
ON_ERROR: O N UNDERSCORE E R R O R;
FIELD_DELIMITER: F I E L D UNDERSCORE D E L I M I T E R;
SKIP_HEADER: S K I P UNDERSCORE H E A D E R;
DATE_FORMAT: D A T E UNDERSCORE F O R M A T;
WAREHOUSE_SIZE: W A R E H O U S E UNDERSCORE S I Z E;
WAREHOUSE_TYPE: W A R E H O U S E UNDERSCORE T Y P E;
AUTO_SUSPEND: A U T O UNDERSCORE S U S P E N D;
AUTO_RESUME: A U T O UNDERSCORE R E S U M E;
MIN_CLUSTER_COUNT: M I N UNDERSCORE C L U S T E R UNDERSCORE C O U N T;
MAX_CLUSTER_COUNT: M A X UNDERSCORE C L U S T E R UNDERSCORE C O U N T;
SCALING_POLICY: S C A L I N G UNDERSCORE P O L I C Y;
INITIALLY_SUSPENDED: I N I T I A L L Y UNDERSCORE S U S P E N D E D;
MAX_CONCURRENCY_LEVEL: M A X UNDERSCORE C O N C U R R E N C Y UNDERSCORE L E V E L;
STATEMENT_QUEUED_TIMEOUT_IN_SECONDS: S T A T E M E N T UNDERSCORE Q U E U E D UNDERSCORE T I M E O U T UNDERSCORE I N UNDERSCORE S E C O N D S;
STATEMENT_TIMEOUT_IN_SECONDS: S T A T E M E N T UNDERSCORE T I M E O U T UNDERSCORE I N UNDERSCORE S E C O N D S;
ENABLE_QUERY_ACCELERATION: E N A B L E UNDERSCORE Q U E R Y UNDERSCORE A C C E L E R A T I O N;
QUERY_ACCELERATION_MAX_SCALE_FACTOR: Q U E R Y UNDERSCORE A C C E L E R A T I O N UNDERSCORE M A X UNDERSCORE S C A L E UNDERSCORE F A C T O R;
STANDARD: S T A N D A R D;
ECONOMY: E C O N O M Y;
MULTI_STATEMENT_COUNT: M U L T I UNDERSCORE S T A T E M E N T UNDERSCORE C O U N T;
CLUSTER: C L U S T E R;
COLLATE: C O L L A T E;
TRANSIENT: T R A N S I E N T;
TEMPORARY: T E M P O R A R Y;
TEMP: T E M P;
REPLACE: R E P L A C E;
WITH: W I T H;
WITHIN: W I T H I N;
SWAP: S W A P;
THEN: T H E N;
URL: U R L;
FILE_FORMAT: F I L E UNDERSCORE F O R M A T;
ENCRYPTION: E N C R Y P T I O N;
VALIDATION_MODE: V A L I D A T I O N UNDERSCORE M O D E;
SIZE_LIMIT: S I Z E UNDERSCORE L I M I T;
PURGE: P U R G E;
FORCE: F O R C E;
ALLOWED_VALUES: A L L O W E D UNDERSCORE V A L U E S;
MATCH_BY_COLUMN_NAME: M A T C H UNDERSCORE B Y UNDERSCORE C O L U M N UNDERSCORE N A M E;
HEADER: H E A D E R;
OVERWRITE: O V E R W R I T E;
SINGLE: S I N G L E;
MAX_FILE_SIZE: M A X UNDERSCORE F I L E UNDERSCORE S I Z E;
COMPRESSION: C O M P R E S S I O N;
RECORD_DELIMITER: R E C O R D UNDERSCORE D E L I M I T E R;
TYPE: T Y P E;
FILES: F I L E S;
COMMENT: C O M M E N T;
LANGUAGE: L A N G U A G E;
JAVASCRIPT: J A V A S C R I P T;
JAVA: J A V A;
SCALA: S C A L A;
PYTHON: P Y T H O N;
RUNTIME_VERSION: R U N T I M E UNDERSCORE V E R S I O N;
HANDLER: H A N D L E R;
PACKAGES: P A C K A G E S;
IMPORTS: I M P O R T S;
SQL: S Q L;
LIST: L I S T;
PUT: P U T;
GET: G E T;
REMOVE: R E M O V E;
RM: R M;
AT: '@';
AT_KEYWORD: A T;
BEFORE: B E F O R E;
STATEMENT: S T A T E M E N T;
CHANGES: C H A N G E S;
INFORMATION: I N F O R M A T I O N;
PATTERN: P A T T E R N;

// Security
USER: U S E R;
ROLE: R O L E;
USERS: U S E R S;
ROLES: R O L E S;
SECONDARY: S E C O N D A R Y;
GRANT: G R A N T;
REVOKE: R E V O K E;
GRANTS: G R A N T S;
PRIVILEGES: P R I V I L E G E S;
ALL: A L L;
ANY: A N Y;
SOME: S O M E;
USAGE: U S A G E;
OWNERSHIP: O W N E R S H I P;
PASSWORD: P A S S W O R D;
DEFAULT_ROLE: D E F A U L T UNDERSCORE R O L E;
TRUNCATE: T R U N C A T E;
MODIFY: M O D I F Y;
OPERATE: O P E R A T E;
MONITOR: M O N I T O R;
READ: R E A D;
WRITE: W R I T E;
REFERENCES: R E F E R E N C E S;
CONSTRAINT: C O N S T R A I N T;
FOREIGN: F O R E I G N;
CASCADE: C A S C A D E;
RESTRICT: R E S T R I C T;
ACTION: A C T I O N;
NO: N O;
RELY: R E L Y;
NORELY: N O R E L Y;
APPLY: A P P L Y;
IMPORTED: I M P O R T E D;
SEQUENCE: S E Q U E N C E;
PIPE: P I P E;
PROCEDURE: P R O C E D U R E;
FUNCTION: F U N C T I O N;
GENERATOR: G E N E R A T O R;
ROWCOUNT: R O W C O U N T;
TIMELIMIT: T I M E L I M I T;
SPLIT_TO_TABLE: S P L I T '_' T O '_' T A B L E;
DELIMITER: D E L I M I T E R;
FLATTEN: F L A T T E N;
INPUT: I N P U T;
PATH: P A T H;
RECURSIVE: R E C U R S I V E;
MODE: M O D E;
ACCOUNT: A C C O U N T;
INTEGRATION: I N T E G R A T I O N;
NETWORK: N E T W O R K;
POLICY: P O L I C Y;
POLICIES: P O L I C I E S;
MASKING: M A S K I N G;
ROW: R O W;
NATURAL: N A T U R A L;
SAMPLE: S A M P L E;
TABLESAMPLE: T A B L E S A M P L E;
BERNOULLI: B E R N O U L L I;
SYSTEM: S Y S T E M;
SEED: S E E D;
REPEATABLE: R E P E A T A B L E;
REVERSE: R E V E R S E;
REPEAT: R E P E A T;
UNTIL: U N T I L;
ACCESS: A C C E S S;
SESSION: S E S S I O N;
TAG: T A G;
TAGS: T A G S;
IMPORT: I M P O R T;
SHARE: S H A R E;
MANAGE: M A N A G E;
EXECUTION: E X E C U T I O N;
FILE: F I L E;
FORMAT: F O R M A T;
FORMATS: F O R M A T S;
RESOURCE: R E S O U R C E;
USE_ANY_ROLE: U S E UNDERSCORE A N Y UNDERSCORE R O L E;
RESOURCE_MONITOR : R E S O U R C E '_' M O N I T O R;
MASKING_POLICY : M A S K I N G '_' P O L I C Y;
ROW_ACCESS_POLICY : R O W '_' A C C E S S '_' P O L I C Y;
SESSION_POLICY : S E S S I O N '_' P O L I C Y;

// Cursor and ResultSet
CURSOR: C U R S O R;
RESULTSET: R E S U L T S E T;
OPEN: O P E N;
FETCH: F E T C H;
CLOSE: C L O S E;

// Data Types
INTEGER: I N T E G E R;
INT: I N T;
BIGINT: B I G I N T;
SMALLINT: S M A L L I N T;
TINYINT: T I N Y I N T;
BYTEINT: B Y T E I N T;
NUMBER: N U M B E R;
DECIMAL: D E C I M A L;
FLOAT: F L O A T;
FLOAT4: F L O A T '4';
FLOAT8: F L O A T '8';
DOUBLE: D O U B L E;
REAL: R E A L;
PRECISION: P R E C I S I O N;
UUID: U U I D;
VECTOR: V E C T O R;
VARCHAR: V A R C H A R;
STRING: S T R I N G;
TEXT: T E X T;
CHAR: C H A R;
BOOLEAN: B O O L E A N;
DATE: D A T E;
DATA: D A T A;
DATETIME: D A T E T I M E;
TIMESTAMP: T I M E S T A M P;
TIMESTAMP_NTZ: T I M E S T A M P UNDERSCORE N T Z;
TIMESTAMPNTZ: T I M E S T A M P N T Z;
TIMESTAMP_LTZ: T I M E S T A M P UNDERSCORE L T Z;
TIMESTAMP_TZ: T I M E S T A M P UNDERSCORE T Z;
VARIANT: V A R I A N T;
ARRAY: A R R A Y;
OBJECT: O B J E C T;
BINARY: B I N A R Y;
VARBINARY: V A R B I N A R Y;

// Interval
INTERVAL: I N T E R V A L;
YEAR: Y E A R;
YEARS: Y E A R S;
MONTH: M O N T H;
MONTHS: M O N T H S;
DAY: D A Y;
DAYS: D A Y S;
HOUR: H O U R;
HOURS: H O U R S;
MINUTE: M I N U T E;
MINUTES: M I N U T E S;
SECOND: S E C O N D;
SECONDS: S E C O N D S;

// Transaction
BEGIN: B E G I N;
TRANSACTION: T R A N S A C T I O N;
COMMIT: C O M M I T;
ROLLBACK: R O L L B A C K;

// Procedural Language Keywords
DECLARE: D E C L A R E;
LET: L E T;
IF: I F;
EXISTS: E X I S T S;
ELSEIF: E L S E I F;
ELSE: E L S E;
END: E N D;
LOOP: L O O P;
WHILE: W H I L E;
FOR: F O R;
DO: D O;
RETURN: R E T U R N;
RETURNS: R E T U R N S;
BREAK: B R E A K;
CONTINUE: C O N T I N U E;
RAISE: R A I S E;
CALL: C A L L;
EXECUTE: E X E C U T E;
OWNER: O W N E R;
CALLER: C A L L E R;
STRICT: S T R I C T;
IMMUTABLE: I M M U T A B L E;
VOLATILE: V O L A T I L E;
SECURE: S E C U R E;
CALLED: C A L L E D;
IMMEDIATE: I M M E D I A T E;
CASE: C A S E;
CAST: C A S T;
EXCEPTION: E X C E P T I O N;
OTHER: O T H E R;

// Identifiers
QUOTED_IDENTIFIER: '"' (~["\r\n] | '""')* '"';
POSITIONAL_PARAMETER: '$' [0-9]+;
SESSION_VAR_REF: '$' [a-zA-Z_][a-zA-Z0-9_]*;
SYSTEM_STREAM_HAS_DATA: S Y S T E M '$' S T R E A M '_' H A S '_' D A T A;
SYSTEM_USER_TASK_CANCEL: S Y S T E M '$' U S E R '_' T A S K '_' C A N C E L '_' O N G O I N G '_' E X E C U T I O N S;
// General SYSTEM$ function token — matches any remaining SYSTEM$NAME pattern
SYSTEM_FUNC: S Y S T E M '$' [A-Za-z_][A-Za-z0-9_]*;

// Unquoted identifiers may contain '$' (but not as the first character), per Snowflake — e.g.
// METADATA$ACTION, COL$1. Declared AFTER the SYSTEM$… / $-prefixed tokens so those specific tokens win the
// equal-length tie-break (ANTLR picks the earliest rule when match lengths are equal).
IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_$]*;

// Literals
INTEGER_LITERAL: [0-9]+;
FLOAT_LITERAL: [0-9]+ DOT [0-9]+ ([eE] [+-]? [0-9]+)?;
STRING_LITERAL: '\'' (~['] | '\'\'' | '\\' .)* '\'';      // Single quoted strings with newlines
DOLLAR_QUOTED_STRING: '$$' .*? '$$';

// Operators and Punctuation
SEMI: ';';
LPAREN: '(';
RPAREN: ')';
COMMA: ',';
DOT: '.';
LBRACKET: '[';
RBRACKET: ']';
LBRACE: '{';
RBRACE: '}';
QUESTION: '?';
COLON: ':';
COLON_EQ: ':=';
EQ: '=';
ARROW: '=>';
THIN_ARROW: '->';
NEQ: '<>' | '!=';
LT: '<';
LTE: '<=';
GT: '>';
GTE: '>=';
PLUS: '+';

// Comments and whitespace before MINUS so '--' is matched as LINE_COMMENT not MINUS MINUS
LINE_COMMENT: '--' ~[\r\n]* -> skip;
BLOCK_COMMENT: '/*' .*? '*/' -> skip;
WS: [ \t\r\n]+ -> skip;

MINUS: '-';
STAR: '*';
DOUBLE_STAR: '**';
SLASH: '/';
PERCENT: '%';
TILDE: '~';
PIPE_PIPE: '||';
DOUBLE_COLON: '::';

// Case-insensitive fragments
fragment A: [aA];
fragment B: [bB];
fragment C: [cC];
fragment D: [dD];
fragment E: [eE];
fragment F: [fF];
fragment G: [gG];
fragment H: [hH];
fragment I: [iI];
fragment J: [jJ];
fragment K: [kK];
fragment L: [lL];
fragment M: [mM];
fragment N: [nN];
fragment O: [oO];
fragment P: [pP];
fragment Q: [qQ];
fragment R: [rR];
fragment S: [sS];
fragment T: [tT];
fragment U: [uU];
fragment V: [vV];
fragment W: [wW];
fragment X: [xX];
fragment Y: [yY];
fragment Z: [zZ];
fragment UNDERSCORE: '_';
