package com.arayas.transaction.adapters.web.dto;

import java.util.List;

import org.springframework.data.domain.Page;

public record TransactionPageResponse(
		List<TransactionResponse> content,
		int page,
		int limit,
		long totalElements,
		int totalPages) {

	public static TransactionPageResponse from(Page<TransactionResponse> page) {
		return new TransactionPageResponse(
				page.getContent(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}

}
