package com.pgoptima.analyticsservice.util;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class SqlQueryParser {

    public ParsedQuery parse(String sql) {
        ParsedQuery result = new ParsedQuery();
        result.setOriginalSql(sql);
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            if (stmt instanceof Select) {
                Select select = (Select) stmt;
                processSelectBody(select.getSelectBody(), result);
            } else {
                result.setQueryType("UNKNOWN");
                result.setParseError("Only SELECT queries are fully supported for analysis");
            }
        } catch (JSQLParserException e) {
            log.warn("Failed to parse SQL: {}", e.getMessage());
            result.setParseError(e.getMessage());
        }
        return result;
    }

    private void processSelectBody(SelectBody selectBody, ParsedQuery result) {
        if (selectBody instanceof PlainSelect) {
            PlainSelect plain = (PlainSelect) selectBody;
            result.setQueryType("SELECT");
            if (plain.getFromItem() instanceof Table) {
                Table table = (Table) plain.getFromItem();
                result.addTable(table.getName());
            }
            if (plain.getJoins() != null) {
                for (Join join : plain.getJoins()) {
                    if (join.getRightItem() instanceof Table) {
                        Table table = (Table) join.getRightItem();
                        result.addTable(table.getName());
                        if (join.getOnExpression() != null) {
                            collectColumnsFromExpression(join.getOnExpression(), result.getJoinColumns());
                        }
                    }
                }
            }
            if (plain.getWhere() != null) {
                result.setWhereExpression(plain.getWhere());
                collectColumnsFromExpression(plain.getWhere(), result.getWhereColumns());
            }
            if (plain.getGroupBy() != null && plain.getGroupBy().getGroupByExpressions() != null) {
                for (Expression expr : plain.getGroupBy().getGroupByExpressions()) {
                    collectColumnsFromExpression(expr, result.getGroupByColumns());
                }
            }
            if (plain.getOrderByElements() != null) {
                for (OrderByElement orderBy : plain.getOrderByElements()) {
                    collectColumnsFromExpression(orderBy.getExpression(), result.getOrderByColumns());
                }
            }
            if (plain.getLimit() != null) result.setHasLimit(true);
            if (plain.getDistinct() != null) result.setHasDistinct(true);
            if (plain.getSelectItems() != null) {
                for (SelectItem item : plain.getSelectItems()) {
                    if (item instanceof SelectExpressionItem) {
                        Expression expr = ((SelectExpressionItem) item).getExpression();
                        if (expr instanceof Column) {
                            result.getSelectColumns().add(((Column) expr).getColumnName());
                        } else {
                            result.getSelectExpressions().add(expr.toString());
                        }
                    }
                }
            }
        } else if (selectBody instanceof SetOperationList) {
            result.setQueryType("UNION/INTERSECT/EXCEPT");
        } else if (selectBody instanceof WithItem) {
            result.setQueryType("WITH");
        }
    }

    private void collectColumnsFromExpression(Expression expr, Set<String> target) {
        if (expr == null) return;
        Deque<Expression> stack = new ArrayDeque<>();
        stack.push(expr);
        while (!stack.isEmpty()) {
            Expression current = stack.pop();
            if (current instanceof Column) {
                target.add(((Column) current).getColumnName());
            } else if (current instanceof BinaryExpression) {
                BinaryExpression bin = (BinaryExpression) current;
                if (bin.getLeftExpression() != null) stack.push(bin.getLeftExpression());
                if (bin.getRightExpression() != null) stack.push(bin.getRightExpression());
            } else if (current instanceof LikeExpression) {
                LikeExpression like = (LikeExpression) current;
                if (like.getLeftExpression() != null) stack.push(like.getLeftExpression());
                if (like.getRightExpression() != null) stack.push(like.getRightExpression());
            } else if (current instanceof InExpression) {
                InExpression in = (InExpression) current;
                if (in.getLeftExpression() != null) stack.push(in.getLeftExpression());
            } else if (current instanceof Between) {
                Between between = (Between) current;
                if (between.getLeftExpression() != null) stack.push(between.getLeftExpression());
                if (between.getBetweenExpressionStart() != null) stack.push(between.getBetweenExpressionStart());
                if (between.getBetweenExpressionEnd() != null) stack.push(between.getBetweenExpressionEnd());
            } else if (current instanceof AndExpression) {
                AndExpression and = (AndExpression) current;
                if (and.getLeftExpression() != null) stack.push(and.getLeftExpression());
                if (and.getRightExpression() != null) stack.push(and.getRightExpression());
            } else if (current instanceof OrExpression) {
                OrExpression or = (OrExpression) current;
                if (or.getLeftExpression() != null) stack.push(or.getLeftExpression());
                if (or.getRightExpression() != null) stack.push(or.getRightExpression());
            }
        }
    }

    public static class ParsedQuery {
        private String originalSql;
        private String queryType;
        private String parseError;
        private final Set<String> tables = new HashSet<>();
        private final Set<String> whereColumns = new HashSet<>();
        private final Set<String> joinColumns = new HashSet<>();
        private final Set<String> groupByColumns = new HashSet<>();
        private final Set<String> orderByColumns = new HashSet<>();
        private final Set<String> selectColumns = new HashSet<>();
        private final Set<String> selectExpressions = new HashSet<>();
        private Expression whereExpression;
        private boolean hasLimit;
        private boolean hasDistinct;

        public String getOriginalSql() { return originalSql; }
        public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }
        public String getQueryType() { return queryType; }
        public void setQueryType(String queryType) { this.queryType = queryType; }
        public String getParseError() { return parseError; }
        public void setParseError(String parseError) { this.parseError = parseError; }
        public Set<String> getTables() { return tables; }
        public void addTable(String table) { tables.add(table); }
        public Set<String> getWhereColumns() { return whereColumns; }
        public Set<String> getJoinColumns() { return joinColumns; }
        public Set<String> getGroupByColumns() { return groupByColumns; }
        public Set<String> getOrderByColumns() { return orderByColumns; }
        public Set<String> getSelectColumns() { return selectColumns; }
        public Set<String> getSelectExpressions() { return selectExpressions; }
        public Expression getWhereExpression() { return whereExpression; }
        public void setWhereExpression(Expression whereExpression) { this.whereExpression = whereExpression; }
        public boolean hasLimit() { return hasLimit; }
        public void setHasLimit(boolean hasLimit) { this.hasLimit = hasLimit; }
        public boolean hasDistinct() { return hasDistinct; }
        public void setHasDistinct(boolean hasDistinct) { this.hasDistinct = hasDistinct; }
    }
}