package com.filestorage.ai.indexing;

import com.filestorage.entity.FileEntity;
import com.filestorage.entity.IndexStatus;
import com.filestorage.event.FileArchivedEvent;
import com.filestorage.event.FileOwnerChangedEvent;
import com.filestorage.event.FilePurgedEvent;
import com.filestorage.event.FileRestoredEvent;
import com.filestorage.event.FileUploadedEvent;
import com.filestorage.repository.FileRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps the vector index in step with the file lifecycle.
 * <p>
 * Listens to file-domain events only after the database transaction commits, so a rolled-back
 * upload is never indexed, and does the slow work (parse, split, embed) on the bounded indexing pool.
 * The relational {@code files} table is the source of truth; the vector index is a derived view.
 * <p>
 * {@code NOT_SUPPORTED} matters when a listener runs on the publishing thread (synchronous executor,
 * or CallerRunsPolicy under load): the committed transaction's resources are still bound there, and
 * without suspending them our writes would join a transaction that never commits again.
 */
@Component
public class DocumentIndexer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexer.class);

    static final int BATCH_SIZE = 50;
    private static final int MAX_ERROR_LENGTH = 500;

    private final VectorStore vectorStore;
    private final FileRepository fileRepository;
    private final DocumentParser parser;

    public DocumentIndexer(VectorStore vectorStore, FileRepository fileRepository, DocumentParser parser) {
        this.vectorStore = vectorStore;
        this.fileRepository = fileRepository;
        this.parser = parser;
    }

    // ---- event listeners --------------------------------------------------

    @Async(AsyncConfig.INDEXING_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void onUploaded(FileUploadedEvent event) {
        index(event.fileId());
    }

    @Async(AsyncConfig.INDEXING_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void onRestored(FileRestoredEvent event) {
        index(event.fileId());
    }

    @Async(AsyncConfig.INDEXING_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void onOwnerChanged(FileOwnerChangedEvent event) {
        // ownerName is baked into chunk metadata, so the chunks are rebuilt for the new owner.
        index(event.fileId());
    }

    @Async(AsyncConfig.INDEXING_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void onArchived(FileArchivedEvent event) {
        deleteChunks(List.of(event.fileId()));
        // Not in the index any more; restore will index it again.
        fileRepository.updateIndexStatus(event.fileId(), IndexStatus.PENDING.name(), null, null);
    }

    @Async(AsyncConfig.INDEXING_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void onPurged(FilePurgedEvent event) {
        deleteChunks(event.fileIds());
    }

    // ---- indexing ---------------------------------------------------------

    void index(Long fileId) {
        Optional<FileEntity> found = fileRepository.findById(fileId);
        if (found.isEmpty() || Boolean.TRUE.equals(found.get().getIsDeleted())) {
            log.debug("Skipping indexing for file {}: missing or archived", fileId);
            return;
        }
        FileEntity file = found.get();

        if (!parser.isSupported(file)) {
            deleteChunks(List.of(fileId));
            fileRepository.updateIndexStatus(fileId, IndexStatus.UNSUPPORTED.name(), 0, null);
            log.info("File {} not indexed: unsupported type {}", fileId, file.getFileType());
            return;
        }

        try {
            long start = System.currentTimeMillis();
            List<Document> chunks = parser.parse(file).stream().map(c -> toDocument(file, c)).toList();

            // Replace, never append: re-indexing must not leave old chunks (e.g. with the old owner).
            deleteChunks(List.of(fileId));
            for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
                vectorStore.add(chunks.subList(i, Math.min(i + BATCH_SIZE, chunks.size())));
            }

            // If the file was archived while we were embedding, undo our work.
            boolean archivedMeanwhile = fileRepository.findById(fileId)
                    .map(f -> Boolean.TRUE.equals(f.getIsDeleted()))
                    .orElse(true);
            if (archivedMeanwhile) {
                deleteChunks(List.of(fileId));
                log.info("File {} was archived during indexing; chunks removed", fileId);
                return;
            }

            fileRepository.updateIndexStatus(fileId, IndexStatus.INDEXED.name(), chunks.size(), null);
            log.info("Indexed file {} ({}) into {} chunks in {} ms",
                    fileId, file.getFileType(), chunks.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            String error = shortError(e);
            fileRepository.updateIndexStatus(fileId, IndexStatus.FAILED.name(), null, error);
            log.warn("Indexing failed for file {}: {}", fileId, error);
        }
    }

    /** A chunk carries only our metadata keys (reader metadata such as source paths is dropped). */
    private static Document toDocument(FileEntity file, DocumentParser.Chunk chunk) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ChunkMetadata.FILE_ID, file.getId());
        metadata.put(ChunkMetadata.OWNER_NAME, file.getOwnerName());
        metadata.put(ChunkMetadata.FILE_NAME, file.getFileName());
        metadata.put(ChunkMetadata.CHUNK_INDEX, chunk.index());
        if (chunk.page() != null) {
            metadata.put(ChunkMetadata.PAGE, chunk.page());
        }
        return Document.builder().text(chunk.text()).metadata(metadata).build();
    }

    void deleteChunks(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression filter = fileIds.size() == 1
                ? b.eq(ChunkMetadata.FILE_ID, fileIds.get(0)).build()
                : b.in(ChunkMetadata.FILE_ID, new ArrayList<Object>(fileIds)).build();
        vectorStore.delete(filter);
        log.debug("Deleted chunks for {} file(s)", fileIds.size());
    }

    private static String shortError(Exception e) {
        String message = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
