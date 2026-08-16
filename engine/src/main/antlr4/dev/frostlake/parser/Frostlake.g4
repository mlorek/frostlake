grammar Frostlake;

@parser::members {
    /** The unreserved EXCEPT may serve as a BARE (AS-less) alias only when the next token cannot
     *  start a set-operation right-hand side — `SELECT 1 except` / `FROM t except` are aliases,
     *  `... EXCEPT SELECT ...` stays a set-op (statements need no semicolon between them, so both
     *  parses reach EOF and plain lookahead cannot decide). Every other word passes unchanged. */
    private boolean exceptBareAliasAllowed() {
        if (_input.LT(1).getType() != EXCEPT) {
            return true;
        }
        final int next = _input.LT(2).getType();
        return next != SELECT && next != LPAREN && next != WITH && next != ALL && next != DISTINCT;
    }

    /** Whether a token may BEGIN a column reference. THREE of the five words a NAME position admits do
     *  not reach an expression: CASE, CAST and WHEN each lead their own construct there, and live
     *  refuses `SELECT case FROM t` as a syntax error EVEN WHEN a column of that name exists — defining
     *  one and referencing it are different vocabularies. TRY_CAST is the same story and was this
     *  guard's original occupant.
     *
     *  <p>CONSTRAINT and DEFAULT are NOT in that group, measured: live PARSES both here and answers
     *  "invalid identifier", which is the resolver talking, not the parser. They differ from each other
     *  further down — a CONSTRAINT column RESOLVES once it exists, while an unquoted DEFAULT never does
     *  (see ExpressionAstBuilder, where that one is refused).
     *
     *  <p>A predicate here is hoisted into every decision that can reach this alternative, which is why
     *  the qualified STAR takes starQualifiedName rather than qualifiedName: sharing the rule let the
     *  hoisted predicate eliminate `case.*` along with the bare reference. */
    private boolean expressionLeadingName() {
        switch (_input.LT(1).getType()) {
            case TRY_CAST:
                return false;
            case CASE:
            case CAST:
            case WHEN:
                // QUALIFYING is not REFERENCING: `case.a` names a column of the table CASE and live
                // reads it, while the bare `case` is where the construct starts. A star over such a
                // table expands to exactly these qualified references, so refusing them outright
                // would break `SELECT case.* FROM case` on the way back out.
                return _input.LT(2).getType() == DOT;
            default:
                return true;
        }
    }

    /** Whether a token may lead a table name in the FROM position. A join keyword there is still a
     *  KEYWORD when the name is BARE — live reads `FROM asof` as the start of an ASOF JOIN and refuses
     *  it at end of input — but names a container when a dot follows, `FROM asof.t` reaching
     *  "Schema 'ASOF' does not exist". Both spellings measured, for all eleven words. */
    private boolean tableNameLeadAllowed() {
        switch (_input.LT(1).getType()) {
            case ASOF:
            case CROSS:
            case FULL:
            case INNER:
            case JOIN:
            case LATERAL:
            case LEFT:
            case MATCH_CONDITION:
            case NATURAL:
            case RIGHT:
            case USING:
                return _input.LT(2).getType() == DOT;
            default:
                return true;
        }
    }

    /** The words that may name a COLUMN but may NOT be a bare (AS-less) table alias, measured word by
     *  word against a live account over a RAW connection. Every one of them LEADS something in the FROM
     *  clause — a join, a lateral, a match condition — so a bare alias spelling it would swallow the
     *  clause it starts, and live refuses the qualified `<w>.x` form with it.
     *
     *  <p>The list once held four more — BREAK, CONTINUE, RAISE and RETURN — described as statement
     *  keywords live refuses here even though nothing in a FROM clause begins with them. It does not:
     *  all four are ordinary aliases, and `SELECT break.x FROM st break` RESOLVES. They were excluded
     *  because the test harness split `SELECT x FROM st break` into two statements before submitting
     *  it, so the account was asked about a bare `break` and refused that instead. */
    private boolean bareTableAliasAllowed() {
        switch (_input.LT(1).getType()) {
            case ASOF: case CROSS: case FULL: case INNER: case JOIN:
            case LATERAL: case LEFT: case MATCH_CONDITION: case NATURAL:
            case RIGHT: case USING:
                return false;
            default:
                return true;
        }
    }
}

// Parser Rules - Simplified for demonstration
sqlScript
    : (flowChain SEMI?)+ EOF
    ;

// The flow operator: stmt ->> stmt ->> stmt. Each stage may read a PRIOR stage's result via a
// $n table reference, where n counts BACKWARD ($1 = the immediately preceding statement).
// The chain's result is the LAST statement's result.
flowChain
    : statement (FLOW_ARROW statement)*
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
    | UNDROP TAG qualifiedName SEMI?
    ;

createStatement
    : CREATE or_replace? DATABASE if_not_exists? identifier (CLONE identifier timeTravelClause?)? (DATA_RETENTION_TIME_IN_DAYS EQ INTEGER_LITERAL)? commentClause? SEMI?
    | CREATE or_replace? SCHEMA if_not_exists? qualifiedName (CLONE qualifiedName timeTravelClause?)? commentClause? tagList? SEMI?
    // A table must say what its columns ARE: an explicit column list, CLONE, LIKE, or CTAS. A body-less
    // `CREATE TABLE t`, `CREATE TABLE t TAG (…)` or `CREATE TABLE t CLUSTER BY (…)` is a syntax error in
    // Snowflake (live-verified), so the shape group below is NOT optional.
    | CREATE or_replace? (TRANSIENT | (LOCAL | GLOBAL)? (TEMPORARY | TEMP) | VOLATILE | HYBRID)? TABLE if_not_exists? objectName tableTailOption* (LPAREN columnList RPAREN tableTailOption* (AS selectStatement)? | CLONE qualifiedName timeTravelClause? | LIKE qualifiedName | columnListOptional? AS selectStatement) tableTailOption* SEMI?
    // TEMPORARY/TEMP/VOLATILE views, live-measured, including the keyword ORDER: SECURE comes
    // BEFORE the temporary keyword on a view (`CREATE SECURE TEMPORARY VIEW` parses,
    // `CREATE TEMPORARY SECURE VIEW` is a syntax error) — the opposite of a function, below.
    | CREATE or_replace? SECURE? ((LOCAL | GLOBAL)? (TEMPORARY | TEMP | VOLATILE))? VIEW if_not_exists? qualifiedName copyGrants? viewProperty* (LPAREN viewColumnList RPAREN)? copyGrants? viewProperty* rowAccessPolicyClause? commentClause? tagList? AS selectStatement SEMI?
    | CREATE or_replace? SECURE? MATERIALIZED VIEW if_not_exists? qualifiedName copyGrants? (LPAREN viewColumnList RPAREN)? copyGrants? commentClause? tagList? AS selectStatement SEMI?
    // COMMENT is one of the pre-AS options; a trailing COMMENT after the query is a syntax error (live-verified).
    | CREATE or_replace? DYNAMIC TABLE if_not_exists? qualifiedName (LPAREN identifierList RPAREN)? dynamicTableOptions (LPAREN identifierList RPAREN)? AS selectStatement SEMI?
    | CREATE or_replace? STREAM if_not_exists? qualifiedName ON (TABLE | VIEW) qualifiedName streamOptions? commentClause? SEMI?
    | CREATE or_replace? TASK if_not_exists? qualifiedName warehouseClause? taskOptions? afterClause? commentClause? (WHEN booleanExpr)? AS taskBody commentClause? SEMI?
    | CREATE or_replace? PIPE if_not_exists? qualifiedName pipeOptions? AS copyStatement commentClause? SEMI?
    | CREATE or_replace? SEQUENCE if_not_exists? qualifiedName WITH? sequenceOptions? commentClause? SEMI?
    | CREATE or_replace? WAREHOUSE if_not_exists? identifier warehouseProperties? commentClause? SEMI?
    // CREATE COMPUTE POOL: MIN_NODES, MAX_NODES and INSTANCE_FAMILY are required, checked by the
    // handler so the missing-option report matches a real account's.
    | CREATE COMPUTE POOL if_not_exists? identifier (FOR APPLICATION identifier)? computePoolOption* tagList? SEMI?
    // CREATE CORTEX SEARCH SERVICE: WAREHOUSE and TARGET_LAG are required and ON must follow the name
    // (live-verified: `CREATE CORTEX SEARCH SERVICE ON body s` is a syntax error, while a missing
    // WAREHOUSE or TARGET_LAG is a compilation error the handler reports). The options are order-free,
    // and the parentheses around the defining query are optional.
    | CREATE or_replace? CORTEX SEARCH SERVICE if_not_exists? qualifiedName ON identifier
      (ATTRIBUTES identifierList)? cortexSearchOption* AS (LPAREN selectStatement RPAREN | selectStatement) SEMI?
    | CREATE or_replace? (TEMPORARY | TEMP)? STAGE if_not_exists? qualifiedName stageProperties? tagList? commentClause? SEMI?
    | CREATE or_replace? (TEMPORARY | TEMP)? FILE FORMAT if_not_exists? qualifiedName copyFormatOption* commentClause? SEMI?
    | CREATE or_replace? TAG if_not_exists? qualifiedName tagProperties? commentClause? SEMI?
    // A function takes the temporary keyword BEFORE SECURE and admits no LOCAL/GLOBAL prefix —
    // both live-measured, and both the reverse of the view rule above.
    | CREATE or_replace? (TEMPORARY | TEMP | VOLATILE)? SECURE? FUNCTION if_not_exists? qualifiedName LPAREN parameterList? RPAREN RETURNS returnType functionOption* (AS bodyDefinition)? SEMI?
    | CREATE or_replace? (TEMPORARY | TEMP | VOLATILE)? PROCEDURE if_not_exists? qualifiedName LPAREN parameterList? RPAREN RETURNS returnType languageClause? runtimeVersionClause? packagesClause? importsClause? handlerClause? commentClause? executeAsClause? (AS bodyDefinition)?  SEMI?
    | CREATE or_replace? USER if_not_exists? identifier userProperties? SEMI?
    | CREATE or_replace? ROLE if_not_exists? identifier commentClause? SEMI?
    | CREATE or_replace? MASKING POLICY if_not_exists? qualifiedName AS LPAREN parameterList RPAREN RETURNS dataTypeName typeParameters? THIN_ARROW (bodyDefinition | booleanExpr) commentClause? SEMI?
    | CREATE or_replace? ROW ACCESS POLICY if_not_exists? qualifiedName AS LPAREN parameterList RPAREN RETURNS BOOLEAN THIN_ARROW (bodyDefinition | booleanExpr) commentClause? SEMI?
    | CREATE or_replace? CONTACT if_not_exists? qualifiedName contactProperty* SEMI?
    | CREATE or_replace? PROJECTION POLICY if_not_exists? qualifiedName AS LPAREN parameterList? RPAREN RETURNS (dataTypeName | identifier) THIN_ARROW (bodyDefinition | booleanExpr) commentClause? SEMI?
    | CREATE or_replace? AGGREGATION POLICY if_not_exists? qualifiedName AS LPAREN parameterList? RPAREN RETURNS (dataTypeName | identifier) THIN_ARROW (bodyDefinition | booleanExpr) commentClause? SEMI?
    | CREATE or_replace? JOIN POLICY if_not_exists? qualifiedName AS LPAREN parameterList? RPAREN RETURNS (dataTypeName | identifier) THIN_ARROW (bodyDefinition | booleanExpr) commentClause? SEMI?
    ;

// CREATE CONTACT's properties, all string-valued: COMMENT, URL and EMAIL_DISTRIBUTION_LIST (which
// takes ONE address, live-verified, despite the plural name). Routed through optionKey so none of
// the property names has to become a keyword.
contactProperty
    : optionKey EQ STRING_LITERAL
    ;

// A contact is attached to an object for a PURPOSE. Live takes three, and takes them quoted too.
contactPurpose
    : identifier
    | STRING_LITERAL
    ;

contactAssignment
    : contactPurpose EQ qualifiedName
    ;

// The '=' is positional (live-verified): an OBJECT-level comment REQUIRES it — CREATE TABLE t (x INT)
// COMMENT 'x' is a syntax error at the string — while a COLUMN-level comment FORBIDS it — id INT
// COMMENT = 'x' is a syntax error at the '='. The two rules below carry that split.
commentClause
    : COMMENT EQ (STRING_LITERAL | DOLLAR_QUOTED_STRING)
    ;

columnCommentClause
    : COMMENT (STRING_LITERAL | DOLLAR_QUOTED_STRING)
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
    : DROP DATABASE if_exists? identifier dropBehavior? SEMI?
    | DROP SCHEMA if_exists? qualifiedName dropBehavior? SEMI?
    | DROP TABLE if_exists? objectName SEMI?
    | DROP VIEW if_exists? qualifiedName SEMI?
    | DROP MATERIALIZED VIEW if_exists? qualifiedName SEMI?
    | DROP DYNAMIC TABLE if_exists? qualifiedName SEMI?
    | DROP STREAM if_exists? qualifiedName SEMI?
    | DROP TASK if_exists? qualifiedName SEMI?
    | DROP PIPE if_exists? qualifiedName SEMI?
    | DROP SEQUENCE if_exists? qualifiedName SEMI?
    | DROP WAREHOUSE if_exists? identifier SEMI?
    | DROP COMPUTE POOL if_exists? identifier SEMI?
    | DROP CORTEX SEARCH SERVICE if_exists? qualifiedName SEMI?
    | DROP STAGE if_exists? qualifiedName SEMI?
    | DROP FILE FORMAT if_exists? qualifiedName SEMI?
    | DROP TAG if_exists? qualifiedName SEMI?
    | DROP FUNCTION if_exists? qualifiedName LPAREN dataTypeList? RPAREN SEMI?   // the signature is REQUIRED (live-verified)
    | DROP PROCEDURE if_exists? qualifiedName LPAREN dataTypeList? RPAREN SEMI?   // the signature is REQUIRED (live-verified)
    | DROP USER if_exists? identifier SEMI?
    | DROP ROLE if_exists? identifier SEMI?
    | DROP MASKING POLICY if_exists? qualifiedName SEMI?
    | DROP ROW ACCESS POLICY if_exists? qualifiedName SEMI?
    | DROP CONTACT if_exists? qualifiedName SEMI?
    | DROP PROJECTION POLICY if_exists? qualifiedName SEMI?
    | DROP AGGREGATION POLICY if_exists? qualifiedName SEMI?
    | DROP JOIN POLICY if_exists? qualifiedName SEMI?
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
    | ALTER COMPUTE POOL if_exists? identifier computePoolAction SEMI?
    | ALTER CORTEX SEARCH SERVICE if_exists? qualifiedName cortexSearchAction SEMI?
    | ALTER STAGE if_exists? qualifiedName stageAction SEMI?
    | ALTER FILE FORMAT if_exists? qualifiedName fileFormatAction SEMI?
    | ALTER TAG if_exists? qualifiedName tagAction SEMI?
    | ALTER MASKING POLICY if_exists? qualifiedName maskingPolicyAction SEMI?
    | ALTER CONTACT if_exists? qualifiedName SET commentClause SEMI?
    | ALTER PROJECTION POLICY if_exists? qualifiedName projectionPolicyAction SEMI?
    | ALTER AGGREGATION POLICY if_exists? qualifiedName projectionPolicyAction SEMI?
    | ALTER JOIN POLICY if_exists? qualifiedName projectionPolicyAction SEMI?
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
    | USE SECONDARY ROLES (ALL | identifier (COMMA identifier)*) SEMI?
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
    // An ACCOUNT-level privilege names its scope: live-verified 2026-08-02, `GRANT CREATE DATABASE TO
    // ROLE r` is a syntax error on a real account ("unexpected 'TO'") while `GRANT CREATE DATABASE ON
    // ACCOUNT TO ROLE r` succeeds. Must precede the generic `privilegeList ON ACCOUNT` alternative so
    // two-word privileges (CREATE DATABASE, MONITOR USAGE, APPLY TAG, …) bind as one globalPrivilege.
    | GRANT globalPrivilegeList ON ACCOUNT TO ROLE identifier SEMI?  // GRANT global_privs ON ACCOUNT TO ROLE role_name
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
    // Same ACCOUNT scoping as GRANT — live 2026-08-02: `REVOKE CREATE DATABASE FROM ROLE r` is a
    // syntax error ("unexpected 'FROM'"), `REVOKE CREATE DATABASE ON ACCOUNT FROM ROLE r` succeeds.
    | REVOKE globalPrivilegeList ON ACCOUNT FROM ROLE identifier SEMI?  // REVOKE global_privs ON ACCOUNT FROM ROLE role_name
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
    | DEFAULT_WAREHOUSE EQ (identifier | STRING_LITERAL)
    | DEFAULT_NAMESPACE EQ (qualifiedName | STRING_LITERAL)
    | DEFAULT_SECONDARY_ROLES EQ LPAREN stringLiteralList RPAREN
    | LOGIN_NAME EQ (identifier | STRING_LITERAL)
    | DISPLAY_NAME EQ (identifier | STRING_LITERAL)
    | FIRST_NAME EQ STRING_LITERAL
    | MIDDLE_NAME EQ STRING_LITERAL
    | LAST_NAME EQ STRING_LITERAL
    | EMAIL EQ STRING_LITERAL
    | MUST_CHANGE_PASSWORD EQ booleanValue
    | DISABLED EQ booleanValue
    | TYPE EQ (identifier | STRING_LITERAL)
    | COMMENT EQ STRING_LITERAL
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
    | callStatement               // CALL <procedure>(...) — a task that just invokes a stored procedure
    | executeImmediateStatement   // EXECUTE IMMEDIATE expression
    | beginEndBlock               // A Snowflake Scripting block, with or without its DECLARE section
                                  // (live-verified). The block is written UNQUOTED, exactly like the
                                  // unquoted procedure body — a $$-quoted one is a syntax error there.
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
    // A warehouse size takes either spelling here — live accepts both the quoted 'MEDIUM' and the
    // bare MEDIUM (the sibling USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE is quoted-only).
    | SERVERLESS_TASK_MAX_STATEMENT_SIZE EQ (identifier | STRING_LITERAL)
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
    | SIZE_LIMIT EQ MINUS? INTEGER_LITERAL
    | PURGE EQ copyOptionValue
    | FORCE EQ copyOptionValue
    | MATCH_BY_COLUMN_NAME EQ copyOptionValue
    | optionKey EQ parenOptionList
    | identifier EQ copyOptionValue
    ;

