package com.example.studyassistant.service;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;

final class KnowledgeGraphDuplicate {
    private KnowledgeGraphDuplicate() {}
    static boolean isDuplicate(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) return true;
            if (cause instanceof SQLException sql) {
                Set<SQLException> sqlSeen = Collections.newSetFromMap(new IdentityHashMap<>());
                for (SQLException next = sql; next != null && sqlSeen.add(next); next = next.getNextException()) {
                    // 23000 alone includes NOT NULL and foreign-key violations. Require MySQL's
                    // duplicate key vendor code, or the specific unique-violation SQLState (H2).
                    if ((next.getErrorCode() == 1062 && "23000".equals(next.getSQLState()))
                            || "23505".equals(next.getSQLState())) return true;
                }
            }
        }
        return false;
    }
}
