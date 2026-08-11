package vn.covie.media.application;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps the application composition valid until Task 8 provides the real, persisted media policy.
 * It is deliberately fail-fast rather than allowing unverified attachments.
 */
@Configuration(proxyBeanMethods = false)
class DeferredMediaAttachmentPolicy {

    @Bean
    @ConditionalOnMissingBean(MediaAttachmentPolicy.class)
    MediaAttachmentPolicy mediaAttachmentPolicy() {
        return new MediaAttachmentPolicy() {
            @Override
            public void requireMerchantLogo(UUID mediaId, UUID ownerUserId) {
                unavailable();
            }

            @Override
            public void requireMerchantCover(UUID mediaId, UUID ownerUserId) {
                unavailable();
            }

            private void unavailable() {
                throw new IllegalStateException("Media attachment validation is not configured.");
            }
        };
    }
}
