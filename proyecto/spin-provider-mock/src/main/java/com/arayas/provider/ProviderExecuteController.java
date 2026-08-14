package com.arayas.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/provider/v1")
public class ProviderExecuteController {

	private final Map<String, BigDecimal> balances = new ConcurrentHashMap<>();
	private final AtomicLong txnCounter = new AtomicLong(1);
	private final static String TXN = "txn-";

	@PostMapping("/execute")
	public ResponseEntity<?> execute(@RequestBody ExecuteRequest request) {
		if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
			return rejected(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Amount must be positive");
		}
		if (request.type() == null) {
			return rejected(HttpStatus.BAD_REQUEST, "INVALID_TYPE", "Transaction type is required");
		}

		BigDecimal current = balances.getOrDefault(request.accountId(), new BigDecimal("5000.00"));
		BigDecimal next = switch (request.type().toUpperCase()) {
			case "CREDIT" -> current.add(request.amount());
			case "DEBIT" -> current.subtract(request.amount());
			default -> null;
		};

		if (next == null) {
			return rejected(HttpStatus.BAD_REQUEST, "INVALID_TYPE", "Unsupported transaction type");
		}
		if (next.compareTo(BigDecimal.ZERO) < 0) {
			return rejected(HttpStatus.UNPROCESSABLE_CONTENT, "INSUFFICIENT_FUNDS",
					"The account does not have enough balance to complete the transaction");
		}

		balances.put(request.accountId(), next);
		String txnId = TXN + txnCounter.getAndIncrement();
		return ResponseEntity.ok(new ExecuteResponse(
				txnId,
				"APPROVED",
				next,
				Instant.now(),
				null,
				null));
	}

	private ResponseEntity<ExecuteResponse> rejected(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(new ExecuteResponse(null, "REJECTED", null, null, code, message));
	}

	record ExecuteRequest(String accountId, String type, BigDecimal amount, String currency) {
	}

	record ExecuteResponse(
			String transactionId,
			String status,
			BigDecimal balance,
			Instant executedAt,
			String code,
			String message) {
	}

}
