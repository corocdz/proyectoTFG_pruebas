package ui;



import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import ui.audio.ButtonSound;
import ui.audio.MusicManager;
import ui.audio.SoundManager;

public class PopUpSonidoController {

    @FXML private Slider sliderMusica;
    @FXML private Slider sliderEfectos;
    @FXML private Button btnCerrar;
    @FXML private StackPane rootSonido;

    @FXML
    private void initialize() {
        
        sliderMusica.setValue(MusicManager.getVolumen());
        sliderEfectos.setValue(SoundManager.getVolumen());

        sliderMusica.valueProperty().addListener((obs, oldV, newV) ->
                MusicManager.setVolumen(newV.doubleValue()));

        sliderEfectos.valueProperty().addListener((obs, oldV, newV) ->
                SoundManager.setVolumen(newV.doubleValue()));

        Animaciones.animarBoton(btnCerrar);
        
        ButtonSound.activarClick(btnCerrar);
        
        btnCerrar.setOnAction(e -> ((Pane)rootSonido.getParent()).getChildren().remove(rootSonido));
    }
}
