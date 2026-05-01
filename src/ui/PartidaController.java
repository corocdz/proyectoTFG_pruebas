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
import java.util.function.Consumer;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
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
import partidaUTIL.JuegoYusa;

public class PartidaController {

    private static final double RADIO_BASE = 577.8;
    private static final double ANGULO_BASE = 5.0;
    private static final double ALTURA_BASE = 80.0;
    private static final double ROTACION_BASE = 0.8;
    private static final double BASE_Y = 200.0;

    @FXML
    private Pane zonaArriba;
    @FXML
    private Pane zonaIzquierda;
    @FXML
    private Pane zonaDerecha;
    @FXML
    private Pane zonaAbajo;

    @FXML
    private Label lblArriba;
    @FXML
    private Label lblIzquierda;
    @FXML
    private Label lblDerecha;
    @FXML
    private Label lblAbajo;
    @FXML
    private Label narradorLabel;
    @FXML
    private Label lblInfo;

    @FXML
    private AnchorPane zonaCentro;
    @FXML
    private ImageView imgMazo;
    @FXML
    private ImageView imgDescarte;

    @FXML
    private StackPane overlayFinal;

    @FXML
    private HBox panelDecisionPrivada;
    @FXML
    private Button btnOpcion1;
    @FXML
    private Button btnOpcion2;

    private FirebaseDatabaseService db = new FirebaseDatabaseService();
    private BDPartidaService bd = new BDPartidaService(db);
    private String idToken; // si lo tienes, si no lo añadimos luego

    private String codigoSala;
    private String uidLocal;

    private Map<String, List<String>> manos = new HashMap<>();
    private List<String> baraja = new ArrayList<>();
    private List<String> descarte = new ArrayList<>();

    private boolean esperandoRobo = false;
    private boolean uiBloqueadaPorAccion = false;
    private boolean repartoInicialHecho = false;
    private boolean partidaFinalizada = false;

    private Juego juego;
    private List<String> ordenJugadoresGlobal = new ArrayList<>();
    private List<String> ordenRondaActual;
    private boolean rondaEnCurso = false;
    private String uidTurnoActual;
    private String uidJugadorObjetivo = null;
    private String uidJugadorArriba;
    private String uidJugadorIzquierda;
    private String uidJugadorDerecha;

    // animaciones
    private Queue<String> colaPrivados = new LinkedList<>();
    private boolean narrandoPrivado = false;

    /**
     * PESCAITO
     */
    private Integer numeroSeleccionado = null;
    private Integer numeroPreguntadoAntesDeRobar = null;

    //YUSA
    // variable que nos permite robar carta del jugador local a la baraja (ultimo jugador en ronda NORMAL.)
    private boolean esperandoRoboYusa = false;
    private boolean esperandoDecisionPropia = false;
    private int numeroRondaYusa = 0;
    private long ultimoResolverTimestamp = -1L;
    private PauseTransition pausaResolverRonda = null; // para poder cancelarla si hace falta
    private Map<String, String> snapshotCartasRonda = new HashMap<>();

    private boolean manoAbierta = false;
    private int cartaSeleccionada = -1; // índice de la carta bajo el ratón

    // NUEVO: mapa de nombres reales
    private Map<String, String> nombres = new HashMap<>();

