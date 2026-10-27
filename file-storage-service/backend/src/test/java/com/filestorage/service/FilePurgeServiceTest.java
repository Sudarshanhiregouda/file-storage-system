package com.filestorage.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filestorage.entity.FileEntity;
import com.filestorage.event.FilePurgedEvent;
import com.filestorage.repository.FileRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class FilePurgeServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FilePurgeService purgeService;

    private FileEntity archived(Long id, String storagePath) {
        FileEntity e = new FileEntity();
        e.setId(id);
        e.setStoragePath(storagePath);
        e.setIsDeleted(true);
        return e;
    }

    @Test
    void purge_deletesBinariesAndPublishesPurgedIds() {
        when(fileRepository.findByIsDeletedTrueAndDeletedAtBefore(any()))
                .thenReturn(List.of(archived(1L, "a.pdf"), archived(2L, null)));
        when(fileRepository.hardDeleteArchivedBefore(any())).thenReturn(2);

        purgeService.purgeOldArchivedFiles();

        verify(fileStorageService).delete("a.pdf");
        verify(eventPublisher).publishEvent(new FilePurgedEvent(List.of(1L, 2L)));
    }

    @Test
    void purge_publishesNothing_whenNothingExpired() {
        when(fileRepository.findByIsDeletedTrueAndDeletedAtBefore(any())).thenReturn(List.of());

        purgeService.purgeOldArchivedFiles();

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
