package com.filestorage.repository;

import com.filestorage.entity.FileEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface FileRepository extends JpaRepository<FileEntity, Long> {

    Optional<FileEntity> findByIdAndIsDeletedFalse(Long id);

    Page<FileEntity> findByIsDeletedFalse(Pageable pageable);

    Page<FileEntity> findByIsDeletedFalseAndOwnerNameContainingIgnoreCase(String ownerName, Pageable pageable);

    List<FileEntity> findByIsDeletedTrueOrderByDeletedAtDesc();

    long countByIsDeletedTrue();

    @Query(value = "SELECT COALESCE(SUM(file_size), 0) FROM files WHERE is_deleted = false", nativeQuery = true)
    Long findTotalStorageUsed();

    /*
    SQL used:
    SELECT file_type, COUNT(*) AS total_files, COALESCE(SUM(file_size), 0) AS total_size
    FROM files
    WHERE is_deleted = false
    GROUP BY file_type
    ORDER BY file_type;
    */
    @Query(value = "SELECT file_type, COUNT(*) AS total_files, COALESCE(SUM(file_size), 0) AS total_size " +
            "FROM files WHERE is_deleted = false GROUP BY file_type ORDER BY file_type", nativeQuery = true)
    List<Object[]> findStorageByType();

    /*
    SQL used:
    SELECT id, file_name, file_type, file_size, owner_name
    FROM files
    WHERE is_deleted = false
    ORDER BY file_size DESC
    LIMIT 5;
    */
    @Query(value = "SELECT id, file_name, file_type, file_size, owner_name " +
            "FROM files WHERE is_deleted = false ORDER BY file_size DESC LIMIT 5", nativeQuery = true)
    List<Object[]> findTopLargestFiles();

    @Query(value = "SELECT COUNT(*) FROM files WHERE is_deleted = false", nativeQuery = true)
    Long findActiveFileCount();

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM files WHERE is_deleted = true AND deleted_at < :cutoff", nativeQuery = true)
    int hardDeleteArchivedBefore(@org.springframework.data.repository.query.Param("cutoff") java.time.LocalDateTime cutoff);

    List<FileEntity> findByIsDeletedTrueAndDeletedAtBefore(java.time.LocalDateTime cutoff);
}
