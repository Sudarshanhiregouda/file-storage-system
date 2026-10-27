package com.filestorage.ai.indexing;

import com.filestorage.entity.FileEntity;
import com.filestorage.service.FileStorageService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Turns a stored file into ordered text chunks. Used by the indexer and by get_document_text,
 * so both always agree on what a file's chunks are.
 */
@Component
public class DocumentParser {

    static final Set<String> SUPPORTED_TYPES = Set.of("PDF", "DOCX", "TXT", "MD");

    /** A chunk of a file's text; {@code page} is set for PDFs only. */
    public record Chunk(int index, Integer page, String text) {
    }

    private final FileStorageService fileStorageService;
    private final TokenTextSplitter splitter;

    public DocumentParser(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(200)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10_000)
                .withKeepSeparator(true)
                .build();
    }

    public boolean isSupported(FileEntity file) {
        return file.getStoragePath() != null && SUPPORTED_TYPES.contains(type(file));
    }

    /** Reads and splits the file: PDFs page by page (keeping page numbers), everything else with Tika. */
    public List<Chunk> parse(FileEntity file) throws IOException {
        Resource resource = fileStorageService.loadAsResource(file.getStoragePath());
        boolean pdf = "PDF".equals(type(file));
        List<Document> pages = pdf ? new PagePdfDocumentReader(resource).get() : new TikaDocumentReader(resource).get();

        List<Chunk> chunks = new ArrayList<>();
        for (Document split : splitter.apply(pages)) {
            String text = split.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            Integer page = null;
            if (pdf && split.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER) instanceof Number n) {
                page = n.intValue();
            }
            chunks.add(new Chunk(chunks.size(), page, text));
        }
        return chunks;
    }

    private static String type(FileEntity file) {
        return file.getFileType() == null ? "" : file.getFileType().toUpperCase(Locale.ROOT);
    }
}
