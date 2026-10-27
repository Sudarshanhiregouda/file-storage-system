package com.filestorage.ai.indexing;

/** Metadata keys stored on every chunk in the vector store. Shared by indexing and retrieval. */
public final class ChunkMetadata {

    public static final String FILE_ID = "fileId";
    public static final String OWNER_NAME = "ownerName";
    public static final String FILE_NAME = "fileName";
    /** PDF only: 1-based page number the chunk came from. */
    public static final String PAGE = "page";
    /** Position of the chunk within its file, starting at 0. */
    public static final String CHUNK_INDEX = "chunkIndex";

    private ChunkMetadata() {
    }
}
