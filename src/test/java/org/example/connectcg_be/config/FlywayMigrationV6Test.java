package org.example.connectcg_be.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationV6Test {

    @Test
    @DisplayName("V6 migration script must exist on classpath and define required performance indexes")
    void testV6MigrationScriptContainsExpectedIndexes() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/postgresql/V6__performance_and_lookup_indexes.sql");
        assertTrue(resource.exists(), "V6__performance_and_lookup_indexes.sql must exist on classpath");

        String sqlContent;
        try (InputStream is = resource.getInputStream()) {
            sqlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertFalse(sqlContent.isBlank(), "Migration script must not be empty");

        List<String> requiredIndexes = List.of(
                "idx_reports_created",
                "idx_reports_status_created",
                "idx_reports_target_status_created",
                "idx_posts_author_status_created",
                "idx_group_members_user_status",
                "idx_friends_friend_user"
        );

        for (String indexName : requiredIndexes) {
            assertTrue(
                    sqlContent.contains(indexName),
                    "V6 migration must define index: " + indexName
            );
            assertTrue(
                    sqlContent.toUpperCase().contains("CREATE INDEX IF NOT EXISTS " + indexName.toUpperCase()),
                    "V6 migration must use safe 'CREATE INDEX IF NOT EXISTS' syntax for: " + indexName
            );
        }
    }
}
