/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package ui;

import i18n.IdiomaManager;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import ui.audio.ButtonSound;

public class PopUpCrearSalaController {

    @FXML
    private StackPane rootPopup;
    @FXML
    private VBox popupBox;

    @FXML
    private ImageView popUpCrearSalaImagen;
    @FXML
    private ImageView btnCrearImage;
    @FXML
    private ImageView btnCancelarImage;
    
    @FXML
    private Button btnCancelar;
    @FXML
    private Button btnCrear;

    @FXML
    private PasswordField txtPassword;

    private Stage popupStage; // referencia al Stage del popup
    private String resultado = null; // contraseña devuelta

    // ============================================================
    // INICIALIZACIÓN
    // ============================================================
    @FXML
    private void initialize() {

        // Cargar imágenes del popup
        popUpCrearSalaImagen.setImage(IdiomaManager.cargarImagen("popUpCrearSalaImagen"));
        btnCrearImage.setImage(IdiomaManager.cargarImagen("btnCrear"));
        btnCancelarImage.setImage(IdiomaManager.cargarImagen("btnCancelar"));

        // Animación de entrada
        animarEntrada();
        Animaciones.animarBoton(btnCrear);
        Animaciones.animarBoton(btnCancelar);
        
        ButtonSound.activar(btnCancelar);
        ButtonSound.activar(btnCrear);
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
    // BOTÓN CREAR
    // ============================================================
    @FXML
    private void onCrear() {
        resultado = txtPassword.getText().trim();

        animarSalida(() -> popupStage.close());
    }

    // ============================================================
    // BOTÓN CANCELAR
    // ============================================================
    @FXML
    private void onCancelar() {
        resultado = null;
        animarSalida(() -> popupStage.close());
    }

    // ============================================================
    // MÉTODOS PARA EL MENÚ ONLINE
    // ============================================================
    public void setStage(Stage stage) {
        this.popupStage = stage;
    }

    public String getResultado() {
        return resultado;
    }
}
