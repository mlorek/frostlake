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

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.*;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.*;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses column / data-type / constraint / default-value grammar into metastore models — extracted from
 * {@link DDLCommandHandler} (CREATE TABLE, CREATE FUNCTION/PROCEDURE parameters, DROP FUNCTION arg types,
 * ALTER) so those handlers share one parser. Implements {@link CommandHandler} only to reuse its default
 * {@code getText}/{@code extractComment} helpers; the parsing itself is stateless.
 */
public class ColumnDefinitionParser implements CommandHandler {

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    public ColumnDefinitionParser(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    /**
     * Build a single {@link TableColumn} from one {@code columnDef} — its data type plus column-level
     * constraints (PRIMARY KEY, NOT NULL, UNIQUE, AUTOINCREMENT/IDENTITY, DEFAULT, column-level
     * REFERENCES, COLLATE, COMMENT). Shared by CREATE TABLE's {@link #parseColumnList} and ALTER TABLE
     * ADD COLUMN, so both honour DEFAULT / NOT NULL identically. Table-level PRIMARY KEY / FOREIGN KEY
     * constraints are the caller's concern, not this method's.
     */
    public TableColumn parseSingleColumnDef(final FrostlakeParser.ColumnDefContext colDef) {
        final String colName = getText(colDef.identifier());
        final DataType dataType = parseDataType(colDef.dataTypeName(), colDef.typeParameters());

        boolean primaryKey = false;
        boolean notNull = false;
        boolean unique = false;
        boolean autoIncrement = false;
        long identityStart = 1;
        long identityIncrement = 1;
        Object defaultValue = null;
        String referencedTable = null;
        String referencedColumn = null;
        String onDelete = null;
        String onUpdate = null;
        Boolean rely = null;
        String collation = null;

        for (final FrostlakeParser.ColumnConstraintContext constraint : colDef.columnConstraint()) {
            if (constraint.PRIMARY() != null) {
                primaryKey = true;
                rely = parseRelyOption(constraint.relyOption());
            } else if (constraint.NOT() != null) {
                notNull = true;
                rely = parseRelyOption(constraint.relyOption());
            } else if (constraint.UNIQUE() != null) {
                unique = true;
                rely = parseRelyOption(constraint.relyOption());
            } else if (constraint.AUTOINCREMENT() != null || constraint.IDENTITY() != null) {
                autoIncrement = true;
                // Seed + step from either (start, step) or START <n> INCREMENT <n> (both yield two
                // INTEGER_LITERALs under identityProperties). ORDER/NOORDER parse but don't change values.
                final FrostlakeParser.IdentityPropertiesContext props = constraint.identityProperties();
                if (props != null && props.INTEGER_LITERAL().size() == 2) {
                    identityStart = Long.parseLong(props.INTEGER_LITERAL(0).getText());
                    identityIncrement = Long.parseLong(props.INTEGER_LITERAL(1).getText());
                }
            } else if (constraint.DEFAULT() != null) {
                defaultValue = parseDefaultExpression(constraint.defaultExpression());
            } else if (constraint.REFERENCES() != null) {
                // Column-level foreign key: REFERENCES table(column)
                referencedTable = getText(constraint.qualifiedName());
                referencedColumn = getText(constraint.identifier());
                if (constraint.referentialActions() != null) {
                    final String[] actions = parseReferentialActions(constraint.referentialActions());
                    onDelete = actions[0];
                    onUpdate = actions[1];
                }
                rely = parseRelyOption(constraint.relyOption());
            } else if (constraint.collateClause() != null) {
                collation = extractCollation(constraint.collateClause());
            }
        }

        final TableColumn column = new TableColumn(colName, dataType, !notNull, defaultValue,
                                    primaryKey, unique, autoIncrement, identityStart, identityIncrement);

        // Set foreign key info if present
        if (referencedTable != null) {
            column.setReferencedTable(referencedTable);
            column.setReferencedColumn(referencedColumn);
            column.setOnDelete(onDelete);
            column.setOnUpdate(onUpdate);
        }

        // Set RELY/NORELY if specified
        if (rely != null) {
            column.setRely(rely);
        }

        final String comment = extractComment(colDef.commentClause());
        if (comment != null) {
            column.setComment(comment);
        }

        // Set collation if specified
        if (collation != null) {
            column.setCollation(collation);
        }

        return column;
    }

    public List<TableColumn> parseColumnList(final FrostlakeParser.ColumnListContext ctx) {
        List<TableColumn> columns = new ArrayList<>();
        List<String> tablePrimaryKeys = new ArrayList<>();
        List<ForeignKeyConstraint> tableForeignKeys = new ArrayList<>();

        for (final FrostlakeParser.ColumnOrConstraintContext item : ctx.columnOrConstraint()) {
            if (item.columnDef() != null) {
                columns.add(parseSingleColumnDef(item.columnDef()));
            } else if (item.tableConstraint() != null) {
                FrostlakeParser.TableConstraintContext constraint = item.tableConstraint();
                if (constraint.PRIMARY() != null) {
                    for (final FrostlakeParser.IdentifierContext id : constraint.identifierList(0).identifier()) {
                        tablePrimaryKeys.add(getText(id));
                    }
                } else if (constraint.FOREIGN() != null) {
                    // Table-level foreign key
                    String constraintName = constraint.constraintName() != null ?
                        getText(constraint.constraintName().identifier()) : null;

                    List<String> columnNames = new ArrayList<>();
                    for (final FrostlakeParser.IdentifierContext id : constraint.identifierList(0).identifier()) {
                        columnNames.add(getText(id));
                    }

                    String refTable = getText(constraint.qualifiedName());

                    List<String> refColumns = new ArrayList<>();
                    for (final FrostlakeParser.IdentifierContext id : constraint.identifierList(1).identifier()) {
                        refColumns.add(getText(id));
                    }

                    String onDelete = null;
                    String onUpdate = null;
                    if (constraint.referentialActions() != null) {
                        String[] actions = parseReferentialActions(constraint.referentialActions());
                        onDelete = actions[0];
                        onUpdate = actions[1];
                    }

                    Boolean rely = parseRelyOption(constraint.relyOption());

                    tableForeignKeys.add(new ForeignKeyConstraint(
                        constraintName, columnNames, refTable, refColumns, onDelete, onUpdate, rely
                    ));
                }
            }
        }

        if (!tablePrimaryKeys.isEmpty()) {
            List<TableColumn> updatedColumns = new ArrayList<>();
            for (final TableColumn col : columns) {
                boolean isPrimaryKey = false;
                for (final String pk : tablePrimaryKeys) {
                    if (pk.equalsIgnoreCase(col.getName())) {
                        isPrimaryKey = true;
                        break;
                    }
                }

                TableColumn newCol = new TableColumn(
                    col.getName(),
                    col.getDataType(),
                    col.isNullable(),
                    col.getDefaultValue(),
                    isPrimaryKey || col.isPrimaryKey(),
                    col.isUnique(),
                    col.isAutoIncrement(),
                    col.getIdentityStart(),      // 9-arg ctor: the 7-arg one resets identity to (1,1)
                    col.getIdentityIncrement()
                );
                newCol.setComment(col.getComment());
                newCol.setCollation(col.getCollation());

                // Copy foreign key info
                if (col.hasForeignKey()) {
                    newCol.setReferencedTable(col.getReferencedTable());
                    newCol.setReferencedColumn(col.getReferencedColumn());
                    newCol.setOnDelete(col.getOnDelete());
                    newCol.setOnUpdate(col.getOnUpdate());
                }

                updatedColumns.add(newCol);
            }
            columns = updatedColumns;
        }

        return columns;
    }

    public List<ForeignKeyConstraint> parseForeignKeys(final FrostlakeParser.ColumnListContext ctx) {
        List<ForeignKeyConstraint> foreignKeys = new ArrayList<>();

        for (final FrostlakeParser.ColumnOrConstraintContext item : ctx.columnOrConstraint()) {
            if (item.tableConstraint() != null) {
                FrostlakeParser.TableConstraintContext constraint = item.tableConstraint();
                if (constraint.FOREIGN() != null) {
                    String constraintName = constraint.constraintName() != null ?
                        getText(constraint.constraintName().identifier()) : null;

                    List<String> columnNames = new ArrayList<>();
                    for (final FrostlakeParser.IdentifierContext id : constraint.identifierList(0).identifier()) {
                        columnNames.add(getText(id));
                    }

                    String refTable = getText(constraint.qualifiedName());

                    List<String> refColumns = new ArrayList<>();
                    for (final FrostlakeParser.IdentifierContext id : constraint.identifierList(1).identifier()) {
                        refColumns.add(getText(id));
                    }

                    String onDelete = null;
                    String onUpdate = null;
                    if (constraint.referentialActions() != null) {
                        String[] actions = parseReferentialActions(constraint.referentialActions());
                        onDelete = actions[0];
                        onUpdate = actions[1];
                    }

                    Boolean rely = parseRelyOption(constraint.relyOption());

                    foreignKeys.add(new ForeignKeyConstraint(
                        constraintName, columnNames, refTable, refColumns, onDelete, onUpdate, rely
                    ));
                }
            }
        }

        return foreignKeys;
    }

    public Parameter parseParameterDef(final FrostlakeParser.ParameterDefContext p) {
        String name = getText(p.identifier());
        DataType type = parseDataType(p.dataTypeName(), p.typeParameters());
        String defaultVal = p.expression() != null ? getOriginalText(p.expression()) : null;
        return new Parameter(name, type, defaultVal);
    }

    public DataType parseDataType(final FrostlakeParser.DataTypeNameContext ctx) {
        return parseDataType(ctx, null);
    }

    public DataType parseDataType(final FrostlakeParser.DataTypeNameContext ctx, final FrostlakeParser.TypeParametersContext typeParams) {
        int precision = 9; // Default precision for timestamp types

        // Extract precision if typeParameters present
        if (typeParams != null && typeParams.INTEGER_LITERAL() != null && typeParams.INTEGER_LITERAL().size() > 0) {
            precision = Integer.parseInt(typeParams.INTEGER_LITERAL(0).getText());
        }

        if (ctx.INTEGER() != null || ctx.INT() != null) return NumericType.INTEGER;
        if (ctx.BIGINT() != null) return NumericType.BIGINT;
        if (ctx.SMALLINT() != null) return NumericType.SMALLINT;
        if (ctx.TINYINT() != null || ctx.BYTEINT() != null) return NumericType.TINYINT;
        if (ctx.NUMBER() != null || ctx.DECIMAL() != null) {
            if (typeParams != null && typeParams.INTEGER_LITERAL() != null && !typeParams.INTEGER_LITERAL().isEmpty()) {
                final int numberScale = typeParams.INTEGER_LITERAL().size() > 1
                    ? Integer.parseInt(typeParams.INTEGER_LITERAL(1).getText()) : 0;
                return new NumericType("NUMBER", precision, numberScale);
            }
            return NumericType.NUMBER;
        }
        if (ctx.FLOAT() != null || ctx.FLOAT4() != null || ctx.FLOAT8() != null || ctx.REAL() != null) return NumericType.FLOAT;
        if (ctx.DOUBLE() != null) return NumericType.DOUBLE;   // DOUBLE and DOUBLE PRECISION
        if (ctx.VARCHAR() != null || ctx.STRING() != null || ctx.TEXT() != null) {
            return hasTypeLength(typeParams) ? new StringType("VARCHAR", precision) : StringType.VARCHAR;
        }
        if (ctx.CHAR() != null) {
            return hasTypeLength(typeParams) ? new StringType("CHAR", precision) : StringType.CHAR;
        }
        if (ctx.BOOLEAN() != null) return BooleanType.BOOLEAN;
        if (ctx.DATE() != null) return DateTimeType.DATE;
        if (ctx.DATETIME() != null) return new DateTimeType("TIMESTAMP_NTZ", precision, false);
        if (ctx.TIMESTAMP() != null) return new DateTimeType("TIMESTAMP", precision, false);
        if (ctx.TIMESTAMP_NTZ() != null || ctx.TIMESTAMPNTZ() != null) return new DateTimeType("TIMESTAMP_NTZ", precision, false);
        if (ctx.TIMESTAMP_LTZ() != null) return new DateTimeType("TIMESTAMP_LTZ", precision, true);
        if (ctx.TIMESTAMP_TZ() != null) return new DateTimeType("TIMESTAMP_TZ", precision, true);
        if (ctx.VARIANT() != null) return VariantType.VARIANT;
        if (ctx.ARRAY() != null) return ArrayType.ARRAY;
        if (ctx.OBJECT() != null) return ObjectType.OBJECT;
        if (ctx.UUID() != null) return new StringType("UUID", 36);
        if (ctx.VECTOR() != null) {
            VectorType.ElementType elemType =
                ctx.INT() != null
                    ? VectorType.ElementType.INT
                    : VectorType.ElementType.FLOAT;
            int dim = ctx.INTEGER_LITERAL() != null
                ? Integer.parseInt(ctx.INTEGER_LITERAL().getText())
                : 1;
            return new VectorType(elemType, dim);
        }
        return StringType.VARCHAR;
    }

    private String[] parseReferentialActions(final FrostlakeParser.ReferentialActionsContext ctx) {
        String onDelete = null;
        String onUpdate = null;

        for (final FrostlakeParser.ReferentialActionContext action : ctx.referentialAction()) {
            String actionValue = parseReferentialOption(action.referentialOption());
            if (action.DELETE() != null) {
                onDelete = actionValue;
            } else if (action.UPDATE() != null) {
                onUpdate = actionValue;
            }
        }

        return new String[] { onDelete, onUpdate };
    }

    private String parseReferentialOption(final FrostlakeParser.ReferentialOptionContext ctx) {
        if (ctx.CASCADE() != null) {
            return "CASCADE";
        } else if (ctx.SET() != null && ctx.NULL() != null) {
            return "SET NULL";
        } else if (ctx.SET() != null && ctx.DEFAULT() != null) {
            return "SET DEFAULT";
        } else if (ctx.RESTRICT() != null) {
            return "RESTRICT";
        } else if (ctx.NO() != null && ctx.ACTION() != null) {
            return "NO ACTION";
        }
        return null;
    }

    private Boolean parseRelyOption(final FrostlakeParser.RelyOptionContext ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.RELY() != null) {
            return true;
        } else if (ctx.NORELY() != null) {
            return false;
        }
        return null;
    }

