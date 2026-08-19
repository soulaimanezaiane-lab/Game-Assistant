import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class GameAssistantApp extends JFrame {

    // Legge la chiave dalle variabili d'ambiente in modo sicuro per GitHub
    private static final String API_KEY = System.getenv("GEMINI_API_KEY");
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=" + API_KEY;

    private static final Color BG_DARK = new Color(0x1E, 0x1E, 0x2E);
    private static final Color BG_CHAT = new Color(0x18, 0x18, 0x25);
    private static final Color BG_INPUT = new Color(0x31, 0x32, 0x44);
    private static final Color TEXT_MAIN = new Color(0xCD, 0xD6, 0xF4);
    private static final Color ACCENT_BLUE = new Color(0x89, 0xB4, 0xFA);
    private static final Color BORDER_COLOR = new Color(0x45, 0x47, 0x5A);

    private JEditorPane chatPane;
    private JTextField inputField;
    private JButton sendButton;
    private boolean isWaiting = false;
    private StringBuilder chatHistoryHtml = new StringBuilder();

    public GameAssistantApp() {
        setTitle("AI Gaming Assistant - Dark Edition");
        setSize(580, 680);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout(12, 12));

        chatPane = new JEditorPane();
        chatPane.setEditable(false);
        chatPane.setContentType("text/html");
        chatPane.setBackground(BG_CHAT);

        chatHistoryHtml.append("<html><head><style>")
                .append("body { font-family: 'Segoe UI', sans-serif; font-size: 13px; background-color: #181825; color: #cdd6f4; padding: 12px; margin: 0; } ")
                .append("p { margin-top: 4px; margin-bottom: 12px; line-height: 1.4; } ")
                .append(".user { color: #89b4fa; } ")
                .append(".ai { color: #a6e3a1; } ")
                .append("b { font-weight: bold; } ")
                .append("</style></head><body>")
                .append("<p class='ai'><b>AI:</b> Ciao Suly! Interfaccia aggiornata. Chiedimi quello che vuoi!</p>");

        updateChat();

        JScrollPane scrollPane = new JScrollPane(chatPane);
        scrollPane.setBorder(new LineBorder(BORDER_COLOR, 1, true));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(BG_DARK);
        chatPanel.setBorder(new EmptyBorder(12, 12, 0, 12));
        chatPanel.add(scrollPane, BorderLayout.CENTER);
        add(chatPanel, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(8, 8));
        inputPanel.setBackground(BG_DARK);
        inputPanel.setBorder(new EmptyBorder(0, 12, 12, 12));

        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.setBackground(BG_INPUT);
        inputField.setForeground(TEXT_MAIN);
        inputField.setCaretColor(TEXT_MAIN);
        inputField.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));

        sendButton = new JButton("Invia");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sendButton.setBackground(ACCENT_BLUE);
        sendButton.setForeground(new Color(0x11, 0x11, 0x1B));
        sendButton.setFocusPainted(false);
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendButton.setBorder(new EmptyBorder(8, 20, 8, 20));

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);

        ActionListener sendAction = e -> sendMessage();
        sendButton.addActionListener(sendAction);
        inputField.addActionListener(sendAction);
    }

    private void sendMessage() {
        if (isWaiting) return;

        String userText = inputField.getText().trim();
        if (userText.isEmpty()) return;

        chatHistoryHtml.append("<p><b class='user'>Tu:</b> ").append(escapeHtml(userText)).append("</p>");
        updateChat();

        inputField.setText("");
        setLoading(true);

        new Thread(() -> {
            String response = fetchAIResponse(userText);
            String formattedResponse = markdownToHtml(response);

            SwingUtilities.invokeLater(() -> {
                chatHistoryHtml.append("<p><b class='ai'>AI:</b> ").append(formattedResponse).append("</p>");
                updateChat();
                setLoading(false);
            });
        }).start();
    }

    private void updateChat() {
        chatPane.setText(chatHistoryHtml.toString() + "</body></html>");
        chatPane.setCaretPosition(chatPane.getDocument().getLength());
    }

    private void setLoading(boolean loading) {
        isWaiting = loading;
        sendButton.setEnabled(!loading);
        inputField.setEnabled(!loading);
        sendButton.setText(loading ? "..." : "Invia");
        sendButton.setBackground(loading ? BORDER_COLOR : ACCENT_BLUE);
    }

    private String fetchAIResponse(String userPrompt) {
        if (API_KEY == null || API_KEY.trim().isEmpty()) {
            return "[Errore]: API Key non configurata nelle variabili d'ambiente del sistema.";
        }

        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Connection", "keep-alive");
            
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(40000); // 40 secondi di attesa
            conn.setDoOutput(true);

            String cleanPrompt = userPrompt.replace("\\", "\\\\")
                                          .replace("\"", "\\\"")
                                          .replace("\n", "\\n")
                                          .replace("\r", "");

            String jsonPayload = "{"
                + "\"system_instruction\": {\"parts\": [{\"text\": \"Sei un assistente gaming ultra-veloce. Rispondi in modo sintetico e diretto.\"}]},"
                + "\"contents\": [{"
                + "  \"parts\": [{\"text\": \"" + cleanPrompt + "\"}]"
                + "}], "
                + "\"generationConfig\": {"
                + "  \"maxOutputTokens\": 300"
                + "}"
                + "}";

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            InputStream stream = (code == 200) ? conn.getInputStream() : conn.getErrorStream();

            try (Scanner s = new Scanner(stream, StandardCharsets.UTF_8.name())) {
                String response = s.useDelimiter("\\A").hasNext() ? s.next() : "";
                if (code == 200) {
                    return parseResponse(response);
                } else {
                    return "[Errore HTTP " + code + "]: " + response;
                }
            }

        } catch (Exception e) {
            return "[Errore di connessione]: " + e.getMessage();
        }
    }

    private String parseResponse(String jsonResponse) {
        try {
            int textKeyIndex = jsonResponse.indexOf("\"text\":");
            if (textKeyIndex != -1) {
                int startQuote = jsonResponse.indexOf("\"", textKeyIndex + 7);
                if (startQuote != -1) {
                    int start = startQuote + 1;
                    StringBuilder sb = new StringBuilder();
                    boolean escaped = false;

                    for (int i = start; i < jsonResponse.length(); i++) {
                        char c = jsonResponse.charAt(i);
                        if (escaped) {
                            if (c == 'n') sb.append('\n');
                            else if (c == 't') sb.append('\t');
                            else sb.append(c);
                            escaped = false;
                        } else if (c == '\\') {
                            escaped = true;
                        } else if (c == '"') {
                            break;
                        } else {
                            sb.append(c);
                        }
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            // Ignorato
        }
        return jsonResponse;
    }

    private String markdownToHtml(String text) {
        String html = escapeHtml(text);
        html = html.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1$</b>");
        html = html.replaceAll("\\*(.*?)\\*", "<i>$1</i>");
        html = html.replaceAll("\n\\* ", "<br>• ");
        html = html.replace("\n", "<br>");
        return html;
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new GameAssistantApp().setVisible(true);
        });
    }
}