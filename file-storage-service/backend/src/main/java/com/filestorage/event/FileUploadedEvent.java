package com.filestorage.event;

/** Published by the file domain when a file was uploaded with binary content. Listeners act after commit. */
public record FileUploadedEvent(Long fileId) {
}
