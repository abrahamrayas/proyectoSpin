package com.arayas.transaction.application.port;

import com.arayas.transaction.application.model.ProviderExecuteRequest;
import com.arayas.transaction.application.model.ProviderExecuteResult;

public interface TransactionProviderPort {

	ProviderExecuteResult execute(ProviderExecuteRequest request);

}
