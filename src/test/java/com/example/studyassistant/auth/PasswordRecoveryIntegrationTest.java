package com.example.studyassistant.auth;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.service.*;
import com.example.studyassistant.security.PasswordPolicy;
import com.example.studyassistant.util.HashUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"billing.enforcement.enabled=false","spring.datasource.url=jdbc:h2:mem:otp-policy;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class PasswordRecoveryIntegrationTest {
 static class MutableClock extends Clock {
  volatile Instant now=Instant.parse("2026-11-04T02:00:00Z");
  public ZoneId getZone(){return ZoneOffset.UTC;}
  public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
  public Instant instant(){return now;}
 }
 @TestConfiguration static class Config { @Bean @Primary MutableClock otpTestClock(){return new MutableClock();} }
 @MockitoBean PasswordResetMailer mailer;
 @Autowired PasswordResetService resets;
 @Autowired UserService accounts;
 @Autowired UserRepository users;
 @Autowired PasswordResetChallengeRepository challenges;
 @Autowired MutableClock clock;
 @Autowired WebApplicationContext context;
 MockMvc mvc;User user;
 @BeforeEach void setup(){
  clock.now=Instant.parse("2026-11-04T02:00:00Z");
  User input=new User();input.setUsername("otp_"+UUID.randomUUID());input.setEmail(input.getUsername()+"@test.invalid");input.setPassword("OldPassword123!");user=accounts.register(input);
  mvc=MockMvcBuilders.webAppContextSetup(context).build();
 }
 String issue(){resets.request(user.getEmail());var cap=ArgumentCaptor.forClass(String.class);verify(mailer,atLeastOnce()).send(eq(user.getEmail()),cap.capture());return cap.getValue();}
 @ParameterizedTest @ValueSource(strings={"Short1!","alllowercase1!","ALLUPPERCASE1!","NoDigitsHere!","NoSpecial123","        ","Abc1!"})
 void weakPasswordsRejected(String value){
  assertThatThrownBy(()->PasswordPolicy.validate(value)).isInstanceOf(IllegalArgumentException.class);
  User input=new User();input.setUsername("weak_"+UUID.randomUUID());input.setEmail(input.getUsername()+"@test.invalid");input.setPassword(value);
  assertThatThrownBy(()->accounts.register(input)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void bcryptByteLimitAndUnicodePolicy(){
  PasswordPolicy.validate("MậtKhẩu123!");
  assertThatThrownBy(()->PasswordPolicy.validate("Ab1!"+"á".repeat(35))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->PasswordPolicy.validate(null)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void loginAcceptsEmailIgnoringCaseAndWhitespace(){assertThat(accounts.login("  "+user.getEmail().toUpperCase(Locale.ROOT)+"  ","OldPassword123!").getId()).isEqualTo(user.getId());}
 @Test void codeHasSixDigitsAndIsHashed(){String code=issue();assertThat(code).matches("[0-9]{6}");var row=challenges.findById(user.getId()).orElseThrow();assertThat(row.getDigest()).isNotEqualTo(code);assertThat(HashUtil.verify(code,row.getDigest())).isTrue();assertThat(row.getExpiresAt()).isEqualTo(clock.instant().plusSeconds(120));}
 @Test void resendCooldownAndPreviousCodeInvalidation(){
  String first=issue();String digest=challenges.findById(user.getId()).orElseThrow().getDigest();resets.request(user.getEmail());verify(mailer,times(1)).send(anyString(),anyString());
  assertThat(challenges.findById(user.getId()).orElseThrow().getDigest()).isEqualTo(digest);
  clock.now=clock.now.plusSeconds(61);issue();assertThat(challenges.findById(user.getId()).orElseThrow().getDigest()).isNotEqualTo(digest);
 }
 @Test void resetsOnceAndOldPasswordStopsWorking(){
  String code=issue();
  assertThatThrownBy(()->resets.reset(user.getEmail(),code,"OldPassword123!"))
    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("khác mật khẩu hiện tại");
  resets.reset(user.getEmail(),code,"NewPassword456!");
  assertThat(accounts.login(user.getEmail(),"NewPassword456!").getId()).isEqualTo(user.getId());
  assertThatThrownBy(()->accounts.login(user.getEmail(),"OldPassword123!")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->resets.reset(user.getEmail(),code,"AnotherPass789!")).isInstanceOf(IllegalArgumentException.class);
  assertThat(users.findById(user.getId()).orElseThrow().getResetToken()).isNull();
 }
 @Test void expiredCodeIsRejected(){String code=issue();clock.now=clock.now.plusSeconds(120);assertThatThrownBy(()->resets.reset(user.getEmail(),code,"NewPassword456!")).isInstanceOf(IllegalArgumentException.class);}
 @Test void fiveWrongAttemptsPersistAndInvalidateCode(){
  String code=issue();String wrong=code.equals("000000")?"111111":"000000";
  for(int i=0;i<5;i++)assertThatThrownBy(()->resets.reset(user.getEmail(),wrong,"NewPassword456!")).isInstanceOf(IllegalArgumentException.class);
  assertThat(challenges.findById(user.getId()).orElseThrow().getAttempts()).isEqualTo(5);
  assertThatThrownBy(()->resets.reset(user.getEmail(),code,"NewPassword456!")).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void weakResetDoesNotConsumeCode(){String code=issue();assertThatThrownBy(()->resets.reset(user.getEmail(),code,"weak")).isInstanceOf(IllegalArgumentException.class);resets.reset(user.getEmail(),code,"NewPassword456!");}
 @Test void hourlySendCap(){for(int i=0;i<6;i++){resets.request(user.getEmail());clock.now=clock.now.plusSeconds(61);}verify(mailer,times(5)).send(anyString(),anyString());clock.now=clock.now.plusSeconds(3600);resets.request(user.getEmail());verify(mailer,times(6)).send(anyString(),anyString());}
 @Test void unknownEmailDoesNotSendAndHasSameApiResponse()throws Exception{
  String ip=UUID.randomUUID().toString();
  var unknown=mvc.perform(post("/api/auth/forgot-password").with(r->{r.setRemoteAddr(ip);return r;}).contentType("application/json").content("{\"email\":\"missing@test.invalid\"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.otp").doesNotExist()).andExpect(jsonPath("$.token").doesNotExist()).andReturn().getResponse().getContentAsString();
  var known=mvc.perform(post("/api/auth/forgot-password").with(r->{r.setRemoteAddr(ip);return r;}).contentType("application/json").content("{\"email\":\""+user.getEmail()+"\"}"))
    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertThat(known).isEqualTo(unknown);verify(mailer,times(1)).send(eq(user.getEmail()),anyString());
 }
 @Test void invalidEmailAndMalformedOtpRejected()throws Exception{
  mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{\"email\":\"invalid\"}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/auth/reset-password").contentType("application/json").content("{\"email\":\"test@test.invalid\",\"otp\":\"abc\",\"password\":\"NewPassword456!\"}")).andExpect(status().isBadRequest());
  verifyNoInteractions(mailer);
 }
 @Test void rateLimitUsesSocketIp(){String ip=UUID.randomUUID().toString();for(int i=0;i<20;i++)resets.limit(ip);assertThatThrownBy(()->resets.limit(ip)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);clock.now=clock.now.plusSeconds(60);resets.limit(ip);}
 @Test void concurrentCodeConsumptionAllowsOnlyOne()throws Exception{
  String code=issue();ExecutorService executor=Executors.newFixedThreadPool(2);
  try{Callable<Boolean> task=()->{try{resets.reset(user.getEmail(),code,"NewPassword456!");return true;}catch(IllegalArgumentException ex){return false;}};var results=executor.invokeAll(List.of(task,task));int success=0;for(var result:results)if(result.get())success++;assertThat(success).isEqualTo(1);}finally{executor.shutdownNow();}
 }
 @Test void mailFailureRollsBackNewChallenge(){doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Mail unavailable")).when(mailer).send(anyString(),anyString());assertThatThrownBy(()->resets.request(user.getEmail())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);assertThat(challenges.findById(user.getId())).isEmpty();}
}
