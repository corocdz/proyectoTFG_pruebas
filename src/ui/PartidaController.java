package ui;

import com.google.gson.Gson;
import firebase.BDPartidaService;
import firebase.FirebaseDatabaseService;
import java.io.IOException;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import java.util.*;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import partidaUTIL.Juego;
import partidaUTIL.JuegoPescaito;

public class PartidaController {

    // ─── Constantes de posicionamiento del abanico ───────────────────────────
    private static final double RADIO_BASE    = 577.8;
    private static final double ANGULO_BASE   = 5.0;
    private static final double ALTURA_BASE   = 80.0;
    private static final double ROTACION_BASE = 0.8;
    private static final double BASE_Y        = 200.0;

    // ─── Nodos FXML ──────────────────────────────────────────────────────────
    @FXML private Pane zonaArriba;
    @FXML private Pane zonaIzquierda;
    @FXML private Pane zonaDerecha;
    @FXML private Pane zonaAbajo;

    @FXML private Label lblArriba;
    @FXML private Label lblIzquierda;
    @FXML private Label lblDerecha;
    @FXML private Label lblAbajo;
    @FXML private Label narradorLabel;
    @FXML private Label lblInfo;

    @FXML private AnchorPane zonaCentro;
    @FXML private ImageView  imgMazo;
    @FXML private ImageView  imgDescarte;
    @FXML private StackPane  overlayFinal;

    // ─── Servicios Firebase ───────────────────────────────────────────────────
    private final FirebaseDatabaseService db = new FirebaseDatabaseService();
    private final BDPartidaService        bd = new BDPartidaService(db);

    // ─── Sesión ───────────────────────────────────────────────────────────────
    private String idToken;
    private String codigoSala;
    private String uidLocal;

    // ─── Estado de partida ────────────────────────────────────────────────────
    private final Map<String, List<String>> manos    = new HashMap<>();
    private final List<String>              baraja   = new ArrayList<>();
    private final List<String>              descarte = new ArrayList<>();

    private boolean esperandoRobo         = false;
    private boolean uiBloqueadaPorAccion  = false;
    private boolean repartoInicialHecho   = false;
    private boolean partidaFinalizada     = false;

    private Juego        juego;
    private List<String> ordenJugadoresGlobal = new ArrayList<>();
    private String       uidTurnoActual;
    private String       uidJugadorObjetivo   = null;
    private String       uidJugadorArriba;
    private String       uidJugadorIzquierda;
    private String       uidJugadorDerecha;

    private final Map<String, String> nombres = new HashMap<>();

    // ─── Estado Pescaito ──────────────────────────────────────────────────────
    private Integer numeroSeleccionado           = null;
    private Integer numeroPreguntadoAntesDeRobar = null;

    // ─── Animación de mano ────────────────────────────────────────────────────
    private boolean manoAbierta      = false;
    private int     cartaSeleccionada = -1;

    // ─── Sistema de mensajes privados ─────────────────────────────────────────
    private final Queue<String> colaPrivados    = new LinkedList<>();
    private boolean             narrandoPrivado = false;

    // =========================================================================
    //  INIT
    // =========================================================================

