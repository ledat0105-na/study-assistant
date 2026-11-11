package com.example.studyassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StudyAssistantApplication {

    public static void main(String[] args) {

//        String rawPassword = "123456";
//        String hashedPassword = com.example.studyassistant.util.HashUtil.hash(rawPassword);
//        System.out.println("BCrypt: " + hashedPassword);
//        if (true) return;

        SpringApplication.run(StudyAssistantApplication.class, args);
    }

}
