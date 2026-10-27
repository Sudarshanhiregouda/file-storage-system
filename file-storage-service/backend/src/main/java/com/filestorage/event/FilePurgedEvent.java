package com.filestorage.event;

import java.util.List;

/** Published by the nightly purge with the ids of files that were permanently deleted. */
public record FilePurgedEvent(List<Long> fileIds) {

    public FilePurgedEvent {
        fileIds = List.copyOf(fileIds);
    }
}
