package com.example.studyassistant.billing;
import jakarta.servlet.http.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import java.util.*;

@Component @RequiredArgsConstructor
public class BillingInterceptor implements HandlerInterceptor {
 private final SubscriptionService subscriptions;
 private final MonthlyQuotaService quotas;
 @Value("${billing.enforcement.enabled:true}") private boolean enabled;
 private static final String KEY=BillingInterceptor.class.getName();
 public static String feature(String path,String method) {
  if(!"POST".equals(method))return null;
  if(path.equals("/api/documents/upload"))return "UPLOAD";
  if(path.equals("/api/ai/chat")||path.matches("/api/(?:chat|tutor)/conversations/\\d+/messages"))return "CHAT";
  if(path.equals("/api/quizzes/generate")||path.equals("/api/quizzes"))return "QUIZ";
  if(path.equals("/api/flashcards"))return "FLASHCARD";
  if(path.matches("/api/documents/\\d+/flashcards/generate"))return "FLASHCARD";
  if(path.matches("/api/documents/\\d+/summary/generate"))return "SUMMARY";
  if(path.matches("/api/documents/\\d+/mind-map/generate"))return "MIND_MAP";
  return null;
 }
 @Override public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object handler)throws Exception {
  if(!enabled||!(handler instanceof HandlerMethod))return true;
  var session=req.getSession(false);Object id=session==null?null:session.getAttribute("userId");
  if(!(id instanceof Long userId))return true;
  subscriptions.refresh(userId);
  String matched=java.util.Objects.toString(req.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE),req.getRequestURI().substring(req.getContextPath().length()));
  String canonical=matched.replaceAll("\\{[^}]+\\}","0");
  String feature=feature(canonical,req.getMethod());
  if(feature==null||req.getAttribute(KEY)!=null)return true;
  try {req.setAttribute(KEY,quotas.reserve(userId,feature));return true;}
  catch(MonthlyQuotaService.Exhausted ex) {
   res.setStatus(429);res.setContentType("application/json");res.setCharacterEncoding("UTF-8");
   res.getWriter().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(ex.payload()));return false;
  }
 }
 @Override public void afterCompletion(HttpServletRequest req,HttpServletResponse res,Object handler,Exception ex) {
  if(req.getAttribute(KEY) instanceof MonthlyQuotaService.Reservation reservation && (ex!=null||res.getStatus()>=400)){req.removeAttribute(KEY);quotas.refund(reservation);}
 }
}
