package com.arayas.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.arayas.transaction.application.model.TransactionType;
import com.arayas.transaction.infrastructure.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionRulesValidatorTest {

	private TransactionRulesValidator validator;

	@BeforeEach
	void setUp() {
		validator = new TransactionRulesValidator();
	}

	@Test
	void acceptsValidCredit() {
		assertThatCode(() -> validator.validate("MXN", TransactionType.CREDIT, new BigDecimal("1500.00")))
				.doesNotThrowAnyException();
	}

	@Test
	void rejectsAmountEqualToOne() {
		assertThatThrownBy(() -> validator.validate("MXN", TransactionType.CREDIT, new BigDecimal("1.00")))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("greater than 1.00");
	}

	@Test
	void rejectsDebitAboveLimit() {
		assertThatThrownBy(() -> validator.validate("MXN", TransactionType.DEBIT, new BigDecimal("10000.01")))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("10000.00");
	}

	@Test
	void rejectsNonMxnCurrency() {
		assertThatThrownBy(() -> validator.validate("USD", TransactionType.CREDIT, new BigDecimal("10.00")))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("MXN");
	}

}
