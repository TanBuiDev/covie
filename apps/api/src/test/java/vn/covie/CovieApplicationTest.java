package vn.covie;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class CovieApplicationTest {

    @Test
    void applicationEntrypointIsSpringBootApplication() {
        assertThat(CovieApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
    }
}
