package view;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

import model.*;
import service.*;
import dao.*;
import exception.CustomExceptions.*;

/**
 * BookingConsole.java — Swing GUI Desktop Application
 *
 * Graphical desktop interface for the multi-threaded ticket reservation desk.
 * Provides interactive visual seat maps, live thread monitor feeds,
 * dynamic pricing displays, and ticket management workflows.
 */
public class BookingConsole extends JFrame {

    // ═══════════════════════ CONSTANTS ═══════════════════════
    private static final int GRID_ROWS   = 6;
    private static final int GRID_COLS   = 5;
    private static final int TOTAL_SEATS = GRID_ROWS * GRID_COLS;  // 30

    // ── High-Contrast Deep Space Dark Theme Palette ──
    private static final Color CLR_BG          = new Color(15,  23,  42);   // Slate 900
    private static final Color CLR_CARD        = new Color(30,  41,  59);   // Slate 800 Card
    private static final Color CLR_CARD_ALT    = new Color(20,  29,  45);   // Darker Card
    private static final Color CLR_BORDER      = new Color(71,  85,  105);  // Slate 600 Border
    private static final Color CLR_HEADER      = new Color(11,  17,  32);   // Midnight Blue
    private static final Color CLR_TEXT        = new Color(248, 250, 252);  // Slate 50 (White)
    private static final Color CLR_MUTED       = new Color(148, 163, 184);  // Slate 400
    private static final Color CLR_ACCENT      = new Color(56,  189, 248);  // Sky Blue
    private static final Color CLR_ACCENT_ALT  = new Color(129, 140, 248);  // Indigo
    private static final Color CLR_AVAILABLE   = new Color(16,  185, 129);  // Emerald 500
    private static final Color CLR_LOCKED      = new Color(245, 158, 11);   // Amber 500
    private static final Color CLR_BOOKED      = new Color(239, 68,  68);   // Rose 500
    private static final Color CLR_SELECTED    = new Color(6,   182, 212);  // Cyan 500
    private static final Color CLR_GOLD        = new Color(251, 191, 36);   // Amber 400
    private static final Color CLR_TERMINAL_BG = new Color(8,   12,  20);   // Deep terminal black

    // ═══════════════════════ GUI COMPONENTS ═══════════════════════
    private JComboBox<Vehicle>   vehicleSelector;
    private SeatButton[]         seatButtons;
    private JTextField           passengerField;
    private JLabel               fareLabel;
    private JLabel               fareSubLabel;
    private JLabel               statusLabel;
    private JLabel               selectedSeatLabel;
    private JLabel               vehicleBadgeLabel;
    private JLabel               vehicleRouteLabel;
    private JLabel               vehicleDepLabel;
    private JLabel               vehicleBaseFareLabel;
    private OccupancyProgressBar occupancyBar;
    private JLabel               availCountLabel;
    private JLabel               lockedCountLabel;
    private JLabel               bookedCountLabel;
    private JLabel               waitlistCountLabel;
    private JTextArea            eventLogArea;

    // ═══════════════════════ APPLICATION STATE ═══════════════════════
    private final Map<String, ReservationEngine> engines;   // Cached engines per vehicle
    private ReservationEngine currentEngine;
    private Vehicle           currentVehicle;
    private int               selectedSeat = -1;            // Currently selected seat (0-indexed)
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss");

    // ═══════════════════════ SERVICES & DAO ═══════════════════════
    private final BookingDAO      bookingDAO;
    private final WaitlistManager waitlistManager;
    private final List<Vehicle>   vehicles;

    /* ══════════════════════════════════════════════════════
     * CONSTRUCTOR — Initialises DB, services, and builds GUI.
     * ══════════════════════════════════════════════════════ */
    public BookingConsole() {
        // ── Initialise services ──
        engines         = new HashMap<>();
        bookingDAO      = new BookingDAO();
        waitlistManager = new WaitlistManager();

        // ── Initialise database (Singleton) ──
        DBConnection.getInstance();
        waitlistManager.loadFromDatabase();

        // ── Load vehicles from DB ──
        vehicles = loadVehiclesFromDB();

        // ── Build GUI ──
        setupFrame();
        buildUI();

        // ── Select first vehicle ──
        if (!vehicles.isEmpty()) {
            vehicleSelector.setSelectedIndex(0);
        }

        // ── Start refresh timer (fires every 2 seconds on EDT) ──
        javax.swing.Timer refreshTimer = new javax.swing.Timer(2000, e -> refreshSeatGrid());
        refreshTimer.start();

        logEvent("SYSTEM", "Reservation Desk initialized. Ready for transactions.");
        setVisible(true);
    }

    /* ──────────────────────────────────────────────────────
     * Frame Setup
     * ─────────────────────────────────────────────────────── */
    private void setupFrame() {
        setTitle("Multi-Threaded Flight & Train Ticket Reservation Desk — CSE2006 | VIT Bhopal");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1220, 840);
        setMinimumSize(new Dimension(1050, 720));
        setLocationRelativeTo(null);  // Centre on screen
        getContentPane().setBackground(CLR_BG);

