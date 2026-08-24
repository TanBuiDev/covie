package vn.covie.orders.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "covie.orders")
public record OrderLifecycleProperties(
        Duration confirmationTimeout,
        Duration autoCompleteGrace,
        Duration pickupNoShowGrace,
        Duration issueCloseTimeout) {}
