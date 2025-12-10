package loadbalancer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

public class LoadBalancer implements Runnable {
    private static final int PORTA_LB = 8080;
    private static final int[] SERVIDORES = {9001, 9002, 9003};
    
    // Controle de Concorrência e Represamento
    private boolean sistemaEmEscrita = false;
    private final Queue<String> filaDeEspera = new LinkedList<>();
    private final Object lock = new Object(); // Objeto para sincronização

    @Override
    public void run() {
        System.out.println("[LoadBalancer] Iniciado na porta " + PORTA_LB);
        try (ServerSocket serverSocket = new ServerSocket(PORTA_LB)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            String request = in.readLine();
            if (request == null) return;

            // Lógica de Represamento (Restrição A)
            if (request.startsWith("ESCRITA")) {
                processarEscrita(request);
            } 
            else if (request.startsWith("LEITURA")) {
                // Leitura não espera consistência (Restrição A - parte final)
                broadcastLeitura(request);
            } 
            else if (request.equals("ACK_ESCRITA")) {
                // Mensagem interna do Servidor avisando que terminou a escrita
                liberarProximaEscrita();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processarEscrita(String request) {
        synchronized (lock) {
            if (sistemaEmEscrita) {
                // Sistema ocupado: Represa a requisição
                filaDeEspera.add(request);
                System.out.println("[LoadBalancer] Sistema em escrita. Requisição BUFFERIZADA. Fila: " + filaDeEspera.size());
            } else {
                // Sistema livre: Marca como ocupado e envia
                sistemaEmEscrita = true;
                enviarParaUmServidor(request);
            }
        }
    }

    private void liberarProximaEscrita() {
        synchronized (lock) {
            System.out.println("[LoadBalancer] Recebido ACK de conclusão de escrita.");
            
            if (!filaDeEspera.isEmpty()) {
                // Tem gente na fila: Retira e processa (mantém sistemaEmEscrita = true)
                String proximaReq = filaDeEspera.poll();
                System.out.println("[LoadBalancer] Processando requisição da FILA.");
                enviarParaUmServidor(proximaReq);
            } else {
                // Fila vazia: Libera o sistema
                sistemaEmEscrita = false;
                System.out.println("[LoadBalancer] Fila vazia. Sistema LIVRE para novas escritas.");
            }
        }
    }

    private void enviarParaUmServidor(String msg) {
        int index = new Random().nextInt(SERVIDORES.length);
        int portaAlvo = SERVIDORES[index];
        enviarMensagem(portaAlvo, msg);
        System.out.println("[LoadBalancer] Encaminhou ESCRITA para servidor " + portaAlvo);
    }

    private void broadcastLeitura(String msg) {
        System.out.println("[LoadBalancer] Broadcast de LEITURA.");
        for (int porta : SERVIDORES) {
            enviarMensagem(porta, msg);
        }
    }

    private void enviarMensagem(int porta, String msg) {
        try (Socket s = new Socket("localhost", porta);
             PrintWriter pOut = new PrintWriter(s.getOutputStream(), true)) {
            pOut.println(msg);
        } catch (IOException e) {
            System.err.println("[LoadBalancer] Erro ao conectar no servidor " + porta);
        }
    }
}