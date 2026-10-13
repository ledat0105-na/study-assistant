package com.example.studyassistant.retrieval.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersionPair {
    private Long documentId;
    private String indexVersion;
}
