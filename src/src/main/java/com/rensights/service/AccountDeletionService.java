package com.rensights.service;

import com.rensights.model.AnalysisRequest;
import com.rensights.model.Subscription;
import com.rensights.model.SubscriptionStatus;
import com.rensights.model.User;
import com.rensights.repository.ActivityEventRepository;
import com.rensights.repository.AnalysisRequestRepository;
import com.rensights.repository.DeviceRepository;
import com.rensights.repository.LoginEventRepository;
import com.rensights.repository.SubscriptionRepository;
import com.rensights.repository.UserRepository;
import com.rensights.repository.VerificationCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Erases an account on the user's request (GDPR Art. 17, "right to be forgotten").
 *
 * <h2>What is deleted, and what is not</h2>
 * Everything that identifies the person goes: profile fields, uploaded documents, analysis
 * requests, devices, sign-in history and behavioural events. Issued invoices stay — accounting
 * records have to be retained regardless of an erasure request, which Art. 17(3)(b) explicitly
 * allows — and {@code invoices.user_id} is NOT NULL, so the user row cannot simply be dropped.
 *
 * <p>The row therefore survives as a tombstone: every identifying column is overwritten, the
 * email is replaced with an unusable placeholder, the password is scrambled, and
 * {@code deletedAt} is stamped. Nothing about the person can be recovered from it, and the
 * address is freed so they could sign up again later.
 *
 * <h2>Order of operations</h2>
 * Stripe is dealt with <em>first</em> and its failure aborts the whole deletion. Erasing our
 * records before cancelling would leave a subscription billing an account nobody can find —
 * the exact outcome the user is trying to avoid. Better to fail loudly and let them retry with
 * everything intact.
 */
@Service
public class AccountDeletionService {

    private static final Logger logger = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final DeviceRepository deviceRepository;
    private final ActivityEventRepository activityEventRepository;
    private final LoginEventRepository loginEventRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final FileStorageService fileStorageService;
    private final StripeService stripeService;

