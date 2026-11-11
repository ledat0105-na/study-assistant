CREATE TABLE IF NOT EXISTS password_reset_challenges (
    user_id BIGINT NOT NULL PRIMARY KEY,
    digest VARCHAR(100) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    next_send_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    window_start DATETIME(6) NOT NULL,
    sent_in_window INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
