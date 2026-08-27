package com.filestorage.repository;

import com.filestorage.entity.FileAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileAuditLogRepository extends JpaRepository<FileAuditLog, Long> {

    List<FileAuditLog> findByFileIdOrderByPerformedAtDesc(Long fileId);

    List<FileAuditLog> findTop50ByOrderByPerformedAtDesc();
}
