/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package ui;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import ui.audio.ButtonSound;

public class PopUpErrorController {

    @FXML
    private StackPane rootError;
    @FXML
    private VBox box;
    @FXML
    private Label lblMensaje;
    @FXML
    private Button btnCerrar;

    public void setMensaje(String mensaje) {
        lblMensaje.setText(mensaje);
    }

    @FXML
    public void initialize() {

        // Animación de entrada
        rootError.setOpacity(0);
        box.setScaleX(0.7);
        box.setScaleY(0.7);

        FadeTransition fade = new FadeTransition(Duration.millis(300), rootError);
        fade.setFromValue(0);
        fade.setToValue(1);

        ScaleTransition scale = new ScaleTransition(Duration.millis(300), box);
        scale.setFromX(0.7);
        scale.setFromY(0.7);
        scale.setToX(1);
        scale.setToY(1);

        new ParallelTransition(fade, scale).play();
        
        Animaciones.animarBoton(btnCerrar);
        
        ButtonSound.activarClick(btnCerrar);      
        
        // Cerrar popup
        btnCerrar.setOnAction(e -> cerrar());                
        
    }

    private void cerrar() {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(250), rootError);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(ev -> ((StackPane) rootError.getParent()).getChildren().remove(rootError));
        fadeOut.play();
    }
    
}

