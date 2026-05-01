package i18n;

import java.util.Locale;
import java.util.ResourceBundle;

public class IdiomaManager {

    private static Locale localeActual = new Locale("en");

    public static void setIdioma(String codigo) {
        localeActual = new Locale(codigo);
    }

    public static ResourceBundle getBundle() {
        return ResourceBundle.getBundle("i18n.messages", localeActual);
    }
}
