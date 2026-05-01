package firebase;

import com.google.gson.Gson;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class FirebaseDatabaseService {

    private final Gson gson = new Gson();

    public void guardarUsuario(String uid, Map<String, Object> datosUsuario, String idToken) throws IOException {
        String url = FirebaseConfig.DATABASE_URL + "usuarios/" + uid + ".json?auth=" + idToken;
        String jsonBody = gson.toJson(datosUsuario);
        putJson(url, jsonBody);
    }

    private void putJson(String urlString, String jsonBody) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }
            System.out.println("Respuesta DB: " + response);
        }
    }

    public String leerNodo(String ruta, String idToken) throws IOException {
        String url = FirebaseConfig.DATABASE_URL + ruta + ".json?auth=" + idToken;
        return getJson(url);
    }

    private String getJson(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }
            return response.toString();
        }
    }

    public void actualizarNodo(String ruta, Object valor, String idToken) throws IOException {
        String url = FirebaseConfig.DATABASE_URL + ruta + ".json?auth=" + idToken;
        String jsonBody = gson.toJson(valor);
        putJson(url, jsonBody);  // USAR PUT EN VEZ DE PATCH
    }

    // POSIBLEMENTE EN DESUSO ***************************
    private void patchJson(String urlString, String jsonBody) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("PATCH");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes("utf-8"));
        }

        conn.getInputStream().close();
    }

    public void actualizarUsuario(String uid, Map<String, Object> datos, String idToken) throws IOException {
        String url = FirebaseConfig.DATABASE_URL + "usuarios/" + uid + ".json?auth=" + idToken;
        URL endpoint = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();

        conn.setRequestMethod("PATCH");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);

        String jsonBody = gson.toJson(datos);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes("utf-8"));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }
            System.out.println("Respuesta PATCH: " + response);
        }
    }

    public void actualizarCamposUsuario(String uid, Map<String, Object> nuevosDatos, String idToken) throws IOException {
        // 1. Leer lo que ya hay
        String jsonActual = leerNodo("usuarios/" + uid, idToken);

        Map<String, Object> base = new java.util.HashMap<>();

        if (jsonActual != null && !jsonActual.equals("null")) {
            // Gson lo parsea a un Map genérico
            base = gson.fromJson(jsonActual, Map.class);
            if (base == null) {
                base = new java.util.HashMap<>();
            }
        }

        // 2. Fusionar: lo nuevo pisa a lo viejo
        base.putAll(nuevosDatos);

        // 3. Guardar todo de nuevo con PUT
        guardarUsuario(uid, base, idToken);
    }

    public void guardarSala(String codigo, Map<String, Object> datosSala, String idToken) throws IOException {
        String url = FirebaseConfig.DATABASE_URL + "salas/" + codigo + ".json?auth=" + idToken;
        String jsonBody = gson.toJson(datosSala);
        putJson(url, jsonBody);   // reutilizamos el mismo método que para usuarios
    }

    public void borrarNodo(String ruta, String idToken) throws IOException {
        String urlString = FirebaseConfig.DATABASE_URL + ruta + ".json?auth=" + idToken;
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }
            System.out.println("DELETE respuesta: " + response);
        }
    }

}
