package com.example.studyassistant.controller;

import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dashboard-orphans;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "billing.enforcement.enabled=false",
        "presentation.media.storage-root=build/dashboard-orphans-media"
})
class ProgressDashboardIntegrationTest {
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WebApplicationContext context;

    @Test
    void dashboardCountsValidProgressWhenTopicOrItsDocumentWasDeleted() throws Exception {
        User user = new User();
        String username = "dashboard_" + UUID.randomUUID();
        user.setUsername(username);
        user.setEmail(username + "@example.invalid");
        user.setPassword("unused");
        Long userId = users.saveAndFlush(user).getId();

        jdbc.update("""
                insert into documents (id, user_id, file_name, file_path, status)
                values (70001, ?, 'demo.pdf', 'demo.pdf', 'READY')
                """, userId);
        jdbc.update("insert into topics (id, name, document_id) values (70002, 'Valid topic', 70001)");
        jdbc.update("insert into study_progress (user_id, topic_id, status) values (?, 70002, 'COMPLETED')", userId);
        jdbc.update("""
                insert into documents (id, user_id, file_name, file_path, status)
                values (70004, ?, 'deleted.pdf', 'deleted.pdf', 'DELETED')
                """, userId);
        jdbc.update("insert into topics (id, name, document_id) values (70005, 'Deleted document topic', 70004)");
        jdbc.update("insert into study_progress (user_id, topic_id, status) values (?, 70005, 'LEARNING')", userId);
        jdbc.execute("set referential_integrity false");
        try {
            jdbc.update("insert into study_progress (user_id, topic_id, status) values (?, 70003, 'LEARNING')", userId);
        } finally {
            jdbc.execute("set referential_integrity true");
        }

        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(get("/api/progress/dashboard").sessionAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").value(1))
                .andExpect(jsonPath("$.completedTopics").value(1))
                .andExpect(jsonPath("$.learningTopics").value(0));
        mvc.perform(get("/api/progress").sessionAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
