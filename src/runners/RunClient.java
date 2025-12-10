package runners;

import client.Client;

public class RunClient {
    public static void main(String[] args) {
        System.out.println("Iniciando Cliente...");
        new Thread(new Client()).start();
    }
}