copyIntoStageClause
    : FROM copyTableSource
    | FILE_FORMAT EQ LPAREN copyFormatOptions RPAREN
    | PARTITION BY expression
    | HEADER (EQ booleanValue)?
    | OVERWRITE EQ copyOptionValue
    | SINGLE EQ copyOptionValue
    | MAX_FILE_SIZE EQ MINUS? INTEGER_LITERAL
    | VALIDATION_MODE EQ copyOptionValue
    | optionKey EQ parenOptionList
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
    : AT identifier (DOT identifier)* stagePath?
    | AT TILDE stagePath?
    | AT (identifier DOT)* PERCENT (identifier | TABLE) stagePath?
    ;

// The optional trailing SLASH matches Snowflake, where unload targets are conventionally written as
// directories: COPY INTO @stage/some/dir/ FROM (query).
stagePath
    // TO joins the segment words because paths like @~/some/path/to/file.csv are routine and `to`
    // lexes as the keyword token; a full path-aware lexer mode is not worth the complexity.
    : ((SLASH | DOT) (identifier | INTEGER_LITERAL | TO | SOME | ANY | ALL))+ SLASH?
    | SLASH                                  // a bare trailing slash: @stage/
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
    | SKIP_HEADER EQ MINUS? INTEGER_LITERAL
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
    | MINUS? INTEGER_LITERAL
    | booleanValue
    | CONTINUE
    | AUTO
    // ERROR_LOGGING's value is the BAREWORD DEFAULT — live refuses the quoted spelling — and the
    // keyword is no identifier, so it joins CONTINUE and AUTO as an admitted bare value. Every
    // consumer validates its own values in Java, so widening here does not widen what they accept.
    | DEFAULT
    | qualifiedName
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
    | AUTO_SUSPEND EQ MINUS? INTEGER_LITERAL
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
    | GENERATION EQ (STRING_LITERAL | INTEGER_LITERAL)
    | COMMENT EQ STRING_LITERAL
    ;

stageProperties
    : URL EQ STRING_LITERAL stageOption*
    | stageOption+                       // internal stage declared by its options alone
    ;

stageOption
    : FILE_FORMAT EQ (STRING_LITERAL | parenOptionList | qualifiedName)
    | ENCRYPTION EQ (booleanValue | parenOptionList)
    | COMMENT EQ STRING_LITERAL
    | identifier EQ (parenOptionList | copyOptionValue)   // CREDENTIALS=(...), TEMPORARY-stage props, ...
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
    : REFRESH pipeRefreshOption*
    | SET pipeSetOption+   // PAUSE/RESUME are NOT Snowflake syntax; use SET PIPE_EXECUTION_PAUSED = TRUE/FALSE
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
    : sequenceOption (COMMA? sequenceOption)*
    ;

sequenceOption
    : START (WITH | EQ)? MINUS? INTEGER_LITERAL
    | INCREMENT (BY | EQ)? MINUS? INTEGER_LITERAL
    | ORDER
    | NOORDER
    | commentClause
    ;

sequenceAction
    : SET INCREMENT (BY | EQ) MINUS? INTEGER_LITERAL
    ;                             // RESTART is not a Snowflake sequence action (live: invalid property)

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
    : (ALTER | MODIFY) COLUMN identifier (tagSet | tagUnset)
    ;

warehouseAction
    // The two multi-word forms are single LEXER tokens: they add no parser decisions, so large
    // statements elsewhere keep their prediction complexity — an inline optional group after
    // RESUME degrades ALL(*) prediction across a big corpus.
    : RESUME_IF_SUSPENDED
    | RESUME
    | SUSPEND
    | ABORT_ALL_QUERIES
    | SET warehouseProperty+
    | tagUnset
    | UNSET warehouseUnsetProperty (COMMA warehouseUnsetProperty)*
    | RENAME TO identifier
    | tagSet
    ;

// The names UNSET accepts: the declared warehouse properties, plus a bare identifier so an
// unknown name parses and is refused with live's invalid-property wording. tagUnset is ordered
// first in warehouseAction because TAG itself lexes as an identifier.
warehouseUnsetProperty
    : WAREHOUSE_TYPE | WAREHOUSE_SIZE | AUTO_SUSPEND | AUTO_RESUME | MIN_CLUSTER_COUNT
    | MAX_CLUSTER_COUNT | SCALING_POLICY | INITIALLY_SUSPENDED | RESOURCE_MONITOR
    | MAX_CONCURRENCY_LEVEL | STATEMENT_QUEUED_TIMEOUT_IN_SECONDS | STATEMENT_TIMEOUT_IN_SECONDS
    | ENABLE_QUERY_ACCELERATION | QUERY_ACCELERATION_MAX_SCALE_FACTOR | GENERATION | COMMENT
    | identifier
    ;

databaseAction
    : RENAME TO identifier
    | SET COMMENT EQ STRING_LITERAL
    | SET optionKey EQ (parenOptionList | copyOptionValue)
    | SET READ_ONLY EQ booleanValue
    | UNSET READ_ONLY
    | tagSet
    | tagUnset
    ;

schemaAction
    : RENAME TO identifier
    | SET COMMENT EQ STRING_LITERAL
    | SET optionKey EQ (parenOptionList | copyOptionValue)
    | tagSet
    | tagUnset
    ;

tableAction
    : RENAME TO qualifiedName
    | SWAP WITH qualifiedName
    | ADD COLUMN? if_not_exists? columnDef (COMMA alterAddColumnItem)*
    | DROP CLUSTERING KEY
    // Automatic reclustering is paused and resumed on a CLUSTERED table; SHOW TABLES'
    // automatic_clustering cell follows it (live-verified: ON -> OFF -> ON).
    | (SUSPEND | RESUME) RECLUSTER
    | RENAME COLUMN identifier TO identifier
    // A constraint may be renamed, and its enforcement properties changed through either spelling
    // (live-verified). VALIDATE / NOVALIDATE are deliberately ABSENT: the documentation lists them,
    // live refuses both as syntax errors, and admitting them would be leniency.
    | RENAME CONSTRAINT identifier TO identifier
    | (ALTER | MODIFY) CONSTRAINT identifier constraintProperty+
    // ALTER TABLE t { ALTER | MODIFY } [(] [COLUMN] c1 action [, [COLUMN] c2 action]* [)] — the
    // column-action list. COLUMN is optional per item, the parens are optional but balanced.
    | (ALTER | MODIFY) LPAREN alterColumnItem (COMMA alterColumnItem)* RPAREN
    | (ALTER | MODIFY) alterColumnItem (COMMA alterColumnItem)*
    | SET COMMENT EQ STRING_LITERAL
    // One SET may carry SEVERAL properties, separated by a space or a comma (live-verified, and the
    // same property may even repeat there — unlike CREATE TABLE, where a repeat is refused). Each
    // pair is its own subrule so key and value stay paired however many there are.
    | SET tableSetProperty (COMMA? tableSetProperty)*
    | CLUSTER BY LPAREN expressionList RPAREN
    | ADD tableConstraint
    | DROP CONSTRAINT identifier
    | DROP PRIMARY KEY
    | DROP UNIQUE LPAREN identifierList RPAREN
    | DROP FOREIGN KEY LPAREN identifierList RPAREN
    | ALTER COLUMN identifier SET MASKING POLICY qualifiedName (USING LPAREN identifierList RPAREN)? FORCE?
    | ALTER COLUMN identifier UNSET MASKING POLICY
    | ADD DATA METRIC FUNCTION qualifiedName ON LPAREN identifierList? RPAREN
    | DROP DATA METRIC FUNCTION qualifiedName ON LPAREN identifierList? RPAREN
    | MODIFY DATA METRIC FUNCTION qualifiedName ON LPAREN identifierList? RPAREN (SUSPEND | RESUME)
    | ADD SEARCH OPTIMIZATION (ON searchOptimizationTarget (COMMA searchOptimizationTarget)*)?
    | DROP SEARCH OPTIMIZATION (ON searchOptimizationDrop (COMMA searchOptimizationDrop)*)?
    | SET aggregationPolicyClause FORCE?
    | UNSET AGGREGATION POLICY
    | SET joinPolicyClause FORCE?
    | UNSET JOIN POLICY
    | ALTER COLUMN identifier SET PROJECTION POLICY qualifiedName FORCE?
    | ALTER COLUMN identifier UNSET PROJECTION POLICY
    | SET CONTACT contactAssignment (COMMA contactAssignment)*
    | UNSET CONTACT contactPurpose (COMMA contactPurpose)*
    | ADD ROW ACCESS POLICY qualifiedName ON LPAREN identifierList RPAREN
    | DROP ROW ACCESS POLICY qualifiedName
    // Detaches whatever is attached and is a no-op when nothing is (live-verified). Only the plural
    // spelling parses — live refuses `DROP ALL ROW ACCESS POLICY` at the POLICY token.
    | DROP ALL ROW ACCESS POLICIES
    // COLUMN is optional (live-verified: `ALTER TABLE t DROP c`, a comma list and the IF EXISTS form
    // all run). It sits AFTER the specific DROP alternatives so `DROP CLUSTERING KEY` — CLUSTERING
    // being a legal identifier — is never a candidate column name.
    | DROP COLUMN? if_exists? identifier (COMMA identifier)*
    | tagSet
    | tagUnset
    | columnTagAction
    | tableUnsetProperties
    ;

// ALTER TABLE ... UNSET prop[, prop]* — its own subrule so the masking-policy alternative's UNSET
// keeps a single-token accessor in tableAction.
tableUnsetProperties
    : UNSET optionKey (COMMA optionKey)*
    ;

// One key=value of an ALTER TABLE … SET, kept paired with its value.
tableSetProperty
    : optionKey EQ (parenOptionList | copyOptionValue)
    ;

// An enforcement property of ALTER TABLE … {ALTER | MODIFY} CONSTRAINT. Several may be given at
// once (live-verified: `NOT ENFORCED NORELY` runs).
constraintProperty
    : NOT? ENFORCED
    | relyOption
    ;

cortexSearchOption
    : WAREHOUSE EQ identifier
    | TARGET_LAG EQ STRING_LITERAL
    | EMBEDDING_MODEL EQ STRING_LITERAL
    | COMMENT EQ STRING_LITERAL
    ;

cortexSearchAction
    : SET cortexSearchOption+
    | UNSET COMMENT
    ;

computePoolOption
    : MIN_NODES EQ INTEGER_LITERAL
    | MAX_NODES EQ INTEGER_LITERAL
    | INSTANCE_FAMILY EQ identifier
    | AUTO_RESUME EQ booleanValue
    | INITIALLY_SUSPENDED EQ booleanValue
    | AUTO_SUSPEND_SECS EQ INTEGER_LITERAL
    | COMMENT EQ STRING_LITERAL
    | PLACEMENT_GROUP EQ STRING_LITERAL
    | BACKUP_INSTANCE_FAMILIES EQ LPAREN stringLiteralList RPAREN
    ;

computePoolAction
    : SUSPEND
    | RESUME
    | STOP ALL (OF TYPE computePoolWorkloadType (COMMA computePoolWorkloadType)*)?
    | tagSet
    | tagUnset
    | SET computePoolSetOption+
    | UNSET computePoolUnsetKey (COMMA computePoolUnsetKey)*
    ;

computePoolWorkloadType
    : identifier
    | ALL
    | USER
    ;

computePoolSetOption
    : MIN_NODES EQ INTEGER_LITERAL
    | MAX_NODES EQ INTEGER_LITERAL
    | AUTO_RESUME EQ booleanValue
    | AUTO_SUSPEND_SECS EQ INTEGER_LITERAL
    | INSTANCE_FAMILY EQ identifier
    | PLACEMENT_GROUP EQ STRING_LITERAL
    | BACKUP_INSTANCE_FAMILIES EQ LPAREN stringLiteralList RPAREN
    | COMMENT EQ STRING_LITERAL
    ;

computePoolUnsetKey
    : AUTO_RESUME
    | AUTO_SUSPEND_SECS
    | PLACEMENT_GROUP
    | BACKUP_INSTANCE_FAMILIES
    | COMMENT
    ;

alterColumnItem
    : COLUMN? identifier alterColumnItemAction
    ;

alterColumnItemAction
    : ((SET DATA)? TYPE)? dataTypeName typeParameters? collateClause?
    // SET is optional before NOT NULL (live-verified: `ALTER COLUMN c NOT NULL` runs, as does the
    // MODIFY spelling and the one that omits COLUMN as well). DROP NOT NULL has no such variant.
    | SET? NOT NULL
    | DROP NOT NULL
    | SET DEFAULT defaultExpression
    | DROP DEFAULT
    | COMMENT STRING_LITERAL
    | UNSET COMMENT
    ;

viewAction
    : RENAME TO identifier
    | SET COMMENT EQ STRING_LITERAL
    | ADD ROW ACCESS POLICY qualifiedName ON LPAREN identifierList RPAREN
    | DROP ROW ACCESS POLICY qualifiedName
    | DROP ALL ROW ACCESS POLICIES
    | tagSet
    | tagUnset
    ;

materializedViewAction
    : SUSPEND
    | RESUME
    | REFRESH
    | RENAME TO identifier
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
    | SET FILE_FORMAT EQ (STRING_LITERAL | parenOptionList | qualifiedName)
    | SET COMMENT EQ STRING_LITERAL
    | RENAME TO identifier
    | UNSET optionKey
    | SET identifier EQ (STRING_LITERAL | parenOptionList)
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
    | RENAME TO identifier
    ;

maskingPolicyAction
    : RENAME TO identifier
    ;

// What a projection policy takes after ALTER: renamed, re-commented, or given a new body.
projectionPolicyAction
    : RENAME TO identifier
    | SET commentClause
    | UNSET COMMENT
    | SET BODY THIN_ARROW booleanExpr
    ;

