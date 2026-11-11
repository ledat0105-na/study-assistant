package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotebookPlanQuotaTest {
    @ParameterizedTest
    @ValueSource(strings={"PRO","STUDENT","PREMIUM","BASIC"})
    void paidPlanAliasesAllowMoreThanFiveNotebooks(String plan) {
        var users=mock(UserRepository.class);var notebooks=mock(NotebookRepository.class);
        var user=new User();user.setId(7L);user.setPlan(plan);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(notebooks.countByUserIdAndIsDeletedFalse(7L)).thenReturn(5L);
        when(notebooks.save(any(Notebook.class))).thenAnswer(i->i.getArgument(0));
        var service=new UserService();ReflectionTestUtils.setField(service,"userRepository",users);
        ReflectionTestUtils.setField(service,"notebookRepository",notebooks);
        var input=new Notebook();input.setTitle("Notebook số 6");
        assertThat(service.createNotebook(7L,input).getTitle()).isEqualTo("Notebook số 6");
    }
    @Test void freePlanStillRejectsSixthNotebook() {
        var users=mock(UserRepository.class);var notebooks=mock(NotebookRepository.class);
        var user=new User();user.setId(7L);user.setPlan("FREE");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(notebooks.countByUserIdAndIsDeletedFalse(7L)).thenReturn(5L);
        var service=new UserService();ReflectionTestUtils.setField(service,"userRepository",users);
        ReflectionTestUtils.setField(service,"notebookRepository",notebooks);
        var input=new Notebook();input.setTitle("Notebook số 6");
        assertThatThrownBy(()->service.createNotebook(7L,input)).isInstanceOf(IllegalStateException.class);
        verify(notebooks,never()).save(any());
    }
}
