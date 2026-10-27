package com.filestorage.ai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filestorage.ai.config.AiProperties;
import com.filestorage.ai.indexing.ChunkMetadata;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @Mock
    private VectorStore vectorStore;

    private final AiProperties properties = new AiProperties(5, 10, 0.5, 1000);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String username, boolean admin) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority(admin ? "ROLE_ADMIN" : "ROLE_USER"))));
    }

    private SearchRequest capturedRequest() {
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        return captor.getValue();
    }

    @Test
    void nonAdmin_alwaysGetsOwnerFilter_andTopKIsClamped() {
        loginAs("alice", false);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        new RetrievalService(vectorStore, properties).search("question", 500);

        SearchRequest request = capturedRequest();
        assertThat(request.getTopK()).isEqualTo(10);
        assertThat(request.getSimilarityThreshold()).isEqualTo(0.5);
        assertThat(request.getFilterExpression())
                .isEqualTo(new FilterExpressionBuilder().eq(ChunkMetadata.OWNER_NAME, "alice").build());
    }

    @Test
    void admin_hasNoOwnerFilter_andDefaultTopK() {
        loginAs("admin", true);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        new RetrievalService(vectorStore, properties).search("question", null);

        SearchRequest request = capturedRequest();
        assertThat(request.getTopK()).isEqualTo(5);
        assertThat(request.getFilterExpression()).isNull();
    }

    @Test
    void noAuthentication_failsClosed() {
        assertThatThrownBy(() -> new RetrievalService(vectorStore, properties).search("question", 3))
                .isInstanceOf(AccessDeniedException.class);
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void mapsMetadataToHits() {
        loginAs("alice", false);
        Document doc = Document.builder().text("chunk text").score(0.83)
                .metadata(Map.of(ChunkMetadata.FILE_ID, 7, ChunkMetadata.FILE_NAME, "guide.pdf",
                        ChunkMetadata.PAGE, 3, ChunkMetadata.CHUNK_INDEX, 4))
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<SearchHit> hits = new RetrievalService(vectorStore, properties).search("question", 3);

        assertThat(hits).containsExactly(new SearchHit(7L, "guide.pdf", 3, 4, 0.83, "chunk text"));
    }
}
