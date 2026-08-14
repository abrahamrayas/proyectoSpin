package com.arayas.transaction;

final class DockerTestSupport {

	private DockerTestSupport() {
	}

	static boolean dockerAvailable() {
		try {
			return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
		}
		catch (RuntimeException ex) {
			return false;
		}
	}

}
