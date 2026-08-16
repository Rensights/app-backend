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
 * Asks how the product is working out, about ten days after an account is created.
 *
 * <p>Once per account, ever — {@link LifecycleEmailRunner} claims the account before sending,
 * so nobody is asked twice.
 *
 * <p><b>Window.</b> Verified accounts older than {@code delay-hours} and younger than
 * {@code max-age-hours}. The upper bound has to stay comfortably above the delay or the window
 * is empty; it also keeps the first run after deploy from mailing the back catalogue.
 */
@Component
public class FeedbackEmailScheduler {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackEmailScheduler.class);

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final LifecycleEmailRunner runner;

    @Value("${app.feedback-email.enabled:true}")
    private boolean enabled;

    /** How long after sign-up the check-in goes out. The brief asks for around ten days. */
    @Value("${app.feedback-email.delay-hours:240}")
    private long delayHours;

    /** Accounts older than this are never asked — see the class note on the first run. */
    @Value("${app.feedback-email.max-age-hours:480}")
    private long maxAgeHours;

    /** Cap per run, so a backlog drains steadily instead of in one burst. */
    @Value("${app.feedback-email.batch-size:50}")
    private int batchSize;

    public FeedbackEmailScheduler(UserRepository userRepository, EmailService emailService,
                                  LifecycleEmailRunner runner) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${app.feedback-email.poll-interval-ms:900000}",
               initialDelayString = "${app.feedback-email.initial-delay-ms:180000}")
    public void sendDueFeedbackEmails() {
        if (!enabled) {
            return;
        }

        if (maxAgeHours <= delayHours) {
            logger.warn("Feedback email: max-age-hours ({}) must exceed delay-hours ({}) "
                + "or nothing is ever due - skipping", maxAgeHours, delayHours);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<User> due = userRepository.findFeedbackEmailDue(
            now.minusHours(delayHours),
            now.minusHours(maxAgeHours),
            PageRequest.of(0, batchSize));

        runner.run("Feedback email", due,
            userRepository::claimFeedbackEmail,
            user -> emailService.sendFeedbackEmail(user.getEmail(), user.getFirstName()));
    }
}
