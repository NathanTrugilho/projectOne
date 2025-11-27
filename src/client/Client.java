package client;

import java.io.*;
import java.net.Socket;
import java.util.Random;

public class Client implements Runnable { // Agora implementa Runnable
    private static final String HOST = "localhost";
    private static final int PORTA_LB = 8080;
    private static final String ARQUIVO_LOG = "cliente_log.txt";

    @Override
    public void run() {
        Random random = new Random();

        while (true) {
            try {
                // Envia requisições de Leitura ou Escrita
                try (Socket socket = new Socket(HOST, PORTA_LB);
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                    boolean isEscrita = random.nextBoolean();

                    if (isEscrita) {
                        // Sorteia dois números entre 2 e 1.000.000
                        int x = random.nextInt(999999) + 2;
                        int y = random.nextInt(999999) + 2;

                        String msg = "ESCRITA;" + x + ";" + y;
                        out.println(msg);

                        System.out.println("[Client] Enviou ESCRITA: " + x + ";" + y);
                        // Log local
                        registrarLog(x, y);

                    } else {
                        // Requisição de leitura
                        out.println("LEITURA");
                        System.out.println("[Client] Enviou LEITURA");
                    }
                }

                // Dorme entre 20 e 50ms
                int tempoDormida = random.nextInt(31) + 20;
                Thread.sleep(tempoDormida);

            } catch (Exception e) {
                System.err.println("[Client] Erro: " + e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ex) {}
            }
        }
    }

    private void registrarLog(int x, int y) {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(ARQUIVO_LOG, true)))) {
            out.println("Enviado par: " + x + ", " + y);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}