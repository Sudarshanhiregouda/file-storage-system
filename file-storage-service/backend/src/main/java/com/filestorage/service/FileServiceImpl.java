package com.filestorage.service;

import com.filestorage.dto.FileRequestDTO;
import com.filestorage.dto.FileResponseDTO;
import com.filestorage.dto.FileUpdateDTO;
import com.filestorage.dto.StatsResponseDTO;
import com.filestorage.entity.FileAuditLog;
import com.filestorage.entity.FileEntity;
import com.filestorage.entity.IndexStatus;
import com.filestorage.event.FileArchivedEvent;
import com.filestorage.event.FileOwnerChangedEvent;
import com.filestorage.event.FileRestoredEvent;
import com.filestorage.event.FileUploadedEvent;
import com.filestorage.repository.FileAuditLogRepository;
import com.filestorage.repository.FileRepository;
import com.filestorage.security.AuthUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FileServiceImpl implements FileService {

    private final FileRepository fileRepository;
    private final FileAuditLogRepository auditLogRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    public FileServiceImpl(
            FileRepository fileRepository,
            FileAuditLogRepository auditLogRepository,
            FileStorageService fileStorageService,
            ApplicationEventPublisher eventPublisher) {
        this.fileRepository = fileRepository;
        this.auditLogRepository = auditLogRepository;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public FileResponseDTO createFile(FileRequestDTO requestDTO) {
        FileEntity entity = new FileEntity();
        entity.setFileName(requestDTO.getFileName());
        entity.setFileSize(requestDTO.getFileSize());
        entity.setFileType(requestDTO.getFileType());
        entity.setFilePath(requestDTO.getFilePath());
        entity.setOwnerName(resolveOwnerForCreate(requestDTO.getOwnerName()));
        entity.setIsDeleted(false);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setDeletedAt(null);
        // Metadata-only record: there is no binary content to index.
        entity.setIndexStatus(IndexStatus.UNSUPPORTED);

        FileEntity saved = fileRepository.save(entity);
        auditLogRepository.save(new FileAuditLog(
                saved.getId(), "UPLOADED", saved.getOwnerName(),
                "File '" + saved.getFileName() + "' uploaded (" + saved.getFileSize() + " bytes, type: " + saved.getFileType() + ")"
        ));
        return toResponse(saved);
    }

    @Override
    public List<FileResponseDTO> createFiles(List<FileRequestDTO> requestDTOs) {
        return requestDTOs.stream()
                .map(this::createFile)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public FileResponseDTO uploadFile(MultipartFile file, String ownerName) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
        }

        String storedName;
        try {
            storedName = fileStorageService.store(file);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file", e);
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : storedName;
        String extension = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toUpperCase()
                : "FILE";

        FileEntity entity = new FileEntity();
        entity.setFileName(originalName);
        entity.setFileSize(file.getSize());
        entity.setFileType(extension);
        entity.setFilePath("/uploads/" + storedName);
        entity.setStoragePath(storedName);
        entity.setOwnerName(resolveOwnerForCreate(ownerName));
        entity.setIsDeleted(false);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setIndexStatus(IndexStatus.PENDING);

        FileEntity saved = fileRepository.save(entity);
        auditLogRepository.save(new FileAuditLog(
                saved.getId(), "UPLOADED", saved.getOwnerName(),
                "File '" + saved.getFileName() + "' uploaded (" + saved.getFileSize() + " bytes, type: " + saved.getFileType() + ")"
        ));
        eventPublisher.publishEvent(new FileUploadedEvent(saved.getId()));
        return toResponse(saved);
    }

    @Override
    public FileResponseDTO getActiveFileById(Long id) {
        FileEntity entity = fileRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new NoSuchElementException("File not found"));
        requireOwnerOrAdmin(entity);
        return toResponse(entity);
    }

    @Override
    public Map<String, Object> getActiveFiles(int page, int size, String owner) {
        Pageable pageable = PageRequest.of(page, size);
        Page<FileEntity> pagedResult;

        String effectiveOwner = AuthUtils.isAdmin() ? owner : AuthUtils.currentUsername();

        if (effectiveOwner != null && !effectiveOwner.isBlank()) {
            pagedResult = fileRepository.findByIsDeletedFalseAndOwnerNameContainingIgnoreCase(effectiveOwner, pageable);
        } else {
            pagedResult = fileRepository.findByIsDeletedFalse(pageable);
        }

        List<FileResponseDTO> content = pagedResult.getContent().stream().map(this::toResponse).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", content);
        response.put("totalElements", pagedResult.getTotalElements());
        response.put("totalPages", pagedResult.getTotalPages());
        response.put("currentPage", pagedResult.getNumber());
        return response;
    }

    @Override
    @Transactional
    public void softDelete(Long id) {
        FileEntity entity = fileRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new NoSuchElementException("File not found"));
        requireOwnerOrAdmin(entity);

        entity.setIsDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());
        fileRepository.save(entity);
        auditLogRepository.save(new FileAuditLog(
                entity.getId(), "ARCHIVED", entity.getOwnerName(),
                "File '" + entity.getFileName() + "' archived"
        ));
        eventPublisher.publishEvent(new FileArchivedEvent(entity.getId()));
    }

    @Override
    @Transactional
    public FileResponseDTO restoreFile(Long id) {
        FileEntity entity = fileRepository.findById(id)
                .filter(FileEntity::getIsDeleted)
                .orElseThrow(() -> new NoSuchElementException("Archived file not found with id " + id));
        requireOwnerOrAdmin(entity);

        entity.setIsDeleted(false);
        entity.setDeletedAt(null);
        FileEntity saved = fileRepository.save(entity);
        auditLogRepository.save(new FileAuditLog(
                saved.getId(), "RESTORED", saved.getOwnerName(),
                "File '" + saved.getFileName() + "' restored from archive"
        ));
        eventPublisher.publishEvent(new FileRestoredEvent(saved.getId()));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public FileResponseDTO updateFile(Long id, FileUpdateDTO dto) {
        FileEntity entity = fileRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new NoSuchElementException("File not found"));
        requireOwnerOrAdmin(entity);

        String previousOwner = entity.getOwnerName();
        if (dto.getFileName() != null && !dto.getFileName().isBlank()) {
            entity.setFileName(dto.getFileName());
        }
        if (dto.getOwnerName() != null && !dto.getOwnerName().isBlank() && AuthUtils.isAdmin()) {
            entity.setOwnerName(dto.getOwnerName());
        }
        entity.setUpdatedAt(LocalDateTime.now());

        FileEntity saved = fileRepository.save(entity);
        auditLogRepository.save(new FileAuditLog(
                saved.getId(), "UPDATED", saved.getOwnerName(),
                "File updated: name='" + saved.getFileName() + "', owner='" + saved.getOwnerName() + "'"
        ));
        if (!Objects.equals(previousOwner, saved.getOwnerName())) {
            // Chunks carry ownerName in their metadata, so they must be rebuilt for the new owner.
            eventPublisher.publishEvent(new FileOwnerChangedEvent(saved.getId()));
        }
        return toResponse(saved);
    }

    @Override
    public List<FileResponseDTO> getDeletedFiles() {
        return fileRepository.findByIsDeletedTrueOrderByDeletedAtDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public StatsResponseDTO getStats() {
        StatsResponseDTO stats = new StatsResponseDTO();
        stats.setTotalStorageUsed(fileRepository.findTotalStorageUsed());
        stats.setTotalFileCount(fileRepository.findActiveFileCount());
        stats.setDeletedFileCount(fileRepository.countByIsDeletedTrue());

        List<StatsResponseDTO.StorageByTypeDTO> storageByType = fileRepository.findStorageByType().stream().map(row -> {
            StatsResponseDTO.StorageByTypeDTO dto = new StatsResponseDTO.StorageByTypeDTO();
            dto.setFileType((String) row[0]);
            dto.setTotalFiles(((Number) row[1]).longValue());
            dto.setTotalSize(((Number) row[2]).longValue());
            return dto;
        }).toList();

        List<StatsResponseDTO.LargestFileDTO> largestFiles = fileRepository.findTopLargestFiles().stream().map(row -> {
            StatsResponseDTO.LargestFileDTO dto = new StatsResponseDTO.LargestFileDTO();
            dto.setId(((Number) row[0]).longValue());
            dto.setFileName((String) row[1]);
            dto.setFileType((String) row[2]);
            dto.setFileSize(((Number) row[3]).longValue());
            dto.setOwnerName((String) row[4]);
            return dto;
        }).toList();

        stats.setStorageByType(storageByType);
        stats.setTopLargestFiles(largestFiles);
        stats.setFileTypeCount((long) storageByType.size());
        return stats;
    }

    @Override
    public FileDownload downloadFile(Long id) {
        FileEntity entity = fileRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("File not found"));
        requireOwnerOrAdmin(entity);

        if (entity.getStoragePath() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No binary content stored for this file");
        }

        try {
            InputStream stream = Files.newInputStream(fileStorageService.resolve(entity.getStoragePath()));
            return new FileDownload() {
                @Override
                public String fileName() {
                    return entity.getFileName();
                }

                @Override
                public InputStream content() {
                    return stream;
                }
            };
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored file is missing on disk");
        }
    }

    private String resolveOwnerForCreate(String requestedOwner) {
        String currentUser = AuthUtils.currentUsername();
        if (AuthUtils.isAdmin() && requestedOwner != null && !requestedOwner.isBlank()) {
            return requestedOwner;
        }
        return currentUser != null ? currentUser : requestedOwner;
    }

    private void requireOwnerOrAdmin(FileEntity entity) {
        String currentUser = AuthUtils.currentUsername();
        if (AuthUtils.isAdmin()) {
            return;
        }
        if (currentUser == null || !currentUser.equalsIgnoreCase(entity.getOwnerName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this file");
        }
    }

    private FileResponseDTO toResponse(FileEntity entity) {
        FileResponseDTO dto = new FileResponseDTO();
        dto.setId(entity.getId());
        dto.setFileName(entity.getFileName());
        dto.setFileSize(entity.getFileSize());
        dto.setFileType(entity.getFileType());
        dto.setFilePath(entity.getFilePath());
        dto.setOwnerName(entity.getOwnerName());
        dto.setIsDeleted(entity.getIsDeleted());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setStatus(entity.getIsDeleted() ? "ARCHIVED" : "ACTIVE");
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setDeletedAt(entity.getDeletedAt());
        dto.setDownloadable(entity.getStoragePath() != null);
        dto.setIndexStatus(entity.getIndexStatus() != null ? entity.getIndexStatus().name() : null);
        return dto;
    }
}
