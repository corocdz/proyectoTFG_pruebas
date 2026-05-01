package ui;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class PopUpUnirseController {

    @FXML private StackPane rootPopup;
    @FXML private VBox popupBox;

    @FXML private ImageView imgTitulo;
    @FXML private ImageView imgUnirse;
    @FXML private ImageView imgCancelar;

    @FXML private TextField txtCodigo;
    @FXML private PasswordField txtPassword;

    private Stage popupStage;

    private String codigo = null;
    private String password = null;

    @FXML
    private void initialize() {

        imgTitulo.setImage(new Image(getClass().getResource(
                "/ui/graphicResources/imagenes/popUpCrearSalaImagen.png").toExternalForm()));

        imgUnirse.setImage(new Image(getClass().getResource(
                "/ui/graphicResources/imagenes/btnUnirse.png").toExternalForm()));

        imgCancelar.setImage(new Image(getClass().getResource(
                "/ui/graphicResources/imagenes/btnCancelar.png").toExternalForm()));

        animarEntrada();
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
