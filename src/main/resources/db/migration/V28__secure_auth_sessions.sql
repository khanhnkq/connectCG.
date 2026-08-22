ALTER TABLE `users`
    ADD COLUMN `auth_version` INT NOT NULL DEFAULT 0;

ALTER TABLE `refresh_tokens`
    ADD COLUMN `family_id` VARCHAR(36) NULL,
    ADD COLUMN `replaced_by_hash` VARCHAR(64) NULL,
    ADD COLUMN `revoked_at` DATETIME NULL,
    ADD COLUMN `revoked_reason` VARCHAR(64) NULL;

UPDATE `refresh_tokens`
SET `family_id` = UUID()
WHERE `family_id` IS NULL;

ALTER TABLE `refresh_tokens`
    MODIFY COLUMN `family_id` VARCHAR(36) NOT NULL;

CREATE INDEX `idx_refresh_tokens_family_revoked`
    ON `refresh_tokens` (`family_id`, `is_revoked`);

CREATE INDEX `idx_refresh_tokens_user_revoked`
    ON `refresh_tokens` (`user_id`, `is_revoked`);
