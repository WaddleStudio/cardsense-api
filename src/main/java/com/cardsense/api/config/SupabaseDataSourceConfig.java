package com.cardsense.api.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Supabase PostgreSQL — clients / api_calls / daily_usage only.
 * Promotion data stays on the existing SQLite DataSource (untouched).
 *
 * Inject with: @Qualifier("supabaseJdbcTemplate")
 */
@Configuration
public class SupabaseDataSourceConfig {

    @Value("${cardsense.supabase.url}")
    private String url;

    @Value("${cardsense.supabase.username:postgres}")
    private String username;

    @Value("${cardsense.supabase.password}")
    private String password;

    @Bean(name = "supabaseDataSource")
    public DataSource supabaseDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);
        config.setPoolName("supabase-pool");
        // Supabase pooler / PgBouncer transaction pooling is incompatible with
        // PostgreSQL JDBC server-side prepared statements. Disable them to avoid
        // "prepared statement \"S_n\" already exists" errors in production.
        config.addDataSourceProperty("prepareThreshold", "0");
        config.addDataSourceProperty("preparedStatementCacheQueries", "0");
        config.addDataSourceProperty("preferQueryMode", "simple");
        return new HikariDataSource(config);
    }

    @Bean(name = "supabaseJdbcTemplate")
    public JdbcTemplate supabaseJdbcTemplate() {
        return new JdbcTemplate(supabaseDataSource());
    }
}
