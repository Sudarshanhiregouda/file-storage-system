package com.filestorage.controller;

import com.filestorage.dto.FileRequestDTO;
import com.filestorage.dto.FileResponseDTO;
import com.filestorage.dto.FileUpdateDTO;
import com.filestorage.dto.StatsResponseDTO;
import com.filestorage.entity.FileAuditLog;
import com.filestorage.repository.FileAuditLogRepository;
import com.filestorage.service.FileService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final FileAuditLogRepository auditLogRepository;

    public FileController(FileService fileService, FileAuditLogRepository auditLogRepository) {
        this.fileService = fileService;
        this.auditLogRepository = auditLogRepository;
    }

    @PostMapping
    public ResponseEntity<FileResponseDTO> createFile(@Valid @RequestBody FileRequestDTO requestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fileService.createFile(requestDTO));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<FileResponseDTO>> createFiles(
            @RequestBody @jakarta.validation.Valid List<FileRequestDTO> requestDTOs) {
        if (requestDTOs == null || requestDTOs.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(fileService.createFiles(requestDTOs));
    }

    @PostMapping("/upload")
    public ResponseEntity<FileResponseDTO> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "owner_name", required = false) String ownerName) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fileService.uploadFile(file, ownerName));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable Long id) {
        FileService.FileDownload download = fileService.downloadFile(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.fileName()).build().toString())
                .body(new InputStreamResource(download.content()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileResponseDTO> getFileById(@PathVariable Long id) {
        return ResponseEntity.ok(fileService.getActiveFileById(id));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getFiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String owner
    ) {
        return ResponseEntity.ok(fileService.getActiveFiles(page, size, owner));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> softDeleteFile(@PathVariable Long id) {
        fileService.softDelete(id);
        return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FileResponseDTO> updateFile(@PathVariable Long id, @RequestBody FileUpdateDTO dto) {
        return ResponseEntity.ok(fileService.updateFile(id, dto));
    }

    @PutMapping("/{id}/restore")
    public ResponseEntity<FileResponseDTO> restoreFile(@PathVariable Long id) {
        return ResponseEntity.ok(fileService.restoreFile(id));
    }

    @GetMapping("/audit")
    public ResponseEntity<List<FileAuditLog>> getAuditLog() {
        return ResponseEntity.ok(auditLogRepository.findTop50ByOrderByPerformedAtDesc());
    }

    @GetMapping("/stats")
    public ResponseEntity<StatsResponseDTO> getStats() {
        return ResponseEntity.ok(fileService.getStats());
    }

    @GetMapping("/deleted")
    public ResponseEntity<List<FileResponseDTO>> getDeletedFiles() {
        return ResponseEntity.ok(fileService.getDeletedFiles());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }
}
