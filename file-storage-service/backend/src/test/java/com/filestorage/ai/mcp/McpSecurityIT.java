package com.filestorage.ai.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.filestorage.ai.support.AbstractAiIT;
import com.filestorage.dto.FileResponseDTO;
import com.filestorage.security.JwtService;
import com.filestorage.service.FileService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

/**
 * Talks to /mcp over real HTTP with the MCP Java SDK client, exactly like Claude Desktop or Copilot would.
 * Proves the Bearer token's identity reaches the tools and that the tools are owner-scoped.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "ai.similarity-threshold=0.1")
class McpSecurityIT extends AbstractAiIT {

    @LocalServerPort
    int port;

    @Autowired
    JwtService jwtService;

    @Autowired
    FileService fileService;

    private McpSyncClient clientFor(String username) {
        HttpClientStreamableHttpTransport.Builder transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port)
                .endpoint("/mcp");
        if (username != null) {
            String token = jwtService.generateToken(username, "USER");
            transport.customizeRequest(r -> r.header("Authorization", "Bearer " + token));
        }
        McpSyncClient client = McpClient.sync(transport.build()).requestTimeout(Duration.ofSeconds(20)).build();
        client.initialize();
        return client;
    }

    private Long uploadAs(String username, String fileName, String content) {
        loginAs(username, false);
        FileResponseDTO dto = fileService.uploadFile(new MockMultipartFile(
                "file", fileName, "text/markdown", content.getBytes(StandardCharsets.UTF_8)), null);
        SecurityContextHolder.clearContext();
        return dto.getId();
    }

    private static String text(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .collect(Collectors.joining("\n"));
    }

    @Test
    void connectionWithoutTokenIsRejected() {
        // JwtAuthFilter + SecurityConfig answer 401 before any MCP handshake happens.
        assertThatThrownBy(() -> clientFor(null)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void toolsAreListed() {
        try (McpSyncClient alice = clientFor("alice")) {
            assertThat(alice.listTools().tools()).extracting(McpSchema.Tool::name)
                    .contains("search_my_documents", "list_my_documents", "get_document_text");
        }
    }

    @Test
    void toolsSeeOnlyTheCallersDocuments() {
        Long aliceFile = uploadAs("alice", "jvm-oom.md",
                "OutOfMemoryError after deploy: a duplicate java process was running on the host.");
        Long bobFile = uploadAs("bob", "travel-policy.md",
                "The travel reimbursement limit per trip is 2500 USD. OutOfMemoryError is not covered by this policy.");

        try (McpSyncClient alice = clientFor("alice")) {
            McpSchema.CallToolResult search = alice.callTool(
                    new McpSchema.CallToolRequest("search_my_documents", Map.of("query", "OutOfMemoryError deploy")));
            assertThat(search.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(text(search)).contains("jvm-oom.md").doesNotContain("travel-policy.md");

            String listing = text(alice.callTool(new McpSchema.CallToolRequest("list_my_documents", Map.of())));
            assertThat(listing).contains("jvm-oom.md").doesNotContain("travel-policy.md");

            String own = text(alice.callTool(
                    new McpSchema.CallToolRequest("get_document_text", Map.of("fileId", aliceFile))));
            assertThat(own).contains("duplicate java process");

            // Bob's real id and an id that doesn't exist must look exactly the same.
            String bobs = text(alice.callTool(
                    new McpSchema.CallToolRequest("get_document_text", Map.of("fileId", bobFile))));
            String missing = text(alice.callTool(
                    new McpSchema.CallToolRequest("get_document_text", Map.of("fileId", 999_999))));
            assertThat(bobs).isEqualTo(missing).contains(DocumentTools.NOT_FOUND);
        }
    }

    @Test
    void extraIdentityArgumentsAreIgnored() {
        uploadAs("bob", "travel-policy.md", "The travel reimbursement limit per trip is 2500 USD.");

        try (McpSyncClient alice = clientFor("alice")) {
            String result = text(alice.callTool(new McpSchema.CallToolRequest("search_my_documents",
                    Map.of("query", "travel reimbursement limit", "username", "bob", "ownerName", "bob"))));
            assertThat(result).doesNotContain("travel-policy.md").contains(DocumentTools.NOTHING_FOUND);
        }
    }
}
