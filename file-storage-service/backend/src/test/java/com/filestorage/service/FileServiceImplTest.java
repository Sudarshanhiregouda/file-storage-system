package com.filestorage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filestorage.dto.FileRequestDTO;
import com.filestorage.dto.FileResponseDTO;
import com.filestorage.dto.FileUpdateDTO;
import com.filestorage.entity.FileAuditLog;
import com.filestorage.entity.FileEntity;
import com.filestorage.repository.FileAuditLogRepository;
import com.filestorage.repository.FileRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pure Mockito unit tests: FileService is exercised entirely behind its interface,
 * with the repository and storage layers mocked out — no Spring context is booted.
 */
@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileAuditLogRepository auditLogRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private FileServiceImpl fileService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username, boolean admin) {
        var authorities = List.of(new SimpleGrantedAuthority(admin ? "ROLE_ADMIN" : "ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, authorities));
    }

    private FileRequestDTO buildRequest(String owner) {
        FileRequestDTO dto = new FileRequestDTO();
        dto.setFileName("report.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("PDF");
        dto.setFilePath("/uploads/report.pdf");
        dto.setOwnerName(owner);
        return dto;
    }

    @Test
    void createFile_forcesOwnerToCurrentUser_whenCallerIsNotAdmin() {
        loginAs("alice", false);
        when(fileRepository.save(any())).thenAnswer(inv -> {
            FileEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        FileResponseDTO response = fileService.createFile(buildRequest("someone-else"));

        assertThat(response.getOwnerName()).isEqualTo("alice");
        verify(auditLogRepository).save(any(FileAuditLog.class));
    }

    @Test
    void createFile_allowsAdminToSetArbitraryOwner() {
        loginAs("admin", true);
        when(fileRepository.save(any())).thenAnswer(inv -> {
            FileEntity e = inv.getArgument(0);
            e.setId(2L);
            return e;
        });

        FileResponseDTO response = fileService.createFile(buildRequest("bob"));

        assertThat(response.getOwnerName()).isEqualTo("bob");
    }

    @Test
    void getActiveFileById_returnsFile_whenCallerIsOwner() {
        loginAs("alice", false);
        FileEntity entity = new FileEntity();
        entity.setId(5L);
        entity.setOwnerName("alice");
        entity.setFileName("doc.txt");
        entity.setIsDeleted(false);
        when(fileRepository.findByIdAndIsDeletedFalse(5L)).thenReturn(Optional.of(entity));

        FileResponseDTO response = fileService.getActiveFileById(5L);

        assertThat(response.getId()).isEqualTo(5L);
    }

    @Test
    void getActiveFileById_throwsForbidden_whenCallerIsNeitherOwnerNorAdmin() {
        loginAs("mallory", false);
        FileEntity entity = new FileEntity();
        entity.setId(6L);
        entity.setOwnerName("alice");
        when(fileRepository.findByIdAndIsDeletedFalse(6L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> fileService.getActiveFileById(6L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void getActiveFileById_throwsNotFound_whenFileMissing() {
        loginAs("alice", false);
        when(fileRepository.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getActiveFileById(99L))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void softDelete_marksFileArchivedAndWritesAuditLog() {
        loginAs("alice", false);
        FileEntity entity = new FileEntity();
        entity.setId(7L);
        entity.setOwnerName("alice");
        entity.setFileName("doc.txt");
        entity.setIsDeleted(false);
        when(fileRepository.findByIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(entity));
        when(fileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        fileService.softDelete(7L);

        assertThat(entity.getIsDeleted()).isTrue();
        assertThat(entity.getDeletedAt()).isNotNull();
        verify(auditLogRepository, times(1)).save(any(FileAuditLog.class));
    }

    @Test
    void softDelete_throwsForbidden_forNonOwnerNonAdmin() {
        loginAs("mallory", false);
        FileEntity entity = new FileEntity();
        entity.setId(8L);
        entity.setOwnerName("alice");
        when(fileRepository.findByIdAndIsDeletedFalse(8L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> fileService.softDelete(8L))
                .isInstanceOf(ResponseStatusException.class);
        verify(fileRepository, times(0)).save(any());
    }

    @Test
    void restoreFile_throwsNotFound_whenFileIsNotArchived() {
        loginAs("alice", false);
        FileEntity entity = new FileEntity();
        entity.setId(9L);
        entity.setIsDeleted(false);
        when(fileRepository.findById(9L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> fileService.restoreFile(9L))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void updateFile_ignoresOwnerChange_whenCallerIsNotAdmin() {
        loginAs("alice", false);
        FileEntity entity = new FileEntity();
        entity.setId(10L);
        entity.setOwnerName("alice");
        entity.setFileName("old.txt");
        entity.setIsDeleted(false);
        when(fileRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(entity));
        when(fileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FileUpdateDTO update = new FileUpdateDTO();
        update.setFileName("new.txt");
        update.setOwnerName("someone-else");

        FileResponseDTO response = fileService.updateFile(10L, update);

        assertThat(response.getFileName()).isEqualTo("new.txt");
        assertThat(response.getOwnerName()).isEqualTo("alice");
    }

    @Test
    void getActiveFiles_scopesToCurrentUser_whenCallerIsNotAdmin() {
        loginAs("alice", false);
        Page<FileEntity> emptyPage = new PageImpl<>(List.of());
        when(fileRepository.findByIsDeletedFalseAndOwnerNameContainingIgnoreCase(
                org.mockito.ArgumentMatchers.eq("alice"), any(PageRequest.class)))
                .thenReturn(emptyPage);

        var result = fileService.getActiveFiles(0, 10, "some-other-owner-filter");

        assertThat(result.get("content")).isEqualTo(List.of());
        verify(fileRepository).findByIsDeletedFalseAndOwnerNameContainingIgnoreCase(
                org.mockito.ArgumentMatchers.eq("alice"), any(PageRequest.class));
    }
}
