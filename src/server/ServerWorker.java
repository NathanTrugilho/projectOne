package server;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Random;

public class ServerWorker implements Runnable {
    private Socket socket;
    private FileManager fileManager;
    private List<Integer> portasVizinhos;
    private static final int PORTA_LB = 8080; // Para enviar o ACK

    public ServerWorker(Socket socket, FileManager fileManager, List<Integer> vizinhos) {
        this.socket = socket;
        this.fileManager = fileManager;
        this.portasVizinhos = vizinhos;
    }

    private int calcularMDC(int a, int b) {
        while (b != 0) {
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String request = in.readLine();
            if (request == null) return;

            String[] parts = request.split(";");
            String tipo = parts[0];

            if (tipo.equals("LEITURA")) {
                // Processamento imediato
                int linhas = fileManager.contarLinhas();
                System.out.println("INFO: Meu arquivo possui " + linhas + " linhas.");

            } else if (tipo.equals("ESCRITA")) {
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);

                // Dormir
                try {
                    Thread.sleep(new Random().nextInt(101) + 100); // 100 a 200ms
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Calcular e Escrever Local
                int mdc = calcularMDC(x, y);
                String resultado = "O MDC entre " + x + " e " + y + " é " + mdc;
                fileManager.escreverLinha(resultado);
                System.out.println("Escrita Local: " + resultado);

                // Replicar para garantir consistência
                replicarParaVizinhos(x, y);

                // Notificar LoadBalancer que terminou (Libera a fila)
                notificarLoadBalancerConclusao();

            } else if (tipo.equals("REPLICACAO")) {
                // Apenas escreve o que veio do vizinho (Não notifica LB, pois não é o líder)
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int mdc = calcularMDC(x, y);
                String resultado = "O MDC entre " + x + " e " + y + " é " + mdc;

                fileManager.escreverLinha(resultado);
                System.out.println("Replicação recebida: " + resultado);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void replicarParaVizinhos(int x, int y) {
        for (int porta : portasVizinhos) {
            try (Socket s = new Socket("localhost", porta);
                 PrintWriter pOut = new PrintWriter(s.getOutputStream(), true)) {
                pOut.println("REPLICACAO;" + x + ";" + y);
            } catch (IOException e) {
                System.err.println("Erro ao replicar para porta " + porta);
            }
        }
    }

    private void notificarLoadBalancerConclusao() {
        try (Socket s = new Socket("localhost", PORTA_LB);
             PrintWriter pOut = new PrintWriter(s.getOutputStream(), true)) {
            pOut.println("ACK_ESCRITA");
        } catch (IOException e) {
            System.err.println("Erro ao notificar LoadBalancer (ACK).");
        }
    }
}