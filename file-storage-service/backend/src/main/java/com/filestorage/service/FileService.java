package com.filestorage.service;

import com.filestorage.dto.FileRequestDTO;
import com.filestorage.dto.FileResponseDTO;
import com.filestorage.dto.FileUpdateDTO;
import com.filestorage.dto.StatsResponseDTO;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    FileResponseDTO createFile(FileRequestDTO requestDTO);

    List<FileResponseDTO> createFiles(List<FileRequestDTO> requestDTOs);

    FileResponseDTO uploadFile(MultipartFile file, String ownerName);

    FileResponseDTO getActiveFileById(Long id);

    Map<String, Object> getActiveFiles(int page, int size, String owner);

    void softDelete(Long id);

    FileResponseDTO restoreFile(Long id);

    FileResponseDTO updateFile(Long id, FileUpdateDTO dto);

    List<FileResponseDTO> getDeletedFiles();

    StatsResponseDTO getStats();

    FileDownload downloadFile(Long id);

    interface FileDownload {
        String fileName();
        InputStream content();
    }
}