    /** True if the type carries an explicit length/precision parameter, e.g. VARCHAR(20) or CHAR(5). */
    private boolean hasTypeLength(final FrostlakeParser.TypeParametersContext typeParams) {
        return typeParams != null && typeParams.INTEGER_LITERAL() != null
            && !typeParams.INTEGER_LITERAL().isEmpty();
    }

    public Object parseDefaultExpression(final FrostlakeParser.DefaultExpressionContext ctx) {
        if (ctx.expression() != null) {
            FrostlakeParser.ExpressionContext expr = ctx.expression();

            // Handle literal expressions
            if (expr instanceof FrostlakeParser.LiteralExprContext) {
                FrostlakeParser.LiteralExprContext literalCtx = (FrostlakeParser.LiteralExprContext) expr;
                return parseLiteral(literalCtx.literal());
            }

            // Handle qualified names (identifiers like CURRENT_TIMESTAMP, TRUE, FALSE, NULL)
            if (expr instanceof FrostlakeParser.QualifiedNameExprContext) {
                FrostlakeParser.QualifiedNameExprContext nameCtx = (FrostlakeParser.QualifiedNameExprContext) expr;
                String name = getText(nameCtx.qualifiedName()).toUpperCase();
                // Return recognized special values
                if (name.equals("CURRENT_TIMESTAMP") || name.equals("CURRENT_DATE") ||
                    name.equals("CURRENT_TIME") || name.equals("TRUE") ||
                    name.equals("FALSE") || name.equals("NULL")) {
                    return name;
                }
            }

            // Handle function calls (store as canonical name for evaluation at insert time)
            if (expr instanceof FrostlakeParser.FunctionCallExprContext) {
                FrostlakeParser.FunctionCallExprContext funcCtx = (FrostlakeParser.FunctionCallExprContext) expr;
                String funcName = funcCtx.functionName().getText().toUpperCase();
                if (funcName.equals("CURRENT_TIMESTAMP") || funcName.equals("CURRENT_DATE") ||
                    funcName.equals("CURRENT_TIME")) {
                    return funcName;
                }
                if (funcName.equals("UUID_STRING")) {
                    return "UUID_STRING()";
                }
            }

            // Handle parenthesized expressions - unwrap them
            if (expr instanceof FrostlakeParser.ParenExprContext) {
                FrostlakeParser.ParenExprContext parenCtx = (FrostlakeParser.ParenExprContext) expr;
                FrostlakeParser.DefaultExpressionContext innerCtx = new FrostlakeParser.DefaultExpressionContext(null, 0);
                FrostlakeParser.BooleanExprContext be = parenCtx.booleanExpr();
                if (be instanceof FrostlakeParser.ValueExprContext) {
                    innerCtx.addChild(((FrostlakeParser.ValueExprContext) be).expression());
                }
                return parseDefaultExpression(innerCtx);
            }

            // Recognized context/datetime keywords can reach here as other context shapes (e.g. bare
            // CURRENT_TIMESTAMP); keep returning their canonical string so evaluateDefaultValue resolves
            // them and the stored metadata stays a plain keyword (not an opaque expression).
            final String canonical = getOriginalText(expr).trim().toUpperCase();
            switch (canonical) {
                case "CURRENT_TIMESTAMP":
                case "CURRENT_TIMESTAMP()":
                    return "CURRENT_TIMESTAMP";
                case "CURRENT_DATE":
                case "CURRENT_DATE()":
                    return "CURRENT_DATE";
                case "CURRENT_TIME":
                case "CURRENT_TIME()":
                    return "CURRENT_TIME";
                case "UUID_STRING()":
                    return "UUID_STRING()";
                case "TRUE":
                    return "TRUE";
                case "FALSE":
                    return "FALSE";
                case "NULL":
                    return "NULL";
                default:
                    break;
            }

            // A complex expression (arithmetic, concatenation, function call, CASE, …): keep the text
            // wrapped so INSERT evaluates it per row, rather than inserting the raw text as the value.
            // Use the ORIGINAL source text (whitespace preserved) — ctx.getText() concatenates tokens with
            // no spaces (e.g. "CASE WHEN 1=1 THEN…" → "CASEWHEN1=1THEN…"), which cannot be re-parsed.
            return new DefaultValueExpression(getOriginalText(expr));
        }
        return null;
    }

