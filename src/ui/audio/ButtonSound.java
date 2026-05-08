package ui.audio;

import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;

public class ButtonSound {

    public static void activar(Button b) {
        if (b == null) {
            return;
        }

        b.setPickOnBounds(true);

        // Hover (solo una vez por entrada real)
        b.addEventHandler(MouseEvent.MOUSE_ENTERED_TARGET, e -> {
            SoundManager.hover();
        });

        // Click
        b.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            SoundManager.clickButton();
        });
    }

    // 🔵 Solo hover
    public static void activarHover(Button b) {
        if (b == null) {
            return;
        }

        b.setPickOnBounds(true);

        b.addEventHandler(MouseEvent.MOUSE_ENTERED_TARGET, e -> {
            SoundManager.hover();
        });
    }

    // 🔴 Solo click
    public static void activarClick(Button b) {
        if (b == null) {
            return;
        }

        b.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            SoundManager.clickButton();
        });
    }

}
