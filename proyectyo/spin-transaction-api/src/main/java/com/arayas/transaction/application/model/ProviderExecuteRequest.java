package com.arayas.transaction.application.model;

import java.math.BigDecimal;
import java.time.Instant;

public record ProviderExecuteRequest(
		String accountId,
		TransactionType type,
		BigDecimal amount,
		String currency) {
}
