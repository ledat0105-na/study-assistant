package com.example.studyassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasteryPageDto<T> {
    private List<T> items;
    private int page;
    private int size;
    private boolean hasNext;
}
