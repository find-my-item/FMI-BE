CREATE INDEX `idx_place_status_created`
    ON `place` (`entity_status`, `created_at` DESC, `id` DESC);

CREATE INDEX `idx_place_status_type_created`
    ON `place` (`entity_status`, `type`, `created_at` DESC, `id` DESC);

CREATE INDEX `idx_place_status_type_location`
    ON `place` (`entity_status`, `type`, `latitude`, `longitude`);

CREATE INDEX `idx_place_operation_period_end_date`
    ON `place_operation_period` (`end_date`, `place_id`);

CREATE INDEX `idx_place_favorite_user_active_updated`
    ON `place_favorite` (`user_id`, `is_favorite`, `updated_at` DESC, `id` DESC);

ALTER TABLE `post`
    ADD INDEX `idx_post_visibility_location` (`is_deleted`, `temporary_save`, `latitude`, `longitude`),
    ADD INDEX `idx_post_visibility_type_location` (
        `is_deleted`, `temporary_save`, `post_type`, `latitude`, `longitude`
    );
