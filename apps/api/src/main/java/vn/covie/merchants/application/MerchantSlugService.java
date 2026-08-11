package vn.covie.merchants.application;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

@Service
public class MerchantSlugService {
    private final Predicate<String> slugExists;

    public MerchantSlugService(vn.covie.merchants.persistence.MerchantProfileRepository merchants) {
        this(merchants::existsBySlugAndDeletedAtIsNull);
    }

    MerchantSlugService(Predicate<String> slugExists) {
        this.slugExists = slugExists;
    }

    public String uniqueSlug(String name, UUID merchantId) {
        String base = normalize(name);
        if (!slugExists.test(base)) return base;
        return base + "-" + merchantId.toString().replace("-", "").substring(0, 6);
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
}
