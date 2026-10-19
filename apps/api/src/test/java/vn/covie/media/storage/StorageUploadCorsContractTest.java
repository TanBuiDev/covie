package vn.covie.media.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@EnabledIfSystemProperty(named="covie.test.storage-cors-file",matches=".+")
class StorageUploadCorsContractTest {
    @Test void deploymentCorsAllowsEveryHeaderRequiredByActualConditionalPresign() throws Exception {
        var credentials=StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access","test-secret"));
        try (var presigner=S3Presigner.builder().region(Region.AP_SOUTHEAST_1).credentialsProvider(credentials).build()) {
            var properties=new StorageProperties("covie-media","ap-southeast-1",null,
                    URI.create("https://media.covie.vn"),false,Duration.ofMinutes(10),10485760,20);
            var upload=new S3ObjectStorage(properties,null,presigner).presignPut("cors-contract.png","image/png",68,Duration.ofMinutes(10));
            var headers=upload.requiredHeaders().keySet().stream().map(header -> header.toLowerCase(Locale.ROOT)).toList();
            assertThat(headers).contains("if-none-match");
            var configuration=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(Path.of(System.getProperty("covie.test.storage-cors-file"))));
            assertThat(configuration.isObject()).as("apply script passes the file directly as AWS CLI CORSConfiguration, not a console rules array").isTrue();
            var rules=configuration.path("CORSRules");
            assertThat(rules.isArray()).as("AWS CLI CORSConfiguration requires CORSRules").isTrue();
            boolean matched=false;
            for (var rule : rules) {
                boolean origin=false,method=false;
                for (var value : rule.path("AllowedOrigins")) if (value.asText().equals("https://covie.vn")) origin=true;
                for (var value : rule.path("AllowedMethods")) if (value.asText().equals("PUT")) method=true;
                if (!origin || !method) continue;
                for (String header : headers) {
                    boolean allowed=false;
                    for (var value : rule.path("AllowedHeaders")) {
                        String pattern=value.asText().toLowerCase(Locale.ROOT);
                        int wildcard=pattern.indexOf('*');
                        if (wildcard<0 ? pattern.equals(header) : header.startsWith(pattern.substring(0,wildcard)) && header.endsWith(pattern.substring(wildcard+1))) allowed=true;
                    }
                    assertThat(allowed).as("deployment CORS must allow required signed upload header %s",header).isTrue();
                }
                matched=true;
            }
            assertThat(matched).as("production origin must have a matching PUT rule").isTrue();
        }
    }
}
