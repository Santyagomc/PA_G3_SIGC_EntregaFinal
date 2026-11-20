package com.sigc.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.sigc.gateway.config.ResilienceConfig;
import com.sigc.gateway.graphql.Modelos.PaginaSolicitudes;
import com.sigc.gateway.graphql.SolicitudesService;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * Pruebas del cliente hacia la API REST: retry solo en consultas, sin retry en mutations, apertura
 * del circuit breaker, 4xx que no abren el circuito, propagación de encabezados y contrato de las
 * respuestas de la API (formato {exito, mensaje, data} y PageResponseDTO).
 */
class ApiClientResilienceTest {

	MockRestServiceServer server;
	ApiClient client;
	CircuitBreaker cb;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://api:8080");
		server = MockRestServiceServer.bindTo(builder).build();
		ResilienceConfig cfg = new ResilienceConfig();
		cb = cfg.apiCircuitBreaker(50, 10, 5, 60);
		client = new ApiClient(builder.build(), cb, cfg.apiRetry(3, 1));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
		MDC.clear();
	}

	@Test
	void consultaConError5xxSeReintentaTresVeces() {
		server.expect(times(3), requestTo("http://api:8080/events/summary")).andRespond(withServerError());
		assertThatThrownBy(() -> client.get("/events/summary", Map.of()))
				.isInstanceOf(ApiClientException.class);
		server.verify();
	}

	@Test
	void consultaSeRecuperaEnElSegundoIntento() {
		server.expect(requestTo("http://api:8080/events/summary")).andRespond(withServerError());
		server.expect(requestTo("http://api:8080/events/summary"))
				.andRespond(withSuccess("{\"exito\":true,\"data\":{\"notificaciones\":4}}", MediaType.APPLICATION_JSON));
		assertThat(client.get("/events/summary", Map.of()).at("/data/notificaciones").asInt()).isEqualTo(4);
		server.verify();
	}

	@Test
	void mutationNoSeReintenta() {
		server.expect(times(1), requestTo("http://api:8080/requests/7/status?status=CERRADO"))
				.andExpect(method(HttpMethod.PUT)).andRespond(withServerError());
		assertThatThrownBy(() -> client.put("/requests/7/status", Map.of("status", "CERRADO")))
				.isInstanceOf(ApiClientException.class);
		server.verify();
	}

	@Test
	void errores4xxNoReintentanNiAbrenElCircuito() {
		server.expect(times(6), requestTo("http://api:8080/requests/findById/99")).andRespond(
				withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
						.body("{\"exito\":false,\"mensaje\":\"La solicitud no existe.\"}"));
		for (int i = 0; i < 6; i++) {
			assertThatThrownBy(() -> client.get("/requests/findById/99", Map.of()))
					.isInstanceOfSatisfying(ApiClientException.class, e -> {
						assertThat(e.getStatus()).isEqualTo(404);
						assertThat(e.getMessage()).isEqualTo("La solicitud no existe.");
					});
		}
		assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
		server.verify();
	}

	@Test
	void circuitBreakerSeAbreYFallaRapido() {
		server.expect(times(5), requestTo("http://api:8080/requests/1/status?status=CERRADO"))
				.andRespond(withServerError());
		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> client.put("/requests/1/status", Map.of("status", "CERRADO")))
					.isInstanceOf(ApiClientException.class);
		}
		assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
		long start = System.currentTimeMillis();
		assertThatThrownBy(() -> client.get("/events/summary", Map.of()))
				.isInstanceOfSatisfying(ApiUnavailableException.class, e -> assertThat(e.isCircuitOpen()).isTrue());
		assertThat(System.currentTimeMillis() - start).isLessThan(200);
		server.verify(); // la sexta llamada nunca llegó a la API
	}

	@Test
	void propagaSoloAuthorizationYCorrelationId() {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				"1000000001", "token-abc", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
		MDC.put("correlationId", "corr-12345678");
		server.expect(requestTo("http://api:8080/events/summary"))
				.andExpect(header("Authorization", "Bearer token-abc"))
				.andExpect(header("X-Correlation-Id", "corr-12345678"))
				.andRespond(withSuccess("{\"exito\":true}", MediaType.APPLICATION_JSON));
		client.get("/events/summary", Map.of());
		server.verify();
	}

	/** Contrato con la API: GET /requests/all devuelve ApiResponse<PageResponseDTO<Request>>. */
	@Test
	void contratoPaginaDeSolicitudesDeLaApi() {
		String apiJson = """
				{"exito":true,"mensaje":"Lista de solicitudes obtenida con éxito.","data":{
				 "content":[{"id":12,"title":"Cobro indebido","description":"d","fullName":"Ana Pérez",
				 "email":"ana@example.com","document":"1020304050","city":"Neiva","phone":"3000000000",
				 "type":"PETICIÓN","createdAt":"2026-11-19T20:10:00","status":"CREADO",
				 "acceptedDataPolicy":true,"mailStatus":false,"expirationDate":"2026-12-10"}],
				 "totalElements":1,"totalPages":1,"currentPage":0}}""";
		server.expect(requestTo("http://api:8080/requests/all?page=0&size=10&status=CREADO"))
				.andRespond(withSuccess(apiJson, MediaType.APPLICATION_JSON));
		PaginaSolicitudes p = new SolicitudesService(client).solicitudes(
				new com.sigc.gateway.graphql.Modelos.FiltroSolicitudes("CREADO", null, null), 0, 10);
		assertThat(p.totalElementos()).isEqualTo(1);
		assertThat(p.contenido().get(0).tipo()).isEqualTo("PETICION");
		assertThat(p.contenido().get(0).estado()).isEqualTo("CREADO");
		assertThat(p.contenido().get(0).titulo()).isEqualTo("Cobro indebido");
	}

	/** Cuando no hay datos la API responde sin "data": el gateway devuelve una página vacía. */
	@Test
	void contratoListaVaciaSinData() {
		server.expect(requestTo("http://api:8080/requests/my?page=0&size=10"))
				.andRespond(withSuccess("{\"exito\":true,\"mensaje\":\"No hay solicitudes para mostrar.\"}",
						MediaType.APPLICATION_JSON));
		PaginaSolicitudes p = new SolicitudesService(client).misSolicitudes(0, 10);
		assertThat(p.contenido()).isEmpty();
		assertThat(p.totalElementos()).isZero();
	}
}
