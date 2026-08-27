package com.filestorage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class StatsResponseDTO {

    @JsonProperty("total_storage_used")
    private Long totalStorageUsed;

    @JsonProperty("total_file_count")
    private Long totalFileCount;

    @JsonProperty("deleted_file_count")
    private Long deletedFileCount;

    @JsonProperty("file_type_count")
    private Long fileTypeCount;

    @JsonProperty("storage_by_type")
    private List<StorageByTypeDTO> storageByType;

    @JsonProperty("top_largest_files")
    private List<LargestFileDTO> topLargestFiles;

    public Long getTotalStorageUsed() {
        return totalStorageUsed;
    }

    public void setTotalStorageUsed(Long totalStorageUsed) {
        this.totalStorageUsed = totalStorageUsed;
    }

    public Long getTotalFileCount() {
        return totalFileCount;
    }

    public void setTotalFileCount(Long totalFileCount) {
        this.totalFileCount = totalFileCount;
    }

    public Long getDeletedFileCount() {
        return deletedFileCount;
    }

    public void setDeletedFileCount(Long deletedFileCount) {
        this.deletedFileCount = deletedFileCount;
    }

    public Long getFileTypeCount() {
        return fileTypeCount;
    }

    public void setFileTypeCount(Long fileTypeCount) {
        this.fileTypeCount = fileTypeCount;
    }

    public List<StorageByTypeDTO> getStorageByType() {
        return storageByType;
    }

    public void setStorageByType(List<StorageByTypeDTO> storageByType) {
        this.storageByType = storageByType;
    }

    public List<LargestFileDTO> getTopLargestFiles() {
        return topLargestFiles;
    }

    public void setTopLargestFiles(List<LargestFileDTO> topLargestFiles) {
        this.topLargestFiles = topLargestFiles;
    }

    public static class StorageByTypeDTO {

        @JsonProperty("file_type")
        private String fileType;

        @JsonProperty("total_files")
        private Long totalFiles;

        @JsonProperty("total_size")
        private Long totalSize;

        public String getFileType() {
            return fileType;
        }

        public void setFileType(String fileType) {
            this.fileType = fileType;
        }

        public Long getTotalFiles() {
            return totalFiles;
        }

        public void setTotalFiles(Long totalFiles) {
            this.totalFiles = totalFiles;
        }

        public Long getTotalSize() {
            return totalSize;
        }

        public void setTotalSize(Long totalSize) {
            this.totalSize = totalSize;
        }
    }

    public static class LargestFileDTO {

        private Long id;

        @JsonProperty("file_name")
        private String fileName;

        @JsonProperty("file_type")
        private String fileType;

        @JsonProperty("file_size")
        private Long fileSize;

        @JsonProperty("owner_name")
        private String ownerName;

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

        public String getFileType() {
            return fileType;
        }

        public void setFileType(String fileType) {
            this.fileType = fileType;
        }

        public Long getFileSize() {
            return fileSize;
        }

        public void setFileSize(Long fileSize) {
            this.fileSize = fileSize;
        }

        public String getOwnerName() {
            return ownerName;
        }

        public void setOwnerName(String ownerName) {
            this.ownerName = ownerName;
        }
    }
}