rowAccessPolicyAction
    : RENAME TO identifier
    ;

fileFormatAction
    : RENAME TO qualifiedName
    | SET? copyFormatOption+
    ;

routineAlterAction
    : RENAME TO qualifiedName
    | SET COMMENT EQ STRING_LITERAL
    | UNSET COMMENT
    ;

userAction
    : RENAME TO identifier
    | SET userProperty+
    | SET COMMENT EQ STRING_LITERAL
    | UNSET userUnsetProperty (COMMA userUnsetProperty)*
    ;

// The property names UNSET takes. `identifier` is a catch-all so an unknown name PARSES and the
// handler can report it the way a real account does — `invalid property 'X' for 'USER'` is a
// compilation error there, not a syntax error.
userUnsetProperty
    : PASSWORD
    | LOGIN_NAME
    | DISPLAY_NAME
    | FIRST_NAME
    | MIDDLE_NAME
    | LAST_NAME
    | EMAIL
    | DEFAULT_ROLE
    | DEFAULT_WAREHOUSE
    | DEFAULT_NAMESPACE
    | DEFAULT_SECONDARY_ROLES
    | MUST_CHANGE_PASSWORD
    | DISABLED
    | COMMENT
    | TYPE
    | identifier
    ;

roleAction
    : RENAME TO identifier
    | SET COMMENT EQ STRING_LITERAL
    ;

sessionAction
    : SET sessionAssignment (COMMA sessionAssignment)*
    | UNSET sessionParameter (COMMA sessionParameter)*
    ;

sessionAssignment
    : sessionParameter EQ literal
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
    // columnDefName, not identifier: INNER/JOIN/LEFT/CROSS and CASE are live-legal column names in a
    // column DEFINITION (measured — CREATE TABLE kw (inner INT, join INT, case INT, …) runs).
    : columnDefName dataTypeName typeParameters?  columnConstraint* columnCommentClause?
    ;

typeParameters
    : LPAREN (INTEGER_LITERAL (COMMA INTEGER_LITERAL)?)? RPAREN   // nvarchar() — empty parens allowed
    ;

tableConstraint
    : constraintName? PRIMARY KEY LPAREN identifierList RPAREN relyOption?
    | constraintName? UNIQUE LPAREN identifierList RPAREN relyOption?
    | constraintName? FOREIGN KEY LPAREN identifierList RPAREN REFERENCES qualifiedName LPAREN identifierList RPAREN referentialActions? relyOption?
    | checkConstraint
    ;

// A CHECK constraint, named or not. The enforcement suffix is optional HERE — a CREATE TABLE takes
// the constraint bare, with ENABLE NOVALIDATE or with NOT ENFORCED (all live-verified) — while the
// ALTER form insists on ENABLE NOVALIDATE, which the handler enforces rather than the grammar so the
// refusal is live's sentence and not a syntax error.
checkConstraint
    : constraintName? CHECK LPAREN booleanExpr RPAREN checkEnforcement?
    ;

checkEnforcement
    : ENABLE NOVALIDATE
    | NOT ENFORCED
    ;

constraintName
    : CONSTRAINT identifier
    ;

// One additional column in a multi-column ALTER TABLE ... ADD. Kept as a SUBRULE so the repeated
// COLUMN / IF NOT EXISTS stay out of tableAction itself: repeating a token inside one alternative
// turns the whole rule's accessor into a List, silently breaking every existing `X() != null` guard.
alterAddColumnItem
    : COLUMN? if_not_exists? columnDef
    ;

referentialActions
    : referentialAction+
    ;

// DROP DATABASE/SCHEMA … [CASCADE | RESTRICT] — CASCADE drops the contained objects too, RESTRICT (the
// default) refuses when the schema still has objects in it.
dropBehavior
    : CASCADE
    | RESTRICT
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
structuredFieldList
    : structuredField (COMMA structuredField)*
    ;

structuredField
    : structuredFieldName dataTypeName typeParameters? (NOT? NULL)?
    ;

// Live 2026-08-05 (field/column keyword matrix): INNER, JOIN, CASE, LEFT and CROSS are legal
// unquoted structured-field names — they are legal COLUMN names too, but opening the general
// identifier rule to them would have to disambiguate join parsing, so only the unambiguous field
// position is opened here; the column half is tracked as a round-8 lead. SELECT/FROM/ORDER/GROUP/
// TABLE/NOT/NULL/WHERE/AND were all measured rejected in both positions.
structuredFieldName
    : identifier
    | INNER | JOIN | CASE | LEFT | CROSS
    ;

dateTimeLiteralType
    : DATE | TIME | TIMESTAMP    // only these three; TIMESTAMP_NTZ '...' etc are syntax errors in Snowflake
    ;

dataTypeName
    : INTEGER | INT | BIGINT | SMALLINT | TINYINT | BYTEINT | NUMBER | DECIMAL | NUMERIC | DEC | DECFLOAT | FLOAT | FLOAT4 | FLOAT8 | DOUBLE PRECISION? | REAL
    | VARCHAR | STRING | TEXT | BOOLEAN | DATE | DATETIME | TIME | TIMESTAMP_NTZ | TIMESTAMPNTZ | TIMESTAMP_LTZ | TIMESTAMP_TZ | VARIANT
    | TIMESTAMPLTZ | TIMESTAMPTZ | TIMESTAMP WITH LOCAL TIME ZONE | TIMESTAMP     // TIMESTAMP last: the worded form must win the prediction
    // `X VARYING` is only legal after the three FIXED-length spellings — live 2026-08-04,
    // `VARCHAR VARYING`, `NVARCHAR VARYING` and `NVARCHAR2 VARYING` are all syntax errors.
    | (CHAR | CHARACTER | NCHAR) VARYING? | NVARCHAR | NVARCHAR2
    | ARRAY (LPAREN dataTypeName typeParameters? RPAREN)?      // structured ARRAY(INT)
    // Structured OBJECT(a CHAR NOT NULL, ...). The ZERO-FIELD spelling `OBJECT()` is legal and is its own
    // type — live-verified: `SYSTEM$TYPEOF(CAST(OBJECT_CONSTRUCT() AS OBJECT()))` is `OBJECT()[LOB]`, an
    // empty object casts to it, and a NON-empty one fails the schema check. OBJECT is the only one of the
    // three with that form: `ARRAY()` and `MAP()` are syntax errors live. (`ARRAY()` still PARSES here as
    // bare ARRAY plus an empty `typeParameters` — empty parentheses being a character-type leniency — so
    // DataTypeParser rejects it there rather than in the grammar.)
    | OBJECT (LPAREN structuredFieldList? RPAREN)?
    | BINARY | VARBINARY
    | UUID
    | VECTOR LPAREN (FLOAT | INT) COMMA INTEGER_LITERAL RPAREN
    | GEOGRAPHY | GEOMETRY
    // The FILE column type (a stage-file reference). `FILE` stays a plain identifier too — live-verified
    // 2026-08-03: a column, an alias, a table and a scripting variable may all be named `file` — so this
    // alternative only adds the TYPE position. FILE takes NO type parameters (`FILE(10)` and `FILE()` are
    // both syntax errors live) and is not a legal structured element type (`ARRAY(FILE)`, `OBJECT(x FILE)`
    // and `MAP(VARCHAR, FILE)` all fail "Unsupported data type 'FILE'."); `dataTypeName typeParameters?`
    // and the nested-type positions are shared by every type, so DataTypeParser rejects both there.
    | FILE
    | MAP LPAREN dataTypeName COMMA dataTypeName RPAREN   // MAP(keyType, valueType); backed by OBJECT.
                                                          // The bare `MAP` spelling is a syntax error in
                                                          // Snowflake (live-verified: `NULL::MAP`).
    ;

columnConstraint
    : PRIMARY KEY relyOption?
    | NOT? NULL
    | UNIQUE relyOption?
    | tagList
    // A masking policy attaches in the column DEFINITION too, not only through ALTER COLUMN —
    // live-verified on CREATE TABLE and on ALTER TABLE ADD COLUMN, with WITH optional exactly as it
    // is for TAG, and the conditional USING form available here as well.
    | WITH? MASKING POLICY qualifiedName (USING LPAREN identifierList RPAREN)?
    | WITH? PROJECTION POLICY qualifiedName
    | CHECK LPAREN booleanExpr RPAREN checkEnforcement?
    | AUTOINCREMENT identityProperties?
    | IDENTITY identityProperties?
    | DEFAULT defaultExpression
    | REFERENCES qualifiedName (LPAREN identifier RPAREN)? referentialActions? relyOption?
    | FOREIGN KEY REFERENCES qualifiedName (LPAREN identifier RPAREN)? referentialActions? relyOption?
    | collateClause
    ;

// AUTOINCREMENT / IDENTITY seed + step: (start, step), or any mix of START <n> / INCREMENT <n> /
// ORDER / NOORDER word options in any order (each may appear alone); values may be negative.
identityProperties
    : LPAREN signedInteger COMMA signedInteger RPAREN (ORDER | NOORDER)?
    | identityWordOption+
    ;

identityWordOption
    : START signedInteger
    | INCREMENT signedInteger
    | ORDER
    | NOORDER
    ;

signedInteger
    : MINUS? INTEGER_LITERAL
    ;

defaultExpression
    : expression
    ;

relyOption
    : RELY
    | NORELY
    ;

// COPY GRANTS on CREATE [MATERIALIZED] VIEW / TABLE — accepted; grants are not copied (the security
// model keeps per-object grants, and the emulator has no source-object grant transfer semantics).
copyGrants
    : COPY GRANTS
    ;

// [WITH] TAG (k1='v1', k2='v2') at creation time, on tables, schemas and views. The tags are
// recorded so that TAG_REFERENCES can report them; they do not affect query results.
tagList
    : WITH? TAG LPAREN tagAssignment (COMMA tagAssignment)* RPAREN
    ;

tagAssignment
    : qualifiedName EQ STRING_LITERAL
    ;

// [WITH] ROW ACCESS POLICY p ON (col1, col2) at CREATE time — attached like the ALTER form.
rowAccessPolicyClause
    : WITH? ROW ACCESS POLICY qualifiedName ON LPAREN identifierList RPAREN
    ;

// A parenthesized option group: CREDENTIALS=(AWS_KEY_ID='..' ...), ENCRYPTION=(TYPE='..'),
// STAGE_FILE_FORMAT=(TYPE=CSV ...), FILE_FORMAT=(FORMAT_NAME=db.s.ff). May be empty.
parenOptionList
    : LPAREN parenOption* RPAREN
    ;

parenOption
    : optionKey EQ (copyOptionValue | LPAREN stringLiteralList? RPAREN)
    ;

// Option keys are mostly IDENTIFIER, but several lex as dedicated keyword tokens elsewhere.
optionKey
    : identifier
    | ON_ERROR | SIZE_LIMIT | PURGE | MATCH_BY_COLUMN_NAME | TYPE | FIELD_DELIMITER | RECORD_DELIMITER
    | COMPRESSION | DATE_FORMAT | ESCAPE | SKIP_HEADER | PATTERN | FORCE | HEADER | OVERWRITE | SINGLE
    | MAX_FILE_SIZE | VALIDATION_MODE | ENCRYPTION | FILE_FORMAT | DATA_RETENTION_TIME_IN_DAYS
    | ENABLE
    ;

// EQUALITY(col) / SUBSTRING(col) / FULL_TEXT(col) / GEO(col), or EQUALITY(*) for every column. The
// method is an ordinary identifier — live lets all four words name tables and columns too.
searchOptimizationTarget
    : identifier LPAREN (STAR | qualifiedName) RPAREN
    ;

// A DROP may name the expression or its number (live-verified: DROP … ON 1 removes expression 1).
searchOptimizationDrop
    : searchOptimizationTarget
    | INTEGER_LITERAL
    ;

// An aggregation policy attaches to the TABLE, optionally naming the columns whose DISTINCT values
// count as one entity for the minimum-group-size test (live-verified: without it rows are counted).
aggregationPolicyClause
    : AGGREGATION POLICY qualifiedName (ENTITY KEY LPAREN identifierList RPAREN)?
    ;

// A join policy attaches to the TABLE and takes no arguments of its own.
joinPolicyClause
    : JOIN POLICY qualifiedName
    ;

// CREATE TABLE tail modifiers, in any order: comments, clustering, tags, a row access policy.
tableTailOption
    : commentClause
    | clusterByClause
    | tagList
    | rowAccessPolicyClause
    | WITH? aggregationPolicyClause
    | WITH? joinPolicyClause
    | optionKey EQ (parenOptionList | copyOptionValue)   // CHANGE_TRACKING=TRUE etc. — accepted, not modeled
    ;

// CREATE VIEW key=value properties (CHANGE_TRACKING=TRUE, ...) — accepted, not modeled.
viewProperty
    : optionKey EQ copyOptionValue
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
    : INSERT OVERWRITE? INTO objectName columnListOptional? VALUES valueTupleList SEMI?
    | INSERT OVERWRITE? INTO objectName columnListOptional? selectStatement SEMI?
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
    // Column-NAME list: the keyword names (incl. CASE) are live-legal here — measured,
    // INSERT INTO kw (inner, join, case, left, cross) VALUES (…) runs on a real account.
    : LPAREN namePart (COMMA namePart)* RPAREN
    ;

identifierList
    : identifier (COMMA identifier)*
    ;

viewColumnList
    : viewColumnDef (COMMA viewColumnDef)*
    ;

viewColumnDef
    : identifier columnCommentClause?
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
    : UPDATE objectName (AS? identifier)? (FROM tableReference (COMMA tableReference | joinClause)*)? SET assignmentList (FROM tableReference (COMMA tableReference | joinClause)*)? whereClause? SEMI?
    ;   // Snowflake also accepts the FROM clause BEFORE SET (UPDATE t u FROM (...) b SET ... WHERE ...)

deleteStatement
    : DELETE FROM objectName (AS? identifier)? (USING tableReference (COMMA tableReference | joinClause)*)? whereClause? SEMI?
    ;

mergeStatement
    : MERGE INTO qualifiedName (AS? identifier)?
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
    | WHEN NOT MATCHED (AND booleanExpr)? THEN INSERT mergeInsertColumnList? VALUES valueTuple
    ;

// The MERGE INSERT target column list allows an optional target-table-alias qualifier on each column
// (e.g. INSERT (t.col1, t.col2) ...), mirroring the UPDATE SET assignment target; the qualifier is
// redundant (the target is fixed) and is dropped at execution. A plain `col` is the single-identifier case.
mergeInsertColumnList
    : LPAREN mergeInsertColumn (COMMA mergeInsertColumn)* RPAREN
    ;

mergeInsertColumn
    : (identifier DOT)? identifier
    ;

assignmentList
    : assignment (COMMA assignment)*
    ;

assignment
    // namePart targets: UPDATE kw SET inner = 10 is live-legal (measured).
    : (identifier '.')? namePart EQ expression
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
    // The CTE name takes the full NAME vocabulary, not the column one: live accepts `WITH case AS (…)`
    // and `WITH asof AS (…)` alike (measured for all five name words and all eight join words).
    : nameStartPart columnListOptional? AS LPAREN selectStatement RPAREN   // AS is REQUIRED (live-Snowflake verified)
    ;

// A hierarchical query replaces the grouping tail: Snowflake rejects GROUP BY, HAVING and QUALIFY after
// a CONNECT BY (live-verified — all three are syntax errors), so the two tails are alternatives here.
// The grouping tail is legal without FROM (live-verified: WHERE, GROUP BY, HAVING and QUALIFY all
// parse on a FROM-less select; CONNECT BY parses too but is then refused semantically with
// "missing FROM clause", enforced in the visitor).
selectClause
    : SELECT (DISTINCT | ALL)? topClause? selectList
      (FROM tableExpression)? whereClause? (connectByClause | groupByClause? havingClause? qualifyClause?)
    ;

