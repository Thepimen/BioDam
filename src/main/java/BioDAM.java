import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * BioDAM - Sistema de Ingesta, Auditoría y Procesamiento Clínico Seguro.
 * <p>
 * Implementación académica para el módulo de Acceso a Datos (DAM 2) basada en Java 21 estándar.
 * Integra validación de rutas segura (NIO.2) frente a Path Traversal, auditoría con logs nativos
 * dinámicos y procesamiento analítico de datos clínicos mediante Streams y Lambdas.
 * </p>
 *
 * @author BioDAM Team
 * @version 1.0.0
 */
public class BioDAM {

    /** Formateador para el nombre dinámico del archivo de auditoría. */
    private static final DateTimeFormatter FORMATO_NOMBRE_LOG = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Formateador estricto para cada línea de log: [yyyy-MM-dd HH:mm:ss.SSS] [NIVEL] mensaje */
    private static final DateTimeFormatter FORMATO_LINEA_LOG = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** Ruta del archivo de log dinámico activo durante la sesión. */
    private static Path rutaArchivoLog;

    /**
     * Modelo inmutable que representa un registro clínico de diagnóstico.
     *
     * @param idPaciente  Identificador único del paciente.
     * @param edad        Edad del paciente en años.
     * @param diagnostico Condición médica diagnosticada.
     * @param estado      Estado del caso clínico (ej. ACTIVO, INACTIVO).
     */
    public record RegistroClinico(String idPaciente, int edad, String diagnostico, String estado) {
        /**
         * Parsea una línea en formato CSV con separador ';' a un objeto {@link RegistroClinico}.
         *
         * @param linea Línea del archivo CSV.
         * @return Instancia poblada con los datos clínicos.
         */
        public static RegistroClinico desdeCsv(String linea) {
            String[] tokens = linea.split(";");
            return new RegistroClinico(
                    tokens[0].trim(),
                    Integer.parseInt(tokens[1].trim()),
                    tokens[2].trim(),
                    tokens[3].trim()
            );
        }
    }

