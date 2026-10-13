package com.example.studyassistant.retrieval.repository;

import com.example.studyassistant.retrieval.model.DocumentVersionPair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DocumentChunkSearchRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public static class KeywordSearchResult {
        public Long chunkId;
        public Long documentId;
        public String indexVersion;
        public Double score;
    }

    private static final RowMapper<KeywordSearchResult> ROW_MAPPER = new RowMapper<KeywordSearchResult>() {
        @Override
        public KeywordSearchResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            KeywordSearchResult result = new KeywordSearchResult();
            result.chunkId = rs.getLong("id");
            result.documentId = rs.getLong("document_id");
            result.indexVersion = rs.getString("index_version");
            result.score = rs.getDouble("score");
            return result;
        }
    };

    /**
     * Executes a secure FULLTEXT query restricted by specific document/version pairs.
     */
    public List<KeywordSearchResult> searchByKeyword(String query, List<DocumentVersionPair> authorizedPairs, int limit) {
        if (authorizedPairs == null || authorizedPairs.isEmpty()) {
            return new ArrayList<>();
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, document_id, index_version, MATCH(content) AGAINST(? IN NATURAL LANGUAGE MODE) as score ");
        sql.append("FROM document_chunks ");
        sql.append("WHERE MATCH(content) AGAINST(? IN NATURAL LANGUAGE MODE) ");

        // Build ownership filter
        sql.append("AND (");
        
        List<Object> args = new ArrayList<>();
        args.add(query); // for score
        args.add(query); // for where clause

        for (int i = 0; i < authorizedPairs.size(); i++) {
            DocumentVersionPair pair = authorizedPairs.get(i);
            sql.append("(document_id = ? AND index_version = ?)");
            args.add(pair.getDocumentId());
            args.add(pair.getIndexVersion());
            
            if (i < authorizedPairs.size() - 1) {
                sql.append(" OR ");
            }
        }
        
        sql.append(") ");
        sql.append("ORDER BY score DESC ");
        sql.append("LIMIT ?");
        args.add(limit);

        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }
}
