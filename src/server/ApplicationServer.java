package server;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ApplicationServer {
    // Portas fixas para o exemplo (Servidores rodarão em 9001, 9002, 9003)
    private int minhaPorta;
    private List<Integer> portasVizinhos;
    private FileManager fileManager;

    public ApplicationServer(int porta, List<Integer> vizinhos) {
        this.minhaPorta = porta;
        this.portasVizinhos = vizinhos;
        // Cada servidor tem seu próprio arquivo local [cite: 4]
        this.fileManager = new FileManager("dados_server_" + porta + ".txt");
    }

    public void start() {
        System.out.println("Servidor iniciado na porta " + minhaPorta);
        try (ServerSocket serverSocket = new ServerSocket(minhaPorta)) {
            while (true) {
                // Aceita conexão e cria nova thread para tratar [cite: 16]
                Socket socket = serverSocket.accept();
                new Thread(new ServerWorker(socket, fileManager, portasVizinhos)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}