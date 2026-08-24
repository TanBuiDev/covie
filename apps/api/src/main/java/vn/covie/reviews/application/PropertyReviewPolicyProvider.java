package vn.covie.reviews.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class PropertyReviewPolicyProvider implements ReviewPolicyProvider {
    private final boolean creationEnabled;
    PropertyReviewPolicyProvider(@Value("${covie.reviews.creation-enabled:true}") boolean creationEnabled) { this.creationEnabled = creationEnabled; }
    @Override public boolean creationEnabled() { return creationEnabled; }
}
