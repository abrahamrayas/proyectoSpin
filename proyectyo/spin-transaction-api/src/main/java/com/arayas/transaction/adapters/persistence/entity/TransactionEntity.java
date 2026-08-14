package com.arayas.transaction.adapters.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.arayas.transaction.application.model.TransactionStatus;
import com.arayas.transaction.application.model.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "transactions", indexes = {
		@Index(name = "idx_transactions_account_id", columnList = "account_id"),
		@Index(name = "idx_transactions_status", columnList = "status"),
		@Index(name = "idx_transactions_type", columnList = "type"),
		@Index(name = "idx_transactions_created_at", columnList = "created_at")
})
@Getter
@Setter
public class TransactionEntity {

	@Id
	private UUID id;

	@Column(name = "account_id", nullable = false, length = 64)
	private String accountId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private TransactionType type;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(length = 512)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private TransactionStatus status;

	@Column(name = "provider_transaction_id", length = 128)
	private String providerTransactionId;

	@Column(name = "balance_after", precision = 19, scale = 2)
	private BigDecimal balanceAfter;

	@Column(name = "provider_code", length = 64)
	private String providerCode;

	@Column(name = "provider_message", length = 512)
	private String providerMessage;

	@Column(name = "executed_at")
	private Instant executedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

}
