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
    
    // Método main para iniciar um servidor específico via argumentos
    // Exemplo de execução: java server.ApplicationServer 9001
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java server.ApplicationServer <porta>");
            return;
        }
        int porta = Integer.parseInt(args[0]);
        
        // Define quem são os vizinhos baseados nas portas fixas do projeto
        List<Integer> vizinhos = new ArrayList<>();
        int[] todasPortas = {9001, 9002, 9003};
        
        for (int p : todasPortas) {
            if (p != porta) vizinhos.add(p);
        }

        new ApplicationServer(porta, vizinhos).start();
    }
}