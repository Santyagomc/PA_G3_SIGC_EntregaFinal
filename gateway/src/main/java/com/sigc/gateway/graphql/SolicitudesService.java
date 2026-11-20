package com.sigc.gateway.graphql;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigc.gateway.client.ApiClient;
import com.sigc.gateway.graphql.Modelos.AdjuntoInput;
import com.sigc.gateway.graphql.Modelos.AuthPayload;
import com.sigc.gateway.graphql.Modelos.EventoHistorial;
import com.sigc.gateway.graphql.Modelos.FiltroSolicitudes;
import com.sigc.gateway.graphql.Modelos.PaginaSolicitudes;
import com.sigc.gateway.graphql.Modelos.RadicarSolicitudInput;
import com.sigc.gateway.graphql.Modelos.Solicitud;

/**
 * Adaptación entre el contrato GraphQL y la API REST. No contiene reglas de negocio: las validaciones,
 * permisos finales y la emisión de eventos Kafka permanecen en la API; aquí solo se delega,
 * se compone y se transforman los datos al vocabulario del cliente.
 */
@Service
public class SolicitudesService {

	private final ApiClient api;
	private final ObjectMapper mapper = new ObjectMapper();

	public SolicitudesService(ApiClient api) {
		this.api = api;
	}

	public AuthPayload login(String documento, String password) {
		JsonNode res = api.postJson("/auth/login", Map.of("username", documento, "password", password));
		List<String> roles = new ArrayList<>();
		res.path("roles").forEach(r -> roles.add(r.asText().replaceFirst("^ROLE_", "")));
		return new AuthPayload(res.path("token").asText(), res.path("usuario").asText(documento), roles,
				res.path("mensaje").asText(null));
	}

	public PaginaSolicitudes misSolicitudes(int page, int size) {
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("page", page);
		params.put("size", size);
		return toPagina(api.get("/requests/my", params), page);
	}

	public PaginaSolicitudes solicitudes(FiltroSolicitudes filtro, int page, int size) {
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("page", page);
		params.put("size", size);
		if (filtro != null) {
			params.put("status", filtro.estado());
			params.put("type", Modelos.tipoHaciaApi(filtro.tipo()));
			params.put("search", filtro.busqueda() != null && !filtro.busqueda().isBlank() ? filtro.busqueda() : null);
		}
		return toPagina(api.get("/requests/all", params), page);
	}

	public Solicitud solicitud(String id) {
		return toSolicitud(api.get("/requests/findById/" + Long.parseLong(id), Map.of()).path("data"));
	}

	public List<EventoHistorial> historial(String requestId) {
		JsonNode data = api.get("/requests/" + Long.parseLong(requestId) + "/history", Map.of("size", 100))
				.path("data");
		List<EventoHistorial> list = new ArrayList<>();
		data.path("content").forEach(h -> list.add(new EventoHistorial(
				h.path("id").asText(),
				textOrNull(h, "statusOld"),
				textOrNull(h, "statusNew"),
				// Solo el documento del actor: nunca se expone el resto del usuario (p. ej. hash de contraseña).
				h.path("user").isObject() ? textOrNull(h.path("user"), "document") : null,
				textOrNull(h, "eventId"),
				textOrNull(h, "observations"),
				textOrNull(h, "changedAt"),
				textOrNull(h, "recordedAt"))));
		return list;
	}

	public Solicitud cambiarEstado(String id, String estado, String observaciones) {
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("status", estado);
		params.put("observations", observaciones);
		return toSolicitud(api.put("/requests/" + Long.parseLong(id) + "/status", params).path("data"));
	}

	public Solicitud radicar(RadicarSolicitudInput in) {
		if (in.adjuntos() == null || in.adjuntos().isEmpty()) {
			throw new IllegalArgumentException("Debe adjuntar al menos un documento soporte a la solicitud.");
		}
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("title", in.titulo());
		request.put("description", in.descripcion());
		request.put("fullName", in.nombreCompleto());
		request.put("email", in.correo());
		request.put("document", in.documento());
		request.put("city", in.ciudad());
		request.put("phone", in.telefono());
		request.put("type", Modelos.tipoHaciaApi(in.tipo()));
		request.put("acceptedDataPolicy", in.aceptaPoliticaDatos());
		request.put("expirationDate", in.fechaVencimiento());

		MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
		HttpHeaders jsonHeaders = new HttpHeaders();
		jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
		parts.add("request", new HttpEntity<>(toJson(request), jsonHeaders));
		for (AdjuntoInput a : in.adjuntos()) {
			byte[] bytes;
			try {
				bytes = Base64.getDecoder().decode(a.contenidoBase64());
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("El adjunto '" + a.nombre() + "' no es Base64 válido.");
			}
			HttpHeaders fileHeaders = new HttpHeaders();
			fileHeaders.setContentType(a.tipoMime() != null ? MediaType.parseMediaType(a.tipoMime())
					: MediaType.APPLICATION_OCTET_STREAM);
			parts.add("files", new HttpEntity<>(new NamedResource(bytes, a.nombre()), fileHeaders));
		}
		return toSolicitud(api.postMultipart("/requests/save", parts).path("data"));
	}

	PaginaSolicitudes toPagina(JsonNode res, int page) {
		JsonNode data = res.path("data");
		List<Solicitud> contenido = new ArrayList<>();
		data.path("content").forEach(n -> contenido.add(toSolicitud(n)));
		return new PaginaSolicitudes(contenido, data.path("totalElements").asInt(0),
				data.path("totalPages").asInt(0), data.path("currentPage").asInt(page));
	}

	static Solicitud toSolicitud(JsonNode n) {
		return new Solicitud(n.path("id").asText(), textOrNull(n, "title"), textOrNull(n, "description"),
				textOrNull(n, "fullName"), textOrNull(n, "email"), textOrNull(n, "document"), textOrNull(n, "city"),
				textOrNull(n, "phone"), Modelos.tipoDesdeApi(textOrNull(n, "type")), textOrNull(n, "status"),
				textOrNull(n, "createdAt"), textOrNull(n, "expirationDate"),
				n.hasNonNull("mailStatus") ? n.path("mailStatus").asBoolean() : null);
	}

	static String textOrNull(JsonNode n, String field) {
		JsonNode v = n.path(field);
		if (v.isMissingNode() || v.isNull()) {
			return null;
		}
		if (v.isArray()) {
			// Fechas serializadas como arreglo [aaaa, mm, dd, hh, mi, ss]
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < v.size(); i++) {
				if (i == 1 || i == 2) {
					sb.append('-');
				} else if (i == 3) {
					sb.append('T');
				} else if (i > 3 && i < 6) {
					sb.append(':');
				} else if (i >= 6) {
					break;
				}
				sb.append(String.format(i == 0 ? "%d" : "%02d", v.get(i).asInt()));
			}
			return sb.toString();
		}
		return v.asText();
	}

	private String toJson(Object o) {
		try {
			return mapper.writeValueAsString(o);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/** Recurso en memoria con nombre de archivo (necesario para que la API reciba un MultipartFile). */
	static class NamedResource extends ByteArrayResource {
		private final String filename;

		NamedResource(byte[] bytes, String filename) {
			super(bytes);
			this.filename = filename;
		}

		@Override
		public String getFilename() {
			return filename;
		}
	}
}