// Snowflake hierarchical query: START WITH may come before or after CONNECT BY, but CONNECT BY is
// mandatory — a lone `START WITH` is a syntax error (live-verified). Without START WITH every row seeds
// its own tree. The condition is an ordinary predicate in which PRIOR marks the parent-row side.
connectByClause
    : startWithClause connectByPredicate
    | connectByPredicate startWithClause?
    ;

startWithClause
    : START WITH booleanExpr
    ;

connectByPredicate
    : CONNECT BY booleanExpr
    ;

setOperator
    : UNION ALL? (BY identifier)?   // UNION [ALL] BY NAME aligns columns by name; identifier must be NAME
    | INTERSECT ALL?
    | EXCEPT ALL?
    | MINUS_KW ALL?   // MINUS is a Snowflake synonym for EXCEPT
    ;

tableExpression
    : tableReference (COMMA tableReference | joinClause)* COMMA?   // trailing comma allowed (Snowflake), as in selectList
    ;

tableReference
    // PIVOT/UNPIVOT come AFTER the (optionally aliased) source and may carry their own trailing alias, e.g.
    // FROM (subquery) src PIVOT(...) p — so they live here, not glued to tableSource.
    // The bare-alias alternative carries a predicate for the unreserved EXCEPT: `FROM t except` is an
    // alias only when what follows cannot START a set-operation right-hand side — otherwise
    // `FROM t EXCEPT SELECT ...` must stay a set-op (both parses reach EOF, so lookahead alone
    // cannot decide).
    : LATERAL? tableSource (AS aliasName (LPAREN identifierList RPAREN)?
        | {exceptBareAliasAllowed()}? nonJoinKeywordIdentifier (LPAREN identifierList RPAREN)?)?
      (pivotClause pivotAlias? | unpivotClause pivotAlias?)?
      sampleClause?
    ;

// A pivot alias may carry an ANSI derived-column alias list that renames the pivoted result columns,
// e.g. PIVOT(SUM(amount) FOR quarter IN ('Q1','Q2')) AS p (empid, q1, q2).
pivotAlias
    : AS? identifier (LPAREN identifierList RPAREN)?
    ;

sampleClause
    : (SAMPLE | TABLESAMPLE) sampleMethod? LPAREN (INTEGER_LITERAL | FLOAT_LITERAL | SESSION_VAR_REF) ROWS? RPAREN sampleSeed?
    ;

// BLOCK (and any future method word) arrives via the identifier fallback — the clause is anchored
// between SAMPLE/TABLESAMPLE and the parenthesized size, so a loose word cannot leak elsewhere.
sampleMethod
    : BERNOULLI | ROW | SYSTEM | identifier
    ;

sampleSeed
    : (REPEATABLE | SEED) LPAREN INTEGER_LITERAL RPAREN
    ;

tableSource
    : TABLE LPAREN expression RPAREN    // Table function call
    | DIRECTORY LPAREN stageRef RPAREN  // Directory table: file-level metadata of a stage
    | stageRef stageQueryParams?        // Query staged files: $1..$n fields + metadata$ columns
    // NOTE: a quoted 'FROM <string>' source is deliberately NOT supported — even predicate-gated,
    // STRING_LITERAL as a tableSource lets ALL(*) re-derive FROM (VALUES (...), (...)) tuple lists
    // as a parenthesized join and reject them. Verified twice; do not reintroduce.
    | FLATTEN LPAREN flattenArgList RPAREN  // LATERAL FLATTEN(expr [, name => val ...])
    | KW_IDENTIFIER LPAREN expression RPAREN  // IDENTIFIER(expr) — dynamic table name
    | POSITIONAL_PARAMETER              // $n — a prior flow-chain stage's result (n statements back)
    | tableQualifiedName timeTravelClause?
    | LPAREN selectStatement RPAREN
    | LPAREN tableReference (COMMA tableReference | joinClause)+ RPAREN  // parenthesized FROM join: FROM (a JOIN b ON c ...) — pure grouping, keeps inner aliases in scope. The '+' (>=1 join/comma) disambiguates from (SELECT ...) and (single_table).
    | LPAREN VALUES valueTupleList (AS? identifier columnListOptional?)? RPAREN  // (VALUES (...) [AS] v (cols)) as subquery — Snowflake allows the alias inside the parens
    | VALUES valueTupleList                // Inline values
    | tableFunctionExpr    // bare table function: LATERAL SPLIT_TO_TABLE(x, ',') — no TABLE() wrapper
    ;

// A bare table-function call used directly as a FROM source. Kept as its own rule (not inline
// `expression`) so it cannot shadow a plain table name — it requires the parenthesised call.
tableFunctionExpr
    : functionName LPAREN RPAREN
    | functionName LPAREN expression (COMMA expression)* RPAREN
    | functionName LPAREN expression (COMMA expression)* (COMMA namedArgument)+ RPAREN
    | functionName LPAREN namedArgument (COMMA namedArgument)* RPAREN
    ;

// SELECT ... FROM @stage (FILE_FORMAT => 'name', PATTERN => 'regex'): named read parameters.
stageQueryParams
    : LPAREN stageQueryParam (COMMA stageQueryParam)* RPAREN
    ;

stageQueryParam
    : (FILE_FORMAT | PATTERN | identifier) ARROW (STRING_LITERAL | qualifiedName)
    ;

// FLATTEN argument list: positional INPUT followed by optional named params, or all named
flattenArgList
    : expression (COMMA namedArgument)*   // positional INPUT [, name => val ...]
    | namedArgumentList                   // all named
    ;

// USING (c1, t2.c2, ...) — Snowflake tolerates qualified names here; only the column part joins.
usingColumnList
    : qualifiedName (COMMA qualifiedName)*
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
    | STREAM ARROW expression
    ;

changesClause
    : CHANGES LPAREN INFORMATION ARROW (DEFAULT | identifier) RPAREN
      (AT_KEYWORD LPAREN timeTravelPoint RPAREN | BEFORE LPAREN timeTravelPoint RPAREN)?
      (END LPAREN timeTravelPoint RPAREN)?
    ;

joinClause
    // DIRECTED is an accepted no-op join modifier (e.g. INNER DIRECTED JOIN): a semantic annotation with no
    // effect on the row-level result, so it parses like a plain join of that type.
    // ASOF is its own modifier group: Snowflake accepts only the bare `ASOF JOIN` (live-verified — LEFT /
    // RIGHT / INNER ASOF and NATURAL ASOF are syntax errors), and MATCH_CONDITION must come BEFORE the
    // ON / USING clause (`ON … MATCH_CONDITION (…)` is a syntax error there).
    : (NATURAL? joinType? DIRECTED? | ASOF) JOIN LATERAL? tableReference asofMatchCondition?
      (ON booleanExpr | USING LPAREN usingColumnList RPAREN)?
    ;

// MATCH_CONDITION ( <left expr> {>= | > | <= | <} <right expr> ) — the closest-match predicate of an
// ASOF JOIN. The optional alias in front exists because Snowflake accepts LIMIT as the right-hand
// table's alias here (live-verified: `ASOF JOIN r LIMIT MATCH_CONDITION (q.t > LIMIT.t)` runs); LIMIT
// is deliberately not a general bare alias in this grammar (it would shadow the LIMIT clause), so it is
// admitted only in this position, where the following MATCH_CONDITION anchors it. OFFSET needs no
// special case — it already reaches this position through the ordinary alias rule.
asofMatchCondition
    : (AS? LIMIT)? MATCH_CONDITION LPAREN booleanExpr RPAREN
    ;

joinType
    : INNER
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    | CROSS
    ;

pivotClause
    : PIVOT LPAREN aggregateFunction FOR identifier IN LPAREN pivotInList RPAREN
      (DEFAULT ON NULL LPAREN expression RPAREN)? RPAREN
    ;

// The pivot column set: an explicit value list, ANY (dynamic: the distinct FOR-column values,
// optionally ordered), or a subquery producing the values.
pivotInList
    : pivotValueList
    | ANY (ORDER BY expression (COMMA expression)*)?
    | selectStatement
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
    : STAR starModifier*                                  # StarItem
    | starQualifiedName DOT STAR starModifier*            # QualifiedStarItem
    // Snowflake's BRACED star is an OBJECT constructor over the row, not a star: {*} is
    // OBJECT_CONSTRUCT(*) and projects ONE column whose keys are the star's effective column names
    // ({* EXCLUDE (c)}, {t.*} pick which columns take part). It therefore needs its own alternative —
    // treating the braces as optional decoration on the star above expanded it to N columns instead.
    // The bare (AS-less) alias carries a predicate for the unreserved EXCEPT: `SELECT 1 except` is
    // an alias only when what follows cannot START a set-operation right-hand side — otherwise
    // `SELECT 1 EXCEPT SELECT 2` must stay a set-op (both parses reach EOF, so lookahead alone
    // cannot decide).
    | LBRACE (starQualifiedName DOT)? STAR starModifier* RBRACE
        (AS aliasName | {exceptBareAliasAllowed()}? identifier)?  # ObjectStarItem
    | booleanExpr (AS aliasName | {exceptBareAliasAllowed()}? identifier)?  # ExprItem
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

// The super-group forms come FIRST, and the order is load-bearing: rollup and cube are legal column
// names live, so they are ordinary identifiers here too, and `ROLLUP(g)` would otherwise read as a
// function call by the expression alternative alone. Matching the keyword form first leaves a bare
// `GROUP BY rollup` — the column — to fall through to expression, which is what live does with both.
groupByElement
    : ROLLUP LPAREN groupByColumnList RPAREN             // ROLLUP(a, b, c)
    | CUBE LPAREN groupByColumnList RPAREN               // CUBE(a, b, c)
    | GROUPING SETS LPAREN groupingSetList RPAREN        // GROUPING SETS((a,b),(a),(b),())
    | expression                                         // plain column/expression
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
    : PARTITION BY LPAREN expressionList RPAREN
    | PARTITION BY expressionList
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

// LIMIT takes a literal or a BIND, never a general expression — live-verified: `LIMIT :batch_size`
// runs (the Scripting doc's batch-processing loop is written that way) while `LIMIT 1+1` is a syntax
// error. OFFSET binds the same way. A STRING literal parses but only the EMPTY string survives the
// executor's check (live: LIMIT '' means no limit, OFFSET '' means zero, any other string is a
// syntax error at the literal).
limitClause
    : LIMIT (INTEGER_LITERAL | NULL | STRING_LITERAL | COLON identifier)
      (OFFSET (INTEGER_LITERAL | STRING_LITERAL | COLON identifier))?
    ;

// Every part of the ANSI spelling is optional but FETCH and the count: FETCH 2, FETCH FIRST 2,
// FETCH NEXT 2 ROWS ONLY, and OFFSET 1 [ROWS] FETCH … all run (live-verified); a bare OFFSET
// without a FETCH stays a syntax error, exactly as on the account.
fetchClause
    : (OFFSET (INTEGER_LITERAL | COLON identifier) (ROW | ROWS)?)?
      FETCH (FIRST | NEXT)? (INTEGER_LITERAL | COLON identifier) (ROW | ROWS)? ONLY?
    ;

// WORK is the SQL-standard spelling of TRANSACTION and live accepts it on all three statements
// (live-verified: BEGIN WORK / COMMIT WORK / ROLLBACK WORK).
//
// Only the OPENING statement takes a NAME — `COMMIT NAME x` is a syntax error live.
transactionStatement
    : BEGIN (WORK | TRANSACTION)? transactionName? SEMI?
    | START TRANSACTION transactionName? SEMI?
    | COMMIT WORK? SEMI?
    | ROLLBACK WORK? SEMI?
    ;

// The name is an IDENTIFIER, never a string literal (live refuses `BEGIN NAME 'a string'`), and it
// replaces the UUID an unnamed transaction carries in SHOW TRANSACTIONS' name cell — folded upper
// unquoted, case-preserved quoted. A DOTTED name is accepted and only its FIRST part is kept
// (live-verified: `BEGIN NAME a.b` shows 'A'), which is why this takes a qualifiedName.
transactionName
    : NAME qualifiedName
    ;

listStatement
    : LIST AT qualifiedName (PATTERN EQ STRING_LITERAL)? SEMI?
    ;

// Stage file operations: PUT (local → stage), GET (stage → local), REMOVE/RM (delete staged files).
putStatement
    : PUT (STRING_LITERAL | FILE_URL) stageRef stageFileOption* SEMI?
    ;

getStatement
    : GET stageRef (STRING_LITERAL | FILE_URL) stageFileOption*
    ;

removeStatement
    : (REMOVE | RM) stageRef (PATTERN EQ STRING_LITERAL)? SEMI?
    ;

stageFileOption
    : optionKey EQ copyOptionValue
    ;

