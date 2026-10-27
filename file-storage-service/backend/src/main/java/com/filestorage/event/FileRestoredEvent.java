package com.filestorage.event;

/** Published by the file domain when a file was restored from the archive. Listeners act after commit. */
public record FileRestoredEvent(Long fileId) {
}
