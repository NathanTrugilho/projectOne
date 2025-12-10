package runners;

import server.ApplicationServer;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RunServer {
    public static void main(String[] args) {
        // Exemplo de execução: java RunServer 9001 9002 9003
        if (args.length < 2) {
            System.out.println("Uso: java RunServer <porta_local> <vizinho1> <vizinho2> ...");
            System.exit(1);
        }

        int portaLocal = Integer.parseInt(args[0]);
        
        // Coleta o restante dos argumentos como portas vizinhas
        List<Integer> vizinhos = Arrays.stream(args)
                .skip(1)
                .map(Integer::parseInt)
                .collect(Collectors.toList());

        ApplicationServer server = new ApplicationServer(portaLocal, vizinhos);
        server.start();
    }
}