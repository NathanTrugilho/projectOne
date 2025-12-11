package server;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Random;

public class ServerWorker implements Runnable {
    private Socket socket;
    private FileManager fileManager;
    private List<Integer> portasVizinhos;
    private static final int PORTA_LB = 8080; // Para pedir o Lock e Confirmar

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
                int linhas = fileManager.contarLinhas();
                System.out.println("INFO: Meu arquivo possui " + linhas + " linhas.");

            } else if (tipo.equals("ESCRITA")) {
                // Formato recebido do LB: ESCRITA;ID;X;Y
                // Precisamos extrair o ID para confirmar depois
                String idReq = parts[1];
                int x = Integer.parseInt(parts[2]);
                int y = Integer.parseInt(parts[3]);

                // Fase de Processamento (Paralelo - Sem Lock)
                // Simula o tempo de cálculo/sleep conforme especificado
                try {
                    System.out.println("Processando MDC de " + x + " e " + y + " (Sleep)...");
                    Thread.sleep(new Random().nextInt(101) + 100); 
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                int mdc = calcularMDC(x, y);
                String resultado = "O MDC entre " + x + " e " + y + " é " + mdc;

                // Fase de Escrita (Crítica - Precisa de Lock)
                // Conecta ao Load Balancer e pede permissão
                try (Socket lockSocket = new Socket("localhost", PORTA_LB);
                     PrintWriter lockOut = new PrintWriter(lockSocket.getOutputStream(), true);
                     BufferedReader lockIn = new BufferedReader(new InputStreamReader(lockSocket.getInputStream()))) {
                    
                    System.out.println("Calculo finalizado. Solicitando LOCK ao LoadBalancer...");
                    lockOut.println("ACQUIRE_LOCK");
                    
                    // Fica TRAVADO aqui se o LB colocar na fila.
                    // Só sai daqui quando receber "GRANTED".
                    String response = lockIn.readLine(); 
                    
                    if (response != null && response.equals("GRANTED")) {
                        System.out.println("LOCK adquirido! Iniciando protocolo de escrita...");
                        
                        // Escrita Local
                        fileManager.escreverLinha(resultado);
                        System.out.println("Escrita Local: OK.");

                        // Replicação Síncrona (Garante consistência antes de liberar)
                        // Só avança se os vizinhos confirmarem
                        replicarParaVizinhosComConfirmacao(x, y);
                        
                        // Libera o Lock
                        lockOut.println("RELEASE_LOCK");
                        System.out.println("Processo concluído. LOCK liberado.");
                    }
                }
                // Após liberar o Lock e garantir que tudo foi feito, notifica o LoadBalancer
                // que esta requisição específica (ID) foi totalmente resolvida.
                enviarConfirmacaoAoLoadBalancer(idReq);

            } else if (tipo.equals("REPLICACAO")) {
                // Servidor recebendo ordem de vizinho para salvar
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int mdc = calcularMDC(x, y);
                String resultado = "O MDC entre " + x + " e " + y + " é " + mdc;

                fileManager.escreverLinha(resultado);
                System.out.println("Replicação recebida e gravada: " + resultado);
                
                // IMPORTANTE: Envia confirmação (ACK) de volta para quem mandou replicar
                // Isso permite que o servidor original saiba que pode liberar o lock
                out.println("ACK_REPLICACAO");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

private void replicarParaVizinhosComConfirmacao(int x, int y) {
        for (int porta : portasVizinhos) {
            boolean enviouComSucesso = false;
            
            // Só sai daqui quando o vizinho confirmar
            while (!enviouComSucesso) {
                try (Socket s = new Socket("localhost", porta);
                     PrintWriter pOut = new PrintWriter(s.getOutputStream(), true);
                     BufferedReader pIn = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                    
                    // Envia comando
                    pOut.println("REPLICACAO;" + x + ";" + y);
                    
                    // Espera confirmação (ACK)
                    String ack = pIn.readLine();
                    if (ack != null && ack.equals("ACK_REPLICACAO")) {
                        System.out.println("Confirmação recebida do vizinho " + porta);
                        enviouComSucesso = true; // Sucesso! Sai do while e vai para o próximo vizinho
                    }
                    
                } catch (IOException e) {
                    // Se der erro, NÃO avança. Fica tentando.
                    System.err.println("ERRO CRÍTICO: Vizinho " + porta + " está OFF. Sistema REPRESADO aguardando retorno...");
                    try {
                        Thread.sleep(2000); // Espera 2 segundos antes de tentar de novo
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
        System.out.println("Todos os vizinhos confirmaram a escrita.");
    }

    private void enviarConfirmacaoAoLoadBalancer(String id) {
        try (Socket s = new Socket("localhost", PORTA_LB);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            // Envia a confirmação para remover da fila de retransmissão
            out.println("CONFIRMACAO;" + id);
            System.out.println("Confirmação da requisição " + id + " enviada ao LoadBalancer.");
        } catch (IOException e) {
            System.err.println("Erro ao enviar confirmação ao LoadBalancer: " + e.getMessage());
        }
    }
}