package com.example.vaultweb;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Vault Spring Boot 웹 애플리케이션 테스트 클래스
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.cloud.vault.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver"
})
class VaultWebApplicationTest {

    @Test
    void contextLoads() {
        // Spring Boot 애플리케이션 컨텍스트가 정상적으로 로드되는지 테스트
    }
}
