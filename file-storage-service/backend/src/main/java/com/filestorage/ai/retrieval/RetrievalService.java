package com.filestorage.ai.retrieval;

import com.filestorage.ai.config.AiProperties;
import com.filestorage.ai.indexing.ChunkMetadata;
import com.filestorage.security.AuthUtils;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Permission-aware semantic search over indexed chunks.
 * <p>
 * The caller's identity comes only from the Spring Security context, never from a parameter.
 * Non-admins are restricted with an {@code ownerName == currentUser} filter that is part of the
 * vector query itself, so top-K is computed over the caller's chunks only.
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final VectorStore vectorStore;
    private final AiProperties properties;

    public RetrievalService(VectorStore vectorStore, AiProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public List<SearchHit> search(String query, Integer topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        SearchRequest.Builder request = SearchRequest.builder()
                .query(query)
                .topK(effectiveTopK(topK))
                .similarityThreshold(properties.similarityThreshold());

        Filter.Expression ownerFilter = ownerFilterForCurrentUser();
        if (ownerFilter != null) {
            request.filterExpression(ownerFilter);
        }

        long start = System.currentTimeMillis();
        List<SearchHit> hits = vectorStore.similaritySearch(request.build()).stream()
                .map(RetrievalService::toHit)
                .toList();
        log.debug("Retrieved {} hits in {} ms (admin={})", hits.size(), System.currentTimeMillis() - start,
                ownerFilter == null);
        return hits;
    }

    /**
     * Admins search everything (null filter). Everyone else gets an exact owner filter built with
     * FilterExpressionBuilder: the username is a value in the expression, never spliced into a string.
     */
    Filter.Expression ownerFilterForCurrentUser() {
        if (AuthUtils.isAdmin()) {
            return null;
        }
        String username = AuthUtils.currentUsername();
        if (username == null || username.isBlank()) {
            // Fail closed: no identity means no retrieval, never "no filter".
            throw new AccessDeniedException("Authentication required");
        }
        return new FilterExpressionBuilder().eq(ChunkMetadata.OWNER_NAME, username).build();
    }

    int effectiveTopK(Integer requested) {
        int k = requested == null ? properties.defaultTopK() : requested;
        return Math.max(1, Math.min(k, properties.maxTopK()));
    }

    private static SearchHit toHit(Document doc) {
        Map<String, Object> m = doc.getMetadata();
        return new SearchHit(
                toLong(m.get(ChunkMetadata.FILE_ID)),
                (String) m.get(ChunkMetadata.FILE_NAME),
                toInteger(m.get(ChunkMetadata.PAGE)),
                toInteger(m.get(ChunkMetadata.CHUNK_INDEX)),
                doc.getScore() == null ? 0.0 : doc.getScore(),
                doc.getText());
    }

    // Metadata comes back from JSON, so numbers may be Integer, Long or Double.
    static Long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : value == null ? null : Long.valueOf(value.toString());
    }

    static Integer toInteger(Object value) {
        return value instanceof Number n ? n.intValue() : value == null ? null : Integer.valueOf(value.toString());
    }
}
