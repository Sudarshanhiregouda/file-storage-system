package com.filestorage.ai.ask;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    /** Answers from the authenticated caller's own documents (admins: all documents), with citations. */
    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return askService.ask(request);
    }
}
