package ru.balladali.mashabot.core.clients.exchange;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.XMLConstants;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class CbrExchangeRateClient implements ExchangeRateClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter CBR_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    // кэш всей таблицы на дату (по Москве)
    private volatile LocalDate loadedForDate = null;
    private volatile Map<String, BigDecimal> ratesRubPerUnit = Collections.emptyMap();

    @Override
    public BigDecimal rateToRub(String iso) throws Exception {
        if (iso == null) return null;
        if ("RUB".equalsIgnoreCase(iso)) return BigDecimal.ONE;

        String code = iso.toUpperCase(Locale.ROOT);
        LocalDate todayMsk = LocalDate.now(MSK);

        // ленивая подгрузка таблицы на сегодня
        if (!todayMsk.equals(loadedForDate) || ratesRubPerUnit.isEmpty()) {
            synchronized (this) {
                if (!todayMsk.equals(loadedForDate) || ratesRubPerUnit.isEmpty()) {
                    loadDailyTable(todayMsk);
                }
            }
        }
        return ratesRubPerUnit.get(code); // может быть null, если валюты нет у ЦБ
    }

    private void loadDailyTable(LocalDate date) throws Exception {
        String query = "date_req=" + CBR_FMT.format(date);
        URI uri = new URI("https", "www.cbr.ru", "/scripts/XML_daily.asp", query, null);

        HttpRequest req = HttpRequest.newBuilder(uri).GET().build();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("CBR HTTP " + resp.statusCode());
        }

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        // безопасный парсинг XML
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new java.io.ByteArrayInputStream(resp.body()));
        doc.getDocumentElement().normalize();

        NodeList list = doc.getElementsByTagName("Valute");
        Map<String, BigDecimal> map = new HashMap<>();

        for (int i = 0; i < list.getLength(); i++) {
            Node v = list.item(i);
            String charCode = null;
            String nominalStr = null;
            String valueStr = null;

            Node child = v.getFirstChild();
            while (child != null) {
                String name = child.getNodeName();
                if ("CharCode".equalsIgnoreCase(name)) {
                    charCode = text(child);
                } else if ("Nominal".equalsIgnoreCase(name)) {
                    nominalStr = text(child);
                } else if ("Value".equalsIgnoreCase(name)) {
                    valueStr = text(child);
                }
                child = child.getNextSibling();
            }

            if (charCode == null || nominalStr == null || valueStr == null) continue;

            // value у ЦБ с запятой и может иметь пробелы: "9 512,34"
            BigDecimal valueRub = parseCbrDecimal(valueStr);      // RUB за Nominal единиц
            BigDecimal nominal   = parseCbrDecimal(nominalStr);   // обычно целое 1/10/100
            if (valueRub == null || nominal == null || nominal.signum() == 0) continue;

            BigDecimal perOne = valueRub.divide(nominal, 10, RoundingMode.HALF_UP); // RUB за 1
            map.put(charCode.toUpperCase(Locale.ROOT), perOne);
        }

        // запоминаем «снимок» на дату
        this.ratesRubPerUnit = Collections.unmodifiableMap(map);
        this.loadedForDate = date;
    }

    private static String text(Node n) {
        return n.getTextContent() != null ? n.getTextContent().trim() : null;
    }

    private static BigDecimal parseCbrDecimal(String s) {
        if (s == null) return null;
        // убираем пробелы-разделители тысяч, запятую меняем на точку
        String norm = s.replace("\u00A0", " ")
                .replace(" ", "")
                .replace(",", ".");
        try {
            return new BigDecimal(norm);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
