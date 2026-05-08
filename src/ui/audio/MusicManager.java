package ui.audio;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import java.util.prefs.Preferences;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class MusicManager {

    private static MediaPlayer player;
    private static double volumenMusica;
    private static String musicaActual = "";

    static {
        Preferences prefs = Preferences.userNodeForPackage(MusicManager.class);
        volumenMusica = prefs.getDouble("volumenMusica", 0.5);
    }

    public static void reproducirMusica(String ruta) {

        fadeOut(() -> {
            try {
                Media media = new Media(MusicManager.class.getResource(ruta).toExternalForm());
                player = new MediaPlayer(media);

                player.setCycleCount(MediaPlayer.INDEFINITE);
                player.setVolume(0); // empezamos en 0 para el fade-in

                // ⭐ Si quieres empezar en un punto concreto:
                // player.setStartTime(Duration.seconds(12));
                player.play();

                // ⭐ Fade-in suave
                fadeIn(player, volumenMusica, 1500);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 800); // fade-out de 800ms antes de cambiar
    }

    public static void playMenuMusic() {
        if (isPlaying("menu")) {
            return; // evitar reiniciar
        }
        musicaActual = "menu";
        reproducirMusica("/ui/audio/musica/musicaGeneral.wav");
    }

    public static void playPartidaMusic() {
        if (isPlaying("partida")) {
            return; // evitar reiniciar
        }
        musicaActual = "partida";
        reproducirMusica("/ui/audio/musica/musicaPartida.wav");
    }

    public static void setVolumen(double v) {
        volumenMusica = v;
        if (player != null) {
            player.setVolume(v);
        }

        Preferences prefs = Preferences.userNodeForPackage(MusicManager.class);
        prefs.putDouble("volumenMusica", v);
    }

    public static double getVolumen() {
        return volumenMusica;
    }

    private static void fadeIn(MediaPlayer player, double targetVolume, int durationMs) {
        player.setVolume(0);
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(player.volumeProperty(), 0)),
                new KeyFrame(Duration.millis(durationMs), new KeyValue(player.volumeProperty(), targetVolume))
        );
        timeline.play();
    }

    private static void fadeOut(Runnable afterFade, int durationMs) {
        if (player == null) {
            if (afterFade != null) {
                afterFade.run();
            }
            return;
        }

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(player.volumeProperty(), player.getVolume())),
                new KeyFrame(Duration.millis(durationMs), new KeyValue(player.volumeProperty(), 0))
        );

        timeline.setOnFinished(e -> {
            player.stop();
            if (afterFade != null) {
                afterFade.run();
            }
        });

        timeline.play();
    }

    public static boolean isPlaying(String tipo) {
        return musicaActual.equals(tipo);
    }

}
