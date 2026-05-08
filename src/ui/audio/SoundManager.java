package ui.audio;

import javafx.scene.media.AudioClip;
import java.util.prefs.Preferences;

public class SoundManager {

    private static double volumenEfectos;

    private static AudioClip clickButtonClip;

    static {

        Preferences prefs = Preferences.userNodeForPackage(SoundManager.class);
        volumenEfectos = prefs.getDouble("volumenEfectos", 0.7);

        clickButtonClip = new AudioClip(SoundManager.class
                .getResource("/ui/audio/efectos/clickButton.wav").toExternalForm());
        clickButtonClip.setVolume(volumenEfectos);
    }

    private static AudioClip hoverClip;

    static {
        Preferences prefs = Preferences.userNodeForPackage(SoundManager.class);
        volumenEfectos = prefs.getDouble("volumenEfectos", 0.7);

        clickButtonClip = new AudioClip(SoundManager.class
                .getResource("/ui/audio/efectos/clickButton.wav").toExternalForm());

        hoverClip = new AudioClip(SoundManager.class
                .getResource("/ui/audio/efectos/hover.wav").toExternalForm());

        clickButtonClip.setVolume(volumenEfectos);
        hoverClip.setVolume(volumenEfectos);
    }

    public static void setVolumen(double v) {
        volumenEfectos = v;

        clickButtonClip.setVolume(v);

        Preferences prefs = Preferences.userNodeForPackage(SoundManager.class);
        prefs.putDouble("volumenEfectos", v);
    }

    public static double getVolumen() {
        return volumenEfectos;
    }

    public static void hover() {
        AudioClip hoverClip = new AudioClip(SoundManager.class
                .getResource("/ui/audio/efectos/hover.wav").toExternalForm());
        hoverClip.setVolume(volumenEfectos);
        hoverClip.play();
    }

    public static void clickButton() {
        clickButtonClip.play();
    }
}
