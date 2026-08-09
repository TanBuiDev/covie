package vn.covie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import vn.covie.shared.id.UuidV7Generator;

@SpringBootApplication
public class CovieApplication {

    public static void main(String[] args) {
        SpringApplication.run(CovieApplication.class, args);
    }

    @Bean
    UuidV7Generator uuidV7Generator() {
        return new UuidV7Generator();
    }
}
