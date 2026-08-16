package com.rensights.service;

import com.rensights.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The one place the "exactly once per account" rule for lifecycle emails is implemented.
 *
 * <p>The welcome, getting-started and feedback jobs differ only in their delay, their template
 * and which column they stamp — the send loop itself is the part that has to be right, so it
 * lives here rather than being copied into each scheduler where it could quietly drift apart.
 *
 * <p><b>Claim, then send.</b> The claim is a conditional UPDATE that only matches a row whose
 * stamp is still null, so exactly one caller can ever win a given account — concurrent replicas,
 * overlapping runs and restarts all resolve to a single send.
 *
 * <p><b>A failed send is not retried.</b> The claim stands. Retrying would mean a second copy for
 * anyone whose message actually went out before the call failed, and for these emails a
 * duplicate is worse than a miss. Failures are logged at error level so a real outage shows up.
 */
@Component
public class LifecycleEmailRunner {

    private static final Logger logger = LoggerFactory.getLogger(LifecycleEmailRunner.class);

    /** A conditional stamp; returns the number of rows it won (1) or lost (0). */
    @FunctionalInterface
    public interface Claim {
        int tryClaim(UUID userId, LocalDateTime at);
    }

    /** Sends the message to one account, throwing if it could not be handed to the provider. */
    @FunctionalInterface
    public interface Send {
        void to(User user) throws Exception;
    }

    /**
     * Claim and send for every account in the batch.
     *
     * @param label what to call this email in the logs, e.g. {@code "Welcome email"}
     */
    public void run(String label, List<User> due, Claim claim, Send send) {
        if (due.isEmpty()) {
            return;
        }

        logger.info("{}: {} account(s) due", label, due.size());
        for (User user : due) {
            if (claim.tryClaim(user.getId(), LocalDateTime.now()) == 0) {
                // Someone else already has this account - never send a second copy.
                continue;
            }

            try {
                send.to(user);
            } catch (Exception e) {
                logger.error("{} failed for user {} - not retried, claim stands: {}",
                    label, user.getId(), e.getMessage());
            }
        }
    }
}
