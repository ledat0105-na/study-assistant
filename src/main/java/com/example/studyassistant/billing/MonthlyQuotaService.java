package com.example.studyassistant.billing;
import com.example.studyassistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class MonthlyQuotaService {
 private final UserRepository users;
 private final MonthlyUsageRepository usages;
 private final Clock clock;
 public record Reservation(Long userId,LocalDate period,String feature) {}
 public static class Exhausted extends IllegalStateException {
  public final String plan,feature;
  public Exhausted(String plan,String feature) {
   super((feature.equals("STORAGE")?"Dung lượng lưu trữ đã đầy":feature.equals("NOTEBOOK")?"Bạn đã đạt giới hạn notebook":feature.equals("CONVERSATION")?"Bạn đã đạt giới hạn cuộc trò chuyện":"Bạn đã dùng hết hạn mức tháng")+" của gói "+plan+". Vui lòng nâng cấp "+PlanPolicy.upgrade(plan)+".");this.plan=plan;this.feature=feature;
  }
  public Map<String,Object> payload(){return Map.of("code","QUOTA_EXCEEDED","message",getMessage(),"plan",plan,"feature",feature,"upgradePlan",PlanPolicy.upgrade(plan),"upgradeUrl","/payment.html?plan="+PlanPolicy.upgrade(plan));}

 }
 private LocalDate period() {return LocalDate.ofInstant(clock.instant(),ZoneId.of("Asia/Ho_Chi_Minh")).withDayOfMonth(1);}
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public Reservation reserve(Long id,String feature) {
  var user=users.findByIdForUpdate(id).orElseThrow();
  String plan=PlanPolicy.normalize(user.getPlan());int limit=PlanPolicy.limit(plan,feature);
  LocalDate period=period();
  var usage=usages.findByUserIdAndPeriodStartAndFeature(id,period,feature).orElseGet(()->{
   var value=new MonthlyUsage();value.setUserId(id);value.setPeriodStart(period);value.setFeature(feature);return value;
  });
  if (limit>=0 && usage.getUsed()>=limit) throw new Exhausted(plan,feature);
  usage.setUsed(Math.addExact(usage.getUsed(),1));usages.save(usage);
  return new Reservation(id,period,feature);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void refund(Reservation reservation) {
  users.findByIdForUpdate(reservation.userId()).orElseThrow();
  usages.refundOne(reservation.userId(),reservation.period(),reservation.feature());
 }
 @Transactional(readOnly=true)
 public Map<String,Object> status(Long id) {
  var user=users.findById(id).orElseThrow();String plan=PlanPolicy.normalize(user.getPlan());
  Map<String,Long> used=new HashMap<>();usages.findByUserIdAndPeriodStart(id,period()).forEach(u->used.put(u.getFeature(),u.getUsed()));
  Map<String,Object> features=new LinkedHashMap<>();
  for(String key:List.of("UPLOAD","CHAT","QUIZ","FLASHCARD","SUMMARY","MIND_MAP")) {
   int limit=PlanPolicy.limit(plan,key);long count=used.getOrDefault(key,0L);
   features.put(key,Map.of("used",count,"limit",limit,"remaining",limit<0 ? -1 : Math.max(0,limit-count)));
  }
  return Map.of("currentPlan",plan,"periodStart",period().toString(),"resetsAt",period().plusMonths(1).toString(),"features",features);
 }
}
