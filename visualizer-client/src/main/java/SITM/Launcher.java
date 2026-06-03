package SITM;

// Punto de entrada para el fat JAR del visualizador.
// JavaFX exige que la clase principal NO extienda Application cuando se lanza
// desde un fat JAR — de lo contrario arroja "JavaFX runtime components are missing".
// Este Launcher delega en Main sin extender Application, resolviendo el problema.
public class Launcher {
    public static void main(String[] args) {
        Main.main(args);
    }
}
