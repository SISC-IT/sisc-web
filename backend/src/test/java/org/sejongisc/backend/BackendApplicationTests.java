package org.sejongisc.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"JWT_SECRET=test-secret",
		"SPRING_DATASOURCE_URL=jdbc:h2:mem:testdb",
		"SPRING_DATASOURCE_USERNAME=sa",
		"SPRING_DATASOURCE_PASSWORD=",
		"FIREBASE_CREDENTIAL_PATH=classpath:firebase/test-key.json"
})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
