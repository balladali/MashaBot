package ru.balladali.mashabot.memory;

import org.flywaydb.core.Flyway;

/** One-shot migration process. Never starts Spring, Telegram or the LLM client. */
public final class MemoryMigrationMain {
    public static void main(String[] args) throws java.sql.SQLException {
        var ds = new org.postgresql.ds.PGSimpleDataSource();
        ds.setURL(required("MEMORY_DB_URL"));
        ds.setUser(required("MEMORY_DB_USER"));
        ds.setPassword(required("MEMORY_DB_PASSWORD"));
        ds.setConnectTimeout(3);
        ds.setSocketTimeout(30);
        Flyway.configure().dataSource(ds).load().migrate();
        String runtimePassword = required("MEMORY_APP_DB_PASSWORD");
        try (var c = ds.getConnection();
                var q =
                        c.prepareStatement(
                                "SELECT format('ALTER ROLE masha_app LOGIN PASSWORD %L', ?)")) {
            q.setString(1, runtimePassword);
            try (var rows = q.executeQuery()) {
                rows.next();
                try (var change = c.createStatement()) {
                    change.execute(rows.getString(1));
                }
            }
        }
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }
}
