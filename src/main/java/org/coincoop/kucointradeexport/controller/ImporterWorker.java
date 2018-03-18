package org.coincoop.kucointradeexport.controller;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.JTextArea;
import javax.swing.SwingWorker;

import org.coincoop.kucointradeexport.controller.client.KuCoinClient;
import org.coincoop.kucointradeexport.controller.converter.Converter;
import org.coincoop.kucointradeexport.controller.exporter.Exporter;
import org.coincoop.kucointradeexport.controller.exporter.csv.ExporterCSV;
import org.coincoop.kucointradeexport.controller.format.Format;
import org.coincoop.kucointradeexport.controller.format.Record;
import org.coincoop.kucointradeexport.controller.format.exchange.CoinTracking;
import org.coincoop.kucointradeexport.controller.importer.Importer;
import org.coincoop.kucointradeexport.controller.importer.json.KuCoinImporterJSON;

public class ImporterWorker extends SwingWorker<Boolean, String> {

    private static void failIfInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Interrupted while importing");
        }
    }

    private final String apiKey;
    private final String secretKey;
    private final JTextArea messagesTextArea;
    private final File file;
    private final LocalDate before;
    private final LocalDate since;

    /**
     * Creates an instance of the worker.
     *
     * @param apiKey           API key
     * @param secretKey        secret key
     * @param messagesTextArea The text area where messages are written
     */
    public ImporterWorker(final String apiKey, final String secretKey, final JTextArea messagesTextArea,
                          final File file, final LocalDate before, final LocalDate since) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.messagesTextArea = messagesTextArea;
        this.file = file;
        this.before = before;
        this.since = since;
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        publish("Importing was started...");

        KuCoinClient kuCoinClient = new KuCoinClient(apiKey.trim(), secretKey.trim());

        List<String> symbols = kuCoinClient.getTradingSymbols();
        Importer importer = new KuCoinImporterJSON();

        List<Record> records = new ArrayList<>();

        Long beforeTimestamp = null;
        if (Objects.nonNull(before)) {
            beforeTimestamp = before.toEpochDay();
        }

        Long sinceTimestamp = null;
        if (Objects.nonNull(since)) {
            sinceTimestamp = since.toEpochDay();
        }

        for (String symbol : symbols) {
            publish("Importing trades for symbol " + symbol);
            List<Record> chunks = importer.importChunks(apiKey.trim(), secretKey.trim(), symbol, beforeTimestamp, sinceTimestamp);
            records.addAll(chunks);
            ImporterWorker.failIfInterrupted();
            publish("Imported trades: " + chunks.size());
        }
        publish(records.size() + " records was imported", "Starting conversion...");

        Converter converter = new Converter();
        records = converter.convert(Format.KU_COIN, Format.COIN_TRACKING, records);
        ImporterWorker.failIfInterrupted();

        ArrayList<String> headers = new ArrayList<>();
        headers.add(CoinTracking.TYPE.getCsvHeader());
        headers.add(CoinTracking.BUY_AMOUNT.getCsvHeader());
        headers.add(CoinTracking.BUY_CURRENCY.getCsvHeader());
        headers.add(CoinTracking.SELL_AMOUNT.getCsvHeader());
        headers.add(CoinTracking.SELL_CURRENCY.getCsvHeader());
        headers.add(CoinTracking.FEE.getCsvHeader());
        headers.add(CoinTracking.FEE_CURRENCY.getCsvHeader());
        headers.add(CoinTracking.EXCHANGE.getCsvHeader());
        headers.add(CoinTracking.GROUP.getCsvHeader());
        headers.add(CoinTracking.COMMENT.getCsvHeader());
        headers.add(CoinTracking.DATE.getCsvHeader());

        String filePath;

        if (Objects.nonNull(file)) {
            filePath = file.getAbsolutePath();
            if (filePath.endsWith("/") || filePath.endsWith("\\")) {
                filePath = filePath + "records.csv";
            } else if (!filePath.endsWith(".csv")) {
                filePath = filePath + ".csv";
            }
        } else {
            filePath = "records.csv";
        }

        Exporter exporter = new ExporterCSV();
        exporter.export(headers, records, filePath);

        ImporterWorker.failIfInterrupted();
        publish("Records were saved to path: " + filePath);
        return true;
    }

    @Override
    protected void process(final List<String> chunks) {
        for (final String string : chunks) {
            messagesTextArea.append(string);
            messagesTextArea.append("\n");
        }
    }
}
