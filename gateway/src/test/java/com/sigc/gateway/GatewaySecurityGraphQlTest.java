package com.sigc.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigc.gateway.client.ApiUnavailableException;
import com.sigc.gateway.graphql.IndicadoresService;
import com.sigc.gateway.graphql.Modelos.AuthPayload;
import com.sigc.gateway.graphql.Modelos.ResumenIndicadores;
import com.sigc.gateway.graphql.SolicitudesService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

/**
 * Pruebas de integración del gateway (HTTP real sobre puerto aleatorio): seguridad por operación
 * GraphQL (acceso permitido y denegado), CORS, correlation id y manejo de errores controlado.
 * Los servicios que llaman a la API REST se sustituyen por mocks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewaySecurityGraphQlTest {

	static final String SECRET = "test-secret-key-for-the-sigc-gateway-with-32-chars-min";

	@Autowired
	Environment env;

	@MockitoBean
	SolicitudesService solicitudes;

	@MockitoBean
	IndicadoresService indicadores;

	final ObjectMapper mapper = new ObjectMapper();

	static String token(String documento, String rol, String secret, long ttlMs) {
		return Jwts.builder().setSubject(documento).claim("roles", List.of(rol))
				.setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + ttlMs))
				.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
				.compact();
	}

	record Resp(int status, JsonNode body, String correlationId) {}


	Resp postRaw(String query, String bearer) throws Exception {
		RestClient client = RestClient.create("http://localhost:" + env.getProperty("local.server.port"));
		var spec = client.post().uri("/graphql").contentType(MediaType.APPLICATION_JSON);
		if (bearer != null) {
			spec = spec.header("Authorization", "Bearer " + bearer);
		}
		return spec.body(mapper.writeValueAsString(Map.of("query", query))).exchange((req, res) -> {
			byte[] bytes = res.getBody().readAllBytes();
			return new Resp(res.getStatusCode().value(), mapper.readTree(bytes.length == 0 ? "{}".getBytes() : bytes),
					res.getHeaders().getFirst("X-Correlation-Id"));
		});
	}

	static final ResumenIndicadores RESUMEN = new ResumenIndicadores(5, 3, 2, 10, 5, 1, "2026-11-19T21:00:00-05:00", false);
	static final String Q_RESUMEN = "{ resumenIndicadores { totalSolicitudes solicitudesAbiertas desactualizado } }";

	@Test
	void consultaProtegidaSinTokenDevuelveUnauthorized() throws Exception {
		Resp r = postRaw(Q_RESUMEN, null);
		assertThat(r.status()).isEqualTo(200);
		assertThat(r.body().at("/errors/0/extensions/codigo").asText()).isEqualTo("UNAUTHORIZED");
		assertThat(r.body().at("/errors/0/extensions/correlationId").asText()).isNotBlank();
		assertThat(r.correlationId()).isNotBlank();
	}

	@Test
	void tokenConFirmaInvalidaSeRechaza() throws Exception {
		String forged = token("1000000001", "ADMIN", "otra-clave-secreta-distinta-de-al-menos-32-caracteres", 60_000);
		Resp r = postRaw(Q_RESUMEN, forged);
		assertThat(r.body().at("/errors/0/extensions/codigo").asText()).isEqualTo("UNAUTHORIZED");
	}

	@Test
	void tokenExpiradoSeRechaza() throws Exception {
		Resp r = postRaw(Q_RESUMEN, token("1000000001", "ADMIN", SECRET, -1000));
		assertThat(r.body().at("/errors/0/extensions/codigo").asText()).isEqualTo("UNAUTHORIZED");
	}

	@Test
	void consultorEnConsultaDeAdminDevuelveForbidden() throws Exception {
		Resp r = postRaw(Q_RESUMEN, token("1020304050", "CONSULTOR", SECRET, 60_000));
		assertThat(r.body().at("/errors/0/extensions/codigo").asText()).isEqualTo("FORBIDDEN");
	}

	@Test
	void adminObtieneIndicadores() throws Exception {
		when(indicadores.resumen()).thenReturn(RESUMEN);
		Resp r = postRaw(Q_RESUMEN, token("1000000001", "ADMIN", SECRET, 60_000));
		assertThat(r.body().has("errors")).isFalse();
		assertThat(r.body().at("/data/resumenIndicadores/totalSolicitudes").asInt()).isEqualTo(5);
		assertThat(r.body().at("/data/resumenIndicadores/solicitudesAbiertas").asInt()).isEqualTo(3);
	}

	@Test
	void meDevuelveIdentidadDelToken() throws Exception {
		Resp r = postRaw("{ me { documento roles } }", token("1020304050", "CONSULTOR", SECRET, 60_000));
		assertThat(r.body().at("/data/me/documento").asText()).isEqualTo("1020304050");
		assertThat(r.body().at("/data/me/roles/0").asText()).isEqualTo("CONSULTOR");
	}

	@Test
	void loginEsPublico() throws Exception {
		when(solicitudes.login(anyString(), anyString()))
				.thenReturn(new AuthPayload("jwt.token.value", "1000000001", List.of("ADMIN"), "ok"));
		Resp r = postRaw("mutation { login(documento: \"1000000001\", password: \"x\") { token roles } }", null);
		assertThat(r.body().at("/data/login/token").asText()).isEqualTo("jwt.token.value");
	}

	@Test
	void apiCaidaSeTraduceEnUnavailableControlado() throws Exception {
		when(indicadores.volumenPorEstado()).thenThrow(new ApiUnavailableException("caida", null, true));
		Resp r = postRaw("{ volumenPorEstado { estado cantidad } }", token("1000000001", "ADMIN", SECRET, 60_000));
		assertThat(r.body().at("/errors/0/extensions/codigo").asText()).isEqualTo("UNAVAILABLE");
		assertThat(r.body().at("/errors/0/message").asText()).doesNotContain("Exception");
	}

	@Test
	void corsSoloPermiteOrigenesConfigurados() throws Exception {
		RestClient client = RestClient.create("http://localhost:" + env.getProperty("local.server.port"));
		HttpStatusCode permitido = client.options().uri("/graphql").header("Origin", "http://localhost:4200")
				.header("Access-Control-Request-Method", "POST").exchange((req, res) -> res.getStatusCode());
		HttpStatusCode denegado = client.options().uri("/graphql").header("Origin", "http://evil.example.com")
				.header("Access-Control-Request-Method", "POST").exchange((req, res) -> res.getStatusCode());
		assertThat(permitido.value()).isEqualTo(200);
		assertThat(denegado.value()).isEqualTo(403);
	}

	@Test
	void correlationIdRecibidoSeDevuelve() throws Exception {
		RestClient client = RestClient.create("http://localhost:" + env.getProperty("local.server.port"));
		String header = client.post().uri("/graphql").contentType(MediaType.APPLICATION_JSON)
				.header("X-Correlation-Id", "demo-correlation-0001")
				.body("{\"query\":\"{ __typename }\"}")
				.exchange((req, res) -> res.getHeaders().getFirst("X-Correlation-Id"));
		assertThat(header).isEqualTo("demo-correlation-0001");
	}
}
