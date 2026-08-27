package com.filestorage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class FileRequestDTO {

    @JsonProperty("file_name")
    @NotBlank(message = "file_name is required")
    private String fileName;

    @JsonProperty("file_size")
    @Positive(message = "file_size must be greater than 0")
    private Long fileSize;

    @JsonProperty("file_type")
    @NotBlank(message = "file_type is required")
    private String fileType;

    @JsonProperty("file_path")
    @NotBlank(message = "file_path is required")
    private String filePath;

    @JsonProperty("owner_name")
    @NotBlank(message = "owner_name is required")
    private String ownerName;

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
}
