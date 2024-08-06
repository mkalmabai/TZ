package kz.test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CrptApi {

    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final Queue<Instant> requestTimes;
    private final Lock lock = new ReentrantLock();

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("Request limit must be positive.");
        }
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.requestTimes = new LinkedList<>();
    }

    public void createDocument(Document document, String signature) throws InterruptedException, IOException {
        lock.lock();
        try {
            while (requestTimes.size() >= requestLimit) {
                Instant oldestRequestTime = requestTimes.peek();
                Duration timeElapsed = Duration.between(oldestRequestTime, Instant.now());
                if (timeElapsed.toMillis() < timeUnit.toMillis(1)) {
                    long sleepTime = timeUnit.toMillis(1) - timeElapsed.toMillis();
                    Thread.sleep(sleepTime);
                }
                requestTimes.poll();
            }
            requestTimes.add(Instant.now());
        } finally {
            lock.unlock();
        }
        sendPostRequest(document, signature);
    }

    private void sendPostRequest(Document document, String signature) throws IOException {
        URL url = new URL("https://ismp.crpt.ru/api/v3/lk/documents/create");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Signature", signature);
        connection.setDoOutput(true);

        ObjectMapper mapper = new ObjectMapper();
        String jsonString = mapper.writeValueAsString(document);

        try (var outputStream = connection.getOutputStream()) {
            byte[] input = jsonString.getBytes("utf-8");
            outputStream.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to create document: HTTP " + responseCode);
        }
    }

    public static class Document {
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public Product[] products;
        public String reg_date;
        public String reg_number;

        public static class Description {
            public String participantInn;
        }

        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;
        }
    }
}
