package com.rensights.service;

import com.rensights.model.User;
import com.rensights.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sends the getting-started email a day after an account is created.
 *
 * <p>Deliberately a sibling of {@link WelcomeEmailScheduler} rather than a second job inside it:
 * the two run on their own delays, their own windows and their own kill switches, and each
 * stamps its own column. The loop is short enough that keeping them separate reads better than
 * a shared abstraction over which query and which stamp to use.
 *
 * <p><b>Window.</b> An account qualifies once its email is verified, it is older than
 * {@code delay-hours}, and it is younger than {@code max-age-hours}. The upper bound must stay
 * comfortably above the delay — with both at 24h the window would be empty and nothing would
 * ever send. It also stops the first run after deploy from mailing the back catalogue, and
 * bounds retries for an address that keeps failing.
 */
@Component
public class GettingStartedEmailScheduler {

    private static final Logger logger = LoggerFactory.getLogger(GettingStartedEmailScheduler.class);

    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.getting-started-email.enabled:true}")
    private boolean enabled;

    /** How long after sign-up the email goes out. The brief asks for one day. */
    @Value("${app.getting-started-email.delay-hours:24}")
    private long delayHours;

    /** Accounts older than this are never sent it — see the class note on the first run. */
    @Value("${app.getting-started-email.max-age-hours:96}")
    private long maxAgeHours;

    /** Cap per run, so a backlog drains steadily instead of in one burst. */
    @Value("${app.getting-started-email.batch-size:50}")
    private int batchSize;

    public GettingStartedEmailScheduler(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @Scheduled(fixedDelayString = "${app.getting-started-email.poll-interval-ms:300000}",
               initialDelayString = "${app.getting-started-email.initial-delay-ms:120000}")
    public void sendDueGettingStartedEmails() {
        if (!enabled) {
            return;
        }

        if (maxAgeHours <= delayHours) {
            logger.warn("Getting-started email: max-age-hours ({}) must exceed delay-hours ({}) "
                + "or nothing is ever due - skipping", maxAgeHours, delayHours);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<User> due = userRepository.findGettingStartedEmailDue(
            now.minusHours(delayHours),
            now.minusHours(maxAgeHours),
            PageRequest.of(0, batchSize));

        if (due.isEmpty()) {
            return;
        }

        logger.info("Getting-started email: {} account(s) due", due.size());
        for (User user : due) {
            try {
                emailService.sendGettingStartedEmail(user.getEmail(), user.getFirstName());
                // Stamped only after a successful send, so a transient failure is retried
                // on the next run instead of silently swallowing the email.
                userRepository.markGettingStartedEmailSent(user.getId(), LocalDateTime.now());
            } catch (Exception e) {
                logger.error("Getting-started email failed for user {} - will retry: {}",
                    user.getId(), e.getMessage());
            }
        }
    }
}
