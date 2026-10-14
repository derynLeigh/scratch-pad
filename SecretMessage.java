import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SecretMessage {

    record Cell(int x, int y, String character) {}

    private static final String DEFAULT_URL =
            "https://docs.google.com/document/d/e/2PACX-1vTMOmshQe8YvaRXi6gEPKKlsC6UpFJSMAk4mQjLm_u1gmHdVVTaeh7nBNFBRlui0sTZ-snGwZM4DBCT/pub";

    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

    private static final Pattern ROW =
            Pattern.compile("<tr[^>]*>(.*?)</tr>",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern CELL =
            Pattern.compile("<t[dh][^>]*>(.*?)</t[dh]>",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern TAG =
            Pattern.compile("<[^>]+>");

    private static final Pattern NUMERIC_ENTITY =
            Pattern.compile("&#(x?)([0-9a-fA-F]+);");

    // ---------------------------------------------------------------------
    // Entry point required by the task
    // ---------------------------------------------------------------------
    public static void printSecretMessage(String url) throws Exception {
        String html = fetch(url);
        List<Cell> cells = parseCells(html);

        PrintStream out =
                new PrintStream(System.out, true, StandardCharsets.UTF_8);

        out.println(render(cells));
    }

    // ---------------------------------------------------------------------
    // Step 1: Fetch
    // ---------------------------------------------------------------------
    static String fetch(String url) throws Exception {

        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url))
                        .GET()
                        .build();

        HttpResponse<String> response =
                HTTP_CLIENT.send(
                        request,
                        HttpResponse.BodyHandlers.ofString(
                                StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Failed to fetch document: HTTP "
                            + response.statusCode());
        }

        return response.body();
    }

    // ---------------------------------------------------------------------
    // Step 2: Parse
    // ---------------------------------------------------------------------
    static List<Cell> parseCells(String html) {

        List<List<String>> rows = new ArrayList<>();

        Matcher rowMatcher = ROW.matcher(html);

        while (rowMatcher.find()) {
            List<String> values = new ArrayList<>();

            Matcher cellMatcher =
                    CELL.matcher(rowMatcher.group(1));

            while (cellMatcher.find()) {
                values.add(cleanCellText(cellMatcher.group(1)));
            }

            if (!values.isEmpty()) {
                rows.add(values);
            }
        }

        if (rows.isEmpty()) {
            throw new IllegalStateException(
                    "No table found in document");
        }

        List<String> header = rows.get(0);

        int xCol = indexOfHeader(header, "x-coordinate");
        int yCol = indexOfHeader(header, "y-coordinate");
        int charCol = indexOfHeader(header, "character");

        int requiredColumn =
                Math.max(xCol, Math.max(yCol, charCol));

        List<Cell> cells = new ArrayList<>();
        Set<String> seenCoordinates = new HashSet<>();

        for (List<String> row : rows.subList(1, rows.size())) {

            if (row.size() <= requiredColumn) {
                throw new IllegalStateException(
                        "Malformed row: " + row);
            }

            try {
                int x = Integer.parseInt(row.get(xCol).trim());
                int y = Integer.parseInt(row.get(yCol).trim());

                if (x < 0 || y < 0) {
                    throw new IllegalStateException(
                            "Coordinates must be non-negative: "
                                    + row);
                }

                String coordinateKey = x + "," + y;

                if (!seenCoordinates.add(coordinateKey)) {
                    throw new IllegalStateException(
                            "Duplicate coordinate detected: ("
                                    + x + "," + y + ")");
                }

                cells.add(
                        new Cell(
                                x,
                                y,
                                row.get(charCol)));

            } catch (NumberFormatException e) {
                throw new IllegalStateException(
                        "Invalid coordinate in row: " + row,
                        e);
            }
        }

        return cells;
    }

    private static int indexOfHeader(
            List<String> header,
            String name) {

        for (int i = 0; i < header.size(); i++) {
            if (header.get(i)
                    .trim()
                    .equalsIgnoreCase(name)) {
                return i;
            }
        }

        throw new IllegalStateException(
                "Missing column '" + name
                        + "' in header " + header);
    }

    /**
     * Strips HTML tags, decodes entities,
     * and trims surrounding whitespace.
     */
    private static String cleanCellText(String cellHtml) {

        String text =
                TAG.matcher(cellHtml).replaceAll("");

        text = decodeEntities(text);

        return text.strip();
    }

    private static String decodeEntities(String text) {

        Matcher m = NUMERIC_ENTITY.matcher(text);

        StringBuilder sb = new StringBuilder();

        while (m.find()) {
            int codePoint =
                    Integer.parseInt(
                            m.group(2),
                            m.group(1).isEmpty() ? 10 : 16);

            m.appendReplacement(
                    sb,
                    Matcher.quoteReplacement(
                            new String(
                                    Character.toChars(codePoint))));
        }

        m.appendTail(sb);

        return sb.toString()
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }

    // ---------------------------------------------------------------------
    // Step 3: Render
    // ---------------------------------------------------------------------
    static String render(List<Cell> cells) {

        if (cells.isEmpty()) {
            return "";
        }

        int maxX =
                cells.stream()
                        .mapToInt(Cell::x)
                        .max()
                        .orElse(0);

        int maxY =
                cells.stream()
                        .mapToInt(Cell::y)
                        .max()
                        .orElse(0);

        String[][] grid =
                new String[maxY + 1][maxX + 1];

        for (String[] row : grid) {
            java.util.Arrays.fill(row, " ");
        }

        for (Cell cell : cells) {
            grid[cell.y()][cell.x()] =
                    cell.character();
        }

        StringBuilder out = new StringBuilder();

        for (int y = maxY; y >= 0; y--) {

            out.append(String.join("", grid[y]));

            if (y > 0) {
                out.append('\n');
            }
        }

        return out.toString();
    }

    public static void main(String[] args)
            throws Exception {

        String url =
                args.length > 0
                        ? args[0]
                        : DEFAULT_URL;

        printSecretMessage(url);
    }
}