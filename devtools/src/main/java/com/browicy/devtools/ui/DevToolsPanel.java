package com.browicy.devtools.ui;

import com.browicy.devtools.network.NetworkLog;
import com.browicy.devtools.network.NetworkRequestEntry;
import com.browicy.engine.net.PageLoad;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

public final class DevToolsPanel extends JPanel {

    private static final Color BACKGROUND = Color.WHITE;
    private static final Color SURFACE = new Color(0xF7F5FA);
    private static final Color BORDER = new Color(0xE0DEE5);
    private static final Color TEXT_PRIMARY = new Color(0x1C1B1F);
    private static final Color TEXT_SECONDARY = new Color(0x49454F);
    private static final Color STATE_LOADED = new Color(0x2E7D32);
    private static final Color STATE_FAILED = new Color(0xC42B1C);

    private static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    private static final Font TABLE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    private final NetworkLog log;
    private final NetworkTableModel model = new NetworkTableModel();

    public DevToolsPanel(NetworkLog log, Runnable closeAction) {
        super(new BorderLayout());
        this.log = log;
        setPreferredSize(new Dimension(800, 240));
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));

        add(header(closeAction), BorderLayout.NORTH);
        add(networkTable(), BorderLayout.CENTER);

        log.addListener(() -> SwingUtilities.invokeLater(this::refresh));
        refresh();
    }

    private void refresh() {
        model.setEntries(log.entries());
    }

    private JPanel header(Runnable closeAction) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(4, 12, 4, 4)));

        JLabel title = new JLabel("DevTools – Netzwerk");
        title.setFont(TITLE_FONT);
        title.setForeground(TEXT_PRIMARY);
        header.add(title, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(headerButton("Leeren", log::clear));
        actions.add(headerButton("Schließen", closeAction));
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private static JButton headerButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(TABLE_FONT);
        button.setFocusPainted(false);
        button.addActionListener(event -> action.run());
        return button;
    }

    private JScrollPane networkTable() {
        JTable table = new JTable(model) {
            @Override
            public String getToolTipText(MouseEvent event) {
                int row = rowAtPoint(event.getPoint());
                if (row < 0) {
                    return null;
                }
                NetworkRequestEntry entry = model.entryAt(convertRowIndexToModel(row));
                return entry.failureMessage() != null ? entry.failureMessage() : entry.displayUrl();
            }
        };
        table.setFont(TABLE_FONT);
        table.setForeground(TEXT_PRIMARY);
        table.setRowHeight(24);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(TABLE_FONT);

        TableColumnModel columns = table.getColumnModel();
        columns.getColumn(NetworkTableModel.COLUMN_ID).setMaxWidth(50);
        columns.getColumn(NetworkTableModel.COLUMN_URL).setPreferredWidth(400);
        columns.getColumn(NetworkTableModel.COLUMN_STATE).setPreferredWidth(110);
        columns.getColumn(NetworkTableModel.COLUMN_STATE).setCellRenderer(stateRenderer(table));
        columns.getColumn(NetworkTableModel.COLUMN_STATUS).setPreferredWidth(60);
        columns.getColumn(NetworkTableModel.COLUMN_REDIRECTS).setPreferredWidth(100);
        columns.getColumn(NetworkTableModel.COLUMN_SIZE).setPreferredWidth(100);
        columns.getColumn(NetworkTableModel.COLUMN_DURATION).setPreferredWidth(80);

        JScrollPane pane = new JScrollPane(table);
        pane.setBorder(null);
        pane.getViewport().setBackground(BACKGROUND);
        return pane;
    }

    private TableCellRenderer stateRenderer(JTable table) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable renderedTable, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component component = super.getTableCellRendererComponent(
                        renderedTable, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    NetworkRequestEntry entry = model.entryAt(table.convertRowIndexToModel(row));
                    component.setForeground(stateColor(entry.state()));
                }
                return component;
            }
        };
    }

    private static Color stateColor(PageLoad.State state) {
        return switch (state) {
            case LOADING, CANCELLED -> TEXT_SECONDARY;
            case LOADED -> STATE_LOADED;
            case FAILED -> STATE_FAILED;
        };
    }
}
