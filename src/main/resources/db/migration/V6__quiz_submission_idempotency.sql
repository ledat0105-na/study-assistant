ALTER TABLE quiz_results
ADD COLUMN submission_token VARCHAR(36) NULL,
ADD COLUMN submission_hash CHAR(64) NULL,
ADD CONSTRAINT uk_quiz_result_token UNIQUE (user_id, quiz_id, submission_token);
