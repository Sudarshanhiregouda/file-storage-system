package com.filestorage.event;

/** Published by the file domain when a file was given a new owner. Listeners act after commit. */
public record FileOwnerChangedEvent(Long fileId) {
}