showStatement
    : SHOW TERSE? DATABASES HISTORY? (LIKE STRING_LITERAL)? showTail SEMI?
    | SHOW TERSE? SCHEMAS (LIKE STRING_LITERAL)? (IN (ACCOUNT | DATABASE identifier))? showTail SEMI?
    | SHOW TERSE? TABLES HISTORY? (LIKE STRING_LITERAL)? (IN (ACCOUNT | (DATABASE | SCHEMA)? qualifiedName))? showTail SEMI?
    | SHOW TERSE? ICEBERG TABLES (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA)? qualifiedName)? showTail SEMI?
    | SHOW TERSE? VIEWS (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA | ACCOUNT)? qualifiedName?)? showTail SEMI?
    | SHOW TERSE? MATERIALIZED VIEWS (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA)? qualifiedName)? showTail SEMI?
    | SHOW TERSE? DYNAMIC TABLES (LIKE STRING_LITERAL)? (IN (ACCOUNT | (DATABASE | SCHEMA)? qualifiedName))? showTail SEMI?
    | SHOW HYBRID TABLES (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA)? qualifiedName)? SEMI?
    | SHOW TERSE? COLUMNS (LIKE STRING_LITERAL)? (IN (ACCOUNT | (TABLE | VIEW | DATABASE | SCHEMA)? qualifiedName?))? showTail SEMI?   // FROM is not Snowflake syntax (live-verified)
    | SHOW TERSE? STREAMS (LIKE STRING_LITERAL)? (IN (ACCOUNT | (DATABASE | SCHEMA)? qualifiedName))? showTail SEMI?
    | SHOW TERSE? TASKS (LIKE STRING_LITERAL)? (IN (ACCOUNT | (DATABASE | SCHEMA)? qualifiedName))? showTail SEMI?
    | SHOW TERSE? PIPES (LIKE STRING_LITERAL)? (IN (ACCOUNT | (DATABASE | SCHEMA)? qualifiedName))? showTail SEMI?
    | SHOW TERSE? SEQUENCES (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA | ACCOUNT)? qualifiedName?)? showTail SEMI?
    | SHOW TERSE? WAREHOUSES (LIKE STRING_LITERAL)? showTail SEMI?
    | SHOW TERSE? CORTEX SEARCH SERVICES (LIKE STRING_LITERAL)? (IN (ACCOUNT | (DATABASE | SCHEMA)? qualifiedName))? showTail SEMI?
    | SHOW COMPUTE POOLS (LIKE STRING_LITERAL)? showTail SEMI?
    // The instance-family catalog. Its order is the account's own, so the listing is not re-sorted.
    | SHOW COMPUTE POOL INSTANCE FAMILIES SEMI?
    | SHOW TERSE? STAGES (LIKE STRING_LITERAL)? (IN (ACCOUNT | (DATABASE | SCHEMA)? qualifiedName))? showTail SEMI?
    | SHOW TERSE? FILE FORMATS (LIKE STRING_LITERAL)? (IN (ACCOUNT | (DATABASE | SCHEMA)? qualifiedName))? showTail SEMI?
    | SHOW TERSE? TAGS (LIKE STRING_LITERAL)? (IN (ACCOUNT | (DATABASE | SCHEMA)? qualifiedName))? showTail SEMI?
    // The routine listings take the same three modifiers, and TERSE must precede USER / BUILTIN.
    // Live-verified on a real account (2026-08-03):
    //   * USER and BUILTIN are mutually exclusive for BOTH families — `SHOW BUILTIN USER FUNCTIONS`,
    //     `SHOW USER BUILTIN FUNCTIONS`, `SHOW BUILTIN USER PROCEDURES`, `SHOW USER BUILTIN PROCEDURES`
    //     and `SHOW TERSE USER BUILTIN PROCEDURES` are all syntax errors, while `SHOW BUILTIN FUNCTIONS`
    //     returns the 1134-row built-in catalog and `SHOW BUILTIN PROCEDURES` the 32-row one.
    //   * TERSE only binds in front: `SHOW TERSE USER FUNCTIONS` / `SHOW TERSE BUILTIN PROCEDURES` run,
    //     while `SHOW USER TERSE FUNCTIONS` and `SHOW BUILTIN TERSE PROCEDURES` are syntax errors.
    // TERSE is accepted here but changes NOTHING — unlike SHOW TERSE TABLES it does not trim the column
    // set (see ShowModifierProfile). Neither do STARTS WITH and LIMIT, which parse and are then ignored.
    | SHOW TERSE? (USER | BUILTIN)? PROCEDURES (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA | APPLICATION PACKAGE? | CLASS)? qualifiedName)? showTail SEMI?
    | SHOW TERSE? (USER | BUILTIN)? FUNCTIONS (LIKE STRING_LITERAL)? (IN (DATABASE | SCHEMA | CLASS | APPLICATION)? qualifiedName)? showTail SEMI?
    | SHOW TERSE? USERS (LIKE STRING_LITERAL)? showTail SEMI?
    | SHOW TERSE? ROLES (LIKE STRING_LITERAL)? showTail SEMI?
    | SHOW GRANTS ON objectType identifier SEMI?
    | SHOW GRANTS TO (USER | ROLE) identifier SEMI?  // SHOW GRANTS TO USER/ROLE name
    | SHOW GRANTS OF ROLE identifier SEMI?           // who holds this role
    | SHOW GRANTS SEMI?                              // everything granted to the current user
    | SHOW TERSE? MASKING POLICIES (LIKE STRING_LITERAL)? (IN (ACCOUNT | (DATABASE | SCHEMA)? qualifiedName))? showTail SEMI?
    | SHOW TERSE? CONTACTS (LIKE STRING_LITERAL)? (IN (ACCOUNT | (DATABASE | SCHEMA)? qualifiedName))? showTail SEMI?
    | SHOW TERSE? PROJECTION POLICIES (LIKE STRING_LITERAL)? (IN (ACCOUNT | (DATABASE | SCHEMA)? qualifiedName))? showTail SEMI?
    | SHOW TERSE? AGGREGATION POLICIES (LIKE STRING_LITERAL)? (IN (ACCOUNT | (DATABASE | SCHEMA)? qualifiedName))? showTail SEMI?
    | SHOW TERSE? JOIN POLICIES (LIKE STRING_LITERAL)? (IN (ACCOUNT | (DATABASE | SCHEMA)? qualifiedName))? showTail SEMI?
    | SHOW TERSE? ROW ACCESS POLICIES (LIKE STRING_LITERAL)? (IN (ACCOUNT | (DATABASE | SCHEMA)? qualifiedName))? showTail SEMI?
    | SHOW PARAMETERS (LIKE STRING_LITERAL)? (IN (SESSION | ACCOUNT | (DATABASE | SCHEMA | TABLE | WAREHOUSE | USER | ROLE | TASK) identifier))? SEMI?
    | SHOW TERSE? OBJECTS (LIKE STRING_LITERAL)? (IN (ACCOUNT | (DATABASE | SCHEMA)? qualifiedName))? showTail SEMI?
    | SHOW ORGANIZATION ACCOUNTS SEMI?
    | SHOW ACCOUNTS SEMI?
    | SHOW LOCKS (IN ACCOUNT)? SEMI?
    | SHOW TRANSACTIONS (LIKE STRING_LITERAL)? SEMI?
    | SHOW VARIABLES SEMI?
    | SHOW TERSE? (PRIMARY | UNIQUE | IMPORTED) KEYS (IN (ACCOUNT | DATABASE identifier? | SCHEMA qualifiedName? | TABLE qualifiedName? | qualifiedName))? SEMI?
    ;

// Trailing SHOW modifiers shared by the object listings (all optional; the rule may match empty):
// STARTS WITH 'prefix' (case-sensitive name-prefix filter), LIMIT n [FROM 'name'] (pagination), and
// WITH PRIVILEGES p1, p2. The privilege LIST is mandatory — bare `SHOW TABLES WITH PRIVILEGES` is a
// syntax error live, while `SHOW WAREHOUSES WITH PRIVILEGES USAGE, MODIFY` runs. Which object types
// honour the filter is a SEMANTIC matter (a real account answers `SHOW TABLES … WITH PRIVILEGES` with
// "Unsupported feature", i.e. it parses), so the grammar accepts it everywhere.
//
// Parsing STARTS WITH / LIMIT is likewise NOT the same as acting on them: a real account accepts both
// on nearly every listing and then ignores them on several. `SHOW STAGES STARTS WITH 'ZZZ'` returns
// every stage, `SHOW SEQUENCES LIMIT 1` every sequence. ShowModifierProfile carries the measured
// honour/ignore split per listing; this rule only decides what parses. The two listings that reject the
// suffix outright are KEYS (`SHOW PRIMARY KEYS LIMIT 2` → "syntax error … unexpected 'LIMIT'") and
// HYBRID TABLES (untested — no hybrid tables on a standard account), which is why neither carries it.
// Ordering is by name, byte-wise, so uppercase sorts before lowercase; `FROM 'x'` keeps the rows
// sorting strictly after x and is a syntax error without a preceding LIMIT; `LIMIT 0` is rejected
// everywhere with "page size "0" must be greater than 0 in limit clause".
showTail
    : (STARTS WITH STRING_LITERAL)? (LIMIT INTEGER_LITERAL (FROM STRING_LITERAL)?)? (WITH PRIVILEGES showPrivilege (COMMA showPrivilege)*)?
    ;

// A privilege word in SHOW ... WITH PRIVILEGES — most are keyword tokens elsewhere in the grammar.
showPrivilege
    : identifier
    | USAGE
    | MODIFY
    | SELECT
    | INSERT
    | UPDATE
    | DELETE
    | TRUNCATE
    | REFERENCES
    | ALL
    ;

describeStatement
    : (DESCRIBE | DESC) TABLE qualifiedName (TYPE EQ (STAGE | COLUMNS | identifier))? SEMI?
    | (DESCRIBE | DESC) VIEW qualifiedName SEMI?
    | (DESCRIBE | DESC) MATERIALIZED VIEW qualifiedName SEMI?
    | (DESCRIBE | DESC) DYNAMIC TABLE qualifiedName SEMI?
    | (DESCRIBE | DESC) STREAM identifier SEMI?
    | (DESCRIBE | DESC) TASK identifier SEMI?
    | (DESCRIBE | DESC) PIPE identifier SEMI?
    | (DESCRIBE | DESC) SEQUENCE identifier SEMI?
    | (DESCRIBE | DESC) WAREHOUSE identifier SEMI?
    | (DESCRIBE | DESC) COMPUTE POOL identifier SEMI?
    | (DESCRIBE | DESC) CORTEX SEARCH SERVICE qualifiedName SEMI?
    | (DESCRIBE | DESC) STAGE identifier SEMI?
    | (DESCRIBE | DESC) TAG identifier SEMI?
    | (DESCRIBE | DESC) FUNCTION qualifiedName (LPAREN dataTypeList? RPAREN)? SEMI?
    | (DESCRIBE | DESC) PROCEDURE qualifiedName (LPAREN dataTypeList? RPAREN)? SEMI?
    | (DESCRIBE | DESC) USER identifier SEMI?
    | (DESCRIBE | DESC) MASKING POLICY qualifiedName SEMI?
    | (DESCRIBE | DESC) ROW ACCESS POLICY qualifiedName SEMI?
    | (DESCRIBE | DESC) PROJECTION POLICY qualifiedName SEMI?
    | (DESCRIBE | DESC) AGGREGATION POLICY qualifiedName SEMI?
    | (DESCRIBE | DESC) JOIN POLICY qualifiedName SEMI?
    | (DESCRIBE | DESC) SEARCH OPTIMIZATION ON qualifiedName SEMI?
    | (DESCRIBE | DESC) FILE FORMAT qualifiedName SEMI?
    | (DESCRIBE | DESC) RESULT (STRING_LITERAL | identifier LPAREN RPAREN) SEMI?
    // No bare `DESCRIBE <name>` alternative: Snowflake requires the object type (live-verified,
    // `DESCRIBE t` is a syntax error there).
    ;

// Procedural Language Statements
proceduralStatement
    // There is no DECLARE-as-a-statement alternative: in Snowflake a DECLARE only ever opens a block's
    // declaration section, so it must be followed by BEGIN (live-verified — `BEGIN … DECLARE c CURSOR
    // FOR …; OPEN c; … END` is a syntax error, while the nested `DECLARE b INT; BEGIN … END;` form
    // parses because that IS a block). beginEndBlock therefore carries every legal DECLARE.
    : beginEndBlock
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
    | asyncStatement
    | awaitStatement
    | executeImmediateStatement
    | openStatement
    | fetchStatement
    | closeStatement
    | selectIntoStatement
    | nullStatement
    ;

declarationItem
    : identifier (EXCEPTION (LPAREN MINUS? INTEGER_LITERAL COMMA STRING_LITERAL RPAREN)? SEMI?
                 | CURSOR FOR cursorSource SEMI?
                 | RESULTSET (DEFAULT LPAREN selectStatement RPAREN)? SEMI?
                 | dataTypeName typeParameters? ((DEFAULT | COLON_EQ) expression)? SEMI?)
    ;

// A DECLARE-section item with the type omitted — Snowflake infers it from the initializer
// (e.g. `cid1 := UUID_STRING();`, `skey1 := 0;`). Kept OUT of `declarationItem` because `x := expr` is
// syntactically an assignment; it is only valid in the pre-BEGIN `declareSection`, which `BEGIN` ends.
untypedDeclarationItem
    : identifier (DEFAULT | COLON_EQ) expression SEMI?
    ;

// A cursor's source is either a SELECT query or the name of a RESULTSET variable
// (Snowflake: `c CURSOR FOR res;` where `res RESULTSET DEFAULT (…)`).
cursorSource
    : selectStatement
    | identifier
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
    | identifier COLON_EQ LPAREN executeImmediateStatement RPAREN SEMI?   // rs := (EXECUTE IMMEDIATE :stmt) — documented Snowflake RESULTSET form
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

// The IF / ELSEIF condition is PARENTHESIZED in Snowflake (live-verified: `IF 1 = 1 THEN` is a
// syntax error, `IF (1 = 1) THEN` runs). CASE is deliberately not tightened the same way — there
// both `CASE (n)` and `CASE n` are accepted live.
ifStatement
    : IF LPAREN booleanExpr RPAREN THEN statementList
      (ELSEIF LPAREN booleanExpr RPAREN THEN statementList)*
      (ELSE statementList)?
      END IF SEMI?
    ;

caseStatement
    : CASE booleanExpr?
      (WHEN booleanExpr THEN statementList)+
      (ELSE statementList)?
      END CASE? SEMI?
    ;

// A loop label is declared by the TRAILING label — `END LOOP my_loop` / `END FOR sum_loop` /
// `END WHILE w` — and referenced by `BREAK <label>` / `CONTINUE <label>` (live-verified: a BREAK naming
// no trailing label fails SEMANTICALLY with "Label 'MY_LOOP' not found", which is what proves the
// syntax is real). The LEADING `my_loop:` declaration is NOT Snowflake — it is a syntax error at the
// ':' — so there is deliberately no `loopLabel` rule.
// A loop LABEL is written on the END, and may be a join keyword: the Scripting doc's nested example
// closes with `END LOOP INNER;` / `END LOOP OUTER;`. INNER is admitted HERE rather than in the general
// `identifier` rule, because live accepts `CREATE TABLE inner` yet refuses a bare `FROM inner` — see
// KeywordColumnNamesTest.
loopStatement
    : LOOP statementList END LOOP loopLabel? SEMI?
    ;

loopLabel
    : identifier
    | INNER
    ;

whileStatement
    : WHILE LPAREN booleanExpr RPAREN DO statementList END WHILE loopLabel? SEMI?
    ;

forStatement
    : FOR identifier IN REVERSE? expression TO expression DO statementList END FOR loopLabel? SEMI?   // integer range
    | FOR identifier IN expression DO statementList END FOR loopLabel? SEMI?                          // cursor / list
    ;

// UNTIL takes a PARENTHESIZED condition, like IF and WHILE (live-verified: `UNTIL i >= 3` is a
// syntax error, `UNTIL (i >= 3)` runs).
repeatStatement
    : REPEAT statementList UNTIL LPAREN booleanExpr RPAREN END REPEAT loopLabel? SEMI?
    ;

// RETURN TABLE takes a RESULTSET VARIABLE, never a direct query — live refuses
// `RETURN TABLE(SELECT …)` as a syntax error at the SELECT (live-verified), so there is
// deliberately no selectStatement alternative here.
returnStatement
    : RETURN TABLE LPAREN expression RPAREN SEMI?
    | RETURN expression? SEMI?
    ;

breakStatement
    : BREAK loopLabel? SEMI?
    ;

continueStatement
    : CONTINUE loopLabel? SEMI?
    ;

raiseStatement
    : RAISE (identifier STRING_LITERAL?)? SEMI?
    ;

callStatement
    : CALL qualifiedName LPAREN callArguments? RPAREN SEMI?
    ;

callArguments
    : callArgument (COMMA callArgument)*
    ;

callArgument
    : namedArgument   // name => value
    | expression      // positional
    ;

executeImmediateStatement
    : EXECUTE IMMEDIATE expression (USING LPAREN expressionList RPAREN)? SEMI?
    | EXECUTE IMMEDIATE FROM (stageRef | STRING_LITERAL) SEMI?
    ;

beginEndBlock
    : declareSection? BEGIN statementList exceptionSection? END SEMI?
    ;

// A declaration is terminated by its semicolon exactly like a statement (live-verified: an item
// without one is a syntax error at the token that follows it, BEGIN included). Stray semicolons
// AFTER a declaration are tolerated, as Snowflake tolerates them — real deployment scripts end
// their DECLARE section with a lone `;` line before BEGIN — but the section may not OPEN with one.
declareSection
    : DECLARE ((declarationItem | untypedDeclarationItem) {_input.LT(-1).getType() == SEMI}? SEMI*)+
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
    : withClause? SELECT DISTINCT? selectList INTO intoTargetList (FROM tableExpression whereClause? groupByClause? havingClause? qualifyClause?)? orderByClause? (limitClause | fetchClause)? SEMI?
    ;

intoTargetList
    : intoTarget (COMMA intoTarget)*
    ;

intoTarget
    : COLON identifier   // :varname  (bind variable)
    | identifier         // plain variable name
    ;

nullStatement
    : NULL SEMI   // the separator is mandatory — BEGIN NULL END is a Snowflake syntax error (live-verified)
    // …except when nothing follows at all. A bare `NULL` submitted on its own is refused live for
    // being a scripting statement outside a block, at the WORD's own line and column — a refusal it
    // can only reach by parsing first. Requiring the semicolon everywhere left the parser looking for
    // more input and naming <EOF> instead. The lookahead keeps `BEGIN NULL END` refused: there the
    // next token is END, not the end of input.
    | {_input.LA(2) == Token.EOF}? NULL
    ;

// Snowflake Scripting asynchronous execution. The engine is single-threaded, so ASYNC runs its wrapped
// statement synchronously and AWAIT is a no-op (results are identical, only concurrency is lost).
asyncStatement
    : ASYNC LPAREN (dmlStatement | callStatement) RPAREN SEMI?
    ;

awaitStatement
    : AWAIT (ALL | identifier) SEMI?
    ;

