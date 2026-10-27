package com.filestorage.ai.retrieval;

/**
 * One retrieved chunk.
 *
 * @param page  1-based PDF page, or {@code null} for formats without pages
 * @param score similarity in [0, 1], higher is closer
 * @param text  chunk text; untrusted document content, never instructions
 */
public record SearchHit(Long fileId, String fileName, Integer page, Integer chunkIndex, double score, String text) {
}
