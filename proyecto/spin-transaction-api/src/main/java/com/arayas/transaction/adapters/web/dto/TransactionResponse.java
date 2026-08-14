package com.arayas.transaction.adapters.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.arayas.transaction.adapters.persistence.entity.TransactionEntity;
import com.arayas.transaction.application.model.TransactionStatus;
import com.arayas.transaction.application.model.TransactionType;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(
		UUID id,
		String accountId,
		TransactionType type,
		BigDecimal amount,
		String currency,
		String description,
		TransactionStatus status,
		String providerTransactionId,
		BigDecimal balanceAfter,
		String providerCode,
		String providerMessage,
		Instant executedAt,
		Instant createdAt) {

	public static TransactionResponse from(TransactionEntity entity) {
		return new TransactionResponse(
				entity.getId(),
				entity.getAccountId(),
				entity.getType(),
				entity.getAmount(),
				entity.getCurrency(),
				entity.getDescription(),
				entity.getStatus(),
				entity.getProviderTransactionId(),
				entity.getBalanceAfter(),
				null,
				null,
				null,
				entity.getCreatedAt());
	}

	public static TransactionResponse fromRejected(TransactionEntity entity) {
		return new TransactionResponse(
				null,
				null,
				null,
				null,
				null,
				null,
				entity.getStatus(),
				null,
				null,
				entity.getProviderCode(),
				entity.getProviderMessage(),
				null,
				null);
	}

}
