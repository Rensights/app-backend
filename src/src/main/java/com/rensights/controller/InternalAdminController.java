package com.rensights.controller;

import com.rensights.service.AccountDeletionService;
import com.rensights.service.AdminJwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Service-to-service endpoints the admin backend calls. Not for browsers.
 *
 * <h2>Why this exists</h2>
 * Account erasure has to happen here: this service owns the uploaded documents on its own
 * filesystem, which the admin service cannot reach. Rather than duplicate the erasure (and let
 * the two copies drift), the admin backend delegates to the one implementation.
 *
 * <h2>Authentication</h2>
 * The caller presents an admin JWT. Both services read {@code JWT_SECRET} from the same shared
 * secret, so {@link AdminJwtService} can verify a token the admin backend signed — no separate
 * service credential to provision. Spring Security lets this path through unauthenticated
 * because the app's own user filter would misread an admin token; the check below is the gate,
 * so it rejects anything that is not a validly signed admin token.
 */
@RestController
@RequestMapping("/api/internal/admin")
public class InternalAdminController {

    private static final Logger logger = LoggerFactory.getLogger(InternalAdminController.class);

    private final AdminJwtService adminJwtService;
    private final AccountDeletionService accountDeletionService;

    public InternalAdminController(AdminJwtService adminJwtService,
                                   AccountDeletionService accountDeletionService) {
        this.adminJwtService = adminJwtService;
        this.accountDeletionService = accountDeletionService;
    }

    /** Erase a user account at an administrator's request. Irreversible. */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable UUID userId, HttpServletRequest request) {
        if (!isAuthenticAdmin(request)) {
            logger.warn("SECURITY: rejected internal deletion request for user {} from {}",
                userId, request.getRemoteAddr());
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        try {
            accountDeletionService.deleteAccountAsAdmin(userId);
            return ResponseEntity.ok(Map.of("message", "Account deleted"));
        } catch (AccountDeletionService.BillingCancellationException e) {
            // Nothing was deleted; the admin should retry once Stripe is reachable again.
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error deleting account {} on admin request", userId, e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to delete account"));
        }
    }

    private boolean isAuthenticAdmin(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return false;
        }
        return adminJwtService.validateToken(header.substring(7).trim());
    }
}
