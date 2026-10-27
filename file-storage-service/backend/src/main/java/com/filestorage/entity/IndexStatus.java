package com.filestorage.entity;

/** Where a file is in the search-indexing pipeline. */
public enum IndexStatus {
    PENDING,
    INDEXED,
    FAILED,
    UNSUPPORTED
}
