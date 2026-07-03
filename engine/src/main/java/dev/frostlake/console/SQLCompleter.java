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

package dev.frostlake.console;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.Arrays;
import java.util.List;

/**
 * SQL auto-completion for console client
 * Provides completion for SQL keywords, table names, column names, and functions
 */
public class SQLCompleter implements Completer {

    private final DatabaseEngine engine;

    private static final List<String> SQL_KEYWORDS = Arrays.asList(
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
            "CREATE", "DROP", "ALTER", "TABLE", "DATABASE", "SCHEMA", "VIEW", "INDEX",
            "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "NOT", "NULL",
            "AND", "OR", "IN", "BETWEEN", "LIKE", "IS", "EXISTS",
            "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "LATERAL",
            "ON", "USING", "GROUP", "BY", "HAVING", "ORDER", "ASC", "DESC",
            "LIMIT", "OFFSET", "UNION", "INTERSECT", "EXCEPT", "ALL", "DISTINCT",
            "CASE", "WHEN", "THEN", "ELSE", "END",
            "AS", "WITH", "RECURSIVE",
            "GRANT", "REVOKE", "TO", "FROM", "ROLE", "USER",
            "BEGIN", "COMMIT", "ROLLBACK", "TRANSACTION",
            "USE", "SHOW", "DESCRIBE", "DESC", "EXPLAIN",
            "WAREHOUSE", "STAGE", "TASK", "STREAM", "PROCEDURE", "FUNCTION",
            "IF", "EXISTS", "REPLACE", "TEMPORARY", "TEMP",
            "CLONE", "AT", "BEFORE", "TIMESTAMP",
            "PIVOT", "UNPIVOT", "QUALIFY",
            "VARIANT", "OBJECT", "ARRAY", "VARCHAR", "INTEGER", "BIGINT", "FLOAT", "DOUBLE",
            "BOOLEAN", "DATE", "TIME", "TIMESTAMP", "BINARY",
            "FLATTEN", "GENERATOR", "SPLIT_TO_TABLE",
            "TRUNCATE", "MERGE", "COPY", "CALL", "EXECUTE", "IMMEDIATE",
            "COMMENT", "RETURNS", "LANGUAGE", "CALLED", "IMMUTABLE", "VOLATILE"
    );

    private static final List<String> SQL_FUNCTIONS = Arrays.asList(
            "COUNT", "SUM", "AVG", "MIN", "MAX",
            "UPPER", "LOWER", "SUBSTRING", "CONCAT", "LENGTH", "TRIM", "LTRIM", "RTRIM",
            "REVERSE", "INITCAP", "LPAD", "RPAD",
            "ABS", "ROUND", "FLOOR", "CEIL", "SQRT", "POWER", "MOD",
            "SIGN", "TRUNC", "EXP", "LN", "LOG",
            "COALESCE", "NULLIF", "NVL", "IFF", "GREATEST", "LEAST",
            "CAST", "TRY_CAST", "TO_VARCHAR", "TO_NUMBER", "TO_DATE", "TO_TIMESTAMP",
            "CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME",
            "DATEADD", "DATEDIFF", "DATE_TRUNC",
            "ROW_NUMBER", "RANK", "DENSE_RANK", "LAG", "LEAD",
            "FIRST_VALUE", "LAST_VALUE", "NTH_VALUE",
            "ARRAY_AGG", "OBJECT_AGG", "LISTAGG",
            "PARSE_JSON", "OBJECT_CONSTRUCT", "ARRAY_CONSTRUCT",
            "GET", "GET_PATH", "ARRAY_SIZE"
    );

    public SQLCompleter(final DatabaseEngine engine) {
        this.engine = engine;
    }

