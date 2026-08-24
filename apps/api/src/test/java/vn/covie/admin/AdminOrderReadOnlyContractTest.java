package vn.covie.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import vn.covie.admin.api.AdminOrderController;

class AdminOrderReadOnlyContractTest {
    @Test
    void adminOrderControllerContainsNoLifecycleMutationMapping() {
        assertThat(Arrays.stream(AdminOrderController.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getAnnotations()))
                .noneMatch(annotation -> annotation.annotationType() == PostMapping.class
                        || annotation.annotationType() == PatchMapping.class))
                .isTrue();
    }
}
