/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package partidaUTIL;

public class Carta {

    public enum Palo {
        CORONAS, CORAZONES, BALANZAS, DIANAS
    }

    private Palo palo;
    private int numero;
    private String rutaImagen;
    // public static final String RUTA_DORSO = "/ui/graphicResources/cartas/parteTrasera.png";

    public Carta(Palo palo, int numero, String rutaImagen) {
        this.palo = palo;
        this.numero = numero;
        this.rutaImagen = rutaImagen;
    }

    public Palo getPalo() {
        return palo;
    }

    public int getNumero() {
        return numero;
    }

    public String getRutaImagen() {
        return rutaImagen;
    }

    @Override
    public String toString() {
        return palo + " " + numero;
    }
}
