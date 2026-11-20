package com.sigc.gateway.security;

import java.util.List;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Acceso a la identidad confiable obtenida del JWT validado. */
public final class CurrentUser {

	private CurrentUser() {}

	public static Authentication authentication() {
		Authentication a = SecurityContextHolder.getContext().getAuthentication();
		if (a == null || a instanceof AnonymousAuthenticationToken || !a.isAuthenticated()) {
			return null;
		}
		return a;
	}

	public static boolean isAuthenticated() {
		return authentication() != null;
	}

	/** Token original, para propagarlo a la API (que lo vuelve a validar). */
	public static String bearerToken() {
		Authentication a = authentication();
		return (a != null && a.getCredentials() instanceof String s) ? s : null;
	}

	public static String documento() {
		Authentication a = authentication();
		return a != null ? a.getName() : null;
	}

	public static List<String> roles() {
		Authentication a = authentication();
		if (a == null) {
			return List.of();
		}
		return a.getAuthorities().stream().map(g -> g.getAuthority().replaceFirst("^ROLE_", "")).toList();
	}
}
