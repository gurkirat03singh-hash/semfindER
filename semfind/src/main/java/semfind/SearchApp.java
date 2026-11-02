package semfind;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Minimal Swing window: search box, ranked results, snippet preview. Double-click opens the file. */
public final class SearchApp extends JFrame {
    private static final int MAX_RESULTS = 10;

    private final Indexer indexer;
    private final Searcher searcher;
   
    private final JTextField query = new JTextField();
    private final DefaultListModel<Searcher.Result> model = new DefaultListModel<>();
    private final JList<Searcher.Result> list = new JList<>(model);
    private final JTextArea preview = new JTextArea();
    private final JLabel status = new JLabel(" Ready. Click \"Add folder\" to index your files.");
    private final GlassButton searchBtn = new GlassButton("Search");
    private final GlassButton addBtn = new GlassButton("Add folder...");
    private final GlassButton clearBtn = new GlassButton("Clear results");
    private final GlassButton themeBtn = new GlassButton("Dark mode");
    private final GlassButton appDetails = new GlassButton("SemFind v0.2");

    private static final Color LIGHT_BACKGROUND = new Color(0xF2EBDD);
    private static final Color LIGHT_SURFACE = new Color(0xFFFCF6);
    private static final Color LIGHT_FOREGROUND = new Color(0x302B25);
    private static final Color DARK_BACKGROUND = new Color(0x202124);
    private static final Color DARK_SURFACE = new Color(0x2B2D30);
    private static final Color DARK_FOREGROUND = new Color(0xE8EAED);
    private static final Color DARK_SELECTION = new Color(0x3D5A80);
    private static final Color LIGHT_SELECTION = new Color(0xE8DCC7);

    private boolean darkMode;

    public SearchApp(Indexer indexer, Searcher searcher) {
        super("NotefIND - meaning-based file search");
        this.indexer = indexer;
        this.searcher = searcher;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 600);
        setMinimumSize(new Dimension(680, 460));
        setLocationRelativeTo(null);

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(true);
        toolbar.setRollover(true);
        toolbar.setBorder(BorderFactory.createEmptyBorder(12, 16, 6, 16));
        toolbar.setLayout(new BoxLayout (toolbar, BoxLayout.X_AXIS));

        toolbar.add(searchBtn);
        toolbar.add(Box.createHorizontalStrut(8));
    
        toolbar.add(addBtn);
        toolbar.add(Box.createHorizontalStrut(8));

        toolbar.add(clearBtn);
        toolbar.add(Box.createHorizontalStrut(8));

        toolbar.add(themeBtn);

        toolbar.add(Box.createHorizontalGlue());

        toolbar.add(appDetails);


        JPanel searchBar = new JPanel(new BorderLayout(6, 6));
        searchBar.setBorder(BorderFactory.createEmptyBorder(6, 16, 14, 16));
        searchBar.add(query, BorderLayout.CENTER);
        query.setFont(new Font("Roboto", Font.PLAIN, 14));
        query.setPreferredSize(new Dimension(0, 42));

        JPanel top = new JPanel(new BorderLayout());
        top.add(toolbar, BorderLayout.NORTH);
        top.add(searchBar, BorderLayout.SOUTH);
        
        

