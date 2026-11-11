package com.example.studyassistant.service;

import com.example.studyassistant.entity.PasswordResetChallenge;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.security.PasswordPolicy;
import com.example.studyassistant.util.HashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class PasswordResetService {
    private final UserRepository users;
    private final PasswordResetChallengeRepository challenges;
    private final PasswordResetMailer mailer;
    private final Clock clock;
    private final SecureRandom random=new SecureRandom();
    private final Map<String,Window> requests=new HashMap<>();
    private record Window(Instant start,int count) {}
    public static final int OTP_TTL_SECONDS = 120;
    public static final String INVALID="Mã OTP không đúng, đã hết hạn hoặc đã dùng. Vui lòng yêu cầu mã mới.";
    // Use the real socket address; never trust a client-supplied forwarding header.
    public synchronized void limit(String ip) {
        Instant now=clock.instant();
        requests.entrySet().removeIf(e->!e.getValue().start().plusSeconds(60).isAfter(now));
        Window old=requests.get(ip);
        if((old!=null && old.count()>=20) || (old==null && requests.size()>=10000))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Bạn thao tác quá nhiều. Vui lòng thử lại sau 1 phút.");
        requests.put(ip,new Window(old==null?now:old.start(),old==null?1:old.count()+1));
    }
    @Transactional
    public void request(String email) {
        mailer.requireConfigured();
        var found=users.findByEmailIgnoreCase(email.trim());
        if(found.isEmpty())return;
        var user=users.findByIdForUpdate(found.get().getId()).orElseThrow();
        Instant now=clock.instant();
        var challenge=challenges.findById(user.getId()).orElse(null);
        if(challenge!=null && challenge.getNextSendAt().isAfter(now))return;
        if(challenge==null) { challenge=new PasswordResetChallenge();challenge.setUserId(user.getId());challenge.setWindowStart(now); }
        if(!challenge.getWindowStart().plusSeconds(3600).isAfter(now)) {challenge.setWindowStart(now);challenge.setSentInWindow(0);}
        if(challenge.getSentInWindow()>=5)return;
        String otp=String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));
        challenge.setDigest(HashUtil.hash(otp));challenge.setExpiresAt(now.plusSeconds(OTP_TTL_SECONDS));
        challenge.setNextSendAt(now.plusSeconds(60));challenge.setAttempts(0);
        challenge.setSentInWindow(challenge.getSentInWindow()+1);
        challenges.save(challenge);
        mailer.send(user.getEmail(),otp);
    }
    @Transactional(noRollbackFor=IllegalArgumentException.class)
    public void reset(String email,String otp,String password) {
        PasswordPolicy.validate(password);
        var found=users.findByEmailIgnoreCase(email.trim());
        if(found.isEmpty())throw new IllegalArgumentException(INVALID);
        var user=users.findByIdForUpdate(found.get().getId()).orElseThrow();
        var challenge=challenges.findById(user.getId()).orElse(null);
        if(challenge==null || !challenge.getExpiresAt().isAfter(clock.instant()) || challenge.getAttempts()>=5)
            throw new IllegalArgumentException(INVALID);
        if(!HashUtil.verify(otp,challenge.getDigest())) {
            challenge.setAttempts(challenge.getAttempts()+1);challenges.save(challenge);
            throw new IllegalArgumentException(INVALID);
        }
        if (HashUtil.verify(password, user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        user.setPassword(HashUtil.hash(password)); user.setResetToken(null);user.setResetTokenExpiry(null);
        user.setFailedLoginAttempts(0);user.setLockUntil(null);
        users.save(user);
        // Keep send throttling even after consuming the code.
        challenge.setDigest("");challenge.setExpiresAt(clock.instant());challenges.save(challenge);
    }
}