    @Override
    public void complete(final LineReader reader, final ParsedLine line, final List<Candidate> candidates) {
        String buffer = line.line();
        String word = line.word();

        // If word is empty or just started, don't complete
        if (word.isEmpty()) {
            return;
        }

        String wordUpper = word.toUpperCase();

        // Get the text before the current word to determine context
        int wordStart = line.wordIndex();
        String beforeWord = wordStart > 0 ? buffer.substring(0, wordStart).trim() : "";
        String[] tokens = beforeWord.split("\\s+");
        String lastToken = tokens.length > 0 ? tokens[tokens.length - 1].toUpperCase() : "";

        // Determine what type of completion to provide based on context
        boolean afterFrom = containsToken(tokens, "FROM");
        boolean afterSelect = containsToken(tokens, "SELECT");
        boolean afterWhere = containsToken(tokens, "WHERE");
        boolean afterTable = lastToken.equals("TABLE");
        boolean afterDatabase = lastToken.equals("DATABASE");
        boolean afterSchema = lastToken.equals("SCHEMA");

        // Complete table names after FROM, JOIN, INTO, UPDATE
        if (afterFrom || lastToken.equals("JOIN") || lastToken.equals("INTO") ||
            lastToken.equals("UPDATE") || afterTable) {
            addTableCompletions(wordUpper, candidates);
        }

        // Complete database names after DATABASE keyword
        if (afterDatabase) {
            addDatabaseCompletions(wordUpper, candidates);
        }

        // Complete schema names after SCHEMA keyword
        if (afterSchema) {
            addSchemaCompletions(wordUpper, candidates);
        }

        // Complete column names after SELECT or WHERE
        if ((afterSelect && !afterFrom) || afterWhere) {
            addColumnCompletions(wordUpper, candidates);
        }

        // Always add SQL keywords
        addKeywordCompletions(wordUpper, candidates);

        // Always add function names
        addFunctionCompletions(wordUpper, candidates);
    }

    private boolean containsToken(final String[] tokens, final String target) {
        for (final String token : tokens) {
            if (token.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private void addKeywordCompletions(final String prefix, final List<Candidate> candidates) {
        for (final String keyword : SQL_KEYWORDS) {
            if (keyword.startsWith(prefix)) {
                candidates.add(new Candidate(keyword, keyword, "keyword", null, null, null, true));
            }
        }
    }

    private void addFunctionCompletions(final String prefix, final List<Candidate> candidates) {
        for (final String function : SQL_FUNCTIONS) {
            if (function.startsWith(prefix)) {
                candidates.add(new Candidate(function, function, "function", null, null, null, true));
            }
        }
    }

    private void addTableCompletions(final String prefix, final List<Candidate> candidates) {
        try {
            String currentDb = engine.getCatalog().getCurrentDatabase();
            String currentSchema = engine.getCatalog().getCurrentSchema();

            if (currentDb != null && currentSchema != null) {
                Database db = engine.getCatalog().getDatabase(currentDb);
                if (db != null) {
                    Schema schema = db.getSchema(currentSchema);
                    if (schema != null) {
                        for (final Table table : schema.getTables()) {
                            String tableName = table.getName();
                            if (tableName.toUpperCase().startsWith(prefix)) {
                                candidates.add(new Candidate(tableName, tableName, "table", null, null, null, true));
                            }
                        }
                    }
                }
            }
        } catch (final Exception e) {
            // Ignore errors during completion
        }
    }

    private void addDatabaseCompletions(final String prefix, final List<Candidate> candidates) {
        try {
            for (final Database db : engine.getCatalog().getAllDatabases()) {
                String dbName = db.getName();
                if (dbName.toUpperCase().startsWith(prefix)) {
                    candidates.add(new Candidate(dbName, dbName, "database", null, null, null, true));
                }
            }
        } catch (final Exception e) {
            // Ignore errors during completion
        }
    }

    private void addSchemaCompletions(final String prefix, final List<Candidate> candidates) {
        try {
            String currentDb = engine.getCatalog().getCurrentDatabase();
            if (currentDb != null) {
                Database db = engine.getCatalog().getDatabase(currentDb);
                if (db != null) {
                    for (final Schema schema : db.getAllSchemas()) {
                        String schemaName = schema.getName();
                        if (schemaName.toUpperCase().startsWith(prefix)) {
                            candidates.add(new Candidate(schemaName, schemaName, "schema", null, null, null, true));
                        }
                    }
                }
            }
        } catch (final Exception e) {
            // Ignore errors during completion
        }
    }

    private void addColumnCompletions(final String prefix, final List<Candidate> candidates) {
        try {
            String currentDb = engine.getCatalog().getCurrentDatabase();
            String currentSchema = engine.getCatalog().getCurrentSchema();

            if (currentDb != null && currentSchema != null) {
                Database db = engine.getCatalog().getDatabase(currentDb);
                if (db != null) {
                    Schema schema = db.getSchema(currentSchema);
                    if (schema != null) {
                        // Add columns from all tables in current schema
                        for (final Table table : schema.getTables()) {
                            for (final TableColumn column : table.getColumns()) {
                                String columnName = column.getName();
                                if (columnName.toUpperCase().startsWith(prefix)) {
                                    candidates.add(new Candidate(
                                            columnName,
                                            columnName,
                                            "column",
                                            table.getName() + "." + columnName,
                                            null,
                                            null,
                                            true
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        } catch (final Exception e) {
            // Ignore errors during completion
        }
    }
}
