package firebase;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

public class BDPartidaService {

    private final FirebaseDatabaseService db;
    private final Gson gson = new Gson();

    public BDPartidaService(FirebaseDatabaseService db) {
        this.db = db;
    }

    // ---------------------------------------------------------
    // LECTURA DE LA PARTIDA
    // ---------------------------------------------------------
    public Map<String, Object> leerPartida(String codigoSala, String idToken) throws IOException {
        String json = db.leerNodo("salas/" + codigoSala + "/partida", idToken);

        if (json == null || json.equals("null")) {
            return null;
        }

        return gson.fromJson(json, Map.class);
    }

    // ---------------------------------------------------------
    // CREAR PARTIDA (cuando el host pulsa "Iniciar partida")
    // ---------------------------------------------------------
    public void iniciarPartida(String codigoSala, Map<String, Object> datosPartida, String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida", datosPartida, idToken);
    }

    // ---------------------------------------------------------
    // ACTUALIZAR MANOS
    // ---------------------------------------------------------
    public void actualizarMano(String codigoSala, String uid, List<String> mano, String token) throws IOException {

        // 🛡️ Si la mano está vacía → escribir un valor sentinela
        List<String> manoSegura;

        if (mano == null || mano.isEmpty()) {
            manoSegura = List.of("EMPTY");   // nunca borra el nodo
        } else {
            manoSegura = mano;
        }

        db.actualizarNodo("salas/" + codigoSala + "/partida/manos/" + uid, manoSegura, token);
    }

    // ---------------------------------------------------------
    // ACTUALIZAR BARAJA
    // ---------------------------------------------------------
    public void actualizarBaraja(String codigoSala, List<String> baraja, String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida/baraja", baraja, idToken);
    }

    // ---------------------------------------------------------
    // ACTUALIZAR DESCARTE
    // ---------------------------------------------------------
    public void actualizarDescarte(String codigoSala, List<String> descarte, String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida/descarte", descarte, idToken);
    }

    // ---------------------------------------------------------
    // ACTUALIZAR TURNO
    // ---------------------------------------------------------
    public void actualizarTurno(String codigoSala, String uidTurno, String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida/turno", uidTurno, idToken);
    }

    // ---------------------------------------------------------
    // ACTUALIZAR VIDAS
    // ---------------------------------------------------------
    /**
     * public void actualizarVidas(String codigoSala, Map<String, Integer>
     * vidas, String idToken) throws IOException { db.actualizarNodo("salas/" +
     * codigoSala + "/partida/vidas", vidas, idToken); }
     */
    public void actualizarPescaitos(String codigoSala, Map<String, Integer> pescaitos, String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida/pescaitos", pescaitos, idToken);
    }

    public void actualizarPescaitoJugador(String codigoSala, String uid, int valor, String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida/pescaitos/" + uid, valor, idToken);
    }

    // ---------------------------------------------------------
// ACTUALIZAR ESTADO DE LA PARTIDA
// ---------------------------------------------------------
    public void actualizarEstadoPartida(String codigoSala, String estado, String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida/estado", estado, idToken);
    }

