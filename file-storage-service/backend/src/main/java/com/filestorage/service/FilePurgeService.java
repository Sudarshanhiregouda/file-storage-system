package com.filestorage.service;

import com.filestorage.entity.FileEntity;
import com.filestorage.event.FilePurgedEvent;
import com.filestorage.repository.FileRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FilePurgeService {

    private static final Logger log = LoggerFactory.getLogger(FilePurgeService.class);

    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    public FilePurgeService(
            FileRepository fileRepository,
            FileStorageService fileStorageService,
            ApplicationEventPublisher eventPublisher) {
        this.fileRepository = fileRepository;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Runs every day at 2:00 AM.
     * Permanently deletes files that have been ARCHIVED for more than 30 days.
     * Audit log rows are untouched by design: file_audit_log has no FK to files,
     * so lifecycle history survives even after the source record is purged.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void purgeOldArchivedFiles() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);

        // Collect ids before the hard delete: afterwards the rows are gone.
        List<FileEntity> expired = fileRepository.findByIsDeletedTrueAndDeletedAtBefore(cutoff);
        List<Long> purgedIds = expired.stream().map(FileEntity::getId).toList();

        for (FileEntity file : expired) {
            if (file.getStoragePath() != null) {
                fileStorageService.delete(file.getStoragePath());
            }
        }

        int deleted = fileRepository.hardDeleteArchivedBefore(cutoff);
        if (!purgedIds.isEmpty()) {
            eventPublisher.publishEvent(new FilePurgedEvent(purgedIds));
        }
        log.info("Purged {} files archived before {}", deleted, cutoff);
    }
}