    public Object parseLiteral(final FrostlakeParser.LiteralContext ctx) {
        if (ctx.INTEGER_LITERAL() != null) {
            return Long.parseLong(ctx.INTEGER_LITERAL().getText());
        } else if (ctx.FLOAT_LITERAL() != null) {
            return Double.parseDouble(ctx.FLOAT_LITERAL().getText());
        } else if (ctx.STRING_LITERAL() != null) {
            return extractStringLiteral(ctx.STRING_LITERAL());
        } else if (ctx.DOLLAR_QUOTED_STRING() != null) {
            String text = ctx.DOLLAR_QUOTED_STRING().getText();
            if (text.startsWith("$$") && text.endsWith("$$")) {
                return text.substring(2, text.length() - 2);
            }
            return text;
        } else if (ctx.TRUE() != null) {
            return true;
        } else if (ctx.FALSE() != null) {
            return false;
        } else if (ctx.NULL() != null) {
            return "NULL";
        }
        return null;
    }

    private String extractCollation(final FrostlakeParser.CollateClauseContext ctx) {
        if (ctx == null || ctx.STRING_LITERAL() == null) {
            return null;
        }
        return extractStringLiteral(ctx.STRING_LITERAL());
    }

    private String extractStringLiteral(final TerminalNode node) {
        return SqlStringLiterals.decode(node.getText());
    }

    private String getOriginalText(final ParserRuleContext ctx) {
        if (ctx.start == null || ctx.stop == null || ctx.start.getInputStream() == null) {
            return ctx.getText();
        }
        return ctx.start.getInputStream().getText(
            new Interval(ctx.start.getStartIndex(), ctx.stop.getStopIndex())
        );
    }
}
