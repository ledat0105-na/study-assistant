-- Apply once before deploying the new billing policy; legacy PRO meant the old premium tier.
ALTER TABLE payments ADD COLUMN duration_months INT NOT NULL DEFAULT 1;
CREATE TABLE monthly_feature_usage (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL, period_start DATE NOT NULL, feature VARCHAR(24) NOT NULL, used BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_monthly_feature_usage UNIQUE(user_id,period_start,feature),
 CONSTRAINT fk_monthly_usage_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT ck_monthly_usage_nonnegative CHECK(used >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
UPDATE users SET plan=CASE WHEN plan IN ('PRO','PREMIUM') THEN 'VIP' WHEN plan IN ('BASIC','STUDENT') THEN 'PRO' ELSE plan END;
UPDATE subscriptions SET plan=CASE WHEN plan IN ('PRO','PREMIUM') THEN 'VIP' WHEN plan IN ('BASIC','STUDENT') THEN 'PRO' ELSE plan END;
UPDATE payments SET plan=CASE WHEN plan IN ('PRO','PREMIUM') THEN 'VIP' WHEN plan IN ('BASIC','STUDENT') THEN 'PRO' ELSE plan END;
-- Rebuild catalog rows explicitly; GET /api/plans never rewrites administrator data.
UPDATE pricing_plans SET is_active=false WHERE plan_code IN ('BASIC','STUDENT','PREMIUM');

INSERT INTO pricing_plans(plan_code,name,original_price,sale_price,billing_cycle,is_popular,is_active,badge_text,description,features,document_limit)
VALUES ('FREE','Gói FREE',0,0,'tháng',false,true,'FREE','Hạn mức tháng','3 tài liệu/tháng|20 chat/tháng|3 quiz/tháng|3 lượt tạo flashcard/tháng',3),
('PRO','Gói PRO',50000,50000,'tháng',true,true,'PRO','Hạn mức tháng','50 tài liệu/tháng|500 chat/tháng|100 quiz/tháng|100 lượt tạo flashcard/tháng',50),
('VIP','Gói VIP',100000,100000,'tháng',false,true,'VIP','Không giới hạn quota sử dụng','Không giới hạn quota sử dụng',-1)
ON DUPLICATE KEY UPDATE name=VALUES(name),original_price=VALUES(original_price),sale_price=VALUES(sale_price),billing_cycle=VALUES(billing_cycle),is_active=true,badge_text=VALUES(badge_text),features=VALUES(features),document_limit=VALUES(document_limit);

CREATE TABLE IF NOT EXISTS billing_policy_versions (name VARCHAR(64) PRIMARY KEY, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
INSERT INTO billing_policy_versions(name) VALUES ('free-pro-vip-v1');
