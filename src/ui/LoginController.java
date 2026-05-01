package ui;

import firebase.FirebaseAuthService;
import firebase.FirebaseDatabaseService;
import java.util.HashMap;
import java.util.Map;
import javafx.fxml.FXML;
import i18n.IdiomaManager;
import java.util.ResourceBundle;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import ui.MainApp;

public class LoginController {

    @FXML
    private TextField txtEmail;
    @FXML
    private PasswordField txtPassword;
    @FXML
    private Button btnLogin;
    @FXML
    private Button btnRegistro;
    @FXML
    private Button btnLoginJ1;
    @FXML
    private Button btnLoginJ2;
    @FXML
    private Button btnLoginJ3;
    @FXML
    private Button btnLoginJ4;
    @FXML
    private ImageView logoImage;
    @FXML
    private Label tituloBienvenida;
    @FXML
    private Label textoRegistro;
    @FXML
    private StackPane rootLogin;
    
    private final ResourceBundle bundle = IdiomaManager.getBundle();

    private final FirebaseAuthService authService = new FirebaseAuthService();
    private final FirebaseDatabaseService dbService = new FirebaseDatabaseService();

    @FXML
    private void initialize() {

        // Cargar imagen
        logoImage.setImage(new Image(
                getClass().getResource("/ui/graphicResources/imagenes/tokaledaCardsGame.png").toExternalForm()
        ));

        Animaciones.animarLogo(logoImage);
        Animaciones.animarLabelGeneral(tituloBienvenida);
        Animaciones.animarLabelSecundario(textoRegistro);
        Animaciones.animarBoton(btnLogin);
        Animaciones.animarBoton(btnRegistro);

        btnLogin.setOnAction(e -> login());
        btnRegistro.setOnAction(e -> MainApp.cambiarEscena("registro.fxml", 600, 400));

        btnLoginJ1.setOnAction(e -> loginRapido("jugador1@test.com", "123456"));
        btnLoginJ2.setOnAction(e -> loginRapido("jugador2@test.com", "123456"));
        btnLoginJ3.setOnAction(e -> loginRapido("jugador3@test.com", "123456"));
        btnLoginJ4.setOnAction(e -> loginRapido("jugador4@test.com", "123456"));
    }

    private void login() {
        String email = txtEmail.getText().trim();
        String password = txtPassword.getText().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Animaciones.mostrarError(rootLogin, bundle.getString("loginController.error.campos"));
            return;
        }

        try {
            boolean ok = authService.login(email, password);

            if (!ok) {
                Animaciones.mostrarError(rootLogin, bundle.getString("loginController.error.credenciales"));
                return;
            }

            String uid = authService.getLocalId();
            String token = authService.getIdToken();
            MainApp.usuarioActualUID = uid;
            MainApp.usuarioActualToken = token;

            // 1. Leer nodo completo del usuario
            String nodoJson = dbService.leerNodo("usuarios/" + uid, token);

            boolean yaConectado = false;

            if (nodoJson != null && !nodoJson.equals("null")) {
                Map<String, Object> datosExistentes = new com.google.gson.Gson().fromJson(nodoJson, Map.class);
                if (datosExistentes != null) {
                    Object conectado = datosExistentes.get("conectado");
                    if (conectado != null && Boolean.TRUE.equals(conectado)) {
                        yaConectado = true;
                    }
                }
            }

            // 2. Si ya está conectado, bloquear login
            if (yaConectado) {
                Animaciones.mostrarError(rootLogin, bundle.getString("loginController.error.activo"));
                MainApp.usuarioActualUID = null;
                MainApp.usuarioActualToken = null;
                return;
            }

            // 3. Preparar cambios: marcar como conectado y actualizar última conexión
            Map<String, Object> cambios = new HashMap<>();
            cambios.put("conectado", true);
            cambios.put("ultimaConexion", System.currentTimeMillis());

            // Si el nodo no existía, también metemos datos básicos
            if (nodoJson == null || nodoJson.equals("null")) {
                cambios.put("email", email);
                cambios.put("nombre", "Usuario");
                cambios.put("avatar", "default.png");
            }

            // 4. Fusionar y guardar
            dbService.actualizarCamposUsuario(uid, cambios, token);

            // 5. Entrar al menú
            MainApp.cambiarEscena("menuPrincipal.fxml", 800, 600);
            System.out.println("UID en login: " + uid);

        } catch (Exception ex) {
            Animaciones.mostrarError(rootLogin, bundle.getString("loginController.error.inicio") + ex.getMessage());
        }
    }

    private void loginRapido(String email, String password) {
        try {
            // 1. Iniciar sesión con Firebase
            boolean ok = authService.login(email, password);

            if (!ok) {
                Animaciones.mostrarError(rootLogin, bundle.getString("loginController.error.pruebas"));
                return;
            }

            // 2. Obtener UID y token igual que en login normal
            String uid = authService.getLocalId();
            String token = authService.getIdToken();
            MainApp.usuarioActualUID = uid;
            MainApp.usuarioActualToken = token;

            // 3. Leer nodo del usuario para comprobar si ya está conectado
            String nodoJson = dbService.leerNodo("usuarios/" + uid, token);

            boolean yaConectado = false;

            if (nodoJson != null && !nodoJson.equals("null")) {
                Map<String, Object> datosExistentes = new com.google.gson.Gson().fromJson(nodoJson, Map.class);
                if (datosExistentes != null) {
                    Object conectado = datosExistentes.get("conectado");
                    if (conectado != null && Boolean.TRUE.equals(conectado)) {
                        yaConectado = true;
                    }
                }
            }

            // 4. Evitar doble login
            if (yaConectado) {
                Animaciones.mostrarError(rootLogin, bundle.getString("loginController.error.activo"));
                MainApp.usuarioActualUID = null;
                MainApp.usuarioActualToken = null;
                return;
            }

            // 5. Preparar cambios en el nodo del usuario
            Map<String, Object> cambios = new HashMap<>();
            cambios.put("conectado", true);
            cambios.put("ultimaConexion", System.currentTimeMillis());

            // Si el nodo no existía, crear datos básicos
            if (nodoJson == null || nodoJson.equals("null")) {
                cambios.put("email", email);
                cambios.put("nombre", "Usuario");
                cambios.put("avatar", "default.png");
            }

            // 6. Guardar cambios en Firebase
            dbService.actualizarCamposUsuario(uid, cambios, token);

            // 7. Entrar al menú principal
            MainApp.cambiarEscena("menuPrincipal.fxml", 800, 600);
            System.out.println("UID en login rápido: " + uid);

        } catch (Exception ex) {
            Animaciones.mostrarError(rootLogin, bundle.getString("loginController.error.rapido")+ ex.getMessage());
        }
    }

}
