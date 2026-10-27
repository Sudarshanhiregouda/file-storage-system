package com.filestorage.ai.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filestorage.entity.FileEntity;
import com.filestorage.event.FileArchivedEvent;
import com.filestorage.event.FilePurgedEvent;
import com.filestorage.event.FileUploadedEvent;
import com.filestorage.repository.FileRepository;
import com.filestorage.service.FileStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.FileSystemResource;

@ExtendWith(MockitoExtension.class)
class DocumentIndexerTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileStorageService fileStorageService;

    @TempDir
    Path tempDir;

    private DocumentIndexer indexer() {
        return new DocumentIndexer(vectorStore, fileRepository, fileStorageService);
    }

    private FileEntity file(Long id, String name, String type) {
        FileEntity f = new FileEntity();
        f.setId(id);
        f.setFileName(name);
        f.setFileType(type);
        f.setOwnerName("alice");
        f.setStoragePath("stored-" + name);
        f.setIsDeleted(false);
        return f;
    }

    @Test
    @SuppressWarnings("unchecked")
    void upload_indexesChunksWithOwnerMetadata_andMarksIndexed() throws IOException {
        FileEntity file = file(1L, "runbook.md", "MD");
        Path content = tempDir.resolve("runbook.md");
        Files.writeString(content, "# Runbook\n\nRestart the adapter when the connection pool is exhausted.\n".repeat(20));
        when(fileRepository.findById(1L)).thenReturn(Optional.of(file));
        when(fileStorageService.loadAsResource("stored-runbook.md")).thenReturn(new FileSystemResource(content));

        indexer().onUploaded(new FileUploadedEvent(1L));

        ArgumentCaptor<List<Document>> added = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(added.capture());
        List<Document> chunks = added.getValue();
        assertThat(chunks).isNotEmpty();
        Document first = chunks.get(0);
        assertThat(first.getMetadata())
                .containsEntry(ChunkMetadata.FILE_ID, 1L)
                .containsEntry(ChunkMetadata.OWNER_NAME, "alice")
                .containsEntry(ChunkMetadata.FILE_NAME, "runbook.md")
                .containsEntry(ChunkMetadata.CHUNK_INDEX, 0)
                .doesNotContainKey(ChunkMetadata.PAGE);
        // old chunks are removed before the new ones are added
        verify(vectorStore).delete(any(Filter.Expression.class));
        verify(fileRepository).updateIndexStatus(eq(1L), eq("INDEXED"), eq(chunks.size()), isNull());
    }

    @Test
    void unsupportedType_marksUnsupported_andNeverEmbeds() {
        when(fileRepository.findById(2L)).thenReturn(Optional.of(file(2L, "photo.png", "PNG")));

        indexer().onUploaded(new FileUploadedEvent(2L));

        verify(vectorStore, never()).add(anyList());
        verify(fileRepository).updateIndexStatus(2L, "UNSUPPORTED", 0, null);
    }

    @Test
    void readFailure_marksFailedWithShortError() throws IOException {
        when(fileRepository.findById(3L)).thenReturn(Optional.of(file(3L, "notes.txt", "TXT")));
        when(fileStorageService.loadAsResource(anyString())).thenThrow(new IOException("Stored file is missing"));

        indexer().onUploaded(new FileUploadedEvent(3L));

        verify(vectorStore, never()).add(anyList());
        verify(fileRepository).updateIndexStatus(eq(3L), eq("FAILED"), isNull(), startsWith("IOException"));
    }

    @Test
    void archivedFile_isNotIndexed() {
        FileEntity archived = file(4L, "old.md", "MD");
        archived.setIsDeleted(true);
        when(fileRepository.findById(4L)).thenReturn(Optional.of(archived));

        indexer().onUploaded(new FileUploadedEvent(4L));

        verify(vectorStore, never()).add(anyList());
        verify(fileRepository, never()).updateIndexStatus(any(), anyString(), anyInt(), any());
    }

    @Test
    void archive_deletesChunksForThatFileOnly() {
        indexer().onArchived(new FileArchivedEvent(5L));

        Filter.Expression expected = new FilterExpressionBuilder().eq(ChunkMetadata.FILE_ID, 5L).build();
        verify(vectorStore).delete(expected);
    }

    @Test
    void purge_deletesChunksForAllPurgedFiles() {
        indexer().onPurged(new FilePurgedEvent(List.of(6L, 7L)));

        Filter.Expression expected = new FilterExpressionBuilder()
                .in(ChunkMetadata.FILE_ID, List.<Object>of(6L, 7L)).build();
        verify(vectorStore).delete(expected);
    }
}
