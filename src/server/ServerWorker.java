package server;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Random;

public class ServerWorker implements Runnable {
    private Socket socket;
    private FileManager fileManager;
    private List<Integer> portasVizinhos;

    public ServerWorker(Socket socket, FileManager fileManager, List<Integer> vizinhos) {
        this.socket = socket;
        this.fileManager = fileManager;
        this.portasVizinhos = vizinhos;
    }

    // Função utilitária MDC
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
                // [cite: 20] Leitura processada de imediato, sem dormida
                int linhas = fileManager.contarLinhas();
                // [cite: 14] Imprimir na PRÓPRIA TELA do servidor
                System.out.println("INFO: Meu arquivo possui " + linhas + " linhas.");
                
            } else if (tipo.equals("ESCRITA")) {
                // Formato: ESCRITA;NUM1;NUM2
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);

                // [cite: 18] Thread deve dormir 100-200ms antes de processar
                try {
                    Thread.sleep(new Random().nextInt(101) + 100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                int mdc = calcularMDC(x, y);
                String resultado = "O MDC entre " + x + " e " + y + " é " + mdc;

                // Escreve no arquivo local
                fileManager.escreverLinha(resultado);
                System.out.println("Escrita Local: " + resultado);

                //  REPLICAÇÃO: Envia para os vizinhos para manter consistência
                replicarParaVizinhos(x, y);

            } else if (tipo.equals("REPLICACAO")) {
                // Recebido de outro servidor (não do LoadBalancer). Apenas escreve.
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int mdc = calcularMDC(x, y);
                String resultado = "O MDC entre " + x + " e " + y + " é " + mdc;
                
                fileManager.escreverLinha(resultado);
                System.out.println("Replicação recebida e gravada: " + resultado);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Método que conecta nos outros 2 servidores e manda gravar
    private void replicarParaVizinhos(int x, int y) {
        for (int porta : portasVizinhos) {
            try (Socket s = new Socket("localhost", porta);
                 PrintWriter pOut = new PrintWriter(s.getOutputStream(), true)) {
                // Envia comando especial de REPLICACAO para não causar loop infinito
                pOut.println("REPLICACAO;" + x + ";" + y);
            } catch (IOException e) {
                System.err.println("Erro ao replicar para porta " + porta);
            }
        }
    }
}