statementList
    // Every statement inside a block is TERMINATED by its semicolon: live refuses a block whose
    // statement lacks one wherever it sits — mid-list, the last one before END, a nested block's
    // END, END IF, END LOOP, END CASE, an EXCEPTION handler's body (live-verified). The statement
    // rules each carry an optional trailing SEMI and consume it greedily, so the terminator is
    // enforced here: once a statement is parsed, the token it just consumed must have been that
    // semicolon. Extra semicolons AFTER a statement are tolerated (`RETURN 1;;`, a lone `;` line
    // between two statements or before END), but a list may not START with one — live refuses
    // `THEN ; RETURN 1;` at the ';' itself.
    : (statement {_input.LT(-1).getType() == SEMI}? SEMI*)+
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
    | COLON (identifier | INTEGER_LITERAL)                       # BindVarExpr
    | QUESTION                                                   # PositionalBindExpr
    | SYSTEM_STREAM_HAS_DATA LPAREN expression RPAREN            # SystemStreamHasDataExpr
    | SYSTEM_USER_TASK_CANCEL LPAREN expression RPAREN           # SystemUserTaskCancelExpr
    | SYSTEM_FUNC LPAREN expressionList? RPAREN                  # SystemFuncExpr
    | CURRENT_TIMESTAMP                                          # CurrentTimestampExpr
    | CURRENT_DATE                                               # CurrentDateExpr
    | CURRENT_TIME                                               # CurrentTimeExpr
    | CURRENT_USER                                               # CurrentUserExpr
    | qualifiedName LPAREN PLUS RPAREN                           # OuterJoinColumnExpr
    // Hierarchical-query pseudo-columns. Both take a bare (optionally qualified) column reference —
    // live-verified: `CONNECT_BY_ROOT (sal + 1)` and `CONNECT_BY_ROOT nm || 'x'` are rejected by
    // Snowflake with "Unsupported feature". They precede QualifiedNameExpr so the keyword wins over the
    // same word used as a plain column name (Snowflake resolves it the same way).
    | PRIOR qualifiedName                                        # PriorExpr
    | CONNECT_BY_ROOT qualifiedName                              # ConnectByRootExpr
    // A column reference must not BEGIN with TRY_CAST: in expression position that token always
    // starts the cast construct, so live refuses the bare reference with a syntax error at the
    // NEXT token (SELECT/WHERE/GROUP BY/ORDER BY alike, live-verified) — while a QUALIFIED
    // reference (t.try_cast) and every name position stay legal. With this alternative gated,
    // TryCastExpr is the lone viable parse and its '(' requirement reports at the right token.
    | {expressionLeadingName()}? qualifiedName                   # QualifiedNameExpr
    | jsonObjectLiteral                                          # JsonObjectExpr
    | jsonArrayLiteral                                           # JsonArrayExpr
    | caseExpression                                             # CaseExpr
    // Snowflake interval literals (live-verified): the quoted-string form `INTERVAL '1 day, 2 hours'`
    // (plural units and comma-separated parts INSIDE the string, bare numbers default to seconds), and
    // `INTERVAL '<n>' <singular-unit>`. An unquoted amount (INTERVAL 10 DAY) is a syntax error there,
    // and a PLURAL unit word after the string is NOT a unit — `INTERVAL '10' DAYS` is 10 seconds
    // aliased DAYS — so only the singular keywords are part of this rule.
    | INTERVAL STRING_LITERAL intervalUnitSingular               # IntervalExpr
    | INTERVAL STRING_LITERAL                                    # IntervalStringExpr
    | dateTimeLiteralType STRING_LITERAL                         # TypedDateTimeLiteralExpr
    | CAST LPAREN expression AS dataTypeName typeParameters? ((RENAME | ADD) FIELDS)? RPAREN  # CastExpr
    | TRY_CAST LPAREN expression AS dataTypeName typeParameters? ((RENAME | ADD) FIELDS)? RPAREN  # TryCastExpr
    | COLLATE LPAREN expression COMMA STRING_LITERAL RPAREN                      # CollateFuncExpr
    | functionName LPAREN (identifier | STRING_LITERAL) FROM expression RPAREN   # ExtractFromExpr
    | functionName LPAREN DISTINCT? STAR starModifier* RPAREN                  # FunctionCallStarExpr
    | functionName LPAREN expression (COMMA expression)* (COMMA namedArgument)+ RPAREN overClause?  # FunctionCallMixedArgsExpr
    | functionName LPAREN namedArgumentList RPAREN overClause?   # FunctionCallNamedArgsExpr
    | functionName LPAREN DISTINCT? functionArgList? nullHandling? RPAREN withinGroupClause?
          (FROM (FIRST | LAST) nullHandling? overClause | nullHandling? overClause?)     # FunctionCallExpr
    // The ANSI POSITION(<needle> IN <haystack>) form. It sits AFTER the ordinary call on purpose, so a
    // legitimate membership test inside an argument — UPPER(a IN (1, 2)) — parses as the call it is;
    // this alternative only gets its turn once that parse has failed. It also matches for ANY function
    // name rather than being gated on POSITION: a predicate here eliminates a SYNTACTICALLY VIABLE
    // alternative, which leaves the decision with nothing to pick and anchors the refusal on the
    // function NAME, where live anchors on the operand after IN. The name is checked after the parse
    // instead, which is where live's sentence can be reproduced exactly.
    | functionName LPAREN expression IN expression RPAREN                      # PositionInExpr
    | op=(PLUS | MINUS) expression                               # UnaryExpr
    | EXISTS LPAREN selectStatement RPAREN                       # ExistsExpr
    | LPAREN selectStatement RPAREN                              # ScalarSubqueryExpr
    | expression COLON variantPathKey ((DOT | COLON) variantPathKey)*  # ObjectAccessExpr
    | expression LBRACKET expression RBRACKET                    # ArrayAccessExpr
    | expression DOT variantPathKey                              # FieldAccessExpr
    | expression DOUBLE_COLON dataTypeName typeParameters?       # CastExpr2
    | expression PIPE_PIPE expression                            # ConcatExpr
    | expression op=(STAR | SLASH | PERCENT) expression          # MultiplicativeExpr
    | expression op=(PLUS | MINUS) expression                    # AdditiveExpr
    | expression IS NOT? NULL                                    # IsNullExpr
    | expression IS NOT? DISTINCT FROM expression                # IsDistinctExpr
    // Snowflake has LIKE ANY, LIKE ALL and ILIKE ANY only: NOT LIKE ANY/ALL and ILIKE ALL are
    // compile errors there (live-verified), so the grammar deliberately omits them.
    | expression (LIKE q=(ANY | ALL) | ILIKE q=ANY) LPAREN patterns+=expression (COMMA patterns+=expression)* RPAREN (ESCAPE esc=expression)? # LikeAnyAllExpr
    | expression NOT? (LIKE | ILIKE) expression (ESCAPE expression)? # LikeExpr
    | expression NOT? (RLIKE | REGEXP) expression                # RlikeExpr
    | expression NOT? BETWEEN expression AND expression          # BetweenExpr
    | expression NOT? IN LPAREN selectStatement RPAREN           # InSubqueryExpr
    | expression NOT? IN LPAREN expressionList RPAREN            # InListExpr
    | LPAREN expressionList RPAREN NOT? IN LPAREN selectStatement RPAREN  # TupleInSubqueryExpr
    | LPAREN expressionList RPAREN NOT? IN LPAREN tupleRow (COMMA tupleRow)* RPAREN  # TupleInListExpr
    | LPAREN expressionList RPAREN NOT? IN LPAREN expressionList RPAREN   # TupleInFlatListExpr
    | expression op=(EQ | NEQ | LT | LTE | GT | GTE) expression  # ComparisonExpr
    | expression op=(EQ | NEQ | LT | LTE | GT | GTE) quantifier LPAREN selectStatement RPAREN # QuantifiedComparisonExpr
    | LPAREN booleanExpr RPAREN                                  # ParenExpr
    ;

caseExpression
    : CASE expression whenClause+ (ELSE booleanExpr)? END         # SimpleCaseExpr
    | CASE whenClause+ (ELSE booleanExpr)? END                    # SearchedCaseExpr
    ;

// THEN / ELSE results are booleanExpr (a superset of expression) so a branch may yield a bare boolean —
// CASE WHEN c THEN TRUE ELSE a != b OR c != d END — without extra parentheses.
whenClause
    : WHEN booleanExpr THEN booleanExpr
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

// Only the singular spellings act as an interval unit suffix (see the IntervalExpr alternative).
intervalUnitSingular
    : YEAR | MONTH | DAY | HOUR | MINUTE | SECOND
    ;

jsonObjectLiteral
    : LBRACE jsonObjectEntry (COMMA jsonObjectEntry)* RBRACE
    | LBRACE RBRACE
    ;

jsonObjectEntry
    : jsonKeyValuePair
    ;

jsonKeyValuePair
    : STRING_LITERAL COLON expression
    ;

jsonArrayLiteral
    : LBRACKET arrayElement (COMMA arrayElement)* RBRACKET
    | LBRACKET RBRACKET
    ;

arrayElement
    : spreadArgument
    | expression
    ;

// The `**` spread splices the elements of a CONSTANT array into the enclosing argument list. It is
// argument SPLATTING, not an array feature — `[…]` is sugar for ARRAY_CONSTRUCT, which is why the same
// rule serves both (live-verified: `ARRAY_APPEND(** [[1,2], 3])` splices TWO arguments, and
// `GREATEST(** [1,5,3])` → 5). The operand must be an array LITERAL or an ARRAY_CONSTRUCT call: a
// runtime expression is rejected even over literals — `[** PARSE_JSON('[1,2]')]` fails live. There is
// deliberately no spread in object literals (`{'a':1, ** {'b':2}}` is a syntax error in Snowflake) and
// no bare `SELECT **`.
spreadArgument
    : DOUBLE_STAR expression
    ;

expressionList
    : expression (COMMA expression)*
    ;

// One parenthesized row of a tuple-IN list: (a, b) IN ((1, 2), (3, 4)). The flat spelling
// (a, b) IN (1, 2) parses via TupleInFlatListExpr but is a compile-time TYPE error (ROW vs
// scalars), matching Snowflake.
tupleRow
    : LPAREN expressionList RPAREN
    ;

// Function-call argument list. Uses booleanExpr (which is a superset of expression) so a bare boolean —
// COUNT_IF(a OR b), IFF(x AND y, …) — is accepted as an argument without extra parentheses, while the
// value-context `expressionList` still keeps AND/OR out (so BETWEEN/LIKE operands don't absorb them).
functionArgList
    : functionArg (COMMA functionArg)*
    ;

// A function argument is either a lambda (for higher-order functions like TRANSFORM/FILTER/REDUCE) or a
// normal boolean expression.
functionArg
    : lambdaFunction
    | exprTuple
    | spreadArgument   // ** [a, b] — splices a constant array's elements as arguments
    | booleanExpr
    | STAR          // star argument: MINHASH(5, *), HASH_AGG(*)-style calls
    ;

// A parenthesized tuple argument (two or more expressions): SEARCH((play, line), 'dream').
// A single parenthesized expression stays a plain booleanExpr.
exprTuple
    : LPAREN expression COMMA expression (COMMA expression)* RPAREN
    ;

lambdaFunction
    : lambdaParams THIN_ARROW booleanExpr
    ;

lambdaParams
    : lambdaParam
    | LPAREN lambdaParam (COMMA lambdaParam)* RPAREN
    ;

// A lambda parameter is a name with an optional (ignored) data type — e.g. `x`, `a VARIANT`.
lambdaParam
    : identifier (dataTypeName typeParameters?)?
    ;

namedArgumentList
    : namedArgument (COMMA namedArgument)*
    ;

namedArgument
    : identifier ARROW (expression | selectStatement)   // Snowflake allows a bare subquery value: INPUT => SELECT ...
    ;

functionName
    : KW_IDENTIFIER LPAREN expression RPAREN   // IDENTIFIER('fn') / IDENTIFIER($var) as the function name
    // TRY_CAST never heads a function name: TRY_CAST( always begins the cast construct, so the
    // call shapes TRY_CAST(x, 'type') and TRY_CAST(x) are syntax errors at the comma/paren
    // (live-verified), never calls of a registered or user function.
    | {_input.LT(1).getType() != TRY_CAST}? identifier (DOT identifier)*
    | LIKE   // LIKE/ILIKE also have a function-call form: LIKE(subject, pattern), ILIKE(subject, pattern)
    // Reserved words that are nonetheless FUNCTION names — reserved in identifier positions
    // (live-verified), legal as calls: INSERT(base, pos, len, insert), RLIKE(subject, pattern).
    | INSERT
    | RLIKE
    | ILIKE
    ;

qualifiedName
    // The optional trailing TABLE keyword admits an object part literally named "table"
    // (db.table / db.schema."table"-style references) without making TABLE a general identifier.
    : nameStartPart (DOT namePart)*
    ;

// INNER, JOIN, LEFT and CROSS are live-legal unquoted NAMES in every name position — columns and
// tables alike (live matrix: CREATE TABLE column defs, bare and qualified references, WHERE,
// GROUP BY, ORDER BY, aggregate arguments, INSERT column lists, UPDATE SET targets, and even
// CREATE TABLE inner). The general identifier rule cannot admit them, because a bare table alias
// would then swallow the join keyword of `FROM a LEFT JOIN b` — so only the NAME positions gain
// them. CASE additionally works only AFTER a dot (`kw.case` reads the column); a bare leading
// CASE is live's syntax error — the CASE expression owns that spot.
nameStartPart
    : identifier
    | INNER | JOIN | LEFT | CROSS
    // CASE, CAST, CONSTRAINT, DEFAULT and WHEN lead a NAME wherever a name is the only thing that can
    // appear: live accepts all five for a table, a view, a schema, a CTE, a RENAME TO target, an
    // INSERT INTO target, a bare FROM reference and a `<name>.*` qualifier (every cell measured).
    // In an EXPRESSION they reach the column resolver instead of the parser, which refuses the
    // reference by name — the same "invalid identifier 'CONSTRAINT'" live gives. Guarding them here
    // with a predicate is NOT the way to sharpen that: a predicate on the expression's use of
    // qualifiedName is hoisted into the whole select-item decision and kills `case.*` with it.
    | CASE | CAST | CONSTRAINT | DEFAULT | WHEN
    ;

// The FROM position keeps the join keywords as KEYWORDS in the LEADING part — live, `FROM inner`
// is "unexpected '<EOF>'" even though CREATE TABLE inner succeeds (both measured) — while a
// qualified trailing part still reads (sch.inner).
// The qualifier of a `<name>.*` is a NAME, not a column reference, so it takes the full name
// vocabulary — live reads `SELECT case.* FROM case`. It cannot share qualifiedName with the
// expression: the guard on that alternative is hoisted into the whole select-item decision and would
// eliminate this one too.
starQualifiedName
    : nameStartPart (DOT namePart)*
    ;

tableQualifiedName
    : {tableNameLeadAllowed()}? nameStartPart (DOT namePart)*
    ;

// An ALIAS after AS takes a WIDER vocabulary than a column reference does. Live accepts every one of
// `SELECT 1 AS case`, `AS cast`, `AS constraint`, `AS default`, `AS when` and the join words `AS cross`,
// `AS inner`, `AS join` — none of which may name a column in an EXPRESSION, because there each of them
// leads something. Measured word by word for the select-item and table-alias positions, which agree
// exactly. It stays a rule of its own rather than widening `identifier`: the expression grammar has to
// go on reading CASE as the start of a CASE expression.
aliasName
    : identifier
    | CASE | CAST | CONSTRAINT | CROSS | DEFAULT | INNER | JOIN | WHEN
    ;

// A name being READ rather than defined: the part after a dot, a column list's entries, an UPDATE
// SET target. Measured word by word against the account, and the vocabulary here is the column
// DEFINITION's plus exactly one word — CONSTRAINT, which live reads as `t.constraint`,
// `INSERT INTO t (constraint)` and `UPDATE t SET constraint = 1` alike, while refusing it as a
// column's own name. Every other word tested behaves identically in both positions.
namePart
    : columnDefName
    // CAST, DEFAULT and WHEN join CASE in columnDefName for the same reason: live DEFINES a column so
    // named but cannot REFERENCE one — `SELECT cast FROM t` is its syntax error, because the
    // expression these words lead owns that position. So they are names only where a name is the only
    // thing possible.
    | CONSTRAINT
    ;

