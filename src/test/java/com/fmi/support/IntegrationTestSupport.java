package com.fmi.support;

import static org.mockito.Mockito.mock;

import com.fmi.global.service.S3Service;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IntegrationTestSupport.IntegrationTestConfiguration.class)
public abstract class IntegrationTestSupport {

    @TestConfiguration
    static class IntegrationTestConfiguration {

        @Bean
        @ServiceConnection
        MySQLContainer<?> mysqlContainer() {
            return new MySQLContainer<>("mysql:8.4");
        }

        @Bean
        @Primary
        S3Service integrationTestS3Service() {
            return mock(S3Service.class);
        }

        @Bean
        @Primary
        Clock integrationTestClock() {
            return Clock.fixed(Instant.parse("2026-09-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }
}
