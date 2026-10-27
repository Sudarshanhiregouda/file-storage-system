package com.filestorage.ai.retrieval;

/**
 * The indexed text of one file, chunks joined in order.
 *
 * @param truncated true when only the first {@code chunksReturned} of {@code totalChunks} chunks are included
 */
public record DocumentText(
        Long fileId, String fileName, int chunksReturned, int totalChunks, boolean truncated, String text) {
}
