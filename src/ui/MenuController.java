package ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

public class MenuController {

    @FXML
    private Button btnOnline;
    @FXML
    private Button btnOffline;
    @FXML
    private Button btnSalir;
    @FXML
    private Button btnOpciones;

    @FXML
    private ImageView logoImage;

    @FXML
    private void initialize() {

        // Cargar imagen
        logoImage.setImage(new Image(
                getClass().getResource("/ui/graphicResources/imagenes/tokaledaCardsGame.png").toExternalForm()
        ));

        Animaciones.animarLogo(logoImage);

        Animaciones.animarBoton(btnOnline);
        Animaciones.animarBoton(btnOffline);
        Animaciones.animarBoton(btnOpciones);
        Animaciones.animarBoton(btnSalir);

        btnOnline.setOnAction(e -> abrirModoOnline());
        btnOffline.setOnAction(e -> abrirModoUnJugador());
        btnSalir.setOnAction(e -> {
            MainApp.desconectarUsuario();
            Platform.exit();
        });
    }

    private void abrirModoOnline() {
        MainApp.cambiarEscena("menuOnline.fxml", 800, 600);
    }

    private void abrirModoUnJugador() {
        MainApp.cambiarEscena("mesa.fxml", 1000, 700);
    }

}
