package com.example.studyassistant.billing;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="billing.enforcement.enabled",havingValue="true",matchIfMissing=true)
public class BillingPolicyInitializer implements CommandLineRunner {
 private final JdbcTemplate jdbc;
 private final TransactionTemplate transactions;
 private final java.time.Clock clock;
 @Override public void run(String... args) {
  jdbc.execute("CREATE TABLE IF NOT EXISTS billing_policy_versions (name VARCHAR(64) PRIMARY KEY, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
  transactions.executeWithoutResult(status->{
   if(jdbc.update("INSERT IGNORE INTO billing_policy_versions(name) VALUES ('free-pro-vip-v1')")==0)return;
   jdbc.update("UPDATE payments SET duration_months=1 WHERE duration_months IS NULL OR duration_months NOT IN (1,3,12)");
   // Old public PRO cost 99k/month and represented the premium tier; preserve it as VIP once.
   for(String table:new String[]{"users","subscriptions","payments"})
    jdbc.update("UPDATE "+table+" SET plan=CASE WHEN plan IN ('PRO','PREMIUM') THEN 'VIP' WHEN plan IN ('BASIC','STUDENT') THEN 'PRO' ELSE plan END");
   java.time.LocalDate period=java.time.LocalDate.ofInstant(clock.instant(),java.time.ZoneId.of("Asia/Ho_Chi_Minh")).withDayOfMonth(1);
   var counts=jdbc.queryForList("SELECT user_id,COUNT(*) AS count FROM documents WHERE created_at>=? AND created_at<? GROUP BY user_id",java.sql.Timestamp.valueOf(period.atStartOfDay()),java.sql.Timestamp.valueOf(period.plusMonths(1).atStartOfDay()));
   for(var row:counts)jdbc.update("INSERT IGNORE INTO monthly_feature_usage(user_id,period_start,feature,used) VALUES (?,?,?,?)",row.get("user_id"),java.sql.Date.valueOf(period),"UPLOAD",row.get("count"));
   jdbc.update("UPDATE pricing_plans SET is_active=false WHERE plan_code IN ('STUDENT','BASIC','PREMIUM')");
   for(var plan:BillingController.catalog()) {
    Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM pricing_plans WHERE plan_code=?",Integer.class,plan.getPlanCode());
    if(count==0)jdbc.update("INSERT INTO pricing_plans(plan_code,name,original_price,sale_price,billing_cycle,is_popular,is_active,badge_text,description,features,document_limit) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
      plan.getPlanCode(),plan.getName(),plan.getOriginalPrice(),plan.getSalePrice(),"tháng",plan.getIsPopular(),true,plan.getBadgeText(),plan.getDescription(),plan.getFeatures(),plan.getDocumentLimit());
    else jdbc.update("UPDATE pricing_plans SET name=?,original_price=?,sale_price=?,billing_cycle='tháng',is_popular=?,is_active=true,badge_text=?,description=?,features=?,document_limit=? WHERE plan_code=?",
      plan.getName(),plan.getOriginalPrice(),plan.getSalePrice(),plan.getIsPopular(),plan.getBadgeText(),plan.getDescription(),plan.getFeatures(),plan.getDocumentLimit(),plan.getPlanCode());
   }
  });
 }
}
