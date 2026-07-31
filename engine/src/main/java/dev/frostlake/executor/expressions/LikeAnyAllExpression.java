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

package dev.frostlake.executor.expressions;

import java.util.List;

/**
 * Snowflake multi-pattern matching: {@code x LIKE ANY (p1, p2, …)}, {@code x LIKE ALL (…)} and
 * {@code x ILIKE ANY (…)}, with an optional shared ESCAPE. This is NOT the OR/AND expansion of the
 * single-pattern predicate: Snowflake skips NULL patterns entirely (live-verified —
 * {@code 'a' LIKE ALL ('a', NULL)} is TRUE and {@code 'a' LIKE ANY ('b', NULL)} is FALSE, where
 * three-valued logic would say NULL), returning NULL only for a NULL subject or when every pattern
 * is NULL.
 */
public class LikeAnyAllExpression implements Expression {
    private final Expression subject;
    private final List<Expression> patterns;
    private final boolean all;
    private final boolean caseInsensitive;
    private final Expression escape;

    public LikeAnyAllExpression(final Expression subject, final List<Expression> patterns,
                                final boolean all, final boolean caseInsensitive, final Expression escape) {
        this.subject = subject;
        this.patterns = patterns;
        this.all = all;
        this.caseInsensitive = caseInsensitive;
        this.escape = escape;
    }

    public Expression getSubject() {
        return subject;
    }

    public List<Expression> getPatterns() {
        return patterns;
    }

    public boolean isAll() {
        return all;
    }

    public boolean isCaseInsensitive() {
        return caseInsensitive;
    }

    public Expression getEscape() {
        return escape;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitLikeAnyAll(this);
    }

    @Override
    public String toString() {
        final StringBuilder out = new StringBuilder();
        out.append(subject).append(caseInsensitive ? " ILIKE " : " LIKE ").append(all ? "ALL (" : "ANY (");
        for (int i = 0; i < patterns.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(patterns.get(i));
        }
        out.append(')');
        if (escape != null) {
            out.append(" ESCAPE ").append(escape);
        }
        return out.toString();
    }
}
