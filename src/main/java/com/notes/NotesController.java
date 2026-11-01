package com.notes;

import com.azure.core.util.BinaryData;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class NotesController {

    private static final Logger log = LoggerFactory.getLogger(NotesController.class);

    private final BlobContainerClient notes;
    private final SecretClient secrets;

    @Value("${app.version}")
    private String version;

    @Value("${keyvault.api-key-secret-name}")
    private String apiKeySecretName;

    /** The API key is read from Key Vault once, then kept in memory. */
    private volatile String cachedApiKey;

    public NotesController(BlobContainerClient notes, SecretClient secrets) {
        this.notes = notes;
        this.secrets = secrets;
    }

    /** 1. GET /api/hello - is the app up, and which version is running? */
    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of(
                "message", "Hello from Notes API on Azure",
                "version", version,
                "time", Instant.now().toString());
    }

    /**
     * 2. POST /api/notes - save a note as a text file (blob) in the 'notes' container.
     * Protected: the caller must send header X-API-KEY matching the secret stored in Key Vault.
     */
    @PostMapping("/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> addNote(@RequestHeader(value = "X-API-KEY", required = false) String apiKey,
                                       @RequestBody Map<String, String> body) {
        if (apiKey == null || !apiKey.equals(apiKeyFromKeyVault())) {
            throw new UnauthorizedException("missing or wrong X-API-KEY header");
        }
        String text = body.getOrDefault("text", "").trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("'text' is required");
        }
        String name = "note-" + Instant.now().toEpochMilli() + ".txt";
        notes.getBlobClient(name).upload(BinaryData.fromString(text), true);
        log.info("Saved note {}", name);
        return Map.of("saved", name);
    }

    /** 3. GET /api/notes - list every note with its text (public, read-only). */
    @GetMapping("/notes")
    public List<Map<String, String>> listNotes() {
        List<Map<String, String>> result = new ArrayList<>();
        for (BlobItem item : notes.listBlobs()) {
            Map<String, String> note = new LinkedHashMap<>();
            note.put("name", item.getName());
            note.put("text", notes.getBlobClient(item.getName()).downloadContent().toString());
            result.add(note);
        }
        log.info("Listed {} notes", result.size());
        return result;
    }

    /** Reads the secret from Key Vault on first use and caches it (restart the app after rotating it). */
    private String apiKeyFromKeyVault() {
        if (cachedApiKey == null) {
            cachedApiKey = secrets.getSecret(apiKeySecretName).getValue();
            log.info("Loaded API key secret '{}' from Key Vault", apiKeySecretName);
        }
        return cachedApiKey;
    }

    /** Missing 'text' returns 400 instead of a 500. */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }

    /** Missing or wrong API key returns 401. */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> unauthorized(UnauthorizedException e) {
        return Map.of("error", e.getMessage());
    }

    static class UnauthorizedException extends RuntimeException {
        UnauthorizedException(String message) {
            super(message);
        }
    }
}
