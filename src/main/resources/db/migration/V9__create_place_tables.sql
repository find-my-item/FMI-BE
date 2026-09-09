CREATE TABLE IF NOT EXISTS `place` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `address` varchar(255) NOT NULL,
  `latitude` double NOT NULL,
  `longitude` double NOT NULL,
  `station` varchar(100) NOT NULL,
  `station_distance_meters` int unsigned NOT NULL,
  `type` varchar(20) NOT NULL,
  `thumbnail_url` varchar(500) NOT NULL,
  `entity_status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `place_operation_period` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `place_id` bigint NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `entity_status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_place_operation_period_place` (`place_id`),
  CONSTRAINT `fk_place_operation_period_place` FOREIGN KEY (`place_id`) REFERENCES `place` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `place_business_hour` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `place_id` bigint NOT NULL,
  `day_of_week` varchar(10) NOT NULL,
  `is_closed` bit(1) NOT NULL,
  `type` varchar(20) NOT NULL,
  `start_time` time NOT NULL,
  `end_time` time NOT NULL,
  `entity_status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_place_business_hour_range` (`place_id`, `day_of_week`, `type`, `start_time`, `end_time`),
  CONSTRAINT `fk_place_business_hour_place` FOREIGN KEY (`place_id`) REFERENCES `place` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `place_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `place_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `is_favorite` bit(1) NOT NULL,
  `entity_status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_place_favorite_user_place` (`user_id`, `place_id`),
  CONSTRAINT `fk_place_favorite_place` FOREIGN KEY (`place_id`) REFERENCES `place` (`id`),
  CONSTRAINT `fk_place_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
