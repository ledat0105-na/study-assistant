package com.example.studyassistant.billing;
import com.example.studyassistant.entity.PricingPlan;
import com.example.studyassistant.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
@RestController @RequestMapping("/api/billing") @RequiredArgsConstructor
public class BillingController {
 @org.springframework.beans.factory.annotation.Value("${billing.bank.name:}") private String bankName;
 @org.springframework.beans.factory.annotation.Value("${billing.bank.account:}") private String bankAccount;
 @org.springframework.beans.factory.annotation.Value("${billing.bank.holder:}") private String bankHolder;
 @GetMapping("/payment-details") public Map<String,Object> paymentDetails(){return Map.of("configured",!bankName.isBlank()&&!bankAccount.isBlank()&&!bankHolder.isBlank(),"bankName",bankName,"account",bankAccount,"holder",bankHolder);}
 private final MonthlyQuotaService quotas;
 private final SubscriptionRepository subscriptions;
 public static List<PricingPlan> catalog() {
  List<PricingPlan> plans=new ArrayList<>();
  for(String code:List.of("FREE","PRO","VIP")) {
   PricingPlan p=new PricingPlan();p.setPlanCode(code);p.setName("Gói "+code);p.setOriginalPrice(code.equals("FREE")?0d:code.equals("PRO")?50000d:100000d);p.setSalePrice(p.getOriginalPrice());p.setBillingCycle("tháng");p.setIsActive(true);p.setIsPopular(code.equals("PRO"));p.setBadgeText(code);p.setDocumentLimit(PlanPolicy.limit(code,"UPLOAD"));
   p.setDescription(code.equals("VIP")?"Không giới hạn quota sử dụng trong thời hạn gói":"Hạn mức làm mới mỗi tháng");
   p.setFeatures(code.equals("VIP")?"Tài liệu, chat, quiz và tạo flashcard không giới hạn|Đủ tính năng AI|Giữ giới hạn kỹ thuật của từng tệp":code.equals("FREE")?"3 tài liệu/tháng|20 chat/tháng|3 quiz/tháng|3 lượt tạo flashcard/tháng|Lưu trữ 100MB":"50 tài liệu/tháng|500 chat/tháng|100 quiz/tháng|100 lượt tạo flashcard/tháng|Lưu trữ 2GB");plans.add(p);
  }return plans;
 }
 @GetMapping("/quote") public Map<String,Object> quote(@RequestParam String plan,@RequestParam(defaultValue="1") int months){long total=PlanPolicy.price(plan,months);return Map.of("plan",PlanPolicy.normalize(plan),"months",months,"total",total,"monthlyPrice",total/months,"discountPerMonth",months==3?10000:months==12?20000:0);}
 @GetMapping("/status") public ResponseEntity<?> status(HttpServletRequest request) {
  var session=request.getSession(false);if(session==null||!(session.getAttribute("userId") instanceof Long id))return ResponseEntity.status(401).build();
  Map<String,Object> result=new LinkedHashMap<>(quotas.status(id));
  subscriptions.findByUserId(id).ifPresent(sub->{result.put("expiresAt",sub.getEndDate()==null?null:sub.getEndDate().atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime().toString());result.put("subscriptionStatus",sub.getStatus());});
  return ResponseEntity.ok(result);
 }
 @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));}
}
