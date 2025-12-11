package loadbalancer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LoadBalancer implements Runnable {
    private static final int PORTA_LB = 8080;
    private static final int[] SERVIDORES = {9001, 9002, 9003};
    
    // Constante de Timeout em minutos para retransmissão no caso 100
    private static final long TIMEOUT_RETRANSMISSAO = 100 * 60 * 1000; 

    // Controle de Mutex (Seção Crítica)
    private boolean lockOcupado = false;
    
    // FILA DE ESPERA (Garante que servidores sejam processados na ordem que pediram o lock)
    // Guardamos o PrintWriter para poder responder "GRANTED" para o servidor certo depois.
    private final Queue<PrintWriter> filaDeEsperaLock = new LinkedList<>();
    
    private final Object monitorLock = new Object(); // Objeto para sincronização das threads

    // Controle de Retransmissão: Mapa de ID -> Dados da Requisição
    // ConcurrentHashMap para permitir acesso seguro entre a thread do cliente e a thread de retransmissão
    private final Map<String, RequestEntry> requisicoesPendentes = new ConcurrentHashMap<>();

    // Classe interna para armazenar dados da requisição pendente
    private static class RequestEntry {
        String mensagemCompleta; // Mensagem com ID
        long timestampInicio;

        public RequestEntry(String msg, long time) {
            this.mensagemCompleta = msg;
            this.timestampInicio = time;
        }
    }

    @Override
    public void run() {
        System.out.println("[LoadBalancer] Iniciado na porta " + PORTA_LB);

        // Inicia a Thread de Monitoramento de Retransmissão
        new Thread(this::monitorarRetransmissao).start();

        try (ServerSocket serverSocket = new ServerSocket(PORTA_LB)) {
            while (true) {
                // Aceita conexão e delega para uma thread (não bloqueia novos clientes)
                Socket clientSocket = serverSocket.accept();
                // O handleClient é instanciado em nova thread para cada cliente
                // Assim, múltiplos clientes podem ser atendidos simultaneamente
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Loop infinito que verifica timeouts
    private void monitorarRetransmissao() {
        System.out.println("[LoadBalancer] Monitor de retransmissão iniciado.");
        while (true) {
            try {
                Thread.sleep(1000); // Verifica a cada 1 segundo
                long agora = System.currentTimeMillis();

                // Itera sobre as requisições pendentes
                for (Map.Entry<String, RequestEntry> entry : requisicoesPendentes.entrySet()) {
                    String id = entry.getKey();
                    RequestEntry req = entry.getValue();

                    if ((agora - req.timestampInicio) > TIMEOUT_RETRANSMISSAO) {
                        System.out.println("[LoadBalancer] TIMEOUT na requisição " + id + ". Retransmitindo...");
                        
                        // Retransmite para um servidor aleatório
                        enviarParaUmServidor(req.mensagemCompleta);
                        
                        // Atualiza o timestamp para não retransmitir imediatamente de novo (espera mais 3 min)
                        req.timestampInicio = agora;
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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
                // Adicionar ID e Controle de Retransmissão
                // O formato recebido do cliente é ESCRITA;X;Y (ou similar)
                // Vamos gerar um ID único e transformar em ESCRITA;ID;X;Y
                
                String idReq = UUID.randomUUID().toString();
                
                // Reconstrói a mensagem inserindo o ID na segunda posição
                // Supõe-se entrada "ESCRITA;X;Y" -> Saída "ESCRITA;UUID;X;Y"
                String[] partes = request.split(";", 2); // Divide em "ESCRITA" e "X;Y"
                String novaMensagem = partes[0] + ";" + idReq + ";" + partes[1];

                // Salva na fila de pendentes
                requisicoesPendentes.put(idReq, new RequestEntry(novaMensagem, System.currentTimeMillis()));

                System.out.println("[LoadBalancer] Nova ESCRITA recebida. ID: " + idReq + ". Roteando...");
                
                // Roteia IMEDIATAMENTE para um servidor (Paralelismo de cálculo)
                // O cliente não espera fila aqui, a fila é só na hora de gravar.
                enviarParaUmServidor(novaMensagem);
                clientSocket.close();

            } else if (request.startsWith("CONFIRMACAO")) {
                // Protocolo: CONFIRMACAO;ID
                String[] parts = request.split(";");
                if (parts.length > 1) {
                    String idConfirmado = parts[1];
                    if (requisicoesPendentes.containsKey(idConfirmado)) {
                        requisicoesPendentes.remove(idConfirmado);
                        System.out.println("[LoadBalancer] Requisição " + idConfirmado + " resolvida e removida da fila.");
                    }
                }
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