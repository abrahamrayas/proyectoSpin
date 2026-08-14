package com.arayas.transaction;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@EnabledIf("com.arayas.transaction.DockerTestSupport#dockerAvailable")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("observability")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "test-user", roles = "USER")
class TransactionControllerIT {

	private static MockWebServer providerServer;

	@Autowired
	private MockMvc mockMvc;

	@BeforeAll
	static void startProvider() throws IOException {
		providerServer = new MockWebServer();
		providerServer.start();
	}

	@AfterAll
	static void stopProvider() throws IOException {
		if (providerServer != null) {
			providerServer.close();
		}
	}

	@DynamicPropertySource
	static void providerProperties(DynamicPropertyRegistry registry) {
		registry.add("spin.provider.base-url", () -> providerServer.url("/").toString().replaceAll("/$", ""));
	}

	@Test
	void rejectsBusinessRuleBeforeProvider() throws Exception {
		mockMvc.perform(post("/transactions")
						.with(user("test-user"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "accountId": "acc-1",
								  "type": "DEBIT",
								  "amount": 0.50,
								  "currency": "MXN"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Amount must be greater than 1.00"));

		assertEquals(1, providerServer.getRequestCount());
	}

	@Test
	void postTransactionExecutesAndLists() throws Exception {
		providerServer.enqueue(new MockResponse.Builder()
				.setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				.body("""
						{
						  "transactionId": "txn-789",
						  "status": "APPROVED",
						  "balance": 5500.00,
						  "executedAt": "2025-03-15T10:30:00Z"
						}
						""")
				.build());

		mockMvc.perform(post("/transactions")
						.with(user("test-user"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "accountId": "acc-123456",
								  "type": "CREDIT",
								  "amount": 1500.00,
								  "currency": "MXN",
								  "description": "Transfer received"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("EXECUTED"))
				.andExpect(jsonPath("$.providerTransactionId").value("txn-789"))
				.andExpect(jsonPath("$.balanceAfter").value(5500.00));

		mockMvc.perform(get("/transactions")
						.with(user("test-user"))
						.param("accountId", "acc-123456"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].accountId").value("acc-123456"));
	}
}
