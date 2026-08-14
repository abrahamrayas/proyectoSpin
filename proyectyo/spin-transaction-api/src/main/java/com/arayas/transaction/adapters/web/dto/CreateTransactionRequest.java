package com.arayas.transaction.adapters.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.arayas.transaction.adapters.persistence.entity.TransactionEntity;
import com.arayas.transaction.application.model.TransactionStatus;
import com.arayas.transaction.application.model.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTransactionRequest(
		@NotBlank @Size(max = 64) String accountId,
		@NotNull TransactionType type,
		@NotNull @DecimalMin(value = "0.01") BigDecimal amount,
		@NotBlank @Size(min = 3, max = 3) String currency,
		@Size(max = 512) String description) {
}
