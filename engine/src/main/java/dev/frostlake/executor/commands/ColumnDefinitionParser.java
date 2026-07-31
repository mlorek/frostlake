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

import dev.frostlake.executor.ParseTreeText;
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
        final String colName = ParseTreeText.namePartText(colDef.namePart());
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
                if (props != null) {
                    if (props.signedInteger().size() == 2) {
                        // (start, step) paren form
                        identityStart = parseSignedInteger(props.signedInteger(0));
                        identityIncrement = parseSignedInteger(props.signedInteger(1));
                    }
                    for (final FrostlakeParser.IdentityWordOptionContext opt : props.identityWordOption()) {
                        if (opt.START() != null) {
                            identityStart = parseSignedInteger(opt.signedInteger());
                        } else if (opt.INCREMENT() != null) {
                            identityIncrement = parseSignedInteger(opt.signedInteger());
                        }
                        // ORDER / NOORDER parse but don't change values.
                    }
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

        rejectNonNullableFieldsInNullableStructure(colName, dataType, notNull);

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
        List<String> tableUniqueColumns = new ArrayList<>();
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
                } else if (constraint.UNIQUE() != null) {
                    // Table-level UNIQUE (a, b): every listed column carries the uniqueness flag. The fact
                    // that they form ONE constraint (and any CONSTRAINT <name>) is kept separately — see
                    // parseUniqueConstraints, which the CREATE TABLE handler records on the table.
                    for (final FrostlakeParser.IdentifierContext id : constraint.identifierList(0).identifier()) {
                        tableUniqueColumns.add(getText(id));
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

        if (!tablePrimaryKeys.isEmpty() || !tableUniqueColumns.isEmpty()) {
            List<TableColumn> updatedColumns = new ArrayList<>();
            for (final TableColumn col : columns) {
                final boolean isPrimaryKey = namesContain(tablePrimaryKeys, col.getName());
                final boolean isUnique = namesContain(tableUniqueColumns, col.getName());

                TableColumn newCol = new TableColumn(
                    col.getName(),
                    col.getDataType(),
                    col.isNullable(),
                    col.getDefaultValue(),
                    isPrimaryKey || col.isPrimaryKey(),
                    isUnique || col.isUnique(),
                    col.isAutoIncrement(),
                    col.getIdentityStart(),      // 9-arg ctor: the 7-arg one resets identity to (1,1)
                    col.getIdentityIncrement()
                );
                newCol.setComment(col.getComment());
                newCol.setCollation(col.getCollation());
                newCol.setRely(col.getRely());

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

    /** Case-insensitive membership of a column name in a table-level constraint's column list. */
    private boolean namesContain(final List<String> names, final String columnName) {
        for (final String name : names) {
            if (name.equalsIgnoreCase(columnName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The name an explicit table-level {@code CONSTRAINT <name> PRIMARY KEY (...)} gave the key, or null
     * when it was declared without one (or only as a column-level {@code PRIMARY KEY}). A table has at most
     * one primary key, so the first such declaration wins.
     */
    public String parsePrimaryKeyConstraintName(final FrostlakeParser.ColumnListContext ctx) {
        for (final FrostlakeParser.ColumnOrConstraintContext item : ctx.columnOrConstraint()) {
            final FrostlakeParser.TableConstraintContext constraint = item.tableConstraint();
            if (constraint != null && constraint.PRIMARY() != null && constraint.constraintName() != null) {
                return getText(constraint.constraintName().identifier());
            }
        }
        return null;
    }

    /**
     * The table-level UNIQUE constraints of a column list — ONE per declaration, so {@code UNIQUE (a, b)} is
     * a single multi-column constraint rather than one constraint per column. Unnamed ones auto-name
     * themselves; column-level {@code UNIQUE} declarations are not returned here (they are plain column
     * flags and {@code Table} names them itself).
     */
    public List<UniqueConstraint> parseUniqueConstraints(final FrostlakeParser.ColumnListContext ctx) {
        final List<UniqueConstraint> uniques = new ArrayList<>();
        for (final FrostlakeParser.ColumnOrConstraintContext item : ctx.columnOrConstraint()) {
            final FrostlakeParser.TableConstraintContext constraint = item.tableConstraint();
            if (constraint == null || constraint.UNIQUE() == null) {
                continue;
            }
            final String constraintName = constraint.constraintName() != null
                ? getText(constraint.constraintName().identifier()) : null;
            final List<String> columnNames = new ArrayList<>();
            for (final FrostlakeParser.IdentifierContext id : constraint.identifierList(0).identifier()) {
                columnNames.add(getText(id));
            }
            uniques.add(new UniqueConstraint(constraintName, columnNames));
        }
        return uniques;
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
        return DataTypeParser.parse(ctx, typeParams);
    }

    /**
     * Snowflake's structured-nullability DDL rule, every cell live-measured:
     * a NOT NULL structured field is legal only when EVERYTHING enclosing it is itself non-nullable.
     * A NOT NULL field whose immediately-enclosing object (the column, or an object-typed field) is
     * nullable fails with "DDL operation failed because it would result in a non-nullable structured
     * type field '&lt;path&gt;' contained within a nullable object" — so {@code o OBJECT(inner
     * OBJECT(x INT NOT NULL)) NOT NULL} is still refused ('O.inner.x'; {@code inner} is nullable)
     * while marking every level NOT NULL is accepted. Under an ARRAY or MAP the refusal is
     * unconditional — "'&lt;path&gt;' contained within an array or map" — with path segments
     * {@code .element} (array) / {@code .value} (map), even when the column is NOT NULL.
     */
    private void rejectNonNullableFieldsInNullableStructure(final String columnName,
                                                            final DataType dataType,
                                                            final boolean columnNotNull) {
        walkStructuredNullability(columnName, dataType, !columnNotNull, false);
    }

    /**
     * @param path              dotted path to this position ({@code COL}, {@code COL.f},
     *                          {@code COL.a.element}, …) — upper column name, field names verbatim
     * @param enclosingNullable whether the immediately-enclosing object or column is nullable here
     * @param insideContainer   whether an ARRAY or MAP lies between the column and this position
     */
    private void walkStructuredNullability(final String path, final DataType type,
                                           final boolean enclosingNullable,
                                           final boolean insideContainer) {
        if (type instanceof StructuredObjectType) {
            for (final StructuredField field : ((StructuredObjectType) type).getFields()) {
                final String fieldPath = path + "." + field.getName();
                if (field.isNotNull() && insideContainer) {
                    throw new RuntimeException("SQL compilation error: DDL operation failed because it"
                        + " would result in a non-nullable structured type field '" + fieldPath
                        + "' contained within an array or map");
                }
                if (field.isNotNull() && enclosingNullable) {
                    throw new RuntimeException("SQL compilation error: DDL operation failed because it"
                        + " would result in a non-nullable structured type field '" + fieldPath
                        + "' contained within a nullable object");
                }
                walkStructuredNullability(fieldPath, field.getDataType(), !field.isNotNull(),
                    insideContainer);
            }
        } else if (type instanceof MapType) {
            walkStructuredNullability(path + ".value", ((MapType) type).getValueType(), true, true);
        } else if (type instanceof ArrayType && ((ArrayType) type).getElementType() != null) {
            walkStructuredNullability(path + ".element", ((ArrayType) type).getElementType(),
                true, true);
        }
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

    public Object parseDefaultExpression(final FrostlakeParser.DefaultExpressionContext ctx) {
        if (ctx.expression() != null) {
            FrostlakeParser.ExpressionContext expr = ctx.expression();

            // Handle literal expressions
            if (expr instanceof FrostlakeParser.LiteralExprContext) {
                FrostlakeParser.LiteralExprContext literalCtx = (FrostlakeParser.LiteralExprContext) expr;
                // Keep DEFAULT NULL as the canonical text "NULL" (mirrors the qualified-name path below),
                // so the column records an explicit NULL default that is evaluated at insert time.
                if (literalCtx.literal().NULL() != null) {
                    return "NULL";
                }
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
            // A NULL literal is a real null. (Passing NULL as a CALL argument or SET value must bind
            // null, not the string "NULL" — otherwise :param IS NOT NULL is wrongly true.)
            return null;
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

    /** The value of a signedInteger context ({@code MINUS? INTEGER_LITERAL}). */
    private static long parseSignedInteger(final FrostlakeParser.SignedIntegerContext ctx) {
        final long value = Long.parseLong(ctx.INTEGER_LITERAL().getText());
        return ctx.MINUS() != null ? -value : value;
    }

}