// A column DEFINITION's name. It is namePart MINUS CONSTRAINT: there the word leads a table
// constraint, so live reads `CREATE TABLE t (constraint INT)` as a constraint clause and trips on the
// close paren. Splitting the two rules is what lets the after-dot position gain the word without
// making that statement legal.
columnDefName
    : identifier
    | INNER | JOIN | LEFT | CROSS | CASE
    | CAST | DEFAULT | WHEN
    ;

identifier
    : IDENTIFIER
    | KW_IDENTIFIER  // the literal word "identifier" as a plain name (it lexes as KW_IDENTIFIER now)
    | ACCOUNTS      // Allow ACCOUNTS as identifier
    | ACTION
    | ASOF          // Allow ASOF as identifier (the ASOF JOIN modifier is anchored by the following JOIN);
                    // live-verified: `CREATE TABLE kw (asof INT)` is accepted by Snowflake
    | MATCH_CONDITION   // Allow MATCH_CONDITION as identifier (the ASOF clause is anchored by ASOF JOIN)
    | PRIOR         // Allow PRIOR as identifier (the CONNECT BY operator is anchored by a following name);
                    // live-verified: `CREATE TABLE kw (prior INT)` is accepted, unlike `connect`
    | CONNECT_BY_ROOT   // Allow CONNECT_BY_ROOT as identifier (the pseudo-column needs a following name)
    | AFTER         // Allow AFTER as identifier
    | ALLOW_OVERLAPPING_EXECUTION
    | BEFORE        // Allow BEFORE as identifier
    | CALLER        // Allow CALLER as identifier
    | CLOSE         // Allow CLOSE as identifier (e.g. a qualified fn name like tools.stats.close(); the
                    // CLOSE <cursor> statement is a separate rule, disambiguated by context)
    | COLUMNS       // Allow COLUMNS as identifier (for INFORMATION_SCHEMA views)
    | COMMENT       // Allow COMMENT as identifier
    | COPY          // Allow COPY as identifier (table name)
    | CURRENT_DATE  // Allow CURRENT_DATE as identifier (function name)
    | CURRENT_TIME  // Allow CURRENT_TIME as identifier (function name)
    | CURRENT_TIMESTAMP // Allow CURRENT_TIMESTAMP as identifier (function name)
    | CURRENT_USER      // Allow CURRENT_USER as identifier (function name)
    | DATA          // Allow DATA as identifier
    | DATABASES     // Allow DATABASES as identifier (for INFORMATION_SCHEMA views)
    | CHAR          // Allow CHAR as identifier (function name)
    | DATE          // Allow DATE as identifier
    | DATETIME      // Allow DATETIME as identifier
    | TIME          // Allow TIME as identifier (column names; also the TIME data type)
    | DAY           // Allow DAY as identifier (can be column name)
    | DAYS          // Allow DAYS as identifier (can be column name)
    | DELIMITER     // Allow DELIMITER as identifier (SPLIT_TO_TABLE parameter)
    | DIRECTED      // Allow DIRECTED as identifier (also the no-op DIRECTED join modifier)
    | DO            // Allow DO as identifier (e.g. a table alias `do`); the WHILE/FOR ... DO loop keyword
                    // is positionally anchored in those rules, so this doesn't shadow it
    | DOWNSTREAM    // Allow DOWNSTREAM as identifier
    | DYNAMIC       // Allow DYNAMIC as identifier
    | ECONOMY       // Allow ECONOMY as identifier
    | ENABLE_QUERY_ACCELERATION
    | FILE
    | FIRST         // Allow FIRST as identifier (also ORDER BY ... NULLS FIRST)
    | FLATTEN       // Allow FLATTEN as identifier (table function)
    | BUILTIN       // Allow BUILTIN as identifier (also the SHOW BUILTIN FUNCTIONS modifier)
    | FUNCTIONS     // Allow FUNCTIONS as identifier (INFORMATION_SCHEMA view)
    | STAGES        // Allow STAGES as identifier (INFORMATION_SCHEMA view; SHOW STAGES is anchored by SHOW)
    | PACKAGES      // Allow PACKAGES as identifier (INFORMATION_SCHEMA view; the UDF PACKAGES = (...)
                    // property is anchored by the following EQ)
    | GENERATION    // Allow GENERATION as identifier (also a CREATE WAREHOUSE property)
    | COMPUTE       // Allow COMPUTE as identifier (COMPUTE POOL statements are anchored by CREATE/ALTER/DROP/SHOW/DESCRIBE)
    | POOL          // Allow POOL as identifier
    | POOLS         // Allow POOLS as identifier
    | INSTANCE      // Allow INSTANCE as identifier (SHOW COMPUTE POOL INSTANCE FAMILIES is anchored by SHOW)
    | CORTEX        // Allow CORTEX as identifier (CORTEX SEARCH SERVICE statements are anchored by
                    // CREATE/ALTER/DROP/SHOW/DESCRIBE; SNOWFLAKE.CORTEX.<fn>() is a dotted function name)
    | SEARCH        // Allow SEARCH as identifier
    | SERVICE       // Allow SERVICE as identifier
    | SERVICES      // Allow SERVICES as identifier
    | ATTRIBUTES    // Allow ATTRIBUTES as identifier (the CREATE CORTEX SEARCH SERVICE clause is
                    // positionally anchored between the ON column and the options)
    | EMBEDDING_MODEL   // Allow EMBEDDING_MODEL as identifier (property is anchored by the following EQ)
    | FAMILIES      // Allow FAMILIES as identifier
    | MIN_NODES     // Allow MIN_NODES as identifier (compute pool property, anchored by the following EQ)
    | MAX_NODES     // Allow MAX_NODES as identifier (compute pool property)
    | INSTANCE_FAMILY        // Allow INSTANCE_FAMILY as identifier (compute pool property)
    | AUTO_SUSPEND_SECS      // Allow AUTO_SUSPEND_SECS as identifier (compute pool property)
    | PLACEMENT_GROUP        // Allow PLACEMENT_GROUP as identifier (compute pool property)
    | BACKUP_INSTANCE_FAMILIES  // Allow BACKUP_INSTANCE_FAMILIES as identifier (compute pool property)
    | WORK          // Allow WORK as identifier (live-verified: a column AND a table may be named work;
                    // the BEGIN/COMMIT/ROLLBACK WORK keyword is anchored by its statement word)
    | RECLUSTER     // Allow RECLUSTER as identifier (live-verified: a column AND a table may be named
                    // recluster; the clustering action is anchored by SUSPEND / RESUME)
    | NORELY        // Allow NORELY as identifier (live-verified: `CREATE TABLE kw (norely INT)` runs;
                    // the constraint property is anchored by RELY's own clause position)
    | ENFORCED      // Allow ENFORCED as identifier (live-verified: a column AND a table may be named
                    // enforced; the constraint property is anchored by ALTER/MODIFY CONSTRAINT)
    | NAME          // Allow NAME as identifier (live-verified: a column and a table may be named name,
                    // and `BEGIN NAME name` names a transaction NAME — the keyword is anchored by BEGIN
                    // / START TRANSACTION, which is the only place it is one)
    | STOP          // Allow STOP as identifier (ALTER COMPUTE POOL ... STOP ALL is anchored by ALTER)
    | GENERATOR     // Allow GENERATOR as identifier (table function)
    | GET           // Allow GET as identifier (the GET(array/object, key) semi-structured function; the
                    // GET stage command is a separate statement, disambiguated by context)
    | GRANTS        // Allow GRANTS as identifier
    | FORMAT        // Allow FORMAT as identifier/function name (FILE FORMAT is anchored by FILE)
    | DECFLOAT      // Allow DECFLOAT as identifier (the type use is anchored in dataTypeName)
    | FILTER        // Allow FILTER as identifier (the aggregate FILTER clause is anchored by LPAREN WHERE)
    | POLICY        // Allow POLICY as identifier (MASKING/ROW ACCESS POLICY uses are anchored by the preceding keyword)
    | PUT           // Allow PUT as identifier (the PUT stage statement is anchored by its file-URL argument)
    | SCHEMA        // Allow SCHEMA as a qualified-name part (db.schema.object); CREATE/DROP/USE/IN SCHEMA are anchored
    | SESSION       // Allow SESSION as identifier (ALTER SESSION / IN SESSION are anchored)
    | NUMBER        // Allow NUMBER as identifier (the NUMBER type is anchored in dataTypeName)
    | RENAME        // Allow RENAME as identifier (star-modifier / ALTER ... RENAME are anchored)
    | REPLACE       // Allow REPLACE as identifier (CREATE OR REPLACE / star-modifier are anchored)
    | GROUPING      // Allow GROUPING as identifier (function name)
    | HOUR          // Allow HOUR as identifier (can be column name)
    | HOURS         // Allow HOURS as identifier (can be column name)
    | HYBRID        // Allow HYBRID as identifier (also a CREATE [HYBRID] TABLE modifier)
    | IGNORE        // Allow IGNORE as identifier (also FIRST_VALUE(...) IGNORE NULLS)
    | RESPECT       // Allow RESPECT as identifier (also FIRST_VALUE(...) RESPECT NULLS)
    | INCREMENTAL   // Allow INCREMENTAL as identifier
    | INFORMATION   // Allow INFORMATION as identifier
    | INITIALIZE    // Allow INITIALIZE as identifier
    | INITIALLY_SUSPENDED
    | INPUT         // Allow INPUT as identifier (FLATTEN parameter)
    | EXCEPT        // EXCEPT is unreserved (usable as alias/table/column), unlike UNION/INTERSECT/MINUS
    | KEY           // Allow KEY as identifier (FLATTEN output column)
    | LAST          // Allow LAST as identifier (also ORDER BY ... NULLS LAST)
    | LEFT          // Allow LEFT as identifier (Snowflake compatible)
    | LOCKS         // Allow LOCKS as identifier
    | MAP           // Allow MAP as identifier (also the MAP data type)
    | MAX_CONCURRENCY_LEVEL
    | MINUTE        // Allow MINUTE as identifier (can be column name)
    | MINUTES       // Allow MINUTES as identifier (can be column name)
    | MODE          // Allow MODE as identifier (FLATTEN parameter)
    | MONTH         // Allow MONTH as identifier (can be column name)
    | MONTHS        // Allow MONTHS as identifier (can be column name)
    | NEXT          // Allow NEXT as identifier (not reserved in Snowflake; only used after FETCH)
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
    | DEFAULT_WAREHOUSE
    | DEFAULT_NAMESPACE
    | DEFAULT_SECONDARY_ROLES
    | LOGIN_NAME
    | DISPLAY_NAME
    | FIRST_NAME
    | MIDDLE_NAME
    | LAST_NAME
    | MUST_CHANGE_PASSWORD
    | EMAIL
    | DISABLED
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
    | RANGE         // Allow RANGE as identifier (window-frame keyword)
    | UNBOUNDED     // Allow UNBOUNDED as identifier (window-frame keyword)
    | PRECEDING     // Allow PRECEDING as identifier (window-frame keyword)
    | SYSTEM        // Allow SYSTEM as identifier (sampling method)
    | SEED          // Allow SEED as identifier (sampling keyword)
    | BERNOULLI     // Allow BERNOULLI as identifier (sampling method)
    // The four SCRIPTING statement words are ordinary names in SQL, measured over a raw connection:
    // each may be a column's name, a column reference and a bare table alias, and
    // `SELECT break.x FROM st break` resolves. Only OUTSIDE a block, standing alone as a statement,
    // are they refused — see SQLCommandVisitor.visitProceduralStatement, which is where that belongs.
    | BREAK
    | CONTINUE
    | RAISE
    | RETURN
    | NOVALIDATE    // live: a column may be called novalidate (CHECK itself may NOT — it is reserved)
    | BODY          // only a keyword inside ALTER … SET BODY
    | AGGREGATION   // live: CREATE TABLE aggregation (aggregation INT) runs
    | ENTITY        // live: a column may be called entity
    | PROJECTION    // live: CREATE TABLE projection (projection INT) runs
    | METRIC        // live: CREATE TABLE metric (metric INT, data INT) runs
    | OPTIMIZATION  // live: CREATE TABLE search (optimization INT) runs
    | CONTACT       // live: CREATE TABLE contact (contact INT) runs
    | CONTACTS      // live: a column may be called contacts
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
    | DIRECTORY     // Allow DIRECTORY as identifier (also the DIRECTORY(@stage) table source)
    | FIELDS        // Allow FIELDS as identifier (also CAST ... RENAME/ADD FIELDS)
    | NVARCHAR | NVARCHAR2 | NCHAR | CHARACTER | VARYING | TIMESTAMPLTZ | TIMESTAMPTZ | LOCAL | GLOBAL | ZONE
    | TERSE         // Allow TERSE as identifier (also the SHOW TERSE modifier)
    | STARTS        // Allow STARTS as identifier (also SHOW ... STARTS WITH)
    | HISTORY       // Allow HISTORY as identifier (also SHOW ... HISTORY)
    | ICEBERG       // Allow ICEBERG as identifier (also SHOW ICEBERG TABLES)
    | APPLICATION   // Allow APPLICATION as identifier (also SHOW ... IN APPLICATION)
    | CLASS         // Allow CLASS as identifier (also SHOW FUNCTIONS IN CLASS)
    | PACKAGE       // Allow PACKAGE as identifier (also IN APPLICATION PACKAGE)
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
    | TRY_CAST      // identifier in NAME positions (alias, column/table name, live-verified);
                    // the cast construct is keyword-anchored, and functionName refuses TRY_CAST as a
                    // call head, so the construct wins wherever a call could be confused with it
    | TYPE
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
    | CLUSTER
    | CLUSTERING
    | IDENTITY
    | CHANGES
    | NUMERIC
    | DEC           // Allow DEC as identifier (also the DEC(p,s) NUMBER alias)
    | STREAM
    | NETWORK
    | UNPIVOT
    | PIVOT
    | DATABASE      // e.g. a VARIANT path key `stats:database`
    // Names that metadata output uses as COLUMN names, and that a real account accepts
    // unquoted in that position. INCREMENT and ROWS are deliberately absent: a real
    // account reserves those, so INFORMATION_SCHEMA.SEQUENCES."increment" and
    // SHOW TABLES' "rows" have to be quoted there too.
    | ALLOWED_VALUES
    | AUTOINCREMENT
    | AUTO_RESUME
    | AUTO_SUSPEND
    | COMPRESSION
    | DATE_FORMAT
    | DEFAULT_ROLE
    | ERROR_INTEGRATION
    | ESCAPE
    | FIELD_DELIMITER
    | HANDLER
    | INTEGRATION
    | INTERVAL
    | LANGUAGE
    | MAX_CLUSTER_COUNT
    | MIN_CLUSTER_COUNT
    | RECORD_DELIMITER
    | RELY
    | RESOURCE_MONITOR
    | RUNTIME_VERSION
    | SCHEDULE
    | SKIP_HEADER
    | WAREHOUSE
    // Snowflake reserves only 64 words, and every OTHER word it lexes may name a column: the
    // list below is the measured remainder — each one accepted live both as a column DEFINITION
    // and as a REFERENCE (SELECT/WHERE/GROUP BY/ORDER BY). They are keywords here only because
    // Frostlake tokenises them for some statement it supports, which is not a reason to refuse
    // them as names. Measure before adding: the five words live DEFINES but cannot REFERENCE
    // (CASE, CAST, DEFAULT, TRY_CAST, WHEN) belong in namePart instead, and the join words
    // (INNER/JOIN/LEFT/CROSS/FULL) cannot live here at all — see nameStartPart.
    | ACCESS | ACCOUNT | ADD | APPLY | ARRAY | ASC
    // AT_KEYWORD, not AT: the token named AT is the '@' of a stage reference, and the WORD at is a
    // separate token. Naming the wrong one here quietly makes '@' an identifier.
    | ASYNC | AT_KEYWORD | AUTO | AUTO_INGEST | AWAIT | AWS_SNS_TOPIC
    | BEGIN | BIGINT | BINARY | BOOLEAN | BREAK | BYTEINT
    | CALL | CASCADE | CLONE | COLLATE | COMMIT | CONTINUE
    | CUBE | CURRVAL | CURSOR | DATA_RETENTION_TIME_IN_DAYS | DECIMAL | DECLARE
    | DESC | DESCRIBE | DISABLE | DOUBLE | ELSEIF | ENABLE
    | ENCRYPTION | END | EXCEPTION | EXECUTE | EXECUTION | EXPLAIN
    | FALSE | FETCH | FILES | FILE_FORMAT | FLOAT | FORCE
    | FOREIGN | FORMATS | FULL | FUNCTION | FUTURE | GEOGRAPHY
    | GEOMETRY | HEADER | IF | IMMEDIATE | IMPORT | IMPORTED
    | INT | INTEGER | JAVA | JAVASCRIPT | KEYS | LATERAL
    | LET | LIMIT | LIST | LOOP | MANAGE | MASKING
    | MASKING_POLICY | MATCHED | MATCH_BY_COLUMN_NAME | MATERIALIZED | MAX_FILE_SIZE | MERGE
    | MODIFY | MONITOR | MULTI_STATEMENT_COUNT | NO | NOORDER | OBJECT
    | ONLY | ON_CREATE | ON_ERROR | ON_SCHEDULE | OPEN | OPERATE
    | OVER | OVERWRITE | OWNERSHIP | PASSWORD | PAUSE | PIPE
    | POLICIES | PRIMARY | PROCEDURE | PURGE | PYTHON | RAISE
    | READ | REBUILD | REFRESH | REFRESH_MODE | REMOVE | RESTART
    | RESTRICT | RESULTSET | RESUME | RETURN | RETURNS | RM
    | ROLLBACK | ROLLUP | ROW_ACCESS_POLICY | SCALA | SECONDARY | SEQUENCE
    | SESSION_POLICY | SHARE | SHOW | SINGLE | SIZE_LIMIT | SMALLINT
    | SUSPEND | SWAP | TARGET_LAG | TASK | TIMESTAMP_LTZ | TIMESTAMP_TZ
    | TINYINT | TOP | TRANSACTION | TRANSIENT | TRUE | UNDROP
    | UNSET | UNTIL | USAGE | USE | USE_ANY_ROLE | USING
    | VALIDATION_MODE | VARBINARY | VARCHAR | VARIANT | VIEW | WAREHOUSES
    | WAREHOUSE_SIZE | WHILE | WITHIN | WRITE
    ;

