package server;

import java.io.*;

public class FileManager {
    private File file;

    public FileManager(String filename) {
        this.file = new File(filename);
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Método sincronizado para garantir exclusão mútua na escrita local [cite: 24, 25]
    public synchronized void escreverLinha(String texto) {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file, true)))) {
            out.println(texto);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized int contarLinhas() {
        int linhas = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while (reader.readLine() != null) linhas++;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return linhas;
    }
}