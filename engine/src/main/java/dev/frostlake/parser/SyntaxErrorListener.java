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

package dev.frostlake.parser;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom ANTLR error listener to capture syntax errors
 * Collects all syntax errors and can throw an exception with detailed error information
 */
public class SyntaxErrorListener extends BaseErrorListener {

    private static final Logger logger = LoggerFactory.getLogger(SyntaxErrorListener.class);

    private final List<String> errors;
    private final String sql;
    // Speculative parses (is this UDF body a query or a bare expression?) EXPECT to fail on one of
    // the alternatives; logging their syntax errors at ERROR flooded the log with noise for every
    // perfectly working expression-body UDF. Quiet mode demotes the logging to DEBUG — the collected
    // errors and throwIfErrors() behave identically.
    private final boolean quiet;

    public SyntaxErrorListener(final String sql) {
        this(sql, false);
    }

    public SyntaxErrorListener(final String sql, final boolean quiet) {
        this.errors = new ArrayList<>();
        this.sql = sql;
        this.quiet = quiet;
    }

    @Override
    public void syntaxError(final Recognizer<?, ?> recognizer,
                           final Object offendingSymbol,
                           final int line,
                           final int charPositionInLine,
                           final String msg,
                           final RecognitionException e) {
        String error = String.format("Syntax error at line %d:%d - %s", line, charPositionInLine, msg);
        errors.add(error);
        if (quiet) {
            logger.debug("=> SQL syntax error (speculative parse): {}", error);
        } else {
            logger.error("=> SQL syntax error: {}", error);
        }
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    public void throwIfErrors() {
        if (hasErrors()) {
            StringBuilder sb = new StringBuilder();
            sb.append("SQL syntax error(s) found:\n");
            for (final String error : errors) {
                sb.append("  - ").append(error).append("\n");
            }
            sb.append("\n-Failed query:\n").append(sql);

            if (quiet) {
                logger.debug("=> Failed speculative parse:\n{}", sql);
            } else {
                logger.error("=> Failed to parse SQL query:\n{}", sql);
            }
            throw new SqlSyntaxException(sb.toString(), errors, sql);
        }
    }
}
