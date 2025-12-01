package server;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ApplicationServer {
    private int minhaPorta;
    private List<Integer> portasVizinhos;
    private FileManager fileManager;

    public ApplicationServer(int porta, List<Integer> vizinhos) {
        this.minhaPorta = porta;
        this.portasVizinhos = vizinhos;
        // 1 arquivo de dados local vinculado a cada servidor
        this.fileManager = new FileManager("dados_server_" + porta + ".txt");
    }

    public void start() {
        System.out.println("Servidor iniciado na porta " + minhaPorta);
        try (ServerSocket serverSocket = new ServerSocket(minhaPorta)) {
            while (true) {
                // Instancia thread sob demanda para atender requisição
                Socket socket = serverSocket.accept();
                new Thread(new ServerWorker(socket, fileManager, portasVizinhos)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}