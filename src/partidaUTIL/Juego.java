/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package partidaUTIL;

import java.util.List;
import java.util.Map;

/**
 *
 * @author coro
 */
public interface Juego {

    // 1. Configuración inicial
    void iniciarPartida(Map<String, List<String>> manos, List<String> baraja);

    // 2. Reparto inicial
    void repartirCartas(Map<String, List<String>> manos, List<String> baraja);

    // 3. Robar carta
    boolean puedeRobar(String uidJugador);

    void robarCarta(String uidJugador, Map<String, List<String>> manos, List<String> baraja);

    // 4. Descartar carta
    boolean puedeDescartar(String uidJugador, String carta);

    void descartarCarta(String uidJugador, String carta,
            Map<String, List<String>> manos,
            List<String> baraja,
            List<String> descarte);

    // 5. Turnos
    String siguienteTurno(String turnoActual, List<String> jugadores);

    // 6. Fin de partida
    boolean haTerminado(Map<String, List<String>> manos, List<String> baraja, List<String> descarte);

    // 7. Puntuaciones
    Map<String, Integer> calcularPuntuaciones(Map<String, List<String>> manos);

    default int obtenerNumeroCarta(String ruta) {
        String nombre = ruta.substring(ruta.lastIndexOf("/") + 1);
        String[] partes = nombre.split("_");
        String numeroStr = partes[1].replace(".png", "");
        return Integer.parseInt(numeroStr);
    }

    // Enum que describe qué debe hacer el jugador al inicio de su turno
    enum AccionTurno {
        JUGAR_NORMAL, // tiene cartas, puede preguntar
        ROBAR_AUTOMATICO, // mano vacía pero hay baraja → roba y espera
        PASAR_TURNO         // mano vacía y baraja vacía → pasa sin hacer nada
    }

    // Dado el estado actual, ¿qué debe hacer el jugador al empezar su turno?
    AccionTurno accionInicioTurno(String uidJugador,
            Map<String, List<String>> manos,
            List<String> baraja);
}
