package com.notes;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class NotesApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotesApplication.class, args);
    }

    /**
     * One identity for every Azure call - no password or key in the code:
     *   - on your laptop DefaultAzureCredential uses your "az login"
     *   - on App Service it uses the app's managed identity
     */
    @Bean
    public TokenCredential azureCredential() {
        return new DefaultAzureCredentialBuilder().build();
    }

    /** Connects to the 'notes' blob container (needs role: Storage Blob Data Contributor). */
    @Bean
    public BlobContainerClient notesContainer(TokenCredential credential,
                                              @Value("${storage.account-name}") String accountName,
                                              @Value("${storage.container}") String container) {
        return new BlobServiceClientBuilder()
                .endpoint("https://" + accountName + ".blob.core.windows.net")
                .credential(credential)
                .buildClient()
                .getBlobContainerClient(container);
    }

    /** Connects to Key Vault to read secrets (needs role: Key Vault Secrets User). */
    @Bean
    public SecretClient secretClient(TokenCredential credential,
                                     @Value("${keyvault.url}") String vaultUrl) {
        return new SecretClientBuilder()
                .vaultUrl(vaultUrl)
                .credential(credential)
                .buildClient();
    }
}
