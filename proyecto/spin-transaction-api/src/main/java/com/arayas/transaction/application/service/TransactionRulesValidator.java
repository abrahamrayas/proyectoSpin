package com.arayas.transaction.application.service;

import java.math.BigDecimal;

import com.arayas.transaction.application.model.TransactionType;
import com.arayas.transaction.infrastructure.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

@Component
public class TransactionRulesValidator {

	static final BigDecimal MIN_AMOUNT = new BigDecimal("1.00");
	static final BigDecimal MAX_DEBIT_AMOUNT = new BigDecimal("10000.00");
	static final String SUPPORTED_CURRENCY = "MXN";

	public void validate(String currency, TransactionType type, BigDecimal amount) {
		if (currency == null || !SUPPORTED_CURRENCY.equalsIgnoreCase(currency.trim())) {
			throw new BusinessRuleException("Only MXN currency is supported");
		}
		if (amount == null || amount.compareTo(MIN_AMOUNT) <= 0) {
			throw new BusinessRuleException("Amount must be greater than 1.00");
		}
		if (type == TransactionType.DEBIT && amount.compareTo(MAX_DEBIT_AMOUNT) > 0) {
			throw new BusinessRuleException("DEBIT transactions cannot exceed 10000.00 per operation");
		}
	}

}
