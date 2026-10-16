package vn.covie.billing.api;

import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.billing.application.BillingCatalogService;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;

/** Safe public pricing and eligibility summary for seller registration. */
@RestController
public class BillingOnboardingController {
    private final BillingCatalogService plans;
    private final SystemSettingResolver settings;

    public BillingOnboardingController(BillingCatalogService plans,SystemSettingResolver settings){
        this.plans=plans;this.settings=settings;
    }

    @GetMapping("/api/v1/billing/onboarding")
    public ResponseEntity<Onboarding> onboarding(){
        var policy=plans.currentPolicy();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            new Onboarding(settings.bool("billing.enforcement_enabled",SystemSettingContext.globalOnly()),
                settings.bool("billing.seller_registration_enabled",SystemSettingContext.globalOnly()),
                policy.onboardingMode(),policy.trialDays(),policy.graceDays(),plans.availablePlans()));
    }

    public record Onboarding(boolean enforcementEnabled,boolean registrationEnabled,
        String onboardingMode,int trialDays,int graceDays,
        List<BillingCatalogService.PlanOffer> plans){}
}
