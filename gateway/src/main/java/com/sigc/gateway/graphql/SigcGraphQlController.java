package com.sigc.gateway.graphql;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import com.sigc.gateway.graphql.Modelos.AuthPayload;
import com.sigc.gateway.graphql.Modelos.EventoHistorial;
import com.sigc.gateway.graphql.Modelos.FiltroSolicitudes;
import com.sigc.gateway.graphql.Modelos.PaginaSolicitudes;
import com.sigc.gateway.graphql.Modelos.RadicarSolicitudInput;
import com.sigc.gateway.graphql.Modelos.ResumenIndicadores;
import com.sigc.gateway.graphql.Modelos.Solicitud;
import com.sigc.gateway.graphql.Modelos.Usuario;
import com.sigc.gateway.graphql.Modelos.VolumenEstado;
import com.sigc.gateway.security.CurrentUser;

/**
 * Resolvers GraphQL. Cada uno se relaciona con un servicio interno de la API REST (ver comentarios)
 * y aplica la regla de acceso de la operación. No contienen lógica de negocio.
 */
@Controller
public class SigcGraphQlController {

	private final SolicitudesService solicitudes;
	private final IndicadoresService indicadores;

	public SigcGraphQlController(SolicitudesService solicitudes, IndicadoresService indicadores) {
		this.solicitudes = solicitudes;
		this.indicadores = indicadores;
	}

	/** Identidad del JWT validado (no llama a la API). */
	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public Usuario me() {
		return new Usuario(CurrentUser.documento(), CurrentUser.roles());
	}

	/** GET /requests/my */
	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public PaginaSolicitudes misSolicitudes(@Argument int page, @Argument int size) {
		return solicitudes.misSolicitudes(page, size);
	}

	/** GET /requests/all */
	@QueryMapping
	@PreAuthorize("hasRole('ADMIN')")
	public PaginaSolicitudes solicitudes(@Argument FiltroSolicitudes filtro, @Argument int page, @Argument int size) {
		return solicitudes.solicitudes(filtro, page, size);
	}

	/** GET /requests/findById/{id} */
	@QueryMapping
	@PreAuthorize("hasRole('ADMIN')")
	public Solicitud solicitud(@Argument String id) {
		return solicitudes.solicitud(id);
	}

	/** Composición: GET /requests/{id}/history (escrito por el consumidor Kafka sigc-audit). */
	@SchemaMapping(typeName = "Solicitud", field = "historial")
	@PreAuthorize("hasRole('ADMIN')")
	public List<EventoHistorial> historial(Solicitud solicitud) {
		return solicitudes.historial(solicitud.id());
	}

	/** Composición: GET /requests/all (conteo por estado) + GET /events/summary. Caché Redis. */
	@QueryMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResumenIndicadores resumenIndicadores() {
		try {
			return indicadores.resumen();
		} catch (IndicadoresService.RespaldoDisponible r) {
			return r.getValor();
		}
	}

	/** GET /requests/all?status=... (conteos). Caché Redis. */
	@QueryMapping
	@PreAuthorize("hasRole('ADMIN')")
	public List<VolumenEstado> volumenPorEstado() {
		return indicadores.volumenPorEstado();
	}

	/** Público: POST /auth/login */
	@MutationMapping
	public AuthPayload login(@Argument String documento, @Argument String password) {
		return solicitudes.login(documento, password);
	}

	/** POST /requests/save (multipart). La API emite REQUEST_CREATED (Kafka). */
	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	public Solicitud radicarSolicitud(@Argument RadicarSolicitudInput input) {
		return solicitudes.radicar(input);
	}

	/** PUT /requests/{id}/status. La API emite REQUEST_STATUS_CHANGED (Kafka). */
	@MutationMapping
	@PreAuthorize("hasRole('ADMIN')")
	public Solicitud cambiarEstadoSolicitud(@Argument String id, @Argument String estado,
			@Argument String observaciones) {
		return solicitudes.cambiarEstado(id, estado, observaciones);
	}
}
