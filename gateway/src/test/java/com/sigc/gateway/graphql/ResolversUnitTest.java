package com.sigc.gateway.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigc.gateway.client.ApiClient;
import com.sigc.gateway.client.ApiUnavailableException;
import com.sigc.gateway.graphql.Modelos.ResumenIndicadores;

/** Pruebas unitarias de los resolvers/servicios del gateway con la API simulada (sin red). */
class ResolversUnitTest {

	final ObjectMapper mapper = new ObjectMapper();
	final ApiClient api = mock(ApiClient.class);
	final UltimoValorStore store = mock(UltimoValorStore.class);

	JsonNode json(String s) throws Exception {
		return mapper.readTree(s);
	}

	@Test
	void resumenComponeConteosPorEstadoYResumenDeEventos() throws Exception {
		when(api.get(eq("/requests/all"), anyMap())).thenAnswer(inv -> {
			Map<?, ?> p = inv.getArgument(1);
			int n = switch (String.valueOf(p.get("status"))) {
				case "CREADO" -> 4;
				case "FALTA_DOCUMENTACION" -> 1;
				default -> 2;
			};
			return json("{\"data\":{\"totalElements\":" + n + "}}");
		});
		when(api.get(eq("/events/summary"), anyMap())).thenReturn(json(
				"{\"data\":{\"eventosProcesadosPorConsumidor\":{\"sigc-audit\":7,\"sigc-notifications\":7},"
						+ "\"notificaciones\":6,\"mensajesEnDLT\":3}}"));

		ResumenIndicadores r = new IndicadoresService(api, store).resumen();

		assertThat(r.totalSolicitudes()).isEqualTo(7);
		assertThat(r.solicitudesAbiertas()).isEqualTo(5);
		assertThat(r.solicitudesCerradas()).isEqualTo(2);
		assertThat(r.eventosProcesados()).isEqualTo(14);
		assertThat(r.notificaciones()).isEqualTo(6);
		assertThat(r.mensajesDlt()).isEqualTo(3);
		assertThat(r.desactualizado()).isFalse();
		verify(store).guardar(r);
	}

	@Test
	void resumenUsaRespaldoDesactualizadoSiLaApiNoEstaDisponible() {
		ResumenIndicadores ultimo = new ResumenIndicadores(1, 1, 0, 2, 1, 0, "2026-11-19T20:00:00-05:00", false);
		when(api.get(any(), anyMap())).thenThrow(new ApiUnavailableException("caida", null, true));
		when(store.leer()).thenReturn(ultimo);
		IndicadoresService service = new IndicadoresService(api, store);

		assertThatThrownBy(service::resumen).isInstanceOfSatisfying(IndicadoresService.RespaldoDisponible.class,
				e -> assertThat(e.getValor().desactualizado()).isTrue());
	}

	@Test
	void historialSoloExponeElDocumentoDelActor() throws Exception {
		when(api.get(eq("/requests/5/history"), anyMap())).thenReturn(json("""
				{"data":{"content":[{"id":1,"statusOld":null,"statusNew":"CREADO","eventId":"e-1",
				"user":{"document":"1000000001","password":"$2a$12$hash","email":"x@y.z"},
				"changedAt":"2026-11-19T20:00:00"}]}}"""));
		List<Modelos.EventoHistorial> h = new SolicitudesService(api).historial("5");
		assertThat(h).hasSize(1);
		assertThat(h.get(0).actor()).isEqualTo("1000000001");
		assertThat(h.get(0).estadoAnterior()).isNull();
		assertThat(h.get(0).toString()).doesNotContain("hash");
	}

	@Test
	void radicarSinAdjuntosSeRechazaSinLlamarALaApi() {
		var input = new Modelos.RadicarSolicitudInput("t", "d", "n", "c@x.co", "1", "Neiva", "300", "QUEJA", true,
				"2026-12-01", List.of());
		assertThatThrownBy(() -> new SolicitudesService(api).radicar(input))
				.isInstanceOf(IllegalArgumentException.class);
		verify(api, never()).postMultipart(any(), any());
	}

	@Test
	void tipoPeticionSeTraduceEntreGraphQlYApi() {
		assertThat(Modelos.tipoHaciaApi("PETICION")).isEqualTo("PETICIÓN");
		assertThat(Modelos.tipoDesdeApi("PETICIÓN")).isEqualTo("PETICION");
		assertThat(Modelos.tipoDesdeApi("QUEJA")).isEqualTo("QUEJA");
	}
}
