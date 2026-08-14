package com.arayas.transaction.adapters.web;

import java.util.UUID;

import com.arayas.transaction.adapters.web.dto.CreateTransactionRequest;
import com.arayas.transaction.adapters.web.dto.TransactionPageResponse;
import com.arayas.transaction.adapters.web.dto.TransactionResponse;
import com.arayas.transaction.application.model.TransactionStatus;
import com.arayas.transaction.application.model.TransactionType;
import com.arayas.transaction.application.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Validated
public class TransactionController {

	private static final int DEFAULT_LIMIT = 20;
	private static final int MAX_LIMIT = 100;

	private final TransactionService transactionService;

	@Operation(
			summary = "Execute Transaction",
			security = @SecurityRequirement(name = "bearerAuth")
	)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TransactionResponse execute(@Valid @RequestBody CreateTransactionRequest request, Authentication authentication) {
		String username = authentication.getName();

		return transactionService.execute(request, username);
	}

	@Operation(
			summary = "List Transaction",
			security = @SecurityRequirement(name = "bearerAuth")
	)
	@GetMapping
	public TransactionPageResponse list(
			@RequestParam(required = false) String accountId,
			@RequestParam(required = false) TransactionStatus status,
			@RequestParam(required = false) TransactionType type,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Min(1) @Max(MAX_LIMIT) int limit,
			Authentication authentication) {

		String username = authentication.getName();

		int effectiveLimit = limit <= 0
				? DEFAULT_LIMIT
				: Math.min(limit, MAX_LIMIT);
		return transactionService.list(accountId, status, type, page, effectiveLimit, username);
	}

	@Operation(
			summary = "Ger by id Transaction",
			security = @SecurityRequirement(name = "bearerAuth")
	)
	@GetMapping("/{id}")
	public TransactionResponse getById(@PathVariable UUID id, Authentication authentication) {

		String username = authentication.getName();

		return transactionService.getById(id, username);
	}

}
