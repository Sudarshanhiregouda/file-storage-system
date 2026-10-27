package com.filestorage.event;

/** Published by the file domain when a file was archived (soft-deleted). Listeners act after commit. */
public record FileArchivedEvent(Long fileId) {
}
