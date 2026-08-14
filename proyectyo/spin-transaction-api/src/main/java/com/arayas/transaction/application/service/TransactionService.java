package com.arayas.transaction.application.service;

import java.time.Instant;
import java.util.UUID;

import com.arayas.transaction.adapters.persistence.entity.TransactionEntity;
import com.arayas.transaction.adapters.persistence.repository.TransactionRepository;
import com.arayas.transaction.adapters.persistence.repository.TransactionSpecifications;
import com.arayas.transaction.adapters.web.dto.CreateTransactionRequest;
import com.arayas.transaction.adapters.web.dto.TransactionPageResponse;
import com.arayas.transaction.adapters.web.dto.TransactionResponse;
import com.arayas.transaction.application.model.ProviderExecuteRequest;
import com.arayas.transaction.application.model.ProviderExecuteResult;
import com.arayas.transaction.application.model.TransactionStatus;
import com.arayas.transaction.application.model.TransactionType;
import com.arayas.transaction.application.port.TransactionProviderPort;
import com.arayas.transaction.infrastructure.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

	private final TransactionRepository repository;
	private final TransactionRulesValidator rulesValidator;
	private final TransactionProviderPort providerPort;

	@Transactional
	public TransactionResponse execute(CreateTransactionRequest request, String username) {
		if (username.isEmpty()) {
			//TODO: create account service (all structure) to validated if the account belongs to the user,
			// if not sent a meesage of Rejected.
		}

		rulesValidator.validate(request.currency(), request.type(), request.amount());

		ProviderExecuteResult providerResult = providerPort.execute(
				new ProviderExecuteRequest(
					request.accountId(),
					request.type(),
					request.amount(),
					request.currency().toUpperCase()));

		TransactionEntity entity = new TransactionEntity();
		entity.setId(UUID.randomUUID());
		entity.setAccountId(request.accountId());
		entity.setType(request.type());
		entity.setAmount(request.amount());
		entity.setCurrency(request.currency().toUpperCase());
		entity.setDescription(request.description());
		entity.setCreatedAt(Instant.now());
		entity.setBalanceAfter(providerResult.balance());
		entity.setExecutedAt(providerResult.executedAt());
		entity.setProviderCode(providerResult.rejectionCode());
		entity.setProviderMessage(providerResult.rejectionMessage());
		entity.setProviderTransactionId(providerResult.transactionId());

		if (!providerResult.approved()) {
			entity.setStatus(TransactionStatus.REJECTED);
			log.info("Transaction rejected accountId={} type={} amount={} code={}",
					request.accountId(), request.type(), request.amount(), providerResult.rejectionCode());
			return TransactionResponse.fromRejected(repository.save(entity));
		}

		entity.setStatus(TransactionStatus.EXECUTED);
		log.info("Transaction executed accountId={} type={} amount={} providerTxn={}",
				request.accountId(), request.type(), request.amount(), providerResult.transactionId());

		return TransactionResponse.from(repository.save(entity));
	}

	@Transactional(readOnly = true)
	public TransactionPageResponse list(
			String accountId,
			TransactionStatus status,
			TransactionType type,
			int page,
			int limit,
			String username) {

		if (username.isEmpty()) {
			//TODO: create account service (all structure) to validated if the account belongs to the user,
			// if not sent a meesage of Rejected.
		}

		Specification<TransactionEntity> spec = Specification.allOf(
				TransactionSpecifications.withAccountId(accountId),
				TransactionSpecifications.withStatus(status),
				TransactionSpecifications.withType(type));

		Page<TransactionEntity> result = repository.findAll(
				spec,
				PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "createdAt")));

		return TransactionPageResponse.from(result.map(TransactionResponse::from));
	}

	@Transactional(readOnly = true)
	public TransactionResponse getById(UUID id, String username) {

		if (username.isEmpty()) {
			//TODO: create account service (all structure) to validated if the account belongs to the user,
			// if not sent a meesage of Rejected.
		}

		return repository.findById(id)
				.map(TransactionResponse::from)
				.orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
	}

}