    /**
     * Punto de entrada principal de la aplicación.
     *
     * @param args Argumentos de línea de comandos (no requeridos).
     */
    public static void main(String[] args) {
        try {
            // =========================================================================
            // BLOQUE 1: INICIALIZACIÓN DE LOGS DINÁMICOS
            // =========================================================================
            inicializarSistemaLogs();
            registrarLog("INFO", "Iniciando sistema de procesamiento clínico BioDAM.");

            // =========================================================================
            // BLOQUE 2: VALIDACIÓN DE SEGURIDAD Y VERIFICACIÓN DE RUTA
            // =========================================================================
            Path rutaEntrada = Path.of("datos", "diagnosticos_2026.csv");
            Path archivoValidado = validarRutaSegura(rutaEntrada);

            if (archivoValidado == null) {
                registrarLog("WARN", "Finalizando ejecución de manera limpia debido a la ausencia del archivo de entrada.");
                return;
            }

            // =========================================================================
            // BLOQUE 3: PROCESAMIENTO CLÍNICO CON STREAMS Y LAMBDAS
            // =========================================================================
            procesarDatosClinicos(archivoValidado);

            // =========================================================================
            // BLOQUE 4: CIERRE Y TRASLADO DEL ARCHIVO PROCESADO
            // =========================================================================
            moverArchivoProcesado(archivoValidado);

            // =========================================================================
            // BLOQUE 5: SIMULACIÓN DE PRUEBA DE MITIGACIÓN DE PATH TRAVERSAL
            // =========================================================================
            System.out.println("-------------------------------------------------------------------");
            System.out.println(" DEMOSTRACIÓN DE SEGURIDAD: Detección controlada de Path Traversal ");
            System.out.println("-------------------------------------------------------------------");
            simularPruebaPathTraversal();

            registrarLog("INFO", "Ejecución completa del sistema BioDAM finalizada con éxito.");

        } catch (Exception e) {
            registrarLog("ERROR", "Error inesperado durante la ejecución: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================================
    // BLOQUE 1: MÉTODOS DE REGISTRO Y GESTIÓN DE LOGS
    // =========================================================================

    /**
     * Asegura la creación del directorio 'salida/sistema_logs/' y genera el archivo
     * de auditoría con nombre dinámico estricto: 'salida/sistema_logs/auditoria_yyyyMMddHHmmss.log'.
     *
     * @throws IOException Si ocurre un problema de E/S al crear directorios o archivos.
     */
    public static void inicializarSistemaLogs() throws IOException {
        Path directorioLogs = Path.of("salida", "sistema_logs");
        Files.createDirectories(directorioLogs);

        String nombreArchivo = "auditoria_" + LocalDateTime.now().format(FORMATO_NOMBRE_LOG) + ".log";
        rutaArchivoLog = directorioLogs.resolve(nombreArchivo);
    }

    /**
     * Escribe una entrada en el archivo de log activo con el formato exacto requerido:
     * {@code [yyyy-MM-dd HH:mm:ss.SSS] [NIVEL] mensaje}
     *
     * @param nivel   Nivel de severidad del log (INFO, WARN, ERROR).
     * @param mensaje Detalle o descripción del evento a registrar.
     */
    public static void registrarLog(String nivel, String mensaje) {
        String timestamp = LocalDateTime.now().format(FORMATO_LINEA_LOG);
        String linea = String.format("[%s] [%s] %s%n", timestamp, nivel, mensaje);

        try {
            if (rutaArchivoLog != null) {
                Files.writeString(
                        rutaArchivoLog,
                        linea,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            }
        } catch (IOException e) {
            System.err.println("Fallo al escribir en el log nativo: " + e.getMessage());
        }

        // Notificación en consola para trazabilidad en tiempo real
        System.out.print(linea);
    }

    // =========================================================================
    // BLOQUE 2: SEGURIDAD Y VALIDACIÓN DE RUTAS (NIO.2)
    // =========================================================================

    /**
     * Mitiga ataques de Path Traversal normalizando la ruta y validando que pertenezca
     * estrictamente al directorio base permitido 'datos/'.
     * Verifica adicionalmente la existencia del archivo.
     *
     * @param rutaArchivo Ruta solicitada para procesar.
     * @return {@link Path} absoluto y seguro si el archivo existe; {@code null} si no existe.
     * @throws SecurityException Si se detecta un intento de escape de directorio (Path Traversal).
     */
    public static Path validarRutaSegura(Path rutaArchivo) {
        Path basePermitida = Path.of("datos").toAbsolutePath().normalize();
        Path candidata = rutaArchivo.toAbsolutePath().normalize();

        // Verificación estricta de Path Traversal
        if (!candidata.startsWith(basePermitida)) {
            registrarLog("WARN", "Intento de Path Traversal detectado con la ruta: " + rutaArchivo);
            throw new SecurityException("Acceso Denegado: La ruta no pertenece al directorio autorizado ('datos/').");
        }

        // Verificación de existencia física del fichero
        if (!Files.exists(candidata)) {
            registrarLog("ERROR", "El archivo clínico solicitado no existe en la ruta: " + candidata);
            return null;
        }

        registrarLog("INFO", "Ruta validada correctamente contra Path Traversal: " + candidata);
        return candidata;
    }

    // =========================================================================
    // BLOQUE 3: PROCESAMIENTO CLÍNICO CON STREAMS Y LAMBDAS
    // =========================================================================

    /**
     * Procesa el archivo CSV clínico utilizando exclusivamente Streams y Lambdas
     * dentro de un bloque try-with-resources sobre {@link Files#lines(Path)}.
     * <p>
     * Calcula:
     * a) Total de registros físicos procesados (omitiendo cabecera).
     * b) Lista ordenada alfabéticamente sin duplicados de diagnósticos con estado ACTIVO.
     * c) Edad media (double) de los pacientes con diagnóstico "Diabetes".
     * </p>
     *
     * @param rutaArchivo Ruta segura al archivo CSV.
     */
    public static void procesarDatosClinicos(Path rutaArchivo) {
        registrarLog("INFO", "Iniciando procesamiento de registros clínicos mediante Streams...");

        try (Stream<String> lineas = Files.lines(rutaArchivo, StandardCharsets.UTF_8)) {
            // Conversión de líneas CSV a objetos RegistroClinico excluyendo cabecera y vacíos
            List<RegistroClinico> registros = lineas
                    .skip(1)
                    .filter(linea -> !linea.isBlank())
                    .map(RegistroClinico::desdeCsv)
                    .toList();

            // a) Total de registros físicos procesados
            long totalRegistros = registros.size();

            // b) Lista ordenada alfabéticamente y sin duplicados de diagnósticos únicos en pacientes con ESTADO igual a ACTIVO
            List<String> diagnosticosActivos = registros.stream()
                    .filter(r -> "ACTIVO".equalsIgnoreCase(r.estado()))
                    .map(RegistroClinico::diagnostico)
                    .distinct()
                    .sorted()
                    .toList();

            // c) Edad media (double) de los pacientes diagnosticados con "Diabetes"
            double edadMediaDiabetes = registros.stream()
                    .filter(r -> "Diabetes".equalsIgnoreCase(r.diagnostico()))
                    .mapToDouble(RegistroClinico::edad)
                    .average()
                    .orElse(0.0);

            // Presentación limpia y clara de resultados por consola
            System.out.println();
            System.out.println("================================================================");
            System.out.println("            INFORME CLÍNICO ANALÍTICO - BioDAM                 ");
            System.out.println("================================================================");
            System.out.println(" a) Total de registros clínicos procesados: " + totalRegistros);
            System.out.println(" b) Diagnósticos únicos con pacientes en estado ACTIVO (orden A-Z):");
            diagnosticosActivos.forEach(diag -> System.out.println("    - " + diag));
            System.out.printf(Locale.US, " c) Edad media de pacientes con Diabetes: %.2f años%n", edadMediaDiabetes);
            System.out.println("================================================================");
            System.out.println();

            registrarLog("INFO", String.format(Locale.US,
                    "Análisis completado: %d registros procesados, %d diagnósticos activos únicos, edad media Diabetes = %.2f",
                    totalRegistros, diagnosticosActivos.size(), edadMediaDiabetes));

        } catch (IOException e) {
            registrarLog("ERROR", "Error de E/S leyendo archivo clínico: " + e.getMessage());
        }
    }

    // =========================================================================
    // BLOQUE 4: CIERRE Y GESTIÓN DE ARCHIVOS PROCESADOS
    // =========================================================================

    /**
     * Registra el cierre de la importación y traslada el archivo procesado a la
     * carpeta 'salida/procesados/' mediante {@link Files#move(Path, Path, java.nio.file.CopyOption...)}.
     *
     * @param archivoOrigen Ruta del archivo procesado.
     * @throws IOException Si ocurre un error al crear directorios o mover el archivo.
     */
    public static void moverArchivoProcesado(Path archivoOrigen) throws IOException {
        registrarLog("INFO", "Finalización correcta de la importación clínica.");

        Path directorioDestino = Path.of("salida", "procesados");
        Files.createDirectories(directorioDestino);

        Path archivoDestino = directorioDestino.resolve(archivoOrigen.getFileName());
        Files.move(archivoOrigen, archivoDestino, StandardCopyOption.REPLACE_EXISTING);

        registrarLog("INFO", "Archivo movido con éxito a: " + archivoDestino);
    }

    // =========================================================================
    // BLOQUE 5: SIMULACIÓN DE PRUEBA DE SEGURIDAD
    // =========================================================================

    /**
     * Prueba controlada para demostrar la mitigación de Path Traversal.
     * Intenta resolver una ruta maliciosa con '../', registrando el log WARN
     * y capturando la {@link SecurityException} correspondiente.
     */
    public static void simularPruebaPathTraversal() {
        Path rutaMaliciosa = Path.of("datos", "..", "passwords.txt");
        try {
            registrarLog("INFO", "Lanzando prueba de validación de seguridad con ruta maliciosa: " + rutaMaliciosa);
            validarRutaSegura(rutaMaliciosa);
            System.err.println("[FALLO] La ruta no fue bloqueada.");
        } catch (SecurityException se) {
            System.out.println("[OK - SEGURIDAD ACTIVA] SecurityException capturada satisfactoriamente: " + se.getMessage());
        }
    }
}
