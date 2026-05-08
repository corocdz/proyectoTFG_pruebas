package ui;

import i18n.IdiomaManager;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import ui.audio.ButtonSound;

public class PopUpUnirseController {

    @FXML private StackPane rootPopup;
    @FXML private VBox popupBox;

    @FXML private ImageView popUpCrearSalaImagen;
    @FXML private ImageView btnUnirseImage;
    @FXML private ImageView btnCancelarImage;
    
    @FXML private Button btnUnirse;
    @FXML private Button btnCancelar;

    @FXML private TextField txtCodigo;
    @FXML private PasswordField txtPassword;

    private Stage popupStage;

    private String codigo = null;
    private String password = null;

    @FXML
    private void initialize() {

        popUpCrearSalaImagen.setImage(IdiomaManager.cargarImagen("popUpCrearSalaImagen"));
        btnUnirseImage.setImage(IdiomaManager.cargarImagen("btnUnirse"));
        btnCancelarImage.setImage(IdiomaManager.cargarImagen("btnCancelar"));

        animarEntrada();
        
        Animaciones.animarBoton(btnUnirse);
        Animaciones.animarBoton(btnCancelar);
        
        ButtonSound.activar(btnCancelar);
        ButtonSound.activar(btnUnirse);
        
    }

    private void animarEntrada() {
        popupBox.setScaleX(0.7);
        popupBox.setScaleY(0.7);
        popupBox.setOpacity(0);

        ScaleTransition st = new ScaleTransition(Duration.millis(250), popupBox);
        st.setToX(1);
        st.setToY(1);

        FadeTransition ft = new FadeTransition(Duration.millis(250), popupBox);
        ft.setToValue(1);

        new ParallelTransition(st, ft).play();
    }

    private void animarSalida(Runnable after) {
        ScaleTransition st = new ScaleTransition(Duration.millis(200), popupBox);
        st.setToX(0.7);
        st.setToY(0.7);

        FadeTransition ft = new FadeTransition(Duration.millis(200), popupBox);
        ft.setToValue(0);

        ParallelTransition pt = new ParallelTransition(st, ft);
        pt.setOnFinished(e -> after.run());
        pt.play();
    }

    @FXML
    private void onUnirse() {
        codigo = txtCodigo.getText().trim();
        password = txtPassword.getText().trim();

        animarSalida(() -> popupStage.close());
    }

    @FXML
    private void onCancelar() {
        codigo = null;
        password = null;

        animarSalida(() -> popupStage.close());
    }

    public void setStage(Stage stage) {
        this.popupStage = stage;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getPassword() {
        return password;
    }
}
