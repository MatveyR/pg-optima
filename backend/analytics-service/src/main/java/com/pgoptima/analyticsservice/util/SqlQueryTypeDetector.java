package com.pgoptima.analyticsservice.util;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqlQueryTypeDetector {

    private static final Logger log = LoggerFactory.getLogger(SqlQueryTypeDetector.class);

    public enum QueryType {
        SELECT, INSERT, UPDATE, DELETE, DDL, UNKNOWN
    }

    public static QueryType detect(String sql) {
        if (sql == null || sql.trim().isEmpty()) return QueryType.UNKNOWN;
        String upper = sql.trim().toUpperCase();
        if (upper.startsWith("SELECT")) return QueryType.SELECT;
        if (upper.startsWith("INSERT")) return QueryType.INSERT;
        if (upper.startsWith("UPDATE")) return QueryType.UPDATE;
        if (upper.startsWith("DELETE")) return QueryType.DELETE;
        if (upper.startsWith("CREATE") || upper.startsWith("ALTER") ||
                upper.startsWith("DROP") || upper.startsWith("TRUNCATE")) {
            return QueryType.DDL;
        }
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            if (stmt instanceof Select) return QueryType.SELECT;
            if (stmt instanceof Insert) return QueryType.INSERT;
            if (stmt instanceof Update) return QueryType.UPDATE;
            if (stmt instanceof Delete) return QueryType.DELETE;
        } catch (JSQLParserException e) {
            log.warn("Failed to parse SQL: {}", sql, e);
        }
        return QueryType.UNKNOWN;
    }

    public static boolean isModifyingQuery(String sql) {
        QueryType type = detect(sql);
        return type == QueryType.INSERT || type == QueryType.UPDATE ||
                type == QueryType.DELETE || type == QueryType.DDL;
    }
}