    public void init(String codigoSala, String uidLocal, String idToken) {
        this.codigoSala = codigoSala;
        this.uidLocal = uidLocal;
        this.idToken = idToken;

        cargarJuegoDesdeBD();
        configurarEventosManoJugador();
        cargarOrdenJugadoresGlobal();
        configurarEventosRobar();

        imgMazo.setOnMouseClicked(event -> {

            // 1. ¿Es mi turno?
            if (!uidLocal.equals(uidTurnoActual)) {
                narrarPrivado(uidLocal, "No es tu turno.");
                return;
            }

            if (esPescaito()) {
                // 2. ¿Estoy obligado a robar?
                if (!esperandoRobo) {
                    narrarPrivado(uidLocal, "No puedes robar.");
                    return;
                }

                // 3. OK → robo manual
                realizarRoboManual();
            }

            if (esYusa()) {
                if (!esperandoRoboYusa) {
                    narrarPrivado(uidLocal, "No es momento de robar.");
                    return;
                }

                //realizarRoboYusa()?
                //esperandoRoboYusa = false;
                //return;
            }

        });

        bd.escucharEstadoPartida(codigoSala, idToken, estado -> {

            String limpio = estado == null ? "" : estado.replace("\"", "").trim().toLowerCase();
            if (!limpio.equals("finalizada")) {
                return;
            }

            // Si este cliente ya procesó el fin (fue quien llamó finalizarPartida),
            // no volver a mostrar la pantalla
            if (partidaFinalizada) {
                return;
            }

            Platform.runLater(() -> {
                partidaFinalizada = true;  // marcar aquí para los demás clientes

                if (esPescaito()) {
                    try {
                        Map<String, Map<String, Object>> pescaitosBD
                                = bd.leerPescaitos(codigoSala, idToken);
                        Map<String, Integer> puntuaciones
                                = ((JuegoPescaito) juego).calcularPuntuacionesDesdeBD(pescaitosBD);
                        String uidGanador = obtenerGanador(puntuaciones);
                        String nombreGanador = nombres.getOrDefault(uidGanador, "Jugador");
                        int pescaitosGanador = puntuaciones.getOrDefault(uidGanador, 0);
                        mostrarPantallaFinal(nombreGanador, pescaitosGanador);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (esYusa()) {
                    // mostrar pantalla final de Yusa más adelante
                }
            });
        });

        bd.escucharTurnoActualRonda(codigoSala, idToken, turnoRonda -> {
            Platform.runLater(() -> {
                if (!esYusa()) {
                    return;
                }
                if (!turnoRonda.equals(uidLocal)) {
                    return;
                }

                // Si soy el director Y ya mostré mis botones, no repetir
                if (uidLocal.equals(uidTurnoActual) && esperandoDecisionPropia) {
                    return;
                }

                // Soy un cliente no-director y me toca decidir
                narrarPrivado(uidLocal, "¿Te gusta tu carta?");
                mostrarBotonesDecision("Me la quedo", "La cambio",
                        this::notificarDecisionAlHost);
            });
        });

        bd.escucharDecisionesRonda(codigoSala, idToken, decisiones -> {
            Platform.runLater(() -> {
                if (!esYusa()) {
                    return;
                }
                // Solo el director de ronda (uidTurnoActual) procesa las decisiones
                if (!uidLocal.equals(uidTurnoActual)) {
                    return;
                }
                if (ordenRondaActual == null || ordenRondaActual.isEmpty()) {
                    return;
                }

                String jugadorEsperado = ordenRondaActual.get(0);
                Object decisionObj = decisiones.get(jugadorEsperado);
                if (decisionObj == null) {
                    return;
                }

                String decision = decisionObj.toString();
                if ("PROCESADO".equals(decision)) {
                    return; // ya procesada, ignorar
                }
                // ¿Es la decisión del último jugador?
                boolean esDecisionUltimo = decision.startsWith("QUEDAR_ULTIMO")
                        || decision.startsWith("ROBAR_ULTIMO");

                if (esDecisionUltimo) {
                    procesarDecisionUltimoRecibida(jugadorEsperado, decision);
                } else {
                    // Decisión normal: intercambiar si toca y avanzar
                    if ("CAMBIAR".equals(decision) && ordenRondaActual.size() > 1) {
                        String siguiente = ordenRondaActual.get(1);
                        ((JuegoYusa) juego).intercambiarCartas(jugadorEsperado, siguiente, manos);
                        try {
                            bd.actualizarMano(codigoSala, jugadorEsperado,
                                    manos.get(jugadorEsperado), idToken);
                            bd.actualizarMano(codigoSala, siguiente,
                                    manos.get(siguiente), idToken);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    avanzarAlSiguienteJugadorNormal();
                }
            });
        });

        // Listener: último jugador de la ronda (para botones especiales)
        bd.escucharUltimoJugadorRonda(codigoSala, idToken, ultimoUid -> {
            Platform.runLater(() -> {
                if (!esYusa()) {
                    return;
                }
                // Solo actuar si soy yo el último Y no soy el director de ronda
                // (el director ya gestiona sus propios botones desde avanzar...)
                if (!ultimoUid.equals(uidLocal)) {
                    return;
                }
                if (uidLocal.equals(uidTurnoActual)) {
                    return; // el director lo hace por su cuenta
                }
                narrarPrivado(uidLocal,
                        "Eres el último. ¿Te quedas tu carta o robas de la baraja?");
                mostrarBotonesDecision("Me la quedo", "Robo de la baraja",
                        this::notificarDecisionUltimoAlDirector);
            });
        });

        // Listener: el director de ronda publicó que toca resolver
        // TODOS los clientes muestran las cartas 5 segundos y luego
        // el DIRECTOR ejecuta el cálculo de vidas.
        bd.escucharResolverRonda(codigoSala, idToken, timestamp -> {
            Platform.runLater(() -> {
                if (!esYusa()) {
                    return;
                }

                // No procesar el mismo timestamp dos veces
                if (timestamp == ultimoResolverTimestamp) {
                    return;
                }
                ultimoResolverTimestamp = timestamp;

                // Cancelar cualquier pausa anterior que pudiera estar corriendo
                if (pausaResolverRonda != null) {
                    pausaResolverRonda.stop();
                }

                // Todos muestran las cartas
                mostrarCartasDeRonda();

                pausaResolverRonda = new PauseTransition(Duration.seconds(5));
                pausaResolverRonda.setOnFinished(ev -> {
                    ocultarCartasDeRonda();
                    pausaResolverRonda = null;

                    // Solo el director calcula
                    if (uidLocal.equals(uidTurnoActual)) {
                        resolverFinDeRondaNormal();
                    }
                });
                pausaResolverRonda.play();
            });
        });

        bd.escucharNarrador(codigoSala, idToken, mensaje -> {
            Platform.runLater(() -> procesarMensajeNarrador(mensaje));
        });

        bd.escucharTurno(codigoSala, idToken, nuevoTurno -> {
            Platform.runLater(() -> {
                uidTurnoActual = nuevoTurno;

                if (esPescaito()) {
                    String nombre = nombres.get(uidTurnoActual);
                    narrarGlobal("Turno de: " + nombre);
                    // ← NO llamar a iniciarTurnoLocal() aquí.
                    // iniciarTurnoLocal() ya se llama desde actualizarDesdeModelo()
                    // cuando Firebase notifica el cambio de manos/baraja.
                }

                if (esYusa()) {
                    if (!rondaEnCurso && uidLocal.equals(uidTurnoActual)) {
                        try {
                            iniciarRondaYusa();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        });

    }

    private void cargarJuegoDesdeBD() {
        try {

            Object modoObj = bd.leerCampo(codigoSala, "modo", idToken);

            if (modoObj == null) {
                System.out.println("No se ha encontrado el modo de juego en la partida.");
                return;
            }

            String modo = modoObj.toString();
            System.out.println("Modo de juego cargado: " + modo);

            switch (modo) {
                case "Pescaito":
                    juego = new JuegoPescaito();
                    break;
                case "Yusa":
                    juego = new JuegoYusa();
                    // NO llamar a inicializarJugadoresVivos aquí; se hace después de cargarOrdenJugadoresGlobal
                    break;
                default:
                    System.out.println("Modo de juego no reconocido: " + modo);
            }

            // 🔥 Ahora cargamos toda la partida
            Map<String, Object> partida = bd.leerPartida(codigoSala, idToken);
            if (partida == null) {
                System.out.println("No se ha encontrado el nodo partida.");
                return;
            }

            // manos, baraja, descarte
            Map<String, List<String>> manosBD = (Map<String, List<String>>) partida.get("manos");
            List<String> barajaBD = (List<String>) partida.get("baraja");
            List<String> descarteBD = (List<String>) partida.get("descarte");

            // Pasamos al modelo interno del controlador
            actualizarDesdeModelo(manosBD, barajaBD, descarteBD);

            // 🔥 Preparar estado inicial (reparto si hace falta)
            prepararEstadoInicial(partida);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean esPescaito() {
        return juego instanceof JuegoPescaito;
    }

    private boolean esYusa() {
        return juego instanceof JuegoYusa;
    }

    private void cargarOrdenJugadoresGlobal() {
        try {
            String json = db.leerNodo("salas/" + codigoSala + "/jugadores", idToken);

            if (json == null || json.equals("null")) {
                System.out.println("No se encontró el nodo jugadores.");
                return;
            }

            Map<String, Object> mapa = new Gson().fromJson(json, Map.class);

            ordenJugadoresGlobal.clear();
            ordenJugadoresGlobal.addAll(mapa.keySet()); // Firebase mantiene orden de inserción

            System.out.println("Orden global de jugadores: " + ordenJugadoresGlobal);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void prepararEstadoInicial(Map<String, Object> partida) {

        if (juego == null) {
            System.out.println("Juego no inicializado, no se puede preparar estado inicial.");
            return;
        }

        // Solo si es Yusa y los jugadoresVivos están vacíos
        if (esYusa() && ((JuegoYusa) juego).getJugadoresVivos().isEmpty()) {
            ((JuegoYusa) juego).inicializarJugadoresVivos(new ArrayList<>(manos.keySet()));
        }
        juego.iniciarPartida(manos, baraja);

        // 1. Si ya hay cartas en alguna mano → el reparto ya ocurrió, nada que hacer
        boolean manosVacias = true;
        for (List<String> mano : manos.values()) {
            if (mano != null && !mano.isEmpty()) {
                manosVacias = false;
                break;
            }
        }

        if (!manosVacias) {
            System.out.println("Las manos ya están repartidas. No se hace nada.");
            actualizarInterfaz();
            return;
        }

        // 2. Solo el host reparte (para que no lo hagan todos a la vez)
        try {
            String uidHost = db.leerNodo("salas/" + codigoSala + "/host", idToken)
                    .replace("\"", "");
            boolean soyHost = uidLocal.equals(uidHost);

            if (!soyHost) {
                System.out.println("No soy el host, espero a que el host reparta.");
                actualizarInterfaz();
                return;
            }

            System.out.println("Soy el host y las manos están vacías. Repartiendo cartas...");

            // 3. Leer jugadores de la sala y añadirlos al mapa de manos
            //    para que el juego sepa a quién repartir
            if (manos.isEmpty()) {
                String jsonSala = db.leerNodo("salas/" + codigoSala, idToken);
                Map<String, Object> sala = new Gson().fromJson(jsonSala, Map.class);
                Map<String, Object> jugadores = (Map<String, Object>) sala.get("jugadores");

                for (String uid : jugadores.keySet()) {
                    manos.put(uid, null); // null → iniciarPartida() lo convierte en lista vacía
                }
            }

            // 4. El juego se encarga de poblar y repartir (antes eran 2 pasos separados aquí)
            juego.iniciarPartida(manos, baraja);

            // 5. Guardar resultado en Firebase (responsabilidad del controlador)
            for (String uid : manos.keySet()) {
                bd.actualizarMano(codigoSala, uid, manos.get(uid), idToken);
            }
            bd.actualizarBaraja(codigoSala, baraja, idToken);

            // 6. Redibujar UI
            actualizarInterfaz();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error preparando estado inicial.");
        }
    }

    private void configurarEventosManoJugador() {

        zonaAbajo.setOnMouseEntered(e -> {
            manoAbierta = true;
            redibujarManoJugador();
        });

        zonaAbajo.setOnMouseExited(e -> {
            manoAbierta = false;
            cartaSeleccionada = -1;
            redibujarManoJugador();
        });

        zonaAbajo.setOnMouseMoved(e -> {
            double mouseX = e.getX();
            int nuevaSeleccion = calcularCartaSeleccionada(mouseX);

            // ❗ Solo redibujar si la selección CAMBIA
            if (nuevaSeleccion != cartaSeleccionada) {
                cartaSeleccionada = nuevaSeleccion;
                dibujarAbanicoAbajo(zonaAbajo, manos.get(uidLocal));
            }
        });
    }

    private void configurarEventosRobar() {

        imgMazo.setOnMouseEntered(e -> {
            DropShadow glow = new DropShadow();
            glow.setColor(Color.LIMEGREEN);
            glow.setRadius(25);
            glow.setSpread(0.4);
            imgMazo.setEffect(glow);
        });

        imgMazo.setOnMouseExited(e -> {
            imgMazo.setEffect(null);
        });

        imgMazo.setOnMouseClicked(e -> onClickRobar()); // 🔥 NUEVO
    }

    private void onClickRobar() {
        if (juego == null) {
            System.out.println("Juego no inicializado todavía. No se puede robar.");
            return;
        }

        // Aquí, más adelante, haremos:
        // 1. Leer estado desde Firebase
        // 2. juego.puedeRobar(...)
        // 3. juego.robarCarta(...)
        // 4. Guardar cambios en Firebase
        // 5. Refrescar UI
        System.out.println("Click en mazo: aquí delegaremos en juego.robarCarta() cuando esté implementado.");
    }

    private void redibujarManoJugador() {
        List<String> cartasJugador = manos.get(uidLocal);
        if (cartasJugador != null) {
            dibujarAbanicoAbajo(zonaAbajo, cartasJugador);
        }
    }

    private int calcularCartaSeleccionada(double mouseX) {

        for (int i = 0; i < zonaAbajo.getChildren().size(); i++) {
            Node n = zonaAbajo.getChildren().get(i);
            Bounds b = n.getBoundsInParent();

            // Si el ratón está dentro del ancho de la carta → seleccionada
            if (mouseX >= b.getMinX() && mouseX <= b.getMaxX()) {
                return i;
            }
        }

        // Si no está encima de ninguna carta → no hay selección
        return -1;
    }

    private void onCartaLocalClick(String rutaCarta) {
        // 1. Solo si es mi turno
        if (!uidLocal.equals(uidTurnoActual)) { // o turnoActual, pero siempre el mismo en todo el controlador
            narrarPrivado(uidLocal, "No es tu turno.");
            return;
        }

        if (esPescaito()) {

            // 2. No puedes elegir número si estás obligado a robar
            if (esperandoRobo) {
                narrarPrivado(uidLocal, "Debes robar antes de continuar.");
                return;
            }

            // 3. Seleccionar número
            int numero = juego.obtenerNumeroCarta(rutaCarta);
            numeroSeleccionado = numero;

            narrarPrivado(uidLocal, "Has seleccionado el número " + numero + ".\nAhora elige un jugador.");

        }

        if (esYusa()) {
            return; // de momento
        }

    }

    public void actualizarDesdeModelo(Map<String, List<String>> manos,
            List<String> baraja,
            List<String> descarte) throws IOException {

        if (partidaFinalizada) {
            return;
        }

        if (manos == null) {
            manos = new HashMap<>();
        }

        // Normalizar manos: convertir ["EMPTY"] → lista vacía real
        for (Map.Entry<String, List<String>> entry : manos.entrySet()) {
            List<String> lista = entry.getValue();
            if (lista != null && lista.size() == 1 && "EMPTY".equals(lista.get(0))) {
                entry.setValue(new ArrayList<>());
            }
        }

        if (baraja == null) {
            baraja = new ArrayList<>();
        }
        if (descarte == null) {
            descarte = new ArrayList<>();
        }

        this.manos.clear();
        this.manos.putAll(manos);

        this.baraja.clear();
        this.baraja.addAll(baraja);

        this.descarte.clear();
        this.descarte.addAll(descarte);

        // Leer turno actual
        try {
            String turno = db.leerNodo("salas/" + codigoSala + "/partida/turno", idToken);
            if (turno != null) {
                turno = turno.replace("\"", "");
                uidTurnoActual = turno;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 🔥 Detectar si el reparto inicial ya ocurrió
        int totalCartas = 0;
        for (List<String> mano : this.manos.values()) {
            if (mano != null) {
                totalCartas += mano.size();
            }
        }

        if (!repartoInicialHecho && totalCartas > 0) {
            repartoInicialHecho = true;
        }

        // Pintar UI SIEMPRE
        actualizarInterfaz();

        // 🔥 Si es mi turno → iniciar turno local
        if (uidLocal.equals(uidTurnoActual)) {
            iniciarTurnoLocal();
        }
    }

    private void actualizarInterfaz() {
        limpiarZonas();
        cargarNombresJugadores();
        colocarJugadores();
        mostrarMazo();
        mostrarDescarte();

        // TU MANO
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
                // json será algo como: "Pepe"
                String nombre = json.replace("\"", ""); // quitar comillas
                nombres.put(uid, nombre);
            } catch (Exception e) {
                System.out.println("Error leyendo nombre de " + uid + ": " + e.getMessage());
            }
        }
    }

    private void colocarJugadores() {

        if (ordenJugadoresGlobal == null || ordenJugadoresGlobal.isEmpty()) {
            return;
        }

        // 1. Rotar la lista para que el local sea el primero
        int idxLocal = ordenJugadoresGlobal.indexOf(uidLocal);
        if (idxLocal == -1) {
            return;
        }

        List<String> ordenRotado = new ArrayList<>();
        for (int i = 0; i < ordenJugadoresGlobal.size(); i++) {
            int idx = (idxLocal + i) % ordenJugadoresGlobal.size();
            ordenRotado.add(ordenJugadoresGlobal.get(idx));
        }

        // 2. Asignar posiciones
        String abajo = ordenRotado.get(0);
        String derecha = ordenRotado.size() > 1 ? ordenRotado.get(1) : null;
        String arriba = ordenRotado.size() > 2 ? ordenRotado.get(2) : null;
        String izquierda = ordenRotado.size() > 3 ? ordenRotado.get(3) : null;

        // 3. Pintar abajo (local)
        lblAbajo.setText(nombres.get(abajo));

        // 4. Pintar derecha
        if (derecha != null) {
            uidJugadorDerecha = derecha;
            colocarEnDerecha(derecha);
            lblDerecha.setText(nombres.get(derecha));
            lblDerecha.setRotate(-90);
            zonaDerecha.setOnMouseClicked(e -> onZonaRivalClick(derecha));
        }

        // 5. Pintar arriba
        if (arriba != null) {
            uidJugadorArriba = arriba;
            colocarEnArriba(arriba);
            lblArriba.setText(nombres.get(arriba));
            zonaArriba.setOnMouseClicked(e -> onZonaRivalClick(arriba));
        }

        // 6. Pintar izquierda
        if (izquierda != null) {
            uidJugadorIzquierda = izquierda;
            colocarEnIzquierda(izquierda);
            lblIzquierda.setText(nombres.get(izquierda));
            lblIzquierda.setRotate(90);
            zonaIzquierda.setOnMouseClicked(e -> onZonaRivalClick(izquierda));
        }
    }

    private void onZonaRivalClick(String uidRival) {

        // 1. Solo si es mi turno
        if (!uidLocal.equals(uidTurnoActual)) {
            narrarPrivado(uidLocal, "No es tu turno.");
            return;
        }

        if (esPescaito()) {
            // 2. Si estoy obligado a robar, no puedo preguntar
            if (esperandoRobo) {
                narrarPrivado(uidLocal, "Debes robar antes de continuar.");
                return;
            }

            // 3. Debo haber seleccionado un número antes
            if (numeroSeleccionado == null) {
                narrarPrivado(uidLocal, "Primero selecciona un número de tu mano.");
                return;
            }

            // 4. No puedo preguntarme a mí mismo
            if (uidRival.equals(uidLocal)) {
                narrarPrivado(uidLocal, "No puedes preguntarte a ti mismo.");
                return;
            }

            // 5. No puedo preguntar a una zona vacía
            if (uidRival == null) {
                narrarPrivado(uidLocal, "No hay jugador en esa posición.");
                return;
            }

            // 🛡️ 2. Comprobar si el jugador tiene cartas
            List<String> manoObjetivo = manos.get(uidRival);

            if (manoObjetivo == null || manoObjetivo.isEmpty()) {
                narrarPrivado(uidLocal, "Ese jugador no tiene cartas. Elige otro.");
                return;
            }

            // 6. Guardar objetivo y ejecutar pregunta
            uidJugadorObjetivo = uidRival;

            //mostrarMensaje("Has seleccionado al jugador " + uidRival + " como objetivo.");
            ejecutarPregunta();
            return;
        }
        if (esYusa()) {
            return;
        }
    }

    private void colocarEnArriba(String uid) {
        dibujarAbanicoArriba(zonaArriba, manos.get(uid));
    }

    private void colocarEnIzquierda(String uid) {
        dibujarAbanicoIzquierda(zonaIzquierda, manos.get(uid));
    }

    private void colocarEnDerecha(String uid) {
        dibujarAbanicoDerecha(zonaDerecha, manos.get(uid));
    }

    private void mostrarMazo() {
        if (baraja == null || baraja.isEmpty()) {
            imgMazo.setVisible(false);
            return;
        }
        imgMazo.setImage(new Image("/ui/graphicResources/cartas/parteTrasera.png"));
        imgMazo.setVisible(true);
    }

    private void mostrarDescarte() {
        if (descarte == null || descarte.isEmpty()) {
            imgDescarte.setVisible(false);
            return;
        }

        String ultimaCarta = descarte.get(descarte.size() - 1);
        imgDescarte.setImage(new Image(ultimaCarta));
        imgDescarte.setVisible(true);
    }

    // ---------------------------------------------------------
    //  CURVA PERFECTA ABAJO (TU MANO)
    // ---------------------------------------------------------
    private void dibujarAbanicoAbajo(Pane zona, List<String> cartas) {

        zona.getChildren().clear();
        if (cartas == null || cartas.isEmpty()) {
            return;
        }

        int n = cartas.size();

        // 🟦 CASO ESPECIAL: SOLO 1 CARTA → CENTRADA Y AJUSTADA (ABAJO)
        if (n == 1) {

            Image imgRaw = new Image(getClass().getResourceAsStream(cartas.get(0)));
            ImageView img = new ImageView(imgRaw);
            img.setFitHeight(140);
            img.setPreserveRatio(true);

            double radio = RADIO_BASE + 20;
            double alturaArco = ALTURA_BASE + 3;

            double angulo = 0;
            double rad = Math.toRadians(angulo);

            double x = Math.sin(rad) * radio;
            double y = BASE_Y - Math.cos(rad) * alturaArco;

            double rot = 0;
            double theta = Math.toRadians(Math.abs(rot));
            double h = img.getFitHeight();
            double compensacionY = Math.sin(theta) * (h * 0.35);
            y += compensacionY;

            // 🟥 MOVER ARRIBA / ABAJO
            double DESPLAZAMIENTO_VERTICAL = -20;   // + baja, - sube
            double y0 = y + DESPLAZAMIENTO_VERTICAL;

            // 🟦 MOVER IZQUIERDA / DERECHA
            double centroPaneX = zona.getWidth() / 2;
            double DESPLAZAMIENTO_HORIZONTAL = -10; // + derecha, - izquierda
            double x0 = centroPaneX - img.getFitWidth() / 2 + DESPLAZAMIENTO_HORIZONTAL;

            // 🟧 AJUSTE FINO DESPUÉS
            double AJUSTE_FINAL_X = 0;
            double AJUSTE_FINAL_Y = 0;

            img.setLayoutX(x0 + AJUSTE_FINAL_X);
            img.setLayoutY(y0 + AJUSTE_FINAL_Y);

            // Click
            String rutaCarta = cartas.get(0);
            img.setOnMouseClicked(e -> onCartaLocalClick(rutaCarta));

            // Animación mano abierta
            if (manoAbierta) {
                img.setScaleX(1.25);
                img.setScaleY(1.25);
                img.setTranslateY(-25);

                DropShadow glow = new DropShadow();
                glow.setColor(Color.RED);
                glow.setRadius(25);
                img.setEffect(glow);
            }

            zona.getChildren().add(img);
            return;
        }

        // 🟦 A PARTIR DE AQUÍ, TU CÓDIGO ORIGINAL PARA 2+ CARTAS
        double radio = RADIO_BASE + (n * 20) + (manoAbierta ? 60 : 0);
        double anguloTotal = ANGULO_BASE + (n * 2) + (manoAbierta ? 20 : 0);
        double alturaArco = ALTURA_BASE + (n * 3) + (manoAbierta ? 15 : 0);

        double divisorRotacion = ROTACION_BASE + (n * 0.05);
        double anguloInicio = -anguloTotal / 2;

        double centroPaneX = zona.getWidth() / 2;

        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        List<Double> rots = new ArrayList<>();
        List<ImageView> imgs = new ArrayList<>();

        for (int i = 0; i < n; i++) {

            ImageView img = new ImageView(new Image(cartas.get(i)));
            img.setFitHeight(140);
            img.setPreserveRatio(true);

            String rutaCarta = cartas.get(i);
            img.setOnMouseClicked(e -> onCartaLocalClick(rutaCarta));

            double angulo = anguloInicio + (anguloTotal / (n - 1)) * i;
            double rad = Math.toRadians(angulo);

            double x = Math.sin(rad) * radio;
            double y = BASE_Y - Math.cos(rad) * alturaArco;

            double rot = angulo / divisorRotacion;

            double theta = Math.toRadians(Math.abs(rot));
            double h = img.getFitHeight();
            double compensacionY = Math.sin(theta) * (h * 0.35);

            x -= img.getFitHeight() / 4;
            y += compensacionY;

            if (manoAbierta && cartaSeleccionada != -1) {
                int distancia = Math.abs(i - cartaSeleccionada);
                double separacionExtra = distancia * 12;

                if (i < cartaSeleccionada) {
                    x -= separacionExtra;
                } else if (i > cartaSeleccionada) {
                    x += separacionExtra;
                }
            }

            xs.add(x);
            ys.add(y);
            rots.add(rot);
            imgs.add(img);
        }

        double minX = xs.stream().min(Double::compare).get();
        double maxX = xs.stream().max(Double::compare).get();
        double centroCurva = (minX + maxX) / 2;

        double offsetX = centroPaneX - centroCurva;

        for (int i = 0; i < n; i++) {
            ImageView img = imgs.get(i);

            img.setLayoutX(xs.get(i) + offsetX);
            img.setLayoutY(ys.get(i));
            img.setRotate(rots.get(i));

            zona.getChildren().add(img);

            if (manoAbierta && i == cartaSeleccionada) {

                img.setScaleX(1.25);
                img.setScaleY(1.25);
                img.setTranslateY(-25);

                DropShadow glow = new DropShadow();
                glow.setColor(Color.RED);
                glow.setRadius(25);
                img.setEffect(glow);

            } else {

                img.setScaleX(1.0);
                img.setScaleY(1.0);
                img.setTranslateY(0);
                img.setEffect(null);
            }
        }
    }

    // ---------------------------------------------------------
    //  CURVA ARRIBA (INVERTIDA)
    // ---------------------------------------------------------
    private void dibujarAbanicoArriba(Pane zona, List<String> cartas) {

        zona.getChildren().clear();
        if (cartas == null || cartas.isEmpty()) {
            return;
        }

        int n = cartas.size();

        // 🟦 CASO ESPECIAL: SOLO 1 CARTA → CENTRADA Y AJUSTADA (ARRIBA)
        if (n == 1) {

            ImageView img = new ImageView(new Image("/ui/graphicResources/cartas/parteTrasera.png"));
            img.setFitHeight(120);
            img.setPreserveRatio(true);

            double radio = RADIO_BASE + 20;
            double alturaArco = ALTURA_BASE + 3;

            double angulo = 0;
            double rad = Math.toRadians(angulo);

            double x = Math.sin(rad) * radio;
            double y = (zona.getHeight() - 40) - Math.cos(rad) * alturaArco;

            double rot = 0;
            double theta = Math.toRadians(Math.abs(rot));
            double h = img.getFitHeight();
            double compensacionY = Math.sin(theta) * (h * 0.35);
            y += compensacionY;

            // 🟥 MOVER ARRIBA / ABAJO
            double DESPLAZAMIENTO_VERTICAL = -25;  // + baja, - sube
            double y0 = y + DESPLAZAMIENTO_VERTICAL;

            // 🟦 MOVER IZQUIERDA / DERECHA
            double centroPaneX = zona.getWidth() / 2;
            double DESPLAZAMIENTO_HORIZONTAL = 0; // + derecha, - izquierda
            double x0 = centroPaneX - img.getFitWidth() / 2 + DESPLAZAMIENTO_HORIZONTAL;

            // 🪞 ESPEJO
            double cx = zona.getWidth() / 2;
            double cy = zona.getHeight() / 2;

            double xr = 2 * cx - x0;
            double yr = 2 * cy - y0;

            // 🟧 AJUSTE FINO DESPUÉS DEL ESPEJO
            double AJUSTE_FINAL_X = 0;
            double AJUSTE_FINAL_Y = 0;

            img.setLayoutX(xr + AJUSTE_FINAL_X);
            img.setLayoutY(yr + AJUSTE_FINAL_Y);
            img.setRotate(180);

            zona.getChildren().add(img);
            return;
        }

        double radio = RADIO_BASE + (n * 20);
        double anguloTotal = ANGULO_BASE + (n * 2);
        double alturaArco = ALTURA_BASE + (n * 3);
        double divisorRotacion = ROTACION_BASE + (n * 0.05);

        double anguloInicio = -anguloTotal / 2;

        double centroPaneX = zona.getWidth() / 2;
        double cx = zona.getWidth() / 2;
        double cy = zona.getHeight() / 2;

        double baseYLocal = zona.getHeight() - 40;

        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        List<Double> rots = new ArrayList<>();
        List<ImageView> imgs = new ArrayList<>();

        for (int i = 0; i < n; i++) {

            ImageView img = new ImageView(new Image("/ui/graphicResources/cartas/parteTrasera.png"));
            img.setFitHeight(120);
            img.setPreserveRatio(true);

            double angulo = anguloInicio + (anguloTotal / (n - 1)) * i;
            double rad = Math.toRadians(angulo);

            double x = Math.sin(rad) * radio;
            double y = baseYLocal - Math.cos(rad) * alturaArco;

            double rot = angulo / divisorRotacion;

            double theta = Math.toRadians(Math.abs(rot));
            double h = img.getFitHeight();
            double compensacionY = Math.sin(theta) * (h * 0.35);

            x -= img.getFitHeight() / 4;
            y += compensacionY;

            xs.add(x);
            ys.add(y);
            rots.add(rot);
            imgs.add(img);
        }

        double minX = xs.stream().min(Double::compare).get();
        double maxX = xs.stream().max(Double::compare).get();
        double centroCurva = (minX + maxX) / 2;

        double offsetX = centroPaneX - centroCurva;

        for (int i = 0; i < n; i++) {

            ImageView img = imgs.get(i);

            double x0 = xs.get(i) + offsetX;
            double y0 = ys.get(i);

            double xr = 2 * cx - x0;
            double yr = 2 * cy - y0;

            img.setLayoutX(xr);
            img.setLayoutY(yr);
            img.setRotate(rots.get(i) + 180);

            zona.getChildren().add(img);
        }
    }

    // ---------------------------------------------------------
    //  CURVA IZQUIERDA (VERTICAL)
    // ---------------------------------------------------------
    private void dibujarAbanicoIzquierda(Pane zona, List<String> cartas) {

        zona.getChildren().clear();
        if (cartas == null || cartas.isEmpty()) {
            return;
        }

        int n = cartas.size();

        // 🟦 CASO ESPECIAL: SOLO 1 CARTA → CENTRADA Y AJUSTADA (IZQUIERDA)
        if (n == 1) {

            ImageView img = new ImageView(new Image("/ui/graphicResources/cartas/parteTrasera.png"));
            img.setFitHeight(120);
            img.setPreserveRatio(true);

            double radio = RADIO_BASE + 20;
            double alturaArco = ALTURA_BASE + 3;

            double angulo = 0;
            double rad = Math.toRadians(angulo);

            double x = Math.sin(rad) * radio;
            double y = (zona.getHeight() / 2 + 80) - Math.cos(rad) * alturaArco;

            double rot = 0;
            double theta = Math.toRadians(Math.abs(rot));
            double h = img.getFitHeight();
            double compensacionY = Math.sin(theta) * (h * 0.35);
            y += compensacionY;

            // 🟥 MOVER ARRIBA / ABAJO
            double DESPLAZAMIENTO_VERTICAL = 40; // prueba 10, -10, 20...
            double centroPaneY = zona.getHeight() / 2;
            double y0 = centroPaneY - img.getFitHeight() / 2 + DESPLAZAMIENTO_VERTICAL;

            // 🟦 MOVER IZQUIERDA / DERECHA
            double DESPLAZAMIENTO_HORIZONTAL = -40; // prueba 20, 60...
            double x0 = x + DESPLAZAMIENTO_HORIZONTAL;

            double cx = zona.getWidth() / 2;
            double cy = zona.getHeight() / 2;

            double dx = x0 - cx;
            double dy = y0 - cy;

            double xr = cx - dy;
            double yr = cy + dx;

            // 🟧 AJUSTE FINO DESPUÉS DE ROTAR
            double AJUSTE_FINAL_X = 0;
            double AJUSTE_FINAL_Y = 0;

            img.setLayoutX(xr + AJUSTE_FINAL_X);
            img.setLayoutY(yr + AJUSTE_FINAL_Y);
            img.setRotate(90);

            zona.getChildren().add(img);
            return;
        }

        double radio = RADIO_BASE + (n * 20);
        double anguloTotal = ANGULO_BASE + (n * 2);
        double alturaArco = ALTURA_BASE + (n * 3);
        double divisorRotacion = ROTACION_BASE + (n * 0.05);

        double anguloInicio = -anguloTotal / 2;

        double centroPaneY = zona.getHeight() / 2;
        double cx = zona.getWidth() / 2;
        double cy = zona.getHeight() / 2;

        double baseYLocal = zona.getHeight() / 2 + 80;

        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        List<Double> rots = new ArrayList<>();
        List<ImageView> imgs = new ArrayList<>();

        for (int i = 0; i < n; i++) {

            ImageView img = new ImageView(new Image("/ui/graphicResources/cartas/parteTrasera.png"));
            img.setFitHeight(120);
            img.setPreserveRatio(true);

            double angulo = anguloInicio + (anguloTotal / (n - 1)) * i;
            double rad = Math.toRadians(angulo);

            double x = Math.sin(rad) * radio;
            double y = baseYLocal - Math.cos(rad) * alturaArco;

            double rot = angulo / divisorRotacion;

            double theta = Math.toRadians(Math.abs(rot));
            double h = img.getFitHeight();
            double compensacionY = Math.sin(theta) * (h * 0.35);

            x -= img.getFitHeight() / 4;
            y += compensacionY;

            xs.add(x);
            ys.add(y);
            rots.add(rot);
            imgs.add(img);
        }

        double minY = ys.stream().min(Double::compare).get();
        double maxY = ys.stream().max(Double::compare).get();
        double centroCurvaY = (minY + maxY) / 2;

        double offsetY = centroPaneY - centroCurvaY;

        for (int i = 0; i < n; i++) {

            ImageView img = imgs.get(i);

            double x0 = xs.get(i);
            double y0 = ys.get(i) + offsetY;

            double dx = x0 - cx;
            double dy = y0 - cy;

            double xr = cx - dy;
            double yr = cy + dx;

            img.setLayoutX(xr);
            img.setLayoutY(yr);
            img.setRotate(rots.get(i) + 90);

            zona.getChildren().add(img);
        }
    }

    // ---------------------------------------------------------
    //  CURVA DERECHA (VERTICAL INVERTIDA)
    // ---------------------------------------------------------
    private void dibujarAbanicoDerecha(Pane zona, List<String> cartas) {

        zona.getChildren().clear();
        if (cartas == null || cartas.isEmpty()) {
            return;
        }

        int n = cartas.size();

        // 🟦 CASO ESPECIAL: SOLO 1 CARTA → CENTRADA Y AJUSTADA (DERECHA)
        if (n == 1) {

            ImageView img = new ImageView(new Image("/ui/graphicResources/cartas/parteTrasera.png"));
            img.setFitHeight(120);
            img.setPreserveRatio(true);

            double radio = RADIO_BASE + 20;
            double alturaArco = ALTURA_BASE + 3;

            double angulo = 0;
            double rad = Math.toRadians(angulo);

            // Coordenadas base antes de rotar
            double x = Math.sin(rad) * radio;
            double y = (zona.getHeight() / 2 + 80) - Math.cos(rad) * alturaArco;

            // Compensación vertical igual que en curva
            double rot = 0;
            double theta = Math.toRadians(Math.abs(rot));
            double h = img.getFitHeight();
            double compensacionY = Math.sin(theta) * (h * 0.35);
            y += compensacionY;

            // 🟥 MOVER ARRIBA / ABAJO
            double DESPLAZAMIENTO_VERTICAL = 15;  // ajusta libremente
            double centroPaneY = zona.getHeight() / 2;
            double y0 = centroPaneY - img.getFitHeight() / 2 + DESPLAZAMIENTO_VERTICAL;

            // 🟦 MOVER IZQUIERDA / DERECHA
            double DESPLAZAMIENTO_HORIZONTAL = 0;  // ajusta libremente
            double x0 = x + DESPLAZAMIENTO_HORIZONTAL;

            // Centro del pane (para el espejo)
            double cx = zona.getWidth() / 2;
            double cy = zona.getHeight() / 2;

            // Transformación para la curva derecha
            double dx = x0 - cx;
            double dy = y0 - cy;

            double xr = cx + dy;
            double yr = cy - dx;

            // 🟧 AJUSTE FINO DESPUÉS DE ROTAR
            double AJUSTE_FINAL_X = 0;
            double AJUSTE_FINAL_Y = 0;

            img.setLayoutX(xr + AJUSTE_FINAL_X);
            img.setLayoutY(yr + AJUSTE_FINAL_Y);
            img.setRotate(-90);

            zona.getChildren().add(img);
            return;
        }

        double radio = RADIO_BASE + (n * 20);
        double anguloTotal = ANGULO_BASE + (n * 2);
        double alturaArco = ALTURA_BASE + (n * 3);
        double divisorRotacion = ROTACION_BASE + (n * 0.05);

        double anguloInicio = -anguloTotal / 2;

        double centroPaneY = zona.getHeight() / 2;
        double cx = zona.getWidth() / 2;
        double cy = zona.getHeight() / 2;

        double baseYLocal = zona.getHeight() / 2 + 80;

        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        List<Double> rots = new ArrayList<>();
        List<ImageView> imgs = new ArrayList<>();

        for (int i = 0; i < n; i++) {

            ImageView img = new ImageView(new Image("/ui/graphicResources/cartas/parteTrasera.png"));
            img.setFitHeight(120);
            img.setPreserveRatio(true);

            double angulo = anguloInicio + (anguloTotal / (n - 1)) * i;
            double rad = Math.toRadians(angulo);

            double x = Math.sin(rad) * radio;
            double y = baseYLocal - Math.cos(rad) * alturaArco;

            double rot = angulo / divisorRotacion;

            double theta = Math.toRadians(Math.abs(rot));
            double h = img.getFitHeight();
            double compensacionY = Math.sin(theta) * (h * 0.35);

            x -= img.getFitHeight() / 4;
            y += compensacionY;

            xs.add(x);
            ys.add(y);
            rots.add(rot);
            imgs.add(img);
        }

        double minY = ys.stream().min(Double::compare).get();
        double maxY = ys.stream().max(Double::compare).get();
        double centroCurvaY = (minY + maxY) / 2;

        double offsetY = centroPaneY - centroCurvaY;

        for (int i = 0; i < n; i++) {

            ImageView img = imgs.get(i);

            double x0 = xs.get(i);
            double y0 = ys.get(i) + offsetY;

            double dx = x0 - cx;
            double dy = y0 - cy;

            double xr = cx + dy;
            double yr = cy - dx;

            img.setLayoutX(xr);
            img.setLayoutY(yr);
            img.setRotate(rots.get(i) - 90);

            zona.getChildren().add(img);
        }
    }

    public void configurarLayoutEscena(Stage stage) {

        // offsets que TÚ vas a tocar
        final double OFFSET_ABAJO_X = -40;
        final double OFFSET_ABAJO_Y = -10;

        final double OFFSET_ARRIBA_X = -40;
        final double OFFSET_ARRIBA_Y = -110;

        final double OFFSET_IZQUIERDA_X = -70;
        final double OFFSET_IZQUIERDA_Y = 100;

        final double OFFSET_DERECHA_X = 0;
        final double OFFSET_DERECHA_Y = -210;

        final double OFFSET_CENTRO_X = 0;
        final double OFFSET_CENTRO_Y = 0;

        Runnable recolocar = () -> {
            double w = stage.getWidth();
            double h = stage.getHeight();

            double cx = w / 2;
            double cy = h / 2;

            // CENTRO
            zonaCentro.setLayoutX(cx - zonaCentro.getWidth() / 2 + OFFSET_CENTRO_X);
            zonaCentro.setLayoutY(cy - zonaCentro.getHeight() / 2 + OFFSET_CENTRO_Y);

            // ABAJO (X = horizontal, Y = vertical)
            zonaAbajo.setLayoutX(cx - zonaAbajo.getWidth() / 2 + OFFSET_ABAJO_X);
            zonaAbajo.setLayoutY(h - zonaAbajo.getHeight() - 20 + OFFSET_ABAJO_Y);

            // ARRIBA
            zonaArriba.setLayoutX(cx - zonaArriba.getWidth() / 2 + OFFSET_ARRIBA_X);
            zonaArriba.setLayoutY(20 + OFFSET_ARRIBA_Y);

            // IZQUIERDA
            zonaIzquierda.setLayoutX(20 + OFFSET_IZQUIERDA_X);
            zonaIzquierda.setLayoutY(cy - zonaIzquierda.getHeight() / 2 + OFFSET_IZQUIERDA_Y);

            // DERECHA
            zonaDerecha.setLayoutX(w - zonaDerecha.getWidth() - 20 + OFFSET_DERECHA_X);
            zonaDerecha.setLayoutY(cy - zonaDerecha.getHeight() / 2 + OFFSET_DERECHA_Y);
        };

        Platform.runLater(recolocar);
        stage.widthProperty().addListener((obs, o, n) -> recolocar.run());
        stage.heightProperty().addListener((obs, o, n) -> recolocar.run());
    }

    /**
     * private void mostrarMensaje(String texto) { System.out.println("[MENSAJE]
     * " + texto); } / /**
     *
     * PESCAITOOOO
     *
     *
     */
    private void ejecutarPregunta() {

        if (!esPescaito()) {
            return;
        }

        // 🛡 0. Blindaje de turno y estado
        if (!uidLocal.equals(uidTurnoActual)) {   // usa siempre el mismo nombre de variable de turno
            narrarPrivado(uidLocal, "No es tu turno.");
            return;
        }

        if (esperandoRobo) {
            narrarPrivado(uidLocal, "Debes robar antes de continuar.");
            return;
        }

        // 1. Comprobaciones básicas
        if (numeroSeleccionado == null) {
            narrarPrivado(uidLocal, "No has seleccionado número.");
            return;
        }

        if (uidJugadorObjetivo == null) {
            narrarPrivado(uidLocal, "No has seleccionado jugador objetivo.");
            return;
        }

        if (juego == null) {
            narrarPrivado(uidLocal, "El motor del juego no está inicializado.");
            return;
        }

        uiBloqueadaPorAccion = true;
        zonaCentro.setDisable(true);

        narrarPrivado(uidLocal,
                "Has preguntado a " + nombres.get(uidJugadorObjetivo)
                + "\npor el " + numeroSeleccionado + "."
        );

        try {
            Map<String, Object> resultado = ((JuegoPescaito) juego).preguntar(
                    uidLocal,
                    uidJugadorObjetivo,
                    numeroSeleccionado,
                    manos,
                    baraja,
                    descarte
            );

            //mostrarMensaje("Resultado pregunta: " + resultado);
            // 🔥 SOLO AQUÍ
            procesarResultadoPregunta(resultado);

        } catch (Exception e) {
            e.printStackTrace();
            //mostrarMensaje("Error ejecutando la pregunta.");
        } finally {

            uidJugadorObjetivo = null;
            uiBloqueadaPorAccion = false;

            if (uidLocal.equals(uidTurnoActual) && !esperandoRobo) {
                // Solo reactivamos interacción de pregunta si no estamos en modo "debo robar"
                activarInteraccionPregunta();
            }

            if (!esperandoRobo) {
                zonaCentro.setDisable(false);
            }
        }
    }

    private void procesarResultadoPregunta(Map<String, Object> resultado) throws IOException {

        if (!esPescaito()) {
            return;
        }

        // 🛡 1. Validar turno
        if (!uidLocal.equals(uidTurnoActual)) {
            narrarPrivado(uidLocal, "No es tu turno.");
            return;
        }

        // 🛡 2. Validar estado interno
        if (esperandoRobo) {
            narrarPrivado(uidLocal, "Debes robar antes de continuar.");
            return;
        }

        boolean acierto = (boolean) resultado.get("acierto");
        boolean pescaito = (boolean) resultado.get("pescaito");
        boolean debeRobar = (boolean) resultado.get("debeRobar");
        boolean mantieneTurno = (boolean) resultado.get("mantieneTurno");
        int cartasRecibidas = (int) resultado.get("cartasRecibidas");

        // 🧩 3. Mostrar mensajes (tu lógica original)
        if (acierto) {
            narrarPrivado(uidLocal,
                    "Has robado " + cartasRecibidas + " carta(s)."
            );

            if (pescaito) {
                narrarPrivado(uidLocal,
                        "¡Pescaito! Has completado un grupo de 4."
                );
            }

            if (mantieneTurno) {
                narrarPrivado(uidLocal,
                        "Mantienes el turno."
                );
            }

        } else {
            String mensaje = (String) resultado.getOrDefault("mensaje", "No tenía ese número.");
            narrarPrivado(uidLocal, "Fallaste.");

            if (debeRobar) {
                narrarPrivado(uidLocal, "Debes robar una carta.");
            }

            if (!mantieneTurno) {
                narrarPrivado(uidLocal, "El turno pasa al siguiente jugador.");
            }

        }

        // 🐟 4. Procesar pescaito real
        if (pescaito) {
            int numeroPescaito = (int) resultado.get("numeroPescaito");
            bd.registrarPescaito(codigoSala, uidLocal, numeroPescaito, idToken);
            bd.actualizarDescarte(codigoSala, descarte, idToken);
            narrarPrivado(uidLocal, "¡Pescaito!");
        }

        // 🟥 5. Si debe robar → detener flujo aquí
        if (debeRobar) {

            narrarPrivado(uidLocal,
                    "Debes robar una carta porque no acertaste."
            );

            // 🟥 1. Si la baraja está vacía → NO se puede robar → pasar turno
            if (baraja.isEmpty()) {
                narrarPrivado(uidLocal,
                        "No quedan cartas en la baraja. Pasas el turno."
                );

                String siguiente = obtenerSiguienteJugador(uidTurnoActual);
                bd.actualizarTurno(codigoSala, siguiente, idToken);

                // Reset de estado
                numeroSeleccionado = null;
                numeroPreguntadoAntesDeRobar = null;
                esperandoRobo = false;

                return;
            }

            // 5.1 Guardar el número que se preguntó para usarlo al robar
            numeroPreguntadoAntesDeRobar = numeroSeleccionado;

            esperandoRobo = true;

            // Desactivar interacción con jugadores y números
            desactivarInteraccionPregunta();

            // PERO activar el mazo
            zonaCentro.setDisable(false);
            imgMazo.setDisable(false);
            imgMazo.setOpacity(1.0);

            // MUY IMPORTANTE: no bloquear la UI aquí
            uiBloqueadaPorAccion = false;

            return;
        }

        // 🟩 6. Actualizar manos en Firebase
        bd.actualizarMano(codigoSala, uidLocal, manos.get(uidLocal), idToken);
        bd.actualizarMano(codigoSala, uidJugadorObjetivo, manos.get(uidJugadorObjetivo), idToken);

        // 🟦 7. Actualizar turno
        if (mantieneTurno) {
            bd.actualizarTurno(codigoSala, uidLocal, idToken);
        } else {
            String siguiente = obtenerSiguienteJugador(uidTurnoActual);
            bd.actualizarTurno(codigoSala, siguiente, idToken);

        }

        // 🟨 8. Reactivar UI solo si NO estamos esperando robo
        zonaCentro.setDisable(false);

        // 🟫 9. Resetear selección
        numeroSeleccionado = null;
        uidJugadorObjetivo = null;
    }

    private String obtenerSiguienteJugador(String uidActual) {

        // Seguridad: si la lista global está vacía, devolvemos el actual
        if (ordenJugadoresGlobal == null || ordenJugadoresGlobal.isEmpty()) {
            System.out.println("ordenJugadoresGlobal está vacía, devolviendo uidActual.");
            return uidActual;
        }

        // Buscar el índice del jugador actual en la lista global
        int idx = ordenJugadoresGlobal.indexOf(uidActual);

        // Si por algún motivo no está, devolvemos el primero de la lista global
        if (idx == -1) {
            System.out.println("uidActual no está en la lista global, devolviendo primero.");
            return ordenJugadoresGlobal.get(0);
        }

        // Calcular el siguiente jugador en orden circular
        int siguiente = (idx + 1) % ordenJugadoresGlobal.size();
        return ordenJugadoresGlobal.get(siguiente);
    }

    private void realizarRoboManual() {

        if (!esPescaito()) {
            return;
        }

        // 🛡 1. Validar turno
        if (!uidLocal.equals(uidTurnoActual)) {
            narrarPrivado(uidLocal, "No es tu turno.");
            return;
        }

        // 🛡 2. Validar que realmente debes robar
        if (!esperandoRobo) {
            narrarPrivado(uidLocal, "No estás obligado a robar.");
            return;
        }

        uiBloqueadaPorAccion = true;

        // Bloquea TODO menos el mazo
        zonaArriba.setDisable(true);
        zonaIzquierda.setDisable(true);
        zonaDerecha.setDisable(true);
        zonaAbajo.setDisable(true);

        try {
            // 4. Robar carta (void)
            ((JuegoPescaito) juego).robarCarta(uidLocal, manos, baraja);

            // 5. Obtener la carta robada (última de la mano)
            List<String> mano = manos.get(uidLocal);
            String cartaRobada = mano.get(mano.size() - 1);
            int numeroRobado = juego.obtenerNumeroCarta(cartaRobada);

            // 6. Actualizar mano y baraja en Firebase
            bd.actualizarMano(codigoSala, uidLocal, mano, idToken);
            bd.actualizarBaraja(codigoSala, baraja, idToken);

            narrarPrivado(uidLocal, "Has robado un "
                    + numeroRobado);

            // 7. Determinar si HE PESCADO (regla real)
            // Usamos el número que se preguntó antes de entrar en "debo robar"
            boolean haPescado = ((JuegoPescaito) juego)
                    .haPescadoAlRobar(cartaRobada, numeroPreguntadoAntesDeRobar);

            // 8. Determinar si HE HECHO PESCAITO (4 iguales)
            boolean pescaito = ((JuegoPescaito) juego)
                    .esPescaitoPorRobo(uidLocal, manos, descarte);

            if (pescaito) {
                narrarPrivado(uidLocal, "¡PESCAITO!");

                // 1. ACTUALIZAR MANO (ya está limpia gracias al motor)
                bd.actualizarMano(codigoSala, uidLocal, manos.get(uidLocal), idToken);

                // 2. ACTUALIZAR DESCARTE
                bd.actualizarDescarte(codigoSala, descarte, idToken);

                // 3. REGISTRAR PESCAITO EN BD
                int numero = juego.obtenerNumeroCarta(cartaRobada);
                bd.registrarPescaito(codigoSala, uidLocal, numero, idToken);
            }

            // 9. Decidir turno SOLO según si he pescado
            if (haPescado) {
                narrarPrivado(uidLocal, "¡Has pescado! mantienes turno.");
                bd.actualizarTurno(codigoSala, uidLocal, idToken);
            } else {
                String siguiente = obtenerSiguienteJugador(uidTurnoActual);
                narrarPrivado(uidLocal, "Pasas turno.");
                bd.actualizarTurno(codigoSala, siguiente, idToken);
            }

        } catch (Exception e) {
            e.printStackTrace();
            //mostrarMensaje("Error al robar carta.");
        } finally {

            // 10. Resetear estado
            esperandoRobo = false;
            // Limpiamos tanto el número preguntado como el seleccionado
            numeroPreguntadoAntesDeRobar = null;
            numeroSeleccionado = null;
            uiBloqueadaPorAccion = false;

            // 11. Reactivar UI solo si SIGUE siendo tu turno
            if (uidLocal.equals(uidTurnoActual)) {
                zonaCentro.setDisable(false);
                activarInteraccionPregunta();
            } else {
                // Si ya no es tu turno, mejor dejar la zona centro desactivada
                zonaCentro.setDisable(true);
            }
        }
    }

    private void desactivarInteraccionPregunta() {

        if (!esPescaito()) {
            return; // En Yusa NO se usa
        }

        zonaArriba.setDisable(true);
        zonaArriba.setOpacity(0.5);

        zonaIzquierda.setDisable(true);
        zonaIzquierda.setOpacity(0.5);

        zonaDerecha.setDisable(true);
        zonaDerecha.setOpacity(0.5);

        zonaAbajo.setDisable(true);
        zonaAbajo.setOpacity(0.5);
    }

    private void activarInteraccionPregunta() {

        if (!esPescaito()) {
            return; // En Yusa NO se usa
        }

        // Si la UI está bloqueada por una acción interna, NO activar nada
        if (uiBloqueadaPorAccion) {
            return;
        }

        zonaArriba.setDisable(false);
        zonaArriba.setOpacity(1.0);

        zonaIzquierda.setDisable(false);
        zonaIzquierda.setOpacity(1.0);

        zonaDerecha.setDisable(false);
        zonaDerecha.setOpacity(1.0);

        zonaAbajo.setDisable(false);
        zonaAbajo.setOpacity(1.0);
    }

    private void iniciarTurnoLocal() throws IOException {

        if (!esPescaito()) {
            return;
        }
        if (!repartoInicialHecho) {
            activarInteraccionPregunta();
            return;
        }
        if (partidaFinalizada) {
            return;
        }
        if (esperandoRobo || uiBloqueadaPorAccion) {
            return;
        }

        // ── NUEVO: comprobar fin de partida ANTES de cualquier acción ──
        if (esFinDePartida()) {
            finalizarPartida();
            return;
        }

        Juego.AccionTurno accion = juego.accionInicioTurno(uidLocal, manos, baraja);

        switch (accion) {

            case PASAR_TURNO:
                // Mano vacía + baraja vacía.
                // Antes de pasar turno, comprobar si TODOS los jugadores están
                // en la misma situación → si es así, es fin de partida real.
                boolean alguienTieneCartas = manos.values().stream()
                        .anyMatch(m -> m != null && !m.isEmpty());

                if (!alguienTieneCartas && baraja.isEmpty()) {
                    // Nadie tiene cartas y no hay baraja → fin real
                    finalizarPartida();
                    return;
                }

                // Solo yo estoy sin cartas, otros sí tienen → pasar turno normal
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

// Nuevo método auxiliar: salta a un jugador que SÍ tenga cartas
    private String obtenerSiguienteJugadorConCartas() {
        String candidato = obtenerSiguienteJugador(uidTurnoActual);
        // Hacer hasta N iteraciones para no entrar en bucle si todos están vacíos
        for (int i = 0; i < ordenJugadoresGlobal.size(); i++) {
            List<String> mano = manos.get(candidato);
            if (mano != null && !mano.isEmpty()) {
                return candidato;
            }
            candidato = obtenerSiguienteJugador(candidato);
        }
        // Si ninguno tiene cartas, devolver el siguiente normal (el fin de partida lo cortará)
        return obtenerSiguienteJugador(uidTurnoActual);
    }

    private void finalizarPartida() {
        if (partidaFinalizada) {
            return;
        }
        // NO poner partidaFinalizada = true todavía

        zonaArriba.setDisable(true);
        zonaIzquierda.setDisable(true);
        zonaDerecha.setDisable(true);
        zonaAbajo.setDisable(true);
        zonaCentro.setDisable(true);

        try {
            bd.actualizarEstadoPartida(codigoSala, "finalizada", idToken);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Marcar DESPUÉS de escribir en Firebase
        partidaFinalizada = true;

        // Mostrar pantalla directamente en el cliente que detectó el fin
        // sin esperar al listener (que puede llegar tarde o no llegar)
        if (esPescaito()) {
            Platform.runLater(() -> {
                try {
                    Map<String, Map<String, Object>> pescaitosBD
                            = bd.leerPescaitos(codigoSala, idToken);
                    Map<String, Integer> puntuaciones
                            = ((JuegoPescaito) juego).calcularPuntuacionesDesdeBD(pescaitosBD);
                    String uidGanador = obtenerGanador(puntuaciones);
                    String nombreGanador = nombres.getOrDefault(uidGanador, "Jugador");
                    int pescaitosGanador = puntuaciones.getOrDefault(uidGanador, 0);
                    mostrarPantallaFinal(nombreGanador, pescaitosGanador);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private boolean esFinDePartida() {
        // El juego decide completamente cuándo termina.
        // El controlador ya no necesita saber nada sobre manos ni baraja.
        return juego.haTerminado(manos, baraja, descarte);
    }

    //FUTURO AISLAMIENTO
    private void mostrarPantallaFinal(String nombreGanador, int pescaitos) {

        // Evitar duplicados si se llama dos veces
        overlayFinal.getChildren().clear();  // ← AÑADIR ESTA LÍNEA

        Label titulo = new Label("🎉 La partida ha terminado");
        titulo.setStyle("-fx-font-size: 32px; -fx-font-weight: bold;");

        Label ganador = new Label("Ganador: " + nombreGanador + " (" + pescaitos + " pescaitos)");
        ganador.setStyle("-fx-font-size: 24px;");

        Button volverSala = new Button("Volver a la sala");
        //volverSala.setOnAction(e -> volverAlLobby());

        VBox box = new VBox(20, titulo, ganador, volverSala);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-padding: 40px;");

        StackPane.setAlignment(box, Pos.CENTER);
        overlayFinal.getChildren().add(box);
        overlayFinal.setVisible(true);
    }

    //FUTURO AISLAMIENTO
    private String obtenerGanador(Map<String, Integer> puntuaciones) {
        return puntuaciones.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private void procesarMensajeNarrador(Map<String, Object> msg) {
        String tipo = (String) msg.get("tipo");
        String texto = (String) msg.get("texto");
        String uid = (String) msg.get("uid");

        if ("global".equals(tipo)) {
            mostrarMensajeGlobal(texto);
        } else if ("privado".equals(tipo) && uid != null && uid.equals(uidLocal)) {
            encolarMensajePrivado(texto);
        }
    }

    private void mostrarMensajeGlobal(String texto) {
        narradorLabel.setText(texto);
    }

    // ──────────────────────────────────────────────────────────────
// BLOQUE 3 — SUSTITUYE mostrarMensajePrivadoConCallback() (~línea 1768)
//
//  Qué cambia:
//  - El fade anterior se cancela antes de lanzar el nuevo, pero
//    el texto del label se sobreescribía antes de que el jugador
//    pudiera leerlo. Ahora se espera a que el fade actual termine
//    (a través de la cola) en lugar de interrumpirlo.
//  - Se aumenta ligeramente el tiempo visible (delay 1.5s en vez
//    de 1s) para mensajes que llegan seguidos.
//  - Se añade un pequeño fade-in además del fade-out para que
//    los mensajes encadenados no "flashen" abruptamente.
// ──────────────────────────────────────────────────────────────
    private void mostrarMensajePrivadoConCallback(String texto, Runnable alTerminar) {

        lblInfo.setText(texto);

        // Fade-in rápido
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), lblInfo);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        // Fade-out después de que el jugador haya tenido tiempo de leerlo
        FadeTransition fadeOut = new FadeTransition(Duration.seconds(1.5), lblInfo);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setDelay(Duration.seconds(0.5));  // visible 1.5s antes de desvanecerse
        fadeOut.setOnFinished(e -> alTerminar.run());

        // Encadenar: primero entra, luego sale
        new javafx.animation.SequentialTransition(fadeIn, fadeOut).play();
    }

    private void narrarGlobal(String texto) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("tipo", "global");
        msg.put("texto", texto);
        msg.put("uid", null);

        try {
            bd.actualizarNarrador(codigoSala, msg, idToken);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mostrarMensajeGlobalTemporal(String texto) {
        narradorLabel.setOpacity(1.0);
        narradorLabel.setText(texto);

        FadeTransition fade = new FadeTransition(Duration.seconds(1), narradorLabel);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        fade.setDelay(Duration.seconds(1)); // tiempo visible antes de desvanecerse
        fade.play();
    }

    private void narrarPrivado(String uidDestino, String texto) {

        if (uidDestino.equals(uidLocal)) {
            // Mensaje para mí mismo → directo a la cola local, sin Firebase
            Platform.runLater(() -> encolarMensajePrivado(texto));
            return;
        }

        // Mensaje para otro jugador → sigue por Firebase como antes
        Map<String, Object> msg = new HashMap<>();
        msg.put("tipo", "privado");
        msg.put("texto", texto);
        msg.put("uid", uidDestino);

        try {
            bd.actualizarNarrador(codigoSala, msg, idToken);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void encolarMensajePrivado(String texto) {
        colaPrivados.add(texto);

        if (!narrandoPrivado) {
            narrarSiguientePrivado();
        }
    }

    private void narrarSiguientePrivado() {
        String texto = colaPrivados.poll();

        if (texto == null) {
            narrandoPrivado = false;
            return;
        }

        narrandoPrivado = true;
        mostrarMensajePrivadoConCallback(texto, () -> narrarSiguientePrivado());
    }

    private List<String> obtenerOrdenRondaYusa() {
        if (!esYusa()) {
            return Collections.emptyList();
        }

        JuegoYusa jy = (JuegoYusa) juego;
        Set<String> vivosSet = new HashSet<>(jy.getJugadoresVivos());

        // Construir lista respetando el orden global de la sala,
        // pero solo con los jugadores que siguen vivos
        List<String> vivosEnOrdenGlobal = new ArrayList<>();
        for (String uid : ordenJugadoresGlobal) {
            if (vivosSet.contains(uid)) {
                vivosEnOrdenGlobal.add(uid);
            }
        }

        if (vivosEnOrdenGlobal.isEmpty()) {
            return vivosEnOrdenGlobal;
        }

        // Rotar desde uidTurnoActual
        int idx = vivosEnOrdenGlobal.indexOf(uidTurnoActual);
        if (idx < 0) {
            return vivosEnOrdenGlobal; // seguridad
        }
        List<String> orden = new ArrayList<>();
        orden.addAll(vivosEnOrdenGlobal.subList(idx, vivosEnOrdenGlobal.size()));
        orden.addAll(vivosEnOrdenGlobal.subList(0, idx));
        return orden;
    }

    private void iniciarRondaYusa() throws IOException {

        // 🛡 Seguridad básica
        if (!esYusa()) {
            return;
        }
        if (partidaFinalizada) {
            return;
        }

        // Solo el jugador en turno inicia la ronda
        if (!uidLocal.equals(uidTurnoActual)) {
            return;
        }

        // 🟣 1. Comprobar fin de partida (reutilizamos tu método)
        if (esFinDePartida()) {
            finalizarPartida();
            return;
        }

        rondaEnCurso = true;
        numeroRondaYusa++;
        try {
            bd.incrementarRonda(codigoSala, numeroRondaYusa, idToken);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 🟣 2. Determinar fase de ronda usando el motor
        JuegoYusa juegoYusa = (JuegoYusa) juego;
        JuegoYusa.FaseRonda fase = juegoYusa.determinarFaseRonda(manos);

        // 🟣 3. Guardar fase en Firebase (para sincronizar a todos)
        bd.actualizarFaseRonda(codigoSala, fase.name(), idToken);

        // 🟣 4. Delegar según fase (aún sin implementar)
        switch (fase) {
            case NORMAL:
                iniciarFaseNormalYusa();
                break;

            case DOCE:
                iniciarFaseDoceYusa();
                break;

            case YUSA:
                iniciarFaseYusa();
                break;
        }
    }

    private void iniciarFaseNormalYusa() throws IOException {
        esperandoDecisionPropia = false;
        String nombre = nombres.get(uidTurnoActual);
        mostrarMensajeGlobalTemporal("Ronda normal");

        PauseTransition wait = new PauseTransition(Duration.seconds(1.2));
        wait.setOnFinished(e -> narrarGlobal("Turno de: " + nombre));
        wait.play();

        JuegoYusa jy = (JuegoYusa) juego;
        System.out.println("DEBUG jugadoresVivos = " + jy.getJugadoresVivos());
        System.out.println("DEBUG uidTurnoActual = " + uidTurnoActual);

        ordenRondaActual = obtenerOrdenRondaYusa();
        System.out.println("DEBUG ordenRondaActual = " + ordenRondaActual);

        if (ordenRondaActual.isEmpty()) {
            return;
        }

        String primerJugador = ordenRondaActual.get(0);

        // Publicar en Firebase quién debe decidir primero
        bd.actualizarTurnoActualRonda(codigoSala, primerJugador, idToken);

        // Si el primer jugador soy yo (el host), mostrar mis botones directamente
        if (primerJugador.equals(uidLocal)) {
            esperandoDecisionPropia = true;   // ← NUEVO
            narrarPrivado(uidLocal, "¿Te gusta tu carta?");
            mostrarBotonesDecision("Me la quedo", "La cambio",
                    this::procesarDecisionHost);
            desactivarInteraccionPregunta();
        }
        // Si el primer jugador NO es el host, el listener escucharTurnoActualRonda
        // lo recibirá en ese cliente y le mostrará los botones allí.
    }

    private void iniciarFaseDoceYusa() throws IOException {
        // Aquí implementaremos:
        // - mostrar cartas
        // - determinar perdedor
        // - perder vida
        // - gestionar empates
    }

    private void iniciarFaseYusa() throws IOException {
        // Aquí implementaremos:
        // - descartar jugadores sin yusa
        // - poseedores eligen objetivo
        // - objetivos eligen palo
        // - resolver pares
        // - perder vidas
    }

    private void mostrarBotonesDecision(String opcion1, String opcion2, Consumer<String> callback) {

        panelDecisionPrivada.setVisible(true);
        panelDecisionPrivada.setManaged(true);

        btnOpcion1.setText(opcion1);
        btnOpcion2.setText(opcion2);

        btnOpcion1.setVisible(true);
        btnOpcion1.setManaged(true);

        btnOpcion2.setVisible(true);
        btnOpcion2.setManaged(true);

        btnOpcion1.setOnAction(e -> {
            ocultarBotonesDecision();
            callback.accept(opcion1);
        });

        btnOpcion2.setOnAction(e -> {
            ocultarBotonesDecision();
            callback.accept(opcion2);
        });
    }

    private void ocultarBotonesDecision() {
        panelDecisionPrivada.setVisible(false);
        panelDecisionPrivada.setManaged(false);

        btnOpcion1.setVisible(false);
        btnOpcion1.setManaged(false);

        btnOpcion2.setVisible(false);
        btnOpcion2.setManaged(false);
    }

    private void procesarDecisionJugador(String decision) {

        String jugadorActual = ordenRondaActual.get(0);

        // 🟣 Narración privada para el jugador local
        if (jugadorActual.equals(uidLocal)) {
            narrarPrivado(uidLocal, "Has elegido: " + decision);
        }

        // 🟣 Si se la queda → simplemente avanzar
        if (decision.equals("Me la quedo")) {
            avanzarAlSiguienteJugadorNormal();
            return;
        }

        // 🟣 Si la cambia → (intercambio real lo implementamos en el siguiente paso)
        if (decision.equals("La cambio")) {
            narrarPrivado(uidLocal, "Intercambio pendiente (lo implementaremos ahora después)");
            avanzarAlSiguienteJugadorNormal();
            return;
        }
    }

    private void avanzarAlSiguienteJugadorNormal() {
        if (ordenRondaActual == null || ordenRondaActual.isEmpty()) {
            return;
        }

        // Marcar la decisión del jugador que acaba de decidir como procesada
        String jugadorQueDecidio = ordenRondaActual.get(0);
        try {
            bd.publicarDecisionRonda(codigoSala, jugadorQueDecidio, "PROCESADO", idToken);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ordenRondaActual.remove(0);

        if (ordenRondaActual.isEmpty()) {
            // No debería llegar aquí porque el último jugador tiene
            // flujo propio, pero por seguridad publicamos la resolución.
            rondaEnCurso = false;
            try {
                bd.publicarResolverRonda(codigoSala, idToken);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        String siguiente = ordenRondaActual.get(0);
        boolean esUltimo = (ordenRondaActual.size() == 1);

        // Publicar quién decide ahora
        try {
            bd.actualizarTurnoActualRonda(codigoSala, siguiente, idToken);
            if (esUltimo) {
                // Publicar también quién es el último para que todos lo sepan
                bd.actualizarUltimoJugadorRonda(codigoSala, siguiente, idToken);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Si el siguiente soy yo (el director de ronda), mostrar mis botones
        if (siguiente.equals(uidLocal)) {
            esperandoDecisionPropia = true;   // ← NUEVO
            if (esUltimo) {
                narrarPrivado(uidLocal,
                        "Eres el último. ¿Te quedas tu carta o robas de la baraja?");
                mostrarBotonesDecision("Me la quedo", "Robo de la baraja",
                        this::procesarDecisionUltimoJugadorDirector);
            } else {
                narrarPrivado(uidLocal, "¿Te gusta tu carta?");
                mostrarBotonesDecision("Me la quedo", "La cambio",
                        this::procesarDecisionHost);
            }
        }
        // Si siguiente != uidLocal, el listener escucharTurnoActualRonda (o
        // escucharUltimoJugadorRonda si es el último) se encarga en ese cliente.
    }

    private void notificarDecisionAlHost(String decision) {
        // Convertir texto de botón a valor normalizado
        String valor = decision.equals("Me la quedo") ? "QUEDAR" : "CAMBIAR";
        try {
            bd.publicarDecisionRonda(codigoSala, uidLocal, valor, idToken);
            narrarPrivado(uidLocal, "Has elegido: " + decision);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void procesarDecisionHost(String decision) {
        if (ordenRondaActual == null || ordenRondaActual.isEmpty()) {
            return;
        }

        String jugadorActual = ordenRondaActual.get(0);
        narrarPrivado(uidLocal, "Has elegido: " + decision);

        if (decision.equals("La cambio") && ordenRondaActual.size() > 1) {
            String siguiente = ordenRondaActual.get(1);
            ((JuegoYusa) juego).intercambiarCartas(jugadorActual, siguiente, manos);
            try {
                bd.actualizarMano(codigoSala, jugadorActual, manos.get(jugadorActual), idToken);
                bd.actualizarMano(codigoSala, siguiente, manos.get(siguiente), idToken);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        avanzarAlSiguienteJugadorNormal();
    }

    private void mostrarCartaJugador(String uid) {
        List<String> mano = manos.get(uid);
        if (mano == null || mano.isEmpty()) {
            return;
        }

        String carta = mano.get(0);

        ImageView img = new ImageView(new Image(getClass().getResourceAsStream(carta)));
        img.setFitWidth(120);
        img.setPreserveRatio(true);

        if (uid.equals(uidJugadorArriba)) {
            zonaArriba.getChildren().clear();
            zonaArriba.getChildren().add(img);
        } else if (uid.equals(uidJugadorIzquierda)) {
            zonaIzquierda.getChildren().clear();
            zonaIzquierda.getChildren().add(img);
        } else if (uid.equals(uidJugadorDerecha)) {
            zonaDerecha.getChildren().clear();
            zonaDerecha.getChildren().add(img);
        } else if (uid.equals(uidLocal)) {
            zonaAbajo.getChildren().clear();
            zonaAbajo.getChildren().add(img);
        }
    }

    private void ocultarCartaJugador(String uid) {
        if (uid.equals(uidJugadorArriba)) {
            zonaArriba.getChildren().clear();
            colocarEnArriba(uid); // tu método normal
        } else if (uid.equals(uidJugadorIzquierda)) {
            zonaIzquierda.getChildren().clear();
            colocarEnIzquierda(uid);
        } else if (uid.equals(uidJugadorDerecha)) {
            zonaDerecha.getChildren().clear();
            colocarEnDerecha(uid);
        } else if (uid.equals(uidLocal)) {
            zonaAbajo.getChildren().clear();
            dibujarAbanicoAbajo(zonaAbajo, manos.get(uidLocal));
        }
    }

    private void mostrarCartasDeRonda() {
        for (String uid : ordenJugadoresGlobal) {
            mostrarCartaJugador(uid);
        }
    }

    private void ocultarCartasDeRonda() {
        for (String uid : ordenJugadoresGlobal) {
            ocultarCartaJugador(uid);
        }
    }

    private void notificarDecisionUltimoAlDirector(String decision) {
        String valor = decision.equals("Me la quedo") ? "QUEDAR_ULTIMO" : "ROBAR_ULTIMO";
        try {
            bd.publicarDecisionRonda(codigoSala, uidLocal, valor, idToken);
            narrarPrivado(uidLocal, "Has elegido: " + decision);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void procesarDecisionUltimoRecibida(String uidUltimo, String decision) {

        // Marcar la decisión como procesada para que el listener no la repita
        try {
            bd.publicarDecisionRonda(codigoSala, uidUltimo, "PROCESADO", idToken);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ordenRondaActual.remove(0); // quitar al último del orden

        if (decision.contains("ROBAR")) {
            if (baraja.isEmpty()) {
                narrarGlobal(nombres.getOrDefault(uidUltimo, "Jugador")
                        + " quiso robar pero la baraja está vacía. Se queda su carta.");
            } else {
                // El director ejecuta el robo en nombre del último jugador
                String cartaAnterior = manos.get(uidUltimo) != null
                        && !manos.get(uidUltimo).isEmpty()
                        ? manos.get(uidUltimo).get(0) : null;

                ((JuegoYusa) juego).robarCarta(uidUltimo, manos, baraja);

                try {
                    bd.actualizarMano(codigoSala, uidUltimo, manos.get(uidUltimo), idToken);
                    bd.actualizarBaraja(codigoSala, baraja, idToken);
                    if (cartaAnterior != null) {
                        descarte.add(cartaAnterior);
                        bd.actualizarDescarte(codigoSala, descarte, idToken);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                narrarGlobal(nombres.getOrDefault(uidUltimo, "Jugador") + " ha robado de la baraja.");

                // Comprobar si la carta robada cambia la fase
                JuegoYusa.FaseRonda nuevaFase = ((JuegoYusa) juego).determinarFaseRonda(manos);
                if (nuevaFase != JuegoYusa.FaseRonda.NORMAL) {
                    narrarGlobal("¡La carta cambia la fase a " + nuevaFase.name() + "!");
                    try {
                        bd.actualizarFaseRonda(codigoSala, nuevaFase.name(), idToken);
                        bd.limpiarDecisionesRonda(codigoSala, idToken);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    // La nueva fase es DOCE o YUSA: todos ven cartas y se resuelve
                    try {
                        bd.publicarResolverRonda(codigoSala, idToken);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return;
                }
            }
        }

        // Ronda normal completada → publicar señal para que TODOS muestren cartas
        rondaEnCurso = false;
        ordenRondaActual.clear();
        narrarGlobal("Ronda completada. Revelando cartas...");

        try {
            bd.limpiarDecisionesRonda(codigoSala, idToken);
            bd.publicarResolverRonda(codigoSala, idToken); // ← TODOS muestran cartas
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void procesarDecisionUltimoJugadorDirector(String decision) {
        narrarPrivado(uidLocal, "Has elegido: " + decision);

        if (decision.equals("Robo de la baraja")) {
            if (baraja.isEmpty()) {
                narrarPrivado(uidLocal, "La baraja está vacía. Te quedas tu carta.");
            } else {
                String cartaAnterior = manos.get(uidLocal) != null
                        && !manos.get(uidLocal).isEmpty()
                        ? manos.get(uidLocal).get(0) : null;

                ((JuegoYusa) juego).robarCarta(uidLocal, manos, baraja);
                try {
                    bd.actualizarMano(codigoSala, uidLocal, manos.get(uidLocal), idToken);
                    bd.actualizarBaraja(codigoSala, baraja, idToken);
                    if (cartaAnterior != null) {
                        descarte.add(cartaAnterior);
                        bd.actualizarDescarte(codigoSala, descarte, idToken);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                narrarPrivado(uidLocal, "Has robado una carta de la baraja.");

                JuegoYusa.FaseRonda nuevaFase = ((JuegoYusa) juego).determinarFaseRonda(manos);
                if (nuevaFase != JuegoYusa.FaseRonda.NORMAL) {
                    narrarGlobal("¡La carta robada cambia la fase a " + nuevaFase.name() + "!");
                    try {
                        bd.actualizarFaseRonda(codigoSala, nuevaFase.name(), idToken);
                        bd.publicarResolverRonda(codigoSala, idToken);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    rondaEnCurso = false;
                    return;
                }
            }
        }

        rondaEnCurso = false;
        ordenRondaActual.clear();
        narrarGlobal("Ronda completada. Revelando cartas...");
        try {
            bd.limpiarDecisionesRonda(codigoSala, idToken);
            bd.publicarResolverRonda(codigoSala, idToken);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void resolverFinDeRondaNormal() {
        // Solo el director de ronda ejecuta esto
        if (!uidLocal.equals(uidTurnoActual)) {
            esperandoDecisionPropia = false;
            return;
        }

        // Leer manos frescas de Firebase antes de calcular perdedores
        try {
            Map<String, Object> partida = bd.leerPartida(codigoSala, idToken);
            if (partida != null) {
                Object manosObj = partida.get("manos");
                if (manosObj instanceof Map) {
                    Map<String, List<String>> manosFrescas
                            = (Map<String, List<String>>) manosObj;
                    // Normalizar EMPTY → lista vacía
                    for (Map.Entry<String, List<String>> e : manosFrescas.entrySet()) {
                        List<String> lista = e.getValue();
                        if (lista != null && lista.size() == 1
                                && "EMPTY".equals(lista.get(0))) {
                            e.setValue(new ArrayList<>());
                        }
                    }
                    this.manos.clear();
                    this.manos.putAll(manosFrescas);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        JuegoYusa jy = (JuegoYusa) juego;

        // 1. Determinar perdedores
        List<String> perdedores = jy.determinarPerdedores(manos,
                new ArrayList<>(jy.getJugadoresVivos()));

        if (perdedores.size() > 1) {
            // EMPATE → ronda extra entre los empatados
            iniciarRondaExtraEntreEmpatados(perdedores);
            return;
        }

        if (!perdedores.isEmpty()) {
            String perdedor = perdedores.get(0);
            narrarGlobal(nombres.getOrDefault(perdedor, perdedor) + " pierde una vida.");
            boolean eliminado = jy.perderVida(perdedor);
            if (eliminado) {
                narrarGlobal("¡" + nombres.getOrDefault(perdedor, perdedor) + " eliminado!");
            }
            try {
                bd.actualizarVidas(codigoSala, jy.getTodasLasVidas(), idToken);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 2. Descartar manos y actualizar Firebase
        jy.descartarManosAlFinDeRonda(manos, descarte);
        try {
            bd.actualizarDescarte(codigoSala, descarte, idToken);
            for (String uid : ordenJugadoresGlobal) {
                bd.actualizarMano(codigoSala, uid, manos.get(uid), idToken);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 3. Reset baraja si hace falta
        if (jy.debeResetearBaraja(descarte)) {
            jy.resetearBaraja(baraja, descarte);
            narrarGlobal("Se barajan todas las cartas.");
            try {
                bd.actualizarBaraja(codigoSala, baraja, idToken);
                bd.actualizarDescarte(codigoSala, descarte, idToken);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 4. ¿Partida terminada?
        if (jy.getJugadoresVivos().size() <= 1) {
            narrarGlobal("¡La partida ha terminado!");
            try {
                bd.actualizarEstadoPartida(codigoSala, "finalizada", idToken);
            } catch (Exception e) {
                e.printStackTrace();
            }
            finalizarPartida();
            return;
        }

        // 5. Limpiar nodo resolverRonda para que no se dispare otra vez
        try {
            bd.limpiarResolverRonda(codigoSala, idToken);
            // Limpiar también ultimoJugadorRonda para que no quede basura en BD
            db.borrarNodo("salas/" + codigoSala + "/partida/ultimoJugadorRonda", idToken);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 6. Avanzar turno global al siguiente vivo
        //    siguienteTurno() de JuegoYusa ya usa getJugadoresVivos()
        String siguienteTurno = jy.siguienteTurno(uidTurnoActual,
                new ArrayList<>(jy.getJugadoresVivos()));

        // 7. Repartir nueva ronda
        jy.repartirCartas(manos, baraja);
        try {
            for (String uid : ordenJugadoresGlobal) {
                bd.actualizarMano(codigoSala, uid, manos.get(uid), idToken);
            }
            bd.actualizarBaraja(codigoSala, baraja, idToken);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 8. ⚠️ Resetear rondaEnCurso ANTES de actualizar el turno global,
        //    porque el listener de escucharTurno comprueba !rondaEnCurso
        //    para llamar a iniciarRondaYusa().
        rondaEnCurso = false;

        // 9. Publicar nuevo turno global → escucharTurno disparará iniciarRondaYusa()
        try {
            bd.actualizarTurno(codigoSala, siguienteTurno, idToken);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void iniciarRondaExtraEntreEmpatados(List<String> empatados) {
        // Narrar empate
        StringBuilder sb = new StringBuilder("¡Empate entre ");
        for (int i = 0; i < empatados.size(); i++) {
            sb.append(nombres.getOrDefault(empatados.get(i), empatados.get(i)));
            if (i < empatados.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("! Ronda extra pendiente de implementar. Por ahora pierden vida todos.");
        narrarGlobal(sb.toString());

        // Temporal: restar vida a todos los empatados
        JuegoYusa jy = (JuegoYusa) juego;
        for (String uid : empatados) {
            jy.perderVida(uid);
        }
        try {
            bd.actualizarVidas(codigoSala, jy.getTodasLasVidas(), idToken);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Continuar con el resto del flujo normal de fin de ronda
        // (llamando de nuevo con lista vacía de perdedores para que no bucle)
        resolverFinDeRondaNormal();
    }

}
