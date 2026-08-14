package com.arayas.transaction.application.model;

import java.math.BigDecimal;
import java.time.Instant;

public record ProviderExecuteResult(
		boolean approved,
		String transactionId,
		BigDecimal balance,
		Instant executedAt,
		String rejectionCode,
		String rejectionMessage) {

	public static ProviderExecuteResult approved(String transactionId, BigDecimal balance, Instant executedAt) {
		return new ProviderExecuteResult(true, transactionId, balance, executedAt, null, null);
	}

	public static ProviderExecuteResult rejected(String code, String message) {
		return new ProviderExecuteResult(false, null, null, null, code, message);
	}

}
