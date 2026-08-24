package vn.covie.shared.moderation;

import java.util.UUID;

public interface ModerationCaseWriter {
    UUID openPreApproval(ModerationTargetRef target, String reasonCode);
}
