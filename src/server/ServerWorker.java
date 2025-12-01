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

    private int calcularMDC(int a, int b) {
        while (b != 0) {
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    @Override
    public void run() { // Thread finalizada ao término do processamento
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String request = in.readLine();
            if (request == null) return;

            String[] parts = request.split(";");
            String tipo = parts[0];

            if (tipo.equals("LEITURA")) {
                // Leitura processada de imediato, sem dormida
                int linhas = fileManager.contarLinhas();
                // Imprimir na própria tela do servidor a contagem
                System.out.println("INFO: Meu arquivo possui " + linhas + " linhas.");

            } else if (tipo.equals("ESCRITA")) {
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);

                // Thread dorme 100-200ms antes de processar
                try {
                    Thread.sleep(new Random().nextInt(101) + 100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                int mdc = calcularMDC(x, y);
                // Formato obrigatório da frase no arquivo
                String resultado = "O MDC entre " + x + " e " + y + " é " + mdc;

                // Servidor escreve apenas em seu arquivo local
                fileManager.escreverLinha(resultado);
                System.out.println("Escrita Local: " + resultado);

                // Mecanismo para garantir consistência entre arquivos
                replicarParaVizinhos(x, y);

            } else if (tipo.equals("REPLICACAO")) {
                // Auxilia na manutenção da consistência dos dados
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
}