package ui;

import com.google.gson.Gson;
import firebase.BDPartidaService;
import firebase.FirebaseDatabaseService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import i18n.IdiomaManager;
import java.util.ResourceBundle;
import partidaUTIL.Baraja;
import partidaUTIL.Carta;
import partidaUTIL.JuegoYusa;

public class SalaOnlineController {

    @FXML
    private Label lblCodigoSala;
    @FXML
    private ListView<String> listaJugadores;
    @FXML
    private ComboBox<String> comboModoJuego;

    @FXML
    private Button btnIniciar;
    @FXML
    private Button btnSalir;
    @FXML
    private StackPane rootSala;

    private boolean esHost = false;

    private Timer timer;

    private Map<String, String> nombreAUid = new HashMap<>();

    private final ResourceBundle bundle = IdiomaManager.getBundle();

    @FXML
    private void initialize() {

        Animaciones.animarBoton(btnSalir);
        Animaciones.animarBoton(btnIniciar);

        // 1. Mostrar código real de la sala
        lblCodigoSala.setText(bundle.getString("salaOnline.codigo") + SalaContext.codigoSalaActual);

        // 2. Cargar datos reales de Firebase
        cargarDatosSala();

        // 3. Configurar modos de juego
        comboModoJuego.getItems().addAll(
                "Pescaito",
                "Puteao",
                "Yusa",
                "Culo",
                "Chopinki"
        );
        comboModoJuego.getSelectionModel().selectFirst();

        // 4. Control de permisos del host
        if (!esHost) {
            //btnExpulsar.setDisable(true);
            btnIniciar.setDisable(true);
            comboModoJuego.setDisable(true);
        }

        // 5. Botones
        btnSalir.setOnAction(e -> salir());
        btnIniciar.setOnAction(e -> iniciarPartida());

        listaJugadores.setCellFactory(lv -> new ListCell<String>() {

            private final HBox contenedor = new HBox();
            private final Label lblNombre = new Label();

            // Imagen del botón de expulsar
            private final ImageView iconoExpulsar = new ImageView(
                    new Image(getClass().getResourceAsStream("/ui/graphicResources/imagenes/btnExpulsar.png"))
            );

            private final Button btnExpulsar = new Button();

            {
                contenedor.setSpacing(10);
                contenedor.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(lblNombre, Priority.ALWAYS);
                lblNombre.setMaxWidth(Double.MAX_VALUE);

                // Ajustar tamaño del icono
                iconoExpulsar.setFitWidth(50);
                iconoExpulsar.setFitHeight(50);

                // Botón sin texto, solo icono
                btnExpulsar.setGraphic(iconoExpulsar);
                btnExpulsar.setText(null);

                // Estilo del botón (sin fondo, solo icono)
                btnExpulsar.setStyle(
                        "-fx-background-color: transparent;"
                        + "-fx-padding: 0;"
                );

                btnExpulsar.setOnAction(e -> {
                    listaJugadores.getSelectionModel().select(getItem());
                    expulsarJugador();
                });
            }

            @Override
            protected void updateItem(String nombre, boolean empty) {
                super.updateItem(nombre, empty);

                if (empty || nombre == null) {
                    setGraphic(null);
                    return;
                }

                lblNombre.setText(nombre);
                contenedor.getChildren().setAll(lblNombre);

                // Si soy host y NO es el host → mostrar botón expulsar
                if (esHost && !nombre.contains("(Host)")) {
                    contenedor.getChildren().add(btnExpulsar);
                }

                setGraphic(contenedor);
            }
        });

        // 7. Iniciar refresco periódico
        iniciarRefrescoPeriodico();
    }

