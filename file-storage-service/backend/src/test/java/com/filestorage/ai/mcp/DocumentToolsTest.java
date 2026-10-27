package com.filestorage.ai.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filestorage.ai.retrieval.DocumentAccessService;
import com.filestorage.ai.retrieval.DocumentSummary;
import com.filestorage.ai.retrieval.DocumentText;
import com.filestorage.ai.retrieval.RetrievalService;
import com.filestorage.ai.retrieval.SearchHit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentToolsTest {

    @Mock
    private RetrievalService retrievalService;

    @Mock
    private DocumentAccessService documentAccessService;

    private DocumentTools tools;

    @BeforeEach
    void setUp() {
        tools = new DocumentTools(retrievalService, documentAccessService);
    }

    @Test
    void search_returnsCitedExcerpts() {
        when(retrievalService.search("oom", 3)).thenReturn(List.of(
                new SearchHit(1L, "guide.pdf", 4, 2, 0.8, "Check for duplicate processes.")));

        DocumentTools.SearchResult result = tools.searchMyDocuments("oom", 3);

        assertThat(result.message()).isNull();
        assertThat(result.excerpts()).containsExactly(
                new DocumentTools.Excerpt(1L, "guide.pdf", 4, 2, 0.8, "Check for duplicate processes."));
    }

    @Test
    void search_saysNothingFound_insteadOfReturningAnEmptyList() {
        when(retrievalService.search("travel", null)).thenReturn(List.of());

        DocumentTools.SearchResult result = tools.searchMyDocuments("travel", null);

        assertThat(result.message()).isEqualTo(DocumentTools.NOTHING_FOUND);
        assertThat(result.excerpts()).isEmpty();
    }

    @Test
    void list_delegatesToOwnerScopedService() {
        List<DocumentSummary> docs = List.of(new DocumentSummary(1L, "a.md", "MD", "INDEXED", 3, null));
        when(documentAccessService.listMyDocuments()).thenReturn(docs);

        assertThat(tools.listMyDocuments()).isEqualTo(docs);
        verify(documentAccessService).listMyDocuments();
    }

    @Test
    void getDocumentText_returnsText_orTheSameNotFoundForMissingAndForbidden() {
        DocumentText text = new DocumentText(1L, "a.md", 1, 1, false, "hello");
        when(documentAccessService.getDocumentText(1L)).thenReturn(Optional.of(text));
        when(documentAccessService.getDocumentText(2L)).thenReturn(Optional.empty()); // someone else's
        when(documentAccessService.getDocumentText(3L)).thenReturn(Optional.empty()); // doesn't exist

        assertThat(tools.getDocumentText(1L)).isEqualTo(text);
        assertThat(tools.getDocumentText(2L)).isEqualTo(DocumentTools.NOT_FOUND);
        assertThat(tools.getDocumentText(3L)).isEqualTo(tools.getDocumentText(2L));
    }
}
