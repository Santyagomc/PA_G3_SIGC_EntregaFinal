package com.sigc.gateway.graphql;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sigc.gateway.client.ApiClient;
import com.sigc.gateway.client.ApiUnavailableException;
import com.sigc.gateway.graphql.Modelos.ResumenIndicadores;
import com.sigc.gateway.graphql.Modelos.VolumenEstado;

/**
 * Indicadores del tablero (lectura frecuente, mismos datos para todos los administradores):
 * se cachean 30 s en Redis (@Cacheable) y el último valor calculado se guarda además como respaldo
 * para responder (marcado desactualizado=true) si la API REST no está disponible.
 */
@Service
public class IndicadoresService {

	private static final Logger log = LoggerFactory.getLogger(IndicadoresService.class);
	static final List<String> ESTADOS = List.of("CREADO", "FALTA_DOCUMENTACION", "CERRADO");

	private final ApiClient api;
	private final UltimoValorStore respaldo;

	public IndicadoresService(ApiClient api, UltimoValorStore respaldo) {
		this.api = api;
		this.respaldo = respaldo;
	}

	@Cacheable(cacheNames = "volumenPorEstado", key = "'global'")
	public List<VolumenEstado> volumenPorEstado() {
		return volumenSinCache();
	}

	@Cacheable(cacheNames = "resumenIndicadores", key = "'global'")
	public ResumenIndicadores resumen() {
		try {
			ResumenIndicadores r = calcular();
			respaldo.guardar(r);
			return r;
		} catch (ApiUnavailableException e) {
			ResumenIndicadores ultimo = respaldo.leer();
			if (ultimo != null) {
				log.warn("[RESILIENCIA] API no disponible: se responde el último resumen conocido ({})", ultimo.generadoEn());
				throw new RespaldoDisponible(ultimo.comoDesactualizado(), e);
			}
			throw e;
		}
	}

	private ResumenIndicadores calcular() {
		int creadas = 0;
		int faltaDoc = 0;
		int cerradas = 0;
		for (VolumenEstado v : volumenSinCache()) {
			switch (v.estado()) {
				case "CREADO" -> creadas = v.cantidad();
				case "FALTA_DOCUMENTACION" -> faltaDoc = v.cantidad();
				case "CERRADO" -> cerradas = v.cantidad();
				default -> { }
			}
		}
		JsonNode summary = api.get("/events/summary", Map.of()).path("data");
		int procesados = 0;
		var it = summary.path("eventosProcesadosPorConsumidor").elements();
		while (it.hasNext()) {
			procesados += it.next().asInt(0);
		}
		return new ResumenIndicadores(creadas + faltaDoc + cerradas, creadas + faltaDoc, cerradas, procesados,
				summary.path("notificaciones").asInt(0), summary.path("mensajesEnDLT").asInt(0),
				OffsetDateTime.now().toString(), false);
	}

	private List<VolumenEstado> volumenSinCache() {
		List<VolumenEstado> list = new ArrayList<>();
		for (String estado : ESTADOS) {
			JsonNode res = api.get("/requests/all", Map.of("status", estado, "page", 0, "size", 1));
			list.add(new VolumenEstado(estado, res.path("data").path("totalElements").asInt(0)));
		}
		return list;
	}

	/**
	 * Se lanza para no cachear el valor desactualizado (@Cacheable no guarda si hay excepción);
	 * el controlador lo convierte en la respuesta de respaldo.
	 */
	public static class RespaldoDisponible extends RuntimeException {
		private final transient ResumenIndicadores valor;

		RespaldoDisponible(ResumenIndicadores valor, Throwable cause) {
			super("Respaldo de indicadores", cause, false, false);
			this.valor = valor;
		}

		public ResumenIndicadores getValor() {
			return valor;
		}
	}
}