    public void init(String codigoSala, String uidLocal, String idToken) {
        this.codigoSala = codigoSala;
        this.uidLocal   = uidLocal;
        this.idToken    = idToken;

        cargarJuegoDesdeBD();
        configurarEventosManoJugador();
        cargarOrdenJugadoresGlobal();
        configurarEventosRobar();

        // Listener: estado finalizada
        bd.escucharEstadoPartida(codigoSala, idToken, estado -> {
            String limpio = estado == null ? "" : estado.replace("\"", "").trim().toLowerCase();
            if (!limpio.equals("finalizada")) return;
            if (partidaFinalizada) return;

            Platform.runLater(() -> {
                partidaFinalizada = true;
                try {
                    Map<String, Map<String, Object>> pescaitosBD =
                            bd.leerPescaitos(codigoSala, idToken);
                    Map<String, Integer> puntuaciones =
                            ((JuegoPescaito) juego).calcularPuntuacionesDesdeBD(pescaitosBD);
                    String uidGanador       = obtenerGanador(puntuaciones);
                    String nombreGanador    = nombres.getOrDefault(uidGanador, "Jugador");
                    int    pescaitosGanador = puntuaciones.getOrDefault(uidGanador, 0);
                    mostrarPantallaFinal(nombreGanador, pescaitosGanador);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });

        // Listener: narrador
        bd.escucharNarrador(codigoSala, idToken, mensaje ->
                Platform.runLater(() -> procesarMensajeNarrador(mensaje)));

        // Listener: turno
        bd.escucharTurno(codigoSala, idToken, nuevoTurno -> {
            Platform.runLater(() -> {
                uidTurnoActual = nuevoTurno;
                narrarGlobal("Turno de: " + nombres.getOrDefault(uidTurnoActual, ""));
                // iniciarTurnoLocal() se llama desde actualizarDesdeModelo(),
                // no desde aquí, para evitar el bucle de turnos infinito.
            });
        });
    }

    // =========================================================================
    //  CARGA INICIAL
    // =========================================================================

    private void cargarJuegoDesdeBD() {
        try {
            Object modoObj = bd.leerCampo(codigoSala, "modo", idToken);
            if (modoObj == null) {
                System.out.println("No se ha encontrado el modo de juego.");
                return;
            }

            String modo = modoObj.toString();
            System.out.println("Modo de juego cargado: " + modo);

            if ("Pescaito".equals(modo)) {
                juego = new JuegoPescaito();
            } else {
                System.out.println("Modo no reconocido en este controlador: " + modo);
                return;
            }

            Map<String, Object> partida = bd.leerPartida(codigoSala, idToken);
            if (partida == null) {
                System.out.println("No se ha encontrado el nodo partida.");
                return;
            }

            Map<String, List<String>> manosBD   = (Map<String, List<String>>) partida.get("manos");
            List<String>              barajaBD   = (List<String>) partida.get("baraja");
            List<String>              descarteBD = (List<String>) partida.get("descarte");

            actualizarDesdeModelo(manosBD, barajaBD, descarteBD);
            prepararEstadoInicial(partida);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cargarOrdenJugadoresGlobal() {
        try {
            String json = db.leerNodo("salas/" + codigoSala + "/jugadores", idToken);
            if (json == null || "null".equals(json)) return;
            Map<String, Object> mapa = new Gson().fromJson(json, Map.class);
            ordenJugadoresGlobal.clear();
            ordenJugadoresGlobal.addAll(mapa.keySet());
            System.out.println("Orden global de jugadores: " + ordenJugadoresGlobal);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void prepararEstadoInicial(Map<String, Object> partida) {
        if (juego == null) return;

        boolean manosVacias = manos.values().stream().allMatch(m -> m == null || m.isEmpty());

        if (!manosVacias) {
            System.out.println("Las manos ya están repartidas. No se hace nada.");
            actualizarInterfaz();
            return;
        }

        try {
            String uidHost = db.leerNodo("salas/" + codigoSala + "/host", idToken)
                    .replace("\"", "");
            if (!uidLocal.equals(uidHost)) {
                System.out.println("No soy el host, espero a que el host reparta.");
                actualizarInterfaz();
                return;
            }

            System.out.println("Soy el host. Repartiendo cartas iniciales...");

            if (manos.isEmpty()) {
                String jsonSala = db.leerNodo("salas/" + codigoSala, idToken);
                Map<String, Object> sala = new Gson().fromJson(jsonSala, Map.class);
                Map<String, Object> jugadores = (Map<String, Object>) sala.get("jugadores");
                for (String uid : jugadores.keySet()) manos.put(uid, null);
            }

            juego.iniciarPartida(manos, baraja);

            for (String uid : manos.keySet())
                bd.actualizarMano(codigoSala, uid, manos.get(uid), idToken);
            bd.actualizarBaraja(codigoSala, baraja, idToken);

            actualizarInterfaz();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error preparando estado inicial.");
        }
    }

    // =========================================================================
    //  ACTUALIZAR MODELO DESDE FIREBASE
    // =========================================================================

    public void actualizarDesdeModelo(Map<String, List<String>> manosBD,
                                       List<String> barajaBD,
                                       List<String> descarteBD) throws IOException {
        if (partidaFinalizada) return;

        if (manosBD == null) manosBD = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : manosBD.entrySet()) {
            List<String> lista = entry.getValue();
            if (lista != null && lista.size() == 1 && "EMPTY".equals(lista.get(0)))
                entry.setValue(new ArrayList<>());
        }

        this.manos.clear();    this.manos.putAll(manosBD);
        this.baraja.clear();   this.baraja.addAll(barajaBD   != null ? barajaBD   : new ArrayList<>());
        this.descarte.clear(); this.descarte.addAll(descarteBD != null ? descarteBD : new ArrayList<>());

        try {
            String turno = db.leerNodo("salas/" + codigoSala + "/partida/turno", idToken);
            if (turno != null) uidTurnoActual = turno.replace("\"", "");
        } catch (Exception e) {
            e.printStackTrace();
        }

        int totalCartas = manos.values().stream()
                .filter(Objects::nonNull).mapToInt(List::size).sum();
        if (!repartoInicialHecho && totalCartas > 0) repartoInicialHecho = true;

        actualizarInterfaz();

        if (uidLocal.equals(uidTurnoActual)) iniciarTurnoLocal();
    }

    // =========================================================================
    //  INICIO DE TURNO
    // =========================================================================

    private void iniciarTurnoLocal() throws IOException {
        if (!repartoInicialHecho)             { activarInteraccionPregunta(); return; }
        if (partidaFinalizada)                return;
        if (esperandoRobo || uiBloqueadaPorAccion) return;
        if (esFinDePartida())                 { finalizarPartida(); return; }

        Juego.AccionTurno accion = juego.accionInicioTurno(uidLocal, manos, baraja);

        switch (accion) {
            case PASAR_TURNO:
                boolean alguienTieneCartas = manos.values().stream()
                        .anyMatch(m -> m != null && !m.isEmpty());
                if (!alguienTieneCartas && baraja.isEmpty()) {
                    finalizarPartida();
                    return;
                }
                narrarPrivado(uidLocal, "No tienes cartas ni puedes robar.\nPasas el turno.");
                bd.actualizarTurno(codigoSala, obtenerSiguienteJugadorConCartas(), idToken);
                break;

            case ROBAR_AUTOMATICO:
                narrarPrivado(uidLocal, "No tienes cartas.\nRobas automáticamente para continuar.");
                juego.robarCarta(uidLocal, manos, baraja);
                bd.actualizarMano(codigoSala, uidLocal, manos.get(uidLocal), idToken);
                bd.actualizarBaraja(codigoSala, baraja, idToken);
                break;

            case JUGAR_NORMAL:
            default:
                activarInteraccionPregunta();
                break;
        }
    }

    private String obtenerSiguienteJugadorConCartas() {
        String candidato = obtenerSiguienteJugador(uidTurnoActual);
        for (int i = 0; i < ordenJugadoresGlobal.size(); i++) {
            List<String> mano = manos.get(candidato);
            if (mano != null && !mano.isEmpty()) return candidato;
            candidato = obtenerSiguienteJugador(candidato);
        }
        return obtenerSiguienteJugador(uidTurnoActual);
    }

    // =========================================================================
    //  FLUJO DE PREGUNTA
    // =========================================================================

    private void onZonaRivalClick(String uidRival) {
        if (!uidLocal.equals(uidTurnoActual)) { narrarPrivado(uidLocal, "No es tu turno."); return; }
        if (esperandoRobo)                    { narrarPrivado(uidLocal, "Debes robar antes de continuar."); return; }
        if (numeroSeleccionado == null)        { narrarPrivado(uidLocal, "Primero selecciona un número de tu mano."); return; }
        if (uidRival == null || uidRival.equals(uidLocal)) return;

        List<String> manoObjetivo = manos.get(uidRival);
        if (manoObjetivo == null || manoObjetivo.isEmpty()) {
            narrarPrivado(uidLocal, "Ese jugador no tiene cartas. Elige otro.");
            return;
        }

        uidJugadorObjetivo = uidRival;
        ejecutarPregunta();
    }

    private void onCartaLocalClick(String rutaCarta) {
        if (!uidLocal.equals(uidTurnoActual)) { narrarPrivado(uidLocal, "No es tu turno."); return; }
        if (esperandoRobo)                    { narrarPrivado(uidLocal, "Debes robar antes de continuar."); return; }

        int numero = juego.obtenerNumeroCarta(rutaCarta);
        numeroSeleccionado = numero;
        narrarPrivado(uidLocal, "Has seleccionado el número " + numero + ".\nAhora elige un jugador.");
    }

    private void ejecutarPregunta() {
        if (!uidLocal.equals(uidTurnoActual)) { narrarPrivado(uidLocal, "No es tu turno."); return; }
        if (esperandoRobo)                    { narrarPrivado(uidLocal, "Debes robar antes de continuar."); return; }
        if (numeroSeleccionado == null)        { narrarPrivado(uidLocal, "No has seleccionado número."); return; }
        if (uidJugadorObjetivo == null)        { narrarPrivado(uidLocal, "No has seleccionado jugador objetivo."); return; }
        if (juego == null)                     return;

        uiBloqueadaPorAccion = true;
        zonaCentro.setDisable(true);

        narrarPrivado(uidLocal, "Has preguntado a " + nombres.get(uidJugadorObjetivo)
                + "\npor el " + numeroSeleccionado + ".");

        try {
            Map<String, Object> resultado = ((JuegoPescaito) juego).preguntar(
                    uidLocal, uidJugadorObjetivo, numeroSeleccionado, manos, baraja, descarte);
            procesarResultadoPregunta(resultado);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            uidJugadorObjetivo   = null;
            uiBloqueadaPorAccion = false;
            if (uidLocal.equals(uidTurnoActual) && !esperandoRobo) activarInteraccionPregunta();
            if (!esperandoRobo) zonaCentro.setDisable(false);
        }
    }

    private void procesarResultadoPregunta(Map<String, Object> resultado) throws IOException {
        if (!uidLocal.equals(uidTurnoActual)) return;
        if (esperandoRobo) return;

        boolean acierto         = (boolean) resultado.get("acierto");
        boolean pescaito        = (boolean) resultado.get("pescaito");
        boolean debeRobar       = (boolean) resultado.get("debeRobar");
        boolean mantieneTurno   = (boolean) resultado.get("mantieneTurno");
        int     cartasRecibidas = (int)     resultado.get("cartasRecibidas");

        if (acierto) {
            narrarPrivado(uidLocal, "Has robado " + cartasRecibidas + " carta(s).");
            if (pescaito)      narrarPrivado(uidLocal, "¡Pescaito! Has completado un grupo de 4.");
            if (mantieneTurno) narrarPrivado(uidLocal, "Mantienes el turno.");
        } else {
            narrarPrivado(uidLocal, "Fallaste.");
            if (debeRobar)      narrarPrivado(uidLocal, "Debes robar una carta.");
            if (!mantieneTurno) narrarPrivado(uidLocal, "El turno pasa al siguiente jugador.");
        }

        if (pescaito) {
            int numeroPescaito = (int) resultado.get("numeroPescaito");
            bd.registrarPescaito(codigoSala, uidLocal, numeroPescaito, idToken);
            bd.actualizarDescarte(codigoSala, descarte, idToken);
            narrarPrivado(uidLocal, "¡Pescaito!");
        }

        if (debeRobar) {
            narrarPrivado(uidLocal, "Debes robar una carta porque no acertaste.");

            if (baraja.isEmpty()) {
                narrarPrivado(uidLocal, "No quedan cartas en la baraja. Pasas el turno.");
                bd.actualizarTurno(codigoSala, obtenerSiguienteJugador(uidTurnoActual), idToken);
                numeroSeleccionado           = null;
                numeroPreguntadoAntesDeRobar = null;
                esperandoRobo                = false;
                return;
            }

            numeroPreguntadoAntesDeRobar = numeroSeleccionado;
            esperandoRobo                = true;
            desactivarInteraccionPregunta();
            zonaCentro.setDisable(false);
            imgMazo.setDisable(false);
            imgMazo.setOpacity(1.0);
            uiBloqueadaPorAccion = false;
            return;
        }

        bd.actualizarMano(codigoSala, uidLocal,           manos.get(uidLocal),           idToken);
        bd.actualizarMano(codigoSala, uidJugadorObjetivo, manos.get(uidJugadorObjetivo), idToken);

        if (mantieneTurno) bd.actualizarTurno(codigoSala, uidLocal, idToken);
        else               bd.actualizarTurno(codigoSala, obtenerSiguienteJugador(uidTurnoActual), idToken);

        zonaCentro.setDisable(false);
        numeroSeleccionado = null;
        uidJugadorObjetivo = null;
    }

    private void realizarRoboManual() {
        if (!uidLocal.equals(uidTurnoActual)) { narrarPrivado(uidLocal, "No es tu turno."); return; }
        if (!esperandoRobo)                   { narrarPrivado(uidLocal, "No estás obligado a robar."); return; }

        uiBloqueadaPorAccion = true;
        zonaArriba.setDisable(true);
        zonaIzquierda.setDisable(true);
        zonaDerecha.setDisable(true);
        zonaAbajo.setDisable(true);

        try {
            ((JuegoPescaito) juego).robarCarta(uidLocal, manos, baraja);

            List<String> mano         = manos.get(uidLocal);
            String       cartaRobada  = mano.get(mano.size() - 1);
            int          numeroRobado = juego.obtenerNumeroCarta(cartaRobada);

            bd.actualizarMano(codigoSala, uidLocal, mano, idToken);
            bd.actualizarBaraja(codigoSala, baraja, idToken);

            narrarPrivado(uidLocal, "Has robado un " + numeroRobado);

            boolean haPescado = ((JuegoPescaito) juego)
                    .haPescadoAlRobar(cartaRobada, numeroPreguntadoAntesDeRobar);
            boolean pescaito  = ((JuegoPescaito) juego)
                    .esPescaitoPorRobo(uidLocal, manos, descarte);

            if (pescaito) {
                narrarPrivado(uidLocal, "¡PESCAITO!");
                bd.actualizarMano(codigoSala, uidLocal, manos.get(uidLocal), idToken);
                bd.actualizarDescarte(codigoSala, descarte, idToken);
                int numero = juego.obtenerNumeroCarta(cartaRobada);
                bd.registrarPescaito(codigoSala, uidLocal, numero, idToken);
            }

            if (haPescado) {
                narrarPrivado(uidLocal, "¡Has pescado! Mantienes turno.");
                bd.actualizarTurno(codigoSala, uidLocal, idToken);
            } else {
                narrarPrivado(uidLocal, "Pasas turno.");
                bd.actualizarTurno(codigoSala, obtenerSiguienteJugador(uidTurnoActual), idToken);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            esperandoRobo                = false;
            numeroPreguntadoAntesDeRobar = null;
            numeroSeleccionado           = null;
            uiBloqueadaPorAccion         = false;

            if (uidLocal.equals(uidTurnoActual)) {
                zonaCentro.setDisable(false);
                activarInteraccionPregunta();
            } else {
                zonaCentro.setDisable(true);
            }
        }
    }

    // =========================================================================
    //  FIN DE PARTIDA
    // =========================================================================

    private boolean esFinDePartida() {
        return juego != null && juego.haTerminado(manos, baraja, descarte);
    }

    private void finalizarPartida() {
        if (partidaFinalizada) return;

        zonaArriba.setDisable(true);
        zonaIzquierda.setDisable(true);
        zonaDerecha.setDisable(true);
        zonaAbajo.setDisable(true);
        zonaCentro.setDisable(true);

        try { bd.actualizarEstadoPartida(codigoSala, "finalizada", idToken); }
        catch (Exception e) { e.printStackTrace(); }

        partidaFinalizada = true;

        Platform.runLater(() -> {
            try {
                Map<String, Map<String, Object>> pescaitosBD =
                        bd.leerPescaitos(codigoSala, idToken);
                Map<String, Integer> puntuaciones =
                        ((JuegoPescaito) juego).calcularPuntuacionesDesdeBD(pescaitosBD);
                String uidGanador       = obtenerGanador(puntuaciones);
                String nombreGanador    = nombres.getOrDefault(uidGanador, "Jugador");
                int    pescaitosGanador = puntuaciones.getOrDefault(uidGanador, 0);
                mostrarPantallaFinal(nombreGanador, pescaitosGanador);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void mostrarPantallaFinal(String nombreGanador, int pescaitos) {
        overlayFinal.getChildren().clear();

        Label titulo  = new Label("🎉 La partida ha terminado");
        titulo.setStyle("-fx-font-size: 32px; -fx-font-weight: bold;");

        Label ganador = new Label("Ganador: " + nombreGanador + " (" + pescaitos + " pescaitos)");
        ganador.setStyle("-fx-font-size: 24px;");

        Button volverSala = new Button("Volver a la sala");
        // volverSala.setOnAction(e -> volverAlLobby());

        VBox box = new VBox(20, titulo, ganador, volverSala);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-padding: 40px;");

        StackPane.setAlignment(box, Pos.CENTER);
        overlayFinal.getChildren().add(box);
        overlayFinal.setVisible(true);
    }

    private String obtenerGanador(Map<String, Integer> puntuaciones) {
        return puntuaciones.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    // =========================================================================
    //  INTERACCIÓN UI
    // =========================================================================

    private void activarInteraccionPregunta() {
        if (uiBloqueadaPorAccion) return;
        zonaArriba.setDisable(false);    zonaArriba.setOpacity(1.0);
        zonaIzquierda.setDisable(false); zonaIzquierda.setOpacity(1.0);
        zonaDerecha.setDisable(false);   zonaDerecha.setOpacity(1.0);
        zonaAbajo.setDisable(false);     zonaAbajo.setOpacity(1.0);
    }

    private void desactivarInteraccionPregunta() {
        zonaArriba.setDisable(true);    zonaArriba.setOpacity(0.5);
        zonaIzquierda.setDisable(true); zonaIzquierda.setOpacity(0.5);
        zonaDerecha.setDisable(true);   zonaDerecha.setOpacity(0.5);
        zonaAbajo.setDisable(true);     zonaAbajo.setOpacity(0.5);
    }

    // =========================================================================
    //  SISTEMA DE MENSAJES
    // =========================================================================

    private void narrarGlobal(String texto) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("tipo", "global"); msg.put("texto", texto); msg.put("uid", null);
        try { bd.actualizarNarrador(codigoSala, msg, idToken); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private void narrarPrivado(String uidDestino, String texto) {
        if (uidDestino.equals(uidLocal)) {
            Platform.runLater(() -> encolarMensajePrivado(texto));
            return;
        }
        Map<String, Object> msg = new HashMap<>();
        msg.put("tipo", "privado"); msg.put("texto", texto); msg.put("uid", uidDestino);
        try { bd.actualizarNarrador(codigoSala, msg, idToken); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private void procesarMensajeNarrador(Map<String, Object> msg) {
        String tipo  = (String) msg.get("tipo");
        String texto = (String) msg.get("texto");
        String uid   = (String) msg.get("uid");
        if ("global".equals(tipo))
            narradorLabel.setText(texto);
        else if ("privado".equals(tipo) && uid != null && uid.equals(uidLocal))
            encolarMensajePrivado(texto);
    }

    private void encolarMensajePrivado(String texto) {
        colaPrivados.add(texto);
        if (!narrandoPrivado) narrarSiguientePrivado();
    }

    private void narrarSiguientePrivado() {
        String texto = colaPrivados.poll();
        if (texto == null) { narrandoPrivado = false; return; }
        narrandoPrivado = true;
        mostrarMensajePrivadoConCallback(texto, this::narrarSiguientePrivado);
    }

    private void mostrarMensajePrivadoConCallback(String texto, Runnable alTerminar) {
        lblInfo.setText(texto);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), lblInfo);
        fadeIn.setFromValue(0.0); fadeIn.setToValue(1.0);
        FadeTransition fadeOut = new FadeTransition(Duration.seconds(1.5), lblInfo);
        fadeOut.setFromValue(1.0); fadeOut.setToValue(0.0);
        fadeOut.setDelay(Duration.seconds(0.5));
        fadeOut.setOnFinished(e -> alTerminar.run());
        new javafx.animation.SequentialTransition(fadeIn, fadeOut).play();
    }

    // =========================================================================
    //  UTILIDADES
    // =========================================================================

    private String obtenerSiguienteJugador(String uidActual) {
        if (ordenJugadoresGlobal == null || ordenJugadoresGlobal.isEmpty()) return uidActual;
        int idx = ordenJugadoresGlobal.indexOf(uidActual);
        if (idx == -1) return ordenJugadoresGlobal.get(0);
        return ordenJugadoresGlobal.get((idx + 1) % ordenJugadoresGlobal.size());
    }

    // =========================================================================
    //  CONFIGURACIÓN DE EVENTOS
    // =========================================================================

    private void configurarEventosManoJugador() {
        zonaAbajo.setOnMouseEntered(e -> { manoAbierta = true;  redibujarManoJugador(); });
        zonaAbajo.setOnMouseExited(e  -> { manoAbierta = false; cartaSeleccionada = -1; redibujarManoJugador(); });
        zonaAbajo.setOnMouseMoved(e -> {
            int nueva = calcularCartaSeleccionada(e.getX());
            if (nueva != cartaSeleccionada) {
                cartaSeleccionada = nueva;
                dibujarAbanicoAbajo(zonaAbajo, manos.get(uidLocal));
            }
        });
    }

    private void configurarEventosRobar() {
        imgMazo.setOnMouseEntered(e -> {
            DropShadow glow = new DropShadow();
            glow.setColor(Color.LIMEGREEN); glow.setRadius(25); glow.setSpread(0.4);
            imgMazo.setEffect(glow);
        });
        imgMazo.setOnMouseExited(e -> imgMazo.setEffect(null));
        imgMazo.setOnMouseClicked(event -> {
            if (!uidLocal.equals(uidTurnoActual)) { narrarPrivado(uidLocal, "No es tu turno."); return; }
            if (!esperandoRobo)                   { narrarPrivado(uidLocal, "No puedes robar ahora."); return; }
            realizarRoboManual();
        });
    }

    private void redibujarManoJugador() {
        List<String> cartas = manos.get(uidLocal);
        if (cartas != null) dibujarAbanicoAbajo(zonaAbajo, cartas);
    }

    private int calcularCartaSeleccionada(double mouseX) {
        for (int i = 0; i < zonaAbajo.getChildren().size(); i++) {
            Bounds b = zonaAbajo.getChildren().get(i).getBoundsInParent();
            if (mouseX >= b.getMinX() && mouseX <= b.getMaxX()) return i;
        }
        return -1;
    }

    // =========================================================================
    //  INTERFAZ
    // =========================================================================

    private void actualizarInterfaz() {
        limpiarZonas();
        cargarNombresJugadores();
        colocarJugadores();
        mostrarMazo();
        mostrarDescarte();
        dibujarAbanicoAbajo(zonaAbajo, manos.get(uidLocal));
    }

    private void limpiarZonas() {
        zonaArriba.getChildren().clear();
        zonaIzquierda.getChildren().clear();
        zonaDerecha.getChildren().clear();
        zonaAbajo.getChildren().clear();
    }

    private void cargarNombresJugadores() {
        for (String uid : manos.keySet()) {
            try {
                String json = db.leerNodo("usuarios/" + uid + "/nombre", idToken);
                nombres.put(uid, json.replace("\"", ""));
            } catch (Exception e) { /* ignorar */ }
        }
    }

    private void colocarJugadores() {
        if (ordenJugadoresGlobal == null || ordenJugadoresGlobal.isEmpty()) return;
        int idxLocal = ordenJugadoresGlobal.indexOf(uidLocal);
        if (idxLocal == -1) return;

        List<String> ordenRotado = new ArrayList<>();
        for (int i = 0; i < ordenJugadoresGlobal.size(); i++)
            ordenRotado.add(ordenJugadoresGlobal.get((idxLocal + i) % ordenJugadoresGlobal.size()));

        lblAbajo.setText(nombres.get(ordenRotado.get(0)));

        String derecha   = ordenRotado.size() > 1 ? ordenRotado.get(1) : null;
        String arriba    = ordenRotado.size() > 2 ? ordenRotado.get(2) : null;
        String izquierda = ordenRotado.size() > 3 ? ordenRotado.get(3) : null;

        if (derecha != null) {
            uidJugadorDerecha = derecha;
            colocarEnDerecha(derecha);
            lblDerecha.setText(nombres.get(derecha));
            lblDerecha.setRotate(-90);
            final String d = derecha;
            zonaDerecha.setOnMouseClicked(e -> onZonaRivalClick(d));
        }
        if (arriba != null) {
            uidJugadorArriba = arriba;
            colocarEnArriba(arriba);
            lblArriba.setText(nombres.get(arriba));
            final String a = arriba;
            zonaArriba.setOnMouseClicked(e -> onZonaRivalClick(a));
        }
        if (izquierda != null) {
            uidJugadorIzquierda = izquierda;
            colocarEnIzquierda(izquierda);
            lblIzquierda.setText(nombres.get(izquierda));
            lblIzquierda.setRotate(90);
            final String iz = izquierda;
            zonaIzquierda.setOnMouseClicked(e -> onZonaRivalClick(iz));
        }
    }

    private void mostrarMazo() {
        if (baraja.isEmpty()) { imgMazo.setVisible(false); return; }
        imgMazo.setImage(new Image("/ui/graphicResources/cartas/parteTrasera.png"));
        imgMazo.setVisible(true);
    }

    private void mostrarDescarte() {
        if (descarte.isEmpty()) { imgDescarte.setVisible(false); return; }
        imgDescarte.setImage(new Image(descarte.get(descarte.size() - 1)));
        imgDescarte.setVisible(true);
    }

    private void colocarEnArriba(String uid)    { dibujarAbanicoArriba(zonaArriba,       manos.get(uid)); }
    private void colocarEnIzquierda(String uid) { dibujarAbanicoIzquierda(zonaIzquierda, manos.get(uid)); }
    private void colocarEnDerecha(String uid)   { dibujarAbanicoDerecha(zonaDerecha,     manos.get(uid)); }

    public void configurarLayoutEscena(Stage stage) {
        final double OAX = -40, OAY = -10, ORRX = -40, ORRY = -110;
        final double OIX = -70, OIY = 100, ODX = 0, ODY = -210, OCX = 0, OCY = 0;

        Runnable recolocar = () -> {
            double w = stage.getWidth(), h = stage.getHeight();
            double cx = w / 2, cy = h / 2;
            zonaCentro.setLayoutX(cx - zonaCentro.getWidth()   / 2 + OCX);
            zonaCentro.setLayoutY(cy - zonaCentro.getHeight()  / 2 + OCY);
            zonaAbajo.setLayoutX(cx  - zonaAbajo.getWidth()    / 2 + OAX);
            zonaAbajo.setLayoutY(h   - zonaAbajo.getHeight()   - 20 + OAY);
            zonaArriba.setLayoutX(cx - zonaArriba.getWidth()   / 2 + ORRX);
            zonaArriba.setLayoutY(20 + ORRY);
            zonaIzquierda.setLayoutX(20 + OIX);
            zonaIzquierda.setLayoutY(cy - zonaIzquierda.getHeight() / 2 + OIY);
            zonaDerecha.setLayoutX(w - zonaDerecha.getWidth()  - 20 + ODX);
            zonaDerecha.setLayoutY(cy - zonaDerecha.getHeight() / 2 + ODY);
        };

        Platform.runLater(recolocar);
        stage.widthProperty().addListener((obs, o, n) -> recolocar.run());
        stage.heightProperty().addListener((obs, o, n) -> recolocar.run());
    }

    // =========================================================================
    //  DIBUJO DE ABANICOS (sin cambios respecto al original)
    // =========================================================================

    private void dibujarAbanicoAbajo(Pane zona, List<String> cartas) {
        zona.getChildren().clear();
        if (cartas == null || cartas.isEmpty()) return;
        int n = cartas.size();

        if (n == 1) {
            Image imgRaw = new Image(getClass().getResourceAsStream(cartas.get(0)));
            ImageView img = new ImageView(imgRaw);
            img.setFitHeight(140); img.setPreserveRatio(true);
            double y = BASE_Y - Math.cos(0) * (ALTURA_BASE + 3) - 20;
            double centroPaneX = zona.getWidth() / 2;
            img.setLayoutX(centroPaneX - img.getFitWidth() / 2 - 10);
            img.setLayoutY(y);
            String ruta = cartas.get(0);
            img.setOnMouseClicked(e -> onCartaLocalClick(ruta));
            if (manoAbierta) {
                img.setScaleX(1.25); img.setScaleY(1.25); img.setTranslateY(-25);
                DropShadow g = new DropShadow(); g.setColor(Color.RED); g.setRadius(25); img.setEffect(g);
            }
            zona.getChildren().add(img);
            return;
        }

        double radio       = RADIO_BASE    + (n * 20) + (manoAbierta ? 60 : 0);
        double anguloTotal = ANGULO_BASE   + (n * 2)  + (manoAbierta ? 20 : 0);
        double alturaArco  = ALTURA_BASE   + (n * 3)  + (manoAbierta ? 15 : 0);
        double divisorRot  = ROTACION_BASE + (n * 0.05);
        double anguloInic  = -anguloTotal / 2;
        double centroPaneX = zona.getWidth() / 2;

        List<Double> xs = new ArrayList<>(), ys = new ArrayList<>(), rots = new ArrayList<>();
        List<ImageView> imgs = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            ImageView img = new ImageView(new Image(cartas.get(i)));
            img.setFitHeight(140); img.setPreserveRatio(true);
            String ruta = cartas.get(i);
            img.setOnMouseClicked(e -> onCartaLocalClick(ruta));
            double angulo = anguloInic + (anguloTotal / (n - 1)) * i;
            double rad = Math.toRadians(angulo);
            double x = Math.sin(rad) * radio, y = BASE_Y - Math.cos(rad) * alturaArco;
            double rot = angulo / divisorRot;
            double compY = Math.sin(Math.toRadians(Math.abs(rot))) * (img.getFitHeight() * 0.35);
            x -= img.getFitHeight() / 4; y += compY;
            if (manoAbierta && cartaSeleccionada != -1) {
                int dist = Math.abs(i - cartaSeleccionada);
                double sep = dist * 12.0;
                if (i < cartaSeleccionada) x -= sep; else if (i > cartaSeleccionada) x += sep;
            }
            xs.add(x); ys.add(y); rots.add(rot); imgs.add(img);
        }

        double minX = xs.stream().min(Double::compare).get();
        double maxX = xs.stream().max(Double::compare).get();
        double offsetX = centroPaneX - (minX + maxX) / 2;

        for (int i = 0; i < n; i++) {
            ImageView img = imgs.get(i);
            img.setLayoutX(xs.get(i) + offsetX); img.setLayoutY(ys.get(i)); img.setRotate(rots.get(i));
            zona.getChildren().add(img);
            if (manoAbierta && i == cartaSeleccionada) {
                img.setScaleX(1.25); img.setScaleY(1.25); img.setTranslateY(-25);
                DropShadow g = new DropShadow(); g.setColor(Color.RED); g.setRadius(25); img.setEffect(g);
            } else {
                img.setScaleX(1.0); img.setScaleY(1.0); img.setTranslateY(0); img.setEffect(null);
            }
        }
    }

    private void dibujarAbanicoArriba(Pane zona, List<String> cartas) {
        zona.getChildren().clear();
        if (cartas == null || cartas.isEmpty()) return;
        int n = cartas.size();
        if (n == 1) {
            ImageView img = new ImageView(new Image("/ui/graphicResources/cartas/parteTrasera.png"));
            img.setFitHeight(120); img.setPreserveRatio(true);
            double y = (zona.getHeight() - 40) - Math.cos(0) * (ALTURA_BASE + 3) - 25;
            double cx = zona.getWidth() / 2, cy = zona.getHeight() / 2;
            double x0 = cx - img.getFitWidth() / 2;
            img.setLayoutX(2 * cx - x0); img.setLayoutY(2 * cy - y); img.setRotate(180);
            zona.getChildren().add(img);
            return;
        }
        double radio = RADIO_BASE + (n * 20), anguloTotal = ANGULO_BASE + (n * 2), alturaArco = ALTURA_BASE + (n * 3);
        double divisorRot = ROTACION_BASE + (n * 0.05), anguloInic = -anguloTotal / 2;
        double centroPaneX = zona.getWidth() / 2, cx = zona.getWidth() / 2, cy = zona.getHeight() / 2;
        double baseYLocal = zona.getHeight() - 40;
        List<Double> xs = new ArrayList<>(), ys = new ArrayList<>(), rots = new ArrayList<>();
        List<ImageView> imgs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ImageView img = new ImageView(new Image("/ui/graphicResources/cartas/parteTrasera.png"));
            img.setFitHeight(120); img.setPreserveRatio(true);
            double angulo = anguloInic + (anguloTotal / (n - 1)) * i;
            double rad = Math.toRadians(angulo);
            double x = Math.sin(rad) * radio, y = baseYLocal - Math.cos(rad) * alturaArco;
            double rot = angulo / divisorRot;
            x -= img.getFitHeight() / 4;
            y += Math.sin(Math.toRadians(Math.abs(rot))) * (img.getFitHeight() * 0.35);
            xs.add(x); ys.add(y); rots.add(rot); imgs.add(img);
        }
        double minX = xs.stream().min(Double::compare).get(), maxX = xs.stream().max(Double::compare).get();
        double offsetX = centroPaneX - (minX + maxX) / 2;
        for (int i = 0; i < n; i++) {
            ImageView img = imgs.get(i);
            double x0 = xs.get(i) + offsetX, y0 = ys.get(i);
            img.setLayoutX(2 * cx - x0); img.setLayoutY(2 * cy - y0); img.setRotate(rots.get(i) + 180);
            zona.getChildren().add(img);
        }
    }

    private void dibujarAbanicoIzquierda(Pane zona, List<String> cartas) {
        zona.getChildren().clear();
        if (cartas == null || cartas.isEmpty()) return;
        int n = cartas.size();
        if (n == 1) {
            ImageView img = new ImageView(new Image("/ui/graphicResources/cartas/parteTrasera.png"));
            img.setFitHeight(120); img.setPreserveRatio(true);
            double cx = zona.getWidth() / 2, cy = zona.getHeight() / 2;
            double y0 = cy - img.getFitHeight() / 2 + 40, x0 = -40.0;
            double dx = x0 - cx, dy = y0 - cy;
            img.setLayoutX(cx - dy); img.setLayoutY(cy + dx); img.setRotate(90);
            zona.getChildren().add(img);
            return;
        }
        double radio = RADIO_BASE + (n * 20), anguloTotal = ANGULO_BASE + (n * 2), alturaArco = ALTURA_BASE + (n * 3);
        double divisorRot = ROTACION_BASE + (n * 0.05), anguloInic = -anguloTotal / 2;
        double centroPaneY = zona.getHeight() / 2, cx = zona.getWidth() / 2, cy = zona.getHeight() / 2;
        double baseYLocal = zona.getHeight() / 2 + 80;
        List<Double> xs = new ArrayList<>(), ys = new ArrayList<>(), rots = new ArrayList<>();
        List<ImageView> imgs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ImageView img = new ImageView(new Image("/ui/graphicResources/cartas/parteTrasera.png"));
            img.setFitHeight(120); img.setPreserveRatio(true);
            double angulo = anguloInic + (anguloTotal / (n - 1)) * i;
            double rad = Math.toRadians(angulo);
            double x = Math.sin(rad) * radio, y = baseYLocal - Math.cos(rad) * alturaArco;
            double rot = angulo / divisorRot;
            x -= img.getFitHeight() / 4;
            y += Math.sin(Math.toRadians(Math.abs(rot))) * (img.getFitHeight() * 0.35);
            xs.add(x); ys.add(y); rots.add(rot); imgs.add(img);
        }
        double minY = ys.stream().min(Double::compare).get(), maxY = ys.stream().max(Double::compare).get();
        double offsetY = centroPaneY - (minY + maxY) / 2;
        for (int i = 0; i < n; i++) {
            ImageView img = imgs.get(i);
            double x0 = xs.get(i), y0 = ys.get(i) + offsetY;
            double dx = x0 - cx, dy = y0 - cy;
            img.setLayoutX(cx - dy); img.setLayoutY(cy + dx); img.setRotate(rots.get(i) + 90);
            zona.getChildren().add(img);
        }
    }

    private void dibujarAbanicoDerecha(Pane zona, List<String> cartas) {
        zona.getChildren().clear();
        if (cartas == null || cartas.isEmpty()) return;
        int n = cartas.size();
        if (n == 1) {
            ImageView img = new ImageView(new Image("/ui/graphicResources/cartas/parteTrasera.png"));
            img.setFitHeight(120); img.setPreserveRatio(true);
            double cx = zona.getWidth() / 2, cy = zona.getHeight() / 2;
            double y0 = cy - img.getFitHeight() / 2 + 15, x0 = 0.0;
            double dx = x0 - cx, dy = y0 - cy;
            img.setLayoutX(cx + dy); img.setLayoutY(cy - dx); img.setRotate(-90);
            zona.getChildren().add(img);
            return;
        }
        double radio = RADIO_BASE + (n * 20), anguloTotal = ANGULO_BASE + (n * 2), alturaArco = ALTURA_BASE + (n * 3);
        double divisorRot = ROTACION_BASE + (n * 0.05), anguloInic = -anguloTotal / 2;
        double centroPaneY = zona.getHeight() / 2, cx = zona.getWidth() / 2, cy = zona.getHeight() / 2;
        double baseYLocal = zona.getHeight() / 2 + 80;
        List<Double> xs = new ArrayList<>(), ys = new ArrayList<>(), rots = new ArrayList<>();
        List<ImageView> imgs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ImageView img = new ImageView(new Image("/ui/graphicResources/cartas/parteTrasera.png"));
            img.setFitHeight(120); img.setPreserveRatio(true);
            double angulo = anguloInic + (anguloTotal / (n - 1)) * i;
            double rad = Math.toRadians(angulo);
            double x = Math.sin(rad) * radio, y = baseYLocal - Math.cos(rad) * alturaArco;
            double rot = angulo / divisorRot;
            x -= img.getFitHeight() / 4;
            y += Math.sin(Math.toRadians(Math.abs(rot))) * (img.getFitHeight() * 0.35);
            xs.add(x); ys.add(y); rots.add(rot); imgs.add(img);
        }
        double minY = ys.stream().min(Double::compare).get(), maxY = ys.stream().max(Double::compare).get();
        double offsetY = centroPaneY - (minY + maxY) / 2;
        for (int i = 0; i < n; i++) {
            ImageView img = imgs.get(i);
            double x0 = xs.get(i), y0 = ys.get(i) + offsetY;
            double dx = x0 - cx, dy = y0 - cy;
            img.setLayoutX(cx + dy); img.setLayoutY(cy - dx); img.setRotate(rots.get(i) - 90);
            zona.getChildren().add(img);
        }
    }
}