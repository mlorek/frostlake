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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when SQL syntax errors are detected during parsing
 */
public class SqlSyntaxException extends RuntimeException {

    private final List<String> syntaxErrors;
    private final String failedSql;
    private boolean declareSectionEnd;
    private UnknownTypeReading unknownTypes;
    private WholeStatementReading wholeStatement;

    public SqlSyntaxException(final String message, final List<String> syntaxErrors, final String failedSql) {
        super(message);
        this.syntaxErrors = new ArrayList<>(syntaxErrors);
        this.failedSql = failedSql;
    }

    public List<String> getSyntaxErrors() {
        return Collections.unmodifiableList(syntaxErrors);
    }

    public String getFailedSql() {
        return failedSql;
    }

    /**
     * Whether the text was refused at an END written where a DECLARE section's BEGIN belongs: a fault the text has
     * for certain, whatever else in it this grammar could not read.
     *
     * @return true for that refusal
     */
    public boolean refusesDeclareSectionEnd() {
        return declareSectionEnd;
    }

    /** Marks the refusal as one of an END written where a DECLARE section's BEGIN belongs. */
    void markDeclareSectionEnd() {
        declareSectionEnd = true;
    }

    /**
     * The statement read with its words that name no type taken as types, when it was refused for one of them: what
     * else the statement says of its types can then be judged ahead of the word.
     *
     * @return the reading, or null for any other refusal
     */
    public UnknownTypeReading getUnknownTypes() {
        return unknownTypes;
    }

    /** Records the reading a refusal for a word that names no type was made from. */
    void setUnknownTypes(final UnknownTypeReading reading) {
        unknownTypes = reading;
    }

    /**
     * The statement of a request of several that the account reads whole where this grammar refused only that
     * statement: the request then runs the statements before it and fails at it (see {@link WholeStatementReading}).
     *
     * @return the reading, or null for any other refusal
     */
    public WholeStatementReading getWholeStatement() {
        return wholeStatement;
    }

    /** Records the statement the account reads whole. */
    void setWholeStatement(final WholeStatementReading reading) {
        wholeStatement = reading;
    }

    /**
     * The same refusal told of another text: the one a reading of this refusal's text stands for, every place the
     * same in both.
     *
     * @param text the text the refusal is told of
     * @return the refusal
     */
    public SqlSyntaxException ofText(final String text) {
        final SqlSyntaxException told = new SqlSyntaxException(getMessage(), syntaxErrors, text);
        told.declareSectionEnd = declareSectionEnd;
        told.unknownTypes = unknownTypes;
        return told;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SqlSyntaxException: ");
        sb.append(getMessage());
        return sb.toString();
    }
}
