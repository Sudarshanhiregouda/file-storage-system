package com.filestorage.service;

import com.filestorage.entity.FileEntity;
import com.filestorage.repository.FileRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FilePurgeService {

    private static final Logger log = LoggerFactory.getLogger(FilePurgeService.class);

    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;

    public FilePurgeService(FileRepository fileRepository, FileStorageService fileStorageService) {
        this.fileRepository = fileRepository;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Runs every day at 2:00 AM.
     * Permanently deletes files that have been ARCHIVED for more than 30 days.
     * Audit log rows are untouched by design: file_audit_log has no FK to files,
     * so lifecycle history survives even after the source record is purged.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void purgeOldArchivedFiles() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);

        for (FileEntity expired : fileRepository.findByIsDeletedTrueAndDeletedAtBefore(cutoff)) {
            if (expired.getStoragePath() != null) {
                fileStorageService.delete(expired.getStoragePath());
            }
        }

        int deleted = fileRepository.hardDeleteArchivedBefore(cutoff);
        log.info("Purged {} files archived before {}", deleted, cutoff);
    }
}
