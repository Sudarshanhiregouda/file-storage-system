package com.filestorage.ai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.filestorage.ai.support.AbstractAiIT;
import com.filestorage.dto.FileResponseDTO;
import com.filestorage.dto.FileUpdateDTO;
import com.filestorage.entity.IndexStatus;
import com.filestorage.repository.FileRepository;
import com.filestorage.service.FileService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

/**
 * Proves the core rule against real pgvector: a user only ever retrieves chunks of files they own,
 * and the index follows the file lifecycle. No Ollama: embeddings are fake, the chat model is mocked.
 */
@TestPropertySource(properties = "ai.similarity-threshold=0.0") // owner filter alone must keep other users out
class RetrievalSecurityIT extends AbstractAiIT {

    private static final String OOM_QUERY = "OutOfMemoryError heap after deploy duplicate process";

    @Autowired
    FileService fileService;

    @Autowired
    RetrievalService retrievalService;

    @Autowired
    FileRepository fileRepository;

    private Long uploadAs(String username, String fileName, String content) {
        loginAs(username, false);
        FileResponseDTO dto = fileService.uploadFile(new MockMultipartFile(
                "file", fileName, "text/markdown", content.getBytes(StandardCharsets.UTF_8)), null);
        assertThat(fileRepository.findById(dto.getId()).orElseThrow().getIndexStatus())
                .as("indexing ran after commit").isEqualTo(IndexStatus.INDEXED);
        return dto.getId();
    }

    private List<Long> searchAs(String username, boolean admin, String query) {
        loginAs(username, admin);
        return retrievalService.search(query, 10).stream().map(SearchHit::fileId).distinct().toList();
    }

    private Long aliceDoc() {
        return uploadAs("alice", "alice-oom.md",
                "# Alice runbook\nServices throw OutOfMemoryError heap space after a deploy because a duplicate process runs.");
    }

    private Long bobDoc() {
        return uploadAs("bob", "bob-oom.md",
                "# Bob notes\nOutOfMemoryError heap problems after deploy: check for a duplicate process on the host.");
    }

    @Test
    void usersOnlyRetrieveTheirOwnChunks() {
        Long alice = aliceDoc();
        Long bob = bobDoc();

        assertThat(searchAs("bob", false, OOM_QUERY)).containsExactly(bob);
        assertThat(searchAs("alice", false, OOM_QUERY)).containsExactly(alice);
    }

    @Test
    void adminRetrievesFromAllUsers() {
        Long alice = aliceDoc();
        Long bob = bobDoc();

        assertThat(searchAs("admin", true, OOM_QUERY)).containsExactlyInAnyOrder(alice, bob);
    }

    @Test
    void archiveRemovesChunks_andRestoreBringsThemBack() {
        Long alice = aliceDoc();

        loginAs("alice", false);
        fileService.softDelete(alice);
        assertThat(searchAs("alice", false, OOM_QUERY)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'fileId' = ?", Integer.class, alice.toString()))
                .isZero();

        loginAs("alice", false);
        fileService.restoreFile(alice);
        assertThat(searchAs("alice", false, OOM_QUERY)).containsExactly(alice);
    }

    @Test
    void ownerChangeMovesChunksToTheNewOwner() {
        Long alice = aliceDoc();

        loginAs("admin", true);
        FileUpdateDTO update = new FileUpdateDTO();
        update.setOwnerName("bob");
        fileService.updateFile(alice, update);

        assertThat(searchAs("alice", false, OOM_QUERY)).isEmpty();
        assertThat(searchAs("bob", false, OOM_QUERY)).containsExactly(alice);
    }

    @Test
    void quotesInUsernameCannotBreakOrWidenTheFilter() {
        aliceDoc();
        bobDoc();

        // JSONPath-style and SQL-style injection attempts: each must see nothing, and must not error.
        for (String attacker : List.of(
                "eve\" || $.ownerName == \"alice",
                "eve\" || true || \"",
                "eve' OR '1'='1",
                "eve'; DELETE FROM vector_store; --",
                "eve\\")) {
            assertThatCode(() -> assertThat(searchAs(attacker, false, OOM_QUERY)).as(attacker).isEmpty())
                    .as(attacker).doesNotThrowAnyException();
        }

        // A legitimate user whose name contains quotes still finds exactly their own documents.
        Long obrien = uploadAs("o'brien \"ob\"", "obrien-oom.md",
                "OutOfMemoryError heap after deploy, duplicate process found on the batch host.");
        assertThat(searchAs("o'brien \"ob\"", false, OOM_QUERY)).containsExactly(obrien);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Integer.class))
                .as("nothing deleted by injection attempts").isGreaterThan(0);
    }
}
