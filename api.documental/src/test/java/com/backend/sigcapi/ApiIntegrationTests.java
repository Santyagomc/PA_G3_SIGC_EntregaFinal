package com.backend.sigcapi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Pruebas de integración de la API REST (autenticación, validaciones, códigos HTTP,
 * paginación y autorización). Usan el perfil "test" con H2 en memoria, por lo que no
 * requieren una instancia de MariaDB.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApiIntegrationTests {

    private static final String PASSWORD = "Password.123";
    // Administrador inicial creado al arrancar (ver src/test/resources/application.properties)
    private static final String ADMIN_DOCUMENT = "1000000000";
    private static final String ADMIN_PASSWORD = "Admin.12345";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ---------- utilidades ----------

    private String userJson(String email, String document) {
        return """
                {"name":"Santiago Murcia","email":"%s","document":"%s","password":"%s"}"""
                .formatted(email, document, PASSWORD);
    }

    private void register(String email, String document) throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(userJson(email, document)))
                .andExpect(status().isCreated());
    }

    private String loginToken(String document, String password) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(document, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertTrue(matcher.find(), "La respuesta del login debe incluir el token JWT");
        return matcher.group(1);
    }

    // ---------- registro y login ----------

    @Test
    void registroValidoDevuelve201() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                .content(userJson("registro@example.com", "2000000001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exito").value(true));
    }

    @Test
    void registroConCorreoInvalidoDevuelve400ConDetalleDeCampo() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                .content(userJson("no-es-un-correo", "2000000002")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false))
                .andExpect(jsonPath("$.data.email").exists());
    }

    @Test
    void registroConJsonMalFormadoDevuelve400() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));
    }

    @Test
    void loginValidoEntregaTokenYRoles() throws Exception {
        register("login@example.com", "2000000003");
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"2000000003\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    void loginConClaveIncorrectaDevuelve401() throws Exception {
        register("clave@example.com", "2000000004");
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"2000000004\",\"password\":\"Incorrecta.123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.exito").value(false));
    }

    @Test
    void loginConDocumentoInexistenteDevuelve401() throws Exception {
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"9999999999\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- el registro público nunca crea administradores ----------

    @Test
    void registroPublicoIgnoraAdminTrueYCreaCiudadano() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content("""
                {"name":"Intento Administrador","email":"intruso@example.com","document":"2000000010",
                 "password":"%s","admin":true,"status":"ACTIVO"}""".formatted(PASSWORD)))
                .andExpect(status().isCreated());

        String token = loginToken("2000000010", PASSWORD);

        mvc.perform(get("/requests/all").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminInicialDelArranquePuedeIniciarSesionYConsultarComoAdministrador() throws Exception {
        String token = loginToken(ADMIN_DOCUMENT, ADMIN_PASSWORD);

        mvc.perform(get("/requests/all").param("page", "0").param("size", "10")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true));
    }

    // ---------- rutas protegidas ----------

    @Test
    void rutaProtegidaSinTokenDevuelve401() throws Exception {
        mvc.perform(get("/requests/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.exito").value(false));
    }

    @Test
    void rutaProtegidaConTokenInvalidoDevuelve401() throws Exception {
        mvc.perform(get("/requests/my").header("Authorization", "Bearer token.invalido.123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioSinRolAdminRecibe403EnRutaDeAdministrador() throws Exception {
        register("ciudadano@example.com", "2000000005");
        String token = loginToken("2000000005", PASSWORD);

        mvc.perform(get("/requests/all").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.exito").value(false));
    }

    // ---------- solicitudes: consulta, paginación y errores ----------

    @Test
    void consultaPaginadaDeMisSolicitudesDevuelve200() throws Exception {
        register("paginada@example.com", "2000000006");
        String token = loginToken("2000000006", PASSWORD);

        mvc.perform(get("/requests/my").param("page", "0").param("size", "10")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true));
    }

    @Test
    void paginacionConValoresFueraDeRangoSeAjustaYNoFalla() throws Exception {
        register("rango@example.com", "2000000007");
        String token = loginToken("2000000007", PASSWORD);

        mvc.perform(get("/requests/my").param("page", "-1").param("size", "0")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void solicitudInexistenteDevuelve404() throws Exception {
        String token = loginToken(ADMIN_DOCUMENT, ADMIN_PASSWORD);

        mvc.perform(get("/requests/findById/999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.exito").value(false));
    }

    @Test
    void radicarPqrConCamposInvalidosDevuelve400() throws Exception {
        register("radica@example.com", "2000000009");
        String token = loginToken("2000000009", PASSWORD);

        String requestJson = """
                {"title":"","description":"Descripcion de prueba","fullName":"Santiago Murcia",
                 "email":"correo-invalido","document":"2000000009","city":"Neiva","phone":"3000000000",
                 "type":"QUEJA","acceptedDataPolicy":true,"expirationDate":"2030-12-31"}""";

        mvc.perform(multipart("/requests/save")
                .file(new MockMultipartFile("request", "", "application/json",
                        requestJson.getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("files", "soporte.txt", "text/plain",
                        "contenido".getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));
    }
}