        // ── Shutdown hook to close DB ──
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                DBConnection.getInstance().close();
            }
        });
    }

    /* ──────────────────────────────────────────────────────
     * Main UI Builder
     * ─────────────────────────────────────────────────────── */
    private void buildUI() {
        setLayout(new BorderLayout(0, 0));
        buildMenuBar();
        add(buildHeaderPanel(),  BorderLayout.NORTH);

        // Main Center Content Split: Cabin Seat Map on Left, Control Deck on Right
        JPanel contentPanel = new JPanel(new BorderLayout(15, 0));
        contentPanel.setBackground(CLR_BG);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        contentPanel.add(buildCabinSeatMap(), BorderLayout.CENTER);
        contentPanel.add(buildControlDeck(),  BorderLayout.EAST);

        add(contentPanel, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    /* ══════════════════════════════════════════════════════
     * MENU BAR (Explicit Dark Custom Styling)
     * ══════════════════════════════════════════════════════ */
    private void buildMenuBar() {
        JMenuBar menuBar = new JMenuBar() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(CLR_HEADER);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        menuBar.setOpaque(true);
        menuBar.setBackground(CLR_HEADER);
        menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, CLR_BORDER));

        // ── File Menu ──
        JMenu fileMenu = createMenu("File");
        fileMenu.add(createMenuItem("Export Transaction Audit Report (.txt)...", e -> exportAuditReportDialog()));
        fileMenu.addSeparator();
        fileMenu.add(createMenuItem("Exit", e -> {
            DBConnection.getInstance().close();
            System.exit(0);
        }));
        menuBar.add(fileMenu);

        // ── Records Menu ──
        JMenu recordsMenu = createMenu("Records");
        recordsMenu.add(createMenuItem("View All Bookings Table (JTable)...", e -> showBookingHistoryDialog()));
        menuBar.add(recordsMenu);

        // ── Simulate Menu ──
        JMenu simMenu = createMenu("Simulate");
        simMenu.add(createMenuItem("Run 3 Concurrent Agents", e -> simulateAgents(3)));
        simMenu.add(createMenuItem("Run High-Concurrency Stress Test (6 Agents)", e -> simulateAgents(6)));
        menuBar.add(simMenu);

        // ── Tools Menu ──
        JMenu toolsMenu = createMenu("Tools");
        toolsMenu.add(createMenuItem("View Waitlist", e -> showWaitlistDialog()));
        toolsMenu.add(createMenuItem("Add to Waitlist", e -> showAddToWaitlistDialog()));
        toolsMenu.addSeparator();
        toolsMenu.add(createMenuItem("Refresh Seat Grid", e -> refreshSeatGrid()));
        menuBar.add(toolsMenu);

        setJMenuBar(menuBar);
    }

    private JMenu createMenu(String title) {
        JMenu menu = new JMenu(title);
        menu.setOpaque(true);
        menu.setBackground(CLR_HEADER);
        menu.setForeground(Color.WHITE);
        menu.setFont(new Font("Segoe UI", Font.BOLD, 12));
        menu.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        menu.getPopupMenu().setBackground(CLR_CARD);
        menu.getPopupMenu().setBorder(BorderFactory.createLineBorder(CLR_BORDER, 1));
        return menu;
    }

    private JMenuItem createMenuItem(String title, ActionListener action) {
        JMenuItem item = new JMenuItem(title);
        item.setOpaque(true);
        item.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        item.setBackground(CLR_CARD);
        item.setForeground(Color.WHITE);
        item.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        item.addActionListener(action);
        return item;
    }

    /* ══════════════════════════════════════════════════════
     * HEADER PANEL — High-Contrast Top Toolbar
     * ══════════════════════════════════════════════════════ */
    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setBackground(CLR_HEADER);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, CLR_BORDER),
                BorderFactory.createEmptyBorder(12, 24, 12, 24)
        ));

        // ── Left: Brand Title & Subtitle ──
        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setOpaque(false);

        JLabel brand = new JLabel("RESERVATION DESK");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 20));
        brand.setForeground(CLR_ACCENT);

        JLabel sub = new JLabel("High-Concurrency Multi-Threaded Ticketing Console");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(CLR_MUTED);

        titleBox.add(brand);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(sub);
        header.add(titleBox, BorderLayout.WEST);

        // ── Center: Vehicle Selector Dropdown (Dark Styled) ──
        JPanel centerBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        centerBox.setOpaque(false);

        JLabel selectLbl = new JLabel("Active Vehicle:");
        selectLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        selectLbl.setForeground(Color.WHITE);
        centerBox.add(selectLbl);

        vehicleSelector = new JComboBox<>();
        vehicleSelector.setFont(new Font("Segoe UI", Font.BOLD, 13));
        vehicleSelector.setPreferredSize(new Dimension(320, 36));
        vehicleSelector.setBackground(CLR_CARD);
        vehicleSelector.setForeground(Color.WHITE);
        vehicleSelector.setOpaque(true);

        // Custom renderer ensuring vehicle text is ALWAYS bright white on dark background
        vehicleSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                l.setOpaque(true);
                l.setFont(new Font("Segoe UI", Font.BOLD, 12));
                if (isSelected) {
                    l.setBackground(new Color(37, 99, 235));
                    l.setForeground(Color.WHITE);
                } else {
                    l.setBackground(CLR_CARD);
                    l.setForeground(Color.WHITE);
                }
                l.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
                return l;
            }
        });

        for (Vehicle v : vehicles) {
            vehicleSelector.addItem(v);
        }
        vehicleSelector.addActionListener(e -> onVehicleSelected());
        centerBox.add(vehicleSelector);

        header.add(centerBox, BorderLayout.CENTER);

        // ── Right: Quick Action Buttons (Modern Dark Pills) ──
        JPanel quickActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        quickActions.setOpaque(false);

        JButton tableBtn = new ModernPillButton("Records (JTable)", CLR_CARD, new Color(51, 65, 85), Color.WHITE);
        tableBtn.addActionListener(e -> showBookingHistoryDialog());

        JButton auditBtn = new ModernPillButton("Export Audit", CLR_CARD, new Color(51, 65, 85), Color.WHITE);
        auditBtn.addActionListener(e -> exportAuditReportDialog());

        JButton waitBtn  = new ModernPillButton("Waitlist", CLR_CARD, new Color(51, 65, 85), Color.WHITE);
        waitBtn.addActionListener(e -> showWaitlistDialog());

        quickActions.add(tableBtn);
        quickActions.add(auditBtn);
        quickActions.add(waitBtn);

        header.add(quickActions, BorderLayout.EAST);
        return header;
    }

    /* ══════════════════════════════════════════════════════
     * CABIN SEAT MAP — Realistic Transport Layout
     * ══════════════════════════════════════════════════════ */
    private JPanel buildCabinSeatMap() {
        RoundedPanel cabinCard = new RoundedPanel(16, CLR_CARD, CLR_BORDER);
        cabinCard.setLayout(new BorderLayout(0, 10));
        cabinCard.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        // ── Aircraft / Train Nose Indicator ──
        JPanel nosePanel = new JPanel(new BorderLayout());
        nosePanel.setOpaque(false);

        JLabel noseLabel = new JLabel("▲ FORWARD / VEHICLE COCKPIT DIRECTION ▲", SwingConstants.CENTER);
        noseLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        noseLabel.setForeground(CLR_ACCENT);
        noseLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(71, 85, 105)),
                BorderFactory.createEmptyBorder(0, 0, 8, 0)
        ));
        nosePanel.add(noseLabel, BorderLayout.CENTER);
        cabinCard.add(nosePanel, BorderLayout.NORTH);

        // ── Seat Grid Container ──
        JPanel seatingBody = new JPanel();
        seatingBody.setLayout(new BoxLayout(seatingBody, BoxLayout.Y_AXIS));
        seatingBody.setOpaque(false);

        seatButtons = new SeatButton[TOTAL_SEATS];

        // Column Letter Header (A, B ... AISLE ... C, D, E)
        JPanel colHeader = new JPanel(new BorderLayout(10, 0));
        colHeader.setOpaque(false);
        colHeader.setBorder(BorderFactory.createEmptyBorder(4, 30, 4, 10));

        JPanel leftLetters = new JPanel(new GridLayout(1, 2, 8, 0));
        leftLetters.setOpaque(false);
        leftLetters.setPreferredSize(new Dimension(140, 20));
        leftLetters.add(createColLetterLabel("A"));
        leftLetters.add(createColLetterLabel("B"));

        JLabel aisleHeader = new JLabel("AISLE", SwingConstants.CENTER);
        aisleHeader.setFont(new Font("Segoe UI", Font.BOLD, 10));
        aisleHeader.setForeground(CLR_MUTED);
        aisleHeader.setPreferredSize(new Dimension(50, 20));

        JPanel rightLetters = new JPanel(new GridLayout(1, 3, 8, 0));
        rightLetters.setOpaque(false);
        rightLetters.setPreferredSize(new Dimension(210, 20));
        rightLetters.add(createColLetterLabel("C"));
        rightLetters.add(createColLetterLabel("D"));
        rightLetters.add(createColLetterLabel("E"));

        colHeader.add(leftLetters, BorderLayout.WEST);
        colHeader.add(aisleHeader, BorderLayout.CENTER);
        colHeader.add(rightLetters, BorderLayout.EAST);
        seatingBody.add(colHeader);
        seatingBody.add(Box.createVerticalStrut(6));

        // Add 6 Rows with Class dividers
        for (int r = 0; r < GRID_ROWS; r++) {
            if (r == 0) {
                seatingBody.add(createClassBanner("★ FIRST / BUSINESS CLASS (Rows 1–2 | Dynamic Fare Multiplier: 1.5x)", CLR_GOLD));
                seatingBody.add(Box.createVerticalStrut(6));
            } else if (r == 2) {
                seatingBody.add(Box.createVerticalStrut(10));
                seatingBody.add(createClassBanner("● STANDARD / ECONOMY CLASS (Rows 3–6 | Base Pricing Rate)", CLR_ACCENT));
                seatingBody.add(Box.createVerticalStrut(6));
            }

            JPanel rowPanel = new JPanel(new BorderLayout(10, 0));
            rowPanel.setOpaque(false);

            JLabel rowLabel = new JLabel("R" + (r + 1));
            rowLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
            rowLabel.setForeground(CLR_MUTED);
            rowLabel.setPreferredSize(new Dimension(25, 52));

            // Left pair: Col 0 (A), Col 1 (B)
            JPanel leftPair = new JPanel(new GridLayout(1, 2, 8, 0));
            leftPair.setOpaque(false);
            leftPair.setPreferredSize(new Dimension(140, 52));

            int sIdx0 = r * GRID_COLS + 0;
            int sIdx1 = r * GRID_COLS + 1;
            seatButtons[sIdx0] = new SeatButton(sIdx0);
            seatButtons[sIdx1] = new SeatButton(sIdx1);
            leftPair.add(seatButtons[sIdx0]);
            leftPair.add(seatButtons[sIdx1]);

            // Center aisle
            JPanel aisleStrip = new JPanel();
            aisleStrip.setOpaque(false);
            aisleStrip.setPreferredSize(new Dimension(50, 52));
            JLabel aisleIcon = new JLabel("| |", SwingConstants.CENTER);
            aisleIcon.setFont(new Font("Segoe UI", Font.BOLD, 12));
            aisleIcon.setForeground(new Color(71, 85, 105));
            aisleStrip.add(aisleIcon);

            // Right trio: Col 2 (C), Col 3 (D), Col 4 (E)
            JPanel rightTrio = new JPanel(new GridLayout(1, 3, 8, 0));
            rightTrio.setOpaque(false);
            rightTrio.setPreferredSize(new Dimension(210, 52));

            int sIdx2 = r * GRID_COLS + 2;
            int sIdx3 = r * GRID_COLS + 3;
            int sIdx4 = r * GRID_COLS + 4;
            seatButtons[sIdx2] = new SeatButton(sIdx2);
            seatButtons[sIdx3] = new SeatButton(sIdx3);
            seatButtons[sIdx4] = new SeatButton(sIdx4);
            rightTrio.add(seatButtons[sIdx2]);
            rightTrio.add(seatButtons[sIdx3]);
            rightTrio.add(seatButtons[sIdx4]);

            rowPanel.add(rowLabel, BorderLayout.WEST);
            rowPanel.add(leftPair, BorderLayout.LINE_START);
            rowPanel.add(aisleStrip, BorderLayout.CENTER);
            rowPanel.add(rightTrio, BorderLayout.EAST);

            seatingBody.add(rowPanel);
            seatingBody.add(Box.createVerticalStrut(6));
        }

        cabinCard.add(new JScrollPane(seatingBody) {{
            setOpaque(false);
            getViewport().setOpaque(false);
            setBorder(null);
        }}, BorderLayout.CENTER);

        // ── Legend Bar at bottom ──
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 4));
        legend.setOpaque(false);
        legend.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, CLR_BORDER));
        legend.add(createLegendChip("Available", CLR_AVAILABLE));
        legend.add(createLegendChip("Locked (60s Mutex)", CLR_LOCKED));
        legend.add(createLegendChip("Booked / Occupied", CLR_BOOKED));
        legend.add(createLegendChip("Selected", CLR_SELECTED));
        cabinCard.add(legend, BorderLayout.SOUTH);

        return cabinCard;
    }

    private JLabel createColLetterLabel(String letter) {
        JLabel l = new JLabel(letter, SwingConstants.CENTER);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(CLR_MUTED);
        return l;
    }

    private JPanel createClassBanner(String text, Color accent) {
        JPanel banner = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        banner.setBackground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 35));
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 11));
        label.setForeground(accent);
        banner.add(label);
        return banner;
    }

    private JPanel createLegendChip(String text, Color col) {
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        chip.setOpaque(false);
        JLabel dot = new JLabel("●");
        dot.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        dot.setForeground(col);
        JLabel txt = new JLabel(text);
        txt.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        txt.setForeground(CLR_TEXT);
        chip.add(dot);
        chip.add(txt);
        return chip;
    }

    /* ══════════════════════════════════════════════════════
     * CONTROL DECK (Right Sidebar)
     * ══════════════════════════════════════════════════════ */
    private JPanel buildControlDeck() {
        JPanel deck = new JPanel();
        deck.setLayout(new BoxLayout(deck, BoxLayout.Y_AXIS));
        deck.setBackground(CLR_BG);
        deck.setPreferredSize(new Dimension(380, 0));

        // ── Card 1: Fleet & Route Overview ──
        RoundedPanel fleetCard = new RoundedPanel(12, CLR_CARD, CLR_BORDER);
        fleetCard.setLayout(new BoxLayout(fleetCard, BoxLayout.Y_AXIS));
        fleetCard.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        badgeRow.setOpaque(false);
        vehicleBadgeLabel = new JLabel("► FLIGHT MODE");
        vehicleBadgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        vehicleBadgeLabel.setForeground(CLR_ACCENT);
        badgeRow.add(vehicleBadgeLabel);
        fleetCard.add(badgeRow);
        fleetCard.add(Box.createVerticalStrut(4));

        vehicleRouteLabel = new JLabel("Route: —");
        vehicleRouteLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        vehicleRouteLabel.setForeground(Color.WHITE);
        fleetCard.add(vehicleRouteLabel);
        fleetCard.add(Box.createVerticalStrut(3));

        vehicleDepLabel = new JLabel("Departure: —");
        vehicleDepLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        vehicleDepLabel.setForeground(CLR_MUTED);
        fleetCard.add(vehicleDepLabel);

        vehicleBaseFareLabel = new JLabel("Base Rate: —");
        vehicleBaseFareLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        vehicleBaseFareLabel.setForeground(CLR_MUTED);
        fleetCard.add(vehicleBaseFareLabel);

        deck.add(fleetCard);
        deck.add(Box.createVerticalStrut(10));

        // ── Card 2: Occupancy & Live Inventory KPI ──
        RoundedPanel kpiCard = new RoundedPanel(12, CLR_CARD, CLR_BORDER);
        kpiCard.setLayout(new BoxLayout(kpiCard, BoxLayout.Y_AXIS));
        kpiCard.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel occHeader = new JLabel("CABIN OCCUPANCY");
        occHeader.setFont(new Font("Segoe UI", Font.BOLD, 11));
        occHeader.setForeground(CLR_ACCENT_ALT);
        kpiCard.add(occHeader);
        kpiCard.add(Box.createVerticalStrut(6));

        occupancyBar = new OccupancyProgressBar();
        kpiCard.add(occupancyBar);
        kpiCard.add(Box.createVerticalStrut(8));

        // 3 mini KPI pills
        JPanel pills = new JPanel(new GridLayout(1, 3, 6, 0));
        pills.setOpaque(false);
        availCountLabel  = createStatPill("Available", "0", CLR_AVAILABLE);
        lockedCountLabel = createStatPill("Locked", "0", CLR_LOCKED);
        bookedCountLabel = createStatPill("Booked", "0", CLR_BOOKED);
        pills.add(availCountLabel);
        pills.add(lockedCountLabel);
        pills.add(bookedCountLabel);
        kpiCard.add(pills);
        kpiCard.add(Box.createVerticalStrut(6));

        waitlistCountLabel = new JLabel("Waitlist Queue: 0 waiting");
        waitlistCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        waitlistCountLabel.setForeground(CLR_MUTED);
        kpiCard.add(waitlistCountLabel);

        deck.add(kpiCard);
        deck.add(Box.createVerticalStrut(10));

        // ── Card 3: Dynamic Fare Calculator & Booking Form ──
        RoundedPanel actionCard = new RoundedPanel(12, CLR_CARD, CLR_BORDER);
        actionCard.setLayout(new BoxLayout(actionCard, BoxLayout.Y_AXIS));
        actionCard.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        selectedSeatLabel = new JLabel("Selected Seat: None");
        selectedSeatLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        selectedSeatLabel.setForeground(Color.WHITE);
        actionCard.add(selectedSeatLabel);
        actionCard.add(Box.createVerticalStrut(4));

        fareLabel = new JLabel("Dynamic Fare: —");
        fareLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        fareLabel.setForeground(CLR_AVAILABLE);
        actionCard.add(fareLabel);

        fareSubLabel = new JLabel("Real-time surge + category multiplier applied");
        fareSubLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        fareSubLabel.setForeground(CLR_MUTED);
        actionCard.add(fareSubLabel);
        actionCard.add(Box.createVerticalStrut(10));

        // Passenger Name Input
        JLabel nameLbl = new JLabel("Passenger Full Name:");
        nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        nameLbl.setForeground(Color.WHITE);
        actionCard.add(nameLbl);
        actionCard.add(Box.createVerticalStrut(4));

        passengerField = new JTextField();
        passengerField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        passengerField.setBackground(CLR_CARD_ALT);
        passengerField.setForeground(Color.WHITE);
        passengerField.setCaretColor(CLR_ACCENT);
        passengerField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CLR_BORDER, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        passengerField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        actionCard.add(passengerField);
        actionCard.add(Box.createVerticalStrut(10));

        // Action Buttons: BOOK & CANCEL
        JPanel btnGrid = new JPanel(new GridLayout(1, 2, 8, 0));
        btnGrid.setOpaque(false);

        JButton bookBtn = new ModernPillButton("► BOOK SEAT", new Color(37, 99, 235), new Color(29, 78, 216), Color.WHITE);
        bookBtn.addActionListener(e -> onBookSeat());

        JButton cancelBtn = new ModernPillButton("✕ CANCEL", new Color(225, 29, 72), new Color(190, 18, 60), Color.WHITE);
        cancelBtn.addActionListener(e -> onCancelSeat());

        btnGrid.add(bookBtn);
        btnGrid.add(cancelBtn);
        actionCard.add(btnGrid);

        deck.add(actionCard);
        deck.add(Box.createVerticalStrut(10));

        // ── Card 4: Concurrency Simulation & Live Event Terminal ──
        RoundedPanel simCard = new RoundedPanel(12, CLR_CARD, CLR_BORDER);
        simCard.setLayout(new BoxLayout(simCard, BoxLayout.Y_AXIS));
        simCard.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JPanel simHeader = new JPanel(new BorderLayout());
        simHeader.setOpaque(false);
        JLabel simTitle = new JLabel("CONCURRENCY SIMULATION");
        simTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        simTitle.setForeground(CLR_ACCENT);
        simHeader.add(simTitle, BorderLayout.WEST);

        // Simulation Trigger buttons with high-contrast colors
        JPanel simBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        simBtns.setOpaque(false);
        JButton btn3 = new ModernPillButton("3 Agents", new Color(37, 99, 235), new Color(29, 78, 216), Color.WHITE);
        btn3.setPreferredSize(new Dimension(85, 28));
        btn3.addActionListener(e -> simulateAgents(3));

        JButton btn6 = new ModernPillButton("6 Agents", new Color(124, 58, 237), new Color(109, 40, 217), Color.WHITE);
        btn6.setPreferredSize(new Dimension(85, 28));
        btn6.addActionListener(e -> simulateAgents(6));

        simBtns.add(btn3);
        simBtns.add(btn6);
        simHeader.add(simBtns, BorderLayout.EAST);
        simCard.add(simHeader);
        simCard.add(Box.createVerticalStrut(8));

        // Live Event Log Area
        eventLogArea = new JTextArea(5, 25);
        eventLogArea.setEditable(false);
        eventLogArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        eventLogArea.setBackground(CLR_TERMINAL_BG);
        eventLogArea.setForeground(new Color(203, 213, 225));
        eventLogArea.setLineWrap(true);
        eventLogArea.setWrapStyleWord(true);
        eventLogArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JScrollPane logScroll = new JScrollPane(eventLogArea);
        logScroll.setBorder(BorderFactory.createLineBorder(CLR_BORDER, 1));
        logScroll.setPreferredSize(new Dimension(340, 110));
        simCard.add(logScroll);

        deck.add(simCard);
        return deck;
    }

    private JLabel createStatPill(String title, String val, Color col) {
        JLabel l = new JLabel("<html><center><font color='#94A3B8'>" + title + "</font><br><font size='4' color='"
                + String.format("#%02x%02x%02x", col.getRed(), col.getGreen(), col.getBlue())
                + "'><b>" + val + "</b></font></center></html>", SwingConstants.CENTER);
        l.setOpaque(true);
        l.setBackground(CLR_CARD_ALT);
        l.setBorder(BorderFactory.createLineBorder(CLR_BORDER, 1));
        return l;
    }

    /* ══════════════════════════════════════════════════════
     * STATUS BAR — Bottom indicator
     * ══════════════════════════════════════════════════════ */
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(CLR_HEADER);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, CLR_BORDER),
                BorderFactory.createEmptyBorder(8, 20, 8, 20)
        ));

        statusLabel = new JLabel("● Ready — Select a vehicle and click any green seat to begin.");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(CLR_MUTED);
        bar.add(statusLabel, BorderLayout.WEST);

        JLabel projectLabel = new JLabel("CSE2006 — Programming in Java | Group 24 — VIT Bhopal");
        projectLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        projectLabel.setForeground(new Color(100, 116, 139));
        bar.add(projectLabel, BorderLayout.EAST);

        return bar;
    }

    /* ══════════════════════════════════════════════════════════════════
     *                      EVENT HANDLERS & LOGIC
     * ══════════════════════════════════════════════════════════════════ */

    private void onVehicleSelected() {
        Vehicle selected = (Vehicle) vehicleSelector.getSelectedItem();
        if (selected == null) return;

        currentVehicle = selected;
        currentEngine  = getOrCreateEngine(selected);
        selectedSeat   = -1;

        boolean isFlight = "FLIGHT".equalsIgnoreCase(selected.getVehicleType());
        vehicleBadgeLabel.setText(isFlight ? "► AIRLINE PASSENGER FLIGHT" : "► SUPERFAST EXPRESS TRAIN");
        vehicleBadgeLabel.setForeground(isFlight ? CLR_ACCENT : CLR_ACCENT_ALT);

        vehicleRouteLabel.setText("Route: " + selected.getRoute());
        vehicleDepLabel.setText("Departure: " + selected.getDeparture());
        vehicleBaseFareLabel.setText(String.format("Base Rate: Rs. %.2f (%s)",
                selected.getBaseFare(), isFlight ? "1.5x Premium Multiplier" : "1.2x Dynamic Multiplier"));

        selectedSeatLabel.setText("Selected Seat: None");
        fareLabel.setText("Dynamic Fare: —");
        fareSubLabel.setText("Click an available green seat to preview live fare");

        refreshSeatGrid();
        logEvent("FLEET", "Switched vehicle to " + selected.getVehicleId() + " (" + selected.getRoute() + ")");
    }

    private void onSeatClicked(int seatIndex) {
        if (currentEngine == null || currentVehicle == null) {
            updateStatus("ERROR: Please select a vehicle first.");
            return;
        }

        int status = currentEngine.getSeatStatus(seatIndex);
        int seatNum = seatIndex + 1;
        String seatCode = getSeatCode(seatIndex);
        boolean isPremium = (seatIndex < 10);

        if (status == ReservationEngine.AVAILABLE) {
            selectedSeat = seatIndex;
            selectedSeatLabel.setText(String.format("Selected: Seat %s (#%d) [%s]",
                    seatCode, seatNum, isPremium ? "PREMIUM CLASS" : "STANDARD CLASS"));

            double fare = PricingEngine.calculateDynamicPrice(
                    currentVehicle,
                    currentEngine.getBookedCount(),
                    bookingDAO.getRecentFares(currentVehicle.getVehicleId(), 10)
            );

            fareLabel.setText(String.format("Rs. %.2f", fare));
            fareSubLabel.setText(String.format("Class: %s | Surge Multiplier Calculated",
                    isPremium ? (currentVehicle instanceof Flight ? "Business (1.5x)" : "AC First (1.4x)") : "Standard"));

            updateStatus("Seat " + seatCode + " selected. Enter passenger name and click BOOK SEAT.");
            repaintSeats();

        } else if (status == ReservationEngine.LOCKED) {
            String owner = currentEngine.getLockOwner(seatIndex);
            updateStatus("Seat " + seatCode + " is currently LOCKED by " + owner + ". Please wait or choose another.");
            logEvent("LOCK", "Seat " + seatCode + " is locked by active agent: " + owner);

        } else if (status == ReservationEngine.BOOKED) {
            BookingDAO.BookingRecord rec = bookingDAO.findBookingBySeat(
                    currentVehicle.getVehicleId(), seatNum);
            if (rec != null) {
                selectedSeat = seatIndex;
                selectedSeatLabel.setText("Selected: Seat " + seatCode + " [BOOKED]");
                fareLabel.setText(String.format("Rs. %.2f", rec.farePaid));
                fareSubLabel.setText("Passenger: " + rec.passenger + " | Status: CONFIRMED");
                updateStatus("Seat " + seatCode + " is BOOKED by " + rec.passenger + ". Click CANCEL to release.");
                repaintSeats();
            }
        }
    }

    private void onBookSeat() {
        if (currentEngine == null || currentVehicle == null) {
            updateStatus("ERROR: No vehicle selected.");
            return;
        }
        if (selectedSeat < 0) {
            updateStatus("ERROR: No seat selected. Click a green seat first.");
            return;
        }
        String passenger = passengerField.getText().trim();
        if (passenger.isEmpty()) {
            updateStatus("ERROR: Please enter a passenger full name.");
            return;
        }

        final int seatIdx      = selectedSeat;
        final String seatCode  = getSeatCode(seatIdx);
        final String pName     = passenger;
        final String vehicleId = currentVehicle.getVehicleId();

        new Thread(() -> {
            try {
                logEvent("BOOKING", "Attempting lock on Seat " + seatCode + " for " + pName + "...");
                currentEngine.lockSeat(seatIdx, "Console-User");
                SwingUtilities.invokeLater(this::refreshSeatGrid);

                double fare = PricingEngine.calculateDynamicPrice(
                        currentVehicle,
                        currentEngine.getBookedCount(),
                        bookingDAO.getRecentFares(vehicleId, 10)
                );

                int bookingId = bookingDAO.insertBooking(vehicleId, seatIdx + 1, pName, fare);
                currentEngine.confirmSeat(seatIdx);

                bookingDAO.generateETicket(
                        bookingId, vehicleId,
                        currentVehicle.getVehicleType(),
                        currentVehicle.getRoute(),
                        currentVehicle.getDeparture(),
                        seatIdx + 1, pName, fare
                );

                logEvent("SUCCESS", "Confirmed Seat " + seatCode + " for " + pName + " (Ticket #" + bookingId + ")");

                SwingUtilities.invokeLater(() -> {
                    refreshSeatGrid();
                    passengerField.setText("");
                    selectedSeat = -1;
                    selectedSeatLabel.setText("Selected Seat: None");
                    fareLabel.setText("Dynamic Fare: —");
                    updateStatus("✓ CONFIRMED: Seat " + seatCode + " for " + pName + " — Rs."
                            + String.format("%.2f", fare) + " (Ticket #" + bookingId + ")");
                });

            } catch (SeatAlreadyBookedException e) {
                currentEngine.releaseSeat(seatIdx);
                logEvent("COLLISION", "Race condition on Seat " + seatCode + ": " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    refreshSeatGrid();
                    updateStatus("✗ FAILED: " + e.getMessage());
                });
            } catch (BookingFailedException e) {
                currentEngine.releaseSeat(seatIdx);
                logEvent("ERROR", "Database transaction rolled back: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    refreshSeatGrid();
                    updateStatus("✗ DB ERROR: " + e.getMessage());
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "BookingWorker-Seat-" + (seatIdx + 1)).start();
    }

    private void onCancelSeat() {
        if (currentEngine == null || currentVehicle == null || selectedSeat < 0) {
            updateStatus("ERROR: Select a booked (red) seat to cancel.");
            return;
        }

        int seatIdx = selectedSeat;
        String seatCode = getSeatCode(seatIdx);

        if (currentEngine.getSeatStatus(seatIdx) != ReservationEngine.BOOKED) {
            updateStatus("ERROR: Seat " + seatCode + " is not booked. Cannot cancel.");
            return;
        }

        BookingDAO.BookingRecord rec = bookingDAO.findBookingBySeat(
                currentVehicle.getVehicleId(), seatIdx + 1);
        if (rec == null) {
            updateStatus("ERROR: No booking record found for Seat " + seatCode);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Cancel booking for " + rec.passenger + " (Seat " + seatCode + ", Rs."
                        + String.format("%.2f", rec.farePaid) + ")?",
                "Confirm Cancellation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            bookingDAO.cancelBooking(rec.bookingId);
            currentEngine.releaseSeat(seatIdx);
            logEvent("CANCEL", "Cancelled Booking #" + rec.bookingId + " (Seat " + seatCode + ")");

            WaitlistManager.WaitlistEntry promoted =
                    waitlistManager.promoteNext(currentVehicle.getVehicleId());

            if (promoted != null) {
                autoBookWaitlistPassenger(promoted, seatIdx);
                logEvent("WAITLIST", "Auto-promoted " + promoted.passenger + " to Seat " + seatCode);
            }

            refreshSeatGrid();
            selectedSeat = -1;
            selectedSeatLabel.setText("Selected Seat: None");

            String msg = "✓ Booking #" + rec.bookingId + " CANCELLED.";
            if (promoted != null) msg += " Promoted waitlisted: " + promoted.passenger;
            updateStatus(msg);

        } catch (BookingFailedException e) {
            updateStatus("✗ Cancel failed: " + e.getMessage());
        }
    }

    private void autoBookWaitlistPassenger(WaitlistManager.WaitlistEntry entry, int seatIdx) {
        try {
            currentEngine.lockSeat(seatIdx, "Waitlist-Auto");
            double fare = PricingEngine.calculateDynamicPrice(
                    currentVehicle,
                    currentEngine.getBookedCount(),
                    bookingDAO.getRecentFares(currentVehicle.getVehicleId(), 10)
            );

            int bookingId = bookingDAO.insertBooking(
                    currentVehicle.getVehicleId(), seatIdx + 1, entry.passenger, fare);
            currentEngine.confirmSeat(seatIdx);

            bookingDAO.generateETicket(
                    bookingId, currentVehicle.getVehicleId(),
                    currentVehicle.getVehicleType(),
                    currentVehicle.getRoute(),
                    currentVehicle.getDeparture(),
                    seatIdx + 1, entry.passenger, fare);

        } catch (Exception e) {
            System.err.println("[WAITLIST-AUTO] Failed: " + e.getMessage());
        }
    }

    private void simulateAgents(int agentCount) {
        if (currentEngine == null || currentVehicle == null) {
            updateStatus("ERROR: Select a vehicle first.");
            return;
        }

        updateStatus("Spawning " + agentCount + " concurrent booking agents...");
        logEvent("SIMULATION", "Spawning " + agentCount + " concurrent worker threads...");

        Random rand = new Random();
        String vehicleId = currentVehicle.getVehicleId();

        for (int a = 1; a <= agentCount; a++) {
            final int agentNum = a;
            final String agentName = "SimAgent-" + agentNum;

            new Thread(() -> {
                try {
                    int seat = -1;
                    for (int attempt = 0; attempt < TOTAL_SEATS; attempt++) {
                        int idx = rand.nextInt(TOTAL_SEATS);
                        if (currentEngine.getSeatStatus(idx) == ReservationEngine.AVAILABLE) {
                            seat = idx;
                            break;
                        }
                    }
                    if (seat < 0) {
                        logEvent(agentName, "No available seats remaining!");
                        return;
                    }

                    final int targetSeat = seat;
                    final String seatCode = getSeatCode(targetSeat);

                    logEvent(agentName, "Locking Seat " + seatCode + "...");
                    currentEngine.lockSeat(targetSeat, agentName);
                    SwingUtilities.invokeLater(BookingConsole.this::refreshSeatGrid);

                    Thread.sleep(1500 + rand.nextInt(2000));

                    double fare = PricingEngine.calculateDynamicPrice(
                            currentVehicle,
                            currentEngine.getBookedCount(),
                            bookingDAO.getRecentFares(vehicleId, 10));

                    String pName = "Passenger-" + agentNum;
                    int bookingId = bookingDAO.insertBooking(vehicleId, targetSeat + 1, pName, fare);
                    currentEngine.confirmSeat(targetSeat);

                    bookingDAO.generateETicket(
                            bookingId, vehicleId,
                            currentVehicle.getVehicleType(),
                            currentVehicle.getRoute(),
                            currentVehicle.getDeparture(),
                            targetSeat + 1, pName, fare);

                    logEvent(agentName, "✓ Confirmed Seat " + seatCode + " (Ticket #" + bookingId + ")");

                    SwingUtilities.invokeLater(() -> {
                        refreshSeatGrid();
                        updateStatus(agentName + " booked Seat " + seatCode + " for " + pName);
                    });

                } catch (SeatAlreadyBookedException e) {
                    logEvent(agentName, "Contention collision handled: " + e.getMessage());
                } catch (Exception e) {
                    logEvent(agentName, "Error: " + e.getMessage());
                }
            }, agentName).start();
        }
    }

    private void refreshSeatGrid() {
        if (currentEngine == null) return;

        repaintSeats();

        int avail  = currentEngine.getAvailableCount();
        int booked = currentEngine.getBookedCount();
        int locked = currentEngine.getLockedCount();

        occupancyBar.setProgress(booked, TOTAL_SEATS);

        availCountLabel.setText("<html><center><font color='#94A3B8'>Available</font><br><font size='4' color='#10B981'><b>"
                + avail + "</b></font></center></html>");
        lockedCountLabel.setText("<html><center><font color='#94A3B8'>Locked</font><br><font size='4' color='#F59E0B'><b>"
                + locked + "</b></font></center></html>");
        bookedCountLabel.setText("<html><center><font color='#94A3B8'>Booked</font><br><font size='4' color='#EF4444'><b>"
                + booked + "</b></font></center></html>");

        if (currentVehicle != null) {
            int waitCount = waitlistManager.getWaitlistCount(currentVehicle.getVehicleId());
            waitlistCountLabel.setText("Waitlist Queue: " + waitCount + " passenger(s) waiting");
        }
    }

    private void repaintSeats() {
        if (seatButtons == null) return;
        for (SeatButton btn : seatButtons) {
            if (btn != null) btn.repaint();
        }
    }

    private String getSeatCode(int seatIndex) {
        int row = (seatIndex / GRID_COLS) + 1;
        char colChar = (char) ('A' + (seatIndex % GRID_COLS));
        return row + "" + colChar;
    }

    private void logEvent(String tag, String message) {
        String timestamp = timeFmt.format(new java.util.Date());
        String line = String.format("[%s] %-10s %s%n", timestamp, tag, message);
        if (SwingUtilities.isEventDispatchThread()) {
            eventLogArea.append(line);
            eventLogArea.setCaretPosition(eventLogArea.getDocument().getLength());
        } else {
            SwingUtilities.invokeLater(() -> {
                eventLogArea.append(line);
                eventLogArea.setCaretPosition(eventLogArea.getDocument().getLength());
            });
        }
    }

    private void updateStatus(String msg) {
        if (SwingUtilities.isEventDispatchThread()) {
            statusLabel.setText("● " + msg);
        } else {
            SwingUtilities.invokeLater(() -> statusLabel.setText("● " + msg));
        }
    }

    /* ══════════════════════════════════════════════════════
     * DIALOGS: Bookings JTable, Waitlist & Audit Export
     * ══════════════════════════════════════════════════════ */
    private void showBookingHistoryDialog() {
        JDialog dialog = new JDialog(this, "Passenger Booking Database Ledger (JTable)", true);
        dialog.setSize(920, 520);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        String[] cols = {"ID", "Vehicle", "Seat", "Passenger Name", "Fare Paid (INR)", "Status", "Booked At"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        List<BookingDAO.BookingRecord> records = bookingDAO.getAllBookings();
        for (BookingDAO.BookingRecord r : records) {
            model.addRow(new Object[]{
                r.bookingId, r.vehicleId, r.seatNumber, r.passenger,
                String.format("%.2f", r.farePaid), r.status, r.bookedAt
            });
        }

        JTable table = new JTable(model);
        table.setRowHeight(26);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        searchPanel.setBackground(CLR_HEADER);
        JLabel searchLbl = new JLabel("Instant Record Search:");
        searchLbl.setForeground(Color.WHITE);
        searchLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JTextField searchField = new JTextField(24);
        searchField.setBackground(CLR_CARD);
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(CLR_ACCENT);
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            private void filter() {
                String txt = searchField.getText().trim();
                sorter.setRowFilter(txt.isEmpty() ? null : RowFilter.regexFilter("(?i)" + txt));
            }
        });
        searchPanel.add(searchLbl);
        searchPanel.add(searchField);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnPanel.setBackground(CLR_HEADER);
        JButton cancelBtn = new ModernPillButton("Cancel Selected Ticket", new Color(225, 29, 72), new Color(190, 18, 60), Color.WHITE);
        JButton exportBtn = new ModernPillButton("Export Audit Report", CLR_CARD, new Color(51, 65, 85), Color.WHITE);
        JButton closeBtn  = new ModernPillButton("Close", CLR_CARD, new Color(51, 65, 85), Color.WHITE);

        cancelBtn.addActionListener(e -> {
            int sel = table.getSelectedRow();
            if (sel < 0) {
                JOptionPane.showMessageDialog(dialog, "Select a booking row to cancel.");
                return;
            }
            int mRow = table.convertRowIndexToModel(sel);
            int bookingId = (int) model.getValueAt(mRow, 0);
            String vId    = (String) model.getValueAt(mRow, 1);
            int sNum      = (int) model.getValueAt(mRow, 2);
            String st     = (String) model.getValueAt(mRow, 5);

            if ("CANCELLED".equalsIgnoreCase(st)) {
                JOptionPane.showMessageDialog(dialog, "Booking #" + bookingId + " is already cancelled.");
                return;
            }

            int conf = JOptionPane.showConfirmDialog(dialog,
                    "Cancel Booking #" + bookingId + " (Seat " + sNum + " on " + vId + ")?",
                    "Confirm Cancellation", JOptionPane.YES_NO_OPTION);
            if (conf == JOptionPane.YES_OPTION) {
                try {
                    bookingDAO.cancelBooking(bookingId);
                    model.setValueAt("CANCELLED", mRow, 5);
                    ReservationEngine eng = engines.get(vId);
                    if (eng != null) {
                        eng.releaseSeat(sNum - 1);
                        WaitlistManager.WaitlistEntry promoted = waitlistManager.promoteNext(vId);
                        if (promoted != null) autoBookWaitlistPassenger(promoted, sNum - 1);
                    }
                    refreshSeatGrid();
                    logEvent("LEDGER", "Cancelled booking #" + bookingId + " from ledger table");
                    JOptionPane.showMessageDialog(dialog, "Booking cancelled successfully!");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, "Error: " + ex.getMessage());
                }
            }
        });

        exportBtn.addActionListener(e -> exportAuditReportDialog());
        closeBtn.addActionListener(e -> dialog.dispose());

        btnPanel.add(cancelBtn);
        btnPanel.add(exportBtn);
        btnPanel.add(closeBtn);

        dialog.add(searchPanel, BorderLayout.NORTH);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void exportAuditReportDialog() {
        File ticketsDir = new File("tickets");
        if (!ticketsDir.exists()) ticketsDir.mkdirs();
        File target = new File(ticketsDir, "AUDIT_REPORT_" + System.currentTimeMillis() + ".txt");

        if (bookingDAO.exportAuditReport(target)) {
            logEvent("AUDIT", "Exported report to " + target.getName());
            JOptionPane.showMessageDialog(this,
                    "Audit report generated successfully:\n" + target.getAbsolutePath(),
                    "Export Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showWaitlistDialog() {
        if (currentVehicle == null) return;
        List<WaitlistManager.WaitlistEntry> entries =
                waitlistManager.getWaitlistForVehicle(currentVehicle.getVehicleId());

        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No passengers on waitlist for " + currentVehicle.getVehicleId());
            return;
        }

        StringBuilder sb = new StringBuilder("Priority Waitlist for " + currentVehicle.getVehicleId() + ":\n\n");
        int rank = 1;
        for (WaitlistManager.WaitlistEntry e : entries) {
            sb.append(" ").append(rank++).append(". ").append(e.toString()).append("\n");
        }

        JTextArea ta = new JTextArea(sb.toString());
        ta.setEditable(false);
        ta.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JOptionPane.showMessageDialog(this, new JScrollPane(ta) {{ setPreferredSize(new Dimension(350, 250)); }},
                "Waitlist — " + currentVehicle.getVehicleId(), JOptionPane.PLAIN_MESSAGE);
    }

    private void showAddToWaitlistDialog() {
        if (currentVehicle == null) return;
        JTextField nameField = new JTextField();
        String[] priorities = {"Normal (0)", "Senior Citizen (1)", "VIP (2)"};
        JComboBox<String> priorityBox = new JComboBox<>(priorities);

        JPanel panel = new JPanel(new GridLayout(4, 1, 5, 5));
        panel.add(new JLabel("Passenger Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Priority Category:"));
        panel.add(priorityBox);

        int res = JOptionPane.showConfirmDialog(this, panel,
                "Enqueue Waitlist Passenger — " + currentVehicle.getVehicleId(),
                JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            if (name.isEmpty()) return;
            int prio = priorityBox.getSelectedIndex();
            waitlistManager.addToWaitlist(currentVehicle.getVehicleId(), name, prio);
            refreshSeatGrid();
            logEvent("WAITLIST", "Enqueued " + name + " (Priority: " + priorities[prio] + ")");
        }
    }

    private ReservationEngine getOrCreateEngine(Vehicle vehicle) {
        String id = vehicle.getVehicleId();
        if (!engines.containsKey(id)) {
            long timeoutMs = DBConnection.getInstance().getLockTimeoutSec() * 1000L;
            ReservationEngine engine = new ReservationEngine(vehicle, timeoutMs);
            List<BookingDAO.BookingRecord> bookings = bookingDAO.getBookingsForVehicle(id);
            for (BookingDAO.BookingRecord b : bookings) {
                engine.markBooked(b.seatNumber - 1);
            }
            engines.put(id, engine);
        }
        return engines.get(id);
    }

    private List<Vehicle> loadVehiclesFromDB() {
        List<Vehicle> list = new ArrayList<>();
        String sql = "SELECT * FROM vehicles ORDER BY vehicle_type, vehicle_id";
        try (Statement stmt = DBConnection.getInstance().getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String id        = rs.getString("vehicle_id");
                String type      = rs.getString("vehicle_type");
                String route     = rs.getString("route");
                String departure = rs.getString("departure");
                int    seats     = rs.getInt("total_seats");
                double fare      = rs.getDouble("base_fare");

                Vehicle v = "FLIGHT".equals(type)
                        ? new Flight(id, route, departure, seats, fare)
                        : new Train(id, route, departure, seats, fare);
                list.add(v);
            }
        } catch (SQLException e) {
            System.err.println("[CONSOLE] Error loading vehicles: " + e.getMessage());
        }
        return list;
    }

    /* ══════════════════════════════════════════════════════
     * CUSTOM UI HELPER CLASSES (Anti-Aliased 2D Renderers)
     * ══════════════════════════════════════════════════════ */

    /** Antialiased rounded panel container. */
    static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color bgColor;
        private final Color borderColor;

        public RoundedPanel(int radius, Color bgColor, Color borderColor) {
            this.radius = radius;
            this.bgColor = bgColor;
            this.borderColor = borderColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bgColor);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            if (borderColor != null) {
                g2.setColor(borderColor);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g2.dispose();
        }
    }

    /** High-Contrast Modern Pill Button (Immune to OS LookAndFeel override) */
    static class ModernPillButton extends JButton {
        private final Color normalBg;
        private final Color hoverBg;
        private final Color textColor;
        private boolean isHovered = false;

        public ModernPillButton(String text, Color normalBg, Color hoverBg, Color textColor) {
            super(text);
            this.normalBg = normalBg;
            this.hoverBg  = hoverBg;
            this.textColor = textColor;
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setForeground(textColor);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(110, 32));

            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { isHovered = true; repaint(); }
                public void mouseExited(MouseEvent e)  { isHovered = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isHovered ? hoverBg : normalBg);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g2.setColor(new Color(71, 85, 105));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

            // Draw crisp centered text
            g2.setColor(textColor);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int tx = (getWidth() - fm.stringWidth(getText())) / 2;
            int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), tx, ty);

            g2.dispose();
        }
    }

    /** Custom High-Contrast Dark Progress Bar */
    static class OccupancyProgressBar extends JPanel {
        private int value = 0;
        private int max = 30;

        public OccupancyProgressBar() {
            setOpaque(false);
            setPreferredSize(new Dimension(340, 24));
        }

        public void setProgress(int value, int max) {
            this.value = value;
            this.max = max;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            // Track background
            g2.setColor(new Color(15, 23, 42));
            g2.fillRoundRect(0, 0, w, h, 8, 8);
            g2.setColor(new Color(71, 85, 105));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

            // Fill
            int fillW = max > 0 ? (int) (((double) value / max) * (w - 2)) : 0;
            if (fillW > 0) {
                g2.setColor(new Color(16, 185, 129));
                g2.fillRoundRect(1, 1, fillW, h - 2, 6, 6);
            }

            // Text
            int percent = max > 0 ? (value * 100 / max) : 0;
            String text = value + " / " + max + " Booked (" + percent + "%)";
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(text, tx, ty);

            g2.dispose();
        }
    }

    /** Custom painted realistic cabin seat button. */
    class SeatButton extends JButton {
        private final int seatIndex;
        private boolean isHovered = false;

        public SeatButton(int seatIndex) {
            this.seatIndex = seatIndex;
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            addActionListener(e -> onSeatClicked(seatIndex));

            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { isHovered = true; repaint(); }
                public void mouseExited(MouseEvent e)  { isHovered = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            int status = currentEngine != null ? currentEngine.getSeatStatus(seatIndex) : ReservationEngine.AVAILABLE;
            Color baseColor;
            switch (status) {
                case ReservationEngine.LOCKED: baseColor = CLR_LOCKED; break;
                case ReservationEngine.BOOKED: baseColor = CLR_BOOKED; break;
                default: baseColor = CLR_AVAILABLE; break;
            }

            boolean isCurrentSelected = (selectedSeat == seatIndex);

            // Draw seat base
            g2.setColor(isHovered ? baseColor.brighter() : baseColor);
            g2.fillRoundRect(4, 8, w - 8, h - 11, 10, 10);

            // Headrest contour
            g2.setColor(new Color(255, 255, 255, 60));
            g2.fillRoundRect(10, 4, w - 20, 8, 6, 6);

            // Selected outline
            if (isCurrentSelected) {
                g2.setColor(CLR_SELECTED);
                g2.setStroke(new BasicStroke(3f));
                g2.drawRoundRect(2, 2, w - 5, h - 5, 12, 12);
            } else {
                g2.setColor(new Color(255, 255, 255, 30));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(4, 8, w - 9, h - 12, 10, 10);
            }

            // Seat code text (e.g. 1A) and seat number (#1)
            String code = getSeatCode(seatIndex);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(code)) / 2;
            int ty = (h / 2) + 2;
            g2.drawString(code, tx, ty);

            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            String numStr = "#" + (seatIndex + 1);
            FontMetrics fm2 = g2.getFontMetrics();
            int nx = (w - fm2.stringWidth(numStr)) / 2;
            g2.drawString(numStr, nx, ty + 12);

            g2.dispose();
        }
    }

    /* ══════════════════════════════════════════════════════
     * MAIN METHOD — Application Entry Point
     * ══════════════════════════════════════════════════════ */
    public static void main(String[] args) {
        // If CLI flags provided or environment is headless (no GUI display), run CLI
        if (args.length > 0 || GraphicsEnvironment.isHeadless()) {
            BookingCLI.main(args);
            return;
        }

        try {
            // Set cross-platform look and feel to guarantee exact dark theme colors
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) { }

            System.out.println("==========================================================");
            System.out.println("  Multi-Threaded Flight & Train Ticket Reservation Desk  ");
            System.out.println("  CSE2006 -- Programming in Java | VIT Bhopal            ");
            System.out.println("  Group 24                                               ");
            System.out.println("==========================================================");
            System.out.println();

            SwingUtilities.invokeLater(BookingConsole::new);
        } catch (Throwable t) {
            System.out.println("[NOTICE] Headless terminal environment detected (no GUI display).");
            System.out.println("[NOTICE] Redirecting to Terminal Command Line Interface (CLI)...");
            BookingCLI.main(args);
        }
    }
}
