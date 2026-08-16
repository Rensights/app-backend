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
 * Sends the welcome email a few minutes after an account is created, rather than at sign-up.
 *
 * <p>Why a poller instead of a delayed in-process task: an in-memory timer is lost if the pod
 * restarts inside the delay window, and the whole point of the delay is that the message lands
 * after the user has had a first look around. The due state lives in the database
 * ({@code users.welcome_email_sent_at}), so a restart costs nothing.
 *
 * <p><b>Exactly once.</b> Each account is welcomed at most once, ever. The scheduler claims the
 * account with a conditional UPDATE before sending (see
 * {@link UserRepository#claimWelcomeEmail}); losing the claim means someone else already sent it.
 *
 * <p><b>Window.</b> An account qualifies once its email is verified, it is older than
 * {@code delay-minutes}, and it is younger than {@code max-age-hours}. The upper bound is the
 * safety catch: without it, the first run after this feature ships would mail every account ever
 * created.
 *
 * <p>Note that the age is measured from sign-up, not from verification, so someone who verifies
 * more than {@code max-age-hours} after registering is never welcomed. Verification normally
 * happens in the same session (it gates the first login), so that is a rare tail; widen
 * {@code max-age-hours} if it turns out not to be.
 */
@Component
public class WelcomeEmailScheduler {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final LifecycleEmailRunner runner;

    @Value("${app.welcome-email.enabled:true}")
    private boolean enabled;

    /** How long after sign-up the email goes out. The brief asks for 5-10 minutes. */
    @Value("${app.welcome-email.delay-minutes:7}")
    private long delayMinutes;

    /** Accounts older than this are never welcomed — see the class note on the first run. */
    @Value("${app.welcome-email.max-age-hours:24}")
    private long maxAgeHours;

    /** Cap per run, so a backlog drains steadily instead of in one burst. */
    @Value("${app.welcome-email.batch-size:50}")
    private int batchSize;

    public WelcomeEmailScheduler(UserRepository userRepository, EmailService emailService,
                                 LifecycleEmailRunner runner) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${app.welcome-email.poll-interval-ms:60000}",
               initialDelayString = "${app.welcome-email.initial-delay-ms:60000}")
    public void sendDueWelcomeEmails() {
        if (!enabled) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<User> due = userRepository.findWelcomeEmailDue(
            now.minusMinutes(delayMinutes),
            now.minusHours(maxAgeHours),
            PageRequest.of(0, batchSize));

        runner.run("Welcome email", due,
            userRepository::claimWelcomeEmail,
            user -> emailService.sendWelcomeEmail(user.getEmail(), user.getFirstName()));
    }
}
