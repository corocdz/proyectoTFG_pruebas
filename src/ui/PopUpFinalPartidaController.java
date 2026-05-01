package ui;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class PopUpFinalPartidaController {

    @FXML private StackPane rootPopup;
    @FXML private VBox popupBox;

    @FXML private ImageView imgTitulo;
    @FXML private ImageView imgVolverSala;
    @FXML private ImageView imgVolverJugar;

    @FXML private Label lblTitulo;
    @FXML private Label lblGanador;
    
    

    private Stage popupStage;

    // ============================================================
    // INICIALIZACIÓN
    // ============================================================
    @FXML
    private void initialize() {

        // Cargar imagen por defecto (trofeo)
        imgTitulo.setImage(new Image(getClass().getResource(
                "/ui/graphicResources/imagenes/imgGanador.png").toExternalForm()));

        imgVolverSala.setImage(new Image(getClass().getResource(
                "/ui/graphicResources/imagenes/btnIrSala.png").toExternalForm()));
        
        imgVolverSala.setImage(new Image(getClass().getResource(
                "/ui/graphicResources/imagenes/btnVolverJugar.png").toExternalForm()));

        animarEntrada();
    }

    // ============================================================
    // ANIMACIÓN DE ENTRADA
    // ============================================================
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

    // ============================================================
    // ANIMACIÓN DE SALIDA
    // ============================================================
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

    // ============================================================
    // BOTÓN VOLVER A LA SALA
    // ============================================================
    @FXML
    private void onVolverSala() {
        animarSalida(() -> popupStage.close());
    }

    // ============================================================
    // MÉTODOS PARA EL CONTROLADOR PRINCIPAL
    // ============================================================
    public void setStage(Stage stage) {
        this.popupStage = stage;
    }

    /**
     * Configura el popup según si hay ganador o empate.
     *
     * @param textoGanador  "Pepito" o "Pepito y Juanito"
     * @param pescaitos     número de pescaitos
     * @param empate        true si hay empate
     */
    public void setDatos(String textoGanador, int pescaitos, boolean empate) {

        if (empate) {
            lblTitulo.setText("¡Empate!");
            lblGanador.setText("Empate entre: " + textoGanador + " (" + pescaitos + " pescaitos)");

            imgTitulo.setImage(new Image(getClass().getResource(
                    "/ui/graphicResources/imagenes/imgEmpate.png").toExternalForm()));

        } else {
            lblTitulo.setText("¡La partida ha terminado!");
            lblGanador.setText("Ganador: " + textoGanador + " (" + pescaitos + " pescaitos)");

            imgTitulo.setImage(new Image(getClass().getResource(
                    "/ui/graphicResources/imagenes/imgGanador.png").toExternalForm()));
        }
    }
}
