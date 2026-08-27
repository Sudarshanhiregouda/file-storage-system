package com.filestorage.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private final Path storageRoot;

    public FileStorageService(@Value("${storage.location:./uploads}") String storageLocation) {
        this.storageRoot = Paths.get(storageLocation).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(storageRoot);
    }

    public String store(MultipartFile file) throws IOException {
        String extension = "";
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            extension = original.substring(original.lastIndexOf('.'));
        }
        String storedName = UUID.randomUUID() + extension;
        Path target = storageRoot.resolve(storedName).normalize();

        if (!target.getParent().equals(storageRoot)) {
            throw new IOException("Invalid file path");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target);
        }
        return storedName;
    }

    public Path resolve(String storedName) {
        Path target = storageRoot.resolve(storedName).normalize();
        if (!target.getParent().equals(storageRoot)) {
            throw new IllegalArgumentException("Invalid stored file reference");
        }
        return target;
    }

    public void delete(String storedName) {
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException ignored) {
        }
    }
}
