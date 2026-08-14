package com.arayas.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.arayas.transaction.adapters.persistence.entity.TransactionEntity;
import com.arayas.transaction.adapters.persistence.repository.TransactionRepository;
import com.arayas.transaction.adapters.web.dto.CreateTransactionRequest;
import com.arayas.transaction.application.model.ProviderExecuteResult;
import com.arayas.transaction.application.model.TransactionStatus;
import com.arayas.transaction.application.model.TransactionType;
import com.arayas.transaction.application.port.TransactionProviderPort;
import com.arayas.transaction.infrastructure.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

	@Mock
	private TransactionRepository repository;

	@Mock
	private TransactionProviderPort providerPort;

	private TransactionService service;

	@BeforeEach
	void setUp() {
		service = new TransactionService(repository, new TransactionRulesValidator(), providerPort);
	}

	@Test
	void executesApprovedCreditTransaction() {
		when(providerPort.execute(any())).thenReturn(
				ProviderExecuteResult.approved("txn-1", new BigDecimal("6500.00"), Instant.parse("2025-03-15T10:30:00Z")));
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		var request = new CreateTransactionRequest("acc-1", TransactionType.CREDIT, new BigDecimal("1500.00"), "MXN", "Test");
		var response = service.execute(request, "username");

		assertThat(response.status()).isEqualTo(TransactionStatus.EXECUTED);
		assertThat(response.providerTransactionId()).isEqualTo("txn-1");
		assertThat(response.balanceAfter()).isEqualByComparingTo("6500.00");

		ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getCurrency()).isEqualTo("MXN");
	}

	@Test
	void persistsRejectedProviderResponse() {
		when(providerPort.execute(any())).thenReturn(
				ProviderExecuteResult.rejected("INSUFFICIENT_FUNDS", "Not enough balance"));
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		var request = new CreateTransactionRequest("acc-1", TransactionType.DEBIT, new BigDecimal("50.00"), "MXN", null);
		var response = service.execute(request, "username");

		assertThat(response.status()).isEqualTo(TransactionStatus.REJECTED);
		assertThat(response.providerCode()).isEqualTo("INSUFFICIENT_FUNDS");
	}

	@Test
	void rejectsInvalidCurrencyBeforeProviderCall() {
		var request = new CreateTransactionRequest("acc-1", TransactionType.CREDIT, new BigDecimal("10.00"), "USD", null);

		assertThatThrownBy(() -> service.execute(request, "username"))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("MXN");
	}

	@Test
	void getByIdThrowsWhenMissing() {
		when(repository.findById(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getById(UUID.randomUUID(), "username"))
				.isInstanceOf(com.arayas.transaction.infrastructure.exception.ResourceNotFoundException.class);
	}

}
