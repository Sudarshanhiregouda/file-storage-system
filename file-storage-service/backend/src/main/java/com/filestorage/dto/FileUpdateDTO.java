package com.filestorage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FileUpdateDTO {

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("owner_name")
    private String ownerName;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }
}
