package com.rensights.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class GoogleTokenVerifierService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleTokenVerifierService.class);

    @Value("${app.google.client-id:}")
    private String clientId;

    public GoogleUserInfo verify(String credentialJwt) throws Exception {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Google Sign-In is not configured (missing GOOGLE_CLIENT_ID / app.google.client-id)");
        }

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(Collections.singletonList(clientId.trim()))
                .build();

        GoogleIdToken idToken = verifier.verify(credentialJwt);
        if (idToken == null) {
            throw new SecurityException("Invalid Google ID token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        if (email == null || email.isBlank()) {
            throw new SecurityException("Google token has no email");
        }
        if (Boolean.FALSE.equals(payload.getEmailVerified())) {
            throw new SecurityException("Google email is not verified");
        }

        String givenName = (String) payload.get("given_name");
        String familyName = (String) payload.get("family_name");
        String subject = payload.getSubject();

        logger.info("Verified Google ID token for email {}", email);
        return new GoogleUserInfo(email.trim().toLowerCase(), subject, givenName, familyName);
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }

    public record GoogleUserInfo(String email, String subject, String givenName, String familyName) {
    }
}
