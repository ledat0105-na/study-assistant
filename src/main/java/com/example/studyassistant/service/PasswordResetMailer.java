package com.example.studyassistant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class PasswordResetMailer {
    private final JavaMailSender sender;
    private final String username;
    private final String password;
    public PasswordResetMailer(org.springframework.beans.factory.ObjectProvider<JavaMailSender> sender,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password) {
        this.sender=sender.getIfAvailable(); this.username=username; this.password=password;
    }
    public void requireConfigured() {
        if(sender==null || username.isBlank() || password.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Chức năng gửi email chưa được cấu hình. Vui lòng liên hệ quản trị viên.");
    }
    public void send(String email,String otp) {
        requireConfigured();
        var message=new SimpleMailMessage();
        message.setFrom(username); message.setTo(email);
        message.setSubject("Study Assistant — Mã OTP đặt lại mật khẩu");
        message.setText("Mã OTP đặt lại mật khẩu của bạn là: "+otp+
            "\nMã có hiệu lực 2 phút và chỉ dùng một lần. Không chia sẻ mã này với bất kỳ ai."+
            "\nNếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.");
        try { sender.send(message); }
        catch(org.springframework.mail.MailException ex) {
            // Do not log provider exceptions: they may contain addresses or credentials.
            org.slf4j.LoggerFactory.getLogger(getClass()).error("Password reset email delivery failed");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Chưa thể gửi email. Vui lòng thử lại sau.");
        }
    }
}
