package com.filestorage.ai.ask;

import com.filestorage.ai.retrieval.SearchHit;

/** A citation: source [n] in the answer is the n-th SourceRef in the response. */
public record SourceRef(Long fileId, String fileName, Integer page, Integer chunkIndex, double score, String snippet) {

    static final int SNIPPET_LENGTH = 200;

    static SourceRef from(SearchHit hit) {
        String text = hit.text() == null ? "" : hit.text().strip().replaceAll("\\s+", " ");
        String snippet = text.length() > SNIPPET_LENGTH ? text.substring(0, SNIPPET_LENGTH) + "…" : text;
        return new SourceRef(hit.fileId(), hit.fileName(), hit.page(), hit.chunkIndex(), hit.score(), snippet);
    }
}
