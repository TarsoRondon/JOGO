package com.mycompany.jogo;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * CasinoSingleWindow.java
 *
 * Versão única (tudo em um arquivo) conforme pedido.
 * Ajuste DB_URL / DB_USER / DB_PASS para sua configuração MySQL.
 *
 * Tabelas (criadas automaticamente se não existirem):
 * - usuarios (id, usuario, nome, senha, credits)
 * - codigos_promocionais (id, codigo, valor, usado)
 * - historico (id, usuario, jogo, valor_aposta, valor_ganho, data_hora)
 *
 * Observações:
 * - O HistoricoViewer usa Supplier<String> para pegar o usuário logado dinamicamente.
 * - Inclui botão para excluir o histórico do usuário logado.
 */
public class CasinoSingleWindow extends JFrame {

    // ---------- CONFIGURAÇÃO DO BANCO ----------
    private static final String DB_URL = "jdbc:mysql://localhost:3306/login_db?serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "993549889";
    // -------------------------------------------

    private final Connection conn;

    private CardLayout cardLayout;
    private JPanel rootPanel;

    private String usuarioAtual = null;
    private String nomeAtual = null;
    private int credits = 0;

    private JLabel topCreditLabel;
    private final HistoricoManager historico;

    public CasinoSingleWindow(Connection conn) {
        super("Cassino de Luxo 🐯");
        this.conn = conn;
        this.historico = new HistoricoManager(conn);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        initUI();
    }

    private void initUI() {
        cardLayout = new CardLayout();
        rootPanel = new JPanel(cardLayout);

        rootPanel.add(createLoginPanel(), "login");
        rootPanel.add(createMainPanel(), "main");

        add(rootPanel);
        cardLayout.show(rootPanel, "login");
    }