        query.putClientProperty("JTextField.placeholderText", "Search by meaning or keywords...");
        list.setFont(new Font("Roboto", Font.PLAIN, 13));
        list.setFixedCellHeight(58);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(12);
        list.setCellRenderer(new Renderer());
        preview.setEditable(false);
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);
        preview.setMargin(new Insets(8, 8, 8, 8));
        preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        JScrollPane resultsPane = new JScrollPane(list);
        JScrollPane previewPane = new JScrollPane(preview);
        resultsPane.setBorder(BorderFactory.createEmptyBorder());
        previewPane.setBorder(BorderFactory.createEmptyBorder());

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, resultsPane, previewPane);
        split.setResizeWeight(0.7);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerSize(6);

        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        status.setFont(new Font("Roboto", Font.PLAIN, 12));
        status.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
        add(status, BorderLayout.SOUTH);

        searchBtn.setToolTipText("Search indexed files");
        addBtn.setToolTipText("Index a folder");
        clearBtn.setToolTipText("Clear the query and results");
        searchBtn.addActionListener(e -> doSearch());
        query.addActionListener(e -> doSearch());
        addBtn.addActionListener(e -> doIndex());
        themeBtn.addActionListener(e -> setDarkMode(!darkMode));
        clearBtn.addActionListener(e -> {
            query.setText("");
            model.clear();
            preview.setText("");
            status.setText(" Results cleared. Enter a query to search your indexed files.");
            query.requestFocusInWindow();
        });
        setDarkMode(true);

        appDetails.addActionListener(e -> {
            JDialog dialog = new JDialog(this, "App Details", true);
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.add(new JLabel("NotefindER-Finds your And your a..."));
            panel.add(new JLabel("Proto--02 version"));
            panel.add(new JLabel("Author:Nameless One"));
            dialog.add (panel);
            dialog.setContentPane(panel);
            dialog.pack();
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        });

        ListSelectionListener selectionListener = e -> {
            if (e.getValueIsAdjusting()) return;
            Searcher.Result r = list.getSelectedValue();
            preview.setText(r == null ? "" : r.snippet());
            preview.setCaretPosition(0);
        };
        list.addListSelectionListener(selectionListener);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Searcher.Result r = list.getSelectedValue();
                    open(r);
                }
            }
        });
    }

    private void doSearch() {
        String q = query.getText().trim();
        if (q.isEmpty()) {
            model.clear();
            preview.setText("");
            status.setText(" Enter a query to search your indexed files.");
            return;
        }

        setBusy(true);
        status.setText(" Searching for \"" + q + "\"...");

        new SwingWorker<List<Searcher.Result>, Void>() {
            @Override
            protected List<Searcher.Result> doInBackground() {
                return searcher.search(q, MAX_RESULTS);
            }

            @Override
            protected void done() {
                try {
                    List<Searcher.Result> res = get();
                    model.clear();
                    res.forEach(model::addElement);
                    if (res.isEmpty()) {
                        status.setText(" No matches found for \"" + q + "\". Try a broader or more descriptive query.");
                        preview.setText("");
                    } else {
                        status.setText(" " + res.size() + " result(s). Double-click to open a file.");
                        list.setSelectedIndex(0);
                    }
                } catch (Exception ex) {
                    status.setText(" Error: " + ex.getMessage());
                    preview.setText("");
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void doIndex() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        Path dir = fc.getSelectedFile().toPath();
        setBusy(true);
        status.setText(" Indexing \"" + dir + "\"...");

        new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return indexer.index(dir, this::publish);
            }

            @Override
            protected void process(List<String> msgs) {
                if (!msgs.isEmpty()) {
                    status.setText(" " + msgs.get(msgs.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    int n = get();
                    searcher.reload();
                    status.setText(" Done. " + n + " new/changed file(s) indexed.");
                } catch (Exception ex) {
                    status.setText(" Error: " + ex.getMessage());
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        searchBtn.setEnabled(!busy);
        addBtn.setEnabled(!busy);
        clearBtn.setEnabled(!busy);
        query.setEnabled(!busy);
    }

    private void setDarkMode(boolean enabled) {
        if (enabled) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        SwingUtilities.updateComponentTreeUI(this);

        darkMode = enabled;
        themeBtn.setToolTipText(enabled ? "let there be light" : "darkness shall consume the world");
        Color background = enabled ? DARK_BACKGROUND : LIGHT_BACKGROUND;
        Color surface = enabled ? DARK_SURFACE : LIGHT_SURFACE;
        Color foreground = enabled ? DARK_FOREGROUND : LIGHT_FOREGROUND;

        themeBtn.setText(enabled ? "Light mode" : "Dark mode");
        styleComponent(getContentPane(), background, surface, foreground);
        Color borderColor = enabled ? new Color(0x536273) : new Color(0xD6CCBC);
        query.setBorder(new RoundedBorder(borderColor));
        list.setSelectionBackground(enabled ? DARK_SELECTION : LIGHT_SELECTION);
        list.setSelectionForeground(enabled ? DARK_FOREGROUND : LIGHT_FOREGROUND);
        list.repaint();
    }

    private static void styleComponent(Component component, Color background, Color surface, Color foreground) {
        if (component instanceof JTextField || component instanceof JTextArea || component instanceof JList<?>) {
            component.setBackground(surface);
            component.setForeground(foreground);
            if (component instanceof JTextField field) field.setCaretColor(foreground);
            if (component instanceof JTextArea area) area.setCaretColor(foreground);
        } else if (!(component instanceof GlassButton)) {
            component.setBackground(background);
            component.setForeground(foreground);
        }

        if (component instanceof JComponent swingComponent) {
            swingComponent.setOpaque(!(component instanceof GlassButton));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                styleComponent(child, background, surface, foreground);
            }
        }
    }

    private void open(Searcher.Result r) {
        if (r == null) return;
        try {
            Desktop.getDesktop().open(new File(r.path()));
        } catch (Exception ex) {
            status.setText(" Could not open file: " + ex.getMessage());
        }
    }

    private static final class Renderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object value, int i, boolean sel, boolean focus) {
            super.getListCellRendererComponent(l, value, i, sel, focus);
            setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            if (!(value instanceof Searcher.Result r)) {
                return this;
            }

            File f = new File(r.path());
            String parent = f.getParent() == null ? "" : String.valueOf(f.getParent());
            setText("<html><b>" + esc(f.getName()) + "</b> &nbsp; p." + r.page()
                    + " &nbsp; similarity " + String.format(Locale.ROOT, "%.1f%%", r.score())
                    + "<br><small>" + esc(parent) + "</small></html>");
            return this;
        }

        private static String esc(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    private static final class RoundedBorder extends AbstractBorder {
        private final Color color;

        private RoundedBorder(Color color) {
            this.color = color;
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(8, 12, 8, 12);
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.drawRoundRect(x, y, width - 1, height - 1, 14, 14);
            g.dispose();
        }
    }
}
