package com.filestorage.service;

import com.filestorage.entity.FileEntity;
import com.filestorage.repository.FileRepository;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FilePurgeService {

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
        System.out.println("[FilePurgeService] Purged " + deleted + " files archived before " + cutoff);
    }
}