    public void escucharEstadoPartida(String codigoSala, String idToken, Consumer<String> callback) {
        new Thread(() -> {
            String ultimo = null;

            while (true) {
                try {
                    String valor = db.leerNodo("salas/" + codigoSala + "/partida/estado", idToken);

                    if (valor != null && !valor.equals(ultimo)) {
                        ultimo = valor;
                        callback.accept(valor.replace("\"", ""));
                    }

                    Thread.sleep(500);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public Map<String, Map<String, Object>> leerPescaitos(String codigoSala, String idToken) throws IOException {
        String json = db.leerNodo("salas/" + codigoSala + "/partida/pescaitos", idToken);

        System.out.println("DEBUG leerPescaitos JSON: " + json); // quitar cuando confirmes el bug

        if (json == null || json.equals("null")) {
            return new HashMap<>();
        }

        Type tipoGenerico = new TypeToken<Map<String, Object>>() {
        }.getType();
        Map<String, Object> mapaRaw = gson.fromJson(json, tipoGenerico);
        if (mapaRaw == null) {
            return new HashMap<>();
        }

        Map<String, Map<String, Object>> resultado = new HashMap<>();
        for (Map.Entry<String, Object> entry : mapaRaw.entrySet()) {
            String uid = entry.getKey();
            Object valor = entry.getValue();

            if (valor instanceof Map) {
                // Caso normal: {"1": true, "3": true, ...}
                resultado.put(uid, (Map<String, Object>) valor);

            } else if (valor instanceof List) {
                // Firebase convirtió el mapa en array porque las claves eran números
                // consecutivos. Cada posición i con valor no-null = pescaito del número i.
                List<?> lista = (List<?>) valor;
                Map<String, Object> convertido = new HashMap<>();
                for (int i = 0; i < lista.size(); i++) {
                    if (lista.get(i) != null) {
                        convertido.put(String.valueOf(i), lista.get(i));
                    }
                }
                resultado.put(uid, convertido);

            } else {
                // null u otro tipo inesperado → sin pescaitos
                resultado.put(uid, new HashMap<>());
            }
        }

        return resultado;
    }

    public void registrarPescaito(String codigoSala, String uidJugador,
            int numero, String idToken) throws IOException {
        // Usar "n_X" como clave en lugar de "X" para evitar que Firebase
        // convierta el mapa en array cuando las claves son números consecutivos
        String ruta = "salas/" + codigoSala + "/partida/pescaitos/"
                + uidJugador + "/n_" + numero;
        db.actualizarNodo(ruta, true, idToken);
    }

    // ---------------------------------------------------------
    // BORRAR PARTIDA (cuando termina o se vuelve al lobby)
    // ---------------------------------------------------------
    public void borrarPartida(String codigoSala, String idToken) throws IOException {
        db.borrarNodo("salas/" + codigoSala + "/partida", idToken);
    }

    public Object leerCampo(String codigoSala, String campo, String idToken) throws IOException {
        String json = db.leerNodo("salas/" + codigoSala + "/partida/" + campo, idToken);
        return gson.fromJson(json, Object.class);
    }

    public void actualizarNarrador(String codigoSala, Map<String, Object> mensaje, String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida/narrador", mensaje, idToken);
    }

    public void actualizarFaseRonda(String codigoSala, String fase, String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida/faseRonda", fase, idToken);
    }

    public String leerFaseRonda(String codigoSala, String idToken) throws IOException {
        String json = db.leerNodo("salas/" + codigoSala + "/partida/faseRonda", idToken);
        if (json == null || json.equals("null")) {
            return null;
        }
        return json.replace("\"", "");
    }

    public void actualizarTurnoActualRonda(String codigoSala, String uid, String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida/turnoActualRonda", uid, idToken);
    }

    public String leerTurnoActualRonda(String codigoSala, String idToken) throws IOException {
        String json = db.leerNodo("salas/" + codigoSala + "/partida/turnoActualRonda", idToken);
        if (json == null || json.equals("null")) {
            return null;
        }
        return json.replace("\"", "");
    }

    public void actualizarObjetivoYusa(String codigoSala,
            String uidPoseedor,
            String uidObjetivo,
            String idToken) throws IOException {

        String ruta = "salas/" + codigoSala + "/partida/yusa/objetivos/" + uidPoseedor;
        db.actualizarNodo(ruta, uidObjetivo, idToken);
    }

    public void actualizarPaloElegidoYusa(String codigoSala,
            String uidObjetivo,
            String palo,
            String idToken) throws IOException {

        String ruta = "salas/" + codigoSala + "/partida/yusa/palosElegidos/" + uidObjetivo;
        db.actualizarNodo(ruta, palo, idToken);
    }

    public Map<String, String> leerPalosElegidosYusa(String codigoSala, String idToken) throws IOException {
        String json = db.leerNodo("salas/" + codigoSala + "/partida/yusa/palosElegidos", idToken);
        if (json == null || json.equals("null")) {
            return new HashMap<>();
        }
        return gson.fromJson(json, Map.class);
    }

    public Map<String, String> leerObjetivosYusa(String codigoSala, String idToken) throws IOException {
        String json = db.leerNodo("salas/" + codigoSala + "/partida/yusa/objetivos", idToken);
        if (json == null || json.equals("null")) {
            return new HashMap<>();
        }
        return gson.fromJson(json, Map.class);
    }

    public void actualizarEmpatados(String codigoSala,
            List<String> empatados,
            String idToken) throws IOException {

        db.actualizarNodo("salas/" + codigoSala + "/partida/empatados", empatados, idToken);
    }

    public List<String> leerEmpatados(String codigoSala, String idToken) throws IOException {
        String json = db.leerNodo("salas/" + codigoSala + "/partida/empatados", idToken);
        if (json == null || json.equals("null")) {
            return new ArrayList<>();
        }
        return gson.fromJson(json, List.class);
    }

    public void actualizarEstadoRonda(String codigoSala,
            String estado,
            String idToken) throws IOException {

        db.actualizarNodo("salas/" + codigoSala + "/partida/estadoRonda", estado, idToken);
    }

    public String leerEstadoRonda(String codigoSala, String idToken) throws IOException {
        String json = db.leerNodo("salas/" + codigoSala + "/partida/estadoRonda", idToken);
        if (json == null || json.equals("null")) {
            return null;
        }
        return json.replace("\"", "");
    }

    public void escucharNarrador(String codigoSala, String idToken, Consumer<Map<String, Object>> callback) {
        new Thread(() -> {
            String ultimo = null;

            while (true) {
                try {
                    String json = db.leerNodo("salas/" + codigoSala + "/partida/narrador", idToken);

                    if (json != null && !json.equals(ultimo)) {
                        ultimo = json;

                        Map<String, Object> mensaje = gson.fromJson(json, Map.class);
                        if (mensaje != null) {
                            callback.accept(mensaje);
                        }
                    }

                    Thread.sleep(500);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void escucharTurno(String codigoSala, String idToken, Consumer<String> callback) {
        new Thread(() -> {
            String ultimo = null;

            while (true) {
                try {
                    String valor = db.leerNodo("salas/" + codigoSala + "/partida/turno", idToken);

                    if (valor != null && !valor.equals(ultimo)) {
                        ultimo = valor;
                        callback.accept(valor.replace("\"", ""));
                    }

                    Thread.sleep(500);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // ================================================================
//  MÉTODOS NUEVOS para BDPartidaService.java
//  Añade todos al final de la clase, antes del último "}"
//  El PartidaController.java entregado llama a todos estos.
// ================================================================
    // ── VIDAS ────────────────────────────────────────────────────
    public void actualizarVidaJugador(String codigoSala, String uid,
            int vidas, String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida/vidas/" + uid, vidas, idToken);
    }

    public Map<String, Integer> leerVidas(String codigoSala, String idToken) throws IOException {
        String json = db.leerNodo("salas/" + codigoSala + "/partida/vidas", idToken);
        if (json == null || "null".equals(json)) {
            return null;
        }
        Map<String, Object> raw = gson.fromJson(json, Map.class);
        Map<String, Integer> resultado = new HashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            resultado.put(e.getKey(), ((Number) e.getValue()).intValue());
        }
        return resultado;
    }

    // ── OBJETIVOS YUSA ───────────────────────────────────────────
    /**
     * Escribe true en partida/yusa/marcados/{uidObjetivo} para que el listener
     * de ese cliente sepa que fue elegido.
     */
    /**
     * public void marcarObjetivoYusa(String codigoSala, String uidObjetivo,
     * String idToken) throws IOException { db.actualizarNodo("salas/" +
     * codigoSala + "/partida/yusa/marcados/" + uidObjetivo, true, idToken); }
     */
    /**
     * public void escucharObjetivosYusa(String codigoSala, String idToken,
     * Consumer<Map<String, Object>> callback) { new Thread(() -> { String
     * ultimo = null; while (true) { try { String json = db.leerNodo( "salas/" +
     * codigoSala + "/partida/yusa/marcados", idToken); if (json != null &&
     * !json.equals(ultimo)) { ultimo = json; if (!"null".equals(json)) {
     * Map<String, Object> marcados = gson.fromJson(json, Map.class); if
     * (marcados != null) { callback.accept(marcados); } } else {
     * callback.accept(new HashMap<>()); } } Thread.sleep(500); } catch
     * (Exception e) { e.printStackTrace(); } } }).start(); }
     */
    public void limpiarObjetivosYusa(String codigoSala, String idToken) throws IOException {
        db.borrarNodo("salas/" + codigoSala + "/partida/yusa/marcados", idToken);
    }

    // ── PALOS ELEGIDOS YUSA ──────────────────────────────────────
    public void escucharPalosYusa(String codigoSala, String idToken,
            Consumer<Map<String, String>> callback) {
        new Thread(() -> {
            String ultimo = null;
            while (true) {
                try {
                    String json = db.leerNodo(
                            "salas/" + codigoSala + "/partida/yusa/palosElegidos", idToken);
                    if (json != null && !json.equals(ultimo)) {
                        ultimo = json;
                        if (!"null".equals(json)) {
                            Map<String, String> palos = gson.fromJson(json, Map.class);
                            if (palos != null) {
                                callback.accept(palos);
                            }
                        }
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void limpiarPalosYusa(String codigoSala, String idToken) throws IOException {
        db.borrarNodo("salas/" + codigoSala + "/partida/yusa/palosElegidos", idToken);
    }

    // ── FASE DE RONDA ────────────────────────────────────────────
    public void escucharFaseRonda(String codigoSala, String idToken,
            Consumer<String> callback) {
        new Thread(() -> {
            String ultimo = null;
            while (true) {
                try {
                    String valor = db.leerNodo(
                            "salas/" + codigoSala + "/partida/faseRonda", idToken);
                    if (valor != null && !valor.equals(ultimo)) {
                        ultimo = valor;
                        callback.accept(valor.replace("\"", ""));
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // ── TURNO INICIAL DE RONDA NORMAL ────────────────────────────
    public void escucharTurnoActualRonda(String codigoSala, String idToken,
            Consumer<String> callback) {
        new Thread(() -> {
            String ultimo = null;
            while (true) {
                try {
                    String valor = db.leerNodo(
                            "salas/" + codigoSala + "/partida/turnoActualRonda", idToken);
                    if (valor != null && !valor.equals(ultimo)) {
                        ultimo = valor;
                        callback.accept(valor.replace("\"", ""));
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void publicarDecisionRonda(String codigoSala, String uid,
            String decision, String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida/decisionesRonda/" + uid,
                decision, idToken);
    }

    public void limpiarDecisionesRonda(String codigoSala, String idToken) throws IOException {
        db.borrarNodo("salas/" + codigoSala + "/partida/decisionesRonda", idToken);
    }

    public void escucharDecisionesRonda(String codigoSala, String idToken,
            Consumer<Map<String, Object>> callback) {
        new Thread(() -> {
            String ultimo = null;
            while (true) {
                try {
                    String json = db.leerNodo(
                            "salas/" + codigoSala + "/partida/decisionesRonda", idToken);
                    if (json != null && !json.equals(ultimo)) {
                        ultimo = json;
                        if (!"null".equals(json)) {
                            Map<String, Object> decisiones = new com.google.gson.Gson()
                                    .fromJson(json, Map.class);
                            if (decisiones != null) {
                                callback.accept(decisiones);
                            }
                        }
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // Marca quién es el último jugador de esta ronda (para mostrarle
    // botones especiales si no es el director de ronda).
    public void actualizarUltimoJugadorRonda(String codigoSala,
            String uid,
            String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida/ultimoJugadorRonda", uid, idToken);
    }

    // El director de ronda publica cuándo debe mostrarse la resolución.
    // Todos los clientes escuchan esto para mostrar cartas y quitar vida.
    public void publicarResolverRonda(String codigoSala,
            String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida/resolverRonda",
                System.currentTimeMillis(), idToken);
    }

    public void limpiarResolverRonda(String codigoSala, String idToken) throws IOException {
        db.borrarNodo("salas/" + codigoSala + "/partida/resolverRonda", idToken);
    }

    public void escucharResolverRonda(String codigoSala, String idToken,
            java.util.function.Consumer<Long> callback) {
        new Thread(() -> {
            String ultimo = null;
            while (true) {
                try {
                    String val = db.leerNodo(
                            "salas/" + codigoSala + "/partida/resolverRonda", idToken);
                    if (val != null && !val.equals(ultimo) && !"null".equals(val)) {
                        ultimo = val;
                        callback.accept(Long.parseLong(val));
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void escucharUltimoJugadorRonda(String codigoSala, String idToken,
            java.util.function.Consumer<String> callback) {
        new Thread(() -> {
            String ultimo = null;
            while (true) {
                try {
                    String val = db.leerNodo(
                            "salas/" + codigoSala + "/partida/ultimoJugadorRonda", idToken);
                    if (val != null && !val.equals(ultimo)) {
                        ultimo = val;
                        callback.accept(val.replace("\"", ""));
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void incrementarRonda(String codigoSala, int nuevaRonda,
            String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida/ronda",
                nuevaRonda, idToken);
    }

    public void escucharPartida(String codigoSala, String idToken,
            Consumer<Map<String, Object>> callback) {
        new Thread(() -> {
            String ultimo = null;
            while (true) {
                try {
                    String json = db.leerNodo("salas/" + codigoSala + "/partida", idToken);
                    if (json != null && !json.equals(ultimo)) {
                        ultimo = json;
                        if (!"null".equals(json)) {
                            Map<String, Object> partida = gson.fromJson(json, Map.class);
                            if (partida != null) {
                                callback.accept(partida);
                            }
                        }
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // ================================================================
//  MÉTODOS NUEVOS — añade al final de BDPartidaService.java
//  antes del último "}"
// ================================================================
    /**
     * Escribe true en partida/yusa/marcados/{uidObjetivo} para que el cliente
     * de ese jugador sepa que fue elegido como objetivo en esta ronda de yusa.
     */
    public void marcarObjetivoYusa(String codigoSala,
            String uidObjetivo,
            String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida/yusa/marcados/" + uidObjetivo,
                true, idToken);
    }

    /**
     * Escucha el nodo partida/yusa/marcados. El callback recibe el mapa
     * completo cada vez que un poseedor de yusa marca a su objetivo. El cliente
     * usa esto para saber si fue elegido (containsKey(uidLocal)).
     */
    public void escucharObjetivosYusa(String codigoSala, String idToken,
            Consumer<Map<String, Object>> callback) {
        new Thread(() -> {
            String ultimo = null;
            while (true) {
                try {
                    String json = db.leerNodo(
                            "salas/" + codigoSala + "/partida/yusa/marcados", idToken);
                    if (json != null && !json.equals(ultimo)) {
                        ultimo = json;
                        if (!"null".equals(json)) {
                            Map<String, Object> marcados = gson.fromJson(json, Map.class);
                            if (marcados != null) {
                                callback.accept(marcados);
                            }
                        } else {
                            callback.accept(new HashMap<>());
                        }
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * Actualiza las vidas de todos los jugadores de una vez. Útil al inicio de
     * la partida para guardar las 3 vidas iniciales.
     */
    public void actualizarVidas(String codigoSala,
            Map<String, Integer> vidas,
            String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida/vidas", vidas, idToken);
    }

    public void publicarDoceJugado(String codigoSala, String idToken) throws IOException {
        db.actualizarNodo("salas/" + codigoSala + "/partida/doceJugado",
                System.currentTimeMillis(), idToken);
    }

    public void limpiarDoceJugado(String codigoSala, String idToken) throws IOException {
        db.borrarNodo("salas/" + codigoSala + "/partida/doceJugado", idToken);
    }

    public void escucharDoceJugado(String codigoSala, String idToken,
            java.util.function.Consumer<Long> callback) {
        new Thread(() -> {
            String ultimo = null;
            while (true) {
                try {
                    String val = db.leerNodo(
                            "salas/" + codigoSala + "/partida/doceJugado", idToken);
                    if (val != null && !val.equals(ultimo) && !"null".equals(val)) {
                        ultimo = val;
                        callback.accept(Long.parseLong(val.trim()));
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void publicarEstadoRonda(String codigoSala,
            String accion,
            String fase,
            String turnoUid,
            String idToken) throws IOException {
        Map<String, Object> estado = new HashMap<>();
        estado.put("accion", accion);
        estado.put("fase", fase);
        estado.put("turnoUid", turnoUid == null ? "null" : turnoUid);
        estado.put("ts", System.currentTimeMillis());
        db.actualizarNodo("salas/" + codigoSala + "/partida/estadoRonda", estado, idToken);
    }

    public void limpiarEstadoRonda(String codigoSala, String idToken) throws IOException {
        db.borrarNodo("salas/" + codigoSala + "/partida/estadoRonda", idToken);
    }

    public void escucharEstadoRonda(String codigoSala, String idToken,
            Consumer<Map<String, Object>> callback) {
        new Thread(() -> {
            String ultimo = null;
            while (true) {
                try {
                    String json = db.leerNodo(
                            "salas/" + codigoSala + "/partida/estadoRonda", idToken);
                    if (json != null && !json.equals(ultimo)) {
                        ultimo = json;
                        if (!"null".equals(json)) {
                            Map<String, Object> estado = gson.fromJson(json, Map.class);
                            if (estado != null) {
                                callback.accept(estado);
                            }
                        }
                    }
                    Thread.sleep(200);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void publicarDecisionJugador(String codigoSala,
            String uidJugador,
            String decision,
            String idToken) throws IOException {
        Map<String, Object> datos = new HashMap<>();
        datos.put("uid", uidJugador);
        datos.put("decision", decision);
        datos.put("ts", System.currentTimeMillis());
        db.actualizarNodo("salas/" + codigoSala + "/partida/decisionJugador", datos, idToken);
    }

    public void limpiarDecisionJugador(String codigoSala, String idToken) throws IOException {
        db.borrarNodo("salas/" + codigoSala + "/partida/decisionJugador", idToken);
    }

    public void escucharDecisionJugador(String codigoSala, String idToken,
            Consumer<Map<String, Object>> callback) {
        new Thread(() -> {
            String ultimo = null;
            while (true) {
                try {
                    String json = db.leerNodo(
                            "salas/" + codigoSala + "/partida/decisionJugador", idToken);
                    if (json != null && !json.equals(ultimo)) {
                        ultimo = json;
                        if (!"null".equals(json)) {
                            Map<String, Object> datos = gson.fromJson(json, Map.class);
                            if (datos != null) {
                                callback.accept(datos);
                            }
                        }
                    }
                    Thread.sleep(200);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void publicarManosYusa(String codigoSala,
            Map<String, List<String>> manos,
            String idToken) throws IOException {
        Map<String, Object> manosSeguras = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : manos.entrySet()) {
            List<String> mano = entry.getValue();
            manosSeguras.put(entry.getKey(),
                    (mano == null || mano.isEmpty()) ? List.of("EMPTY") : mano);
        }
        db.actualizarNodo("salas/" + codigoSala + "/partida/manos", manosSeguras, idToken);
    }

}
