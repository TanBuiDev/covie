package vn.covie.merchants.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.support.PostgresIntegrationTest;

class MerchantSlugServiceIntegrationTest extends PostgresIntegrationTest {
    @Autowired MerchantSlugService slugs;
    @Autowired MerchantProfileRepository profiles;
    @Autowired TransactionTemplate transactions;

    @Test
    void serializesConcurrentSameNameAllocationAndUsesTheUuidSuffixForSecondMerchant() throws Exception {
        String name = "Tạp hóa Minh " + UUID.randomUUID();
        String base = MerchantSlugService.normalize(name);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> createAfterStart(name, firstId, ready, start));
            Future<String> second = executor.submit(() -> createAfterStart(name, secondId, ready, start));
            ready.await();
            start.countDown();
            Set<String> allocated = Set.of(first.get(), second.get());

            assertThat(allocated).hasSize(2).contains(base);
            assertThat(allocated.stream().filter(slug -> !slug.equals(base)).findFirst().orElseThrow())
                    .startsWith(base + "-").hasSize(base.length() + 7);
        }
    }

    private String createAfterStart(String name, UUID merchantId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return transactions.execute(status -> {
            String slug = slugs.uniqueSlug(name, merchantId);
            profiles.saveAndFlush(MerchantProfileEntity.create(merchantId, name, slug, Instant.now()));
            return slug;
        });
    }
}
