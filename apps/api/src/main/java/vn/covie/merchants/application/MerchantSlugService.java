package vn.covie.merchants.application;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MerchantSlugService {
    private final Predicate<String> slugExists;
    private final EntityManager entityManager;

    @Autowired
    public MerchantSlugService(vn.covie.merchants.persistence.MerchantProfileRepository merchants, EntityManager entityManager) {
        this(merchants::existsBySlugAndDeletedAtIsNull, entityManager);
    }

    public MerchantSlugService(vn.covie.merchants.persistence.MerchantProfileRepository merchants) {
        this(merchants::existsBySlugAndDeletedAtIsNull, null);
    }

    MerchantSlugService(Predicate<String> slugExists, EntityManager entityManager) {
        this.slugExists = slugExists;
        this.entityManager = entityManager;
    }

    public String uniqueSlug(String name, UUID merchantId) {
        String base = normalize(name);
        lock(base);
        if (!slugExists.test(base)) return base;
        String candidate = base + "-" + merchantId.toString().replace("-", "").substring(0, 6);
        if (slugExists.test(candidate)) {
            throw new SlugCollisionException();
        }
        return candidate;
    }

    private void lock(String base) {
        if (entityManager != null) {
            entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(cast(:slug as text), 0))")
                    .setParameter("slug", base)
                    .getSingleResult();
        }
    }

    static String normalize(String name) {
        String decomposed = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replace("đ", "d").replace("Đ", "D")
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return decomposed.isEmpty() ? "merchant" : decomposed;
    }

    static final class SlugCollisionException extends RuntimeException {}
}
