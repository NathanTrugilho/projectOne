package loadbalancer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class LoadBalancer implements Runnable {
    private static final int PORTA_LB = 8080;
    private static final int[] SERVIDORES = {9001, 9002, 9003};

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(PORTA_LB)) {
            while (true) { // nao cria infinitas threads pq ele espera uma conexão com o .accept()
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

            if (request.startsWith("ESCRITA")) {
                // Distribui escrita aleatoriamente para 1 servidor
                int index = new Random().nextInt(SERVIDORES.length);
                int portaAlvo = SERVIDORES[index];
                enviarParaServidor(portaAlvo, request);

            } else if (request.startsWith("LEITURA")) {
                // Distribui leitura em broadcast para os 3 servidores
                for (int porta : SERVIDORES) {
                    enviarParaServidor(porta, request);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void enviarParaServidor(int porta, String msg) {
        try (Socket s = new Socket("localhost", porta);
             PrintWriter pOut = new PrintWriter(s.getOutputStream(), true)) {
            pOut.println(msg);
        } catch (IOException e) {
            System.err.println("Erro ao conectar no servidor " + porta + ". Ele está on?");
        }
    }
}