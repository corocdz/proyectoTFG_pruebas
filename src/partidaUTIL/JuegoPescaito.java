package partidaUTIL;

import java.util.*;

public class JuegoPescaito implements Juego {

    private static final int CARTAS_INICIALES = 5;
    private static final int CARTAS_POR_PESCAITO = 4;

    private Map<Integer, List<String>> agruparPorNumero(List<String> mano) {
        Map<Integer, List<String>> grupos = new HashMap<>();
        for (String carta : mano) {
            int num = obtenerNumeroCarta(carta);
            grupos.computeIfAbsent(num, k -> new ArrayList<>()).add(carta);
        }
        return grupos;
    }

    private boolean tieneNumeroEnMano(List<String> mano, int numero) {
        for (String carta : mano) {
            if (obtenerNumeroCarta(carta) == numero) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------
    //  MÉTODO PRINCIPAL DEL JUEGO: PREGUNTAR
    // ---------------------------------------------------------
    public Map<String, Object> preguntar(
            String jugadorQuePregunta,
            String jugadorObjetivo,
            int numeroPreguntado,
            Map<String, List<String>> manos,
            List<String> baraja,
            List<String> descarte
    ) {

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("acierto", false);
        resultado.put("cartasRecibidas", 0);
        resultado.put("pescaito", false);
        resultado.put("debeRobar", false);
        resultado.put("mantieneTurno", false);

        List<String> manoPreg = manos.get(jugadorQuePregunta);
        List<String> manoObj = manos.get(jugadorObjetivo);

        // 1. Validar que el jugador que pregunta tiene ese número
        if (!tieneNumeroEnMano(manoPreg, numeroPreguntado)) {
            resultado.put("mensaje", "No puedes preguntar por un número que no tienes.");
            return resultado;
        }

        // 2. Buscar cartas del número en el objetivo
        List<String> cartasCoincidentes = new ArrayList<>();
        for (String carta : manoObj) {
            if (obtenerNumeroCarta(carta) == numeroPreguntado) {
                cartasCoincidentes.add(carta);
            }
        }

        // 3. Si acierta
        if (!cartasCoincidentes.isEmpty()) {
            manoObj.removeAll(cartasCoincidentes);
            manoPreg.addAll(cartasCoincidentes);

            resultado.put("acierto", true);
            resultado.put("cartasRecibidas", cartasCoincidentes.size());
            resultado.put("mantieneTurno", true);

            // 4. Comprobar si ahora forma pescaito
            Map<Integer, List<String>> grupos = agruparPorNumero(manoPreg);
            for (Map.Entry<Integer, List<String>> entry : grupos.entrySet()) {
                if (entry.getValue().size() == CARTAS_POR_PESCAITO) {

                    int numeroPescaito = entry.getKey();
                    resultado.put("pescaito", true);
                    resultado.put("numeroPescaito", numeroPescaito);

                    // 🔥 ELIMINAR LAS 4 CARTAS DEL PESCaito
                    List<String> aDescartar = entry.getValue();
                    manoPreg.removeAll(aDescartar);

                    // 🔥 AÑADIRLAS AL DESCARTE
                    if (descarte != null) {
                        descarte.addAll(aDescartar);
                    }

                    break;

                }
            }

            return resultado;
        }

        // 5. Si falla → debe robar
        resultado.put("debeRobar", true);
        resultado.put("mantieneTurno", false);
        resultado.put("mensaje", "No tenía ese número.");

        return resultado;
    }

    // ---------------------------------------------------------
    //  1. INICIO DE PARTIDA
    // ---------------------------------------------------------
    @Override
    public void iniciarPartida(Map<String, List<String>> manos, List<String> baraja) {
        // 1. Asegurarse de que cada jugador tiene una lista (aunque esté vacía).
        //    Si el controlador ya las creó, este bucle no hace nada.
        //    Si no las creó, las crea aquí para que repartirCartas() funcione.
        for (String uid : manos.keySet()) {
            if (manos.get(uid) == null) {
                manos.put(uid, new ArrayList<>());
            }
        }

        // 2. Repartir las cartas iniciales a cada jugador.
        //    repartirCartas() ya existe en esta misma clase y hace exactamente eso.
        repartirCartas(manos, baraja);
    }

    // ---------------------------------------------------------
    //  2. REPARTO INICIAL
    // ---------------------------------------------------------
    @Override
    public void repartirCartas(Map<String, List<String>> manos, List<String> baraja) {
        for (String uid : manos.keySet()) {
            List<String> mano = manos.get(uid);
            if (mano == null) {
                mano = new ArrayList<>();
                manos.put(uid, mano);
            }

            for (int i = 0; i < CARTAS_INICIALES && !baraja.isEmpty(); i++) {
                String carta = baraja.remove(0);
                mano.add(carta);
            }
        }
    }

    // ---------------------------------------------------------
    //  3. ROBAR CARTA
    // ---------------------------------------------------------
    @Override
    public boolean puedeRobar(String uidJugador) {
        return true;
    }

    @Override
    public void robarCarta(String uidJugador,
            Map<String, List<String>> manos,
            List<String> baraja) {

        if (baraja.isEmpty()) {
            return; // no hay carta que robar
        }

        List<String> mano = manos.get(uidJugador);
        if (mano == null) {
            mano = new ArrayList<>();
            manos.put(uidJugador, mano);
        }

        String carta = baraja.remove(0); // roba la primera carta
        mano.add(carta);                 // la añade a la mano
    }

    // ---------------------------------------------------------
    //  4. DESCARTAR CARTA (PESCAITO)
    // ---------------------------------------------------------
    @Override
    public boolean puedeDescartar(String uidJugador, String carta) {
        return true;
    }

    @Override
    public void descartarCarta(String uidJugador,
            String carta,
            Map<String, List<String>> manos,
            List<String> baraja,
            List<String> descarte) {

        List<String> mano = manos.get(uidJugador);
        if (mano == null || mano.isEmpty()) {
            return;
        }

        int numeroObjetivo = obtenerNumeroCarta(carta);
        List<String> aDescartar = new ArrayList<>();

        for (String c : mano) {
            if (obtenerNumeroCarta(c) == numeroObjetivo) {
                aDescartar.add(c);
            }
        }

        if (aDescartar.size() == CARTAS_POR_PESCAITO) {
            mano.removeAll(aDescartar);
            if (descarte != null) {
                descarte.addAll(aDescartar);
            }
        }
    }

    // ---------------------------------------------------------
    //  5. TURNOS
    // ---------------------------------------------------------
    @Override
    public String siguienteTurno(String turnoActual, List<String> jugadores) {
        if (jugadores == null || jugadores.isEmpty()) {
            return turnoActual;
        }

        int idx = jugadores.indexOf(turnoActual);
        if (idx == -1) {
            return jugadores.get(0);
        }

        int siguiente = (idx + 1) % jugadores.size();
        return jugadores.get(siguiente);
    }

    // ---------------------------------------------------------
    //  6. FIN DE PARTIDA
    // ---------------------------------------------------------
    @Override
    public boolean haTerminado(Map<String, List<String>> manos, List<String> baraja, List<String> descarte) {

        if (manos == null || manos.isEmpty()) {
            return true;
        }

        // La partida de Pescaito termina cuando TODOS los jugadores
        // tienen la mano vacía Y además la baraja está agotada.
        // Si aún hay cartas en la baraja, el juego puede continuar
        // (los jugadores con mano vacía robarán automáticamente).
        for (List<String> mano : manos.values()) {
            if (mano != null && !mano.isEmpty()) {
                return false; // aún hay cartas en alguna mano
            }
        }

        // Todas las manos vacías → solo termina si la baraja también está vacía
        return baraja == null || baraja.isEmpty();
    }

    // ---------------------------------------------------------
    //  7. PUNTUACIONES
    // ---------------------------------------------------------
    public Map<String, Integer> calcularPuntuacionesDesdeBD(Map<String, Map<String, Object>> pescaitosBD) {
        Map<String, Integer> resultado = new HashMap<>();

        if (pescaitosBD == null) {
            return resultado;
        }

        for (Map.Entry<String, Map<String, Object>> entry : pescaitosBD.entrySet()) {
            String uid = entry.getKey();
            Map<String, Object> mapa = entry.getValue();

            // null o mapa vacío = 0 pescaitos, no un crash
            int cantidad = (mapa != null) ? mapa.size() : 0;
            resultado.put(uid, cantidad);
        }

        return resultado;
    }

    @Override
    public Map<String, Integer> calcularPuntuaciones(Map<String, List<String>> manos) {
        // En Pescaito la puntuación real viene de los pescaitos, no de las manos.
        return new HashMap<>();
    }

    public boolean esPescaitoPorRobo(String uid,
            Map<String, List<String>> manos,
            List<String> descarte) {

        List<String> mano = manos.get(uid);
        if (mano == null || mano.isEmpty()) {
            return false;
        }

        // Última carta añadida a la mano (la robada)
        String cartaRobada = mano.get(mano.size() - 1);
        int numero = obtenerNumeroCarta(cartaRobada);

        // Contar cuántas cartas de ese número tiene el jugador
        long count = mano.stream()
                .filter(c -> obtenerNumeroCarta(c) == numero)
                .count();

        if (count == CARTAS_POR_PESCAITO) {
            // Extraer el cuarteto
            List<String> cuarteto = mano.stream()
                    .filter(c -> obtenerNumeroCarta(c) == numero)
                    .toList();

            // Mover al descarte
            descarte.addAll(cuarteto);

            // Eliminar de la mano
            mano.removeAll(cuarteto);

            return true;
        }

        return false;
    }

    @Override
    public AccionTurno accionInicioTurno(String uidJugador,
            Map<String, List<String>> manos,
            List<String> baraja) {

        List<String> mano = manos.get(uidJugador);
        boolean manoVacia = (mano == null || mano.isEmpty());

        if (!manoVacia) {
            return AccionTurno.JUGAR_NORMAL;
        }

        // Mano vacía: la baraja decide qué pasa
        if (!baraja.isEmpty()) {
            return AccionTurno.ROBAR_AUTOMATICO;
        }

        return AccionTurno.PASAR_TURNO;
    }

    /**
     * Devuelve true si el jugador "ha pescado": robó exactamente el número que
     * había preguntado antes de entrar en estado "debe robar". En Pescaito,
     * pescar significa mantener el turno.
     *
     * @param cartaRobada ruta de la carta que acaba de robar
     * @param numeroPreguntadoAntes número que preguntó antes de robar (puede
     * ser null si no se preguntó nada)
     */
    public boolean haPescadoAlRobar(String cartaRobada, Integer numeroPreguntadoAntes) {

        if (numeroPreguntadoAntes == null) {
            return false;
        }

        int numeroRobado = obtenerNumeroCarta(cartaRobada);  // usa el método del Caso 4
        return numeroRobado == numeroPreguntadoAntes;
    }

}
