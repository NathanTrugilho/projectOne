import server.ApplicationServer;
import loadbalancer.LoadBalancer;
import client.Client;

import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        System.out.println("=== INICIANDO SISTEMA DISTRIBUÍDO ===");

        // 1. Iniciar os 3 Servidores de Aplicação (9001, 9002, 9003)
        // Definimos as portas e vizinhos manualmente aqui
        int[] portas = {9001, 9002, 9003};

        for (int portaAtual : portas) {
            // Calcula vizinhos
            List<Integer> vizinhos = new ArrayList<>();
            for (int p : portas) {
                if (p != portaAtual) vizinhos.add(p);
            }

            // Inicia cada servidor em uma Thread separada
            new Thread(() -> {
                ApplicationServer server = new ApplicationServer(portaAtual, vizinhos);
                server.start(); // Este método roda o loop infinito do servidor
            }).start();
        }

        // Pequena pausa para garantir que os servidores subiram
        dormir(1000);

        // 2. Iniciar o Load Balancer
        new Thread(new LoadBalancer()).start();

        // Pausa para garantir que o LB subiu
        dormir(1000);

        // 3. Iniciar o Cliente
        // O cliente começará a bombardear o sistema com requisições
        new Thread(new Client()).start();
    }

    // Método auxiliar para pausas
    private static void dormir(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}