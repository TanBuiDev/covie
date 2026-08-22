package vn.covie.needs.application;

public record NeedPolicySnapshot(int maxResponseItems) {
    public NeedPolicySnapshot {
        if (maxResponseItems < 1) throw new IllegalArgumentException("maxResponseItems must be positive");
    }
}
