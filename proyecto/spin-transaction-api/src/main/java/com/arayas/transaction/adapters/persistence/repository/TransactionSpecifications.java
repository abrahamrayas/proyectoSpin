package com.arayas.transaction.adapters.persistence.repository;

import com.arayas.transaction.adapters.persistence.entity.TransactionEntity;
import com.arayas.transaction.application.model.TransactionStatus;
import com.arayas.transaction.application.model.TransactionType;
import org.springframework.data.jpa.domain.Specification;

public final class TransactionSpecifications {

	private TransactionSpecifications() {
	}

	public static Specification<TransactionEntity> withAccountId(String accountId) {
		return (root, query, cb) -> accountId == null || accountId.isBlank()
				? cb.conjunction()
				: cb.equal(root.get("accountId"), accountId);
	}

	public static Specification<TransactionEntity> withStatus(TransactionStatus status) {
		return (root, query, cb) -> status == null
				? cb.conjunction()
				: cb.equal(root.get("status"), status);
	}

	public static Specification<TransactionEntity> withType(TransactionType type) {
		return (root, query, cb) -> type == null
				? cb.conjunction()
				: cb.equal(root.get("type"), type);
	}

}