    /**
     * Lee la sala en Firebase y carga: - Si el usuario es host - La lista de
     * jugadores
     */
    private void cargarDatosSala() {
        try {
            FirebaseDatabaseService db = new FirebaseDatabaseService();
            String token = MainApp.usuarioActualToken;
            String codigo = SalaContext.codigoSalaActual;

            // Leer nodo completo de la sala
            String jsonSala = db.leerNodo("salas/" + codigo, token);

            if (jsonSala == null || jsonSala.equals("null")) {
                System.out.println("La sala no existe.");
                return;
            }

            Map<String, Object> datosSala = new com.google.gson.Gson().fromJson(jsonSala, Map.class);

            // 1. Comprobar si soy el host
            String hostUID = (String) datosSala.get("host");
            esHost = hostUID.equals(MainApp.usuarioActualUID);

            // 2. Cargar jugadores
            Map<String, Object> jugadores = (Map<String, Object>) datosSala.get("jugadores");

            listaJugadores.getItems().clear();
            nombreAUid.clear();

            for (String uid : jugadores.keySet()) {

                String jsonUsuario = db.leerNodo("usuarios/" + uid, token);
                String nombre = uid;

                if (jsonUsuario != null && !jsonUsuario.equals("null")) {
                    Map<String, Object> datosUsuario = new Gson().fromJson(jsonUsuario, Map.class);
                    if (datosUsuario != null && datosUsuario.get("nombre") != null) {
                        nombre = datosUsuario.get("nombre").toString();
                    }
                }

                if (uid.equals(hostUID)) {
                    String mostrado = nombre + " (Host)";
                    listaJugadores.getItems().add(mostrado);
                    nombreAUid.put(mostrado, uid);
                } else {
                    listaJugadores.getItems().add(nombre);
                    nombreAUid.put(nombre, uid);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error cargando datos de la sala: " + e.getMessage());
        }
    }

    private void salir() {
        salirDeSala(MainApp.usuarioActualUID);
        timer.cancel();
        MainApp.cambiarEscena("menuOnline.fxml", 800, 600);
    }

    @FXML
    private void iniciarPartida() {
        if (!esHost) {
            return;
        }

        String modo = comboModoJuego.getValue();
        if (modo == null) {
            System.err.println("Debes seleccionar un modo de juego.");
            return;
        }

        try {
            String token = MainApp.usuarioActualToken;
            String codigo = SalaContext.codigoSalaActual;

            FirebaseDatabaseService rawDb = new FirebaseDatabaseService();
            BDPartidaService bd = new BDPartidaService(rawDb);

            // 1. Leer sala
            String jsonSala = rawDb.leerNodo("salas/" + codigo, token);
            Map<String, Object> sala = new Gson().fromJson(jsonSala, Map.class);
            Map<String, Object> jugadores = (Map<String, Object>) sala.get("jugadores");

            // 2. Crear baraja real
            Baraja baraja = new Baraja();
            baraja.barajar();

            List<String> barajaRestante = new ArrayList<>();
            for (Carta c : baraja.getCartasRestantes()) {
                barajaRestante.add(c.getRutaImagen());
            }

            // 3. Crear manos vacías
            Map<String, List<String>> manos = new HashMap<>();
            for (String uid : jugadores.keySet()) {
                manos.put(uid, null);
            }

            // 3. Crear vidas iniciales
            Map<String, Integer> vidas = new HashMap<>();
            for (String uid : jugadores.keySet()) {
                vidas.put(uid, JuegoYusa.VIDAS_INICIALES); // normalmente 3
            }

            // 4. Crear estructura de partida
            Map<String, Object> datosPartida = new HashMap<>();
            datosPartida.put("estado", "iniciada");
            datosPartida.put("modo", modo);
            datosPartida.put("manos", manos);
            datosPartida.put("baraja", barajaRestante);
            datosPartida.put("descarte", new ArrayList<String>());
            datosPartida.put("turno", sala.get("host"));
            datosPartida.put("ronda", 1);
            datosPartida.put("vidas", vidas);

            // 🟣 Añadir narrador inicial
            Map<String, Object> narradorInicial = new HashMap<>();
            narradorInicial.put("tipo", "global");
            narradorInicial.put("texto", "La partida ha comenzado");
            narradorInicial.put("uid", null);
            datosPartida.put("narrador", narradorInicial);

            // 5. Guardar en Firebase usando BDPartidaService
            bd.iniciarPartida(codigo, datosPartida, token);

            System.out.println("Partida iniciada correctamente.");

        } catch (Exception e) {
            e.printStackTrace();
            Animaciones.mostrarError(rootSala, bundle.getString("salaOnlineController.iniciar"));
        }
    }

    private void expulsarJugador() {
        if (!esHost) {
            return;
        }

        String seleccionado = listaJugadores.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            return;
        }

        // Obtener UID real del jugador seleccionado
        String uidExpulsado = nombreAUid.get(seleccionado);
        if (uidExpulsado == null) {
            return;
        }

        try {
            FirebaseDatabaseService db = new FirebaseDatabaseService();
            String token = MainApp.usuarioActualToken;
            String codigo = SalaContext.codigoSalaActual;

            // 1. Eliminar al jugador del nodo jugadores de la sala
            db.borrarNodo("salas/" + codigo + "/jugadores/" + uidExpulsado, token);

            // 2. Añadirlo al nodo expulsados de la sala
            db.actualizarNodo("salas/" + codigo + "/expulsados/" + uidExpulsado, true, token);

            // 3. Quitarle la salaActual al usuario expulsado
            db.borrarNodo("usuarios/" + uidExpulsado + "/salaActual", token);

            // 4. Marcar que ha sido expulsado de esta sala
            db.actualizarNodo("usuarios/" + uidExpulsado + "/expulsadoDeSala", codigo, token);

            // 4. Eliminarlo de la lista visual SIN refrescar toda la lista
            listaJugadores.getItems().remove(seleccionado);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void iniciarRefrescoPeriodico() {
        timer = new java.util.Timer();

        timer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                javafx.application.Platform.runLater(() -> {
                    refrescarJugadores();
                });
            }
        }, 0, 1000); // refresco cada 1 segundo
    }

