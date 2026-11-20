package com.sigc.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Prueba de contrato: el schema que expone el gateway debe ser idéntico al contrato publicado en
 * contracts/schema.graphql (documentación entregada a los consumidores).
 */
class SchemaContractTest {

	@Test
	void schemaDelGatewayCoincideConElContrato() throws Exception {
		Path contrato = Path.of("..", "contracts", "schema.graphql");
		assumeTrue(Files.exists(contrato), "contracts/schema.graphql no está disponible (build aislado)");
		String implementado = Files.readString(Path.of("src", "main", "resources", "graphql", "schema.graphqls"));
		assertThat(normalize(Files.readString(contrato))).isEqualTo(normalize(implementado));
	}

	private static String normalize(String s) {
		return s.replace("\r\n", "\n").strip();
	}
}
