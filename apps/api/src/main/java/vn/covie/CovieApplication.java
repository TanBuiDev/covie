package vn.covie;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.time.SystemAppClock;

@SpringBootApplication
public class CovieApplication {

    public static void main(String[] args) {
        SpringApplication.run(CovieApplication.class, args);
    }

    @Bean
    UuidV7Generator uuidV7Generator() {
        return new UuidV7Generator();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    AppClock appClock() {
        return new SystemAppClock();
    }
}
