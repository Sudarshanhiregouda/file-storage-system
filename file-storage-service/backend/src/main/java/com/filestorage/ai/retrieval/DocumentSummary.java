package com.filestorage.ai.retrieval;

import java.time.LocalDateTime;

/** An active file the caller can see, with its search-index status. */
public record DocumentSummary(
        Long fileId, String fileName, String fileType, String indexStatus, Integer chunkCount, LocalDateTime createdAt) {
}
