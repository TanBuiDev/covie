package vn.covie;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.time.SystemAppClock;

@SpringBootApplication
@EnableScheduling
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
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        com.fasterxml.jackson.databind.module.SimpleModule module = new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addSerializer(com.fasterxml.jackson.databind.JsonNode.class, new com.fasterxml.jackson.databind.JsonSerializer<>() {
            @Override
            public void serialize(com.fasterxml.jackson.databind.JsonNode value, com.fasterxml.jackson.core.JsonGenerator gen, com.fasterxml.jackson.databind.SerializerProvider serializers) throws java.io.IOException {
                if (value == null) {
                    gen.writeNull();
                } else {
                    gen.writeTree(value);
                }
            }
        });
        mapper.registerModule(module);
        return mapper;
    }

    @Bean
    AppClock appClock() {
        return new SystemAppClock();
    }
}
