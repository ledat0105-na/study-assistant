package com.example.studyassistant.billing;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"billing.enforcement.enabled=true","spring.datasource.url=jdbc:h2:mem:billing-policy;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "presentation.media.storage-root=build/billing-test-media"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class BillingIntegrationTest {
 static class MutableClock extends Clock {
  volatile Instant now=Instant.parse("2026-01-31T16:59:00Z");
  public ZoneId getZone(){return ZoneId.of("Asia/Ho_Chi_Minh");}
  public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
  public Instant instant(){return now;}
 }
 @TestConfiguration static class Config {
  @Bean @Primary MutableClock billingTestClock(){return new MutableClock();}
 }
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.studyassistant.service.AiService aiService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.studyassistant.retrieval.service.GroundedAnswerService groundedAnswers;
 @Autowired NotebookRepository notebooks;
 @Autowired DocumentRepository documents;
 @Autowired MutableClock clock;
 @Autowired UserRepository users;
 @Autowired SubscriptionRepository subscriptions;
 @Autowired MonthlyUsageRepository usages;
 @Autowired MonthlyQuotaService quota;
 @Autowired SubscriptionService subscriptionService;
 @Autowired BillingPolicyInitializer initializer;
 @Autowired WebApplicationContext context;
 @Autowired PaymentRepository payments;
 MockMvc mvc;User user;
 @BeforeEach void setup(){
  clock.now=Instant.parse("2026-01-31T16:59:00Z");
  user=new User();user.setUsername("billing_"+UUID.randomUUID());user.setEmail(user.getUsername()+"@test.invalid");user.setPassword("unused");user=users.saveAndFlush(user);
  mvc=MockMvcBuilders.webAppContextSetup(context).build();
 }
 Subscription paid(String plan,LocalDateTime end){
  Subscription sub=new Subscription();sub.setUser(user);sub.setPlan(plan);sub.setStatus("ACTIVE");sub.setEndDate(end);return subscriptions.saveAndFlush(sub);
 }
 long used(String feature){return usages.findByUserIdAndPeriodStartAndFeature(user.getId(),LocalDate.of(2026,1,1),feature).map(MonthlyUsage::getUsed).orElse(0L);}
 @Test void exactPricesAndInvalidTerms()throws Exception{
  long[][] rows={{1,50000,100000},{3,120000,270000},{12,360000,960000}};
  for(long[] row:rows){assertThat(PlanPolicy.price("PRO",(int)row[0])).isEqualTo(row[1]);assertThat(PlanPolicy.price("VIP",(int)row[0])).isEqualTo(row[2]);}
  mvc.perform(get("/api/billing/quote").param("plan","PRO").param("months","2")).andExpect(status().isBadRequest());
 }
 @Test void newRegistrationCannotChooseVipOrCreateLoginSession()throws Exception{
  var registration = mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"username\":\"new_"+UUID.randomUUID()+"\",\"email\":\""+UUID.randomUUID()+"@test.invalid\",\"password\":\"Password123!\",\"plan\":\"VIP\",\"role\":\"ADMIN\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.user.plan").value("FREE")).andExpect(jsonPath("$.user.role").value("USER")).andReturn();
  assertThat(registration.getRequest().getSession(false)).isNull();
  mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
 }
 @Test void freeQuotaBlocksDirectApiAtExactLimit()throws Exception{
  for(int i=0;i<20;i++)quota.reserve(user.getId(),"CHAT");
  mvc.perform(post("/api/ai/chat").sessionAttr("userId",user.getId()).contentType("application/json").content("{\"message\":\"test\"}"))
   .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.upgradePlan").value("PRO"));
  assertThat(used("CHAT")).isEqualTo(20);org.mockito.Mockito.verifyNoInteractions(aiService);
 }
 @Test void matrixParametersCannotBypassChatQuota()throws Exception{
  for(int i=0;i<20;i++)quota.reserve(user.getId(),"CHAT");
  mvc.perform(post("/api/ai/chat;mode=legacy").sessionAttr("userId",user.getId()).contentType("application/json").content("{\"message\":\"test\"}"))
   .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"));
  org.mockito.Mockito.verifyNoInteractions(aiService);
 }
 @Test void variableRoutesCannotBypassGenerationQuota()throws Exception{
  for(int i=0;i<3;i++)quota.reserve(user.getId(),"FLASHCARD");
  mvc.perform(post("/api/documents/123/flashcards/generate;mode=legacy").sessionAttr("userId",user.getId()).contentType("application/json").content("{}"))
   .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.upgradePlan").value("PRO"));
 }
 @Test void failedHttpRequestRefundsQuota()throws Exception{
  mvc.perform(post("/api/ai/chat").sessionAttr("userId",user.getId()).contentType("application/json").content("{}"))
   .andExpect(status().isBadRequest());assertThat(used("CHAT")).isZero();
 }
 @Test void failedConcurrentHttpCallDoesNotRefundAnotherSuccessfulCall()throws Exception{
  var notebook=new Notebook();notebook.setUser(user);notebook.setTitle("Quota race");notebook=notebooks.saveAndFlush(notebook);
  var document=new Document();document.setUser(user);document.setNotebook(notebook);document.setFileName("quota.docx");document.setFilePath("quota.docx");document.setStatus("READY");document.setActiveIndexVersion("v1");document=documents.saveAndFlush(document);
  var started=new CountDownLatch(1);var release=new CountDownLatch(1);
  org.mockito.Mockito.when(groundedAnswers.answer(org.mockito.ArgumentMatchers.eq(user.getId()),org.mockito.ArgumentMatchers.eq(notebook.getId()),org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation->{
   if("fail".equals(invocation.getArgument(2))){started.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Test timeout");throw new IllegalArgumentException("Synthetic failure");}
   var answer=org.mockito.Mockito.mock(com.example.studyassistant.retrieval.model.GroundedAnswer.class);
   org.mockito.Mockito.when(answer.getAnswer()).thenReturn("ok");org.mockito.Mockito.when(answer.getCitations()).thenReturn(java.util.List.of());return answer;
  });
  var filter=new org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter(){
   @Override protected jakarta.persistence.EntityManagerFactory lookupEntityManagerFactory(){return context.getBean(jakarta.persistence.EntityManagerFactory.class);}
  };
  var http=MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
  Long docId=document.getId();var pool=Executors.newSingleThreadExecutor();
  try{
   var failed=pool.submit(()->http.perform(post("/api/ai/chat").sessionAttr("userId",user.getId()).contentType("application/json").content("{\"message\":\"fail\",\"docIds\":["+docId+"]}")).andReturn().getResponse().getStatus());
   if(!started.await(10,TimeUnit.SECONDS)){if(failed.isDone())failed.get();throw new AssertionError("Request did not reach the stub provider");}
   http.perform(post("/api/ai/chat").sessionAttr("userId",user.getId()).contentType("application/json").content("{\"message\":\"ok\",\"docIds\":["+docId+"]}")).andExpect(status().isOk());
   assertThat(used("CHAT")).isEqualTo(2);release.countDown();assertThat(failed.get(10,TimeUnit.SECONDS)).isEqualTo(400);assertThat(used("CHAT")).isEqualTo(1);
  }finally{release.countDown();pool.shutdownNow();}
 }
 @Test void monthRollsOverInVietnamAndRefundUsesOriginalMonth(){
  var reservation=quota.reserve(user.getId(),"QUIZ");clock.now=Instant.parse("2026-01-31T17:01:00Z");
  assertThat(quota.reserve(user.getId(),"QUIZ").period()).isEqualTo(LocalDate.of(2026,2,1));
  quota.refund(reservation);assertThat(used("QUIZ")).isZero();
 }
 @Test void upgradePreservesUsedCountAndRaisesCap(){
  for(int i=0;i<3;i++)quota.reserve(user.getId(),"UPLOAD");
  assertThatThrownBy(()->quota.reserve(user.getId(),"UPLOAD")).isInstanceOf(MonthlyQuotaService.Exhausted.class);
  paid("PRO",LocalDateTime.of(2026,3,1,0,0));subscriptionService.refresh(user.getId());
  for(int i=3;i<50;i++)quota.reserve(user.getId(),"UPLOAD");
  assertThatThrownBy(()->quota.reserve(user.getId(),"UPLOAD")).isInstanceOf(MonthlyQuotaService.Exhausted.class);
  assertThat(used("UPLOAD")).isEqualTo(50);
 }
 @Test void expiredVipBecomesFreeBeforeApiUse()throws Exception{
  paid("VIP",LocalDateTime.of(2026,1,31,23,58));
  mvc.perform(get("/api/auth/me").sessionAttr("userId",user.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("FREE"));
  assertThat(subscriptions.findByUserId(user.getId()).orElseThrow().getStatus()).isEqualTo("EXPIRED");
 }
 @Test void vipHasUnlimitedUsage(){
  paid("VIP",LocalDateTime.of(2027,1,1,0,0));subscriptionService.refresh(user.getId());
  for(int i=0;i<105;i++)quota.reserve(user.getId(),"QUIZ");
  assertThat(used("QUIZ")).isEqualTo(105);assertThat(PlanPolicy.limit("VIP","UPLOAD")).isEqualTo(-1);
 }
 @Test void twoConcurrentCallsCannotTakeLastSlotTwice()throws Exception{
  quota.reserve(user.getId(),"QUIZ");quota.reserve(user.getId(),"QUIZ");
  var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
  try{
   Callable<Boolean> call=()->{gate.await();try{quota.reserve(user.getId(),"QUIZ");return true;}catch(MonthlyQuotaService.Exhausted e){return false;}};
   var first=pool.submit(call);var second=pool.submit(call);gate.countDown();
   assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);assertThat(used("QUIZ")).isEqualTo(3);
  }finally{pool.shutdownNow();}
 }
 @Test void checkoutUsesServerPriceAndTermAndAdminApprovalSetsExpiry()throws Exception{
  var response=mvc.perform(post("/api/payments/checkout").sessionAttr("userId",user.getId()).contentType("application/json")
   .content("{\"planId\":\"PRO\",\"months\":\"3\",\"amount\":\"1\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(120000)).andExpect(jsonPath("$.months").value(3)).andReturn();
  Long id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("transactionId").asLong();
  assertThat(payments.findById(id).orElseThrow().getDurationMonths()).isEqualTo(3);
  var admin=users.findByUsername("admin").orElseThrow();
  var expected=LocalDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Ho_Chi_Minh")).plusMonths(3);
  mvc.perform(post("/api/payments/"+id+"/approve").sessionAttr("userId",admin.getId())).andExpect(status().isOk());
  assertThat(subscriptions.findByUserId(user.getId()).orElseThrow().getEndDate()).isEqualTo(expected);
 }
 @Test void proQuotaRecommendsVip()throws Exception{
  paid("PRO",LocalDateTime.of(2027,1,1,0,0));subscriptionService.refresh(user.getId());
  for(int i=0;i<100;i++)quota.reserve(user.getId(),"QUIZ");
  mvc.perform(post("/api/quizzes/generate").sessionAttr("userId",user.getId()).contentType("application/json").content("{}"))
   .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.upgradePlan").value("VIP"));
 }
 @Test void renewingSamePlanExtendsExistingExpiry()throws Exception{
  LocalDateTime end=LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusMonths(6).withNano(0);paid("PRO",end);
  var response=mvc.perform(post("/api/payments/checkout").sessionAttr("userId",user.getId()).contentType("application/json").content("{\"planId\":\"PRO\",\"months\":\"3\"}"))
   .andExpect(status().isOk()).andReturn();
  Long id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("transactionId").asLong();
  mvc.perform(post("/api/payments/"+id+"/approve").sessionAttr("userId",users.findByUsername("admin").orElseThrow().getId())).andExpect(status().isOk());
  assertThat(subscriptions.findByUserId(user.getId()).orElseThrow().getEndDate()).isEqualTo(end.plusMonths(3));
 }
 @Test void migrationIsNotRepeatedForNewProUsers(){
  user.setPlan("PRO");users.saveAndFlush(user);initializer.run();initializer.run();
  assertThat(users.findById(user.getId()).orElseThrow().getPlan()).isEqualTo("PRO");
 }
 @Test void allChatRoutesAndGenerationRoutesAreMetered(){
  assertThat(BillingInterceptor.feature("/api/chat/conversations/12/messages","POST")).isEqualTo("CHAT");
  assertThat(BillingInterceptor.feature("/api/tutor/conversations/12/messages","POST")).isEqualTo("CHAT");
  assertThat(BillingInterceptor.feature("/api/documents/12/flashcards/generate","POST")).isEqualTo("FLASHCARD");
  assertThat(BillingInterceptor.feature("/api/documents/12/summary/generate","POST")).isEqualTo("SUMMARY");
  assertThat(BillingInterceptor.feature("/api/flashcards","POST")).isEqualTo("FLASHCARD");
  assertThat(BillingInterceptor.feature("/api/quizzes","POST")).isEqualTo("QUIZ");
 }
}
