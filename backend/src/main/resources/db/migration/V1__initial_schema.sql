-- MySQL 8.0.16+ enforces CHECK constraints. No blank-slot seed rows.
CREATE TABLE category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_category_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE life_goal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    slot_no INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    category_id BIGINT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    reason LONGTEXT NULL,
    cover_attachment_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_life_goal_slot UNIQUE (slot_no),
    CONSTRAINT ck_life_goal_slot CHECK (slot_no BETWEEN 1 AND 1000),
    CONSTRAINT ck_life_goal_title CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT ck_life_goal_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT fk_goal_category FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE goal_check_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    goal_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_check_item_completed CHECK (completed IN (0, 1)),
    CONSTRAINT fk_check_item_goal FOREIGN KEY (goal_id) REFERENCES life_goal(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE goal_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    goal_id BIGINT NOT NULL,
    content LONGTEXT NOT NULL,
    record_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_record_goal FOREIGN KEY (goal_id) REFERENCES life_goal(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE goal_completion (
    id BIGINT NOT NULL AUTO_INCREMENT,
    goal_id BIGINT NOT NULL,
    completed_date DATE NOT NULL,
    completion_note LONGTEXT NULL,
    rating TINYINT NULL,
    status_before_completion VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_completion_goal UNIQUE (goal_id),
    CONSTRAINT ck_completion_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    CONSTRAINT ck_previous_status CHECK (status_before_completion IN ('NOT_STARTED', 'IN_PROGRESS')),
    CONSTRAINT fk_completion_goal FOREIGN KEY (goal_id) REFERENCES life_goal(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE goal_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    goal_id BIGINT NOT NULL,
    record_id BIGINT NULL,
    stage VARCHAR(20) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1024) NOT NULL,
    mime_type VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    is_image BOOLEAN NOT NULL DEFAULT FALSE,
    allow_home_background BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_attachment_stage CHECK (stage IN ('PROCESS', 'COMPLETION', 'GENERAL')),
    CONSTRAINT ck_attachment_size CHECK (file_size >= 0),
    CONSTRAINT ck_attachment_image CHECK (is_image IN (0, 1)),
    CONSTRAINT ck_attachment_home CHECK (allow_home_background IN (0, 1)),
    CONSTRAINT fk_attachment_goal FOREIGN KEY (goal_id) REFERENCES life_goal(id) ON DELETE CASCADE,
    CONSTRAINT fk_attachment_record FOREIGN KEY (record_id) REFERENCES goal_record(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE life_goal ADD CONSTRAINT fk_goal_cover
    FOREIGN KEY (cover_attachment_id) REFERENCES goal_attachment(id) ON DELETE SET NULL;

CREATE TABLE quote (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content LONGTEXT NOT NULL,
    source VARCHAR(1000) NULL,
    include_home BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_quote_home CHECK (include_home IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE app_setting (
    id BIGINT NOT NULL AUTO_INCREMENT,
    setting_key VARCHAR(100) NOT NULL,
    setting_value LONGTEXT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_setting_key UNIQUE (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