    public AccountDeletionService(UserRepository userRepository,
                                  SubscriptionRepository subscriptionRepository,
                                  AnalysisRequestRepository analysisRequestRepository,
                                  DeviceRepository deviceRepository,
                                  ActivityEventRepository activityEventRepository,
                                  LoginEventRepository loginEventRepository,
                                  VerificationCodeRepository verificationCodeRepository,
                                  FileStorageService fileStorageService,
                                  StripeService stripeService) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.analysisRequestRepository = analysisRequestRepository;
        this.deviceRepository = deviceRepository;
        this.activityEventRepository = activityEventRepository;
        this.loginEventRepository = loginEventRepository;
        this.verificationCodeRepository = verificationCodeRepository;
        this.fileStorageService = fileStorageService;
        this.stripeService = stripeService;
    }

    /** Raised when the typed confirmation does not match the account's email address. */
    public static class ConfirmationMismatchException extends RuntimeException {
        public ConfirmationMismatchException(String message) {
            super(message);
        }
    }

    /** Raised when billing could not be stopped, so nothing was deleted. */
    public static class BillingCancellationException extends RuntimeException {
        public BillingCancellationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Delete the account.
     *
     * @param userId            the authenticated caller
     * @param confirmationEmail the address the user typed to confirm; must match the account's.
     *                          Email rather than password: Google sign-ups never chose one, and
     *                          they must be able to exercise erasure too.
     */
    @Transactional
    public void deleteAccount(UUID userId, String confirmationEmail) {
        User user = loadDeletable(userId);
        if (user == null) {
            return;
        }

        if (confirmationEmail == null
            || !confirmationEmail.trim().equalsIgnoreCase(user.getEmail())) {
            throw new ConfirmationMismatchException(
                "The email you entered does not match this account.");
        }

        erase(user);
    }

    /**
     * Delete the account on an administrator's behalf, e.g. when the user asked support to do it.
     *
     * <p>Same erasure, minus the typed confirmation: the admin is acting on someone else's
     * account and cannot be asked to prove they know its address. The confirmation step belongs
     * in the admin UI instead.
     */
    @Transactional
    public void deleteAccountAsAdmin(UUID userId) {
        User user = loadDeletable(userId);
        if (user == null) {
            return;
        }
        logger.info("Account {} being erased by an administrator", userId);
        erase(user);
    }

    /** The account, or null when it is already a tombstone and there is nothing left to erase. */
    private User loadDeletable(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getDeletedAt() != null) {
            logger.info("Account {} is already deleted - nothing to do", userId);
            return null;
        }
        return user;
    }

    private void erase(User user) {
        UUID userId = user.getId();
        String originalEmail = user.getEmail();

        stopBilling(user);
        deleteAnalysisRequests(userId, originalEmail);
        deletePersonalRecords(userId, originalEmail);
        anonymise(user);

        logger.info("Account {} erased on user request", userId);
    }

    /**
     * Cancel every Stripe subscription immediately and delete the customer.
     *
     * <p>No proration is requested: the remainder of a paid period is not refunded, which the
     * confirmation screen states before the user commits.
     */
    private void stopBilling(User user) {
        List<Subscription> subscriptions = subscriptionRepository.findByUserId(user.getId());

        try {
            for (Subscription subscription : subscriptions) {
                String stripeSubscriptionId = subscription.getStripeSubscriptionId();
                if (stripeSubscriptionId != null && !stripeSubscriptionId.isBlank()
                    && subscription.getStatus() != SubscriptionStatus.CANCELLED) {
                    stripeService.cancelSubscriptionImmediately(stripeSubscriptionId);
                }
                subscription.setStatus(SubscriptionStatus.CANCELLED);
            }
            subscriptionRepository.saveAll(subscriptions);

            String stripeCustomerId = user.getStripeCustomerId();
            if (stripeCustomerId != null && !stripeCustomerId.isBlank()) {
                stripeService.deleteCustomer(stripeCustomerId);
            }
        } catch (Exception e) {
            // Abort: the transaction rolls back and the account is left untouched, rather than
            // erasing someone who would keep being charged.
            logger.error("Could not stop billing for user {} - deletion aborted", user.getId(), e);
            throw new BillingCancellationException(
                "We could not cancel your subscription, so your account was not deleted. "
                    + "Please try again shortly.", e);
        }
    }

    /**
     * Analysis requests carry the property details and any documents the user uploaded.
     *
     * <p>Matched by user id <em>and</em> by email: a request submitted before signing in has no
     * user attached and is tied to the address alone, so an id-only sweep would leave the
     * property details and uploaded documents behind.
     */
    private void deleteAnalysisRequests(UUID userId, String email) {
        Map<UUID, AnalysisRequest> requests = new LinkedHashMap<>();
        analysisRequestRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .forEach(request -> requests.put(request.getId(), request));
        analysisRequestRepository.findByEmailOrderByCreatedAtDesc(email)
            .forEach(request -> requests.put(request.getId(), request));

        for (AnalysisRequest request : requests.values()) {
            List<String> filePaths = request.getFilePaths();
            if (filePaths != null && !filePaths.isEmpty()) {
                try {
                    fileStorageService.deleteFiles(filePaths);
                } catch (Exception e) {
                    // Keep going: a stuck file must not block the rest of the erasure. Logged so
                    // it can be cleaned up out of band.
                    logger.error("Failed to delete stored files for analysis request {}",
                        request.getId(), e);
                }
            }
        }
        analysisRequestRepository.deleteAll(requests.values());
    }

    private void deletePersonalRecords(UUID userId, String email) {
        deviceRepository.deleteByUserId(userId);
        loginEventRepository.deleteByUserId(userId);
        activityEventRepository.deleteByUserId(userId);
        verificationCodeRepository.deleteByEmail(email);
        verificationCodeRepository.deleteByEmail("reset:" + email);
    }

    /**
     * Overwrite everything identifying on the surviving row.
     *
     * <p>The placeholder address uses the reserved {@code .invalid} TLD (RFC 2606), so it can
     * never route anywhere, and carries the user id so support can still tie a retained invoice
     * to the deletion event without learning who it was.
     */
    private void anonymise(User user) {
        user.setEmail("deleted-" + user.getId() + "@deleted.invalid");
        // Not a usable credential: nothing hashes to this, so the account cannot be signed into.
        user.setPasswordHash("DELETED");
        user.setFirstName(null);
        user.setLastName(null);
        user.setPhone(null);
        user.setBudget(null);
        user.setPortfolio(null);
        user.setGoalsJson(null);
        user.setRegistrationPlan(null);
        user.setStripeCustomerId(null);
        user.setLastSeenAt(null);
        user.setIsActive(false);
        user.setEmailVerified(false);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
    }
}
