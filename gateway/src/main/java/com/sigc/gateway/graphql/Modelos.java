package com.sigc.gateway.graphql;

import java.io.Serializable;
import java.util.List;

/** Tipos del schema GraphQL (adaptación de las respuestas de la API REST al contrato del cliente). */
public final class Modelos {

	private Modelos() {}

	public record Usuario(String documento, List<String> roles) {}

	public record AuthPayload(String token, String documento, List<String> roles, String mensaje) {}

	public record Solicitud(String id, String titulo, String descripcion, String nombreCompleto, String correo,
			String documento, String ciudad, String telefono, String tipo, String estado, String creadaEn,
			String fechaVencimiento, Boolean correoEnviado) {}

	public record EventoHistorial(String id, String estadoAnterior, String estadoNuevo, String actor, String eventId,
			String observaciones, String cambiadoEn, String registradoEn) {}

	public record PaginaSolicitudes(List<Solicitud> contenido, int totalElementos, int totalPaginas,
			int paginaActual) {}

	public record ResumenIndicadores(int totalSolicitudes, int solicitudesAbiertas, int solicitudesCerradas,
			int eventosProcesados, int notificaciones, int mensajesDlt, String generadoEn, boolean desactualizado)
			implements Serializable {

		public ResumenIndicadores comoDesactualizado() {
			return new ResumenIndicadores(totalSolicitudes, solicitudesAbiertas, solicitudesCerradas,
					eventosProcesados, notificaciones, mensajesDlt, generadoEn, true);
		}
	}

	public record VolumenEstado(String estado, int cantidad) implements Serializable {}

	public record FiltroSolicitudes(String estado, String tipo, String busqueda) {}

	public record AdjuntoInput(String nombre, String contenidoBase64, String tipoMime) {}

	public record RadicarSolicitudInput(String titulo, String descripcion, String nombreCompleto, String correo,
			String documento, String ciudad, String telefono, String tipo, Boolean aceptaPoliticaDatos,
			String fechaVencimiento, List<AdjuntoInput> adjuntos) {}

	/** Enum de la API usa "PETICIÓN" (con tilde), no válido como nombre de enum GraphQL. */
	public static String tipoDesdeApi(String apiValue) {
		if (apiValue == null) {
			return null;
		}
		return apiValue.startsWith("PETICI") ? "PETICION" : apiValue;
	}

	public static String tipoHaciaApi(String graphqlValue) {
		if (graphqlValue == null) {
			return null;
		}
		return "PETICION".equals(graphqlValue) ? "PETICIÓN" : graphqlValue;
	}
}
