package ui;

import firebase.FirebaseAuthService;
import firebase.FirebaseDatabaseService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import ui.MainApp;
import i18n.IdiomaManager;
import java.util.ResourceBundle;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

public class RegistroController {

    @FXML
    private TextField txtNombre;
    @FXML
    private TextField txtEmail;
    @FXML
    private PasswordField txtPassword;
    @FXML
    private PasswordField txtConfirmar;
    @FXML
    private Button btnCrearCuenta;
    @FXML
    private Button btnVolver;
    @FXML
    private ImageView logoImage;
    @FXML
    private Label tituloRegistro;
    @FXML
    private Label textoVolver;
    @FXML
    private StackPane rootRegistro;
    
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
        Animaciones.animarBoton(btnVolver);
        Animaciones.animarBoton(btnCrearCuenta);
        
        Animaciones.animarLabelGeneral(tituloRegistro);
        Animaciones.animarLabelSecundario(textoVolver);
        
        btnCrearCuenta.setOnAction(e -> registrar());
        btnVolver.setOnAction(e -> MainApp.cambiarEscena("login.fxml", 600, 400));
    }

    private void registrar() {
        String nombre = txtNombre.getText().trim();
        String email = txtEmail.getText().trim();
        String password = txtPassword.getText().trim();
        String confirmar = txtConfirmar.getText().trim();

        if (nombre.isEmpty() || email.isEmpty() || password.isEmpty() || confirmar.isEmpty()) {
           Animaciones.mostrarError(rootRegistro, bundle.getString("registroController.error.campos"));
            return;
        }

        if (!password.equals(confirmar)) {
            Animaciones.mostrarError(rootRegistro, bundle.getString("registroController.error.password"));
            return;
        }

        try {
            boolean ok = authService.register(email, password);
            if (ok) {
                String uid = authService.getLocalId();
                String token = authService.getIdToken();

                Map<String, Object> datos = new HashMap<>();
                datos.put("email", email);
                datos.put("nombre", nombre);
                datos.put("avatar", "default.png");
                datos.put("conectado", true);
                datos.put("ultimaConexion", System.currentTimeMillis());

                dbService.guardarUsuario(uid, datos, token);

                System.out.println("Registro correcto. UID: " + uid);
                System.out.println("UID creado en registro: " + uid);

                MainApp.usuarioActualUID = uid;
                MainApp.usuarioActualToken = token;

                MainApp.cambiarEscena("menuPrincipal.fxml", 800, 600);
            } else {
                Animaciones.mostrarError(rootRegistro,bundle.getString("registroController.error.registro"));
            }
        } catch (Exception ex) {
            Animaciones.mostrarError(rootRegistro, bundle.getString("registroController.error.excepcion") + ex.getMessage());
        }
    }

    private void mostrarError(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
