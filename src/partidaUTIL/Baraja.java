package partidaUTIL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Baraja {

    private List<Carta> cartas = new ArrayList<>();

    public Baraja() {
        generarBaraja();
    }

    private void generarBaraja() {
        for (Carta.Palo palo : Carta.Palo.values()) {
            for (int i = 1; i <= 12; i++) {

                String ruta = "/ui/graphicResources/cartas/"
                        + palo.name().toLowerCase() + "_" + i + ".png";

                cartas.add(new Carta(palo, i, ruta));
            }
        }
    }

    public void barajar() {
        Collections.shuffle(cartas);
    }

    public Carta robar() {
        if (cartas.isEmpty()) return null;
        return cartas.remove(0);
    }

    public int size() {
        return cartas.size();
    }

    public List<Carta> getCartasRestantes() {
        return cartas;
    }
}