package com.backend.sigcapi.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backend.sigcapi.dto.ChangePasswordDTO;
import com.backend.sigcapi.dto.PageResponseDTO;
import com.backend.sigcapi.dto.ProfileDTO;
import com.backend.sigcapi.dto.UserDTO;
import com.backend.sigcapi.entities.User;
import com.backend.sigcapi.enums.UserStatus;
import com.backend.sigcapi.exceptions.InvalidPasswordException;
import com.backend.sigcapi.services.IUserService;
import com.backend.sigcapi.util.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin/users")
public class UserController {

	private final IUserService service;

	public UserController(IUserService service) {
		this.service = service;
	}

	@GetMapping("/all")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<PageResponseDTO<User>>> all(
			@RequestParam(name = "status", required = false) UserStatus status,
			@RequestParam(name = "rol", required = false) String rol,
			@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "10") int size) {

		Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("createdAt").descending());

		Page<User> usersPage = service.all(status, rol, from, to, pageable);

		if (usersPage.isEmpty()) {
			return ResponseEntity.ok(new ApiResponse<>(true, "No hay usuarios registrados actualmente."));
		}

		PageResponseDTO<User> data = new PageResponseDTO<>(
				usersPage.getContent(),
				usersPage.getTotalElements(),
				usersPage.getTotalPages(),
				usersPage.getNumber());

		return ResponseEntity.ok(new ApiResponse<>(true, "Lista de usuarios obtenida con éxito.", data));
	}

	@GetMapping("/findById/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<User>> getById(@PathVariable Long id) {
		User user = service.findById(id);
		return ResponseEntity.ok(new ApiResponse<>(true, "Usuario encontrado.", user));
	}

	@GetMapping("/profile")
	public ResponseEntity<ApiResponse<User>> getProfile() {
		User user = service.getAuthenticatedUser();
		return ResponseEntity.ok(new ApiResponse<>(true, "Perfil obtenido.", user));
	}

	@PostMapping("/save")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseEntity<ApiResponse<User>> save(@Valid @RequestBody UserDTO usuario) {
		User user = service.save(usuario);
		return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, "Usuario registrado.", user));
	}

	@PutMapping("/update/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<User>> update(@PathVariable Long id, @Valid @RequestBody UserDTO usuariodto) {
		User user = service.update(id, usuariodto);
		return ResponseEntity.ok(new ApiResponse<>(true, "Usuario actualizado.", user));
	}

	@PutMapping("/profile")
	public ResponseEntity<ApiResponse<User>> updateProfile(@Valid @RequestBody ProfileDTO profileDto) {
		User user = service.getAuthenticatedUser();

		User userUpdate = service.updateProfile(user.getId(), profileDto);

		return ResponseEntity.ok(new ApiResponse<>(true, "Perfil actualizado con éxito", userUpdate));
	}

	@PostMapping("/change-password")
	public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordDTO changePasswordDTO) {

		if (!changePasswordDTO.newPassword().equals(changePasswordDTO.confirmPassword())) { throw new InvalidPasswordException("La nueva contraseña y su confirmación no coinciden"); }

		User currentUser = service.getAuthenticatedUser();

		service.changePassword(currentUser.getId(), changePasswordDTO.currentPassword(), changePasswordDTO.newPassword());

		return ResponseEntity.ok(new ApiResponse<>(true, "Contraseña actualizada exitosamente"));
	}

	@DeleteMapping("/delete/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(code = HttpStatus.NO_CONTENT)
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

}