    private void refrescarJugadores() {

        // 1) DETECTAR SI HE SIDO EXPULSADO
        try {
            FirebaseDatabaseService db = new FirebaseDatabaseService();
            String token = MainApp.usuarioActualToken;
            String uid = MainApp.usuarioActualUID;

            String expulsado = db.leerNodo("usuarios/" + uid + "/expulsadoDeSala", token);

            if (expulsado != null && !expulsado.equals("null")) {

                String valor = expulsado.replace("\"", "").trim();

                if (!valor.isEmpty()) {

                    // Limpiar el campo para evitar bucles
                    db.borrarNodo("usuarios/" + uid + "/expulsadoDeSala", token);

                    // (Opcional, por si el host no lo borró)
                    db.borrarNodo("usuarios/" + uid + "/salaActual", token);

                    if (timer != null) {
                        timer.cancel();
                    }

                    Platform.runLater(() -> {
                        Animaciones.mostrarError(rootSala, bundle.getString("salaOnlineController.expulsado"));

                        // Esperar 1 segundo antes de cambiar de escena
                        PauseTransition delay = new PauseTransition(Duration.seconds(3));
                        delay.setOnFinished(ev -> MainApp.cambiarEscena("menuOnline.fxml", 800, 600));
                        delay.play();
                    });

                    return;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2) REFRESCAR DATOS DE LA SALA
        try {
            FirebaseDatabaseService db = new FirebaseDatabaseService();
            String token = MainApp.usuarioActualToken;
            String codigo = SalaContext.codigoSalaActual;
            String jsonSala = db.leerNodo("salas/" + codigo, token);

            String estado = db.leerNodo("salas/" + codigo + "/partida/estado", token);

            if (estado != null && !estado.equals("null")) {

                String estadoLimpio = estado.replace("\"", "");

                if ("iniciada".equals(estadoLimpio)) {

                    if (timer != null) {
                        timer.cancel();
                    }

                    javafx.application.Platform.runLater(() -> {
                        MainApp.cambiarEscena("partida.fxml", 800, 600);
                    });

                    return;
                }
            }

            // Si la sala ya no existe → el host salió
            if (jsonSala == null || jsonSala.equals("null")) {

                if (timer != null) {
                    timer.cancel();
                }

                Platform.runLater(() -> {
                    Animaciones.mostrarError(rootSala, bundle.getString("salaOnlineController.hostSalio"));

                    PauseTransition delay = new PauseTransition(Duration.seconds(3));
                    delay.setOnFinished(ev -> MainApp.cambiarEscena("menuOnline.fxml", 800, 600));
                    delay.play();
                });
                
                return;
                
            }

            Map<String, Object> sala = new Gson().fromJson(jsonSala, Map.class);

            String hostUID = sala.get("host").toString();

            // Obtener jugadores como LinkedHashMap
            LinkedHashMap<String, Object> jugadores
                    = new Gson().fromJson(
                            new Gson().toJson(sala.get("jugadores")),
                            LinkedHashMap.class
                    );

            // Ordenar por joinedAt
            List<Map.Entry<String, Object>> lista = new ArrayList<>(jugadores.entrySet());

            lista.sort((a, b) -> {
                Map<String, Object> dataA = (Map<String, Object>) a.getValue();
                Map<String, Object> dataB = (Map<String, Object>) b.getValue();

                double ta = (double) dataA.get("joinedAt");
                double tb = (double) dataB.get("joinedAt");

                return Double.compare(ta, tb);
            });

            // Limpiar lista visual
            listaJugadores.getItems().clear();

            // Añadir host primero
            String jsonHost = db.leerNodo("usuarios/" + hostUID, token);
            String nombreHost = hostUID;

            if (jsonHost != null && !jsonHost.equals("null")) {
                Map<String, Object> datosHost = new Gson().fromJson(jsonHost, Map.class);
                if (datosHost.get("nombre") != null) {
                    nombreHost = datosHost.get("nombre").toString();
                }
            }

            listaJugadores.getItems().clear();
            nombreAUid.clear();

// Host
            String mostradoHost = nombreHost + " (Host)";
            listaJugadores.getItems().add(mostradoHost);
            nombreAUid.put(mostradoHost, hostUID);

// Resto
            for (Map.Entry<String, Object> entry : lista) {
                String uid = entry.getKey();
                if (uid.equals(hostUID)) {
                    continue;
                }

                String jsonUsuario = db.leerNodo("usuarios/" + uid, token);
                String nombre = uid;

                if (jsonUsuario != null && !jsonUsuario.equals("null")) {
                    Map<String, Object> datosUsuario = new Gson().fromJson(jsonUsuario, Map.class);
                    if (datosUsuario.get("nombre") != null) {
                        nombre = datosUsuario.get("nombre").toString();
                    }
                }

                listaJugadores.getItems().add(nombre);
                nombreAUid.put(nombre, uid);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void salirDeSala(String uid) {
        try {
            FirebaseDatabaseService db = new FirebaseDatabaseService();
            String token = MainApp.usuarioActualToken;
            String codigo = SalaContext.codigoSalaActual;

            // Leer sala
            String jsonSala = db.leerNodo("salas/" + codigo, token);
            if (jsonSala == null || jsonSala.equals("null")) {
                return;
            }

            Map<String, Object> sala = new Gson().fromJson(jsonSala, Map.class);
            String hostUID = sala.get("host").toString();

            // --- CASO 1: El que sale NO es el host ---
            if (!uid.equals(hostUID)) {

                // 1. Borrar al jugador de la sala
                db.borrarNodo("salas/" + codigo + "/jugadores/" + uid, token);

                // 2. Borrar salaActual del jugador
                db.borrarNodo("usuarios/" + uid + "/salaActual", token);

                return;
            }

            // --- CASO 2: El que sale ES EL HOST ---
            // 1. Borrar salaActual del host
            db.borrarNodo("usuarios/" + uid + "/salaActual", token);

            // 2. Expulsar a todos los jugadores (borrar salaActual)
            Map<String, Object> jugadores = (Map<String, Object>) sala.get("jugadores");

            for (String jugadorUID : jugadores.keySet()) {
                db.borrarNodo("usuarios/" + jugadorUID + "/salaActual", token);
            }

            // 3. Eliminar la sala completa
            db.borrarNodo("salas/" + codigo, token);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * private String obtenerUidDesdeNombre(String textoLista) { try {
     * FirebaseDatabaseService db = new FirebaseDatabaseService(); String token
     * = MainApp.usuarioActualToken;
     *
     * // Quitar " (Host)" si lo tiene String nombreLimpio =
     * textoLista.replace(" (Host)", "").trim();
     *
     * // Leer todos los usuarios String jsonUsuarios =
     * db.leerNodo("usuarios/", token); System.out.println("DEBUG
     * leerNodo(usuarios) = " + jsonUsuarios);
     *
     * if (jsonUsuarios == null || jsonUsuarios.equals("null")) { return null; }
     *
     * Map<String, Object> usuarios = new Gson().fromJson(jsonUsuarios,
     * Map.class);
     *
     * for (String uid : usuarios.keySet()) { Map<String, Object> datos =
     * (Map<String, Object>) usuarios.get(uid); if (datos.get("nombre") != null
     * && datos.get("nombre").toString().equals(nombreLimpio)) { return uid; } }
     *
     * System.out.println("DEBUG JSON USUARIOS = " + jsonUsuarios);
     *
     * } catch (Exception e) { e.printStackTrace(); }
     *
     * return null; }
     */
}
