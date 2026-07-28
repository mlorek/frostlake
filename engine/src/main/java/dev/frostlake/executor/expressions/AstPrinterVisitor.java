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
import java.util.Map;

/**
 * Renders an {@link Expression} AST into a compact, canonical S-expression string.
 *
 * <p>Purpose: stable, human-readable assertions in parser/builder tests and old-vs-new
 * parser diffing during the migration to an ANTLR-driven expression AST. The rendered
 * form is deliberately independent of each node's own {@code toString()} so it can stay
 * stable as the nodes evolve.
 */
public class AstPrinterVisitor implements ExpressionVisitor<String> {

    /** Convenience: render an expression to its canonical string. */
    public static String print(final Expression expression) {
        return expression.accept(new AstPrinterVisitor());
    }

    @Override
    public String visitLiteral(final LiteralExpression expr) {
        switch (expr.getType()) {
            case NULL:
                return "null";
            case STRING:
                return "'" + expr.getValue() + "'";
            default:
                return String.valueOf(expr.getValue());
        }
    }

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        if (expr.isQualified()) {
            return expr.getTableName() + "." + expr.getColumnName();
        }
        return expr.getColumnName();
    }

    @Override
    public String visitBinaryOperation(final BinaryOperationExpression expr) {
        return "(" + expr.getLeft().accept(this) + " " + symbol(expr.getOperator())
            + " " + expr.getRight().accept(this) + ")";
    }

    @Override
    public String visitUnaryOperation(final UnaryOperationExpression expr) {
        switch (expr.getOperator()) {
            case NOT:
                return "(NOT " + expr.getOperand().accept(this) + ")";
            case NEGATE:
                return "(- " + expr.getOperand().accept(this) + ")";
            case EXISTS:
                return "(EXISTS " + expr.getOperand().accept(this) + ")";
            default:
                return "(" + expr.getOperator() + " " + expr.getOperand().accept(this) + ")";
        }
    }

    @Override
    public String visitIsNull(final IsNullExpression expr) {
        return "(" + expr.getOperand().accept(this) + (expr.isNot() ? " IS NOT NULL" : " IS NULL") + ")";
    }

    @Override
    public String visitLambda(final LambdaExpression expr) {
        return "(" + String.join(", ", expr.getParameters()) + ") -> " + expr.getBody().accept(this);
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        final StringBuilder sb = new StringBuilder();
        sb.append(expr.getFunctionName()).append("(");
        if (expr.isDistinct()) {
            sb.append("DISTINCT ");
        }
        if (expr.isStar()) {
            sb.append("*");
        } else {
            final List<Expression> args = expr.getArguments();
            final List<String> names = expr.getArgumentNames();
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                if (names != null && names.get(i) != null) {
                    sb.append(names.get(i)).append(" => ");
                }
                sb.append(args.get(i).accept(this));
            }
        }
        return sb.append(")").toString();
    }

    @Override
    public String visitCaseExpression(final CaseExpression expr) {
        final StringBuilder sb = new StringBuilder("(CASE");
        for (final CaseExpression.WhenClause when : expr.getWhenClauses()) {
            sb.append(" WHEN ").append(when.getCondition().accept(this))
              .append(" THEN ").append(when.getResult().accept(this));
        }
        if (expr.getElseExpression() != null) {
            sb.append(" ELSE ").append(expr.getElseExpression().accept(this));
        }
        return sb.append(" END)").toString();
    }

    @Override
    public String visitCast(final CastExpression expr) {
        return "(" + expr.getExpression().accept(this) + " :: " + expr.getTargetType() + ")";
    }

    @Override
    public String visitObjectAccess(final ObjectAccessExpression expr) {
        return "(" + expr.getBase().accept(this) + ":" + String.join(".", expr.getPathParts()) + ")";
    }

    @Override
    public String visitArrayAccess(final ArrayAccessExpression expr) {
        return "(" + expr.getArray().accept(this) + "[" + expr.getIndex().accept(this) + "])";
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        return "(subquery " + expr.getSubquery() + ")";
    }

    @Override
    public String visitExecuteImmediate(final ExecuteImmediateExpression expr) {
        final StringBuilder sb = new StringBuilder("EXECUTE IMMEDIATE ").append(expr.getSqlExpression().accept(this));
        final List<Expression> binds = expr.getUsingBindings();
        if (binds != null && !binds.isEmpty()) {
            sb.append(" USING (");
            for (int i = 0; i < binds.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(binds.get(i).accept(this));
            }
            sb.append(")");
        }
        return sb.toString();
    }

    @Override
    public String visitJsonObject(final JsonObjectExpression expr) {
        final StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (final Map.Entry<String, Expression> entry : expr.getProperties().entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("'").append(entry.getKey()).append("': ").append(entry.getValue().accept(this));
            first = false;
        }
        return sb.append("}").toString();
    }

    @Override
    public String visitJsonArray(final JsonArrayExpression expr) {
        final StringBuilder sb = new StringBuilder("[");
        final List<Expression> elements = expr.getElements();
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(elements.get(i).accept(this));
        }
        return sb.append("]").toString();
    }

    @Override
    public String visitBetween(final BetweenExpression expr) {
        return "(" + expr.getValue().accept(this)
            + (expr.isNot() ? " NOT BETWEEN " : " BETWEEN ")
            + expr.getLower().accept(this) + " AND " + expr.getUpper().accept(this) + ")";
    }

    @Override
    public String visitIn(final InExpression expr) {
        final StringBuilder sb = new StringBuilder("(");
        sb.append(expr.getValue().accept(this));
        sb.append(expr.isNot() ? " NOT IN (" : " IN (");
        if (expr.hasSubquery()) {
            sb.append(expr.getSubquery().accept(this));
        } else {
            final List<Expression> values = expr.getValues();
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(values.get(i).accept(this));
            }
        }
        return sb.append("))").toString();
    }

    @Override
    public String visitTupleIn(final TupleInExpression expr) {
        final StringBuilder sb = new StringBuilder("((");
        final List<Expression> values = expr.getValues();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values.get(i).accept(this));
        }
        sb.append(expr.isNot() ? ") NOT IN (" : ") IN (");
        if (expr.hasSubquery()) {
            sb.append(expr.getSubquery().accept(this));
        } else {
            final List<Expression> list = expr.getListValues();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(list.get(i).accept(this));
            }
        }
        return sb.append("))").toString();
    }

    @Override
    public String visitQuantifiedComparison(final QuantifiedComparisonExpression expr) {
        return "(" + expr.getLeft().accept(this) + " " + symbol(expr.getOperator()) + " "
            + expr.getQuantifier() + " " + expr.getSubquery().accept(this) + ")";
    }

    @Override
    public String visitInterval(final IntervalExpression expr) {
        return "(INTERVAL " + expr.getValueExpression().accept(this) + " " + expr.getUnit() + ")";
    }

    @Override
    public String visitSessionVar(final SessionVarExpression expr) {
        return "$" + expr.getVarName();
    }

    @Override
    public String visitBindVariable(final BindVariableExpression expr) {
        return ":" + expr.getVarName();
    }

    @Override
    public String visitSystemStreamHasData(final SystemStreamHasDataExpression expr) {
        return "SYSTEM$STREAM_HAS_DATA(" + expr.getStreamNameExpr().accept(this) + ")";
    }

    @Override
    public String visitSystemUserTaskCancel(final SystemUserTaskCancelExpression expr) {
        return "SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS(" + expr.getTaskNameExpr().accept(this) + ")";
    }

    @Override
    public String visitWindowFunction(final WindowFunctionExpression expr) {
        return expr.getCallText();
    }

    private String symbol(final BinaryOperator op) {
        switch (op) {
            case ADD: return "+";
            case SUBTRACT: return "-";
            case MULTIPLY: return "*";
            case DIVIDE: return "/";
            case MODULO: return "%";
            case EQUAL: return "=";
            case NOT_EQUAL: return "<>";
            case LESS_THAN: return "<";
            case LESS_THAN_OR_EQUAL: return "<=";
            case GREATER_THAN: return ">";
            case GREATER_THAN_OR_EQUAL: return ">=";
            case AND: return "AND";
            case OR: return "OR";
            case CONCAT: return "||";
            case LIKE: return "LIKE";
            case ILIKE: return "ILIKE";
            case NOT_LIKE: return "NOT LIKE";
            case NOT_ILIKE: return "NOT ILIKE";
            default: return op.toString();
        }
    }
}
