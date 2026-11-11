package com.example.studyassistant.billing;
import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service @RequiredArgsConstructor
public class SubscriptionService {
 private final UserRepository users;
 private final SubscriptionRepository subscriptions;
 private final Clock clock;
 @Transactional
 public User refresh(Long userId) {
  User user=users.findByIdForUpdate(userId).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED));
  if ("ADMIN".equals(user.getRole())) { user.setPlan("FREE"); return user; }
  var subscription=subscriptions.findByUserId(userId);
  LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Ho_Chi_Minh"));
  if (subscription.isPresent()) {
   var sub=subscription.get();
   if ("ACTIVE".equals(sub.getStatus()) && sub.getEndDate()!=null && sub.getEndDate().isAfter(now)) user.setPlan(PlanPolicy.normalize(sub.getPlan()));
   else { if ("ACTIVE".equals(sub.getStatus())) sub.setStatus("EXPIRED");user.setPlan("FREE"); }
  } else user.setPlan("FREE");
  return user;
 }
}
