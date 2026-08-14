package com.arayas.transaction.infrastructure.provider;

import java.math.BigDecimal;
import java.time.Instant;

import com.arayas.transaction.application.model.ProviderExecuteRequest;
import com.arayas.transaction.application.model.ProviderExecuteResult;
import com.arayas.transaction.application.port.TransactionProviderPort;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class RestTransactionProviderClient implements TransactionProviderPort {

	private static final String EXECUTE_PATH = "/provider/v1/execute";

	private final RestClient restClient;
	private final Tracer tracer;

	public RestTransactionProviderClient(
			@Qualifier("transaction-provider") RestClient restClient,
			Tracer tracer) {
		this.restClient = restClient;
		this.tracer = tracer;
	}

	@Override
	public ProviderExecuteResult execute(ProviderExecuteRequest request) {
		tagActiveSpan(request);
		long startNanos = System.nanoTime();
		log.info("Provider POST {} accountId={} type={} amount={}",
				EXECUTE_PATH, request.accountId(), request.type(), request.amount());
		try {
			return restClient.post()
					.uri(EXECUTE_PATH)
					.contentType(MediaType.APPLICATION_JSON)
					.body(new ProviderExecuteBody(
							request.accountId(),
							request.type().name(),
							request.amount(),
							request.currency()))
					.exchange((req, res) -> {
						ProviderExecuteResponse body = res.bodyTo(ProviderExecuteResponse.class);
						if (body == null) {
							return ProviderExecuteResult.rejected("PROVIDER_ERROR", "Empty provider response");
						}
						if (res.getStatusCode().is2xxSuccessful() && "APPROVED".equalsIgnoreCase(body.status())) {
							log.info("Provider POST {} done in {} ms status=APPROVED txn={}",
									EXECUTE_PATH, elapsedMs(startNanos), body.transactionId());
							return ProviderExecuteResult.approved(
									body.transactionId(),
									body.balance(),
									body.executedAt() != null ? body.executedAt() : Instant.now());
						}
						log.info("Provider POST {} done in {} ms status={} code={}",
								EXECUTE_PATH, elapsedMs(startNanos), body.status(), body.code());
						return ProviderExecuteResult.rejected(
								body.code() != null ? body.code() : "REJECTED",
								body.message() != null ? body.message() : "Transaction rejected by provider");
					});
		}
		catch (RuntimeException ex) {
			log.warn("Provider POST {} failed in {} ms — {}", EXECUTE_PATH, elapsedMs(startNanos), ex.getMessage());
			throw ex;
		}
	}

	private void tagActiveSpan(ProviderExecuteRequest request) {
		Span span = tracer.currentSpan();
		if (span == null) {
			return;
		}
		span.tag("provider.account_id", request.accountId());
		span.tag("provider.type", request.type().name());
		span.tag("provider.amount", request.amount().toPlainString());
		span.tag("provider.currency", request.currency());
	}

	private record ProviderExecuteBody(
			String accountId,
			String type,
			BigDecimal amount,
			String currency) {
	}

	private record ProviderExecuteResponse(
			String transactionId,
			String status,
			BigDecimal balance,
			Instant executedAt,
			String code,
			String message) {
	}

	private static long elapsedMs(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}

}