    // ------------------- LOGIN / CADASTRO -------------------
    private JPanel createLoginPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(8, 18, 40));

        JLabel title = new JLabel("Cassino do Tigrinho - Login", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(new Color(190, 220, 255));
        title.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        p.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(new Color(10, 20, 50));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel userLabel = new JLabel("Usuário:");
        userLabel.setForeground(Color.WHITE);
        gbc.gridx = 0;
        gbc.gridy = 0;
        form.add(userLabel, gbc);

        JTextField userField = new JTextField(18);
        gbc.gridx = 1;
        form.add(userField, gbc);

        JLabel passLabel = new JLabel("Senha:");
        passLabel.setForeground(Color.WHITE);
        gbc.gridx = 0;
        gbc.gridy = 1;
        form.add(passLabel, gbc);

        JPasswordField passField = new JPasswordField(18);
        gbc.gridx = 1;
        form.add(passField, gbc);

        JLabel msg = new JLabel(" ");
        msg.setForeground(Color.LIGHT_GRAY);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        form.add(msg, gbc);

        JButton btnLogin = new JButton("Entrar");
        btnLogin.setBackground(new Color(40, 120, 255));
        btnLogin.setForeground(Color.WHITE);

        JButton btnRegister = new JButton("Cadastrar");
        btnRegister.setBackground(new Color(70, 160, 255));
        btnRegister.setForeground(Color.WHITE);

        JPanel buttons = new JPanel(new FlowLayout());
        buttons.setBackground(new Color(10, 20, 50));
        buttons.add(btnLogin);
        buttons.add(btnRegister);
        gbc.gridy = 3;
        form.add(buttons, gbc);

        p.add(form, BorderLayout.CENTER);

        btnLogin.addActionListener(e -> {
            String usuario = userField.getText().trim();
            String senha = new String(passField.getPassword());
            if (usuario.isEmpty() || senha.isEmpty()) {
                msg.setText("Preencha usuário e senha.");
                return;
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT nome, senha, credits FROM usuarios WHERE usuario = ?")) {
                ps.setString(1, usuario);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String senhaDB = rs.getString("senha");
                    if (!senhaDB.equals(senha)) {
                        msg.setText("Senha incorreta.");
                    } else {
                        nomeAtual = rs.getString("nome");
                        credits = rs.getInt("credits");
                        usuarioAtual = usuario;
                        topCreditLabel.setText("Créditos: " + credits);
                        msg.setText("Login bem-sucedido.");
                        loadAfterLogin();
                        cardLayout.show(rootPanel, "main");
                    }
                } else {
                    msg.setText("Usuário não encontrado.");
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
                msg.setText("Erro DB: " + ex.getMessage());
            }
        });

        btnRegister.addActionListener(e -> {
            CadastroDialog dlg = new CadastroDialog(this, conn);
            dlg.setVisible(true);
        });

        return p;
    }

    private void loadAfterLogin() {
        topCreditLabel.setText("Créditos: " + credits);
        // Se quiser atualizar outras coisas ao logar, faça aqui.
    }

    private class CadastroDialog extends JDialog {
        public CadastroDialog(JFrame owner, Connection conn) {
            super(owner, "Cadastro de Usuário", true);
            setSize(420, 320);
            setLocationRelativeTo(owner);
            JPanel f = new JPanel(new GridBagLayout());
            f.setBackground(new Color(8, 20, 50));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(8, 8, 8, 8);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JTextField nameField = new JTextField(15);
            JTextField userField = new JTextField(15);
            JPasswordField passField = new JPasswordField(15);
            JLabel lblMsg = new JLabel(" ");
            lblMsg.setForeground(Color.WHITE);

            JLabel lblNome = new JLabel("Nome:");
            lblNome.setForeground(Color.WHITE);
            JLabel lblUsuario = new JLabel("Usuário:");
            lblUsuario.setForeground(Color.WHITE);
            JLabel lblSenha = new JLabel("Senha:");
            lblSenha.setForeground(Color.WHITE);

            gbc.gridx = 0;
            gbc.gridy = 0;
            f.add(lblNome, gbc);
            gbc.gridx = 1;
            f.add(nameField, gbc);
            gbc.gridx = 0;
            gbc.gridy = 1;
            f.add(lblUsuario, gbc);
            gbc.gridx = 1;
            f.add(userField, gbc);
            gbc.gridx = 0;
            gbc.gridy = 2;
            f.add(lblSenha, gbc);
            gbc.gridx = 1;
            f.add(passField, gbc);
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.gridwidth = 2;
            f.add(lblMsg, gbc);

            JButton create = new JButton("Criar Conta");
            JButton cancel = new JButton("Cancelar");
            JPanel bot = new JPanel();
            bot.setBackground(new Color(8, 20, 50));
            bot.add(create);
            bot.add(cancel);

            create.addActionListener(e -> {
                String nome = nameField.getText().trim();
                String usuario = userField.getText().trim();
                String senha = new String(passField.getPassword());
                if (nome.isEmpty() || usuario.isEmpty() || senha.isEmpty()) {
                    lblMsg.setText("Preencha todos os campos.");
                    return;
                }
                try (PreparedStatement check = conn.prepareStatement("SELECT id FROM usuarios WHERE usuario = ?")) {
                    check.setString(1, usuario);
                    ResultSet rs = check.executeQuery();
                    if (rs.next()) {
                        lblMsg.setText("Usuário já existe.");
                        return;
                    }
                } catch (SQLException ex) {
                    lblMsg.setText("Erro DB: " + ex.getMessage());
                    return;
                }

                try (PreparedStatement ins = conn.prepareStatement("INSERT INTO usuarios (usuario,nome,senha,credits) VALUES (?,?,?,?)")) {
                    ins.setString(1, usuario);
                    ins.setString(2, nome);
                    ins.setString(3, senha);
                    ins.setInt(4, 1000);
                    ins.executeUpdate();
                    lblMsg.setText("Conta criada. Faça login.");
                } catch (SQLException ex) {
                    lblMsg.setText("Erro DB: " + ex.getMessage());
                }
            });

            cancel.addActionListener(e -> dispose());

            add(f, BorderLayout.CENTER);
            add(bot, BorderLayout.SOUTH);
        }
    }

    // ------------------- PAINEL PRINCIPAL -------------------
    private JPanel createMainPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(6, 14, 35));

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(new Color(10, 25, 50));
        JLabel title = new JLabel("  Cassino de Luxo", SwingConstants.LEFT);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(new Color(160, 210, 255));
        title.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 8));
        top.add(title, BorderLayout.WEST);

        topCreditLabel = new JLabel("Créditos: 0", SwingConstants.RIGHT);
        topCreditLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        topCreditLabel.setForeground(Color.WHITE);
        topCreditLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 12));
        top.add(topCreditLabel, BorderLayout.EAST);

        p.add(top, BorderLayout.NORTH);

        // abas
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(new Color(12, 24, 50));

        // supplier e consumer para compartilhar créditos
        IntConsumer ajustarCredits = delta -> {
            SwingUtilities.invokeLater(() -> {
                credits += delta;
                if (credits < 0) credits = 0;
                topCreditLabel.setText("Créditos: " + credits);
            });
            // persistir no DB
            if (usuarioAtual != null) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE usuarios SET credits = ? WHERE usuario = ?")) {
                    ps.setInt(1, credits);
                    ps.setString(2, usuarioAtual);
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        };
        Supplier<Integer> obterCredits = () -> credits;

        // placeholders
        tabs.addTab("🎰 Slots", new JPanel());
        tabs.addTab("🎡 Roleta", new JPanel());
        tabs.addTab("🐯 Tigre Dice", new JPanel());
        tabs.addTab("📜 Histórico", new JPanel());
        tabs.addTab("👤 Perfil", new JPanel());

        p.add(tabs, BorderLayout.CENTER);

        // carregar painéis quando logado
        new Timer(400, new ActionListener() {
            private boolean loaded = false;

            public void actionPerformed(ActionEvent e) {
                if (!loaded && usuarioAtual != null) {
                    tabs.setComponentAt(0, new SlotsPanel(conn, usuarioAtual, historico, ajustarCredits, obterCredits));
                    tabs.setComponentAt(1, new RoletaPanel(conn, usuarioAtual, historico, ajustarCredits, obterCredits));
                    tabs.setComponentAt(2, new TigreDicePanel(conn, usuarioAtual, historico, ajustarCredits, obterCredits));
                    tabs.setComponentAt(3, new HistoricoViewer(conn, () -> usuarioAtual));
                    tabs.setComponentAt(4, createProfilePanel(ajustarCredits, obterCredits));
                    loaded = true;
                    ((Timer) e.getSource()).stop();
                }
            }
        }).start();

        return p;
    }

    private JPanel createProfilePanel(IntConsumer ajustarCredits, Supplier<Integer> obterCredits) {
        JPanel p = new JPanel();
        p.setBackground(new Color(18, 24, 40));
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(30, 60, 30, 60));

        JLabel tit = new JLabel("Perfil do Jogador");
        tit.setForeground(Color.WHITE);
        tit.setFont(new Font("SansSerif", Font.BOLD, 20));
        tit.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(tit);
        p.add(Box.createVerticalStrut(16));

        JLabel nomeLabel = new JLabel("Nome: " + (nomeAtual == null ? "-" : nomeAtual));
        nomeLabel.setForeground(Color.WHITE);
        nomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(nomeLabel);
        p.add(Box.createVerticalStrut(8));

        JLabel userLabel = new JLabel("Usuário: " + (usuarioAtual == null ? "-" : usuarioAtual));
        userLabel.setForeground(Color.WHITE);
        userLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(userLabel);
        p.add(Box.createVerticalStrut(16));

        JLabel creditsLabelLocal = new JLabel("Créditos: " + obterCredits.get());
        creditsLabelLocal.setForeground(new Color(160, 220, 255));
        creditsLabelLocal.setFont(new Font("SansSerif", Font.BOLD, 16));
        creditsLabelLocal.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(creditsLabelLocal);
        p.add(Box.createVerticalStrut(12));

        JButton btnRefresh = new JButton("Atualizar");
        btnRefresh.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnRefresh.addActionListener(e -> creditsLabelLocal.setText("Créditos: " + obterCredits.get()));
        p.add(btnRefresh);
        p.add(Box.createVerticalStrut(20));

        JLabel promo = new JLabel("Resgatar código promocional:");
        promo.setForeground(Color.WHITE);
        promo.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(promo);
        p.add(Box.createVerticalStrut(6));

        JTextField codeField = new JTextField();
        codeField.setMaximumSize(new Dimension(300, 30));
        p.add(codeField);
        p.add(Box.createVerticalStrut(8));

        JButton btnRedeem = new JButton("Resgatar");
        btnRedeem.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(btnRedeem);

        JLabel res = new JLabel(" ");
        res.setForeground(Color.LIGHT_GRAY);
        res.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(Box.createVerticalStrut(12));
        p.add(res);

        btnRedeem.addActionListener(e -> {
            String code = codeField.getText().trim();
            if (code.isEmpty()) {
                res.setText("Digite um código.");
                return;
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT valor, usado FROM codigos_promocionais WHERE codigo = ?")) {
                ps.setString(1, code);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    res.setText("Código inválido.");
                } else {
                    boolean usado = rs.getBoolean("usado");
                    int valor = rs.getInt("valor");
                    if (usado) res.setText("Código já utilizado.");
                    else {
                        ajustarCredits.accept(valor);
                        res.setText("Resgatado! +" + valor + " créditos.");
                        try (PreparedStatement up = conn.prepareStatement("UPDATE codigos_promocionais SET usado = TRUE WHERE codigo = ?")) {
                            up.setString(1, code);
                            up.executeUpdate();
                        }
                    }
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
                res.setText("Erro ao resgatar.");
            }
        });

        return p;
    }

    // ------------------- HELPERS / UTILITIES -------------------
    public static class ImageLoader {
        public static ImageIcon load(String filename, int w, int h) {
            String[] tries = {
                    "imagens/" + filename,
                    "images/" + filename,
                    filename,
                    filename.toLowerCase()
            };
            for (String p : tries) {
                try {
                    File f = new File(p);
                    if (f.exists()) {
                        BufferedImage img = ImageIO.read(f);
                        Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                        return new ImageIcon(scaled);
                    }
                } catch (IOException ignored) {
                }
            }
            // placeholder
            BufferedImage ph = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = ph.createGraphics();
            g.setColor(new Color(20, 50, 110));
            g.fillRect(0, 0, w, h);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.drawString("imagem", 8, h / 2 - 6);
            g.drawString("ausente", 8, h / 2 + 12);
            g.dispose();
            return new ImageIcon(ph);
        }
    }

    public static class SoundPlayer {
        public static void play(String filename) {
            try {
                File f = new File("sons/" + filename);
                if (!f.exists()) return;
                AudioInputStream ais = AudioSystem.getAudioInputStream(f);
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                clip.start();
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------- HISTÓRICO MANAGER -------------------
    public class HistoricoManager {
        private final Connection conn;
        private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        public HistoricoManager(Connection conn) {
            this.conn = conn;
            createTablesIfNeeded();
        }

        private void createTablesIfNeeded() {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS usuarios (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "usuario VARCHAR(100) UNIQUE, " +
                        "nome VARCHAR(100), " +
                        "senha VARCHAR(100), " +
                        "credits INT DEFAULT 1000" +
                        ")");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS codigos_promocionais (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "codigo VARCHAR(50) UNIQUE NOT NULL, " +
                        "valor INT NOT NULL, " +
                        "usado BOOLEAN DEFAULT FALSE" +
                        ")");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS historico (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "usuario VARCHAR(100) NOT NULL, " +
                        "jogo VARCHAR(50) NOT NULL, " +
                        "valor_aposta DECIMAL(10,2) NOT NULL, " +
                        "valor_ganho DECIMAL(10,2) NOT NULL, " +
                        "data_hora DATETIME NOT NULL" +
                        ")");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        public void registrar(String usuario, String jogo, double aposta, double ganho) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO historico (usuario, jogo, valor_aposta, valor_ganho, data_hora) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, usuario);
                ps.setString(2, jogo);
                ps.setDouble(3, aposta);
                ps.setDouble(4, ganho);
                ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        public void excluirHistoricoDoUsuario(String usuario) throws SQLException {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM historico WHERE usuario = ?")) {
                ps.setString(1, usuario);
                ps.executeUpdate();
            }
        }
    }

    // ------------------------------ HISTÓRICO VIEWER ------------------------------
    public static class HistoricoViewer extends JPanel {
        private final Connection conn;
        private final Supplier<String> usuarioSupplier;
        private final DefaultTableModel model;
        private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        public HistoricoViewer(Connection conn, Supplier<String> usuarioSupplier) {
            this.conn = conn;
            this.usuarioSupplier = usuarioSupplier;

            setLayout(new BorderLayout());
            setBackground(new Color(8, 18, 40));

            JLabel lbl = new JLabel("📜 Histórico de Jogos", SwingConstants.CENTER);
            lbl.setForeground(Color.WHITE);
            lbl.setFont(new Font("SansSerif", Font.BOLD, 18));
            lbl.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            add(lbl, BorderLayout.NORTH);

            String[] cols = {"Data", "Jogo", "Aposta", "Ganho"};
            model = new DefaultTableModel(cols, 0);
            JTable table = new JTable(model);
            table.setBackground(new Color(20, 30, 60));
            table.setForeground(Color.WHITE);
            table.setEnabled(false);
            table.getTableHeader().setBackground(new Color(30, 40, 70));
            table.getTableHeader().setForeground(Color.WHITE);
            table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
            JScrollPane scroll = new JScrollPane(table);
            add(scroll, BorderLayout.CENTER);

            JButton refresh = new JButton("🔄 Atualizar Histórico");
            refresh.addActionListener(e -> carregarHistorico());

            JButton btnDelete = new JButton("🗑️ Apagar Histórico");
            btnDelete.addActionListener(e -> {
                String usuario = usuarioSupplier.get();
                if (usuario == null || usuario.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Nenhum usuário logado!", "Aviso", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                int resp = JOptionPane.showConfirmDialog(this, "Deseja apagar TODO o histórico do usuário '" + usuario + "'?", "Confirmar exclusão", JOptionPane.YES_NO_OPTION);
                if (resp == JOptionPane.YES_OPTION) {
                    try {
                        // excluir no DB
                        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM historico WHERE usuario = ?")) {
                            ps.setString(1, usuario);
                            ps.executeUpdate();
                        }
                        carregarHistorico();
                        JOptionPane.showMessageDialog(this, "Histórico apagado.");
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(this, "Erro ao apagar histórico:\n" + ex.getMessage(), "Erro de Banco", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });

            JPanel bottom = new JPanel();
            bottom.setBackground(new Color(8, 18, 40));
            bottom.add(refresh);
            bottom.add(btnDelete);
            add(bottom, BorderLayout.SOUTH);

            carregarHistorico();
        }

        private void carregarHistorico() {
            model.setRowCount(0);
            String usuario = usuarioSupplier.get();
            if (usuario == null || usuario.isEmpty()) {
                // Não exibir popup repetido ao carregar automaticamente; apenas limpa
                return;
            }

            String sql = "SELECT data_hora, jogo, valor_aposta, valor_ganho FROM historico WHERE usuario = ? ORDER BY data_hora DESC LIMIT 500";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, usuario);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("data_hora");
                    String dataFormatada = ts == null ? "-" : ts.toLocalDateTime().format(dateFormat);
                    Object aposta = rs.getObject("valor_aposta");
                    Object ganho = rs.getObject("valor_ganho");
                    model.addRow(new Object[]{
                            dataFormatada,
                            rs.getString("jogo"),
                            aposta,
                            ganho
                    });
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Erro ao carregar histórico:\n" + ex.getMessage(),
                        "Erro de Banco", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ------------------- SLOTS PANEL -------------------
    public static class SlotsPanel extends JPanel {
        private final Connection conn;
        private final String usuario;
        private final CasinoSingleWindow.HistoricoManager historico;
        private final IntConsumer ajustarCredits;
        private final Supplier<Integer> obterCredits;
        private ImageIcon[] icons;
        private final JLabel[] reels = new JLabel[3];
        private final JTextField betField = new JTextField("20", 6);
        private final JButton spinBtn = new JButton("GIRAR");
        private final JLabel status = new JLabel("Aposte e gire");
        private final Random rnd = new Random();

        public SlotsPanel(Connection conn, String usuario, CasinoSingleWindow.HistoricoManager historico,
                          IntConsumer ajustarCredits, Supplier<Integer> obterCredits) {
            this.conn = conn;
            this.usuario = usuario;
            this.historico = historico;
            this.ajustarCredits = ajustarCredits;
            this.obterCredits = obterCredits;

            setLayout(new BorderLayout(10, 10));
            setBackground(new Color(18, 24, 40));
            loadIcons();

            JLabel title = new JLabel("Slots do Tigrinho", SwingConstants.CENTER);
            title.setForeground(Color.WHITE);
            title.setFont(new Font("SansSerif", Font.BOLD, 20));
            add(title, BorderLayout.NORTH);

            JPanel center = new JPanel(new GridLayout(1, 3, 12, 12));
            center.setOpaque(false);
            for (int i = 0; i < 3; i++) {
                reels[i] = new JLabel(icons[rnd.nextInt(icons.length)], SwingConstants.CENTER);
                reels[i].setBorder(BorderFactory.createLineBorder(new Color(60, 90, 140), 4));
                reels[i].setOpaque(true);
                reels[i].setBackground(new Color(10, 15, 30));
                center.add(reels[i]);
            }
            add(center, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 12));
            bottom.setBackground(new Color(18, 24, 40));
            bottom.add(new JLabel("Aposta:"));
            betField.setHorizontalAlignment(JTextField.CENTER);
            bottom.add(betField);

            spinBtn.setPreferredSize(new Dimension(240, 64));
            spinBtn.addActionListener(e -> startSpin());
            bottom.add(spinBtn);
            bottom.add(status);
            add(bottom, BorderLayout.SOUTH);
        }

        private void loadIcons() {
            String[] files = {"7.png", "cereja.png", "diamante.png", "estrela.png", "limao.png", "tigre.png"};
            icons = new ImageIcon[files.length];
            for (int i = 0; i < files.length; i++) icons[i] = ImageLoader.load(files[i], 140, 140);
        }

        private void startSpin() {
            int bet;
            try {
                bet = Integer.parseInt(betField.getText().trim());
            } catch (Exception ex) {
                status.setText("Aposta inválida");
                return;
            }
            if (bet <= 0) {
                status.setText("Aposta > 0");
                return;
            }
            if (obterCredits.get() < bet) {
                status.setText("Créditos insuficientes");
                return;
            }

            // debita aposta
            ajustarCredits.accept(-bet);
            spinBtn.setEnabled(false);
            status.setText("Girando...");
            SoundPlayer.play("spin.wav");

            AtomicInteger remaining = new AtomicInteger(3);
            int[] finalIdx = new int[3];
            int[] durations = {700, 1100, 1500};

            for (int r = 0; r < 3; r++) {
                final int reel = r;
                Timer t = new Timer(80, null);
                long start = System.currentTimeMillis();
                t.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent ev) {
                        reels[reel].setIcon(icons[rnd.nextInt(icons.length)]);
                        if (System.currentTimeMillis() - start >= durations[reel]) {
                            int fi = rnd.nextInt(icons.length);
                            finalIdx[reel] = fi;
                            reels[reel].setIcon(icons[fi]);
                            ((Timer) ev.getSource()).stop();
                            if (remaining.decrementAndGet() == 0) {
                                SwingUtilities.invokeLater(() -> {
                                    computeResult(finalIdx, bet);
                                    spinBtn.setEnabled(true);
                                });
                            }
                        }
                    }
                });
                t.start();
            }
        }

        private void computeResult(int[] idx, int bet) {
            int mult = 0;
            if (idx[0] == idx[1] && idx[1] == idx[2]) mult = 10 + idx[0] * 2;
            else if (idx[0] == idx[1] || idx[1] == idx[2] || idx[0] == idx[2]) mult = 2;

            double ganho = 0;
            if (mult > 0) {
                ganho = bet * mult;
                ajustarCredits.accept((int) ganho);
                status.setText("🎉 Ganhou x" + mult + " (+" + (int) ganho + ")");
                SoundPlayer.play("win.wav");
            } else {
                ganho = 0;
                status.setText("Sem prêmio. Boa sorte!");
                SoundPlayer.play("lose.wav");
            }
            historico.registrar(usuario, "Slots", bet, ganho);
        }
    }

    // ------------------- ROLETA PANEL -------------------
    public static class RoletaPanel extends JPanel {
        private final Connection conn;
        private final String usuario;
        private final CasinoSingleWindow.HistoricoManager historico;
        private final IntConsumer ajustarCredits;
        private final Supplier<Integer> obterCredits;
        private final RoletaCanvas canvas = new RoletaCanvas();
        private final JTextField betField = new JTextField("50", 6);
        private final JButton spinBtn = new JButton("GIRAR");
        private final JLabel status = new JLabel("Coloque a aposta e gire");
        private final Random rnd = new Random();

        public RoletaPanel(Connection conn, String usuario, CasinoSingleWindow.HistoricoManager historico,
                           IntConsumer ajustarCredits, Supplier<Integer> obterCredits) {
            this.conn = conn;
            this.usuario = usuario;
            this.historico = historico;
            this.ajustarCredits = ajustarCredits;
            this.obterCredits = obterCredits;
            setLayout(new BorderLayout());
            setBackground(new Color(18, 24, 40));

            JLabel title = new JLabel("Roleta da Sorte", SwingConstants.CENTER);
            title.setForeground(Color.WHITE);
            title.setFont(new Font("SansSerif", Font.BOLD, 20));
            add(title, BorderLayout.NORTH);

            add(canvas, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 12));
            bottom.setBackground(new Color(18, 24, 40));
            bottom.add(new JLabel("Aposta:"));
            betField.setHorizontalAlignment(JTextField.CENTER);
            bottom.add(betField);

            spinBtn.setPreferredSize(new Dimension(160, 56));
            spinBtn.addActionListener(e -> doSpin());
            bottom.add(spinBtn);
            bottom.add(status);
            add(bottom, BorderLayout.SOUTH);
        }

        private void doSpin() {
            int bet;
            try {
                bet = Integer.parseInt(betField.getText().trim());
            } catch (Exception ex) {
                status.setText("Aposta inválida");
                return;
            }
            if (bet <= 0) {
                status.setText("Aposta > 0");
                return;
            }
            if (obterCredits.get() < bet) {
                status.setText("Créditos insuficientes");
                return;
            }

            ajustarCredits.accept(-bet);
            spinBtn.setEnabled(false);
            status.setText("Girando...");
            SoundPlayer.play("spin.wav");

            canvas.spin(mult -> SwingUtilities.invokeLater(() -> {
                double ganho = 0;
                if (mult > 0) {
                    ganho = bet * mult;
                    ajustarCredits.accept((int) ganho);
                    status.setText("🎉 Ganhou x" + mult + " (+" + (int) ganho + ")");
                    SoundPlayer.play("win.wav");
                } else {
                    ganho = 0;
                    status.setText("Sem prêmio.");
                    SoundPlayer.play("lose.wav");
                }
                historico.registrar(usuario, "Roleta", bet, ganho);
                spinBtn.setEnabled(true);
            }));
        }

        private static class RoletaCanvas extends JPanel {
            private final int[] multiplicadores = {0, 2, 3, 5, 10, 15, 20, 0, 2, 3, 5, 10};
            private double angle = 0;
            private double angularVel = 0;
            private Timer timer;
            private final Random rnd = new Random();
            private boolean spinning = false;

            public RoletaCanvas() {
                setPreferredSize(new Dimension(420, 420));
                setBackground(new Color(12, 18, 36));
            }

            public interface Callback {
                void onResult(int mult);
            }

            public void spin(Callback cb) {
                if (spinning) return;
                spinning = true;
                angularVel = 18 + rnd.nextDouble() * 12;
                double decel = 0.985 + rnd.nextDouble() * 0.006;

                timer = new Timer(16, null);
                timer.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        angle += angularVel;
                        angularVel *= decel;
                        repaint();
                        if (angularVel < 0.5) {
                            ((Timer) e.getSource()).stop();
                            spinning = false;
                            int sectors = multiplicadores.length;
                            double adjusted = (angle % 360 + 360) % 360;
                            double sectorAngle = 360.0 / sectors;
                            int idx = (int) ((360 - adjusted) / sectorAngle) % sectors;
                            int mult = multiplicadores[idx];
                            cb.onResult(mult);
                        }
                    }
                });
                timer.start();
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                int r = Math.min(w, h) / 2 - 20;
                int cx = w / 2, cy = h / 2;
                int sectors = multiplicadores.length;
                double sectorAngle = 360.0 / sectors;

                for (int i = 0; i < sectors; i++) {
                    g2.setColor((i % 2 == 0) ? new Color(40, 80, 160) : new Color(60, 120, 200));
                    g2.fill(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, (int) (angle + i * sectorAngle), (int) sectorAngle, Arc2D.PIE));
                }

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 14));
                for (int i = 0; i < sectors; i++) {
                    double mid = Math.toRadians(angle + i * sectorAngle + sectorAngle / 2.0);
                    int tx = (int) (cx + Math.cos(mid) * r * 0.62);
                    int ty = (int) (cy - Math.sin(mid) * r * 0.62);
                    String txt = "x" + multiplicadores[i];
                    int tw = g2.getFontMetrics().stringWidth(txt);
                    g2.drawString(txt, tx - tw / 2, ty + 5);
                }

                g2.setColor(new Color(10, 12, 20));
                g2.fillOval(cx - 30, cy - 30, 60, 60);

                Polygon p = new Polygon();
                p.addPoint(cx + r + 10, cy);
                p.addPoint(cx + r - 14, cy - 12);
                p.addPoint(cx + r - 14, cy + 12);
                g2.setColor(Color.YELLOW);
                g2.fill(p);

                g2.dispose();
            }
        }
    }

    // ------------------- TIGRE DICE PANEL -------------------
    public static class TigreDicePanel extends JPanel {
        private final Connection conn;
        private final String usuario;
        private final CasinoSingleWindow.HistoricoManager historico;
        private final IntConsumer ajustarCredits;
        private final Supplier<Integer> obterCredits;
        private final JTextField betField = new JTextField("50", 6);
        private final JButton playBtn = new JButton("Jogar Dados");
        private final JLabel resultLabel = new JLabel("Clique em Jogar para lançar os dados");
        private final Random rnd = new Random();

        public TigreDicePanel(Connection conn, String usuario, CasinoSingleWindow.HistoricoManager historico,
                              IntConsumer ajustarCredits, Supplier<Integer> obterCredits) {
            this.conn = conn;
            this.usuario = usuario;
            this.historico = historico;
            this.ajustarCredits = ajustarCredits;
            this.obterCredits = obterCredits;

            setLayout(new BorderLayout());
            setBackground(new Color(18, 24, 40));

            JLabel title = new JLabel("Tigre Dice", SwingConstants.CENTER);
            title.setFont(new Font("SansSerif", Font.BOLD, 20));
            title.setForeground(Color.WHITE);
            add(title, BorderLayout.NORTH);

            resultLabel.setHorizontalAlignment(SwingConstants.CENTER);
            resultLabel.setForeground(Color.LIGHT_GRAY);
            add(resultLabel, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 12));
            bottom.setBackground(new Color(18, 24, 40));
            bottom.add(new JLabel("Aposta:"));
            betField.setHorizontalAlignment(JTextField.CENTER);
            bottom.add(betField);

            playBtn.setPreferredSize(new Dimension(160, 48));
            playBtn.addActionListener(e -> jogar());
            bottom.add(playBtn);
            add(bottom, BorderLayout.SOUTH);
        }

        private void jogar() {
            int bet;
            try {
                bet = Integer.parseInt(betField.getText().trim());
            } catch (Exception ex) {
                resultLabel.setText("Aposta inválida");
                return;
            }
            if (bet <= 0) {
                resultLabel.setText("Aposta > 0");
                return;
            }
            if (obterCredits.get() < bet) {
                resultLabel.setText("Créditos insuficientes");
                return;
            }

            ajustarCredits.accept(-bet);
            playBtn.setEnabled(false);
            SoundPlayer.play("spin.wav");

            new Timer(300, new ActionListener() {
                int step = 0;

                @Override
                public void actionPerformed(ActionEvent e) {
                    step++;
                    if (step == 2) {
                        ((Timer) e.getSource()).stop();
                        int d1 = rnd.nextInt(6) + 1;
                        int d2 = rnd.nextInt(6) + 1;
                        int soma = d1 + d2;
                        double ganho = 0;
                        String msg;
                        if (d1 == d2) {
                            ganho = bet * 5;
                            msg = "Doubles! " + d1 + "+" + d2 + " → Ganhou " + (int) ganho;
                        } else if (soma == 7 || soma == 11) {
                            ganho = bet * 3;
                            msg = "Sorte! " + d1 + "+" + d2 + " = " + soma + " → Ganhou " + (int) ganho;
                        } else {
                            ganho = 0;
                            msg = "Resultado: " + d1 + "+" + d2 + " = " + soma + " → Sem prêmio";
                        }
                        if (ganho > 0) ajustarCredits.accept((int) ganho);
                        historico.registrar(usuario, "Tigre Dice", bet, ganho);
                        resultLabel.setText(msg);
                        if (ganho > 0) SoundPlayer.play("win.wav");
                        else SoundPlayer.play("lose.wav");
                        playBtn.setEnabled(true);
                    }
                }
            }).start();
        }
    }

    // ------------------- MAIN -------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                CasinoSingleWindow app = new CasinoSingleWindow(conn);
                app.setVisible(true);
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null, "Erro ao conectar ao banco: " + ex.getMessage());
            }
        });
    }
}
