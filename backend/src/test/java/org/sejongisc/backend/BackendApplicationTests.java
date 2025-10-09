package org.sejongisc.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"jwt.secret=test-secret",
		"spring.datasource.url=jdbc:h2:mem:testdb",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"google.client.id=test-google-id",
		"google.client.secret=test-google-secret",
		"kakao.client.id=test-kakao-id",
		"github.client.id=test-github-id",
		"github.client.secret=test-github-secret"
})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
