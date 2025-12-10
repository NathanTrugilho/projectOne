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
    
    // Controle de Mutex (Seção Crítica)
    private boolean lockOcupado = false;
    
    // FILA DE ESPERA (Garante que servidores sejam processados na ordem que pediram o lock)
    // Guardamos o PrintWriter para poder responder "GRANTED" para o servidor certo depois.
    private final Queue<PrintWriter> filaDeEsperaLock = new LinkedList<>();
    
    private final Object monitorLock = new Object(); // Objeto para sincronização das threads

    @Override
    public void run() {
        System.out.println("[LoadBalancer] Iniciado na porta " + PORTA_LB);
        try (ServerSocket serverSocket = new ServerSocket(PORTA_LB)) {
            while (true) {
                // Aceita conexão e delega para uma thread (não bloqueia novos clientes)
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket clientSocket) {
        try {
            // Nota: Não usamos 'try-with-resources' fechando o socket automaticamente aqui
            // porque se o servidor entrar na fila de espera, precisaremos manter esse socket aberto.
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            String request = in.readLine();
            if (request == null) {
                clientSocket.close();
                return;
            }

            if (request.startsWith("ESCRITA")) {
                // Roteia IMEDIATAMENTE para um servidor (Paralelismo de cálculo)
                // O cliente não espera fila aqui, a fila é só na hora de gravar.
                System.out.println("[LoadBalancer] Recebeu ESCRITA. Roteando para processamento paralelo.");
                enviarParaUmServidor(request);
                clientSocket.close();

            } else if (request.startsWith("LEITURA")) {
                broadcastLeitura(request);
                clientSocket.close();

            } else if (request.equals("ACQUIRE_LOCK")) {
                // O Servidor terminou o cálculo e quer escrever.
                // Se o lock estiver ocupado, ele entra na fila aqui dentro.
                tratarAquisicaoLock(out);
                
                // O socket fica aberto esperando o servidor terminar tudo e mandar RELEASE
                String nextCmd = in.readLine();
                if (nextCmd != null && nextCmd.equals("RELEASE_LOCK")) {
                    tratarLiberacaoLock();
                }
                clientSocket.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Lógica da Fila e do Lock
    private void tratarAquisicaoLock(PrintWriter serverOut) {
        synchronized (monitorLock) {
            if (lockOcupado) {
                // Se já tem alguém escrevendo, põe este servidor na FILA.
                // Ele vai ficar bloqueado no 'readLine()' dele esperando nossa resposta.
                System.out.println("[LoadBalancer] Lock OCUPADO. Servidor adicionado à FILA.");
                filaDeEsperaLock.add(serverOut);
            } else {
                // Se está livre, concede o lock imediatamente.
                lockOcupado = true;
                System.out.println("[LoadBalancer] Lock LIVRE. Concedido imediatamente.");
                serverOut.println("GRANTED"); 
            }
        }
    }

    private void tratarLiberacaoLock() {
        synchronized (monitorLock) {
            System.out.println("[LoadBalancer] Lock LIBERADO pelo servidor anterior.");
            
            if (!filaDeEsperaLock.isEmpty()) {
                // Tem gente na fila: Passa o bastão para o próximo.
                // O sistema continua ocupado (lockOcupado = true), mas agora é a vez do próximo.
                PrintWriter proximoServer = filaDeEsperaLock.poll();
                System.out.println("[LoadBalancer] Concedendo Lock para o próximo da fila. Restantes: " + filaDeEsperaLock.size());
                proximoServer.println("GRANTED");
            } else {
                // Fila vazia: O sistema fica livre.
                lockOcupado = false;
                System.out.println("[LoadBalancer] Fila vazia. Sistema totalmente LIVRE.");
            }
        }
    }

    private void enviarParaUmServidor(String msg) {
        int index = new Random().nextInt(SERVIDORES.length);
        int portaAlvo = SERVIDORES[index];
        enviarMensagem(portaAlvo, msg);
    }

    private void broadcastLeitura(String msg) {
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