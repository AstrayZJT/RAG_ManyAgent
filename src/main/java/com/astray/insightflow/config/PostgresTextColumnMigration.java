package com.astray.insightflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PostgresTextColumnMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PostgresTextColumnMigration.class);

    private static final List<ColumnRef> COLUMNS = List.of(
            new ColumnRef("graph_checkpoint_meta", "state_json"),
            new ColumnRef("graph_checkpoint_meta", "state_summary_json"),
            new ColumnRef("final_report", "report_markdown"),
            new ColumnRef("final_report", "report_json"),
            new ColumnRef("document_chunk", "content"),
            new ColumnRef("task_plan", "plan_json"),
            new ColumnRef("tool_call_log", "input_json"),
            new ColumnRef("tool_call_log", "output_json"),
            new ColumnRef("tool_call_log", "metrics_json"),
            new ColumnRef("agent_run_log", "output_json"),
            new ColumnRef("agent_run_log", "metrics_json"),
            new ColumnRef("evaluation_record", "details_json"),
            new ColumnRef("verified_claim", "support_evidence_json"),
            new ColumnRef("verified_claim", "conflict_evidence_json")
    );

    private final JdbcTemplate jdbcTemplate;

    public PostgresTextColumnMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute((Connection connection) -> {
            if (!isPostgres(connection)) {
                return null;
            }
            for (ColumnRef ref : COLUMNS) {
                migrateColumn(connection, ref);
            }
            return null;
        });
    }

    private boolean isPostgres(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String productName = metaData.getDatabaseProductName();
        return productName != null && productName.toLowerCase().contains("postgresql");
    }

    private void migrateColumn(Connection connection, ColumnRef ref) throws SQLException {
        String currentType = readColumnType(connection, ref.tableName(), ref.columnName());
        if (currentType == null || "text".equalsIgnoreCase(currentType)) {
            return;
        }

        String alterSql;
        if ("oid".equalsIgnoreCase(currentType)) {
            alterSql = """
                    alter table %s alter column %s type text using case
                        when %s is null then null
                        else convert_from(lo_get(%s), 'UTF8')
                    end
                    """.formatted(ref.tableName(), ref.columnName(), ref.columnName(), ref.columnName());
        } else {
            alterSql = "alter table %s alter column %s type text using %s::text"
                    .formatted(ref.tableName(), ref.columnName(), ref.columnName());
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(alterSql);
        }
        log.info("Migrated {}.{} from {} to text", ref.tableName(), ref.columnName(), currentType);
    }

    private String readColumnType(Connection connection, String tableName, String columnName) throws SQLException {
        String sql = """
                select data_type
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = ?
                  and column_name = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString(1);
                }
            }
        }
        return null;
    }

    private record ColumnRef(String tableName, String columnName) {
    }
}
