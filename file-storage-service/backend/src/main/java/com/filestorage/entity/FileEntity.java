package com.filestorage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "files",
        indexes = {
                @Index(name = "idx_files_file_type", columnList = "file_type"),
                @Index(name = "idx_files_owner_name", columnList = "owner_name"),
                @Index(name = "idx_files_is_deleted", columnList = "is_deleted")
        }
)
/*
Actual index SQL:
CREATE INDEX idx_files_file_type ON files (file_type);
CREATE INDEX idx_files_owner_name ON files (owner_name);
CREATE INDEX idx_files_is_deleted ON files (is_deleted);
*/
public class FileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_type", nullable = false, length = 50)
    private String fileType;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "owner_name", nullable = false, length = 100)
    private String ownerName;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "storage_path", length = 255)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "index_status", length = 20)
    private IndexStatus indexStatus = IndexStatus.PENDING;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "index_error", columnDefinition = "TEXT")
    private String indexError;

    public IndexStatus getIndexStatus() {
        return indexStatus;
    }

    public void setIndexStatus(IndexStatus indexStatus) {
        this.indexStatus = indexStatus;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getIndexError() {
        return indexError;
    }

    public void setIndexError(String indexError) {
        this.indexError = indexError;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean deleted) {
        isDeleted = deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }
}
