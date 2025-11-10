import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Locale;

public class ClientApp extends JFrame {

    private final JTextField hostField = new JTextField("127.0.0.1");
    private final JTextField tcpPortField = new JTextField("9000");
    private final JTextField udpPortField = new JTextField("9001");
    private final JTextField countField = new JTextField("1000");
    private final JTextArea logArea = new JTextArea(14, 60);
    private final JButton tcpBtn = new JButton("Gửi TCP");
    private final JButton udpBtn = new JButton("Gửi UDP");

    private final ClientLogic logic = new ClientLogic();

    public ClientApp() {
        super("Client Demo - TCP/UDP Sender");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel top = new JPanel(new GridLayout(2, 1, 8, 8));

        JPanel row1 = new JPanel(new GridLayout(1, 6, 8, 8));
        row1.add(new JLabel("Host:"));
        row1.add(hostField);
        row1.add(new JLabel("TCP Port:"));
        row1.add(tcpPortField);
        row1.add(new JLabel("UDP Port:"));
        row1.add(udpPortField);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row2.add(new JLabel("Số lượng request:"));
        countField.setColumns(8);
        row2.add(countField);
        row2.add(tcpBtn);
        row2.add(udpBtn);

        top.add(row1);
        top.add(row2);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        tcpBtn.addActionListener(e -> sendTcp());
        udpBtn.addActionListener(e -> sendUdp());

        pack();
        setLocationRelativeTo(null);
    }

    private void lockUI(boolean lock) {
        tcpBtn.setEnabled(!lock);
        udpBtn.setEnabled(!lock);
        hostField.setEnabled(!lock);
        tcpPortField.setEnabled(!lock);
        udpPortField.setEnabled(!lock);
        countField.setEnabled(!lock);
    }

    private void appendLog(String s) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void sendTcp() {
        final String host = hostField.getText().trim();
        final int port = Integer.parseInt(tcpPortField.getText().trim());
        final int count = Integer.parseInt(countField.getText().trim());

        lockUI(true);
        logic.sendTcp(host, port, count, new ClientLogic.Listener() {
            @Override public void onLog(String line) { appendLog(line); }

            @Override
            public void onDoneTcp(int ok, int fail, List<ClientLogic.PodStats> summary) {
                appendLog(String.format("TCP DONE: ok=%d, fail=%d", ok, fail));
                SwingUtilities.invokeLater(() -> {
                    showSummaryDialog("TCP Summary (by Pod IP)", summary);
                    lockUI(false);
                });
            }
        });
    }

    private void sendUdp() {
        final String host = hostField.getText().trim();
        final int port = Integer.parseInt(udpPortField.getText().trim());
        final int count = Integer.parseInt(countField.getText().trim());

        lockUI(true);
        logic.sendUdp(host, port, count, new ClientLogic.Listener() {
            @Override public void onLog(String line) { appendLog(line); }

            @Override
            public void onDoneUdp(int ok, int fail, List<ClientLogic.PodStats> summary) {
                appendLog(String.format("UDP DONE: ok=%d, fail=%d", ok, fail));
                SwingUtilities.invokeLater(() -> {
                    showSummaryDialog("UDP Summary (by Pod IP)", summary);
                    lockUI(false);
                });
            }
        });
    }

    private void showSummaryDialog(String title, List<ClientLogic.PodStats> summary) {
        String[] cols = {"Pod IP", "Requests", "Avg (ms)", "Min (ms)", "Max (ms)"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (ClientLogic.PodStats s : summary) {
            model.addRow(new Object[]{
                    s.podIp,
                    s.count,
                    String.format(Locale.US, "%.3f", s.avgMs),
                    String.format(Locale.US, "%.3f", s.minMs),
                    String.format(Locale.US, "%.3f", s.maxMs)
            });
        }
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);

        JDialog dlg = new JDialog(this, title, true);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.getContentPane().add(new JScrollPane(table));
        dlg.setSize(640, 420);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientApp().setVisible(true));
    }
}
