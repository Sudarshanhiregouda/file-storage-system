package com.filestorage.ai.retrieval;

import com.filestorage.ai.indexing.DocumentParser;
import com.filestorage.entity.FileEntity;
import com.filestorage.entity.IndexStatus;
import com.filestorage.repository.FileRepository;
import com.filestorage.security.AuthUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Owner-scoped document listing and full-text access for the MCP tools.
 * Identity comes only from the security context. Missing, archived, unindexed and not-owned files
 * are indistinguishable to the caller: all return {@link Optional#empty()}.
 */
@Service
public class DocumentAccessService {

    static final int MAX_LISTED = 200;
    static final int MAX_CHUNKS_RETURNED = 40;

    private final FileRepository fileRepository;
    private final DocumentParser parser;

    public DocumentAccessService(FileRepository fileRepository, DocumentParser parser) {
        this.fileRepository = fileRepository;
        this.parser = parser;
    }

    public List<DocumentSummary> listMyDocuments() {
        List<FileEntity> files = AuthUtils.isAdmin()
                ? fileRepository.findByIsDeletedFalseOrderByCreatedAtDesc()
                : fileRepository.findByIsDeletedFalseAndOwnerNameOrderByCreatedAtDesc(requireUsername());
        return files.stream()
                .limit(MAX_LISTED)
                .map(f -> new DocumentSummary(f.getId(), f.getFileName(), f.getFileType(),
                        f.getIndexStatus() == null ? null : f.getIndexStatus().name(), f.getChunkCount(),
                        f.getCreatedAt()))
                .toList();
    }

    /**
     * Text of an indexed file, chunk by chunk in order. The file is re-parsed with the same parser the
     * indexer used, so the chunks match the index without depending on similarity search.
     */
    public Optional<DocumentText> getDocumentText(Long fileId) {
        if (fileId == null) {
            return Optional.empty();
        }
        boolean admin = AuthUtils.isAdmin();
        String username = admin ? null : requireUsername();

        Optional<FileEntity> found = fileRepository.findByIdAndIsDeletedFalse(fileId)
                // exact, case-sensitive match: the same rule the vector filter uses
                .filter(f -> admin || username.equals(f.getOwnerName()))
                .filter(f -> f.getIndexStatus() == IndexStatus.INDEXED);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        FileEntity file = found.get();

        List<DocumentParser.Chunk> chunks;
        try {
            chunks = parser.parse(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Stored content could not be read", e);
        }
        List<DocumentParser.Chunk> shown = chunks.stream().limit(MAX_CHUNKS_RETURNED).toList();
        String text = shown.stream()
                .map(c -> c.page() != null ? "[page " + c.page() + "]\n" + c.text() : c.text())
                .collect(Collectors.joining("\n\n"));
        return Optional.of(new DocumentText(file.getId(), file.getFileName(), shown.size(), chunks.size(),
                chunks.size() > shown.size(), text));
    }

    private static String requireUsername() {
        String username = AuthUtils.currentUsername();
        if (username == null || username.isBlank()) {
            throw new AccessDeniedException("Authentication required");
        }
        return username;
    }
}