// A VARIANT path key (the field name after `:` or `.`) is just a JSON key, so — unlike a bare identifier —
// it may be ANY reserved keyword: j:create, j:order, j:from are all valid in Snowflake. This rule is used
// ONLY in path access (ObjectAccessExpr / FieldAccessExpr), so these keywords stay out of the general
// identifier rule and never affect statement parsing.
variantPathKey
    : identifier
    | ACCOUNT   // a semi-structured path key may be any word, including otherwise-reserved keywords (e.g. src:account.status)
    | CREATE | DROP | ALTER | SELECT | DELETE | WHERE | GROUP | ORDER | HAVING | FROM
    | JOIN | INNER | OUTER | CROSS | FULL | ON | NATURAL
    | AND | OR | NOT | IN | IS | LIKE | BETWEEN | EXISTS
    | UNION | INTERSECT | ALL | DISTINCT | AS | BY
    | CASE | WHEN | THEN | ELSE | END | NULL | ASC | DESC
    | INTO | INSERT | CURRENT | FOLLOWING | RLIKE | UNIQUE | UPDATE
    | LIMIT | WITH | QUALIFY | PIVOT | UNPIVOT | MERGE | SET | VALUES | SHOW
    | GRANT | REVOKE | DESCRIBE | USE | EXPLAIN | TRUNCATE | CALL | EXECUTE | RETURN
    ;

nonJoinKeywordIdentifier
    // A bare table alias is the WIDEST name position measured: everything a column may be called, less
    // the fifteen words that lead something in a FROM clause (see bareTableAliasAllowed), plus ten that
    // are NOT legal column names yet are legal aliases — live accepts `FROM t current_date` while
    // refusing a column called current_date. The explicit list below it is older and narrower; the
    // words it names that identifier already covers are harmless duplicates.
    : {bareTableAliasAllowed()}? identifier
    | CASE | CAST | CONSTRAINT | CURRENT_DATE | CURRENT_TIME | CURRENT_TIMESTAMP | CURRENT_USER
    | DEFAULT | TRY_CAST | WHEN
    | IDENTIFIER
    | KW_IDENTIFIER
    | EXCEPT        // unreserved: `FROM t except` is a bare alias; `FROM t EXCEPT SELECT` still a set-op
    | APPEND_ONLY
    | DECFLOAT
    | FORMAT
    | FILTER
    | DIRECTORY     // vendor idiom: LEFT JOIN (...) directory USING (...)
    | FIELDS
    | PUT           // a bare alias named put (the PUT statement is anchored at statement start)
    | SESSION
    | NUMBER
    | RENAME
    | REPLACE
    | NVARCHAR | NVARCHAR2 | NCHAR | CHARACTER | VARYING | TIMESTAMPLTZ | TIMESTAMPTZ | LOCAL | GLOBAL | ZONE
    | TERSE
    | STARTS
    | HISTORY
    | ICEBERG
    | APPLICATION
    | CLASS
    | PACKAGE
    // Live-verified per word: `FROM h prior` and `FROM h connect_by_root` are accepted as bare table
    // aliases by Snowflake, while `FROM h asof` and `FROM h match_condition` are syntax errors there —
    // so ASOF and MATCH_CONDITION are deliberately NOT listed (ASOF would also make `FROM x asof JOIN y`
    // ambiguous with the ASOF join it introduces).
    | PRIOR
    | CONNECT_BY_ROOT
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
    | DO            // Allow DO as an un-AS'd table alias (e.g. `FROM detection_output do`)
    | FLATTEN
    | INPUT
    | PATH
    | RECURSIVE
    | MODE
    | KEY
    | TAG
    | NEXTVAL
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
    | CLUSTER
    | CLUSTERING
    | IDENTITY
    | CHANGES
    | NUMERIC
    | DEC           // Allow DEC as identifier (also the DEC(p,s) NUMBER alias)
    | STREAM
    | NETWORK
    | UNPIVOT
    | PIVOT
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
    | HEX_LITERAL                 // x'A1B2' hex binary
    | TRUE
    | FALSE
    | NULL
    ;

// Lexer Rules
// Keywords
SELECT: S E L E C T;
DIRECTED: D I R E C T E D;
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
RLIKE: R L I K E;
REGEXP: R E G E X P;
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
CORTEX: C O R T E X;
SEARCH: S E A R C H;
SERVICE: S E R V I C E;
SERVICES: S E R V I C E S;
ATTRIBUTES: A T T R I B U T E S;
EMBEDDING_MODEL: E M B E D D I N G '_' M O D E L;
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
COMPUTE: C O M P U T E;
POOL: P O O L;
POOLS: P O O L S;
INSTANCE: I N S T A N C E;
FAMILIES: F A M I L I E S;
MIN_NODES: M I N '_' N O D E S;
MAX_NODES: M A X '_' N O D E S;
INSTANCE_FAMILY: I N S T A N C E '_' F A M I L Y;
AUTO_SUSPEND_SECS: A U T O '_' S U S P E N D '_' S E C S;
PLACEMENT_GROUP: P L A C E M E N T '_' G R O U P;
BACKUP_INSTANCE_FAMILIES: B A C K U P '_' I N S T A N C E '_' F A M I L I E S;
STOP: S T O P;
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
BUILTIN: B U I L T I N;

// Stream, Task, Warehouse, Stage
STREAM: S T R E A M;
TASK: T A S K;
WAREHOUSE: W A R E H O U S E;
STAGE: S T A G E;
MERGE: M E R G E;
USING: U S I N G;
ON: O N;
OF: O F;
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
// Snowflake ASOF JOIN (closest-match time-series join) and its mandatory MATCH_CONDITION clause.
// Live-verified: only the bare `ASOF JOIN` spelling exists — LEFT/RIGHT/INNER ASOF are syntax errors.
ASOF: A S O F;
MATCH_CONDITION: M A T C H UNDERSCORE C O N D I T I O N;
// Hierarchical-query keywords: `[START WITH <pred>] CONNECT BY [PRIOR] c = [PRIOR] c` plus the
// CONNECT_BY_ROOT pseudo-column. CONNECT is reserved in Snowflake (live-verified: a column named
// `connect` is a syntax error) — ASOF / MATCH_CONDITION / PRIOR / CONNECT_BY_ROOT are not, so they
// stay usable as identifiers via the `identifier` rule.
CONNECT: C O N N E C T;
PRIOR: P R I O R;
CONNECT_BY_ROOT: C O N N E C T UNDERSCORE B Y UNDERSCORE R O O T;
RESUME: R E S U M E;
SUSPEND: S U S P E N D;
RECLUSTER: R E C L U S T E R;
CHECK: C H E C K;
CONTACT: C O N T A C T;
METRIC: M E T R I C;
OPTIMIZATION: O P T I M I Z A T I O N;
PROJECTION: P R O J E C T I O N;
AGGREGATION: A G G R E G A T I O N;
ENTITY: E N T I T Y;
BODY: B O D Y;
NOVALIDATE: N O V A L I D A T E;
CONTACTS: C O N T A C T S;
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
// Multi-word statements as single tokens (whitespace inside): zero parser-decision impact, and
// the lexer's longest-match fallback still yields plain RESUME when the tail is absent.
RESUME_IF_SUSPENDED: R E S U M E TOKEN_WS I F TOKEN_WS S U S P E N D E D;
ABORT_ALL_QUERIES: A B O R T TOKEN_WS A L L TOKEN_WS Q U E R I E S;
fragment TOKEN_WS: [ \t\r\n]+;
MAX_CONCURRENCY_LEVEL: M A X UNDERSCORE C O N C U R R E N C Y UNDERSCORE L E V E L;
STATEMENT_QUEUED_TIMEOUT_IN_SECONDS: S T A T E M E N T UNDERSCORE Q U E U E D UNDERSCORE T I M E O U T UNDERSCORE I N UNDERSCORE S E C O N D S;
STATEMENT_TIMEOUT_IN_SECONDS: S T A T E M E N T UNDERSCORE T I M E O U T UNDERSCORE I N UNDERSCORE S E C O N D S;
ENABLE_QUERY_ACCELERATION: E N A B L E UNDERSCORE Q U E R Y UNDERSCORE A C C E L E R A T I O N;
QUERY_ACCELERATION_MAX_SCALE_FACTOR: Q U E R Y UNDERSCORE A C C E L E R A T I O N UNDERSCORE M A X UNDERSCORE S C A L E UNDERSCORE F A C T O R;
GENERATION: G E N E R A T I O N;
STANDARD: S T A N D A R D;
ECONOMY: E C O N O M Y;
MULTI_STATEMENT_COUNT: M U L T I UNDERSCORE S T A T E M E N T UNDERSCORE C O U N T;
CLUSTERING: C L U S T E R I N G;
CLUSTER: C L U S T E R;
COLLATE: C O L L A T E;
TRANSIENT: T R A N S I E N T;
HYBRID: H Y B R I D;
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
FILTER: F I L T E R;
DECFLOAT: D E C F L O A T;
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
DEFAULT_WAREHOUSE: D E F A U L T UNDERSCORE W A R E H O U S E;
DEFAULT_NAMESPACE: D E F A U L T UNDERSCORE N A M E S P A C E;
DEFAULT_SECONDARY_ROLES: D E F A U L T UNDERSCORE S E C O N D A R Y UNDERSCORE R O L E S;
LOGIN_NAME: L O G I N UNDERSCORE N A M E;
DISPLAY_NAME: D I S P L A Y UNDERSCORE N A M E;
FIRST_NAME: F I R S T UNDERSCORE N A M E;
MIDDLE_NAME: M I D D L E UNDERSCORE N A M E;
LAST_NAME: L A S T UNDERSCORE N A M E;
MUST_CHANGE_PASSWORD: M U S T UNDERSCORE C H A N G E UNDERSCORE P A S S W O R D;
EMAIL: E M A I L;
DISABLED: D I S A B L E D;
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
ENFORCED: E N F O R C E D;
APPLY: A P P L Y;
IMPORTED: I M P O R T E D;
TERSE: T E R S E;
STARTS: S T A R T S;
HISTORY: H I S T O R Y;
ICEBERG: I C E B E R G;
APPLICATION: A P P L I C A T I O N;
CLASS: C L A S S;
PACKAGE: P A C K A G E;
NVARCHAR2: N V A R C H A R '2';
NVARCHAR: N V A R C H A R;
NCHAR: N C H A R;
CHARACTER: C H A R A C T E R;
VARYING: V A R Y I N G;
TIMESTAMPLTZ: T I M E S T A M P L T Z;
TIMESTAMPTZ: T I M E S T A M P T Z;
GLOBAL: G L O B A L;
LOCAL: L O C A L;
ZONE: Z O N E;
DIRECTORY: D I R E C T O R Y;
FIELDS: F I E L D S;
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
NUMERIC: N U M E R I C;
DEC: D E C;
FLOAT: F L O A T;
FLOAT4: F L O A T '4';
FLOAT8: F L O A T '8';
DOUBLE: D O U B L E;
REAL: R E A L;
PRECISION: P R E C I S I O N;
UUID: U U I D;
VECTOR: V E C T O R;
GEOGRAPHY: G E O G R A P H Y;
GEOMETRY: G E O M E T R Y;
VARCHAR: V A R C H A R;
STRING: S T R I N G;
TEXT: T E X T;
CHAR: C H A R;
BOOLEAN: B O O L E A N;
DATE: D A T E;
DATA: D A T A;
DATETIME: D A T E T I M E;
TIME: T I M E;
TIMESTAMP: T I M E S T A M P;
TIMESTAMP_NTZ: T I M E S T A M P UNDERSCORE N T Z;
TIMESTAMPNTZ: T I M E S T A M P N T Z;
TIMESTAMP_LTZ: T I M E S T A M P UNDERSCORE L T Z;
TIMESTAMP_TZ: T I M E S T A M P UNDERSCORE T Z;
VARIANT: V A R I A N T;
ARRAY: A R R A Y;
OBJECT: O B J E C T;
MAP: M A P;
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
WORK: W O R K;
NAME: N A M E;
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
ASYNC: A S Y N C;
AWAIT: A W A I T;
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
TRY_CAST: T R Y '_' C A S T;
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
FLOAT_LITERAL: [0-9]+ DOT [0-9]+ ([eE] [+-]? [0-9]+)? | [0-9]+ [eE] [+-]? [0-9]+;   // The second alternative is the DOT-less exponent (1e0, 1E+3, 1e-3), a number wherever a number is allowed. Without it the lexer split 1e0 into 1 and the identifier e0, which silently read as an ALIAS in a select list and was a syntax error everywhere else. An exponent is folded in before the type is taken, so these are FIXED-point: 1e0 is NUMBER(1,0) and 1e20 NUMBER(21,0).
STRING_LITERAL: '\'' ('\\' . | '\'\'' | ~['\\])* '\'';    // Single-quoted. Backslash is EXCLUDED from ~[..] so it always begins a '\\' . escape (incl. \'); otherwise maximal-munch lets \'' lex as \ + '' and mis-aligns the string boundaries. Decode via SqlStringLiterals.
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
FLOW_ARROW: '->>';
THIN_ARROW: '->';
NEQ: '<>' | '!=';
LT: '<';
LTE: '<=';
GT: '>';
GTE: '>=';
PLUS: '+';

// Comments and whitespace before MINUS so '--' is matched as LINE_COMMENT not MINUS MINUS
// x'A1B2' — a hex binary literal (the engine's BINARY values are uppercase hex strings).
HEX_LITERAL: [xX] '\'' [0-9a-fA-F]* '\'';

// An unquoted file/cloud URL in PUT/GET (Snowflake allows the unquoted form): scheme '://' then
// everything up to whitespace or a statement delimiter.
FILE_URL: (F I L E | S '3' | A Z U R E | G C S | H T T P S | H T T P) ':' '/' '/' ~[ \t\r\n',;)]*;

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
